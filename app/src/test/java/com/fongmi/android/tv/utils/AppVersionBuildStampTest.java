package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for the box update loop: the stable and beta channel manifests of one release
 * line share a {@code versionCode}, so their build stamps are the only way to tell which one is the
 * newer build. Treating "different tag" as "newer" made the app offer the older stable package to a
 * beta install (and the other way round), which overwrote data forever.
 */
public class AppVersionBuildStampTest {

    private static final String STABLE = "v5.6.0-202609221328";
    private static final String BETA = "v5.6.0-beta-202610091755";

    @Test
    public void buildStampReadsTheTrailingReleaseMoment() {
        assertEquals("202609221328", AppVersion.buildStamp(STABLE));
        assertEquals("202609221328", AppVersion.buildStamp(AppVersion.stripPrefix(STABLE)));
        assertEquals("202610091755", AppVersion.buildStamp(BETA));
        assertEquals("202610111200", AppVersion.buildStamp("5.6.0-fast-202610111200"));
        assertEquals("", AppVersion.buildStamp("5.6.0"));
        assertEquals("", AppVersion.buildStamp(null));
    }

    @Test
    public void newerBetaIsAnUpdateForAStableInstall() {
        assertTrue(AppVersion.isNewer(STABLE, BETA));
    }

    @Test
    public void olderStableIsNeverAnUpdateForABetaInstall() {
        assertFalse("An older stable package must not be offered over a newer beta install",
                AppVersion.isNewer(BETA, STABLE));
    }

    @Test
    public void identicalBuildIsNotAnUpdate() {
        assertFalse(AppVersion.isNewer(BETA, BETA));
        assertFalse(AppVersion.isNewer(STABLE, AppVersion.stripPrefix(STABLE)));
        assertFalse(AppVersion.isNewer(BETA, ""));
        assertFalse(AppVersion.isNewer(BETA, null));
    }

    @Test
    public void newerStampWinsAcrossChannels() {
        assertTrue(AppVersion.isNewer(STABLE, "v5.6.0-beta-202609221329"));
        assertTrue(AppVersion.isNewer(BETA, "v5.6.1-202610101755"));
        assertFalse(AppVersion.isNewer(BETA, "v5.6.1-202610081755"));
    }

    @Test
    public void stampsOfDifferentLengthsAreStillOrdered() {
        String minuteInstall = "v5.6.0-beta-202610091755";
        String secondInstall = "v5.6.0-beta-20261009175522";

        assertFalse("An older minute stamp must not look newer than a second-resolution install",
                AppVersion.isNewer(secondInstall, "v5.6.0-202609221328"));
        assertTrue(AppVersion.isNewer(secondInstall, "v5.6.0-20261009175530"));
        assertTrue(AppVersion.isNewer(minuteInstall, "v5.6.0-20261009175530"));
        assertFalse("The same minute without seconds is not a later build",
                AppVersion.isNewer(secondInstall, minuteInstall));
    }

    @Test
    public void tagsWithoutAComparableStampKeepTheHistoricalBehavior() {
        assertTrue(AppVersion.isNewer("5.6.0", "5.6.1"));
        assertFalse(AppVersion.isNewer("5.6.0", "5.6.0"));
    }

    @Test
    public void runningBuildNameIsReadFromTheBuildTag() {
        assertFalse(AppVersion.fullName().startsWith("v"));
        assertTrue(AppVersion.isCurrent(AppVersion.fullName()));
        assertTrue(AppVersion.isCurrent("v" + AppVersion.fullName()));
    }
}
