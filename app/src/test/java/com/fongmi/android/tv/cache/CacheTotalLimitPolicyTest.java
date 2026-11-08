package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CacheTotalLimitPolicyTest {

    @Test
    public void unlimitedWhenNotConfigured() {
        assertEquals(0, CacheTotalLimitPolicy.effectiveLimit(0, 1024));
        assertFalse(CacheTotalLimitPolicy.overLimit(4096, 0));
    }

    @Test
    public void systemQuotaClampsUserLimit() {
        assertEquals(512, CacheTotalLimitPolicy.effectiveLimit(1024, 512));
        assertEquals(1024, CacheTotalLimitPolicy.effectiveLimit(1024, 0));
    }

    @Test
    public void overLimitOnlyWhenEffectiveLimitIsPositive() {
        assertTrue(CacheTotalLimitPolicy.overLimit(600, 512));
        assertFalse(CacheTotalLimitPolicy.overLimit(500, 512));
    }
}
