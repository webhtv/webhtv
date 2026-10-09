package com.fongmi.android.tv.cache;

import java.util.List;

public record CacheCleanupRecord(long startedAtMs, long durationMs, String reason,
                                 CacheCleanupMode mode, CacheCleanupStatus status,
                                 long bytesBefore, long bytesAfter, long deletedFiles,
                                 long skippedFiles, List<String> warnings) {

    public long releasedBytes() {
        return Math.max(0, bytesBefore - bytesAfter);
    }
}
