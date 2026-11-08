package com.fongmi.android.tv.cache;

public record CacheCleanupProgress(CacheModuleId moduleId, int completedModules,
                                   int totalModules, long bytesBefore, long bytesAfter) {
}
