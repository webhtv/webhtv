package com.fongmi.android.tv.cache;

/**
 * Decides whether a temporary file must be preserved because a transfer is still using it.
 */
final class CacheTempFilePolicy {

    private CacheTempFilePolicy() {
    }

    static boolean isInUse(String name, boolean updaterDownloading, boolean apkUrlPushing) {
        if (name == null || name.isEmpty()) return false;
        if (updaterDownloading && name.equals("update.apk")) return true;
        return apkUrlPushing && name.startsWith("pushed-url-");
    }
}
