package com.fongmi.android.tv.player.exo;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-level record of the most recent <em>foreground</em> HTTP EOF recovery.
 *
 * <p>An external loopback proxy can truncate a fixed-length response at any time; the player
 * recovers by reopening the same byte offset. That recovery is invisible to the preload policy,
 * which only sees buffer levels and a throughput estimate that this route classifies as
 * {@code BLOCKED}. The result observed on device was preload and foreground loaders competing for
 * one fragile upstream: 176 of 177 EOF recoveries landed inside an active preload window, and the
 * one 37 s stretch with preload suppressed recorded none at all.
 *
 * <p>This signal is deliberately narrower than a bandwidth estimate: it is raised only by an
 * actually observed {@link java.net.ProtocolException}, so it needs no confidence model and cannot
 * be defeated by an untrusted throughput path. Preload reads it to yield while the foreground
 * connection is being repaired; {@link ExoNetworkGuardController} is not wired to it yet.
 *
 * <p>Time-based rather than event-based, so it expires on its own and needs no reset at session
 * boundaries. Only foreground recoveries are recorded; a preload-side recovery must not make
 * preload yield to itself.
 */
final class ExoForegroundEofSignal {

    /** Sentinel for "no foreground EOF recovery observed in this process". */
    private static final long NEVER = Long.MIN_VALUE;

    private static final AtomicLong LAST_RECOVERY_AT_MS = new AtomicLong(NEVER);

    private ExoForegroundEofSignal() {
    }

    /** Records a foreground recovery. {@code nowMs} is {@code SystemClock.elapsedRealtime()}. */
    static void record(long nowMs) {
        LAST_RECOVERY_AT_MS.set(Math.max(0L, nowMs));
    }

    /**
     * Age of the most recent foreground recovery, or {@link Long#MAX_VALUE} when none was ever
     * observed. Callers compare this against a yield window; the sentinel is far above any window,
     * so "never" never triggers a yield.
     */
    static long ageMs(long nowMs) {
        long last = LAST_RECOVERY_AT_MS.get();
        if (last == NEVER) return Long.MAX_VALUE;
        return Math.max(0L, nowMs - last);
    }

    /** Test-only: clears the process-level record so cases cannot leak into one another. */
    static void reset() {
        LAST_RECOVERY_AT_MS.set(NEVER);
    }
}
