package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 原生宿主侧的埋点助手：把 Activity/视图节点翻译成总线事件。
 *
 * <p>P1 只做翻译，不做任何加载或渲染。把这段逻辑独立于 {@code PluginEventBus} 之外，
 * 是因为它依赖 Android 类型（{@link Activity}/{@link ContextWrapper}），而总线本身
 * 保持纯 JVM，便于单元测试直接构造。
 *
 * <p><b>Activity 解析必须包 {@code ContextWrapper} 循环。</b>{@code ProgressLayout} 拿到的
 * {@code getContext()} 通常是 {@code ContextThemeWrapper}，直接 {@code instanceof Activity}
 * 会返回 {@code null}，导致所有视图事件丢失宿主身份。同类实现可参照
 * {@code DialogSafeArea}（它也是循环解包到真正的 Activity）。
 */
public final class PluginHostEvents {

    /** 解包循环的上限，防御异常深或自引用的 ContextWrapper 链。 */
    private static final int MAX_CONTEXT_DEPTH = 16;

    private PluginHostEvents() {
    }

    /** {@code App.onActivityCreated} 的埋点。 */
    public static void activityCreated(@Nullable Activity activity) {
        emitActivity(PluginEventBus.EVENT_ACTIVITY_CREATED, activity);
    }

    /** {@code App.onActivityDestroyed} 的埋点。 */
    public static void activityDestroyed(@Nullable Activity activity) {
        emitActivity(PluginEventBus.EVENT_ACTIVITY_DESTROYED, activity);
    }

    /** {@code App.onActivityResumed} 的埋点。 */
    public static void appForeground() {
        PluginEventBus.get().emit(PluginEventBus.EVENT_APP_FOREGROUND, null);
    }

    /** {@code App.onActivityPaused} 的埋点。 */
    public static void appBackground() {
        PluginEventBus.get().emit(PluginEventBus.EVENT_APP_BACKGROUND, null);
    }

    /**
     * 宿主内容层切到 CONTENT 态的埋点。
     *
     * <p>入参是 {@code ProgressLayout} 自身，它用 {@code getContext()} 反查 Activity。
     * 解析不到 Activity 时**仍然广播**，只是不带身份键——宁可让订阅者看到"某处内容显示了"，
     * 也不要因为一次解包失败就静默丢掉事件（那会让插件行为变得不可解释）。
     */
    public static void uiContentShown(@Nullable View host) {
        PluginEventBus bus = PluginEventBus.get();
        if (!bus.hasSubscribers(PluginEventBus.EVENT_UI_CONTENT_SHOWN)) return;
        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, activityName(host));
    }

    /** 从视图的 Context 反查 Activity 简名；解不出时返回空串。 */
    @Nullable
    public static String activityName(@Nullable View host) {
        if (host == null) return "";
        Activity activity = activity(host.getContext());
        return activity == null ? "" : activity.getClass().getSimpleName();
    }

    /** 循环解包 {@link ContextWrapper} 直到找到 Activity。 */
    @Nullable
    public static Activity activity(@Nullable Context context) {
        Context current = context;
        for (int depth = 0; depth < MAX_CONTEXT_DEPTH && current instanceof ContextWrapper wrapper; depth++) {
            if (current instanceof Activity activity) return activity;
            Context base = wrapper.getBaseContext();
            if (base == current) return null;
            current = base;
        }
        return current instanceof Activity activity ? activity : null;
    }

    private static void emitActivity(String event, @Nullable Activity activity) {
        PluginEventBus bus = PluginEventBus.get();
        if (!bus.hasSubscribers(event)) return;
        bus.emitActivity(event, activity == null ? "" : activity.getClass().getSimpleName());
    }
}
