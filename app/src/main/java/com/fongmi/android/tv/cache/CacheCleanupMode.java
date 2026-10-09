package com.fongmi.android.tv.cache;

public enum CacheCleanupMode {
    MODULE,
    LIGHT,
    STANDARD,
    DEEP,
    /**
     * Clears every module at once (the settings row's long-press shortcut). Unlike {@link #DEEP} it
     * also removes the report-only and owner-managed leftovers, so the cache directory really ends
     * up empty the way the pre-cache-management "clear everything" entry left it.
     */
    FULL
}
