package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DroppedFrameAccountingTest {

    private static final long T0 = 1_000L;

    private final DroppedFrameAccounting accounting = new DroppedFrameAccounting();

    @Test
    public void pauseSettledReportKeepsTheTotalButNotTheRate() {
        accounting.onPlayingStateChanged(true, T0);
        // media3 settles the renderer accumulator from onStopped(), i.e. after playback
        // stopped; the frames themselves were dropped during the 10s that just played.
        accounting.onPlayingStateChanged(false, T0 + 10_000);
        long total = accounting.onDroppedFramesReported(4, true);

        assertEquals(4, total);
        assertEquals(4, accounting.pauseSettledDroppedFrames());
        assertEquals(1, accounting.pauseSettledReports());
        assertEquals(10_000, accounting.playTimeMs(T0 + 10_000));
        assertEquals(0.4d, accounting.droppedFramesPerSecond(T0 + 10_000), 1e-9);
    }

    @Test
    public void playedTimeAccumulatesAcrossPauses() {
        accounting.onPlayingStateChanged(true, T0);
        accounting.onPlayingStateChanged(false, T0 + 10_000);
        accounting.onPlayingStateChanged(true, T0 + 30_000);
        accounting.onPlayingStateChanged(false, T0 + 35_000);

        assertEquals(15_000, accounting.playTimeMs(T0 + 35_000));
    }

    @Test
    public void openPlayingWindowIsCountedUpToNow() {
        accounting.onPlayingStateChanged(true, T0);

        assertEquals(0, accounting.playTimeMs(T0 - 1));
        assertEquals(2_500, accounting.playTimeMs(T0 + 2_500));
    }

    @Test
    public void redundantPlayingStateNeverDoubleBillsTheInterval() {
        accounting.onPlayingStateChanged(true, T0);
        accounting.onPlayingStateChanged(true, T0 + 5_000);
        accounting.onPlayingStateChanged(false, T0 + 10_000);

        assertEquals(10_000, accounting.playTimeMs(T0 + 10_000));
    }

    @Test
    public void rateIsZeroWithoutMeasuredPlayTime() {
        // A single frame with no measured played time must not read as an infinite rate.
        accounting.onDroppedFramesReported(1, false);

        assertEquals(0d, accounting.droppedFramesPerSecond(T0), 1e-9);
        assertEquals(0d, accounting.droppedFramesPerSecond(T0 + 60_000), 1e-9);
    }

    @Test
    public void pauseSettledSplitSeparatesTheReportThatArrivedWhileStopped() {
        accounting.onPlayingStateChanged(true, T0);
        accounting.onDroppedFramesReported(3, false);
        accounting.onPlayingStateChanged(false, T0 + 4_000);
        accounting.onDroppedFramesReported(5, true);
        accounting.onDroppedFramesReported(2, true);

        assertEquals(10, accounting.droppedFrames());
        assertEquals(7, accounting.pauseSettledDroppedFrames());
        assertEquals(2, accounting.pauseSettledReports());
        assertEquals(2.5d, accounting.droppedFramesPerSecond(T0 + 4_000), 1e-9);
    }

    @Test
    public void negativeReportNeverSubtracts() {
        accounting.onPlayingStateChanged(true, T0);
        accounting.onPlayingStateChanged(false, T0 + 1_000);
        accounting.onDroppedFramesReported(-4, true);

        assertEquals(0, accounting.droppedFrames());
        assertEquals(0, accounting.pauseSettledDroppedFrames());
        assertEquals(1, accounting.pauseSettledReports());
    }

    @Test
    public void resetClearsEveryAccumulator() {
        accounting.onPlayingStateChanged(true, T0);
        accounting.onDroppedFramesReported(9, false);
        accounting.onPlayingStateChanged(false, T0 + 2_000);

        accounting.reset();

        assertEquals(0, accounting.droppedFrames());
        assertEquals(0, accounting.pauseSettledDroppedFrames());
        assertEquals(0, accounting.pauseSettledReports());
        assertEquals(0, accounting.playTimeMs(T0 + 60_000));
        assertEquals(0d, accounting.droppedFramesPerSecond(T0 + 60_000), 1e-9);
    }
}
