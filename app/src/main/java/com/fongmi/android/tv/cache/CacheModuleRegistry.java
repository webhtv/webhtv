package com.fongmi.android.tv.cache;

import com.github.catvod.crawler.diagnostics.RollingDiagnosticFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure definition of the cache module registry.
 *
 * <p>This class deliberately depends only on the cache root directory. It must never read player
 * settings, sessions or any other runtime state: switching players may not change which modules
 * exist or where they point.</p>
 */
public final class CacheModuleRegistry {

    private static final Set<String> MANAGED_ROOTS = Set.of(
            "exo", "mpv_hls", "mpv-demuxer-cache", "mpv_lut_shaders", "fontconfig",
            "lyrics", "karaoke_tracks", "webhome_ext", "webhome_raw", "epg",
            "image_manager_disk_cache", "js", "py", "jar"
    );

    private static final Set<String> PROTECTED_NAMES = Set.of(
            "mpv-playback-recovery.lock",
            "mpv-playback-recovery.state",
            "mpv-playback-recovery.result"
    );

    private static final Set<String> TEMP_SUFFIXES = Set.of(".apk", ".zip", ".tmp", ".log");

    /**
     * Owner-managed cache files that live in the cache root itself and must not be reported as
     * unclassified cache just because no directory holds them.
     *
     * <p>{@code goProxy.log} is written by {@code LocalProxyDebug}; {@code youtube-mpd.xml} is the
     * sanitized manifest published by {@code DebugLogs}; {@code proc_auxv} is written by the mpv
     * native runtime.</p>
     */
    private static final Set<String> UNCLASSIFIED_FILES = Set.of(
            "goProxy.log", "youtube-mpd.xml", "proc_auxv"
    );

    /**
     * Names that stay out of the {@link CacheModuleId#UNCLASSIFIED} report because another module
     * already owns them: the diagnostic log family is measured by
     * {@link CacheModuleId#DIAGNOSTIC_LOGS}, and the legacy rule table owns its own paths.
     *
     * <p>{@code UNCLASSIFIED} is the one module that deliberately keeps an inverted rule
     * ("everything a known owner does not claim"), because a catch-all report must not silently
     * drop a cache directory a future build starts writing. That inversion is safe here and only
     * here: the module declares {@code allowManualCleanup=false} and
     * {@code allowAutomaticCleanup=false}, so it can never delete the files it reports.</p>
     */
    private static final Set<String> UNCLASSIFIED_EXCLUDED_PREFIXES = Set.of(
            "webhtv-debug-log",
            "webhtv-diagnostic-incident",
            "webhtv-diagnostics.journal"
    );

    /**
     * Exact diagnostic file names that {@link CacheModuleId#TEMP_FILES} would otherwise claim.
     *
     * <p>{@code webhtv-debug-log.txt} ends in {@code .txt} and so never matched the temporary-file
     * suffix list, but the family is listed here so a future suffix change can not silently
     * reclassify live logs as disposable temporary files.</p>
     */
    private static final Set<String> DIAGNOSTIC_LOG_NAMES = Set.of(RollingDiagnosticFile.FILE_NAME);

    private CacheModuleRegistry() {
    }

    public static List<CacheModule> modules(File cache) {
        return List.of(
                module(CacheModuleId.EXO, CacheGroup.PLAYBACK, List.of(CacheRoot.tree(new File(cache, "exo"))), CacheCleanability.DEFER_UNTIL_IDLE, CacheEvictionPolicy.LRU, false, true, true, true),
                module(CacheModuleId.MPV_HLS, CacheGroup.PLAYBACK, List.of(CacheRoot.tree(new File(cache, "mpv_hls"))), CacheCleanability.DEFER_UNTIL_IDLE, CacheEvictionPolicy.LRU_WITH_TTL, false, true, true, true),
                module(CacheModuleId.MPV_DEMUXER, CacheGroup.PLAYBACK, List.of(CacheRoot.tree(new File(cache, "mpv-demuxer-cache"))), CacheCleanability.DEFER_UNTIL_IDLE, CacheEvictionPolicy.OWNER_MANAGED, false, true, true, true),
                module(CacheModuleId.MPV_RUNTIME, CacheGroup.PLAYBACK, List.of(
                        CacheRoot.tree(new File(cache, "mpv_lut_shaders")),
                        CacheRoot.tree(new File(cache, "fontconfig"))
                ), CacheCleanability.DEFER_UNTIL_IDLE, CacheEvictionPolicy.TTL, false, true, true, true),
                module(CacheModuleId.LYRICS, CacheGroup.MEDIA, List.of(CacheRoot.tree(new File(cache, "lyrics"))), CacheCleanability.SAFE_NOW, CacheEvictionPolicy.LRU_WITH_TTL, true, true, false, false),
                module(CacheModuleId.KARAOKE, CacheGroup.MEDIA, List.of(CacheRoot.tree(new File(cache, "karaoke_tracks"))), CacheCleanability.SAFE_NOW, CacheEvictionPolicy.COUNT_THEN_LRU, true, true, false, false),
                module(CacheModuleId.WEBHOME_EXT, CacheGroup.NETWORK, List.of(CacheRoot.tree(new File(cache, "webhome_ext"))), CacheCleanability.SAFE_NOW, CacheEvictionPolicy.LRU_WITH_TTL, true, true, false, false),
                module(CacheModuleId.WEBHOME_RAW, CacheGroup.NETWORK, List.of(CacheRoot.tree(new File(cache, "webhome_raw"))), CacheCleanability.OWNER_MANAGED, CacheEvictionPolicy.LRU, false, true, true, false),
                module(CacheModuleId.EPG, CacheGroup.NETWORK, List.of(CacheRoot.tree(new File(cache, "epg"))), CacheCleanability.SAFE_NOW, CacheEvictionPolicy.TTL, true, true, false, false),
                module(CacheModuleId.GLIDE, CacheGroup.MEDIA, List.of(CacheRoot.tree(new File(cache, "image_manager_disk_cache"))), CacheCleanability.OWNER_MANAGED, CacheEvictionPolicy.OWNER_MANAGED, false, true, true, false),
                module(CacheModuleId.PLUGIN_SCRIPTS, CacheGroup.PLUGIN, List.of(
                        CacheRoot.tree(new File(cache, "js")),
                        CacheRoot.tree(new File(cache, "py")),
                        CacheRoot.tree(new File(cache, "jar"))
                // Manual cleanup is declared not-allowed here (single source of truth for the UI):
                // there is no way to pause plugin loading yet, so deleting a jar/py/js that is
                // currently loading could break a site that is in use.
                ), CacheCleanability.DEFER_UNTIL_IDLE, CacheEvictionPolicy.LRU_WITH_TTL, false, false, true, false),
                module(CacheModuleId.TEMP_FILES, CacheGroup.TEMPORARY, List.of(CacheRoot.files(cache, TEMP_SUFFIXES, union(PROTECTED_NAMES, DIAGNOSTIC_LOG_NAMES))), CacheCleanability.SAFE_NOW, CacheEvictionPolicy.AGE_BASED, true, true, false, false),
                module(CacheModuleId.DIAGNOSTIC_LOGS, CacheGroup.DIAGNOSTIC, List.of(CacheRoot.prefixedFiles(cache, Set.of(
                        RollingDiagnosticFile.FILE_NAME,
                        "webhtv-diagnostic-incident-",
                        "webhtv-diagnostics.journal"
                ), Set.of())), CacheCleanability.SAFE_NOW, CacheEvictionPolicy.AGE_BASED, false, true, false, false),
                module(CacheModuleId.LEGACY_FILES, CacheGroup.LEGACY, CacheLegacyRules.roots(cache), CacheCleanability.SAFE_NOW, CacheEvictionPolicy.AGE_BASED, true, true, false, false),
                module(CacheModuleId.UNCLASSIFIED, CacheGroup.UNCLASSIFIED, List.of(CacheRoot.orphanTree(cache,
                        union(union(MANAGED_ROOTS, PROTECTED_NAMES), union(CacheLegacyRules.names(), UNCLASSIFIED_FILES)),
                        TEMP_SUFFIXES, UNCLASSIFIED_EXCLUDED_PREFIXES)), CacheCleanability.OWNER_MANAGED, CacheEvictionPolicy.OWNER_MANAGED, false, false, false, false)
        );
    }

    /**
     * Validates registry invariants that guard against deleting the wrong data.
     *
     * @return human readable problems; empty when the registry is safe
     */
    public static List<String> validate(File cache, List<CacheModule> modules) {
        ArrayList<String> problems = new ArrayList<>();
        if (cache == null) {
            problems.add("cache root is null");
            return problems;
        }
        if (modules == null) {
            problems.add("module registry is null");
            return problems;
        }
        String cacheKey = canonicalKey(cache);
        if (cacheKey == null) {
            problems.add("cache root is not canonicalizable");
            return problems;
        }
        HashSet<CacheModuleId> ids = new HashSet<>();
        HashSet<String> definitions = new HashSet<>();
        ArrayList<RootEntry> treeRoots = new ArrayList<>();
        for (CacheModule module : modules) {
            if (module == null) {
                problems.add("null module entry");
                continue;
            }
            if (!ids.add(module.id())) problems.add("duplicate module id: " + module.id().id());
            if (module.roots().isEmpty()) problems.add("module without roots: " + module.id().id());
            for (CacheRoot root : module.roots()) {
                if (root == null || root.root() == null) {
                    problems.add("null cache root: " + module.id().id());
                    continue;
                }
                String key = canonicalKey(root.root());
                if (key == null) {
                    problems.add("cache root is not canonicalizable: " + module.id().id());
                    continue;
                }
                if (!isInside(cacheKey, key)) {
                    problems.add("cache root escapes cache dir: " + root.root().getName());
                }
                String definition = module.id().id() + '|' + key + '|' + root.recursive() + '|'
                        + root.includeSuffixes() + '|' + root.excludeNames() + '|' + root.excludeSuffixes()
                        + '|' + root.includePrefixes() + '|' + root.excludePrefixes();
                if (!definitions.add(definition)) {
                    problems.add("duplicate cache root: " + root.root().getName());
                }
                if (isUnfilteredTree(root)) {
                    treeRoots.add(new RootEntry(module.id(), key, root.root().getName()));
                }
            }
        }
        for (int outer = 0; outer < treeRoots.size(); outer++) {
            for (int inner = outer + 1; inner < treeRoots.size(); inner++) {
                RootEntry first = treeRoots.get(outer);
                RootEntry second = treeRoots.get(inner);
                if (first.key.equals(second.key)) {
                    problems.add("overlapping tree roots: " + first.name + " and " + second.name);
                } else if (isInside(first.key, second.key)) {
                    problems.add("nested tree root: " + second.name + " inside " + first.name);
                } else if (isInside(second.key, first.key)) {
                    problems.add("nested tree root: " + first.name + " inside " + second.name);
                }
            }
        }
        return problems;
    }

    static boolean isUnfilteredTree(CacheRoot root) {
        return root.unfiltered();
    }

    static boolean isInside(String parentKey, String childKey) {
        return childKey.equals(parentKey) || childKey.startsWith(parentKey + File.separator);
    }

    static String canonicalKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CacheModule module(CacheModuleId id, CacheGroup group, List<CacheRoot> roots,
                                      CacheCleanability cleanability, CacheEvictionPolicy policy,
                                      boolean automatic, boolean manual, boolean ownerIdle,
                                      boolean appIdle) {
        return new RegistryModule(id, group, roots, cleanability, policy,
                new CacheProtection(automatic, manual, ownerIdle, appIdle, 0, 0, ownerIdle));
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        HashSet<String> result = new HashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private record RegistryModule(CacheModuleId id, CacheGroup group, List<CacheRoot> roots,
                                  CacheCleanability cleanability, CacheEvictionPolicy evictionPolicy,
                                  CacheProtection protection) implements CacheModule {
    }

    private record RootEntry(CacheModuleId id, String key, String name) {
    }
}
