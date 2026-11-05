package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.plugin.PluginHttpCache;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The plugin HTTP cache is a first-class cache module, not a stray directory.
 *
 * <p>Three things must stay true or the feature is worse than no cache at all: the module has to
 * exist in the registry (otherwise nothing in the UI can report or clear it), its root has to pass
 * registry validation (otherwise the whole registry is rejected at startup), and its entries must
 * never be claimed by {@link CacheModuleId#TEMP_FILES}, which deletes on suffix alone.
 */
public class CachePluginHttpModuleTest {

    @Test
    public void registryDeclaresThePluginHttpCacheModule() throws Exception {
        File cache = cacheDir("plugin-http-module");
        List<CacheModule> modules = CacheModuleRegistry.modules(cache);

        CacheModule module = modules.stream()
                .filter(candidate -> candidate.id() == CacheModuleId.PLUGIN_HTTP_CACHE)
                .findFirst().orElseThrow();

        assertEquals(CacheGroup.PLUGIN, module.group());
        assertEquals(CacheCleanability.SAFE_NOW, module.cleanability());
        assertEquals(CacheEvictionPolicy.AGE_BASED, module.evictionPolicy());
        assertEquals(1, module.roots().size());
        assertEquals(new File(cache, PluginHttpCache.DIRECTORY).getCanonicalPath(),
                module.roots().get(0).root().getCanonicalPath());
        assertTrue("the whole directory is the module", module.roots().get(0).recursive());
        assertEquals(List.of(), CacheModuleRegistry.validate(cache, modules));
        // Manual cleanup yes, automatic eviction no: silently dropping a cache the plugin expects
        // is a bug report the user cannot diagnose.
        assertFalse(module.protection().allowAutomaticCleanup());
        assertTrue(module.protection().allowManualCleanup());
        assertFalse(CachePolicyEngine.allowsAutomatic(CacheModuleId.PLUGIN_HTTP_CACHE));
        assertTrue(CachePolicyEngine.manualCleanupAllowed(CacheModuleId.PLUGIN_HTTP_CACHE, cache));
    }

    @Test
    public void moduleMeasuresItsOwnEntriesAndTemporaryCleanupLeavesThemAlone() throws Exception {
        File cache = cacheDir("plugin-http-measure");
        File directory = new File(new File(cache, PluginHttpCache.DIRECTORY), "douban.comments");
        assertTrue(directory.mkdirs() || directory.isDirectory());
        String name = "9f2c1b".repeat(10) + "abcd";
        File payload = new File(directory, name);
        Files.write(payload.toPath(), "{\"rating\":8.7}".getBytes(StandardCharsets.UTF_8));

        CacheModule module = CacheModuleRegistry.modules(cache).stream()
                .filter(candidate -> candidate.id() == CacheModuleId.PLUGIN_HTTP_CACHE)
                .findFirst().orElseThrow();
        CacheMeasurement measurement = CacheInventory.measureRoots(CacheModuleId.PLUGIN_HTTP_CACHE,
                module.roots());

        assertEquals(14, measurement.bytes());
        assertEquals(1, measurement.fileCount());
        assertFalse("a suffix-free cache entry must not look like a temporary file",
                CacheCleanupManager.temporaryFamily(cache).matches(name));
    }

    private static File cacheDir(String prefix) throws Exception {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        return Files.createTempDirectory(base, prefix).toFile();
    }
}
