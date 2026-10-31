package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Util;

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
     * <p>结果以 Toast 预览：P4 之前没有豆瓣面板可开，若点击毫无可见反馈，
     * 这条链路（槽位→点击→运行时→回填）就无法手工验证。Toast 零布局、零资源，
     * 是让插件作者与验收者确认接线成立的最小代价。
     */
    private void onSlotAction(String pluginId, String action) {
        Activity activity = current;
        String payload = "{\"activity\":\"" + (activity == null ? "" : activity.getClass().getSimpleName()) + "\"}";
        actions.execute(() -> {
            String result = host.action(pluginId, action, payload);
            showPreview(result);
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
        presenter.detach();
        current = null;
    }

    private void renderCurrent() {
        Activity activity = current;
        if (activity == null) return;
        if (host.size() == 0) {
            presenter.detach();
            return;
        }
        presenter.attach(activity, host.uiSlots(), isLeanback());
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
                instance.host.unloadAll();
                instance.presenter.detach();
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
