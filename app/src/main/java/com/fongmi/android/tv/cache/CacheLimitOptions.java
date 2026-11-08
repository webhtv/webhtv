package com.fongmi.android.tv.cache;

public final class CacheLimitOptions {

    public static final long[] BYTES = {
            0L,
            64L * 1024L * 1024L,
            128L * 1024L * 1024L,
            256L * 1024L * 1024L,
            512L * 1024L * 1024L,
            1024L * 1024L * 1024L
    };

    private CacheLimitOptions() {
    }

    public static int indexOf(long bytes) {
        for (int index = 0; index < BYTES.length; index++) {
            if (BYTES[index] == bytes) return index;
        }
        return 0;
    }
}
