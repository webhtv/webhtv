package com.fongmi.android.tv.cache;

import java.util.List;

public record CacheSnapshot(long generatedAtElapsedMs, long totalBytes,
                            long systemQuotaBytes, long availableBytes,
                            long totalStorageBytes, List<CacheMeasurement> modules,
                            List<String> warnings) {

    public CacheMeasurement find(CacheModuleId id) {
        for (CacheMeasurement module : modules) if (module.id() == id) return module;
        return CacheMeasurement.unavailable(id, "missing measurement");
    }
}
