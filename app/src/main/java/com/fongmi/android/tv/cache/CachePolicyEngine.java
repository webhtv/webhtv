package com.fongmi.android.tv.cache;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CachePolicyEngine {

    private static final Set<CacheModuleId> LIGHT = EnumSet.of(
            CacheModuleId.EPG,
            CacheModuleId.TEMP_FILES,
            CacheModuleId.LEGACY_FILES
    );
    private static final Set<CacheModuleId> STANDARD = EnumSet.of(
            CacheModuleId.EPG,
            CacheModuleId.TEMP_FILES,
            CacheModuleId.LEGACY_FILES,
            CacheModuleId.GLIDE,
            CacheModuleId.LYRICS,
            CacheModuleId.KARAOKE,
            CacheModuleId.WEBHOME_EXT,
            CacheModuleId.WEBHOME_RAW
    );

    private static final Set<CacheModuleId> DEEP = deepModules();

    /**
     * L3 by design §12.1 is L2 plus the playback caches, and nothing else.
     *
     * <p>The two report-only modules added with the legacy fix are excluded explicitly: a tiered
     * run must not remove diagnostic logs (owner-bounded, evidence the user may still need) or
     * unclassified cache (owner unknown). Both remain reachable through their own module row, which
     * is a separate {@link CacheCleanupMode#MODULE} plan and therefore unaffected.</p>
     */
    private static Set<CacheModuleId> deepModules() {
        EnumSet<CacheModuleId> ids = EnumSet.allOf(CacheModuleId.class);
        ids.remove(CacheModuleId.DIAGNOSTIC_LOGS);
        ids.remove(CacheModuleId.UNCLASSIFIED);
        return ids;
    }

    private CachePolicyEngine() {
    }

    public static CacheCleanupPlan plan(CacheCleanupMode mode) {
        return new CacheCleanupPlan(mode == null ? CacheCleanupMode.LIGHT : mode,
                List.copyOf(modules(mode == null ? CacheCleanupMode.LIGHT : mode)));
    }

    public static CacheCleanupPlan module(CacheModuleId id) {
        return new CacheCleanupPlan(CacheCleanupMode.MODULE, id == null ? List.of() : List.of(id));
    }

    public static boolean allowsAutomatic(CacheModuleId id) {
        return id != null && switch (id) {
            case LYRICS, KARAOKE, WEBHOME_EXT, EPG, TEMP_FILES, LEGACY_FILES -> true;
            default -> false;
        };
    }

    /**
     * Whether the user may trigger this module's cleanup from the UI (design §14.3).
     *
     * <p>Read from the registry rather than hardcoded per call site, so a module that declares
     * itself owner-managed can not be shown as directly cleanable.</p>
     *
     * <p>This is a pure function: it builds the registry from the supplied cache directory instead
     * of resolving the process cache dir, so it is testable without an Android context and can not
     * drift with runtime state.</p>
     */
    public static boolean manualCleanupAllowed(CacheModuleId id, java.io.File cache) {
        if (id == null || cache == null) return false;
        for (CacheModule module : CacheModuleRegistry.modules(cache)) {
            if (module.id() == id) return module.protection().allowManualCleanup();
        }
        return false;
    }

    public static CacheCleanupStatus directCleanupStatus(CacheModuleId id, boolean playing) {
        if (id == null) return CacheCleanupStatus.NOT_ALLOWED;
        return switch (id) {
            case EXO, MPV_HLS, MPV_DEMUXER, MPV_RUNTIME, KARAOKE -> playing
                    ? CacheCleanupStatus.DEFERRED : CacheCleanupStatus.COMPLETED;
            case PLUGIN_SCRIPTS -> CacheCleanupStatus.NOT_ALLOWED;
            // An owner-managed or unclassified module must never run through direct cleanup.
            case UNCLASSIFIED -> CacheCleanupStatus.NOT_ALLOWED;
            default -> CacheCleanupStatus.COMPLETED;
        };
    }

    private static List<CacheModuleId> modules(CacheCleanupMode mode) {
        Set<CacheModuleId> selected = switch (mode) {
            case LIGHT -> LIGHT;
            case STANDARD -> STANDARD;
            case DEEP -> DEEP;
            // The long-press shortcut is documented as "the one-key clear from before the cache
            // management split", so it must cover every cache the panel can name - including the
            // report-only leftovers a tiered run deliberately protects.
            case FULL -> EnumSet.allOf(CacheModuleId.class);
            case MODULE -> Set.of();
        };
        ArrayList<CacheModuleId> ordered = new ArrayList<>();
        for (CacheModuleId id : CacheModuleId.values()) if (selected.contains(id)) ordered.add(id);
        return ordered;
    }
}
