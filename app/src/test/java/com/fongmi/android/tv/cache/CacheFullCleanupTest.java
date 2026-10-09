package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Locks the two user-reported defects fixed for this branch.
 *
 * <p>1. The temporary-file row reported freshly written {@code webhtv-*.zip} / {@code update.apk}
 * leftovers and then released nothing, because the row's own button applied the 24-hour retention
 * meant for background runs. An explicit request must ignore that window while still protecting a
 * transfer that is running right now.</p>
 *
 * <p>2. The settings row's long-press shortcut has to clear everything the way the pre-split
 * one-key clear did, which needs a residual sweep over whatever no module claims.</p>
 */
public class CacheFullCleanupTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /** The reported defect: a fresh archive is exactly what the user asked to remove. */
    @Test
    public void explicitRequestDeletesFreshTemporaryFiles() throws Exception {
        File cache = cacheDir("temp-explicit");
        File archive = write(cache, "webhtv-sync-4242.zip", 4096);
        File apk = write(cache, "update.apk", 2048);
        archive.setLastModified(NOW - 60_000L);
        apk.setLastModified(NOW - 60_000L);

        CacheCleanupManager.Outcome outcome = CacheCleanupManager.clearTemporaryFiles(cache,
                CacheCleanupManager.explicitRetention(CacheCleanupMode.MODULE, DAY_MS), 0L, false, false, NOW);

        assertTrue(outcome.success());
        assertFalse("a fresh sync archive must not survive the row's own button", archive.exists());
        assertFalse("a fresh update download must not survive the row's own button", apk.exists());
    }

    /**
     * The same files stay for a background or tiered run: only an explicit request is immediate.
     *
     * <p>The window is not written into the test, it is asked of the same decision the cleanup
     * switch uses, so weakening that decision fails here instead of silently leaving the row's own
     * button with a 24-hour window again.</p>
     */
    @Test
    public void backgroundRetentionStillKeepsFreshTemporaryFiles() throws Exception {
        File cache = cacheDir("temp-retained");
        File archive = write(cache, "webhtv-sync-4242.zip", 4096);
        archive.setLastModified(NOW - 60_000L);

        CacheCleanupManager.Outcome outcome = CacheCleanupManager.clearTemporaryFiles(cache,
                CacheCleanupManager.explicitRetention(CacheCleanupMode.LIGHT, DAY_MS), 0L, false, false, NOW);

        assertTrue(outcome.success());
        assertTrue("an age-windowed run must keep a one-minute-old archive", archive.exists());
    }

    /** A transfer that is running right now survives even the immediate request. */
    @Test
    public void runningTransferSurvivesTheExplicitRequest() throws Exception {
        File cache = cacheDir("temp-in-use");
        File apk = write(cache, "update.apk", 2048);
        File pushed = write(cache, "pushed-url-9.apk", 1024);
        apk.setLastModified(NOW - 60_000L);
        pushed.setLastModified(NOW - 60_000L);

        CacheCleanupManager.Outcome outcome =
                CacheCleanupManager.clearTemporaryFiles(cache, 0L, 0L, true, true, NOW);

        assertTrue(outcome.success());
        assertTrue("the running updater keeps its download", apk.exists());
        assertTrue("the running push keeps its archive", pushed.exists());
    }

    /**
     * The explicit request ignores the age window but not the module's own candidate set: a family
     * the registry does not claim stays on disk even when nothing is in use.
     */
    @Test
    public void explicitRequestStillOnlyClaimsTheReportedFamily() throws Exception {
        File cache = cacheDir("temp-foreign");
        File notes = write(cache, "notes.txt", 256);
        File partial = write(cache, "attachment.zip.part", 512);
        File archive = write(cache, "webhtv-sync-4242.zip", 2048);
        notes.setLastModified(NOW - 60_000L);
        partial.setLastModified(NOW - 60_000L);
        archive.setLastModified(NOW - 60_000L);

        CacheCleanupManager.Outcome outcome =
                CacheCleanupManager.clearTemporaryFiles(cache, 0L, 0L, false, false, NOW);

        assertTrue(outcome.success());
        assertTrue("a .txt note is not part of the temporary family", notes.exists());
        assertTrue("a partial download is not part of the temporary family", partial.exists());
        assertFalse("the module deletes exactly the family it reports", archive.exists());
    }

    /** The sweep removes what no module claims, including the scripts nobody is loading. */
    @Test
    public void sweepRemovesLeftoversAndIdlePluginScripts() throws Exception {
        File cache = cacheDir("sweep-leftovers");
        File leftover = write(cache, "unknown-owner.bin", 512);
        File staleTree = new File(cache, "stale-dir");
        write(staleTree, "deep/data.bin", 128);
        File idleScript = write(new File(cache, "js"), "idle-site.js", 256);
        File activeScript = write(new File(cache, "js"), "active-site.js", 256);

        ArrayList<String> warnings = new ArrayList<>();
        CacheCleanupManager.sweepResidual(cache, List.of(activeScript), false, false, warnings);

        assertFalse(leftover.exists());
        assertFalse("a tree no module owns is a leftover", staleTree.exists());
        assertFalse(idleScript.exists());
        assertTrue("the script of a running loader must survive", activeScript.exists());
        assertTrue("nothing in this scenario is a failure: " + warnings, warnings.isEmpty());
    }

    /**
     * Every age-windowed module decides through one shared rule, so the reported defect can not
     * come back at a single call site. The row's own button (MODULE) and the long-press shortcut
     * (FULL) are explicit requests; the tiered and automatic runs keep the background window.
     */
    @Test
    public void onlyExplicitRequestsIgnoreTheBackgroundRetentionWindow() {
        assertTrue(CacheCleanupManager.explicitRequest(CacheCleanupMode.MODULE));
        assertTrue(CacheCleanupManager.explicitRequest(CacheCleanupMode.FULL));
        assertFalse(CacheCleanupManager.explicitRequest(CacheCleanupMode.LIGHT));
        assertFalse(CacheCleanupManager.explicitRequest(CacheCleanupMode.STANDARD));
        assertFalse(CacheCleanupManager.explicitRequest(CacheCleanupMode.DEEP));

        assertEquals(0L, CacheCleanupManager.explicitRetention(CacheCleanupMode.MODULE, DAY_MS));
        assertEquals(0L, CacheCleanupManager.explicitRetention(CacheCleanupMode.FULL, DAY_MS));
        assertEquals(DAY_MS, CacheCleanupManager.explicitRetention(CacheCleanupMode.LIGHT, DAY_MS));
        assertEquals(DAY_MS, CacheCleanupManager.explicitRetention(CacheCleanupMode.STANDARD, DAY_MS));
        assertEquals(DAY_MS, CacheCleanupManager.explicitRetention(CacheCleanupMode.DEEP, DAY_MS));
    }

    /**
     * The two carve-outs the full clean documents: a transfer that is running right now, and the
     * trees the caller preserved because a playback cache has to wait for playback to stop.
     */
    @Test
    public void sweepKeepsPreservedTreesTransfersAndRecoveryFiles() throws Exception {
        File cache = cacheDir("sweep-carveouts");
        File playback = new File(cache, "exo");
        write(playback, "segment.bin", 4096);
        File recovery = write(cache, "mpv-playback-recovery.state", 64);
        File apk = write(cache, "update.apk", 2048);
        File leftover = write(cache, "unknown-owner.bin", 512);

        ArrayList<String> warnings = new ArrayList<>();
        CacheCleanupManager.sweepResidual(cache, List.of(playback), true, false, warnings);

        assertTrue("a deferred playback cache keeps its tree", new File(playback, "segment.bin").exists());
        assertTrue("the mpv recovery file is never swept", recovery.exists());
        assertTrue("the running updater keeps its download", apk.exists());
        assertFalse(leftover.exists());
        assertFalse("the skipped files must be reported: " + warnings, warnings.isEmpty());
    }

    private static File write(File dir, String name, int size) throws Exception {
        File file = new File(dir, name);
        File parent = file.getParentFile();
        assertTrue("could not create " + parent, parent != null && (parent.isDirectory() || parent.mkdirs()));
        Files.write(file.toPath(), new byte[size]);
        return file;
    }

    private static File cacheDir(String prefix) throws Exception {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        return Files.createTempDirectory(base, prefix).toFile();
    }
}
