package com.fongmi.android.tv.cache;

public final class ExoCapacityBudget {

    /** Keeps SimpleCache constructible when the other modules already exceed the total limit. */
    public static final long MIN_BUDGET_BYTES = 64L * 1024L * 1024L;

    private ExoCapacityBudget() {
    }

    /**
     * The total limit is one budget shared by every cache module, so the playback cache may only
     * use what the other modules leave over.
     *
     * <p>The preload setting stays the ceiling: a user who configured a 2 GiB preload does not
     * want a larger cache just because the total limit was raised above it. A zero total limit
     * means "unlimited" and leaves the preload setting untouched, which keeps the previous
     * behaviour for every user who never opened the cache panel.</p>
     */
    /**
     * The capacity for one phase of the cache lifetime.
     *
     * <p>Playback is deliberately not bounded by the total limit: shrinking the cache while a
     * player reads it trades buffering headroom, seek range and re-buffering risk for disk savings
     * the user cannot see until playback stops. The total limit applies while nothing plays, which
     * is also when the panel reports the number.</p>
     */
    public static long capacityBytes(boolean playing, long totalLimitBytes, long preloadBytes, long othersBytes) {
        return playing ? Math.max(0, preloadBytes) : capacityBytes(totalLimitBytes, preloadBytes, othersBytes);
    }

    public static long capacityBytes(long totalLimitBytes, long preloadBytes, long othersBytes) {
        return capacityBytes(totalLimitBytes, preloadBytes, othersBytes, MIN_BUDGET_BYTES);
    }

    static long capacityBytes(long totalLimitBytes, long preloadBytes, long othersBytes, long floorBytes) {
        long preload = Math.max(0, preloadBytes);
        long total = Math.max(0, totalLimitBytes);
        if (total <= 0) return preload;
        long remaining = total - Math.max(0, othersBytes);
        return Math.min(preload, Math.max(Math.max(0, floorBytes), remaining));
    }
}
