package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CacheAutoCleanupPolicyTest {

    @Test
    public void lowSpaceUses512MbOrTenPercentFloor() {
        assertTrue(CacheAutoCleanupPolicy.isLowSpace(400L * 1024L * 1024L, 8L * 1024L * 1024L * 1024L));
        assertFalse(CacheAutoCleanupPolicy.isLowSpace(900L * 1024L * 1024L, 8L * 1024L * 1024L * 1024L));
        assertFalse(CacheAutoCleanupPolicy.isLowSpace(4L * 1024L * 1024L * 1024L, 20L * 1024L * 1024L * 1024L));
    }

    @Test
    public void playingAlwaysDowngradesToLight() {
        assertEquals(CacheCleanupMode.LIGHT, CacheAutoCleanupPolicy.cleanupMode(true, true));
        assertEquals(CacheCleanupMode.STANDARD, CacheAutoCleanupPolicy.cleanupMode(false, true));
        assertEquals(CacheCleanupMode.LIGHT, CacheAutoCleanupPolicy.cleanupMode(false, false));
    }

    @Test
    public void lowSpaceNeedsTwoConsecutiveSamples() {
        assertFalse(CacheAutoCleanupPolicy.shouldTrigger(1, false));
        assertTrue(CacheAutoCleanupPolicy.shouldTrigger(2, false));
        assertTrue(CacheAutoCleanupPolicy.shouldTrigger(0, true));
    }
}
