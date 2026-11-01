package com.fongmi.android.tv.plugin;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * <p><b>为什么必须区分线程。</b>这条路径**不**只跑在网络线程上：{@code PluginHost.onEvent}
 * 由 {@code PluginEventBus} 在主线程驱动，插件脚本一抛错就会走到
 * {@code quarantine -> recordFailure -> append}。若仍在这里同步重写整份文件，那就是
 * StrictMode 磁盘写违规加掉帧，而审计只是观测面，不该拖慢 UI。所以：主线程只做内存序列化，
 * 写文件交给单线程写入器并立刻返回；网络线程 / JS 线程 / JVM 单测线程保持同步写，
 * "调用返回即已落盘"的既有语义一个字都不变。写入顺序由版本号保证，旧快照永不覆盖新快照。
 */
public final class PluginAuditLog {

    /** 保留的最大记录条数；超出后丢弃最旧的。 */
    public static final int MAX_ENTRIES = 500;

    /** 单条记录里 host 与 path 的长度上限，防止畸形 URL 撑爆内存。 */
    private static final int MAX_HOST_LENGTH = 128;
    private static final int MAX_PATH_LENGTH = 256;

    /** 落盘行里的字段名；与 {@link Entry} 的 record 组件一一对应。 */
    private static final String KEY_PLUGIN_ID = "pluginId";
    private static final String KEY_PLUGIN_VERSION = "pluginVersion";
    private static final String KEY_EVENT = "event";
    private static final String KEY_METHOD = "method";
    private static final String KEY_HOST = "host";
    private static final String KEY_PATH = "path";
    private static final String KEY_STATUS = "status";
    private static final String KEY_DURATION = "durationMs";
    private static final String KEY_OUTCOME = "outcome";
    private static final String KEY_KIND = "kind";

    private static final PluginAuditLog INSTANCE = new PluginAuditLog();

    private final Deque<Entry> entries;
    private final Object lock;

    /**
     * 落盘后端；{@code null} 表示还没解析过，首次使用时才取 {@link PluginAuditStore#get()}。
     *
     * <p>惰性而不是在静态初始化里直取：JVM 单测没有 {@code Init.context()}，
     * static 初始化抛异常会变成 {@code ExceptionInInitializerError}，把整个测试类打死。</p>
     */
    private static volatile PluginAuditStore store;

    /** 是否已经尝试过从落盘装载；只装载一次，之后内存就是权威副本。 */
    private boolean loaded;

    /**
     * 落盘顺序的唯一依据：每次要写盘就 +1，取到编号最大的内容才有资格覆盖文件。
     *
     * <p><b>为什么必须有它。</b>主线程把快照交给写入器就返回，网络线程却仍同步写盘；两者
     * 并发时，"谁先发起"和"谁先写完"不再同序。没有版本号，一个更旧的快照完全可能在新快照
     * 落盘之后才写下去，磁盘上就会永久留下少了最后几条的日志——而那几条往往正是刚发生的
     * 失败。有了版本号，任何较早的写入都发现自己不是最新就整条放弃，旧快照永不覆盖新快照。</p>
     *
     * <p>由 {@link #lock} 递增、由 {@link #writeLock} 消费。</p>
     */
    private long nextVersion;

    /** 版本号与排队状态的短暂临界区；与 {@link #lock} 分开，避免写文件时挡住记账线程。 */
    private final Object writeLock = new Object();

    /** 真正落盘的互斥；只包住 IO 与版本比对，绝不在主线程上被持有。 */
    private final Object ioLock = new Object();

    /** 是否已经给写入器排过一个 drain 任务；避免主线程刷日志时堆出一串空转任务。 */
    private boolean drainScheduled;

    /** 已真正落盘的版本号；写入前比对它，小于等于就弃写。 */
    private long writtenVersion;

    /** 已排队但还没写的最新快照版本与内容；{@code null} 表示没有待写内容。 */
    private long queuedVersion;
    private String queuedText;

    /** 单线程写入器；惰性创建，守护线程，进程退出不被它拖住。 */
    private ExecutorService writer;

    /** 仅测试使用：强制指定"是否走异步写入器"，绕过真实主线程判定。 */
    private static volatile Boolean deferForTest;

    private PluginAuditLog() {
        this.entries = new ArrayDeque<>();
        this.lock = new Object();
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
        Target target = target(url);
        append(new Entry(pluginId, pluginVersion, event, method, target.host, target.path, status,
                durationMs, outcome, "request"));
    }

    /** 记录一次插件异常或被拒绝的能力调用。 */
    public void recordFailure(@Nullable String pluginId, @Nullable String pluginVersion,
            @Nullable String event, @Nullable String capability, @Nullable String outcome,
            @Nullable String detail) {
        append(new Entry(pluginId, pluginVersion, event, capability, "", limited(detail, MAX_PATH_LENGTH),
                0, 0L, outcome == null ? "error" : outcome, "failure"));
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
            String text = store().read();
            if (text.isEmpty()) return;
            entries.clear();
            for (String line : text.split("\n")) {
                Entry entry = parse(line);
                if (entry == null) continue;
                entries.addLast(entry);
            }
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        } catch (Throwable ignored) {
            // 读盘失败等同于没有历史：审计不能因此不可用。
        }
    }

    /**
     * 把当前整份缓冲落盘（调用方必须持有 {@link #lock}）。
     *
     * <p><b>为什么这里只序列化、不写文件。</b>本方法会在主线程被调到（事件驱动的
     * {@code quarantine -> recordFailure}）。序列化是纯内存操作，写文件才是 StrictMode 的
     * 磁盘违规；把两者拆开，主线程那条路就能立刻返回。非主线程仍同步写，调用方"返回即已
     * 落盘"的既有语义不变。</p>
     *
     * <p>整段 try/catch：写盘失败只丢持久化，绝不让异常冒到网络请求或事件派发的返回路径上。</p>
     */
    private void persist() {
        try {
            String text = serialize();
            // 版本号必须与内容在同一个临界区里取，否则两个线程可能拿到同一号。
            long version = ++nextVersion;
            if (isMainThread()) {
                enqueue(version, text);
            } else {
                writeIfNewer(version, text);
            }
        } catch (Throwable ignored) {
            // 见方法注释：落盘是观测面，不能让它的失败影响记账。
        }
    }

    /** 把内存环形缓冲序列化成一行一条 JSON 的文本；空缓冲返回空串（等于"删除文件"）。 */
    @NonNull
    private String serialize() {
        if (entries.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (Entry entry : entries) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(toJson(entry).toString());
        }
        return builder.toString();
    }

    /**
     * 主线程分支：只把"稍后要写的最终内容"放进来就返回，绝不碰文件。
     *
     * <p>同一个快照槽位被后到的更新内容直接覆盖，而不是排队——审计要的是"最终状态收敛到
     * 内存最新快照"，中间态没有保存价值，排队反而会在频繁事件时积压成写放大。</p>
     */
    private void enqueue(long version, String text) {
        synchronized (writeLock) {
            // 更旧的快照已经在磁盘上了，排队也没有意义。
            if (version <= writtenVersion) return;
            if (queuedText != null && queuedVersion > version) return;
            queuedVersion = version;
            queuedText = text;
        }
        scheduleDrain();
    }

    /** 已经在跑就不要再排一个任务，否则主线程刷日志时会堆出一串空转任务。 */
    private void scheduleDrain() {
        synchronized (writeLock) {
            if (drainScheduled) return;
            drainScheduled = true;
        }
        try {
            writer().execute(this::drain);
        } catch (Throwable error) {
            synchronized (writeLock) {
                drainScheduled = false;
                writeLock.notifyAll();
            }
        }
    }

    /** 写入器线程主体：一直写到手头没有待写内容为止，然后放开 {@link #drainScheduled}。 */
    private void drain() {
        try {
            while (true) {
                long version;
                String text;
                synchronized (writeLock) {
                    if (queuedText == null) {
                        drainScheduled = false;
                        writeLock.notifyAll();
                        return;
                    }
                    version = queuedVersion;
                    text = queuedText;
                    queuedText = null;
                }
                writeIfNewer(version, text);
            }
        } catch (Throwable error) {
            synchronized (writeLock) {
                drainScheduled = false;
                writeLock.notifyAll();
            }
        }
    }

    /**
     * 真正落盘的唯一入口：只有比已落盘版本更新的内容才有资格覆盖文件。
     *
     * <p>IO 由 {@link #ioLock} 串行化，版本比对与写入在同一个 ioLock 临界区内完成，
     * 所以"比对通过"与"写完"之间不会有别的线程插进来写一个更旧的版本。</p>
     */
    private void writeIfNewer(long version, String text) {
        synchronized (ioLock) {
            synchronized (writeLock) {
                if (version <= writtenVersion) return;
            }
            try {
                store().write(text);
            } catch (Throwable ignored) {
                // PluginAuditStore 自己已吞异常；这里再兜一层，防测试用的 Sink 直接抛。
            }
            synchronized (writeLock) {
                if (version > writtenVersion) writtenVersion = version;
                writeLock.notifyAll();
            }
        }
    }

    /**
     * 主线程判定，写法与 {@code PluginController.isMainThread} 一致。
     *
     * <p>整体 try/catch 是必需的：JVM 单测里 {@code android.os.Looper} 是桩，调用会抛
     * {@code RuntimeException("Stub!")} 或 {@code NoClassDefFoundError}。那种情况必须当作
     * "非主线程"（同步写），而不是被误判成主线程而丢掉落盘。</p>
     */
    private static boolean isMainThread() {
        Boolean forced = deferForTest;
        if (forced != null) return forced;
        try {
            return Looper.myLooper() == Looper.getMainLooper();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 单线程写入器；守护线程，进程退出时不会被一条待写审计记录拖住。 */
    private ExecutorService writer() {
        synchronized (writeLock) {
            if (writer == null) {
                writer = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "plugin-audit-writer");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return writer;
        }
    }

    /**
     * 仅测试使用：强制"这次是否走异步写入器"，绕过真实主线程判定。
     *
     * <p>JVM 单测里 {@link #isMainThread()} 恒为假，没有这个入口就永远测不到异步分支。
     * 传 {@code null} 恢复正常判定。</p>
     */
    static void forceAsyncWriterForTest(@Nullable Boolean value) {
        deferForTest = value;
    }

    /**
     * 仅测试使用：阻塞到写入器把当前待写内容真正落盘为止。
     *
     * <p>生产代码不需要它——主线程自己在返回前不会去读文件；需要等的是测试里的断言。</p>
     */
    static void flushForTest() {
        PluginAuditLog log = INSTANCE;
        long deadline = System.currentTimeMillis() + 5000L;
        synchronized (log.writeLock) {
            while (log.queuedText != null || log.drainScheduled) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return;
                try {
                    log.writeLock.wait(left);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static PluginAuditStore store() {
        PluginAuditStore current = store;
        if (current != null) return current;
        synchronized (PluginAuditLog.class) {
            if (store == null) store = PluginAuditStore.get();
            return store;
        }
    }

    /** 仅测试使用：替换落盘后端。传 {@code null} 恢复默认实现。 */
    static void storeForTest(@Nullable PluginAuditStore value) {
        synchronized (PluginAuditLog.class) {
            store = value;
        }
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

    private static JsonObject toJson(Entry entry) {
        JsonObject object = new JsonObject();
        object.addProperty(KEY_PLUGIN_ID, entry.pluginId());
        object.addProperty(KEY_PLUGIN_VERSION, entry.pluginVersion());
        object.addProperty(KEY_EVENT, entry.event());
        object.addProperty(KEY_METHOD, entry.method());
        object.addProperty(KEY_HOST, entry.host());
        object.addProperty(KEY_PATH, entry.path());
        object.addProperty(KEY_STATUS, entry.status());
        object.addProperty(KEY_DURATION, entry.durationMs());
        object.addProperty(KEY_OUTCOME, entry.outcome());
        object.addProperty(KEY_KIND, entry.kind());
        return object;
    }

    /**
     * 解析落盘的一行；不是本类写出的记录返回 {@code null}。
     *
     * <p>{@code kind} 是必填的判别字段：没有它的行（半行、别的东西写进来的行、上一版本的
     * 残留）无法构成一条可追责的记录，只能跳过。</p>
     */
    @Nullable
    private static Entry parse(String line) {
        try {
            if (line == null || line.trim().isEmpty()) return null;
            JsonObject element = PluginAuditStore.object(line);
            if (element == null) return null;
            String kind = string(element, KEY_KIND);
            if (kind == null || kind.isEmpty()) return null;
            return new Entry(string(element, KEY_PLUGIN_ID), string(element, KEY_PLUGIN_VERSION),
                    string(element, KEY_EVENT), string(element, KEY_METHOD),
                    string(element, KEY_HOST), string(element, KEY_PATH),
                    integer(element, KEY_STATUS), number(element, KEY_DURATION),
                    string(element, KEY_OUTCOME), kind);
        } catch (Throwable error) {
            return null;
        }
    }

    @Nullable
    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) return null;
        try {
            return element.getAsString();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static int integer(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) return 0;
        try {
            return element.getAsInt();
        } catch (RuntimeException error) {
            return 0;
        }
    }

    private static long number(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) return 0L;
        try {
            return element.getAsLong();
        } catch (RuntimeException error) {
            return 0L;
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
     * @param kind {@code request} 或 {@code failure}
     */
    public record Entry(String pluginId, String pluginVersion, @Nullable String event,
            @Nullable String method, String host, String path, int status, long durationMs,
            @Nullable String outcome, String kind) {
    }
}
