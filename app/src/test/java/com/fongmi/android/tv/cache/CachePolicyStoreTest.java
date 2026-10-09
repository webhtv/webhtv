package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CachePolicyStoreTest {

    @Test
    public void featureSwitchDefaultsToEnabledWhenUnset() {
        assertTrue(CachePolicyStore.enabledByDefault(null));
    }

    @Test
    public void featureSwitchRespectsExplicitValues() {
        assertTrue(CachePolicyStore.enabledByDefault(true));
        assertFalse(CachePolicyStore.enabledByDefault(false));
    }

    @Test
    public void migrationRunsOnlyForOlderSchemas() {
        assertTrue("fresh install must migrate", CachePolicyStore.needsMigration(0));
        assertFalse("current schema must be a no-op", CachePolicyStore.needsMigration(1));
        assertFalse("newer/unknown schema must keep working", CachePolicyStore.needsMigration(99));
    }
}
