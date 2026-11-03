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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        String activityName = activity == null ? "" : activity.getClass().getSimpleName();
        String payload = buildActionPayload(pluginId, activityName);
        actions.execute(() -> {
            String result = host.action(pluginId, action, payload);
            routeSlotResult(activity, pluginId, action, result);
        });
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
     * 动作返回后按槽位类型分流：panel 打开面板，其余一律走提示预览。
     *
     * <p>为什么不问插件"你要返回 html 还是文本"：插件返回的就是一个字符串，
     * 它属于面板还是提示由**槽位声明**决定——同一段 html 在 floating 上弹提示、
     * 在 panel 上开面板，是有意的可配置行为，不是运行时猜测。
     */
    private void routeSlotResult(@Nullable Activity origin, String pluginId, String action, @Nullable String result) {
        PluginHost.PluginSlot slot = host.slotOfAction(pluginId, action);
        if (slot != null && "panel".equalsIgnoreCase(slot.slot().slot())) {
            showPanel(origin, slot.slot().label(), result);
            return;
        }
        showPreview(result);
    }

    /**
     * 打开插件面板。
     *
     * <p>结果为空时**什么都不做**：一个面板槽位返回空串，通常意味着插件抓取失败，
     * 此时弹一个空白侧滑面板比没有任何反应更让人困惑。失败应当由插件自己
     * 返回 html 里的错误文案来表达。
     */
    private void showPanel(@Nullable Activity origin, @Nullable String title, @Nullable String html) {
        if (html == null || html.isEmpty()) return;
        Handler handler = mainHandler();
        if (handler == null) return;
        handler.post(() -> {
            // 只开在"发起点击的那一页"上：JS 回来时最长已过 5 秒，用户完全可能已经翻到下一页，
            // 把上一页的评论面板盖到新页面上，正是本项目反复要避免的"串台"。
            if (origin == null || origin != current || origin.isFinishing()) return;
            if (!(origin instanceof FragmentActivity fragment)) return;
            try {
                // 关闭时把焦点还给刚才那颗入口 chip：电视端不然会丢焦点，用户得重新按方向键回来。
                PluginPanelDialog.show(fragment, title, html, () -> presenter().focusLastClicked());
            } catch (Throwable ignored) {
                // 面板是可选增强：开不出来也不能影响宿主页面。
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
