package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Locks the two review findings fixed for the cache-management branch.
 *
 * <p>1. Automatic cleanup must honour the registry's {@code allowAutomaticCleanup} declaration
 * instead of only skipping the diagnostic-log module.</p>
 *
 * <p>2. The temporary-file cleanup must delete exactly the family the inventory reports, instead of
 * a second hand-written prefix list that covered fewer names than the registry.</p>
 */
public class CacheAutoPlanAndTempFamilyTest {

    @Test
    public void automaticLightPlanKeepsOnlyRegistryApprovedModules() {
        assertEquals(List.of(CacheModuleId.EPG, CacheModuleId.TEMP_FILES, CacheModuleId.LEGACY_FILES),
                CacheScheduler.automaticPlan(CacheCleanupMode.LIGHT).modules());
        assertEquals(CacheCleanupMode.LIGHT, CacheScheduler.automaticPlan(CacheCleanupMode.LIGHT).mode());
    }

    /**
     * The STANDARD tier is what the low-space automatic run uses. It contains GLIDE and
     * WEBHOME_RAW for manual cleanup, but those entries declare {@code allowAutomaticCleanup=false}
     * and therefore must not survive into an automatic plan.
     */
    @Test
    public void automaticStandardPlanDropsModulesThatForbidAutomaticCleanup() {
        List<CacheModuleId> modules = CacheScheduler.automaticPlan(CacheCleanupMode.STANDARD).modules();
        assertFalse("GLIDE declares allowAutomaticCleanup=false", modules.contains(CacheModuleId.GLIDE));
        assertFalse("WEBHOME_RAW declares allowAutomaticCleanup=false", modules.contains(CacheModuleId.WEBHOME_RAW));
        assertFalse(modules.contains(CacheModuleId.PLUGIN_SCRIPTS));
        assertFalse(modules.contains(CacheModuleId.DIAGNOSTIC_LOGS));
        assertFalse(modules.contains(CacheModuleId.UNCLASSIFIED));
        assertFalse(modules.contains(CacheModuleId.EXO));
        assertTrue(modules.contains(CacheModuleId.LYRICS));
        assertTrue(modules.contains(CacheModuleId.KARAOKE));
        assertTrue(modules.contains(CacheModuleId.WEBHOME_EXT));
        assertTrue(modules.contains(CacheModuleId.EPG));
        assertTrue(modules.contains(CacheModuleId.TEMP_FILES));
        assertTrue(modules.contains(CacheModuleId.LEGACY_FILES));
    }

    /** No automatic tier may ever contain a module the registry forbids for automatic cleanup. */
    @Test
    public void everyAutomaticPlanRespectsTheRegistryDeclaration() {
        for (CacheCleanupMode mode : CacheCleanupMode.values()) {
            if (mode == CacheCleanupMode.MODULE) continue;
            for (CacheModuleId id : CacheScheduler.automaticPlan(mode).modules()) {
                assertTrue("automatic plan leaked " + id, CachePolicyEngine.allowsAutomatic(id));
            }
        }
    }

    @Test
    public void automaticPlanOfNullFallsBackToLight() {
        assertEquals(CacheScheduler.automaticPlan(CacheCleanupMode.LIGHT).modules(),
                CacheScheduler.automaticPlan(null).modules());
    }

    /**
     * The cleanup candidate set must be derived from the registry, so every name the inventory
     * counts as a temporary file is also deletable.
     */
    @Test
    public void temporaryFamilyComesFromTheRegistryAndCoversReportedSuffixes() throws Exception {
        File cache = cacheDir("temp-family");
        CacheCleanupManager.TemporaryFamily family = CacheCleanupManager.temporaryFamily(cache);

        assertTrue("update.apk is reported and must stay cleanable", family.matches("update.apk"));
        assertTrue("a sync archive is reported by the .zip suffix", family.matches("webhtv-sync-123.zip"));
        assertTrue("a login-state archive is reported by the .zip suffix", family.matches("webhtv-login-state-123.zip"));
        assertTrue("a legacy backup archive is reported by the .zip suffix", family.matches("bak-20260928-1200.zip"));
        assertTrue("goProxy.log is reported by the .log suffix", family.matches("goProxy.log"));
        assertTrue(family.matches("pushed-123.apk"));
        assertTrue(family.matches("pushed-url-123.apk"));
        assertTrue(family.matches("partial.tmp"));
        assertTrue(family.matches("apk-push-123.apk"));
    }

    /** Owner-managed files the registry excludes stay out of the candidate set. */
    @Test
    public void temporaryFamilyNeverClaimsProtectedOrUnrelatedNames() throws Exception {
        File cache = cacheDir("temp-family-exclude");
        CacheCleanupManager.TemporaryFamily family = CacheCleanupManager.temporaryFamily(cache);

        assertFalse("the diagnostic log is excluded by the registry",
                family.matches("webhtv-debug-log.txt"));
        assertFalse(family.matches("mpv-playback-recovery.lock"));
        assertFalse(family.matches("mpv-playback-recovery.state"));
        assertFalse(family.matches("mpv-playback-recovery.result"));
        assertFalse("a rotated diagnostic segment is not a temporary file",
                family.matches("webhtv-debug-log.txt.1"));
        assertFalse(family.matches("unrelated.bin"));
        assertFalse(family.matches("notes.txt"));
        assertFalse(family.matches(null));
    }

    private static File cacheDir(String prefix) throws Exception {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        return Files.createTempDirectory(base, prefix).toFile();
    }
}
