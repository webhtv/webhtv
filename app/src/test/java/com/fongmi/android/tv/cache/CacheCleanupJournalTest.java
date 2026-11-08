package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class CacheCleanupJournalTest {

    @Test
    public void journalRecordCarriesReasonAndResultFacts() {
        CacheCleanupRecord record = new CacheCleanupRecord(100, 25, "periodic",
                CacheCleanupMode.LIGHT, CacheCleanupStatus.COMPLETED,
                2048, 512, 3, 1, List.of());

        assertEquals(1536, record.releasedBytes());
        assertEquals("periodic", record.reason());
        assertEquals(CacheCleanupStatus.COMPLETED, record.status());
        assertTrue(record.deletedFiles() == 3);
    }
}
