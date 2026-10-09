package com.fongmi.android.tv.cache;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

public final class CacheRetentionManager {

    private CacheRetentionManager() {
    }

    public static boolean applyLimit(File root, long limitBytes, long retentionMs,
                                     LongSupplier clock, Set<String> excludedNames) {
        if (root == null || !root.isDirectory()) return true;
        ArrayList<File> files = new ArrayList<>();
        collect(root, files, excludedNames);
        long now = clock.getAsLong();
        boolean success = true;
        if (retentionMs > 0) {
            for (File file : files) {
                long modified = file.lastModified();
                if (modified > 0 && now - modified >= retentionMs && !file.delete()) success = false;
            }
        }
        files.removeIf(file -> !file.exists());
        long total = totalBytes(files);
        if (limitBytes <= 0) return success;
        long target = Math.max(0, limitBytes * 9 / 10);
        files.sort(Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= target) break;
            long size = Math.max(0, file.length());
            if (file.delete()) total -= size;
            else success = false;
        }
        return success;
    }

    /**
     * Evicts the oldest eligible files until the total drops to 90% of {@code limitBytes}.
     *
     * <p>Every candidate must live inside {@code allowedRoot} and must not be a symlink. This
     * enforces the design invariant that all deletions stay inside a declared root, even when a
     * caller passes a hand-built file list.</p>
     */
    public static boolean enforceFileLimit(File allowedRoot, List<File> files, long limitBytes,
                                           long minimumAgeMs, long now) {
        if (limitBytes <= 0 || files == null || files.isEmpty()) return true;
        String rootKey = allowedRoot == null ? null : canonicalKey(allowedRoot);
        if (rootKey == null) return false;
        ArrayList<File> eligible = new ArrayList<>();
        for (File file : files) {
            if (isInside(rootKey, file)) eligible.add(file);
        }
        long total = totalBytes(eligible);
        if (total <= limitBytes) return true;
        long target = Math.max(0, limitBytes * 9 / 10);
        ArrayList<File> ordered = new ArrayList<>(eligible);
        ordered.sort(Comparator.comparingLong(File::lastModified));
        boolean success = true;
        for (File file : ordered) {
            if (total <= target) break;
            long modified = file.lastModified();
            if (minimumAgeMs > 0 && modified > 0 && now - modified < minimumAgeMs) continue;
            long size = Math.max(0, file.length());
            if (file.delete()) total -= size;
            else success = false;
        }
        return success;
    }

    static boolean isInside(String rootKey, File file) {
        if (file == null) return false;
        try {
            if (CachePathSafety.isSymbolicLink(file)) return false;
            String key = file.getCanonicalPath();
            return key.startsWith(rootKey + File.separator);
        } catch (java.io.IOException ignored) {
            return false;
        }
    }

    static String canonicalKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (java.io.IOException ignored) {
            return null;
        }
    }

    private static void collect(File file, List<File> output, Set<String> excludedNames) {
        if (file == null || excludedNames.contains(file.getName())) return;
        if (CachePathSafety.isSymbolicLink(file)) return;
        if (file.isFile()) {
            output.add(file);
            return;
        }
        File[] children = file.listFiles();
        if (children == null) return;
        for (File child : children) collect(child, output, excludedNames);
    }

    private static long totalBytes(List<File> files) {
        long total = 0;
        for (File file : files) total = saturatedAdd(total, Math.max(0, file.length()));
        return total;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

}
