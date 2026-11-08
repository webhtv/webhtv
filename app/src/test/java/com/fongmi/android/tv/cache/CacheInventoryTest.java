package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class CacheInventoryTest {

    @Test
    public void percentageHandlesZeroSmallAndNormalValues() {
        assertEquals("0%", CacheFormat.percent(0, 100));
        assertEquals("<0.1%", CacheFormat.percent(1, 10_000));
        assertEquals("25.0%", CacheFormat.percent(25, 100));
    }

    @Test
    public void measuresRecursiveFilesAndTimes() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-inventory-recursive");
        try {
            Files.writeString(root.resolve("a.bin"), "abc", StandardCharsets.UTF_8);
            Files.createDirectories(root.resolve("nested"));
            Files.writeString(root.resolve("nested/b.bin"), "12345", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(
                    CacheModuleId.EXO, List.of(CacheRoot.tree(root.toFile())));

            assertEquals(8, result.bytes());
            assertEquals(2, result.fileCount());
            assertEquals(CacheAvailability.AVAILABLE, result.availability());
        } finally {
            delete(root.toFile());
        }
    }

    @Test
    public void filesOnlyRootCountsDirectChildrenAndAppliesSuffixFilter() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-inventory-files");
        try {
            Files.writeString(root.resolve("keep.apk"), "1234", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("skip.zip"), "123456", StandardCharsets.UTF_8);
            Files.createDirectories(root.resolve("nested"));
            Files.writeString(root.resolve("nested/keep.apk"), "12345678", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(
                    CacheModuleId.TEMP_FILES,
                    List.of(CacheRoot.files(root.toFile(), Set.of(".apk"), Set.of())));

            assertEquals(4, result.bytes());
            assertEquals(1, result.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    @Test
    public void excludesProtectedFiles() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-inventory-protected");
        try {
            Files.writeString(root.resolve("update.apk"), "12345", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("mpv-playback-recovery.lock"), "ignored", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(
                    CacheModuleId.TEMP_FILES,
                    List.of(CacheRoot.files(root.toFile(), Set.of(), Set.of("mpv-playback-recovery.lock"))));

            assertEquals(5, result.bytes());
            assertEquals(1, result.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    @Test
    public void missingDirectoryIsEmptyAndDoesNotCreateIt() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-inventory-missing").resolve("missing");

        CacheMeasurement result = CacheInventory.measureRoots(
                CacheModuleId.LYRICS, List.of(CacheRoot.tree(root.toFile())));

        assertEquals(0, result.bytes());
        assertEquals(0, result.fileCount());
        assertEquals(CacheAvailability.EMPTY, result.availability());
        assertEquals(false, Files.exists(root));
    }

    @Test
    public void orphanRootSkipsManagedDirectories() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-inventory-orphan");
        try {
            Files.createDirectories(root.resolve("exo"));
            Files.writeString(root.resolve("exo/managed.bin"), "123456", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("orphan.tmp"), "12", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("keep.txt"), "1234", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(
                    CacheModuleId.LEGACY_FILES,
                    List.of(CacheRoot.orphanTree(root.toFile(), Set.of("exo"), Set.of(".tmp"))));

            assertEquals(4, result.bytes());
            assertEquals(1, result.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    /**
     * A name excluded by prefix applies to both files and directories, so an owner-managed family
     * such as the diagnostic logs is never reported as unclassified cache.
     */
    @Test
    public void orphanRootHonoursExcludePrefixes() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-inventory-orphan-prefix");
        try {
            Files.writeString(root.resolve("webhtv-debug-log.txt"), "1234", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("webhtv-diagnostic-incident-0.txt"), "12", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("unknown.bin"), "123456", StandardCharsets.UTF_8);

            CacheMeasurement result = CacheInventory.measureRoots(
                    CacheModuleId.UNCLASSIFIED,
                    List.of(CacheRoot.orphanTree(root.toFile(), Set.of(), Set.of(),
                            Set.of("webhtv-debug-log", "webhtv-diagnostic-incident-"))));

            assertEquals(6, result.bytes());
            assertEquals(1, result.fileCount());
        } finally {
            delete(root.toFile());
        }
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
