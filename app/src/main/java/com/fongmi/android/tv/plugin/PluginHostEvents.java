package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import java.util.Map;

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

    /**
     * 播放服务已连接、控制器可用的埋点（{@code PlaybackActivity.onServiceConnected}）。
     *
     * <p>放在连接收口而不是起播点击处：点击只代表用户意图，此刻控制器还可能为 {@code null}，
     * 订阅者拿到事件就去改播放器会直接空指针。
     */
    public static void playbackReady(@Nullable Activity activity) {
        emitActivity(PluginEventBus.EVENT_PLAYBACK_READY, activity);
    }

    /**
     * 播放/暂停翻转的埋点。
     *
     * <p>布尔值按 {@code "true"}/{@code "false"} 传：总线 payload 的类型是 {@code String -> String}，
     * 整条链路都是字符串，这里不引入第二套解析约定。
     */
    public static void playbackPlaying(@Nullable Activity activity, boolean playing) {
        emitWithKey(PluginEventBus.EVENT_PLAYBACK_PLAYING, activity, PluginEventBus.KEY_PLAYING, Boolean.toString(playing));
    }

    /** 引擎状态机变化的埋点；{@code state} 是 {@code Player.STATE_*} 的整数值。 */
    public static void playbackState(@Nullable Activity activity, int state) {
        emitWithKey(PluginEventBus.EVENT_PLAYBACK_STATE, activity, PluginEventBus.KEY_STATE, Integer.toString(state));
    }

    /** 播放页 {@code onPause} 的埋点——页面可见性，不是播放器暂停。 */
    public static void playbackPause(@Nullable Activity activity) {
        emitActivity(PluginEventBus.EVENT_PLAYBACK_PAUSE, activity);
    }

    /** 播放页 {@code onResume} 的埋点——页面可见性，不是播放器起播。 */
    public static void playbackResume(@Nullable Activity activity) {
        emitActivity(PluginEventBus.EVENT_PLAYBACK_RESUME, activity);
    }

    /**
     * 播放页剧集身份变化的埋点。
     *
     * <p>payload 只放宿主已经发布到 {@link PluginEpisodeContext} 的那份 JSON，由调用方传入
     * （宿主侧复用同一个 {@code PluginEpisodePayload} 结果），本类不重新构造：
     * 事件与点击时读到的上下文必须同源，否则插件会读到"事件里的 A + 上下文里的 B"。
     *
     * <p>入参为空时**仍然广播**，只是剧集键给空串——和 {@link #uiContentShown} 同一个取舍：
     * 宁可让订阅者看到"这一页换了集但没有数据"，也不要因为一次构造失败就静默丢事件。
     */
    public static void episodeChanged(@Nullable Activity activity, @Nullable String episodeJson) {
        PluginEventBus bus = PluginEventBus.get();
        if (!bus.hasSubscribers(PluginEventBus.EVENT_EPISODE_CHANGED)) return;
        try {
            // Map.of 不接受 null 值：这里显式归一，Map 构造就不可能失败。
            bus.emit(PluginEventBus.EVENT_EPISODE_CHANGED, Map.of(
                    PluginEventBus.KEY_ACTIVITY, activityName(activity),
                    PluginEventBus.KEY_EPISODE, episodeJson == null ? "" : episodeJson));
        } catch (Throwable ignored) {
            // 埋点是可选增强：任何意外都不许反向打断播放页生命周期（订阅者异常已由 dispatch 吞掉）。
        }
    }

    /**
     * {@link #episodeChanged(Activity, String)} 的对象版重载：调用点传 {@link JsonObject}，
     * 序列化推迟进来。
     *
     * <p><b>先短路、后 {@code toString()}。</b>{@code episode.changed} 的 payload 是最大的一份，
     * 序列化成本（含临时字符串与 GC 压力）不该在没有听众时付；所以这里第一件事仍然是问
     * "有人听吗"，确认有订阅者之后才把对象转成字符串。
     *
     * <p>真正的发射只有 {@link #episodeChanged(Activity, String)} 一处，本方法不复制那段 Map
     * 构造，短路后直接委托过去。代价是委托前会再做一次 {@code hasSubscribers}——那只是一次
     * Map 查询，比让两份发射逻辑各自演化便宜得多。
     */
    public static void episodeChanged(@Nullable Activity activity, @Nullable JsonObject episode) {
        PluginEventBus bus = PluginEventBus.get();
        if (!bus.hasSubscribers(PluginEventBus.EVENT_EPISODE_CHANGED)) return;
        episodeChanged(activity, episode == null ? "" : episode.toString());
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

    /** Activity 简名；入参为空时给空串（沿用既有 emitActivity 的取舍）。 */
    private static String activityName(@Nullable Activity activity) {
        return activity == null ? "" : activity.getClass().getSimpleName();
    }

    /**
     * 带一个额外键的事件：短路必须发生在 Map.of 之前，否则"无订阅者零行为"就名不副实。
     */
    private static void emitWithKey(String event, @Nullable Activity activity, String key, String value) {
        PluginEventBus bus = PluginEventBus.get();
        if (!bus.hasSubscribers(event)) return;
        try {
            bus.emit(event, Map.of(PluginEventBus.KEY_ACTIVITY, activityName(activity), key, value));
        } catch (Throwable ignored) {
            // 同上：payload 构造与派发都不许把异常交回宿主生命周期。
        }
    }

    private static void emitActivity(String event, @Nullable Activity activity) {
        PluginEventBus bus = PluginEventBus.get();
        if (!bus.hasSubscribers(event)) return;
        bus.emitActivity(event, activity == null ? "" : activity.getClass().getSimpleName());
    }
}
