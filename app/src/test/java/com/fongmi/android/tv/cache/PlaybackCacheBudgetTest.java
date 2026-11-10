package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackCacheBudgetTest {

    private static final long MB = 1024L * 1024L;

    @Test
    public void playbackKeepsThePreloadCeilingAndIgnoresTheTotalLimit() {
        assertEquals(2048 * MB, PlaybackCacheBudget.capacityBytes(true, 128 * MB, 2048 * MB, 900 * MB));
        assertEquals(0, PlaybackCacheBudget.capacityBytes(false, 128 * MB, 2048 * MB, 900 * MB));
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

    /**
     * The reported defect: a 64 MB total with 69.9 MB in other modules kept a 64 MB playback cache,
     * so the panel promised a 64 MB total while the directory held 133.7 MB. The total is one
     * budget for every module, so this module gets what is left over and nothing more.
     */
    @Test
    public void otherModulesAboveTheLimitLeaveNoShareForPlayback() {
        assertEquals(0, PlaybackCacheBudget.capacityBytes(64 * MB, 2048 * MB, 69 * MB));
        assertEquals(0, PlaybackCacheBudget.capacityBytes(128 * MB, 2048 * MB, 210 * MB));
    }

    @Test
    public void playbackShareNeverPushesTheDirectoryPastTheTotal() {
        long total = 128 * MB;
        for (long others = 0; others <= total + 256 * MB; others += 8 * MB) {
            long share = PlaybackCacheBudget.capacityBytes(total, 2048 * MB, others);
            if (others <= total) {
                assertEquals("others=" + others, total, others + share);
            } else {
                // Other modules alone already fill the limit: playback may add nothing at all.
                assertEquals("others=" + others, 0, share);
            }
        }
    }

    @Test
    public void remainingShareIsUsedWhenItIsSmallerThanThePreloadSetting() {
        assertEquals(8 * MB, PlaybackCacheBudget.capacityBytes(128 * MB, 2048 * MB, 120 * MB));
    }

    @Test
    public void preloadSettingRemainsTheCeiling() {
        assertEquals(64 * MB, PlaybackCacheBudget.capacityBytes(512 * MB, 64 * MB, 0));
    }

    @Test
    public void preloadSettingRemainsTheCeilingWhenTheShareIsLarger() {
        assertEquals(32 * MB, PlaybackCacheBudget.capacityBytes(128 * MB, 32 * MB, 80 * MB));
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
