package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CachePathSafetyTest {

    @Test
    public void ancestorSymlinkDoesNotHideRegularCacheFiles() throws Exception {
        Path real = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-alias-real");
        Path alias = real.getParent().resolve(real.getFileName() + "-alias");
        try {
            Files.createSymbolicLink(alias, real);
        } catch (UnsupportedOperationException | FileSystemException error) {
            delete(real.toFile());
            Assume.assumeTrue("symbolic links unavailable", false);
        }
        try {
            Files.createDirectories(real.resolve("data/lyrics"));
            Files.writeString(real.resolve("data/lyrics/marker.bin"), "1234", StandardCharsets.UTF_8);

            File aliased = alias.resolve("data/lyrics/marker.bin").toFile();
            assertTrue(aliased.isFile());
            assertFalse(CachePathSafety.isSymbolicLink(aliased));

            CacheMeasurement result = CacheInventory.measureRoots(
                    CacheModuleId.LYRICS, List.of(CacheRoot.tree(alias.resolve("data/lyrics").toFile())));

            assertEquals(4, result.bytes());
            assertEquals(1, result.fileCount());
            assertEquals(CacheAvailability.AVAILABLE, result.availability());
        } finally {
            Files.deleteIfExists(alias);
            delete(real.toFile());
        }
    }

    @Test
    public void finalComponentSymlinkIsStillDetected() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-link-root");
        Path target = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "cache-link-target");
        try {
            Files.writeString(target.resolve("victim.bin"), "secret", StandardCharsets.UTF_8);
            try {
                Files.createSymbolicLink(root.resolve("link.bin"), target.resolve("victim.bin"));
            } catch (UnsupportedOperationException | FileSystemException error) {
                Assume.assumeTrue("symbolic links unavailable", false);
            }

            // The production check compares the canonical path with the parent-canonical + name
            // composition. That comparison only sees a link when File.getCanonicalFile() resolves
            // it, which is the documented behaviour on Linux/Android but not on every host JVM
            // (Windows keeps the link path). Skip on hosts whose canonicalization does not follow
            // links, otherwise this test would fail for platform reasons instead of code reasons.
            File link = root.resolve("link.bin").toFile();
            Assume.assumeTrue("File.getCanonicalFile() does not follow symbolic links on this host",
                    !link.getCanonicalFile().equals(link.getAbsoluteFile()));

            assertTrue(CachePathSafety.isSymbolicLink(link));

            CacheMeasurement result = CacheInventory.measureRoots(
                    CacheModuleId.LYRICS, List.of(CacheRoot.tree(root.toFile())));

            assertEquals(0, result.bytes());
            assertEquals(0, result.fileCount());
            assertEquals(CacheAvailability.PARTIAL, result.availability());
            assertTrue(target.resolve("victim.bin").toFile().exists());
        } finally {
            Files.deleteIfExists(root.resolve("link.bin"));
            delete(root.toFile());
            delete(target.toFile());
        }
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
