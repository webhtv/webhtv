package com.fongmi.android.tv.cache;

import java.io.File;
import java.util.Set;

/**
 * Declares one measurable and cleanable root inside the cache directory.
 *
 * <p>Filters are declarative so the same definition drives both the read-only inventory and the
 * cleanup path: whatever a root reports is exactly what a cleanup of that root may touch.</p>
 *
 * @param includeSuffixes when non-empty, only files with one of these suffixes are counted
 * @param excludeNames    exact file or directory names that are skipped entirely
 * @param includePrefixes when non-empty, only files whose name starts with one of these prefixes are counted
 * @param excludePrefixes names starting with one of these prefixes are skipped entirely
 */
public record CacheRoot(File root, boolean recursive, Set<String> includeSuffixes,
                        Set<String> excludeNames, Set<String> excludeSuffixes,
                        Set<String> includePrefixes, Set<String> excludePrefixes) {

    public static CacheRoot tree(File root) {
        return new CacheRoot(root, true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static CacheRoot files(File root, Set<String> includeSuffixes, Set<String> excludeNames) {
        return new CacheRoot(root, false, includeSuffixes, excludeNames, Set.of(), Set.of(), Set.of());
    }

    /**
     * Direct children of {@code root} whose name starts with one of {@code prefixes}.
     *
     * <p>Needed for owners that rotate a single logical file under derived names such as
     * {@code webhtv-debug-log.txt.1} or {@code webhtv-diagnostic-incident-2.txt}, where a suffix
     * filter can not address the whole family.</p>
     */
    public static CacheRoot prefixedFiles(File root, Set<String> includePrefixes, Set<String> excludeNames) {
        return new CacheRoot(root, false, Set.of(), excludeNames, Set.of(), includePrefixes, Set.of());
    }

    public static CacheRoot orphanTree(File root, Set<String> excludeNames, Set<String> excludeSuffixes) {
        return new CacheRoot(root, true, Set.of(), excludeNames, excludeSuffixes, Set.of(), Set.of());
    }

    /**
     * Recursive orphan tree that additionally skips names matching {@code excludePrefixes}.
     *
     * <p>Used to keep owner-managed files that a single name filter can not describe out of the
     * unclassified-cache report.</p>
     */
    public static CacheRoot orphanTree(File root, Set<String> excludeNames, Set<String> excludeSuffixes,
                                       Set<String> excludePrefixes) {
        return new CacheRoot(root, true, Set.of(), excludeNames, excludeSuffixes, Set.of(), excludePrefixes);
    }

    /** True when the root accepts every file below it without any filter. */
    boolean unfiltered() {
        return recursive
                && includeSuffixes.isEmpty()
                && excludeNames.isEmpty()
                && excludeSuffixes.isEmpty()
                && includePrefixes.isEmpty()
                && excludePrefixes.isEmpty();
    }
}
