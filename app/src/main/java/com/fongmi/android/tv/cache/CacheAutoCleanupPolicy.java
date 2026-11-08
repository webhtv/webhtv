package com.fongmi.android.tv.cache;

public final class CacheAutoCleanupPolicy {

    public static final long MIN_RESERVE_BYTES = 512L * 1024L * 1024L;

    private CacheAutoCleanupPolicy() {
    }

    public static boolean isLowSpace(long availableBytes, long totalBytes) {
        long threshold = Math.max(MIN_RESERVE_BYTES, totalBytes * 10L / 100L);
        return availableBytes >= 0 && totalBytes > 0 && availableBytes < threshold;
    }

    public static CacheCleanupMode cleanupMode(boolean playing, boolean lowSpace) {
        if (playing) return CacheCleanupMode.LIGHT;
        return lowSpace ? CacheCleanupMode.STANDARD : CacheCleanupMode.LIGHT;
    }

    public static boolean shouldTrigger(int lowSpaceStreak, boolean periodic) {
        return periodic || lowSpaceStreak >= 2;
    }
}
