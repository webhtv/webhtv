package com.fongmi.android.tv.cache;

import java.util.List;

public record CacheMeasurement(CacheModuleId id, long bytes, long fileCount,
                               long oldestModifiedMs, long newestModifiedMs,
                               CacheAvailability availability, List<String> warnings) {

    public static CacheMeasurement unavailable(CacheModuleId id, String warning) {
        return new CacheMeasurement(id, 0, 0, 0, 0, CacheAvailability.UNAVAILABLE, List.of(warning));
    }
}
