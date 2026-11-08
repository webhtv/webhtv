package com.fongmi.android.tv.player.exo;

/**
 * Attributes EXO dropped-frame reports to playing time instead of a running total.
 *
 * <p>media3 only reports dropped frames when the renderer's accumulator reaches
 * {@code maxDroppedFramesToNotify} (50) — or unconditionally from
 * {@code MediaCodecVideoRenderer.onStopped()}. A pause therefore settles whatever
 * tail accumulated since {@code onStarted()}, however small, so a bare running
 * total jumps exactly when playback stops and reads as "pausing drops frames".
 * The frames were dropped while playing; only the report arrived at the pause.
 *
 * <p>This class keeps the same totals the caller already had, plus the played
 * time that makes a rate comparable across sessions, plus a separate count of the
 * reports that arrived while playback was not advancing. The rate is what quality
 * verdicts should read; the totals and the paused-settled split stay available as
 * diagnostics evidence.
 */
public final class DroppedFrameAccounting {

    private long totalDroppedFrames;
    private long pauseSettledDroppedFrames;
    private int pauseSettledReports;
    private long playTimeMs;
    private long playingSinceMs;
    private boolean playing;

    synchronized void reset() {
        totalDroppedFrames = 0;
        pauseSettledDroppedFrames = 0;
        pauseSettledReports = 0;
        playTimeMs = 0;
        playingSinceMs = 0;
        playing = false;
    }

    /**
     * Opens or closes the playing-time window. Repeated calls with the same value are
     * ignored, so a redundant callback cannot bill the same interval twice.
     */
    synchronized void onPlayingStateChanged(boolean isPlaying, long nowElapsedMs) {
        if (isPlaying == playing) return;
        if (isPlaying) {
            playing = true;
            playingSinceMs = nowElapsedMs;
            return;
        }
        playing = false;
        if (playingSinceMs > 0 && nowElapsedMs > playingSinceMs) {
            playTimeMs = saturatedAdd(playTimeMs, nowElapsedMs - playingSinceMs);
        }
        playingSinceMs = 0;
    }

    /**
     * Records one media3 dropped-frame report and returns the new running total.
     *
     * @param settledWhileNotPlaying true when the report arrived outside a playing
     *        window, i.e. it is the accumulation tail settled by a renderer stop
     */
    synchronized long onDroppedFramesReported(int droppedFrames, boolean settledWhileNotPlaying) {
        long observed = Math.max(0, droppedFrames);
        totalDroppedFrames = saturatedAdd(totalDroppedFrames, observed);
        if (settledWhileNotPlaying) {
            pauseSettledDroppedFrames = saturatedAdd(pauseSettledDroppedFrames, observed);
            pauseSettledReports++;
        }
        return totalDroppedFrames;
    }

    synchronized boolean isPlaying() {
        return playing;
    }

    synchronized long droppedFrames() {
        return totalDroppedFrames;
    }

    synchronized long pauseSettledDroppedFrames() {
        return pauseSettledDroppedFrames;
    }

    synchronized int pauseSettledReports() {
        return pauseSettledReports;
    }

    /** Played time so far, including the interval that is still open. */
    synchronized long playTimeMs(long nowElapsedMs) {
        if (!playing || playingSinceMs <= 0 || nowElapsedMs <= playingSinceMs) return playTimeMs;
        return saturatedAdd(playTimeMs, nowElapsedMs - playingSinceMs);
    }

    /**
     * Dropped frames per played second. Zero before any playback time was measured:
     * a division by an unmeasured window would report an infinite rate for a single
     * tail frame and turn the pause artefact into a worse verdict than the bare count.
     */
    synchronized double droppedFramesPerSecond(long nowElapsedMs) {
        long measuredMs = playTimeMs(nowElapsedMs);
        if (measuredMs <= 0) return 0d;
        return totalDroppedFrames * 1000d / measuredMs;
    }

    private static long saturatedAdd(long current, long delta) {
        if (delta <= 0) return current;
        return current > Long.MAX_VALUE - delta ? Long.MAX_VALUE : current + delta;
    }
}
