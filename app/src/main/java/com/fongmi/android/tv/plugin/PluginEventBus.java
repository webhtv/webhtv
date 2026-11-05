package com.fongmi.android.tv.plugin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 插件事件总线：原生宿主把生命周期节点广播给订阅者。
 *
 * <p><b>P1 阶段没有任何插件加载能力。</b>本类只负责"宿主能说话了"，订阅者为空，
 * 因此对现有行为零副作用——这是刻意的：先证明事件在正确时机、带正确身份触发，
 * 再接运行时。任何插件运行时都不该出现在这一层。
 *
 * <p><b>为什么事件带宿主身份而不是按页面命名。</b>最初的设计想让
 * {@link com.fongmi.android.tv.ui.custom.ProgressLayout#showContent()} 直接派发
 * {@code detail.ready}，但该类被 {@code HomeActivity}、{@code FileActivity}、
 * {@code HistoryActivity}、{@code KeepActivity}、{@code TypeFragment} 共用，
 * 那样会让首页也误报"详情页就绪"。所以底层只广播 {@code ui.contentShown(activity)}，
 * 由订阅者按 Activity 类型自己过滤。这样既不必改 mobile/leanback 两份
 * {@code VideoActivity}，也不会产生语义错误的公共事件。
 *
 * <p><b>线程约束。</b>所有 {@link #emit} 调用都发生在主线程（生命周期回调与视图状态切换
 * 天然在主线程），订阅者据此可以安全触碰视图。本类不做线程切换，也不允许订阅者在回调里
 * 阻塞——宿主节点不能被插件拖慢。
 */
public final class PluginEventBus {

    /** 应用进入前台（{@code App.onActivityResumed}）。 */
    public static final String EVENT_APP_FOREGROUND = "app.foreground";
    /** 应用失去前台（{@code App.onActivityPaused}）。 */
    public static final String EVENT_APP_BACKGROUND = "app.background";
    /** 任一 Activity 创建（{@code App.onActivityCreated}）。 */
    public static final String EVENT_ACTIVITY_CREATED = "activity.created";
    /** 任一 Activity 销毁（{@code App.onActivityDestroyed}）。 */
    public static final String EVENT_ACTIVITY_DESTROYED = "activity.destroyed";
    /**
     * 宿主内容层切到 CONTENT 态。
     *
     * <p>payload 的 {@code activity} 是宿主 Activity 的类名简名；订阅者必须据此过滤，
     * 不能假设它来自详情页。
     */
    public static final String EVENT_UI_CONTENT_SHOWN = "ui.contentShown";

    /**
     * 播放页的播放服务已连接、控制器可用。
     *
     * <p>从点集到真正出画面之间还有取流、绑定 Service 与建控制器三段异步，
     * 订阅者需要区分"用户进了播放页"（{@link #EVENT_PLAYBACK_RESUME}）和
     * "播放器已经能接受指令"。这个事件报告的是后者。
     */
    public static final String EVENT_PLAYBACK_READY = "playback.ready";
    /** 播放/暂停态翻转，payload 带 {@link #KEY_PLAYING}。 */
    public static final String EVENT_PLAYBACK_PLAYING = "playback.playing";
    /** 引擎状态机变化，payload 带 {@link #KEY_STATE}。 */
    public static final String EVENT_PLAYBACK_STATE = "playback.state";
    /**
     * 播放页 {@code onPause}：用户离开了播放页。
     *
     * <p>它不是"播放器暂停"——后台播放、画中画与跳转都可能让播放继续，
     * 需要播放器语义的订阅者应当看 {@link #EVENT_PLAYBACK_PLAYING}。两者分成两个事件，
     * 是因为把页面可见性混进播放态后，订阅者再也无法分辨这两种完全不同的情况。
     */
    public static final String EVENT_PLAYBACK_PAUSE = "playback.pause";
    /** 播放页 {@code onResume}：用户回到了播放页。语义同 {@link #EVENT_PLAYBACK_PAUSE}。 */
    public static final String EVENT_PLAYBACK_RESUME = "playback.resume";
    /**
     * 播放页认领的剧集身份变了。
     *
     * <p>payload 带 {@link #KEY_EPISODE}，内容就是同一时刻发布到
     * {@link PluginEpisodeContext} 的那份 JSON。两者同源是刻意的：订阅者若从事件里读到 A、
     * 点击时又从上下文槽读到 B，评论就会串台，而这类错误在日志里完全没有痕迹。
     */
    public static final String EVENT_EPISODE_CHANGED = "episode.changed";

    /** payload 中承载宿主 Activity 简名的键。 */
    public static final String KEY_ACTIVITY = "activity";
    /** payload 中承载 {@code playback.playing} 布尔值的键，取值只有 {@code "true"}/{@code "false"}。 */
    public static final String KEY_PLAYING = "playing";
    /** payload 中承载引擎状态机整数的键，取值是 {@code Player.STATE_*} 的十进制字符串。 */
    public static final String KEY_STATE = "state";
    /** payload 中承载剧集上下文 JSON 的键，与 {@link PluginEpisodeContext} 的同一份快照同源。 */
    public static final String KEY_EPISODE = "episode";

    private static final PluginEventBus INSTANCE = new PluginEventBus();

    private final Map<String, List<Listener>> listeners;
    private final List<Listener> anyListeners;

    private PluginEventBus() {
        this.listeners = new ConcurrentHashMap<>();
        this.anyListeners = new CopyOnWriteArrayList<>();
    }

    public static PluginEventBus get() {
        return INSTANCE;
    }

    /**
     * 订阅单个事件。{@code event} 为空或 {@code listener} 为 {@code null} 时忽略。
     *
     * @return 是否真的登记成功
     */
    public boolean subscribe(@Nullable String event, @Nullable Listener listener) {
        if (event == null || event.isEmpty() || listener == null) return false;
        listeners.computeIfAbsent(event, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return true;
    }

    /** 取消单个事件的订阅。重复取消是安全的。 */
    public void unsubscribe(@Nullable String event, @Nullable Listener listener) {
        if (event == null || event.isEmpty() || listener == null) return;
        List<Listener> registered = listeners.get(event);
        if (registered == null) return;
        registered.remove(listener);
        if (registered.isEmpty()) listeners.remove(event, registered);
    }

    /** 订阅全部事件（诊断或通用审计用）。 */
    public boolean subscribeAll(@Nullable Listener listener) {
        if (listener == null) return false;
        anyListeners.add(listener);
        return true;
    }

    public void unsubscribeAll(@Nullable Listener listener) {
        if (listener == null) return;
        anyListeners.remove(listener);
    }

    /**
     * 宿主在构造 payload 前先问一句，避免无订阅者时白做 {@code Map.of}。
     *
     * <p>这就是"空订阅零行为"的前置门：P1 接入后所有既有页面都走这条 early-return。
     */
    public boolean hasSubscribers(@Nullable String event) {
        if (!anyListeners.isEmpty()) return true;
        if (event == null || event.isEmpty()) return false;
        List<Listener> registered = listeners.get(event);
        return registered != null && !registered.isEmpty();
    }

    /**
     * 广播事件。订阅方抛出的异常被吞掉——绝不允许插件反向打断宿主生命周期或视图切换。
     *
     * @return 实际成功收到事件的订阅者数量
     */
    public int emit(@Nullable String event, @Nullable Map<String, String> payload) {
        if (!hasSubscribers(event)) return 0;
        Map<String, String> safe = payload == null ? Map.of() : payload;
        int delivered = 0;
        List<Listener> registered = event == null ? null : listeners.get(event);
        if (registered != null) {
            for (Listener listener : registered) {
                if (dispatch(listener, event, safe)) delivered++;
            }
        }
        for (Listener listener : anyListeners) {
            if (dispatch(listener, event == null ? "" : event, safe)) delivered++;
        }
        return delivered;
    }

    /** 便捷广播：只带一个 Activity 身份键。无订阅者时不构造 payload。 */
    public int emitActivity(@Nullable String event, @Nullable String activitySimpleName) {
        if (!hasSubscribers(event)) return 0;
        if (activitySimpleName == null || activitySimpleName.isEmpty()) return emit(event, null);
        return emit(event, Map.of(KEY_ACTIVITY, activitySimpleName));
    }

    /** 清空全部订阅，仅用于测试或应用级重置。 */
    public void clear() {
        listeners.clear();
        anyListeners.clear();
    }

    private static boolean dispatch(Listener listener, String event, Map<String, String> payload) {
        try {
            listener.onEvent(event, payload);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 订阅回调。实现方必须快速返回，不得阻塞主线程。 */
    public interface Listener {
        void onEvent(@NonNull String event, @NonNull Map<String, String> payload);
    }
}
