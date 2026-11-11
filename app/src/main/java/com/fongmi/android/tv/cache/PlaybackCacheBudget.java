package com.fongmi.android.tv.cache;

/**
 * Splits one total cache limit between the playback cache and every other cache module.
 *
 * <p>Both players answer to the same rule: Exo sizes its SimpleCache capacity with it and MPV
 * prunes its HLS disk cache with it, so the arithmetic lives here instead of in either engine.</p>
 */
public final class PlaybackCacheBudget {

    private PlaybackCacheBudget() {
    }

    /**
     * The capacity for one phase of the cache lifetime.
     *
     * <p>Playback is deliberately not bounded by the total limit: shrinking the cache while a
     * player reads it trades buffering headroom, seek range and re-buffering risk for disk savings
     * the user cannot see until playback stops. The total limit applies once nothing plays, which
     * is also when the panel reports the number.</p>
     */
    public static long capacityBytes(boolean playing, long totalLimitBytes, long preloadBytes, long othersBytes) {
        return playing ? Math.max(0, preloadBytes) : capacityBytes(totalLimitBytes, preloadBytes, othersBytes);
    }

    /**
     * The idle capacity: the total limit is one budget shared by every cache module, so the
     * playback cache may only use what the other modules leave over.
     *
     * <p>The configured size stays a ceiling: a user who configured a 2 GiB preload does not want a
     * larger cache just because the total limit was raised above it. A zero total limit means
     * "unlimited" and leaves that configured size untouched, which keeps the previous behaviour for
     * every user who never opened the cache panel.</p>
     *
     * <p>The shared budget is never exceeded on behalf of this module: when the other modules
     * already fill the total limit the playback cache converges to zero and stops writing, because
     * the total limit is the user's statement about the whole cache directory, not a hint that the
     * playback cache may still keep a minimum of its own. Playback stays exempt while it runs, so
     * the user-visible cost of a tight limit is a cold cache on the next play, never a cut buffer.</p>
     */
    public static long capacityBytes(long totalLimitBytes, long preloadBytes, long othersBytes) {
        long preload = Math.max(0, preloadBytes);
        long total = Math.max(0, totalLimitBytes);
        if (total <= 0) return preload;
        long remaining = total - Math.max(0, othersBytes);
        return Math.min(preload, Math.max(0, remaining));
    }

    /**
     * The ceiling a user-configured playback limit applies to the preload setting.
     *
     * <p>The panel's playback row is the only place a user can bound a playback cache by hand, so
     * the value it stores has to reach the capacity the player computes. It may only lower that
     * ceiling: the unlimited default leaves the preload setting deciding on its own.</p>
     */
    public static long ceilingBytes(long preloadBytes, long userLimitBytes) {
        long preload = Math.max(0, preloadBytes);
        long limit = Math.max(0, userLimitBytes);
        return limit > 0 ? Math.min(preload, limit) : preload;
    }
}
