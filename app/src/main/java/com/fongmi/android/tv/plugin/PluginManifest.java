package com.fongmi.android.tv.plugin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 插件清单：声明式描述一个插件能接收什么事件、能占什么 UI 槽位、要什么权限。
 *
 * <p>形状照 {@code web/WebThemeManifest}（schemaVersion + minSdk + permissions + 长度上限 +
 * 严格 id 校验），因为那套字段语义已过两版迭代验证，且它的失败方式（解析失败即整体拒绝、
 * 不降级）正是插件需要的安全姿态。
 *
 * <p><b>失败即拒绝，不做部分加载。</b>一个字段越界的清单整体返回 {@code null}，
 * 而不是"能读多少读多少"——半加载的插件会产生无法解释的行为，比不加载更糟。
 *
 * <p><b>本类纯 JVM。</b>不 import 任何 {@code android.*}，因此可以被单元测试直接覆盖，
 * 也保证了解析层无法偷偷访问设备能力。
 */
public final class PluginManifest {

    /** 当前清单协议版本；不匹配即拒绝。 */
    public static final int SCHEMA_VERSION = 1;
    /** 本宿主实现的插件 SDK 版本；清单 {@code sdk} 必须 ≤ 本值。 */
    public static final int HOST_SDK_VERSION = 1;
    /** 清单体积硬上限，防止超大 JSON 拖慢解析。 */
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;

    static final String PROTOCOL = "webhtv-plugin";

    private static final int MAX_ID_LENGTH = 96;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_VERSION_LENGTH = 32;
    private static final int MAX_ENTRY_LENGTH = 512;
    private static final int MAX_PERMISSIONS = 32;
    private static final int MAX_PERMISSION_LENGTH = 64;
    private static final int MAX_EVENTS = 32;
    private static final int MAX_UI_SLOTS = 16;
    private static final int MAX_LABEL_LENGTH = 32;
    private static final int MAX_SURFACES = 8;
    private static final int MAX_SURFACE_LENGTH = 16;

    private static final String ID_PATTERN = "[A-Za-z0-9._-]+";

    private final String id;
    private final String name;
    private final String version;
    private final int sdk;
    private final String runtimeApi;
    private final String runtimeEntry;
    private final int timeoutSeconds;
    private final Set<String> permissions;
    private final Set<String> events;
    private final List<UiSlot> uiSlots;
    /** 参数模板：值里可含 ``${KEY}`` 占位符，由用户填写的环境变量替换后交给运行时。 */
    private final JsonObject params;

    private PluginManifest(String id, String name, String version, int sdk, String runtimeApi,
            String runtimeEntry, int timeoutSeconds, Set<String> permissions, Set<String> events,
            List<UiSlot> uiSlots, JsonObject params) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.sdk = sdk;
        this.runtimeApi = runtimeApi;
        this.runtimeEntry = runtimeEntry;
        this.timeoutSeconds = timeoutSeconds;
        this.permissions = permissions;
        this.events = events;
        this.uiSlots = uiSlots;
        this.params = params;
    }

    /**
     * 解析清单。
     *
     * @return 解析成功返回实例；任何字段不合法都返回 {@code null}（调用方负责记录原因）
     */
    @Nullable
    public static PluginManifest parse(@Nullable String json) {
        String raw = json == null ? "" : json.trim();
        if (raw.isEmpty()) return null;
        // 先按字节判体积，再解析；否则超大 JSON 会先吃掉内存。
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) return null;
        try {
            JsonElement root = JsonParser.parseString(raw);
            if (!root.isJsonObject()) return null;
            return from(root.getAsJsonObject());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static PluginManifest from(JsonObject root) {
        if (!PROTOCOL.equals(string(root, "protocol", 64))) return null;
        if (integer(root, "version", -1) != SCHEMA_VERSION) return null;

        String id = string(root, "id", MAX_ID_LENGTH);
        if (id == null || !id.matches(ID_PATTERN)) return null;
        String name = string(root, "name", MAX_NAME_LENGTH);
        if (name == null) name = id;
        // version 是协议版本（上面已按整数校验），pluginVersion 才是插件自身的版本；
        // 两者不得复用同一个键——复用会让插件版本恒等于协议版本号，升级与重导入无从区分。
        String protocolVersion = string(root, "version", MAX_VERSION_LENGTH);
        String pluginVersion = string(root, "pluginVersion", MAX_VERSION_LENGTH);
        // 旧清单不写 pluginVersion：回退成协议版本字符串，避免界面出现空版本列。
        String version = pluginVersion != null ? pluginVersion : protocolVersion;

        int sdk = integer(root, "sdk", -1);
        if (sdk < 1 || sdk > HOST_SDK_VERSION) return null;

        JsonObject runtime = object(root, "runtime");
        if (runtime == null) return null;
        String api = string(runtime, "api", 16);
        if (!isSupportedRuntime(api)) return null;
        String entry = string(runtime, "entry", MAX_ENTRY_LENGTH);
        if (entry == null || entry.isEmpty()) return null;
        int timeout = integer(runtime, "timeout", PluginNetBridge.DEFAULT_TIMEOUT_SECONDS);
        if (timeout < 1 || timeout > PluginNetBridge.MAX_TIMEOUT_SECONDS) {
            timeout = PluginNetBridge.DEFAULT_TIMEOUT_SECONDS;
        }

        Set<String> declared = stringSet(root, "permissions", MAX_PERMISSIONS, MAX_PERMISSION_LENGTH);
        Set<String> permissions = PluginCapabilityRegistry.filterPermissions(declared);
        // 声明了权限但一个都不认识 → 清单与本宿主不兼容，拒绝而非降级。
        if (!declared.isEmpty() && permissions.isEmpty()) return null;

        Set<String> events = events(root, permissions);
        List<UiSlot> slots = uiSlots(root, permissions);
        if (slots == null) return null;

        // 参数模板是插件自己定义的任意 JSON 对象，宿主不认识也不校验其中的键；
        // 深拷贝一份，避免调用方拿到解析树后原地改动。
        JsonObject template = object(root, "params");
        JsonObject params = template == null ? new JsonObject() : template.deepCopy();

        return new PluginManifest(id, name, version == null ? "" : version, sdk, api, entry, timeout,
                permissions, events, slots, params);
    }

    /**
     * 解析 {@code events[]}：只保留既有权限覆盖的事件。
     *
     * <p>没权限却声明事件不算错误——直接丢弃该事件即可，这样插件可以在清单里保留
     * "将来想要"的事件名，而不会因为少了权限就整体装不上。
     */
    private static Set<String> events(JsonObject root, Set<String> permissions) {
        LinkedHashSet<String> granted = new LinkedHashSet<>();
        JsonArray array = array(root, "events");
        if (array == null) return Collections.unmodifiableSet(granted);
        int count = 0;
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || count >= MAX_EVENTS) break;
            String event = element.getAsString().trim();
            count++;
            if (event.isEmpty() || event.length() > MAX_PERMISSION_LENGTH) continue;
            if (PluginCapabilityRegistry.allowsEvent(permissions, event)) granted.add(event);
        }
        return Collections.unmodifiableSet(granted);
    }

    /**
     * 解析 {@code ui[]} 槽位声明。
     *
     * <p>槽位类型决定所需权限：{@code floating → ui.floating}、{@code toolbar → ui.toolbar}、
     * {@code panel → ui.panel}、{@code page → ui.page}。缺权限的槽位被丢弃（不影响其他槽位）。
     */
    @Nullable
    private static List<UiSlot> uiSlots(JsonObject root, Set<String> permissions) {
        JsonArray array = array(root, "ui");
        if (array == null) return Collections.emptyList();
        List<UiSlot> slots = new ArrayList<>();
        int count = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject() || count >= MAX_UI_SLOTS) break;
            count++;
            JsonObject slot = element.getAsJsonObject();
            String type = string(slot, "slot", 16);
            String required = permissionForSlot(type);
            if (required == null || !permissions.contains(required)) continue;
            String label = string(slot, "label", MAX_LABEL_LENGTH);
            String action = string(slot, "action", MAX_PERMISSION_LENGTH);
            if (action == null || action.isEmpty()) continue;
            slots.add(new UiSlot(type, label == null ? "" : label, action,
                    string(slot, "anchor", 32), string(slot, "event", MAX_PERMISSION_LENGTH),
                    surfaces(slot)));
        }
        return Collections.unmodifiableList(slots);
    }

    /**
     * 解析槽位的 {@code surfaces}：这个槽位只在哪些页面上出现。
     *
     * <p><b>不写这个键 = 空集 = 所有页面都显示。</b>这是向后兼容的命门：SDK 1 早期的清单全都没有
     * 这个键，若把空集理解成"哪都不显示"，所有既有插件会一次性集体消失。
     *
     * <p><b>不做白名单校验。</b>宿主不认识的值永远匹配不上真实页面，效果天然就是"永不显示"，
     * 这个语义由消费侧自然产生。在这里拒绝整份清单，只会让插件作者一个拼写错误就整个装不上。
     *
     * <p>元素必须是小写字符串：非字符串、空串、超过 {@link #MAX_SURFACE_LENGTH} 的元素被逐个丢弃，
     * 不做任何报错；超过 {@link #MAX_SURFACES} 之后的部分不再读取（防止恶意长数组耗尽解析时间）。
     */
    private static Set<String> surfaces(JsonObject slot) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JsonArray array = array(slot, "surfaces");
        if (array == null) return Collections.unmodifiableSet(values);
        int count = 0;
        for (JsonElement element : array) {
            if (count >= MAX_SURFACES) break;
            count++;
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            // 统一小写：清单写 "Detail" 与写 "detail" 必须落到同一个页面。
            String value = element.getAsString().trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || value.length() > MAX_SURFACE_LENGTH) continue;
            values.add(value);
        }
        return Collections.unmodifiableSet(values);
    }

    /**
     * 槽位类型 → 所需权限；未知槽位返回 {@code null}。
     *
     * <p><b>这张表没有重复来源，故保留在清单侧。</b>{@link PluginCapabilityRegistry} 只登记
     * "权限 → 允许的事件"，并不记录"槽位类型 → 权限"；宿主侧
     * {@code PluginHost.action} 也是向本方法要这个映射。若要抽走它，得先在注册表里
     * 新造一份槽位表，那只是把一个真源换成另一个真源，并没有消除重复。
     */
    @Nullable
    static String permissionForSlot(@Nullable String slot) {
        if (slot == null) return null;
        return switch (slot.toLowerCase(Locale.ROOT)) {
            case "floating" -> PluginCapabilityRegistry.PERMISSION_UI_FLOATING;
            case "toolbar" -> PluginCapabilityRegistry.PERMISSION_UI_TOOLBAR;
            case "panel" -> PluginCapabilityRegistry.PERMISSION_UI_PANEL;
            case "page" -> PluginCapabilityRegistry.PERMISSION_UI_PAGE;
            default -> null;
        };
    }

    /**
     * 该清单声明的运行时引擎是否为本宿主认识的引擎。
     *
     * <p><b>委托 {@link PluginRuntimeFactory#isKnownApi}，本类不再自带 case 列表。</b>
     * 此前这里与注册表的 {@code KNOWN_APIS} 各写一遍 {@code js/py/html}：注册表新增
     * 一个引擎时，清单侧会继续拒绝它，表现为"引擎已支持但清单装不上"，而两处都不报错。
     * 引擎集合只有一个真源，就在注册表里。
     *
     * <p>注意这里判定的是"已知"而不是"已注册"：{@code py}/{@code html} 在当前版本
     * 会装进 {@link PluginRuntimeFactory.UnsupportedRuntime}，由 {@code PluginHost.loadOne}
     * 的 init 失败路径给出准确审计，而不是在解析清单这一步就被悄悄判为"清单不合法"。
     */
    private static boolean isSupportedRuntime(@Nullable String api) {
        return PluginRuntimeFactory.isKnownApi(api);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * 插件自身版本：清单写了合法的 {@code pluginVersion} 就返回它，否则回退成协议版本字符串。
     *
     * <p>回退是有意的：旧清单把 {@code version} 当插件版本用，界面依赖"版本列永不为空"。
     */
    public String getVersion() {
        return version;
    }

    public int getSdk() {
        return sdk;
    }

    public String getRuntimeApi() {
        return runtimeApi;
    }

    public String getRuntimeEntry() {
        return runtimeEntry;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<String> getEvents() {
        return events;
    }

    public List<UiSlot> getUiSlots() {
        return uiSlots;
    }

    /**
     * 参数模板（清单 {@code params} 的深拷贝）。
     *
     * <p>返回拷贝而不是内部引用：调用方会在占位符替换后就地改写树，不能污染清单。
     */
    public JsonObject getParams() {
        return params.deepCopy();
    }

    /** 该插件是否有权接收指定事件（清单已按权限过滤过，这里再兜一层）。 */
    public boolean subscribesTo(@Nullable String event) {
        return event != null && events.contains(event);
    }

    private static String string(JsonObject object, String key, int maxLength) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty() || value.length() > maxLength) return null;
        return value;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Nullable
    private static JsonObject object(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) return null;
        return root.getAsJsonObject(key);
    }

    @Nullable
    private static JsonArray array(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) return null;
        return root.getAsJsonArray(key);
    }

    private static Set<String> stringSet(JsonObject root, String key, int maxItems, int maxLength) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JsonArray array = array(root, key);
        if (array == null) return values;
        int count = 0;
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || count >= maxItems) break;
            count++;
            String value = element.getAsString().trim();
            if (!value.isEmpty() && value.length() <= maxLength) values.add(value);
        }
        return values;
    }

    /**
     * UI 槽位声明。
     *
     * @param slot   槽位类型：floating / toolbar / panel / page
     * @param label  控件显示文案（电视端建议短中文）
     * @param action 点击后插件的动作标识
     * @param anchor 锚点提示（如 {@code bottom-end}、{@code before:episode-list}）
     * @param event  该槽位生效所依赖的事件
     * @param surfaces 该槽位只在哪些页面上出现（home / detail / playback / other）；
     *                 <b>空集表示所有页面都显示</b>，清单不写 {@code surfaces} 就是这个默认值
     */
    public record UiSlot(String slot, String label, String action, @Nullable String anchor,
            @Nullable String event, @NonNull Set<String> surfaces) {

        /**
         * 五参便捷构造器：等价于"所有页面都显示"。
         *
         * <p>留着它是因为加 {@code surfaces} 组件会把三个既有测试（插件渲染相关的
         * PluginUiPresenterTest、PluginUiPresenterDeviceTest、PluginPanelLeanbackFocusDeviceTest）
         * 的 5 参构造全打红，而它们验证的是渲染不是清单解析；让它们继续只表达自己关心的事。
         */
        public UiSlot(String slot, String label, String action, @Nullable String anchor,
                @Nullable String event) {
            this(slot, label, action, anchor, event, Set.of());
        }
    }
}
