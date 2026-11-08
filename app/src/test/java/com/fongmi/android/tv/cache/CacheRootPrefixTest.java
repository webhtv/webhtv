package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Covers the prefix filters added for owners that rotate one logical file under derived names,
 * such as the diagnostic log family ({@code webhtv-debug-log.txt}, {@code ...txt.1},
 * {@code webhtv-diagnostic-incident-0.txt}). A suffix filter can not address that family.
 */
public class CacheRootPrefixTest {

    @Test
    public void includePrefixCountsOnlyTheDeclaredFamily() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),
                "root-prefix-include");
        try {
            Files.writeString(root.resolve("webhtv-debug-log.txt"), "1234", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("webhtv-debug-log.txt.1"), "123456", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("webhtv-diagnostic-incident-0.txt"), "12", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("update.apk"), "12345678", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("tv.lck"), "1", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(CacheModuleId.DIAGNOSTIC_LOGS,
                    List.of(CacheRoot.prefixedFiles(root.toFile(),
                            Set.of("webhtv-debug-log", "webhtv-diagnostic-incident-"), Set.of())));

            assertEquals(12, result.bytes());
            assertEquals(3, result.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    @Test
    public void excludePrefixWinsOverIncludePrefix() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),
                "root-prefix-exclude");
        try {
            Files.writeString(root.resolve("webhtv-debug-log.txt"), "1234", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("webhtv-diagnostics.journal"), "123456", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(CacheModuleId.DIAGNOSTIC_LOGS,
                    List.of(CacheRoot.prefixedFiles(root.toFile(),
                            Set.of("webhtv-"), Set.of("webhtv-diagnostics.journal"))));

            assertEquals(4, result.bytes());
            assertEquals(1, result.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    /** An unfiltered recursive root must still count everything below it. */
    @Test
    public void prefixFiltersDoNotChangePlainTreeBehaviour() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),
                "root-prefix-tree");
        try {
            Files.createDirectories(root.resolve("nested"));
            Files.writeString(root.resolve("nested/a.txt"), "12345", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(CacheModuleId.LYRICS,
                    List.of(CacheRoot.tree(root.toFile())));

            assertEquals(5, result.bytes());
            assertEquals(1, result.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    @Test
    public void unfilteredReportsOnlyUnfilteredRoots() {
        File any = new File(System.getProperty("java.io.tmpdir"));
        assertTrue(CacheRoot.tree(any).unfiltered());
        assertFalse(CacheRoot.prefixedFiles(any, Set.of("a"), Set.of()).unfiltered());
        assertFalse(CacheRoot.files(any, Set.of(".apk"), Set.of()).unfiltered());
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
