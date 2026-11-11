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
 * Locks the total-limit contract: the configured total is one budget for every module, so reaching
 * it requires cleaning the playback caches too, and it must never reach plugin scripts or the
 * unclassified cache.
 */
public class TotalLimitConvergencePlanTest {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    @Test
    public void convergencePlanCoversThePlaybackCaches() throws Exception {
        List<CacheModuleId> modules = CachePolicyEngine.convergencePlan(cacheDir("converge-playback")).modules();

        assertTrue(modules.contains(CacheModuleId.EXO));
        assertTrue(modules.contains(CacheModuleId.MPV_HLS));
        assertTrue(modules.contains(CacheModuleId.MPV_DEMUXER));
        assertTrue(modules.contains(CacheModuleId.MPV_RUNTIME));
    }

    /** The largest non-playback consumers have to participate, or the total can not move. */
    @Test
    public void convergencePlanCoversImagesAndDiagnosticLogs() throws Exception {
        List<CacheModuleId> modules = CachePolicyEngine.convergencePlan(cacheDir("converge-media")).modules();

        assertTrue(modules.contains(CacheModuleId.GLIDE));
        assertTrue(modules.contains(CacheModuleId.DIAGNOSTIC_LOGS));
        assertTrue(modules.contains(CacheModuleId.WEBHOME_EXT));
        assertTrue(modules.contains(CacheModuleId.WEBHOME_RAW));
        assertTrue(modules.contains(CacheModuleId.EPG));
    }

    /**
     * A jar/py/js a site is loading from and a cache whose owner is unknown must survive every
     * convergence, so a limit tighter than them is reported instead of forced.
     */
    @Test
    public void convergencePlanSkipsProtectedModules() throws Exception {
        List<CacheModuleId> modules = CachePolicyEngine.convergencePlan(cacheDir("converge-protected")).modules();

        assertFalse(modules.contains(CacheModuleId.PLUGIN_SCRIPTS));
        assertFalse(modules.contains(CacheModuleId.UNCLASSIFIED));
    }

    @Test
    public void convergencePlanSkipsLocalNeverEvictModules() throws Exception {
        List<CacheModuleId> modules = CachePolicyEngine.convergencePlan(cacheDir("converge-plugin-protected")).modules();

        assertFalse(modules.contains(CacheModuleId.PLUGIN_AUDIT));
        assertFalse(modules.contains(CacheModuleId.PLUGIN_HTTP_CACHE));
        assertTrue(modules.contains(CacheModuleId.EXO));
        assertTrue(modules.contains(CacheModuleId.DIAGNOSTIC_LOGS));
    }

    @Test
    public void convergencePlanIsOrderedAndCarriesItsOwnMode() throws Exception {
        CacheCleanupPlan plan = CachePolicyEngine.convergencePlan(cacheDir("converge-order"));

        assertEquals(CacheCleanupMode.CONVERGE, plan.mode());
        List<CacheModuleId> modules = plan.modules();
        for (int index = 1; index < modules.size(); index++) {
            assertTrue("modules must follow the enum order",
                    modules.get(index - 1).ordinal() < modules.get(index).ordinal());
        }
    }

    /** A total limit the cache can only reach by deleting fresh files must not keep a 7-day window. */
    @Test
    public void convergenceKeepsNoRetentionWindow() {
        assertTrue(CacheCleanupManager.explicitRequest(CacheCleanupMode.CONVERGE));
        assertEquals(0L, CacheCleanupManager.explicitRetention(CacheCleanupMode.CONVERGE, DAY_MS));
    }

    @Test
    public void convergencePlanOfAMissingCacheIsEmpty() {
        assertTrue(CachePolicyEngine.convergencePlan(null).modules().isEmpty());
    }

    private static File cacheDir(String prefix) throws Exception {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        return Files.createTempDirectory(base, prefix).toFile();
    }
}
