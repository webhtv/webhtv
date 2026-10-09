package com.fongmi.android.tv.cache;

import com.github.catvod.utils.Prefers;

public final class CachePolicyStore {

    private static final String PREFIX = "cache_mgmt_";
    private static final long UNLIMITED = 0L;

    /** Design §11.3: versioned migration marker for the cache management settings. */
    static final int SCHEMA_VERSION = 1;
    private static final String KEY_SCHEMA_VERSION = PREFIX + "schema_version";

    private CachePolicyStore() {
    }

    /**
     * Brings persisted values up to {@link #SCHEMA_VERSION}.
     *
     * <p>Must be called once before any value is read or written (the UI and the scheduler both
     * go through {@link CacheCenter#get()}). Every step normalizes existing keys to safe,
     * clamped values and never removes keys it does not own, so an unknown/older schema keeps
     * working with defaults and a failure can not lose the previous values.</p>
     */
    public static synchronized void migrate() {
        int stored = Prefers.getInt(KEY_SCHEMA_VERSION, 0);
        if (!needsMigration(stored)) return;
        try {
            for (CacheModuleId id : CacheModuleId.values()) {
                if (!supportsPersistedLimit(id)) continue;
                long value = Prefers.getLong(key(id), defaultLimit(id));
                Prefers.put(key(id), Math.max(UNLIMITED, value));
            }
            Prefers.put(PREFIX + "retention_days", clamp(Prefers.getInt(PREFIX + "retention_days", 30), 1, 3650));
            Prefers.put(PREFIX + "total_limit_bytes", Math.max(UNLIMITED, Prefers.getLong(PREFIX + "total_limit_bytes", UNLIMITED)));
            Prefers.put(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        } catch (Throwable ignored) {
            // Keep the old values and fall back to safe defaults; retry on the next launch.
        }
    }

    /** Modules whose limit is a user-tunable preference; playback caches stay player-owned. */
    private static boolean supportsPersistedLimit(CacheModuleId id) {
        return switch (id) {
            case GLIDE, LYRICS, KARAOKE, WEBHOME_EXT, EPG, PLUGIN_SCRIPTS, TEMP_FILES, LEGACY_FILES -> true;
            // Diagnostic logs are bounded by the owner's own rolling segment budget, and
            // unclassified cache is report-only: neither may gain a second, conflicting limit.
            case DIAGNOSTIC_LOGS, UNCLASSIFIED -> false;
            default -> false;
        };
    }

    /** Pure decision so the upgrade contract stays unit-testable without Android prefs. */
    static boolean needsMigration(int storedVersion) {
        return storedVersion < SCHEMA_VERSION;
    }

    public static long getLimit(CacheModuleId id) {
        migrate();
        return Math.max(UNLIMITED, Prefers.getLong(key(id), defaultLimit(id)));
    }

    public static void putLimit(CacheModuleId id, long bytes) {
        Prefers.put(key(id), Math.max(UNLIMITED, bytes));
    }

    public static int getRetentionDays() {
        return clamp(Prefers.getInt(PREFIX + "retention_days", 30), 1, 3650);
    }

    public static void putRetentionDays(int days) {
        Prefers.put(PREFIX + "retention_days", clamp(days, 1, 3650));
    }

    /**
     * Feature switch for the cache management UI (design §23.1).
     *
     * <p>Enabled by default. Setting it to {@code false} restores the legacy settings behaviour
     * (plain total size plus the original one-tap full cache clear) without shipping a new build.</p>
     */
    public static boolean isManagementEnabled() {
        return enabledByDefault(Prefers.getBoolean(PREFIX + "enabled", true));
    }

    /** Cache management is on unless the user or a rollout explicitly disabled it. */
    static boolean enabledByDefault(Boolean stored) {
        return stored == null || stored;
    }

    public static void putManagementEnabled(boolean enabled) {
        Prefers.put(PREFIX + "enabled", enabled);
    }

    public static boolean isAutoCleanupEnabled() {
        return Prefers.getBoolean(PREFIX + "auto_enabled", false);
    }

    public static void putAutoCleanupEnabled(boolean enabled) {
        Prefers.put(PREFIX + "auto_enabled", enabled);
    }

    public static long getTotalLimitBytes() {
        return Math.max(UNLIMITED, Prefers.getLong(PREFIX + "total_limit_bytes", UNLIMITED));
    }

    public static void putTotalLimitBytes(long bytes) {
        Prefers.put(PREFIX + "total_limit_bytes", Math.max(UNLIMITED, bytes));
    }

    public static long defaultLimit(CacheModuleId id) {
        return switch (id) {
            case GLIDE, LYRICS, KARAOKE, WEBHOME_EXT, PLUGIN_SCRIPTS -> 256L * 1024L * 1024L;
            case EPG, TEMP_FILES, LEGACY_FILES -> 128L * 1024L * 1024L;
            // Owner-bounded or report-only: no user-tunable limit applies.
            case DIAGNOSTIC_LOGS, UNCLASSIFIED -> UNLIMITED;
            default -> UNLIMITED;
        };
    }

    private static String key(CacheModuleId id) {
        return PREFIX + "limit_" + id.id().replace('.', '_');
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
