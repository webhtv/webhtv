package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 已导入插件包的持久化配置：插件系统第一次拥有真实来源。
 *
 * <p><b>为什么配置只记"目录 + 元信息"，不存包体。</b>脚本与清单已经落在
 * {@code files/plugins/<id>/}（见 {@link PluginPackageImporter}），配置负责"记得哪些包存在、
 * 哪些启用"。两者分离后，删配置不会在盘上留垃圾（导入器管目录），删目录也不会让配置崩掉
 * （{@link #toManifests()} 逐条跳过坏条目）。
 *
 * <p><b>为什么每个读入口都 sanitize，而不是信任盘上数据。</b>SharedPreferences 是明文可改的，
 * 且旧版本可能写过今天已不合法的条目。任何一条脏数据（缺 id、缺目录、id 形如 {@code ".."}）
 * 都会在装载期被放大成一次文件系统访问，所以必须在读入时就丢弃，而不是使用前再判断。
 *
 * <p><b>为什么用 Gson 的树 API 而不是反射绑定 record。</b>D8 会把 record 脱糖成普通类，
 * 反射字段名在设备上不可靠；{@link PluginManifest} 已经用同一套 {@code JsonParser} 树 API，
 * 这里追随它既能跨 JVM/设备一致，也让本类不依赖 {@code App.gson()}（单测里 App 不存在）。
 *
 * <p>本类除 {@code Prefers} 外不碰任何 Android API，可在普通 JVM 单测里完整覆盖。
 */
public final class PluginSourceConfig {

    /** SharedPreferences 键。设置页按同一键读取，不得更改。 */
    public static final String KEY = "plugin_source_config";

    /**
     * 一个已导入的插件包。
     *
     * @param id         清单 id，同时是 {@code files/plugins/} 下的目录名
     * @param directory  包目录绝对路径（装载时据此找 manifest.json）
     * @param enabled    是否参与装载；禁用只影响装载，不删目录
     * @param importedAt 导入时间，仅用于界面排序与排查
     */
    public record Entry(String id, String name, String version, String runtimeApi,
                        String directory, boolean enabled, long importedAt) {
    }

    /** 包内清单文件名：导入器与装载共用同一常量，避免两边拼错。 */
    static final String MANIFEST_NAME = "manifest.json";

    /** 存储格式版本；将来不兼容变更时用它区分旧数据。 */
    private static final int STORAGE_VERSION = 1;
    private static final int MAX_ID_LENGTH = 96;
    /** 条目数上限：配置可被外部写入，没有上界就等于允许用脏数据拖死界面。 */
    private static final int MAX_ENTRIES = 128;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * 持久化后端抽象。
     *
     * <p>真机走 SharedPreferences；JVM 单测注入内存实现——{@code Prefers} 依赖
     * {@code Init.context()}，单测里没有 Context，直接调会在桩代码上抛异常。
     */
    interface Store {
        String read();

        void write(String json);
    }

    private static final Store PREFS_STORE = new Store() {

        @Override
        public String read() {
            // Prefers.getString 自身已兜异常，但 getPrefers() 在极端情况下也可能抛，
            // 配置读取失败必须退化成"没有插件"，不能让 App 启动中断。
            try {
                return Prefers.getString(KEY);
            } catch (Throwable error) {
                return "";
            }
        }

        @Override
        public void write(String json) {
            // Prefers.put 不兜异常（它直连 SharedPreferences.edit()），这里必须兜：
            // 保存失败只意味着"下次启动回到旧配置"，不该影响本次导入结果。
            try {
                Prefers.put(KEY, json);
            } catch (Throwable ignored) {
            }
        }
    };

    private final Map<String, Entry> entries;
    private final Store store;

    private PluginSourceConfig(Store store) {
        this.entries = new LinkedHashMap<>();
        this.store = store;
        load();
    }

    private static volatile PluginSourceConfig instance;

    /** 进程级单例；首次访问才读盘。永不为 {@code null}。 */
    public static PluginSourceConfig get() {
        PluginSourceConfig local = instance;
        if (local == null) {
            synchronized (PluginSourceConfig.class) {
                local = instance;
                if (local == null) {
                    local = new PluginSourceConfig(PREFS_STORE);
                    instance = local;
                }
            }
        }
        return local;
    }

    /** 测试注入：换存储并按新存储重建单例（传 {@code null} 恢复真机存储）。包内可见，不扩大公开面。 */
    static PluginSourceConfig useStoreForTest(@Nullable Store store) {
        synchronized (PluginSourceConfig.class) {
            instance = new PluginSourceConfig(store == null ? PREFS_STORE : store);
            return instance;
        }
    }

    /**
     * id 是否可以安全地当成目录名。
     *
     * <p>{@code PluginManifest} 的 {@code [A-Za-z0-9._-]+} 允许 {@code ".."}，而
     * {@code new File(pluginsRoot, "..")} 会把写入点抬到 plugins 的父目录。纯点 id 必须单独拒掉。
     */
    static boolean isSafeId(@Nullable String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) return false;
        if (!ID_PATTERN.matcher(id).matches()) return false;
        return !id.replace(".", "").isEmpty();
    }

    /** 不可变快照，顺序为插入顺序（先导入的在前），保证界面列表稳定。 */
    public synchronized List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /** 只取启用的条目，顺序同上。 */
    public List<Entry> enabledEntries() {
        List<Entry> enabled = new ArrayList<>();
        for (Entry entry : entries()) if (entry.enabled()) enabled.add(entry);
        return Collections.unmodifiableList(enabled);
    }

    /** 按 id 查找；不存在返回 {@code null}。 */
    @Nullable
    public synchronized Entry find(@Nullable String id) {
        return id == null ? null : entries.get(id);
    }

    /** 按 id 覆盖或插入。同一 id 不会出现两条。 */
    public synchronized void put(@Nullable Entry entry) {
        Entry safe = sanitize(entry);
        if (safe == null) return;
        entries.put(safe.id(), safe);
    }

    /** 切换启用状态；id 不存在时静默无操作（界面可能比配置旧一帧）。 */
    public synchronized void setEnabled(@Nullable String id, boolean enabled) {
        Entry entry = id == null ? null : entries.get(id);
        if (entry == null || entry.enabled() == enabled) return;
        entries.put(id, new Entry(entry.id(), entry.name(), entry.version(), entry.runtimeApi(),
                entry.directory(), enabled, entry.importedAt()));
    }

    /** 移除条目（不删目录——目录归导入器管，避免这里出现第二种删除语义）。 */
    public synchronized void remove(@Nullable String id) {
        if (id != null) entries.remove(id);
    }

    /** 落盘当前快照。 */
    public void save() {
        String json;
        synchronized (this) {
            json = encode();
        }
        store.write(json);
    }

    /**
     * 把启用条目变成可装载的清单。
     *
     * <p>逐条读 {@code <directory>/manifest.json}：{@code Path.read(File)} 在文件缺失或读取失败时
     * 返回 {@code ""} 而不是 {@code null}（catvod/utils/Path.java:135-141），因此空串与
     * {@code parse} 失败必须一起当成"跳过这条"，否则一个坏包会拖垮整批装载。
     */
    public List<PluginManifest> toManifests() {
        List<PluginManifest> manifests = new ArrayList<>();
        for (Entry entry : enabledEntries()) {
            String json;
            try {
                json = Path.read(new File(entry.directory(), MANIFEST_NAME));
            } catch (Throwable error) {
                continue;
            }
            if (json == null || json.isEmpty()) continue;
            PluginManifest manifest = PluginManifest.parse(json);
            if (manifest == null) continue;
            manifests.add(manifest);
        }
        return Collections.unmodifiableList(manifests);
    }

    private synchronized void load() {
        entries.clear();
        for (Entry entry : parse(store.read())) entries.put(entry.id(), entry);
    }

    private static List<Entry> parse(@Nullable String json) {
        String raw = json == null ? "" : json.trim();
        if (raw.isEmpty()) return List.of();
        try {
            JsonElement root = JsonParser.parseString(raw);
            if (!root.isJsonObject()) return List.of();
            JsonArray array = root.getAsJsonObject().getAsJsonArray("entries");
            if (array == null) return List.of();
            List<Entry> parsed = new ArrayList<>();
            for (JsonElement element : array) {
                Entry entry = sanitize(element);
                if (entry == null) continue;
                parsed.add(entry);
                // 超限即停：脏数据不该让本类把内存吃满。
                if (parsed.size() >= MAX_ENTRIES) break;
            }
            return parsed;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @Nullable
    private static Entry sanitize(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        String id = string(object, "id");
        if (!isSafeId(id)) return null;
        String directory = string(object, "directory");
        // 没有目录的条目永远装不上，留着只会让界面显示一个点不开的插件。
        if (directory == null) return null;
        String name = string(object, "name");
        String version = string(object, "version");
        String api = string(object, "runtimeApi");
        return new Entry(id, name == null ? id : name, version == null ? "" : version,
                api == null ? "" : api, directory, bool(object, "enabled"), number(object, "importedAt"));
    }

    @Nullable
    private static Entry sanitize(@Nullable Entry entry) {
        if (entry == null) return null;
        String id = trim(entry.id());
        if (!isSafeId(id)) return null;
        String directory = trim(entry.directory());
        if (directory == null) return null;
        String name = trim(entry.name());
        String version = trim(entry.version());
        String api = trim(entry.runtimeApi());
        return new Entry(id, name == null ? id : name, version == null ? "" : version,
                api == null ? "" : api, directory, entry.enabled(), entry.importedAt());
    }

    private synchronized String encode() {
        JsonObject root = new JsonObject();
        root.addProperty("version", STORAGE_VERSION);
        JsonArray array = new JsonArray();
        for (Entry entry : entries.values()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.id());
            item.addProperty("name", entry.name());
            item.addProperty("version", entry.version());
            item.addProperty("runtimeApi", entry.runtimeApi());
            item.addProperty("directory", entry.directory());
            item.addProperty("enabled", entry.enabled());
            item.addProperty("importedAt", entry.importedAt());
            array.add(item);
        }
        root.add("entries", array);
        return root.toString();
    }

    @Nullable
    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        return trim(object.get(key).getAsString());
    }

    @Nullable
    private static String trim(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean bool(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return false;
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static long number(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return 0L;
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
