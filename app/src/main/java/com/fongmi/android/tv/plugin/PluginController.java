package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ui.dialog.PluginPanelDialog;
import com.fongmi.android.tv.utils.Util;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 插件系统的进程级入口：装配 {@link PluginHost} 与 {@link PluginUiPresenter}，并跟随 Activity 切换。
 *
 * <p><b>为什么需要一个装配层而不是直接在 `App` 里 new。</b>`App` 已经 30KB+ 且承担
 * 资源/服务/日志等多重职责；插件系统的装配逻辑（谁订总线、何时渲染、何时清理）属于独立关注点，
 * 放在这里可以让 `App` 只保留三行调用。
 *
 * <p><b>默认不加载任何插件。</b>P2d 阶段没有插件来源（订阅解析在 P6），所以这里提供
 * {@link #reload(List)} 由外部显式触发；进程启动时不做任何自动装载。
 * 这保证了"没有插件来源 = 插件系统完全静默"，与 P1 的零开销设计一致。
 *
 * <p>本类不持有 Activity 强引用：{@link #onActivityResumed(Activity)} 只用于即时渲染，
 * 卸载由 {@link #onActivityDestroyed(Activity)} 精确匹配。
 */
public final class PluginController {

    /** 点击回调结果预览的最大长度；再长就不是提示而是日志了。 */
    private static final int MAX_PREVIEW_LENGTH = 160;

    private final PluginRuntimeFactory factory;
    private final PluginHost host;
    private final PluginUiPresenter presenter;
    private final ExecutorService actions;
    private volatile Handler main;

    /**
     * 正在跑的 panel 动作，键是 {@code pluginId + '\0' + action}。
     *
     * <p>存在的唯一理由是抑制连点：面板槽位在动作发起时就把壳弹出来，若用户以为自己没点中
     * 而继续点，第二次点击会再开一个面板、再跑一遍插件（最长 5 秒的 JS 调用）。登记发生在
     * **派发之前**，归还发生在同一次派发出的任务的 {@code finally} 里，所以它是「进行中」
     * 而不是「已点过」的集合；非 panel 槽位不参与（它们的产物是即时 Toast，没有壳可叠）。
     */
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    private volatile Activity current;

    public PluginController() {
        this.factory = new PluginRuntimeFactory();
        this.host = new PluginHost(factory, PluginEventBus.get(), PluginAuditLog.get());
        this.presenter = new PluginUiPresenter();
        this.actions = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "plugin-action");
            thread.setDaemon(true);
            return thread;
        });
        this.presenter.setOnSlotAction(this::onSlotAction);
    }

    /**
     * 槽位点击 → 插件动作。
     *
     * <p><b>必须离开主线程。</b>插件动作会走 JS 运行时，那是一个带 5 秒超时的阻塞调用
     * （{@code QuickJsPluginRuntime.CALL_TIMEOUT_MS}）。直接在主线程调，最长会把界面冻住 5 秒，
     * 这与"插件不能拖累宿主"的前提直接冲突。
     *
     * <p><b>结果怎么呈现由槽位声明决定</b>（见 {@link #routeSlotResult}）：{@code panel} 槽位
     * 打开侧滑面板，其余槽位以 Toast 预览。插件只返回一个字符串，宿主不猜它是 html 还是文本。
     */
    private void onSlotAction(String pluginId, String action) {
        Activity activity = current;
        String key = pendingKey(pluginId, action);
        PluginHost.PluginSlot slot = host.slotOfAction(pluginId, action);
        boolean panel = isPanelSlot(slot);
        // 「这个动作是否已经在跑」必须在**派发之前**判定：同一 panel 动作进行中时再点一次，
        // 既不派发动作（插件 JS 会被跑两遍），也不开第二个壳。登记的唯一执行者是本次调用，
        // 归还的唯一执行者是这次派发出的任务的 finally——别人的任务不能替它归还，否则
        // 第二次点击会提前把第一次的 key 删掉，第三次点击又能开壳换 token、第一次的结果被丢弃。
        if (panel && !pending.add(key)) return;
        String activityName = activity == null ? "" : activity.getClass().getSimpleName();
        String payload = buildActionPayload(pluginId, activityName);
        // 面板槽位先把壳弹出来并显示加载中，再让插件在后台跑：插件单次调用最长 5 秒，
        // 这段空白里若什么都不显示，用户会以为没点上而连点，进而叠出多个面板。
        String token = panel ? showLoadingPanel(activity, slot) : null;
        // panel 判定与标题在这里就定下来，随 token 一起交给结果侧：worker 回来时插件可能已被卸载、
        // 清单也可能已经变了，那时再回查槽位就会把 panel 的结果误判成预览——已经开好的壳永远收不了尾。
        String label = panel ? slot.slot().label() : null;
        Runnable job = () -> {
            try {
                String result = host.action(pluginId, action, payload);
                routeSlotResult(activity, panel, label, result, token);
            } finally {
                // 「进行中」必须在动作结束时立刻归还，否则一次失败会让这颗入口永久失效。
                if (panel) pending.remove(key);
            }
        };
        try {
            actions.execute(job);
        } catch (RejectedExecutionException rejected) {
            // reset() 之后线程池已 shutdown：任务永远不会执行，上面那个 finally 也就永远跑不到。
            // 必须在这里归还 key 并撤掉加载壳，否则壳一直转圈、这颗入口永久失效。
            // 壳可能压根没开（token 为 null），activity 也可能已销毁——dismissLoading 自己兜住这两种。
            if (panel) pending.remove(key);
            PluginPanelDialog.dismissLoading(activity instanceof FragmentActivity fragment ? fragment : null, token);
        }
    }

    private static String pendingKey(String pluginId, String action) {
        return pluginId + '\u0000' + action;
    }

    /** 是不是 panel 槽位：只有它开加载壳，也只有它需要抑制连点。 */
    private static boolean isPanelSlot(@Nullable PluginHost.PluginSlot slot) {
        return slot != null && slot.slot() != null && "panel".equalsIgnoreCase(slot.slot().slot());
    }

    /**
     * panel 槽位在动作发起时先开壳；其余槽位没有面板可开，返回 {@code null}。
     *
     * <p>只对 panel 生效：toolbar / floating 的产物是 Toast 预览，提前开面板反而会弹一个
     * 永远等不到内容的空壳。
     *
     * <p>点击来自控件监听器，恒在主线程；非主线程直接不开壳（宁缺一个加载态，也不跨线程碰视图）。
     *
     * <p>开不出壳时**不碰**「进行中」集合：key 的生命周期归动作自己（派发前登记、动作的
     * {@code finally} 归还）。这里替它归还，等于把刚装上的防连点关掉。
     */
    @Nullable
    private String showLoadingPanel(@Nullable Activity activity, @Nullable PluginHost.PluginSlot slot) {
        if (!isPanelSlot(slot)) return null;
        if (!(activity instanceof FragmentActivity fragment) || !isMainThread()) return null;
        return PluginPanelDialog.showLoading(fragment, slot.slot().label(),
                () -> presenter().focusLastClicked());
    }

    /**
     * 构造动作 payload：{@code activity} 恒在，{@code context} 仅在"有上下文 + 插件有数据权限"时附加。
     *
     * <p>{@code activity} 必须在：插件用它判断"我现在在哪个页面"，也是既有约定的键。
     * {@code context} 之所以要额外查一次权限，是因为槽位权限（ui.panel）与数据权限
     * （plugin.data.episode）互相独立——一个插件可以只要入口不要剧集数据。
     */
    private String buildActionPayload(String pluginId, String activityName) {
        JsonObject payload = new JsonObject();
        payload.addProperty(PluginEventBus.KEY_ACTIVITY, activityName);
        JsonObject context = PluginEpisodeContext.current(activityName);
        if (context != null && host.grants(pluginId, PluginCapabilityRegistry.PERMISSION_EPISODE_DATA)) {
            payload.add("context", context);
        }
        return payload.toString();
    }

    /**
     * 动作返回后分流：panel 打开面板，其余一律走提示预览。
     *
     * <p>为什么不问插件"你要返回 html 还是文本"：插件返回的就是一个字符串，
     * 它属于面板还是提示由**槽位声明**决定——同一段 html 在 floating 上弹提示、
     * 在 panel 上开面板，是有意的可配置行为，不是运行时猜测。
     *
     * <p><b>{@code panel} / {@code label} 由派发侧传下来，这里**不再回查槽位**。</b>本方法跑在
     * worker 线程上，回来时插件可能已被卸载、清单也可能已经换了：那时 {@code host.slotOfAction}
     * 返回 {@code null}，结果就会掉进 {@code showPreview} 分支，而派发时已经开出来的加载壳
     * 从此没人收尾——用户盯着一个永远转圈的面板。
     */
    private void routeSlotResult(@Nullable Activity origin, boolean panel, @Nullable String label,
            @Nullable String result, @Nullable String token) {
        if (panel) {
            fillPanel(origin, token, label, result);
            return;
        }
        showPreview(result);
    }

    /**
     * 把异步结果填进面板：有壳就填壳，没壳就直接开一个有内容的面板。
     *
     * <p><b>空结果必须显式收尾。</b>结果为空（插件抓取失败、被隔离、或运行时抛错）时，
     * 那个一直转圈的面板不能就这么留着——用户会永远等下去。此时让插件自己表达失败更好，
     * 但它这次没给任何 html，只能由宿主撤销这个壳。
     *
     * <p><b>token 为 {@code null} 时不能只是"什么都不做"。</b>它表示派发时那个壳没开成
     * （{@code activity} 不是 {@code FragmentActivity}、不在主线程、{@code isStateSaved}、
     * {@code show()} 抛异常）。此时结果已经回来了，静默丢弃等于用户点了 chip 屏幕上毫无反应；
     * 所以退化成"直接开一个有内容的面板"——也就是没有加载壳时的既有行为。
     *
     * <p>token 由 {@link PluginPanelDialog#fill} 校验：用户可能已经关掉面板或点了别的入口，
     * 把这次的结果灌进去就是串台。
     */
    private void fillPanel(@Nullable Activity origin, @Nullable String token, @Nullable String title,
            @Nullable String html) {
        Handler handler = mainHandler();
        if (handler == null) return;
        handler.post(() -> {
            if (origin == null || origin != current || origin.isFinishing()) return;
            if (!(origin instanceof FragmentActivity fragment)) return;
            try {
                if (html == null || html.isEmpty()) {
                    PluginPanelDialog.dismissLoading(fragment, token);
                    return;
                }
                if (token == null) {
                    PluginPanelDialog.show(fragment, title, html, () -> presenter().focusLastClicked());
                    return;
                }
                PluginPanelDialog.fill(fragment, token, html);
            } catch (Throwable ignored) {
                // 面板是可选增强：填不进去也不能影响宿主页面。
            }
        });
    }

    private void showPreview(@Nullable String result) {
        if (result == null || result.isEmpty()) return;
        Handler handler = mainHandler();
        if (handler == null) return;
        String text = result.length() > MAX_PREVIEW_LENGTH ? result.substring(0, MAX_PREVIEW_LENGTH) + "…" : result;
        handler.post(() -> {
            try {
                Toast.makeText(App.get(), text, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * 主线程 Handler，**惰性创建**。
     *
     * <p>不能在构造器里建：JVM 单元测试下 {@code Looper.getMainLooper()} 是 android.jar
     * 的桩，直接抛 {@code Stub!}，会让整个装配层无法在单测里实例化。惰性 + 兜底既能
     * 保住单测，又能在真机上正常投递。
     */
    @Nullable
    private Handler mainHandler() {
        Handler local = main;
        if (local != null) return local;
        synchronized (this) {
            local = main;
            if (local == null) {
                try {
                    local = new Handler(Looper.getMainLooper());
                } catch (Throwable error) {
                    return null;
                }
                main = local;
            }
        }
        return local;
    }

    /** 注册一个运行时实现。由后续切片（或测试）注入 `js`/`py`/`html`。 */
    public PluginController registerRuntime(String api, PluginRuntime runtime) {
        factory.register(api, runtime);
        return this;
    }

    /**
     * 装载插件清单并渲染当前页面。
     *
     * <p>传空列表等于"卸载全部插件"，这是禁用插件功能的唯一入口。
     */
    public List<PluginHost.LoadedPlugin> reload(@Nullable List<PluginManifest> manifests, @Nullable String config) {
        return reload(manifests, config, null);
    }

    /**
     * 装载插件并按插件注入配置；{@code resolver} 为 {@code null} 时行为等同两参数版本。
     *
     * <p>之所以多这一层而不是让调用方自己拼 config：配置里的参数需要先做占位符替换，
     * 而替换要读用户填的环境变量——那是宿主知识，不该泄漏到每个 reload 调用点。
     */
    public List<PluginHost.LoadedPlugin> reload(@Nullable List<PluginManifest> manifests, @Nullable String config,
            @Nullable PluginHost.ConfigResolver resolver) {
        try {
            List<PluginHost.LoadedPlugin> loaded = host.load(manifests, config, resolver);
            renderCurrent();
            return loaded;
        } catch (Throwable error) {
            // 装配层绝不能把异常抛给 App 的生命周期回调。
            PluginAuditLog.get().recordFailure("<controller>", "", null, "reload", "error",
                    error.getClass().getSimpleName() + ": " + error.getMessage());
            return List.of();
        }
    }

    /** Activity 可见：把已有插件槽位渲染到新页面。 */
    public void onActivityResumed(Activity activity) {
        if (activity == null) return;
        current = activity;
        renderCurrent();
    }

    /**
     * 页面形态变化后重算入口可见性（全屏进出、分屏切换等）。
     *
     * <p><b>为什么必须有一个独立入口。</b>宿主页面把方向与尺寸变化都声明进了
     * {@code configChanges}，系统不会重建 Activity，{@link #onActivityResumed} 也就不会
     * 再被调用；而 {@link PluginUiPresenter#attach} 在沉浸态下会选择"整批不挂"。缺了这个
     * 入口，chip 一旦隐藏就再也不会回来——那比一直显示更糟。
     *
     * <p>只对"当前页"生效：形态变化发生在哪一页，就该只重算哪一页。
     */
    public void refreshRender(@Nullable Activity activity) {
        if (activity == null || activity != current) return;
        renderCurrent();
    }

    /** Activity 销毁：精确匹配本控制器持有的页面时清理控件并清空引用。 */
    public void onActivityDestroyed(Activity activity) {
        if (activity == null || activity != current) return;
        detachOnMainThread();
        PluginEpisodeContext.clear(activity.getClass().getSimpleName());
        current = null;
    }

    /**
     * 把插件槽位渲染到当前页面。
     *
     * <p><b>为什么整段渲染都必须回到主线程。</b>渲染的两个动作都碰视图：{@code attach} 走
     * {@code decorView.addView}，{@code detach} 走 {@code parent.removeView}——二者都只能在建树
     * 线程上执行。要把它们一起挡住，守卫就必须落在方法最外层；只挡 attach 会留下一条真实可达的
     * 故障路径：用户在插件管理页关掉最后一个插件时，装载在后台 worker 上返回空清单，
     * {@code host.size() == 0} 那一支随即在后台线程 removeView，抛出的
     * {@code CalledFromWrongThreadException} 被 {@link #reload} 的 {@code catch (Throwable)}
     * 吞成一条"reload 失败"审计。用户看到的是"关掉插件后 chip 还在、开关像没生效"，
     * 日志里却指向一个根本没坏的装载过程——这是最难排查的组合。
     *
     * <p><b>为什么主线程仍然同步执行。</b>{@code onActivityResumed} 与会话内 reload 都在主线程，
     * 它们依赖"调用返回时控件已在"（真机测试同样经 {@code runOnMainSync} 落在这条路径上）。
     * 投递只作为跨线程的补救，不是常态。
     */
    private void renderCurrent() {
        if (current == null) return;
        if (isMainThread()) {
            renderNow();
            return;
        }
        Handler handler = mainHandler();
        if (handler == null) return;
        handler.post(this::renderNow);
    }

    /**
     * 真正触碰视图的那一段，**只允许在主线程调用**。
     *
     * <p>自己重读 {@code current} 与 {@code host.size()} 而不接受入参：从投递到执行之间，
     * 用户可能已经翻到别的页面，也可能又把插件启用了——沿用投递前的快照会把控件挂到错误的页面上。
     */
    private void renderNow() {
        Activity target = current;
        if (target == null) return;
        if (host.size() == 0) {
            presenter.detach();
            return;
        }
        presenter.attach(target, host.uiSlots(), isLeanback());
    }

    /**
     * 把 {@code detach()} 收口到主线程：主线程同步执行，其余线程只投递，投递不出去就放弃。
     *
     * <p><b>为什么这不是「顺手加个守卫」，而是一条崩溃防线。</b>{@code detach()} 内部是
     * {@code parent.removeView(view)}。在错误线程上调它并不是「抛一次异常、状态不变」：
     * {@link android.view.ViewGroup} 会先改动自己的子视图数组、再走到
     * {@code requestLayout()} 才被 {@code ViewRootImpl} 拦下，也就是说那次删除在异常抛出之前
     * 已经把一个半成品状态留在了视图树里。真机上用户看到的不是这条异常，而是**主线程下一次
     * measure/layout 时 DecorView 抛 {@code NullPointerException}**——栈里全是 framework 帧，
     * 看不到任何插件代码，归因成本极高。
     *
     * <p><b>为什么取不到 Handler 时直接放弃而不退化成后台 removeView。</b>放弃的代价是控件留在
     * 已经要销毁的页面上（页面本身随 Activity 一起消失）；退化的代价是把上面的半成品状态写进
     * 仍存活的视图树。前者可接受，后者不可接受。JVM 单测下 {@code Looper} 是桩、
     * {@link #mainHandler()} 必然返回 {@code null}，走的正是这条放弃分支——这也解释了为什么
     * 纯 JVM 单测永远抓不到这个缺陷。
     *
     * <p>投递时捕获的是<b>投递那一刻的 final 局部引用</b>，不是字段、也不是 {@code this}：
     * {@link #reset()} 在投递之后立刻把静态单例置空，捕获字段的 lambda 会在主线程上读到
     * {@code null} 或读到另一个实例。
     */
    private void detachOnMainThread() {
        PluginUiPresenter target = presenter;
        if (isMainThread()) {
            target.detach();
            return;
        }
        Handler handler = mainHandler();
        if (handler == null) return;
        handler.post(target::detach);
    }

    /** 当前是否在主线程；{@code Looper} 在 JVM 单测下是桩，必须兜住。 */
    private static boolean isMainThread() {
        try {
            return Looper.myLooper() == Looper.getMainLooper();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLeanback() {
        try {
            return Util.isLeanback();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public PluginHost host() {
        return host;
    }

    public PluginUiPresenter presenter() {
        return presenter;
    }

    /** 全部插件的诊断快照，供设置页或日志使用。 */
    public String diagnostics() {
        StringBuilder text = new StringBuilder();
        text.append("runtimes=").append(factory.describe());
        text.append(" plugins=").append(host.describe());
        text.append(" failed=").append(host.failedPlugins());
        text.append(" ui=").append(presenter.attachedCount());
        text.append(" audit=").append(PluginAuditLog.get().size());
        return text.toString();
    }

    /** 进程级单例。首次访问才创建，未装插件时无额外成本。 */
    private static volatile PluginController instance;

    public static PluginController get() {
        PluginController local = instance;
        if (local == null) {
            synchronized (PluginController.class) {
                local = instance;
                if (local == null) {
                    local = new PluginController();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** 测试或「重置插件系统」用：卸载全部并丢弃单例。 */
    public static void reset() {
        synchronized (PluginController.class) {
            if (instance != null) {
                instance.host.shutdown();
                instance.host.unloadAll();
                instance.detachOnMainThread();
                instance.actions.shutdownNow();
                instance = null;
            }
        }
    }

    /** 供测试注入点击，避免依赖真实点击事件。包内可见，不扩大公开面。 */
    void dispatchSlotAction(String pluginId, String action) {
        onSlotAction(pluginId, action);
    }

    /** 供 `App` 使用：避免在生命周期回调里写 try/catch。必须 public——调用方在父包。 */
    public static void safeOnResumed(@Nullable Activity activity) {
        try {
            get().onActivityResumed(activity);
        } catch (Throwable ignored) {
        }
    }

    public static void safeOnDestroyed(@Nullable Activity activity) {
        try {
            get().onActivityDestroyed(activity);
        } catch (Throwable ignored) {
        }
    }
}
