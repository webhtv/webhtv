package com.fongmi.android.tv.cache;

import java.util.List;

public record CacheCleanupPlan(CacheCleanupMode mode, List<CacheModuleId> modules) {
}
