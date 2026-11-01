package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimelineStreamKindPolicyTest {

    @Test
    public void unknownDurationIsVodWhenTheManifestIsNotLive() {
        assertFalse(TimelineStreamKindPolicy.isLive(C.TIME_UNSET, false));
        assertTrue(TimelineStreamKindPolicy.isVod(C.TIME_UNSET, false));
    }

    @Test
    public void unknownDurationStillFollowsALiveManifest() {
        assertTrue(TimelineStreamKindPolicy.isLive(C.TIME_UNSET, true));
        assertFalse(TimelineStreamKindPolicy.isVod(C.TIME_UNSET, true));
    }

    @Test
    public void zeroDurationIsTreatedAsUnknown() {
        assertFalse(TimelineStreamKindPolicy.isLive(0, false));
        assertTrue(TimelineStreamKindPolicy.isVod(0, false));
    }

    @Test
    public void shortKnownDurationIsStillLive() {
        assertTrue(TimelineStreamKindPolicy.isLive(30_000, false));
        assertFalse(TimelineStreamKindPolicy.isVod(30_000, false));
    }

    @Test
    public void thresholdItselfIsVodAlthoughItIsNotLive() {
        long threshold = TimelineStreamKindPolicy.LEGACY_LIVE_DURATION_THRESHOLD_MS;

        assertFalse(TimelineStreamKindPolicy.isLive(threshold, false));
        assertTrue(TimelineStreamKindPolicy.isVod(threshold, false));
    }

    @Test
    public void longKnownDurationIsVod() {
        assertFalse(TimelineStreamKindPolicy.isLive(900_000, false));
        assertTrue(TimelineStreamKindPolicy.isVod(900_000, false));
    }

    @Test
    public void liveManifestOverridesALongDuration() {
        assertTrue(TimelineStreamKindPolicy.isLive(900_000, true));
        assertFalse(TimelineStreamKindPolicy.isVod(900_000, true));
    }
}
