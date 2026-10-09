package com.fongmi.android.tv.cache;

public final class CacheTotalLimitPolicy {

    private CacheTotalLimitPolicy() {
    }

    public static long effectiveLimit(long configuredLimitBytes, long systemQuotaBytes) {
        long configured = Math.max(0, configuredLimitBytes);
        if (configured <= 0) return 0;
        long quota = Math.max(0, systemQuotaBytes);
        return quota > 0 ? Math.min(configured, quota) : configured;
    }

    public static boolean overLimit(long totalBytes, long effectiveLimitBytes) {
        return effectiveLimitBytes > 0 && Math.max(0, totalBytes) > effectiveLimitBytes;
    }
}
