package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CacheTempFilePolicyTest {

    @Test
    public void inFlightUpdateIsProtected() {
        assertTrue(CacheTempFilePolicy.isInUse("update.apk", true, false));
        assertFalse(CacheTempFilePolicy.isInUse("update.apk", false, false));
    }

    @Test
    public void inFlightApkUrlPushIsProtected() {
        assertTrue(CacheTempFilePolicy.isInUse("pushed-url-123.apk", false, true));
        assertFalse(CacheTempFilePolicy.isInUse("pushed-url-123.apk", false, false));
    }

    @Test
    public void unrelatedFilesAreNeverMarkedInUse() {
        assertFalse(CacheTempFilePolicy.isInUse("webhtv-sync-1.zip", true, true));
        assertFalse(CacheTempFilePolicy.isInUse("pushed-123.apk", true, true));
        assertFalse(CacheTempFilePolicy.isInUse(null, true, true));
        assertFalse(CacheTempFilePolicy.isInUse("", true, true));
    }
}
