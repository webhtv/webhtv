package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExoCapacityBudgetTest {

    private static final long MB = 1024L * 1024L;

    @Test
    public void unlimitedTotalKeepsPreloadSetting() {
        assertEquals(2048 * MB, ExoCapacityBudget.capacityBytes(0, 2048 * MB, 0));
        assertEquals(2048 * MB, ExoCapacityBudget.capacityBytes(0, 2048 * MB, 900 * MB));
    }

    @Test
    public void emptyOtherModulesGiveTheWholeLimit() {
        assertEquals(128 * MB, ExoCapacityBudget.capacityBytes(128 * MB, 2048 * MB, 0));
    }

    @Test
    public void otherModulesShrinkThePlaybackShare() {
        assertEquals(116 * MB, ExoCapacityBudget.capacityBytes(128 * MB, 2048 * MB, 12 * MB));
    }

    @Test
    public void floorAppliesWhenOtherModulesExceedTheLimit() {
        assertEquals(ExoCapacityBudget.MIN_BUDGET_BYTES,
                ExoCapacityBudget.capacityBytes(128 * MB, 2048 * MB, 210 * MB));
    }

    @Test
    public void preloadSettingRemainsTheCeiling() {
        assertEquals(64 * MB, ExoCapacityBudget.capacityBytes(512 * MB, 64 * MB, 0));
    }

    @Test
    public void floorNeverExceedsThePreloadSetting() {
        assertEquals(32 * MB, ExoCapacityBudget.capacityBytes(128 * MB, 32 * MB, 400 * MB));
    }

    @Test
    public void negativeInputsAreTreatedAsZero() {
        assertEquals(0, ExoCapacityBudget.capacityBytes(128 * MB, -1, 0));
        assertEquals(128 * MB, ExoCapacityBudget.capacityBytes(128 * MB, 2048 * MB, -5));
    }
}
