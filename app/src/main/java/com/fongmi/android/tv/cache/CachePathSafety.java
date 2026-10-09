package com.fongmi.android.tv.cache;

import java.io.File;
import java.io.IOException;

/**
 * Shared filesystem checks for cache scans and cleanup candidates.
 */
final class CachePathSafety {

    private CachePathSafety() {
    }

    /**
     * Returns whether the final path component itself is a symbolic link.
     *
     * <p>Comparing the canonical file with the absolute file is not sufficient on Android: some
     * builds expose the data directory through a symlinked ancestor such as
     * {@code /data/user/0 -> /data/data}. That alias makes every cache path look like a link, so
     * the inventory reports zero bytes and cleanup silently skips every file. Resolve the parent
     * and compare the canonical final component instead.</p>
     */
    static boolean isSymbolicLink(File file) {
        if (file == null) return true;
        try {
            File parent = file.getParentFile();
            if (parent == null) return false;
            File canonical = file.getCanonicalFile();
            File canonicalNameParent = canonical.getParentFile();
            return canonicalNameParent == null
                    || !canonicalNameParent.equals(parent.getCanonicalFile())
                    || !canonical.getName().equals(file.getName());
        } catch (IOException ignored) {
            return true;
        }
    }
}
