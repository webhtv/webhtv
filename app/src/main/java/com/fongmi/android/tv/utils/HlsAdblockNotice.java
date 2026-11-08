package com.fongmi.android.tv.utils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Playback-session debounce for repeated live-playlist refreshes, plus the shared notice text. */
public final class HlsAdblockNotice {

    private static final long WINDOW_MS = 30_000L;
    private static final int MAX_ENTRIES = 64;
    /** Display granularity of the removed-ad duration: one tenth of a second. */
    private static final long TENTHS_PER_SECOND = 10L;
    private static final long TENTHS_PER_MINUTE = 60L * TENTHS_PER_SECOND;
    private static final Map<String, Long> RECENT = new LinkedHashMap<>();

    private HlsAdblockNotice() {}

    public static synchronized boolean shouldNotify(String playlistUrl, long nowMs) {
        String key = playlistUrl == null ? "" : playlistUrl;
        Long previous = RECENT.get(key);
        if (previous != null && nowMs - previous < WINDOW_MS) return false;
        RECENT.put(key, nowMs);
        while (RECENT.size() > MAX_ENTRIES) RECENT.remove(RECENT.keySet().iterator().next());
        return true;
    }

    /**
     * Builds the single user-visible ad-block confirmation used by every HLS channel.
     *
     * <p>The notice always reports the number of removed ad segments and, whenever the removed
     * duration is known, the total ad time as well. The duration is included for both the
     * structured rule path and the legacy fallback path, because a user who only sees a segment
     * count cannot tell whether one 2-second bumper or a 3-minute ad break was removed.</p>
     */
    public static String message(int removedSegments, double removedDurationSec) {
        String count = "已跳过 " + Math.max(0, removedSegments) + " 个广告片段";
        String duration = durationText(removedDurationSec);
        return duration.isEmpty() ? count : count + "，总广告时长 " + duration;
    }

    /**
     * Formats a removed-ad duration as seconds below one minute and as minutes at or above it.
     *
     * <p>The value is rounded to the tenth-of-a-second display granularity <em>before</em> the unit
     * is chosen, exactly like {@link AdBlockTimeFormatter#formatSeconds(double)}: a 59.96-second
     * removal is reported as {@code 1 分钟}, never as the self-contradicting {@code 60.0 秒}.</p>
     *
     * @return the text to show, or an empty string when no duration is displayable (zero, negative,
     *         non-finite, or smaller than the display granularity) so that the caller falls back to
     *         the segment count instead of showing a misleading {@code 0.0 秒}
     */
    public static String durationText(double seconds) {
        long tenths = displayableTenths(seconds);
        if (tenths == 0L) return "";
        if (tenths < TENTHS_PER_MINUTE) {
            return String.format(Locale.US, "%.1f 秒", tenths / (double) TENTHS_PER_SECOND);
        }
        long total = Math.round(tenths / (double) TENTHS_PER_SECOND);
        long minutes = total / 60L;
        long remainder = total % 60L;
        return remainder == 0L ? minutes + " 分钟" : minutes + " 分 " + remainder + " 秒";
    }

    private static long displayableTenths(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0d) return 0L;
        return Math.round(seconds * TENTHS_PER_SECOND);
    }
}
