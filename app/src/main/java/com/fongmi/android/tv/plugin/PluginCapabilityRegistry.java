package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件能力注册表：能力 → 所需权限 → 允许的事件。单一事实源。
 *
 * <p>形状照 {@code web/WebThemeCapabilityRegistry}：那个类已证明「集中白名单 + 生成式兼容矩阵」
 * 在本项目可维护，且有漂移测试兜底。插件侧沿用同一形状，避免出现第三套权限模型。
 *
 * <p><b>为什么权限是硬门禁而不是软检查。</b>{@code docs/universal-webhome-theme-design.md:142}
 * 已明文约束「不在第一阶段开放任意插件代码或无边界的 net.request」。D2 又裁定
 * `net.request` 不做域名白名单——于是权限声明与审计日志一起构成唯一的准入边界，
 * 两者都不可裁剪。任何未在清单 {@code permissions[]} 登记的能力调用一律拒绝，
 * 不提供"默认允许"分支。
 *
 * <p>本类在 P2a 阶段只做判定，不执行任何脚本，也不触碰 Android API。
 */
public final class PluginCapabilityRegistry {

    /** 插件可接收的事件白名单。宿主只对声明过的事件派发。 */
    public static final String PERMISSION_EVENTS = "plugin.events";
    /** 读取当前剧集上下文（剧名/年份/演职员等，脱敏后的只读字段）。 */
    public static final String PERMISSION_EPISODE_DATA = "plugin.data.episode";
    /** 发起外部网络请求。D2：无域名白名单，但每次调用必须落审计。 */
    public static final String PERMISSION_NET_REQUEST = "net.request";
    /** 新增悬浮控件（手机端主入口）。 */
    public static final String PERMISSION_UI_FLOATING = "ui.floating";
    /** 在宿主既有界面中插入控件（电视版主入口）。 */
    public static final String PERMISSION_UI_TOOLBAR = "ui.toolbar";
    /** 弹出面板（侧滑/底部弹层）。 */
    public static final String PERMISSION_UI_PANEL = "ui.panel";
    /** 贡献独立整页（D1 已允许的 html 插件形态）。 */
    public static final String PERMISSION_UI_PAGE = "ui.page";

    private static final List<Capability> CAPABILITIES = List.of(
            new Capability(PERMISSION_EVENTS, Set.of(
                    PluginEventBus.EVENT_APP_FOREGROUND,
                    PluginEventBus.EVENT_APP_BACKGROUND,
                    PluginEventBus.EVENT_ACTIVITY_CREATED,
                    PluginEventBus.EVENT_ACTIVITY_DESTROYED,
                    PluginEventBus.EVENT_UI_CONTENT_SHOWN,
                    PluginEventBus.EVENT_PLAYBACK_READY,
                    PluginEventBus.EVENT_PLAYBACK_PLAYING,
                    PluginEventBus.EVENT_PLAYBACK_STATE,
                    PluginEventBus.EVENT_PLAYBACK_PAUSE,
                    PluginEventBus.EVENT_PLAYBACK_RESUME,
                    // 全屏态是纯生命周期信号，payload 只有一个布尔值，不含剧集业务数据，
                    // 因此由 plugin.events 覆盖即可，不需要 plugin.data.episode。
                    PluginEventBus.EVENT_PLAYBACK_FULLSCREEN)),
            // 剧集身份变化（episode.changed）的 payload 携带片名/集号等业务数据，它是一条
            // 业务数据事件而非纯生命周期信号，因此只由 plugin.data.episode 覆盖——只持
            // plugin.events 的插件收不到它。真正由两条能力共同覆盖的是 ui.contentShown：
            // events 侧拿到事件本身，data.episode 侧才拿得到 context。
            // 切勿为了让 plugin.events 收到"换集了"而把 episode.changed 加进
            // PERMISSION_EVENTS：那等于让只申请生命周期权限的插件拿到完整剧集数据，是越权。
            new Capability(PERMISSION_EPISODE_DATA, Set.of(
                    PluginEventBus.EVENT_UI_CONTENT_SHOWN,
                    PluginEventBus.EVENT_EPISODE_CHANGED)),
            new Capability(PERMISSION_NET_REQUEST, Set.of()),
            new Capability(PERMISSION_UI_FLOATING, Set.of(PluginEventBus.EVENT_UI_CONTENT_SHOWN)),
            new Capability(PERMISSION_UI_TOOLBAR, Set.of(PluginEventBus.EVENT_UI_CONTENT_SHOWN)),
            new Capability(PERMISSION_UI_PANEL, Set.of()),
            new Capability(PERMISSION_UI_PAGE, Set.of()));

    private static final Map<String, Capability> BY_PERMISSION = indexByPermission();

    /**
     * 事件 → 覆盖它的权限（反向索引）。
     *
     * <p>{@link #permissionForEvent} 原先每次线性扫描 {@link #CAPABILITIES}，而
     * {@link #allowsEvent} 在事件热路径（每次页面切换的 {@code ui.contentShown}）上都要经过它。
     * 索引在类初始化时建一次，之后是 O(1) 查表。
     *
     * <p><b>为什么是 putIfAbsent 而不是 put。</b>一个事件可能被多条能力覆盖
     * （{@code ui.contentShown} 就同时属于 events / data.episode / ui.floating / ui.toolbar）。
     * 旧实现返回的是"遍历顺序上第一条覆盖它的权限"，索引必须复刻这一语义，
     * 否则 {@link #allowsEvent} 的判定会随清单顺序悄悄变化。
     */
    private static final Map<String, String> BY_EVENT = indexByEvent();

    private PluginCapabilityRegistry() {
    }

    /** 该权限是否真实存在（清单里写了不认识的权限会被静默丢弃，而不是报错）。 */
    public static boolean isKnownPermission(@Nullable String permission) {
        return permission != null && BY_PERMISSION.containsKey(permission);
    }

    /** 过滤清单声明的权限，只保留本宿主认识的。未知项静默丢弃以保持向前兼容。 */
    public static Set<String> filterPermissions(@Nullable Set<String> declared) {
        if (declared == null || declared.isEmpty()) return Collections.emptySet();
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String permission : declared) {
            if (isKnownPermission(permission)) filtered.add(permission);
        }
        return Collections.unmodifiableSet(filtered);
    }

    /** 判断某插件是否有权接收指定事件。未声明对应权限时一律 {@code false}。 */
    public static boolean allowsEvent(@Nullable Set<String> grantedPermissions, @Nullable String event) {
        // 同样先查注册表：自造事件名不得因为出现在 permissions 派生集合里而通过。
        if (!isKnownPermission(permissionForEvent(event))) return false;
        if (grantedPermissions == null || grantedPermissions.isEmpty() || event == null) return false;
        for (String permission : grantedPermissions) {
            Capability capability = BY_PERMISSION.get(permission);
            if (capability != null && capability.events.contains(event)) return true;
        }
        return false;
    }

    /**
     * 判断某插件是否有权执行某个能力。
     *
     * <p>这是 {@code net.request} 与 UI 槽位的统一入口；没有"默认允许"。
     *
     * <p><b>必须先查注册表再查集合。</b>只做 {@code grantedPermissions.contains(permission)}
     * 会让任意自造字符串通过——只要清单把它写进 permissions[] 就成立。这条曾经是真实缺陷：
     * 单元测试用 {@code "net.unlimited"}（一个本宿主根本不认识的能力）证明了它会放行。
     * 因此 {@code isKnownPermission} 是硬前置，注册表之外的一切恒为拒绝。
     */
    public static boolean allows(@Nullable Set<String> grantedPermissions, @Nullable String permission) {
        if (grantedPermissions == null || grantedPermissions.isEmpty()) return false;
        if (!isKnownPermission(permission)) return false;
        return grantedPermissions.contains(permission);
    }

    /** 反向查找：覆盖该事件的最小权限；没有任何权限覆盖时返回 {@code null}。 */
    @Nullable
    private static String permissionForEvent(@Nullable String event) {
        return event == null ? null : BY_EVENT.get(event);
    }

    /** 某权限允许的事件集合；未知权限返回空集。 */
    public static Set<String> eventsFor(@Nullable String permission) {
        Capability capability = permission == null ? null : BY_PERMISSION.get(permission);
        return capability == null ? Collections.emptySet() : capability.events;
    }

    /** 全部已知权限，供生成兼容矩阵与设置页展示。 */
    public static Set<String> knownPermissions() {
        return Collections.unmodifiableSet(BY_PERMISSION.keySet());
    }

    private static Map<String, Capability> indexByPermission() {
        LinkedHashMap<String, Capability> index = new LinkedHashMap<>();
        for (Capability capability : CAPABILITIES) index.put(capability.permission, capability);
        return Collections.unmodifiableMap(index);
    }

    /** 建事件反向索引，保持 {@link #CAPABILITIES} 的遍历顺序（先到先得）。 */
    private static Map<String, String> indexByEvent() {
        LinkedHashMap<String, String> index = new LinkedHashMap<>();
        for (Capability capability : CAPABILITIES) {
            for (String event : capability.events) index.putIfAbsent(event, capability.permission);
        }
        return Collections.unmodifiableMap(index);
    }

    private record Capability(String permission, Set<String> events) {
    }
}
