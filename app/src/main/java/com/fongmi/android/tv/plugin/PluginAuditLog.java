package com.fongmi.android.tv.plugin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 插件审计日志：每次插件外发请求与每次插件异常的唯一记账点。
 *
 * <p><b>为什么这不是可选的调试功能。</b>D2 裁定 `net.request` 不做域名白名单，等于
 * 放弃了"只允许访问已知 host"这道硬约束。于是审计日志成为**唯一的安全观测面**：
 * 一旦某个插件外发了不该发的请求，只有这份记录能把它定位到具体插件 id 与版本。
 * 因此它是 P2 的验收项，而不是装饰。
 *
 * <p><b>脱敏规则。</b>记录 host + path，**不记录 query**——query 里经常带 token、
 * 签名或用户标识。这与主题侧 {@code WebThemeRuntimeDiagnostics.safeUrl} 的取向一致。
 *
 * <p><b>内存环形缓冲 + 落盘。</b>缓冲保留最近 {@link #MAX_ENTRIES} 条；每次追加后把整份
 * 缓冲交给 {@link PluginAuditStore} 覆盖写入 cache 目录，进程被杀或重启后仍能回放上次的
 * 记录——崩溃现场恰恰是最需要证据的时刻。落盘代价是每次记账重写整份文件（约
 * 500 × 200 B ≈ 100 KiB），换来的是"读路径永不解析增量日志、文件永远不会出现半条记录
 * 之外的结构性损坏"这一确定性。
 *
 * <p><b>三块职责各在一个文件里。</b>本类只做环形缓冲与对外契约；JSON 读写由
 * {@link PluginAuditCodec} 承担；"同步写还是交给写入器、旧快照会不会覆盖新快照"由
 * {@link PluginAuditWriter} 承担。拆开的直接动因是 docs §7:310 的 500 行门禁，但这三块本来
 * 就互不依赖：缓冲不关心字段名，编解码无状态，调度只看"待写的最终文本"。
 */
public final class PluginAuditLog {

    /** 保留的最大记录条数；超出后丢弃最旧的。 */
    public static final int MAX_ENTRIES = 500;

    /** 单条记录里 host 与 path 的长度上限，防止畸形 URL 撑爆内存。 */
    private static final int MAX_HOST_LENGTH = 128;
    private static final int MAX_PATH_LENGTH = 256;

    private static final PluginAuditLog INSTANCE = new PluginAuditLog();

    private final Deque<Entry> entries;
    private final Object lock;

    /** 落盘调度与版本仲裁都在这里；本类只用它"把最终文本交出去"，不碰任何 IO 细节。 */
    private final PluginAuditWriter writer;

    /** 是否已经尝试过从落盘装载；只装载一次，之后内存就是权威副本。 */
    private boolean loaded;

    private PluginAuditLog() {
        this.entries = new ArrayDeque<>();
        this.lock = new Object();
        this.writer = new PluginAuditWriter();
    }

    public static PluginAuditLog get() {
        return INSTANCE;
    }

    /**
     * 记录一次插件网络请求。
     *
     * @param status  HTTP 状态码；网络失败传 {@code 0}
     * @param outcome {@code ok} / {@code error} / {@code timeout} / {@code blocked}
     */
    public void recordRequest(@Nullable String pluginId, @Nullable String pluginVersion,
            @Nullable String event, @Nullable String method, @Nullable String url, int status,
            long durationMs, @Nullable String outcome) {
        recordRequest(pluginId, pluginVersion, event, method, url, status, durationMs, outcome, 0L);
    }

    /**
     * 记录一次插件网络请求，并记下请求体大小。
     *
     * <p>docs §8.1:330 把"请求体大小"列为审计字段：既然不做域名白名单，一个插件把多少字节
     * 发出去就是仅次于"发给谁"的证据。{@code bodySize} 只记字节数，**不记内容**——内容里
     * 可能带用户数据，而审计是观测面而非抓包器。</p>
     *
     * <p>8 参重载保留并委托到这里、{@code bodySize} 传 {@code 0}：调用方（网络桥、缓存命中
     * 分支）不需要一次全改，且"没有请求体"与"未测量"在语义上本来就都是 {@code 0}。</p>
     */
    public void recordRequest(@Nullable String pluginId, @Nullable String pluginVersion,
            @Nullable String event, @Nullable String method, @Nullable String url, int status,
            long durationMs, @Nullable String outcome, long bodySize) {
        Target target = target(url);
        append(new Entry(pluginId, pluginVersion, event, method, target.host, target.path, status,
                durationMs, outcome, "request", bodySize));
    }

    /** 记录一次插件异常或被拒绝的能力调用。 */
    public void recordFailure(@Nullable String pluginId, @Nullable String pluginVersion,
            @Nullable String event, @Nullable String capability, @Nullable String outcome,
            @Nullable String detail) {
        append(new Entry(pluginId, pluginVersion, event, capability, "", limited(detail, MAX_PATH_LENGTH),
                0, 0L, outcome == null ? "error" : outcome, "failure", 0L));
    }

    /** 快照当前全部记录，最早的在最前。 */
    @NonNull
    public List<Entry> entries() {
        synchronized (lock) {
            loadOnce();
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /** 某个插件的全部记录，用于"追责到具体插件"。 */
    @NonNull
    public List<Entry> entriesFor(@Nullable String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return Collections.emptyList();
        List<Entry> matched = new ArrayList<>();
        synchronized (lock) {
            loadOnce();
            for (Entry entry : entries) {
                if (pluginId.equals(entry.pluginId)) matched.add(entry);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    public int size() {
        synchronized (lock) {
            loadOnce();
            return entries.size();
        }
    }

    /**
     * 清空全部记录（用户主动清理或应用级重置）。
     *
     * <p>落盘一起清掉，否则下次启动会把用户刚刚删掉的记录原样读回来。清空本身也算"已装载"，
     * 避免之后的 {@link #entries()} 又从旧文件里恢复出内存副本。</p>
     */
    public void clear() {
        synchronized (lock) {
            entries.clear();
            loaded = true;
            persist();
        }
    }

    private void append(Entry entry) {
        synchronized (lock) {
            loadOnce();
            entries.addLast(entry);
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
            persist();
        }
    }

    /**
     * 首次访问时把落盘记录装进内存环形缓冲（调用方必须持有 {@link #lock}）。
     *
     * <p>先把 {@code loaded} 置真再读：读失败时不再重试，否则一条坏文件会让每次访问都重读
     * 一遍磁盘，把审计变成性能问题。</p>
     */
    private void loadOnce() {
        if (loaded) return;
        loaded = true;
        try {
            String text = PluginAuditWriter.store().read();
            if (text.isEmpty()) return;
            entries.clear();
            for (String line : text.split("\n")) {
                Entry entry = PluginAuditCodec.parse(line);
                if (entry == null) continue;
                entries.addLast(entry);
            }
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        } catch (Throwable ignored) {
            // 读盘失败等同于没有历史：审计不能因此不可用。
        }
    }

    /**
     * 把当前整份缓冲交给落盘路径（调用方必须持有 {@link #lock}）。
     *
     * <p><b>为什么这里只序列化、不写文件。</b>本方法会在主线程被调到（事件驱动的
     * {@code quarantine -> recordFailure}）。序列化是纯内存操作，写文件才是 StrictMode 的
     * 磁盘违规；把两者拆开，主线程那条路就能立刻返回。非主线程仍同步写，调用方"返回即已
     * 落盘"的既有语义不变——由 {@link PluginAuditWriter} 分流。</p>
     *
     * <p>序列化与版本号都在本临界区内完成：版本号必须与它对应的内容取自同一个临界区，
     * 否则两个线程可能拿到同一号。</p>
     *
     * <p>这里再包一层 try/catch 而不是只靠写入器里那层：序列化本身也可能抛（内存不足、
     * Gson 内部异常），而拆分前它同样被这一层兜住。语法上少接一段就少一次"审计把网络请求
     * 弄挂"的机会，所以这层恒定保留。</p>
     */
    private void persist() {
        try {
            writer.persist(PluginAuditCodec.serialize(entries));
        } catch (Throwable ignored) {
            // 见方法注释：落盘是观测面，不能让它的失败影响记账。
        }
    }

    /**
     * 仅测试使用：强制"这次是否走异步写入器"，绕过真实主线程判定。
     *
     * <p>JVM 单测里主线程判定恒为假，没有这个入口就永远测不到异步分支。
     * 传 {@code null} 恢复正常判定。</p>
     */
    static void forceAsyncWriterForTest(@Nullable Boolean value) {
        PluginAuditWriter.forceAsyncForTest(value);
    }

    /**
     * 仅测试使用：阻塞到写入器把当前待写内容真正落盘为止。
     *
     * <p>生产代码不需要它——主线程自己在返回前不会去读文件；需要等的是测试里的断言。</p>
     */
    static void flushForTest() {
        INSTANCE.writer.flush();
    }

    /** 仅测试使用：替换落盘后端。传 {@code null} 恢复默认实现。 */
    static void storeForTest(@Nullable PluginAuditStore value) {
        PluginAuditWriter.storeForTest(value);
    }

    /**
     * 仅测试使用：丢弃内存副本并让下一次访问重新装载，模拟"进程重启后重新打开日志"。
     *
     * <p>单例是 {@code static final}，测试无法真的造第二个实例，所以用这一个入口表达同一件事。</p>
     */
    static void reloadForTest() {
        PluginAuditLog log = INSTANCE;
        synchronized (log.lock) {
            log.entries.clear();
            log.loaded = false;
        }
    }

    /**
     * 从 URL 提取 host 与 path，**丢弃 query 与 fragment**。
     *
     * <p>不用 {@code java.net.URI}：畸形 URL 会抛异常，而审计路径不能因为一条脏 URL
     * 就整条记录丢失（那正好是安全上最该留痕的时刻）。这里用手工切分，永不抛异常。
     */
    @NonNull
    static Target target(@Nullable String url) {
        String raw = url == null ? "" : url.trim();
        if (raw.isEmpty()) return new Target("", "");
        int schemeEnd = raw.indexOf("://");
        String rest = schemeEnd >= 0 ? raw.substring(schemeEnd + 3) : raw;
        int queryStart = rest.indexOf('?');
        if (queryStart >= 0) rest = rest.substring(0, queryStart);
        int fragmentStart = rest.indexOf('#');
        if (fragmentStart >= 0) rest = rest.substring(0, fragmentStart);
        int slash = rest.indexOf('/');
        String host = slash >= 0 ? rest.substring(0, slash) : rest;
        String path = slash >= 0 ? rest.substring(slash) : "/";
        return new Target(limited(host, MAX_HOST_LENGTH), limited(path, MAX_PATH_LENGTH));
    }

    private static String limited(@Nullable String value, int maxLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    record Target(String host, String path) {
    }

    /**
     * 一条审计记录。
     *
     * @param kind     {@code request} 或 {@code failure}
     * @param bodySize 请求体字节数；{@code failure} 与旧快照恒为 {@code 0}
     */
    public record Entry(String pluginId, @Nullable String pluginVersion, @Nullable String event,
            @Nullable String method, String host, String path, int status, long durationMs,
            @Nullable String outcome, String kind, long bodySize) {
    }
}
