package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.List;

public class CachePolicyEngineTest {

    @Test
    public void lightPlanContainsOnlyLowRiskModules() {
        assertEquals(List.of(CacheModuleId.EPG, CacheModuleId.TEMP_FILES, CacheModuleId.LEGACY_FILES),
                CachePolicyEngine.plan(CacheCleanupMode.LIGHT).modules());
    }

    @Test
    public void standardPlanNeverContainsPlaybackCache() {
        List<CacheModuleId> modules = CachePolicyEngine.plan(CacheCleanupMode.STANDARD).modules();
        assertFalse(modules.contains(CacheModuleId.EXO));
        assertFalse(modules.contains(CacheModuleId.MPV_HLS));
        assertFalse(modules.contains(CacheModuleId.MPV_DEMUXER));
        assertFalse(modules.contains(CacheModuleId.MPV_RUNTIME));
    }

    @Test
    public void deepPlanCoversPlaybackAndMediaExactlyOnce() {
        List<CacheModuleId> modules = CachePolicyEngine.plan(CacheCleanupMode.DEEP).modules();
        // Four modules stay out of every tiered run: two report-only surfaces, unclassified cache
        // (owner unknown), and the plugin HTTP cache (its entries only mean anything inside the TTL
        // a plugin asked for, so a deep plan must not silently turn a cached plugin into a network
        // call). Each remains reachable through its own module row.
        assertEquals(CacheModuleId.values().length - 4, modules.size());
        assertEquals(modules.size(), modules.stream().distinct().count());
        // L3 = L2 + playback caches. The report-only modules are excluded by design.
        assertTrue(modules.contains(CacheModuleId.EXO));
        assertTrue(modules.contains(CacheModuleId.MPV_HLS));
        assertTrue(modules.contains(CacheModuleId.MPV_DEMUXER));
        assertTrue(modules.contains(CacheModuleId.MPV_RUNTIME));
        assertFalse(modules.contains(CacheModuleId.DIAGNOSTIC_LOGS));
        assertFalse(modules.contains(CacheModuleId.PLUGIN_AUDIT));
        assertFalse(modules.contains(CacheModuleId.PLUGIN_HTTP_CACHE));
        assertFalse(modules.contains(CacheModuleId.UNCLASSIFIED));
    }

    /**
     * The legacy module's cleanup must be reached by the tiered plans too, since that is the entry
     * the user actually pressed when the cleanup reported "0 files deleted".
     */
    @Test
    public void legacyModuleIsPartOfEveryTierThatClaimsIt() {
        assertTrue(CachePolicyEngine.plan(CacheCleanupMode.LIGHT).modules()
                .contains(CacheModuleId.LEGACY_FILES));
        assertTrue(CachePolicyEngine.plan(CacheCleanupMode.STANDARD).modules()
                .contains(CacheModuleId.LEGACY_FILES));
        assertTrue(CachePolicyEngine.plan(CacheCleanupMode.DEEP).modules()
                .contains(CacheModuleId.LEGACY_FILES));
    }

    /**
     * Diagnostic logs are manually cleanable through their own module row, but no tiered run and
     * no automatic run may remove them: they are the evidence a user may still need.
     */
    @Test
    public void diagnosticLogsAreManualOnly() {
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.DIAGNOSTIC_LOGS));
        assertFalse(CachePolicyEngine.plan(CacheCleanupMode.LIGHT).modules()
                .contains(CacheModuleId.DIAGNOSTIC_LOGS));
        assertFalse(CachePolicyEngine.plan(CacheCleanupMode.STANDARD).modules()
                .contains(CacheModuleId.DIAGNOSTIC_LOGS));
        assertFalse(CachePolicyEngine.plan(CacheCleanupMode.DEEP).modules()
                .contains(CacheModuleId.DIAGNOSTIC_LOGS));
        assertEquals(List.of(CacheModuleId.DIAGNOSTIC_LOGS),
                CachePolicyEngine.module(CacheModuleId.DIAGNOSTIC_LOGS).modules());
    }

    /**
     * The plugin audit trail is the only evidence surface for {@code net.request} (no domain
     * allowlist exists by decision), so no tiered or automatic run may erase it. It stays reachable
     * through its own module row, exactly like the diagnostic logs.
     */
    @Test
    public void pluginAuditLogIsManualOnly() {
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.PLUGIN_AUDIT));
        assertFalse(CachePolicyEngine.plan(CacheCleanupMode.LIGHT).modules()
                .contains(CacheModuleId.PLUGIN_AUDIT));
        assertFalse(CachePolicyEngine.plan(CacheCleanupMode.STANDARD).modules()
                .contains(CacheModuleId.PLUGIN_AUDIT));
        assertFalse(CachePolicyEngine.plan(CacheCleanupMode.DEEP).modules()
                .contains(CacheModuleId.PLUGIN_AUDIT));
        assertEquals(List.of(CacheModuleId.PLUGIN_AUDIT),
                CachePolicyEngine.module(CacheModuleId.PLUGIN_AUDIT).modules());
        assertEquals(CacheCleanupStatus.COMPLETED,
                CachePolicyEngine.directCleanupStatus(CacheModuleId.PLUGIN_AUDIT, false));
        assertTrue(CachePolicyEngine.manualCleanupAllowed(CacheModuleId.PLUGIN_AUDIT,
                new File(System.getProperty("java.io.tmpdir"))));
    }

    /** Unclassified cache is report-only: it must never be offered as cleanable. */
    @Test
    public void unclassifiedCacheIsNotDirectlyCleanable() {
        assertEquals(CacheCleanupStatus.NOT_ALLOWED,
                CachePolicyEngine.directCleanupStatus(CacheModuleId.UNCLASSIFIED, false));
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.UNCLASSIFIED));
    }

    /** The UI restriction must come from the registry, not from a hardcoded module check. */
    @Test
    public void manualCleanupPermissionComesFromTheRegistry() {
        File cache = new File(System.getProperty("java.io.tmpdir"));
        assertFalse(CachePolicyEngine.manualCleanupAllowed(CacheModuleId.PLUGIN_SCRIPTS, cache));
        assertFalse(CachePolicyEngine.manualCleanupAllowed(CacheModuleId.UNCLASSIFIED, cache));
        assertTrue(CachePolicyEngine.manualCleanupAllowed(CacheModuleId.LEGACY_FILES, cache));
        assertTrue(CachePolicyEngine.manualCleanupAllowed(CacheModuleId.DIAGNOSTIC_LOGS, cache));
        assertFalse(CachePolicyEngine.manualCleanupAllowed(null, cache));
        assertFalse(CachePolicyEngine.manualCleanupAllowed(CacheModuleId.LEGACY_FILES, null));
    }

    /**
     * The settings row's plain click clears every module, including the report-only ones a tiered
     * plan deliberately protects, and it is the mode that selects the full clean path.
     */
    @Test
    public void fullPlanCoversEveryModuleExactlyOnce() {
        CacheCleanupPlan plan = CachePolicyEngine.plan(CacheCleanupMode.FULL);
        assertEquals(CacheCleanupMode.FULL, plan.mode());
        assertEquals(CacheModuleId.values().length, plan.modules().size());
        assertEquals(plan.modules().size(), plan.modules().stream().distinct().count());
        assertTrue(plan.modules().contains(CacheModuleId.EXO));
        assertTrue(plan.modules().contains(CacheModuleId.DIAGNOSTIC_LOGS));
        assertTrue(plan.modules().contains(CacheModuleId.UNCLASSIFIED));
    }

    @Test
    public void modulePlanIsExplicitAndSingleModule() {
        assertEquals(List.of(CacheModuleId.LYRICS),
                CachePolicyEngine.module(CacheModuleId.LYRICS).modules());
    }

    @Test
    public void playbackModulesAreDeferredWhilePlaying() {
        assertEquals(CacheCleanupStatus.DEFERRED,
                CachePolicyEngine.directCleanupStatus(CacheModuleId.EXO, true));
        assertEquals(CacheCleanupStatus.DEFERRED,
                CachePolicyEngine.directCleanupStatus(CacheModuleId.MPV_HLS, true));
        assertEquals(CacheCleanupStatus.COMPLETED,
                CachePolicyEngine.directCleanupStatus(CacheModuleId.EXO, false));
    }

    @Test
    public void automaticCleanupExcludesPlaybackAndOwnerManagedModules() {
        assertTrue(CachePolicyEngine.allowsAutomatic(CacheModuleId.LYRICS));
        assertTrue(CachePolicyEngine.allowsAutomatic(CacheModuleId.EPG));
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.EXO));
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.WEBHOME_RAW));
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.GLIDE));
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.PLUGIN_SCRIPTS));
    }
}
