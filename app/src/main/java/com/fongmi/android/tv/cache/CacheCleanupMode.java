package com.fongmi.android.tv.cache;

public enum CacheCleanupMode {
    MODULE,
    LIGHT,
    STANDARD,
    DEEP,
    /**
     * Clears every module at once (the settings row's plain click). Unlike {@link #DEEP} it also
     * removes the report-only and owner-managed leftovers, so the cache directory really ends up
     * empty the way the pre-cache-management "clear everything" entry left it.
     */
    FULL,
    /**
     * Brings the whole cache directory back under the configured total limit once nothing plays.
     *
     * <p>Not a user-pressed tier: the module list comes from {@code CachePolicyEngine
     * .convergencePlan} and covers every module the registry lets a user clean by hand, so the
     * playback caches are reclaimable while the residual sweep of {@link #FULL} - plugin scripts
     * and unclassified cache - stays out of it. It keeps no age windows, because a total limit the
     * cache can not reach by deleting fresh files would be a limit that never takes effect.</p>
     */
    CONVERGE
}
