package com.fongmi.android.tv.cache;

public enum CacheEvictionPolicy {
    LRU,
    TTL,
    LRU_WITH_TTL,
    AGE_BASED,
    SIZE_THEN_LRU,
    COUNT_THEN_LRU,
    OWNER_MANAGED
}
