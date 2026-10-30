package com.fongmi.android.tv.plugin;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Util;

import java.util.List;

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

    private final PluginRuntimeFactory factory;
    private final PluginHost host;
    private final PluginUiPresenter presenter;

    private volatile Activity current;

    public PluginController() {
        this.factory = new PluginRuntimeFactory();
        this.host = new PluginHost(factory, PluginEventBus.get(), PluginAuditLog.get());
        this.presenter = new PluginUiPresenter();
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
        try {
            List<PluginHost.LoadedPlugin> loaded = host.load(manifests, config);
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
                instance = null;
            }
        }
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
