package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户为插件填写的参数（键值 JSON），按插件 id 分键存储。
 *
 * <p>形状照 {@code Setting.getSubtitleSourceEnvironment} 的四件套（读整根 / 写整根 / 读某个插件的子对象 /
 * 写某个插件的子对象），但**刻意不放进 {@code Setting}**：Setting 那套写子对象时直接
 * {@code JsonParser.parseString(value).getAsJsonObject()}，用户填了非法 JSON 就会抛到调用方；
 * 插件参数由用户在管理页里手打，非法输入是常态而不是异常，必须由本类自己判类型并返回布尔结果。
 *
 * <p><b>为什么带 {@link Store} 注入点。</b>JVM 单测里没有 Context，{@code Prefers} 不可用；
 * 与 {@link PluginSourceConfig} 同一做法，测试注入内存实现就够。
 *
 * <p>存储键是 {@code plugin_environment}，与字幕源的 {@code subtitle_source_environment} 并列而不是共用：
 * 两者生命周期不同（字幕源随订阅撤销而清空，插件随卸载而清空），混在一起会互相误删。
 */
public final class PluginEnvironmentStore {

    private static final String KEY = "plugin_environment";
    /** 单个插件的参数上限；用户手打 JSON，超过这个量基本是把脚本整个塞进来了。 */
    static final int MAX_ENVIRONMENT_BYTES = 32 * 1024;
    static final int MAX_PLUGINS = 128;

    /** 存储后端；测试可替换成内存实现。 */
    public interface Store {
        String read();

        void write(String json);
    }

    private static final Store PREFS_STORE = new Store() {
        @Override
        public String read() {
            try {
                return Prefers.getString(KEY);
            } catch (Throwable error) {
                return "";
            }
        }

        @Override
        public void write(String json) {
            try {
                Prefers.put(KEY, json);
            } catch (Throwable ignored) {
            }
        }
    };

    private final Store store;
    private static volatile PluginEnvironmentStore instance;

    private PluginEnvironmentStore(Store store) {
        this.store = store;
    }

    public static PluginEnvironmentStore get() {
        if (instance != null) return instance;
        synchronized (PluginEnvironmentStore.class) {
            if (instance == null) instance = new PluginEnvironmentStore(PREFS_STORE);
            return instance;
        }
    }

    /** 仅测试使用：替换后端并重建单例。传 {@code null} 回到默认实现。 */
    static PluginEnvironmentStore useStoreForTest(@Nullable Store store) {
        synchronized (PluginEnvironmentStore.class) {
            instance = new PluginEnvironmentStore(store == null ? PREFS_STORE : store);
            return instance;
        }
    }

    /** 读取某个插件的参数原始 JSON 文本；没有或解析失败返回空串。 */
    public String get(@Nullable String pluginId) {
        JsonObject environment = parse(pluginId);
        return environment.size() == 0 ? "" : environment.toString();
    }

    /** 读取某个插件的参数；没有或解析失败返回空对象（每次都是新实例，调用方可随意改写）。 */
    public JsonObject getJson(@Nullable String pluginId) {
        return parse(pluginId);
    }

    /**
     * 覆盖写入某个插件的参数。
     *
     * @param json 必须是 JSON 对象文本；空串表示清除该插件的参数
     * @return 写入成功返回 {@code true}；JSON 非法或超限返回 {@code false}，且不改动已有值
     */
    public boolean put(@Nullable String pluginId, @Nullable String json) {
        if (pluginId == null || pluginId.isEmpty()) return false;
        String raw = json == null ? "" : json.trim();
        Map<String, JsonObject> all = readAll();
        if (raw.isEmpty()) {
            if (all.remove(pluginId) == null) return true;
            writeAll(all);
            return true;
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_ENVIRONMENT_BYTES) return false;
        JsonObject parsed;
        try {
            JsonElement element = JsonParser.parseString(raw);
            if (!element.isJsonObject()) return false;
            parsed = element.getAsJsonObject();
        } catch (RuntimeException error) {
            return false;
        }
        if (!all.containsKey(pluginId) && all.size() >= MAX_PLUGINS) return false;
        all.put(pluginId, parsed);
        writeAll(all);
        return true;
    }

    /** 清除某个插件的参数（卸载插件时调用，避免残留 token）。 */
    public void remove(@Nullable String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return;
        Map<String, JsonObject> all = readAll();
        if (all.remove(pluginId) != null) writeAll(all);
    }

    private JsonObject parse(@Nullable String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return new JsonObject();
        JsonObject value = readAll().get(pluginId);
        return value == null ? new JsonObject() : value.deepCopy();
    }

    private Map<String, JsonObject> readAll() {
        Map<String, JsonObject> all = new LinkedHashMap<>();
        String json = store.read();
        if (json == null || json.isEmpty()) return all;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) return all;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) continue;
                if (!entry.getValue().isJsonObject()) continue;
                if (all.size() >= MAX_PLUGINS) break;
                all.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
        return all;
    }

    private void writeAll(Map<String, JsonObject> all) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : all.entrySet()) root.add(entry.getKey(), entry.getValue());
        store.write(root.size() == 0 ? "" : root.toString());
    }
}
