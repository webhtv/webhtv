package com.fongmi.android.tv.cache;

import java.util.List;
import java.util.Set;

/**
 * Versioned rule table for the "legacy / orphan files" module (design §9.13).
 *
 * <p>The previous implementation derived this module from an inverted rule — "everything in the
 * cache root except the managed roots" — which had two defects. It reported live caches that were
 * simply not yet registered (diagnostic logs, {@code plugin-preheat}) as if they were leftovers,
 * and it cleaned only {@code restore-legacy} while measuring the whole orphan tree, so pressing
 * "clean" could never delete what the module had just reported.</p>
 *
 * <p>This table restores the design contract: only explicitly declared paths may enter the legacy
 * module, and every entry declares both what is measured and what is deleted. An entry therefore
 * always cleans exactly what it reported. Renaming or dropping a directory in a later release is
 * expressed by appending a new {@link Rule} with a higher {@code sinceVersion}; nothing is inferred
 * from a file name.</p>
 */
final class CacheLegacyRules {

    /** Current table version. Bump together with any addition or removal of a rule. */
    static final int TABLE_VERSION = 2;

    /**
     * A declared legacy path.
     *
     * @param sinceVersion table version that introduced the rule
     * @param name         path relative to the cache root; a plain file name or a directory name
     * @param recursive    whether a directory is measured and deleted depth-first
     * @param note         why the path is legacy, kept next to the rule for reviewability
     */
    record Rule(int sinceVersion, String name, boolean recursive, String note) {
    }

    /**
     * The rules themselves, ordered oldest first.
     *
     * <p>{@code restore-legacy} is the staging directory written by the legacy (non-zip) backup
     * restore path. It is transient: {@code AppBackup.restoreLegacy} clears it in a {@code finally}
     * block, so anything left behind is a real leftover from an interrupted restore.</p>
     *
     * <p>{@code subtitle_asset} was the subtitle asset cache directory removed when the subtitle
     * store was replaced. Older builds leave it behind, and no current code reads it.</p>
     */
    private static final List<Rule> RULES = List.of(
            new Rule(1, "restore-legacy", true, "legacy backup restore staging directory"),
            new Rule(2, "subtitle_asset", true, "subtitle asset cache from the removed subtitle store")
    );

    private CacheLegacyRules() {
    }

    static List<Rule> rules() {
        return RULES;
    }

    /** Rules whose names must not be treated as ordinary unclassified cache. */
    static Set<String> names() {
        return Set.copyOf(RULES.stream().map(Rule::name).toList());
    }

    /**
     * Builds one measurement and cleanup root per declared rule.
     *
     * <p>Both the inventory and the cleanup path consume this single list, which is what makes the
     * reported size and the deleted size the same set of files.</p>
     */
    static List<CacheRoot> roots(java.io.File cache) {
        java.util.ArrayList<CacheRoot> roots = new java.util.ArrayList<>();
        for (Rule rule : RULES) roots.add(CacheRoot.tree(new java.io.File(cache, rule.name())));
        return List.copyOf(roots);
    }
}
