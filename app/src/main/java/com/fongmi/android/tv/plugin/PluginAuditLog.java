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
 * <p><b>内存环形缓冲 + 可选落盘。</b>P2a 只做内存记账（无 IO、无权限、可单测）；
 * 落盘接入 {@code CacheModuleId.PLUGIN_SCRIPTS} 之外的新模块是后续切片的事。
 * 这样本阶段既满足审计要求，又不引入文件系统副作用。
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
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /** 某个插件的全部记录，用于"追责到具体插件"。 */
    @NonNull
    public List<Entry> entriesFor(@Nullable String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return Collections.emptyList();
        List<Entry> matched = new ArrayList<>();
        synchronized (lock) {
            for (Entry entry : entries) {
                if (pluginId.equals(entry.pluginId)) matched.add(entry);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /** 清空全部记录（用户主动清理或应用级重置）。 */
    public void clear() {
        synchronized (lock) {
            entries.clear();
        }
    }

    private void append(Entry entry) {
        synchronized (lock) {
            entries.addLast(entry);
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
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
