package com.fongmi.android.tv.cache;

import java.util.Locale;

public final class CacheFormat {

    private CacheFormat() {
    }

    public static String percent(long bytes, long totalBytes) {
        if (bytes <= 0 || totalBytes <= 0) return "0%";
        double percent = bytes * 100.0d / totalBytes;
        if (percent < 0.1d) return "<0.1%";
        return String.format(Locale.getDefault(), "%.1f%%", percent);
    }
}
