package com.fongmi.android.tv.plugin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;

/**
 * 审计记录的编解码层：内存里的 {@link PluginAuditLog.Entry} 与落盘的一行 JSON 之间的双向转换。
 *
 * <p><b>为什么单独成文件。</b>{@link PluginAuditLog} 原先同时承担缓冲、序列化、落盘调度三件事，
 * 已经撞上 docs §7:310 的 500 行门禁。序列化是其中唯一无状态、无锁、无 IO 的一段，
 * 整段搬走后行为一个字不变，而原类的行数与职责一起降下来。</p>
 *
 * <p><b>键名的唯一来源。</b>读写两侧都用下面这组 {@code KEY_*}，所以"写出去的键名"和"读回来的
 * 键名"不可能漂移。反过来说，任何新增字段必须同时在 {@link #toJson} 与 {@link #parse} 出现，
 * 只加一侧就等于"写了却读不回来"的静默黑洞。</p>
 */
final class PluginAuditCodec {

    /** 落盘行里的字段名；与 {@link PluginAuditLog.Entry} 的 record 组件一一对应。 */
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
    private static final String KEY_BODY_SIZE = "bodySize";

    private PluginAuditCodec() {
    }

    /** 把内存环形缓冲序列化成一行一条 JSON 的文本；空缓冲返回空串（等于"删除文件"）。 */
    @NonNull
    static String serialize(Collection<PluginAuditLog.Entry> entries) {
        if (entries.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (PluginAuditLog.Entry entry : entries) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(toJson(entry).toString());
        }
        return builder.toString();
    }

    private static JsonObject toJson(PluginAuditLog.Entry entry) {
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
        object.addProperty(KEY_BODY_SIZE, entry.bodySize());
        return object;
    }

    /**
     * 解析落盘的一行；不是本类写出的记录返回 {@code null}。
     *
     * <p>{@code kind} 是必填的判别字段：没有它的行（半行、别的东西写进来的行、上一版本的
     * 残留）无法构成一条可追责的记录，只能跳过。</p>
     *
     * <p>{@code bodySize} 相反是**可缺**的：加这个字段之前写下的快照里没有它，那些记录仍然
     * 是有效证据，缺失或非法一律回落 {@code 0}——旧快照必须照读，否则升级一次就把历史全丢了。</p>
     */
    @Nullable
    static PluginAuditLog.Entry parse(String line) {
        try {
            if (line == null || line.trim().isEmpty()) return null;
            JsonObject element = PluginAuditStore.object(line);
            if (element == null) return null;
            String kind = string(element, KEY_KIND);
            if (kind == null || kind.isEmpty()) return null;
            return new PluginAuditLog.Entry(string(element, KEY_PLUGIN_ID),
                    string(element, KEY_PLUGIN_VERSION), string(element, KEY_EVENT),
                    string(element, KEY_METHOD), string(element, KEY_HOST),
                    string(element, KEY_PATH), integer(element, KEY_STATUS),
                    number(element, KEY_DURATION), string(element, KEY_OUTCOME), kind,
                    number(element, KEY_BODY_SIZE));
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
}
