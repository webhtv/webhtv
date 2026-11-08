package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExoFrameTimingMetricsTest {

    @Test
    public void aggregatesEarlyAndLateFrameBatches() {
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();
        metrics.observeProcessingOffset(40_000, 4);
        metrics.observeProcessingOffset(-10_000, 1);

        ExoFrameTimingMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(6_000, snapshot.averageOffsetUs());
        assertEquals(5, snapshot.frameCount());
        assertEquals(1, snapshot.lateBatchCount());
    }

    @Test
    public void recordsRecoverableCodecErrorsAndReset() {
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();
        metrics.observeCodecError(new IllegalStateException("codec stalled"));
        assertEquals(1, metrics.snapshot().codecErrorCount());
        assertEquals("IllegalStateException", metrics.snapshot().lastCodecError());

        metrics.reset();
        assertEquals(0, metrics.snapshot().codecErrorCount());
        assertEquals(0, metrics.snapshot().frameCount());
    }

    @Test
    public void aggregatesScheduledReleaseLatenessJitterAndCallbackGap() {
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();
        metrics.observeFrameRelease(0, 1_000_000_000L, 999_000_000L);
        metrics.observeFrameRelease(40_000, 1_040_000_000L, 1_041_000_000L);
        metrics.observeFrameRelease(80_000, 1_082_000_000L, 1_084_000_000L);

        ExoFrameTimingMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(3, snapshot.releaseFrameCount());
        assertEquals(-666, snapshot.averageReleaseLeadUs());
        assertEquals(2, snapshot.lateReleaseFrameCount());
        assertEquals(2_000, snapshot.maxLateReleaseUs());
        assertEquals(1_000, snapshot.averageReleaseJitterUs());
        assertEquals(42_500, snapshot.averageCallbackGapUs());
        assertEquals(43_000, snapshot.maxCallbackGapUs());
    }

    @Test
    public void doesNotCountInterruptionAsReleaseJitterOrCallbackGap() {
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();
        metrics.observeFrameRelease(0, 1_000_000_000L, 999_000_000L);
        metrics.resetReleaseContinuity();
        metrics.observeFrameRelease(40_000, 10_000_000_000L, 9_999_000_000L);

        ExoFrameTimingMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(2, snapshot.releaseFrameCount());
        assertEquals(0, snapshot.releaseJitterSampleCount());
        assertEquals(0, snapshot.callbackGapSampleCount());
    }

    @Test
    public void recordsSignedTimingAndMagnitudeBuckets() {
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();
        metrics.observeProcessingOffset(-60_000, 2);
        metrics.observeProcessingOffset(-10_000, 1);
        metrics.observeProcessingOffset(10_000, 1);
        metrics.observeProcessingOffset(30_000, 1);
        metrics.observeProcessingOffset(60_000, 1);
        metrics.observeProcessingOffset(80_000, 1);
        metrics.observeProcessingOffset(100_000, 1);

        long now = 1_000_000_000L;
        metrics.observeFrameRelease(0, now - 30_000_000L, now);
        metrics.resetReleaseContinuity();
        metrics.observeFrameRelease(1, now - 10_000_000L, now);
        metrics.resetReleaseContinuity();
        metrics.observeFrameRelease(2, now + 10_000_000L, now);
        metrics.resetReleaseContinuity();
        metrics.observeFrameRelease(3, now + 30_000_000L, now);
        metrics.resetReleaseContinuity();
        metrics.observeFrameRelease(4, now + 60_000_000L, now);
        metrics.resetReleaseContinuity();
        metrics.observeFrameRelease(5, now + 80_000_000L, now);
        metrics.resetReleaseContinuity();
        metrics.observeFrameRelease(6, now + 100_000_000L, now);

        ExoFrameTimingMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals("2/1/1/1/1/1/1",
                snapshot.processingOffsetBuckets().compact());
        assertEquals("1/1/1/1/1/1/1",
                snapshot.releaseLeadBuckets().compact());
        assertEquals(snapshot.frameCount(),
                snapshot.processingOffsetBuckets().total());
        assertEquals(snapshot.releaseFrameCount(),
                snapshot.releaseLeadBuckets().total());
    }

    @Test
    public void stableTelemetryCanSkipExperimentDistributionBuckets() {
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();

        metrics.observeProcessingOffset(40_000, 4, false);

        ExoFrameTimingMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(10_000, snapshot.averageOffsetUs());
        assertEquals(4, snapshot.frameCount());
        assertEquals(0, snapshot.processingOffsetBuckets().total());
    }

    @Test
    public void bucketsSeparateLateFramesFromEarlyFrames() {
        // media3 reports one offset sample per processed frame: positive = processed early enough,
        // negative = the position had already passed the frame. These buckets are what turns a
        // "掉帧" report into "frames delivered 30-100 ms late" — the measurement shape
        // androidx/media#3347 needed to tell a skip apart from a hold.
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();
        metrics.observeProcessingOffset(-30_000, 1, true);
        metrics.observeProcessingOffset(-5_000, 1, true);
        metrics.observeProcessingOffset(10_000, 1, true);
        metrics.observeProcessingOffset(60_000, 1, true);

        ExoFrameTimingMetrics.SignedTimingBuckets buckets =
                metrics.snapshot().processingOffsetBuckets();
        assertEquals(1, buckets.earlierThanMinus20Ms());
        assertEquals(1, buckets.minus20MsToZero());
        assertEquals(1, buckets.zeroTo20Ms());
        assertEquals(1, buckets.fiftyTo75Ms());
        assertEquals(4, buckets.total());
        assertEquals("1/1/1/0/1/0/0", buckets.compact());
    }

    @Test
    public void extremeTimestampsSaturateWithoutNegativeJitterOrCounts() {
        ExoFrameTimingMetrics metrics = new ExoFrameTimingMetrics();
        metrics.observeProcessingOffset(Long.MAX_VALUE, 1);
        metrics.observeProcessingOffset(Long.MAX_VALUE, 1);
        metrics.observeFrameRelease(
                Long.MIN_VALUE + 1, Long.MAX_VALUE, 1);
        metrics.observeFrameRelease(
                Long.MAX_VALUE, 1, Long.MAX_VALUE);

        ExoFrameTimingMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(2, snapshot.frameCount());
        assertEquals(2, snapshot.releaseFrameCount());
        assertTrue(snapshot.averageReleaseJitterUs() >= 0);
        assertTrue(snapshot.maxLateReleaseUs() >= 0);
        assertTrue(snapshot.processingOffsetBuckets().total() >= 0);
        assertTrue(snapshot.releaseLeadBuckets().total() >= 0);
    }
}
