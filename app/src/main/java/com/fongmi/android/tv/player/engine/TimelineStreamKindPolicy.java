package com.fongmi.android.tv.player.engine;

import java.util.concurrent.TimeUnit;

/**
 * 时长未知时的点播/直播判定。
 *
 * <p>四个内核原先都用「时长小于 1 分钟即直播」判定，而 {@code C.TIME_UNSET} 是负数、恒小于该阈值，
 * 于是还没吐出时长的点播会被当成直播：倍速、字幕与选集入口消失，续播位置与片尾探测失效。
 * 未知时长只是「还不知道」，不能作为直播的证据。
 *
 * <p>恰好等于阈值时按点播处理：{@code isLive} 用 {@code <}、{@code isVod} 用 {@code >=}，
 * 两个判定保持互补，不再出现既不直播也不点播的第三态。
 */
public final class TimelineStreamKindPolicy {

    /** 沿用四个内核原有的 1 分钟门槛，短于它的已知时长仍按直播处理（短片、循环流）。 */
    public static final long LEGACY_LIVE_DURATION_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(1);

    private TimelineStreamKindPolicy() {
    }

    public static boolean isLive(long durationMs, boolean liveManifest) {
        if (liveManifest) return true;
        if (isUnknown(durationMs)) return false;
        return durationMs < LEGACY_LIVE_DURATION_THRESHOLD_MS;
    }

    public static boolean isVod(long durationMs, boolean liveManifest) {
        if (liveManifest) return false;
        if (isUnknown(durationMs)) return true;
        return durationMs >= LEGACY_LIVE_DURATION_THRESHOLD_MS;
    }

    private static boolean isUnknown(long durationMs) {
        return durationMs <= 0;
    }
}
