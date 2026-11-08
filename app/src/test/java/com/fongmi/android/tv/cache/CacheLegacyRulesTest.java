package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guards the versioned legacy rule table (design §9.13).
 *
 * <p>The regression these tests lock down: the legacy module used to measure an inverted orphan
 * tree while cleaning only one directory, so its reported size could never be deleted.</p>
 */
public class CacheLegacyRulesTest {

    @Test
    public void everyRuleIsDeclaredWithAVersionAndReason() {
        List<CacheLegacyRules.Rule> rules = CacheLegacyRules.rules();
        assertFalse("the rule table must not be empty", rules.isEmpty());
        HashSet<String> names = new HashSet<>();
        int previousVersion = 0;
        for (CacheLegacyRules.Rule rule : rules) {
            assertTrue("rule needs a name", rule.name() != null && !rule.name().isBlank());
            assertTrue("duplicate rule name: " + rule.name(), names.add(rule.name()));
            assertTrue("a rule must not contain a path separator: " + rule.name(),
                    !rule.name().contains("/") && !rule.name().contains("\\"));
            assertTrue("rule version must be positive: " + rule.name(), rule.sinceVersion() >= 1);
            assertTrue("rule versions must be declared oldest first: " + rule.name(),
                    rule.sinceVersion() >= previousVersion);
            previousVersion = rule.sinceVersion();
            assertTrue("rule needs a documented reason: " + rule.name(),
                    rule.note() != null && !rule.note().isBlank());
        }
        assertEquals("table version must cover every declared rule",
                previousVersion, CacheLegacyRules.TABLE_VERSION);
    }

    @Test
    public void measurementRootsMatchTheDeclaredRulesExactly() throws Exception {
        File cache = cacheDir("legacy-roots");

        List<CacheRoot> roots = CacheLegacyRules.roots(cache);
        HashSet<String> rootNames = new HashSet<>();
        for (CacheRoot root : roots) rootNames.add(root.root().getName());

        assertEquals(CacheLegacyRules.rules().size(), roots.size());
        assertEquals(new HashSet<>(CacheLegacyRules.names()), rootNames);
        for (CacheRoot root : roots) {
            assertTrue("legacy root must be measurable as a tree: " + root.root(),
                    root.recursive() && root.unfiltered());
            assertTrue("legacy root must stay inside the cache dir: " + root.root(),
                    root.root().getCanonicalPath().startsWith(cache.getCanonicalPath() + File.separator));
        }
    }

    /**
     * The decisive regression test: the module's measured set equals the set a cleanup iterates,
     * because both are built from {@link CacheLegacyRules#rules()}.
     */
    @Test
    public void moduleRootsAreBuiltFromTheSameRuleTableCleanupUses() throws Exception {
        File cache = cacheDir("legacy-consistency");

        CacheModule legacy = null;
        for (CacheModule module : CacheModuleRegistry.modules(cache)) {
            if (module.id() == CacheModuleId.LEGACY_FILES) legacy = module;
        }
        assertTrue("legacy module must exist", legacy != null);

        Set<String> moduleRootNames = new HashSet<>();
        for (CacheRoot root : legacy.roots()) moduleRootNames.add(root.root().getName());

        Set<String> cleanedNames = new HashSet<>();
        for (CacheLegacyRules.Rule rule : CacheLegacyRules.rules()) cleanedNames.add(rule.name());

        assertEquals("legacy must measure exactly what it cleans", cleanedNames, moduleRootNames);
    }

    @Test
    public void declaredLegacyNamesStayOutOfTheUnclassifiedReport() throws Exception {
        File cache = cacheDir("legacy-not-unclassified");

        CacheModule unclassified = null;
        for (CacheModule module : CacheModuleRegistry.modules(cache)) {
            if (module.id() == CacheModuleId.UNCLASSIFIED) unclassified = module;
        }
        assertTrue("unclassified module must exist", unclassified != null);

        for (CacheRoot root : unclassified.roots()) {
            for (String name : CacheLegacyRules.names()) {
                assertTrue("legacy path must be excluded from unclassified: " + name,
                        root.excludeNames().contains(name));
            }
        }
    }

    /**
     * The declared rule must not also be registered as a managed root: that is precisely the old
     * incoherence, where {@code restore-legacy} was excluded from measurement and then cleaned.
     */
    @Test
    public void declaredRulePathsAreHonouredByMeasurement() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),
                "legacy-measure");
        try {
            Files.createDirectories(root.resolve("restore-legacy"));
            Files.writeString(root.resolve("restore-legacy/leftover.bin"), "12345678");
            Files.createDirectories(root.resolve("subtitle_asset"));
            Files.writeString(root.resolve("subtitle_asset/old.bin"), "1234");

            CacheMeasurement legacy = CacheInventory.measureRoots(
                    CacheModuleId.LEGACY_FILES, CacheLegacyRules.roots(root.toFile()));

            assertEquals("both declared legacy paths must be measured", 12, legacy.bytes());
            assertEquals(2, legacy.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    /** A path that is not in the table must not be reported as legacy. */
    @Test
    public void undeclaredDirectoriesAreNotLegacy() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),
                "legacy-undeclared");
        try {
            Files.createDirectories(root.resolve("plugin-preheat"));
            Files.writeString(root.resolve("plugin-preheat/live-cache.txt"), "123456");
            Files.writeString(root.resolve("webhtv-debug-log.txt"), "1234");

            CacheMeasurement legacy = CacheInventory.measureRoots(
                    CacheModuleId.LEGACY_FILES, CacheLegacyRules.roots(root.toFile()));

            assertEquals("an unregistered live cache must not be reported as legacy",
                    0, legacy.bytes());
            assertEquals(0, legacy.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    private static File cacheDir(String prefix) throws Exception {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        return Files.createTempDirectory(base, prefix).toFile();
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
