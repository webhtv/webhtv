package com.fongmi.android.tv.cache;

import java.util.List;

public record CacheCleanupResult(CacheModuleId id, CacheCleanupStatus status,
                                 long bytesBefore, long bytesAfter, long deletedFiles,
                                 long skippedFiles, List<String> warnings) {

    public long releasedBytes() {
        return Math.max(0, bytesBefore - bytesAfter);
    }
}
