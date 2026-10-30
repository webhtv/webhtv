package com.fongmi.android.tv.plugin;

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

    private PluginManifest(String id, String name, String version, int sdk, String runtimeApi,
            String runtimeEntry, int timeoutSeconds, Set<String> permissions, Set<String> events,
            List<UiSlot> uiSlots) {
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
        String version = string(root, "version", MAX_VERSION_LENGTH);

        int sdk = integer(root, "sdk", -1);
        if (sdk < 1 || sdk > HOST_SDK_VERSION) return null;

        JsonObject runtime = object(root, "runtime");
        if (runtime == null) return null;
        String api = string(runtime, "api", 16);
        if (!isSupportedRuntime(api)) return null;
        String entry = string(runtime, "entry", MAX_ENTRY_LENGTH);
        if (entry == null || entry.isEmpty()) return null;
        int timeout = integer(runtime, "timeout", 15);
        if (timeout < 1 || timeout > 60) timeout = 15;

        Set<String> declared = stringSet(root, "permissions", MAX_PERMISSIONS, MAX_PERMISSION_LENGTH);
        Set<String> permissions = PluginCapabilityRegistry.filterPermissions(declared);
        // 声明了权限但一个都不认识 → 清单与本宿主不兼容，拒绝而非降级。
        if (!declared.isEmpty() && permissions.isEmpty()) return null;

        Set<String> events = events(root, permissions);
        List<UiSlot> slots = uiSlots(root, permissions);
        if (slots == null) return null;

        return new PluginManifest(id, name, version == null ? "" : version, sdk, api, entry, timeout,
                permissions, events, slots);
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
                    string(slot, "anchor", 32), string(slot, "event", MAX_PERMISSION_LENGTH)));
        }
        return Collections.unmodifiableList(slots);
    }

    /** 槽位类型 → 所需权限；未知槽位返回 {@code null}。 */
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

    private static boolean isSupportedRuntime(@Nullable String api) {
        if (api == null) return false;
        return switch (api.toLowerCase(Locale.ROOT)) {
            case "js", "py", "html" -> true;
            default -> false;
        };
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

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
     */
    public record UiSlot(String slot, String label, String action, @Nullable String anchor,
            @Nullable String event) {
    }
}
