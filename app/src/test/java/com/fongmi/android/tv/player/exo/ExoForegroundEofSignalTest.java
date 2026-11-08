package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackRoute;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoForegroundEofSignalTest {

    @After
    public void clearSignal() {
        // Process-level state: without this, cases would leak into one another.
        ExoForegroundEofSignal.reset();
    }

    @Test
    public void neverObservedRecoveryReportsUnreachableAge() {
        ExoForegroundEofSignal.reset();
        // Must be far above any yield window. Returning 0 here would make a healthy session look
        // like a recovering one and suspend preload permanently.
        assertEquals(Long.MAX_VALUE, ExoForegroundEofSignal.ageMs(0));
        assertEquals(Long.MAX_VALUE, ExoForegroundEofSignal.ageMs(1_000_000));
    }

    @Test
    public void ageGrowsFromTheRecordedMoment() {
        ExoForegroundEofSignal.reset();
        ExoForegroundEofSignal.record(10_000);

        assertEquals(0, ExoForegroundEofSignal.ageMs(10_000));
        assertEquals(5_000, ExoForegroundEofSignal.ageMs(15_000));
        assertEquals(15_000, ExoForegroundEofSignal.ageMs(25_000));
    }

    @Test
    public void laterRecoveryReplacesTheEarlierOne() {
        ExoForegroundEofSignal.reset();
        ExoForegroundEofSignal.record(10_000);
        ExoForegroundEofSignal.record(40_000);

        // A second burst must restart the window, not inherit the first one's age.
        assertEquals(0, ExoForegroundEofSignal.ageMs(40_000));
        assertEquals(1_000, ExoForegroundEofSignal.ageMs(41_000));
    }

    @Test
    public void clockGoingBackwardsIsClampedToZero() {
        ExoForegroundEofSignal.reset();
        ExoForegroundEofSignal.record(50_000);

        // elapsedRealtime is monotonic in practice, but a negative age must never be reported as a
        // value that could compare below a yield window by accident.
        assertEquals(0, ExoForegroundEofSignal.ageMs(49_000));
        assertTrue(ExoForegroundEofSignal.ageMs(49_000) >= 0);
    }

    /**
     * End-to-end through the real capture path. The policy tests build {@code Inputs} directly, so
     * they would still pass if {@code Inputs.capture()} never read the signal at all — the change
     * would be silently inert in production. This pins the wiring itself.
     */
    @Test
    public void captureReadsTheSignalSoTheYieldCanActuallyFire() {
        PlaybackAutoContext.SessionToken session =
                new PlaybackAutoContext.SessionToken("p-eof-capture", 1);
        PlaybackAutoContext context = new PlaybackAutoContext(
                session, 0, 1, 0,
                PlaybackAutoContext.Fact.forSession(PlaybackAutoContext.Kernel.EXO,
                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH, 0),
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.DecodeMode.UNKNOWN),
                null, null, null, null);

        ExoForegroundEofSignal.reset();
        ExoForegroundEofSignal.record(1_000);
        AutoPreloadPolicy.Inputs fresh = capture(session, context, 1_000);
        assertEquals(0, fresh.foregroundEofRecoveryAgeMs());
        assertFalse("a fresh recovery must pause preload",
                new AutoPreloadPolicy().evaluate(fresh).enabled());

        // Same session, later clock: the window has passed and preload must be allowed again.
        AutoPreloadPolicy.Inputs expired = capture(session, context, 1_000 + 60_000);
        assertEquals(60_000, expired.foregroundEofRecoveryAgeMs());
        assertTrue("an expired recovery must not pause preload",
                new AutoPreloadPolicy().evaluate(expired).enabled());
    }

    @Test
    public void captureWithoutAnyRecoveryNeverYields() {
        PlaybackAutoContext.SessionToken session =
                new PlaybackAutoContext.SessionToken("p-eofcap-none", 1);
        PlaybackAutoContext context = new PlaybackAutoContext(
                session, 0, 1, 0,
                PlaybackAutoContext.Fact.forSession(PlaybackAutoContext.Kernel.EXO,
                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH, 0),
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.DecodeMode.UNKNOWN),
                null, null, null, null);

        ExoForegroundEofSignal.reset();
        AutoPreloadPolicy.Inputs inputs = capture(session, context, 5_000);

        assertEquals(Long.MAX_VALUE, inputs.foregroundEofRecoveryAgeMs());
        assertTrue("a healthy session must keep preloading",
                new AutoPreloadPolicy().evaluate(inputs).enabled());
    }

    private static AutoPreloadPolicy.Inputs capture(
            PlaybackAutoContext.SessionToken session,
            PlaybackAutoContext context,
            long nowElapsedMs) {
        return AutoPreloadPolicy.Inputs.capture(
                nowElapsedMs,
                session,
                PlaybackRoute.EXTERNAL_LOOPBACK_PROXY,
                30_000,
                15_000_000,
                0,
                false,
                ForwardBufferTrend.Snapshot.unknown(),
                ExoThroughputEstimator.Snapshot.empty(),
                context,
                false,
                false);
    }

    @Test
    public void resetReturnsToUnobserved() {
        ExoForegroundEofSignal.record(10_000);
        ExoForegroundEofSignal.reset();

        assertEquals(Long.MAX_VALUE, ExoForegroundEofSignal.ageMs(10_000));
    }
}
