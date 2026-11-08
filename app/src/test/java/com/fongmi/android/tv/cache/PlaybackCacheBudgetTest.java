package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackCacheBudgetTest {

    private static final long MB = 1024L * 1024L;

    @Test
    public void playbackKeepsThePreloadCeilingAndIgnoresTheTotalLimit() {
        assertEquals(2048 * MB, PlaybackCacheBudget.capacityBytes(true, 128 * MB, 2048 * MB, 900 * MB));
        assertEquals(64 * MB, PlaybackCacheBudget.capacityBytes(false, 128 * MB, 2048 * MB, 900 * MB));
    }

    @Test
    public void playbackCapacityNeverGoesNegative() {
        assertEquals(0, PlaybackCacheBudget.capacityBytes(true, 128 * MB, -1, 0));
    }

    @Test
    public void unlimitedTotalKeepsPreloadSetting() {
        assertEquals(2048 * MB, PlaybackCacheBudget.capacityBytes(0, 2048 * MB, 0));
        assertEquals(2048 * MB, PlaybackCacheBudget.capacityBytes(0, 2048 * MB, 900 * MB));
    }

    @Test
    public void emptyOtherModulesGiveTheWholeLimit() {
        assertEquals(128 * MB, PlaybackCacheBudget.capacityBytes(128 * MB, 2048 * MB, 0));
    }

    @Test
    public void otherModulesShrinkThePlaybackShare() {
        assertEquals(116 * MB, PlaybackCacheBudget.capacityBytes(128 * MB, 2048 * MB, 12 * MB));
    }

    @Test
    public void floorAppliesWhenOtherModulesExceedTheLimit() {
        assertEquals(PlaybackCacheBudget.MIN_BUDGET_BYTES,
                PlaybackCacheBudget.capacityBytes(128 * MB, 2048 * MB, 210 * MB));
    }

    @Test
    public void preloadSettingRemainsTheCeiling() {
        assertEquals(64 * MB, PlaybackCacheBudget.capacityBytes(512 * MB, 64 * MB, 0));
    }

    @Test
    public void floorNeverExceedsThePreloadSetting() {
        assertEquals(32 * MB, PlaybackCacheBudget.capacityBytes(128 * MB, 32 * MB, 400 * MB));
    }

    @Test
    public void negativeInputsAreTreatedAsZero() {
        assertEquals(0, PlaybackCacheBudget.capacityBytes(128 * MB, -1, 0));
        assertEquals(128 * MB, PlaybackCacheBudget.capacityBytes(128 * MB, 2048 * MB, -5));
    }

    @Test
    public void panelLimitLowersThePreloadCeiling() {
        assertEquals(128 * MB, PlaybackCacheBudget.ceilingBytes(2048 * MB, 128 * MB));
    }

    @Test
    public void unlimitedPanelLimitKeepsThePreloadCeiling() {
        assertEquals(2048 * MB, PlaybackCacheBudget.ceilingBytes(2048 * MB, 0));
        assertEquals(2048 * MB, PlaybackCacheBudget.ceilingBytes(2048 * MB, -1));
    }

    @Test
    public void panelLimitAboveThePreloadSettingChangesNothing() {
        assertEquals(512 * MB, PlaybackCacheBudget.ceilingBytes(512 * MB, 1024 * MB));
    }
}
