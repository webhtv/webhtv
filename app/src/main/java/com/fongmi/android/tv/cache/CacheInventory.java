package com.fongmi.android.tv.cache;

import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;

import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class CacheInventory {

    private static final long MODULE_TIMEOUT_NS = 2_000_000_000L;

    private final Context context;

    public CacheInventory(Context context) {
        this.context = context.getApplicationContext();
    }

    public CacheSnapshot scan() {
        long startNs = System.nanoTime();
        File cache = Path.cache();
        List<CacheModule> modules = CacheModuleRegistry.modules(cache);
        ArrayList<CacheMeasurement> measurements = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        for (String problem : CacheModuleRegistry.validate(cache, modules)) {
            warnings.add("registry: " + problem);
        }
        long totalBytes = 0;
        for (CacheModule module : modules) {
            CacheMeasurement measurement = measure(module, System.nanoTime() + MODULE_TIMEOUT_NS);
            measurements.add(measurement);
            totalBytes = saturatedAdd(totalBytes, measurement.bytes());
            warnings.addAll(measurement.warnings());
        }
        FileUtil.StorageSpace storage = FileUtil.getStorageSpace(Path.cache());
        return new CacheSnapshot(
                System.nanoTime() / 1_000_000L,
                totalBytes,
                cacheQuotaBytes(context),
                storage.availableBytes(),
                storage.totalBytes(),
                Collections.unmodifiableList(measurements),
                Collections.unmodifiableList(warnings)
        );
    }

    static CacheMeasurement measure(CacheModule module, long deadlineNs) {
        return measureRoots(module.id(), module.roots(), deadlineNs);
    }

    static CacheModule find(CacheModuleId id) {
        if (id == null) return null;
        for (CacheModule module : modules()) if (module.id() == id) return module;
        return null;
    }

    static CacheMeasurement measureRoots(CacheModuleId id, List<CacheRoot> roots) {
        return measureRoots(id, roots, Long.MAX_VALUE);
    }

    static CacheMeasurement measureRoots(CacheModuleId id, List<CacheRoot> roots, long deadlineNs) {
        ScanResult result = new ScanResult();
        for (CacheRoot root : roots) {
            if (root == null || root.root() == null) continue;
            scan(root.root(), root, result, deadlineNs, 0);
        }
        CacheAvailability availability = result.warnings.isEmpty()
                ? result.fileCount == 0 ? CacheAvailability.EMPTY : CacheAvailability.AVAILABLE
                : CacheAvailability.PARTIAL;
        return new CacheMeasurement(
                id,
                result.bytes,
                result.fileCount,
                result.oldestModifiedMs == Long.MAX_VALUE ? 0 : result.oldestModifiedMs,
                result.newestModifiedMs,
                availability,
                Collections.unmodifiableList(result.warnings)
        );
    }

    private static List<CacheModule> modules() {
        return CacheModuleRegistry.modules(Path.cache());
    }

    private static void scan(File file, CacheRoot root, ScanResult result, long deadlineNs, int depth) {
        if (file == null || System.nanoTime() > deadlineNs) {
            if (file != null && System.nanoTime() > deadlineNs) result.warning("scan timeout");
            return;
        }
        if (CachePathSafety.isSymbolicLink(file)) {
            result.warning("symbolic link skipped: " + file.getName());
            return;
        }
        if (root.excludeNames().contains(file.getName())) return;
        if (matchesAnyPrefix(file.getName(), root.excludePrefixes())) return;
        if (file.isFile()) {
            if (!root.recursive() && depth > 1) return;
            if (!root.includePrefixes().isEmpty()
                    && !matchesAnyPrefix(file.getName(), root.includePrefixes())) return;
            if (!root.excludeSuffixes().isEmpty()
                    && matchesAnySuffix(file.getName(), root.excludeSuffixes())) return;
            if (!matchesSuffix(file.getName(), root.includeSuffixes())) return;
            result.accept(file);
            return;
        }
        if (!file.isDirectory()) return;
        File[] children = file.listFiles();
        if (children == null) {
            result.warning("unreadable directory: " + file.getName());
            return;
        }
        if (children.length == 0) return;
        if (!root.recursive() && depth > 0) return;
        for (File child : children) scan(child, root, result, deadlineNs, depth + 1);
    }

    private static boolean matchesSuffix(String name, Set<String> suffixes) {
        if (suffixes.isEmpty()) return true;
        return matchesAnySuffix(name, suffixes);
    }

    private static boolean matchesAnyPrefix(String name, Set<String> prefixes) {
        for (String prefix : prefixes) if (name.startsWith(prefix)) return true;
        return false;
    }

    private static boolean matchesAnySuffix(String name, Set<String> suffixes) {
        for (String suffix : suffixes) if (name.endsWith(suffix)) return true;
        return false;
    }

    private static long cacheQuotaBytes(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0;
        try {
            StorageManager manager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            return manager == null ? 0 : Math.max(0, manager.getCacheQuotaBytes(manager.getUuidForPath(Path.cache())));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static final class ScanResult {
        private long bytes;
        private long fileCount;
        private long oldestModifiedMs = Long.MAX_VALUE;
        private long newestModifiedMs;
        private final ArrayList<String> warnings = new ArrayList<>();

        private void accept(File file) {
            bytes = saturatedAdd(bytes, Math.max(0, file.length()));
            fileCount = saturatedAdd(fileCount, 1);
            long modified = file.lastModified();
            if (modified > 0) {
                if (oldestModifiedMs == Long.MAX_VALUE || modified < oldestModifiedMs) oldestModifiedMs = modified;
                if (modified > newestModifiedMs) newestModifiedMs = modified;
            }
        }

        private void warning(String warning) {
            if (!warnings.contains(warning)) warnings.add(warning);
        }
    }
}
