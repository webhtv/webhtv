package com.fongmi.android.tv.plugin;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 插件宿主：把"清单声明的插件"挂到 {@link PluginEventBus} 上，并守住三条边界。
 *
 * <p>本类是 P2 的中枢，但它刻意**只做路由与门禁，不做脚本实现**——具体引擎由
 * {@link PluginRuntimeFactory} 注入，从而在普通 JVM 单测里可用假运行时完整验证。
 *
 * <p><b>三条不可放松的边界</b>（对应 §5 安全边界）：
 * <ol>
 *   <li><b>权限白名单</b>：插件只收自己 `events[]` 里声明、且被 `permissions[]` 覆盖的事件。
 *       未声明的直接不派发，不存在"默认收全部"。</li>
 *   <li><b>故障隔离</b>：任一插件 init/onEvent/action 抛异常，只记审计 + 标记该插件失败，
 *       **绝不让异常冒泡到宿主生命周期或视图切换路径**。</li>
 *   <li><b>可审计</b>：所有失败与外发请求都落 {@link PluginAuditLog}，且能追到具体插件 id 与版本。</li>
 * </ol>
 *
 * <p><b>为什么"失败即禁用"而不是重试。</b>插件是第三方代码，失败通常是确定性的
 * （清单与实现不符、脚本语法错、依赖缺失）。反复重试只会在主线程上制造抖动，
 * 且让用户看到"时好时坏"的行为。禁用到下一次显式重载为止，语义最清晰。
 */
public final class PluginHost implements PluginEventBus.Listener {

    private final Map<String, LoadedPlugin> plugins;
    private final PluginRuntimeFactory factory;
    private final PluginEventBus bus;
    private final PluginAuditLog audit;
    /**
     * 事件派发线程：脚本求值是同步阻塞调用（最长 {@code QuickJsPluginRuntime.CALL_TIMEOUT_MS}），
     * 绝不能占用调用方线程。单线程是为了让同一插件看到的先后顺序与事件发生顺序一致。
     *
     * <p>懒创建：只有真的从主线程收到过事件才需要它。JVM 单测里 {@link #isMainThread()} 恒假，
     * 事件一律同步走，这条线程永远不会被创建出来。
     */
    @Nullable
    private volatile ExecutorService events;
    /** 已重置：此后任何事件都不再派发，{@link #events()} 也不会再起线程。 */
    private volatile boolean closed;
    private boolean subscribed;

    public PluginHost(PluginRuntimeFactory factory, PluginEventBus bus, PluginAuditLog audit) {
        this.plugins = new ConcurrentHashMap<>();
        this.factory = factory;
        this.bus = bus;
        this.audit = audit;
    }

    /**
     * 装载一批清单。
     *
     * <p><b>先整体销毁旧插件再装新的</b>，顺序与 {@code SubtitleSourceManager.install} 一致：
     * 订阅更新时整批替换，避免新旧插件同时收事件。任一条装载失败不影响其余条目
     * （与字幕源的"整批回滚"不同——插件之间互相独立，没有共享初始化步骤）。
     */
    @NonNull
    public List<LoadedPlugin> load(@Nullable List<PluginManifest> manifests, @Nullable String config) {
        return load(manifests, config, null);
    }

    /**
     * 装载一批清单，并允许**按插件**决定配置。
     *
     * <p>三参数重载而不是改两参数的签名：两参数版本已被 27 处测试调用点使用，
     * 改签名只会换来一堆 {@code null} 字面量。这里保留它，新入口按需覆盖。
     *
     * <p>优先级：{@code resolver} 返回非 {@code null} 就用它的结果，否则回落到 {@code config}。
     * 回落而不是"二者取并"：共享配置与按插件配置混在一起时，谁覆盖谁必须唯一确定。
     */
    @NonNull
    public List<LoadedPlugin> load(@Nullable List<PluginManifest> manifests, @Nullable String config,
            @Nullable ConfigResolver resolver) {
        unloadAll();
        List<LoadedPlugin> loaded = new ArrayList<>();
        if (manifests == null || manifests.isEmpty()) return loaded;
        for (PluginManifest manifest : manifests) {
            if (manifest == null) continue;
            String resolved = resolver == null ? null : resolver.resolve(manifest);
            LoadedPlugin plugin = loadOne(manifest, resolved != null ? resolved : config);
            if (plugin != null) {
                plugins.put(manifest.getId(), plugin);
                loaded.add(plugin);
            }
        }
        attach();
        return Collections.unmodifiableList(loaded);
    }

    /** 按插件构造配置；返回 {@code null} 表示"用共享配置"。 */
    @FunctionalInterface
    public interface ConfigResolver {
        @Nullable
        String resolve(PluginManifest manifest);
    }

    @Nullable
    private LoadedPlugin loadOne(PluginManifest manifest, @Nullable String config) {
        PluginRuntime runtime = factory.create(manifest.getRuntimeApi());
        String payload = config == null ? "" : config;
        try {
            runtime.init(manifest.getId(), manifest.getRuntimeEntry(), payload);
            return new LoadedPlugin(manifest, runtime);
        } catch (Throwable error) {
            // 引擎未实现、脚本语法错、依赖缺失都走这里：记账 + 不装载，绝不上抛。
            audit.recordFailure(manifest.getId(), manifest.getVersion(), null,
                    "runtime.init", "blocked", describe(error));
            destroyQuietly(runtime, manifest.getId());
            return null;
        }
    }

    /** 订阅总线。无插件时不订阅，保证"零插件零开销"。 */
    private void attach() {
        if (subscribed || plugins.isEmpty()) return;
        bus.subscribeAll(this);
        subscribed = true;
    }

    private void detach() {
        if (!subscribed) return;
        bus.unsubscribeAll(this);
        subscribed = false;
    }

    /**
     * 事件入口：按权限过滤后逐个派发。
     *
     * <p>这里就是安全边界 1 的落地点——{@link PluginCapabilityRegistry#allowsEvent} 为假时
     * 该插件根本不会收到事件，且不会留下"尝试过"的噪音（未声明不算违规，只是没订阅）。
     */
    @Override
    public void onEvent(@NonNull String event, @NonNull Map<String, String> payload) {
        if (closed || plugins.isEmpty()) return;
        String json = toJson(payload);
        if (!isMainThread()) {
            deliver(event, json);
            return;
        }
        // 主线程只排队：playback.playing/state 这些事件是从 Player.Listener 同步发出来的，
        // 在这里等脚本就是让每次播放/暂停/seek 都握着主线程最多 5 秒。排队之后"事件发生"与
        // "脚本看到事件"解耦，代价是插件晚一个轮次才知道状态变了，收益是界面永远不被拖住。
        ExecutorService target = events();
        if (target == null) return;
        try {
            target.execute(() -> deliver(event, json));
        } catch (RejectedExecutionException ignored) {
            // 宿主已重置：丢弃这条事件，绝不在调用方线程上补一次同步执行。
        }
    }

    @Nullable
    private ExecutorService events() {
        ExecutorService local = events;
        if (local != null) return local;
        synchronized (this) {
            if (closed) return null;
            if (events == null) {
                events = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "plugin-events");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return events;
        }
    }

    /**
     * 丢弃排队中的事件并停掉派发线程。
     *
     * <p>只在进程级重置（{@code PluginController.reset()}）时调用。刻意不放进 {@link #unloadAll()}：
     * 卸载之后还会重新装载，那时派发线程必须还在；而重置之后这个 host 实例本身要被丢掉。
     * 先停线程再卸载，是为了让队列里那些还指向旧运行时的闭包直接消失，而不是卸载后才被派发到
     * 已销毁的运行时上去。
     */
    synchronized void shutdown() {
        closed = true;
        ExecutorService local = events;
        events = null;
        if (local != null) local.shutdownNow();
    }

    /** 真正逐个派发。执行时重读 {@code plugins}，因此卸载之后排队的事件不会再打到已销毁的实例上。 */
    private void deliver(String event, String json) {
        for (LoadedPlugin plugin : plugins.values()) {
            if (!interested(plugin, event)) continue;
            dispatch(plugin, event, json);
        }
    }

    /**
     * 当前是否在主线程；{@code Looper} 在 JVM 单测下是桩，必须兜住。
     *
     * <p>非主线程直接同步派发而不绕队列：调用方本来就是后台线程，阻塞它没有任何代价，
     * 而绕一趟队列只会让"事件已发出"与"脚本已处理"之间多出一段不可观测的延迟。
     */
    private static boolean isMainThread() {
        try {
            return Looper.myLooper() == Looper.getMainLooper();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 该插件此刻是否应当收到这个事件：未隔离 + 有权限 + 清单声明过。
     *
     * <p>抽成单一判定是因为 {@link #onEvent} 与 {@link #subscribersOf} 曾经各写一遍同样的
     * 三个条件。两处条件一旦漂移，诊断接口会报告"某插件订阅了"，而派发路径却不给它事件——
     * 这种不一致只能靠人眼比对两段 for 循环发现，代价远高于保留一个私有方法。
     *
     * <p>条件顺序保持 {@code allowsEvent} 在前：未声明权限不算违规，只是没订阅，
     * 所以先被权限挡住的插件连清单查询都不做。
     */
    private static boolean interested(LoadedPlugin plugin, String event) {
        if (plugin.failed) return false;
        PluginManifest manifest = plugin.manifest;
        return PluginCapabilityRegistry.allowsEvent(manifest.getPermissions(), event)
                && manifest.subscribesTo(event);
    }

    private void dispatch(LoadedPlugin plugin, String event, String payload) {
        try {
            plugin.runtime.onEvent(plugin.manifest.getId(), event, payload);
        } catch (Throwable error) {
            quarantine(plugin, event, "runtime.onEvent", error);
        }
    }

    /**
     * 触发一个 UI 槽位动作。
     *
     * @return 脚本返回值；插件未加载、已隔离或缺少 `ui.*` 权限时返回 {@code null}
     */
    @Nullable
    public String action(@Nullable String pluginId, @Nullable String action, @Nullable String payload) {
        LoadedPlugin plugin = pluginId == null ? null : plugins.get(pluginId);
        if (plugin == null) {
            // 点到了一个不存在的插件：界面上的按钮还在（例如插件刚被卸载），点击却
            // 悄无声息。这是唯一完全不可见的失败路径，必须留痕，否则「点了没反应」
            // 无从诊断。已隔离的插件不在此列——隔离时已记过审计。
            audit.recordFailure(pluginId, null, null, action, "blocked", "unknown plugin");
            return null;
        }
        if (plugin.failed) return null;
        String slotPermission = PluginManifest.permissionForSlot(slotOf(plugin, action));
        if (slotPermission != null && !PluginCapabilityRegistry.allows(plugin.manifest.getPermissions(), slotPermission)) {
            audit.recordFailure(plugin.manifest.getId(), plugin.manifest.getVersion(), null, action, "blocked",
                    "missing " + slotPermission);
            return null;
        }
        try {
            return plugin.runtime.action(plugin.manifest.getId(), action == null ? "" : action,
                    payload == null ? "" : payload);
        } catch (Throwable error) {
            quarantine(plugin, null, "runtime.action", error);
            return null;
        }
    }

    /**
     * 按 action 找出槽位声明；找不到时返回 {@code null}。
     *
     * <p>这是本类唯一的槽位查找逻辑：{@link #slotOf} 只取它的类型，
     * {@link #slotOfAction} 只取它的完整对象。此前两者各写一遍同样的 for 循环，
     * 一旦清单匹配规则（例如改为大小写不敏感）只改一处，点击校验与点击路由就会分叉。
     */
    @Nullable
    private static PluginManifest.UiSlot findSlot(LoadedPlugin plugin, @Nullable String action) {
        if (action == null) return null;
        for (PluginManifest.UiSlot slot : plugin.manifest.getUiSlots()) {
            if (action.equals(slot.action())) return slot;
        }
        return null;
    }

    /** 该动作属于哪个槽位类型；找不到时返回 {@code null}（表示不额外校验槽位权限）。 */
    @Nullable
    private static String slotOf(LoadedPlugin plugin, @Nullable String action) {
        PluginManifest.UiSlot slot = findSlot(plugin, action);
        return slot == null ? null : slot.slot();
    }

    /**
     * 隔离故障插件：记审计 + 标记失败 + 立即释放运行时。
     *
     * <p>失败后不再派发任何事件，也不再接受动作——避免一个坏插件在每次页面切换时
     * 反复抛异常刷日志。
     */
    private void quarantine(LoadedPlugin plugin, @Nullable String event, String capability, Throwable error) {
        plugin.failed = true;
        audit.recordFailure(plugin.manifest.getId(), plugin.manifest.getVersion(), event, capability,
                "error", describe(error));
        destroyQuietly(plugin.runtime, plugin.manifest.getId());
    }

    /** 卸载全部插件并退订总线。 */
    public void unloadAll() {
        for (LoadedPlugin plugin : plugins.values()) destroyQuietly(plugin.runtime, plugin.manifest.getId());
        plugins.clear();
        detach();
    }

    /** 已成功装载的插件清单。 */
    @NonNull
    public List<PluginManifest> manifests() {
        List<PluginManifest> items = new ArrayList<>();
        for (LoadedPlugin plugin : plugins.values()) items.add(plugin.manifest);
        return Collections.unmodifiableList(items);
    }

    /** 已装载但被隔离的插件 id。 */
    @NonNull
    public Set<String> failedPlugins() {
        LinkedHashSet<String> failed = new LinkedHashSet<>();
        for (LoadedPlugin plugin : plugins.values()) {
            if (plugin.failed) failed.add(plugin.manifest.getId());
        }
        return Collections.unmodifiableSet(failed);
    }

    public boolean isLoaded(@Nullable String pluginId) {
        return pluginId != null && plugins.containsKey(pluginId);
    }

    public int size() {
        return plugins.size();
    }

    /**
     * 收集所有已装载插件声明的 UI 槽位，**带上归属插件 id**，供 UI 宿主渲染与点击路由。
     *
     * <p>必须带 pluginId：槽位被点击时宿主只知道 action 字符串，而 {@link #action(String, String, String)}
     * 需要 pluginId 才能找到对应运行时。早期版本只返回裸 {@code UiSlot}，
     * 结果点击无处可路由——这不是可以事后补的参数，而是槽位的固有属性。
     */
    @NonNull
    public List<PluginSlot> uiSlots() {
        List<PluginSlot> slots = new ArrayList<>();
        for (LoadedPlugin plugin : plugins.values()) {
            if (plugin.failed) continue;
            for (PluginManifest.UiSlot slot : plugin.manifest.getUiSlots()) {
                slots.add(new PluginSlot(plugin.manifest.getId(), slot));
            }
        }
        return Collections.unmodifiableList(slots);
    }

    /**
     * 该插件此刻是否真的持有某项能力。
     *
     * <p>点击路径需要它：宿主准备把剧集上下文塞进动作 payload 之前，必须先确认
     * 这个插件被授予了 {@code plugin.data.episode}。不能因为"槽位存在"就认为"数据可用"——
     * 槽位权限（ui.panel）与数据权限（plugin.data.episode）是两件独立的事。
     */
    public boolean grants(@Nullable String pluginId, @Nullable String permission) {
        if (pluginId == null || permission == null) return false;
        LoadedPlugin plugin = plugins.get(pluginId);
        if (plugin == null) return false;
        return PluginCapabilityRegistry.allows(plugin.manifest.getPermissions(), permission);
    }

    /**
     * 定位某个动作所属的槽位，**带上归属插件 id**。
     *
     * <p>{@link #action(String, String, String)} 已经在权限校验时用私有 {@code slotOf} 找过一遍，
     * 但点击路径需要在动作返回之后知道"这个动作是 panel 还是 floating"，以便决定结果是开面板
     * 还是弹提示。返回 {@code null} 表示没有对应槽位（例如插件直接调 action）。
     */
    @Nullable
    public PluginSlot slotOfAction(@Nullable String pluginId, @Nullable String action) {
        if (pluginId == null) return null;
        LoadedPlugin plugin = plugins.get(pluginId);
        if (plugin == null) return null;
        PluginManifest.UiSlot slot = findSlot(plugin, action);
        return slot == null ? null : new PluginSlot(pluginId, slot);
    }

    /** 收集对某事件感兴趣且仍有权限的插件 id，供诊断与 UI 决策。 */
    @NonNull
    public List<String> subscribersOf(@Nullable String event) {
        if (event == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (LoadedPlugin plugin : plugins.values()) {
            if (interested(plugin, event)) ids.add(plugin.manifest.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    /** 诊断快照：插件 → 状态。 */
    @NonNull
    public Map<String, String> describe() {
        Map<String, String> items = new LinkedHashMap<>();
        for (LoadedPlugin plugin : plugins.values()) {
            items.put(plugin.manifest.getId(), plugin.failed ? "quarantined" : "ready");
        }
        return Collections.unmodifiableMap(items);
    }

    private static void destroyQuietly(PluginRuntime runtime, String pluginId) {
        try {
            runtime.destroy(pluginId);
        } catch (Throwable ignored) {
            // 释放阶段的异常没有可去之处：这里再抛会污染卸载路径。
        }
    }

    private static String describe(Throwable error) {
        if (error == null) return "";
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }

    /**
     * 把 payload 序列化成脚本可读的 JSON。
     *
     * <p>手工拼接而非引入 Gson：payload 只有
     * {@link PluginEventBus#KEY_ACTIVITY} 这一个受控键，值是 Activity 简名。
     * 引入完整 JSON 序列化器会在每个事件上分配对象树，而这是热路径
     * （{@code ui.contentShown} 在每次页面切换时触发）。
     */
    private static String toJson(@NonNull Map<String, String> payload) {
        if (payload.isEmpty()) return "{}";
        StringBuilder json = new StringBuilder(64).append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    /**
     * 带归属的 UI 槽位。
     *
     * @param pluginId 声明该槽位的插件 id
     * @param slot     槽位本身
     */
    public record PluginSlot(String pluginId, PluginManifest.UiSlot slot) {
    }

    /** 一个已装载插件。{@code failed} 是可变的隔离标记。 */
    static final class LoadedPlugin {
        final PluginManifest manifest;
        final PluginRuntime runtime;
        volatile boolean failed;

        LoadedPlugin(PluginManifest manifest, PluginRuntime runtime) {
            this.manifest = manifest;
            this.runtime = runtime;
            this.failed = false;
        }
    }
}
