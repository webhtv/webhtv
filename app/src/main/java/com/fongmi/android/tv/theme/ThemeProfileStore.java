package com.fongmi.android.tv.theme;

import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

/** SharedPreferences-backed store for the v2 profile with last-good recovery. */
public final class ThemeProfileStore {

    public static final String KEY_PROFILE = "theme_profile_v2_json";
    public static final String KEY_LAST_GOOD = "theme_profile_v2_last_good";
    public static final String KEY_SCHEMA = "theme_profile_v2_schema";
    public static final String LEGACY_KEY_PROFILE = "theme_profile_json";

    private ThemeProfileStore() {
    }

    /** Reads the active profile; corrupted or missing data never blocks the caller. */
    public static ThemeProfile load() {
        ThemeProfile current = parse(Prefers.getString(KEY_PROFILE));
        if (current != null) return current;
        ThemeProfile lastGood = parse(Prefers.getString(KEY_LAST_GOOD));
        return lastGood != null ? lastGood : migrateLegacy(Setting.getThemeColor(), Prefers.getString(LEGACY_KEY_PROFILE));
    }

    /** Returns the last successfully persisted profile, or null when none is usable. */
    public static ThemeProfile loadLastGood() {
        return parse(Prefers.getString(KEY_LAST_GOOD));
    }

    /** Persists the one-time legacy migration so later loads do not re-derive it. */
    public static ApplyResult ensureMigrated() {
        ThemeProfile current = parse(Prefers.getString(KEY_PROFILE));
        if (current != null) return ApplyResult.success(current);
        ThemeProfile migrated = load();
        return apply(migrated);
    }

    public static ApplyResult apply(ThemeProfile draft) {
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(draft);
        if (!result.valid()) return ApplyResult.failure(result.message());
        String json;
        try {
            json = ThemeProfileCodec.encode(result.profile());
        } catch (RuntimeException error) {
            return ApplyResult.failure(error.getMessage() == null ? "theme serialization failed" : error.getMessage());
        }
        if (!writeJson(result.profile(), json)) return ApplyResult.failure("theme preferences could not be saved");
        return ApplyResult.success(result.profile());
    }

    public static ApplyResult reset() {
        return apply(ThemeProfile.defaultProfile());
    }

    /** Maps the legacy theme_color mirror and, when present, bounded v1 profile fields. */
    public static ThemeProfile migrateLegacy(int legacyThemeColor, String legacyProfileJson) {
        ThemeProfile migrated = fromLegacyJson(legacyProfileJson);
        applyLegacyThemeColor(migrated, legacyThemeColor);
        return migrated;
    }

    public static ThemeProfile migrateLegacy(int legacyThemeColor) {
        return migrateLegacy(legacyThemeColor, null);
    }

    /** Mirrors seed source/color back into the legacy theme_color preference. */
    public static int legacyThemeColor(ThemeProfile profile) {
        if (profile == null || ThemeProfile.SEED_NONE.equals(profile.seedSource)) return -1;
        if (ThemeProfile.SEED_WALLPAPER.equals(profile.seedSource)) return 0;
        return ThemeProfileValidator.parseColor(profile.seedColor, -1);
    }

    private static ThemeProfile fromLegacyJson(String json) {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        if (json == null || json.isBlank()) return profile;
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return profile;
            com.google.gson.JsonObject root = parsed.getAsJsonObject();
            profile.mode = ThemeProfile.normalizeMode(string(root, "mode"));
            profile.seedSource = ThemeProfile.normalizeSeedSource(string(root, "seedSource"));
            String seed = nullableString(root, "seedColor");
            if (seed != null) profile.seedColor = ThemeProfileValidator.normalizeColor(seed);
            com.google.gson.JsonObject colors = object(root, "colors");
            if (colors != null) {
                copyLegacySlots(object(colors, "light"), profile.slots(false));
                copyLegacySlots(object(colors, "dark"), profile.slots(true));
            }
        } catch (RuntimeException ignored) {
            // A malformed legacy profile must not block startup; theme_color remains the fallback.
        }
        return profile;
    }

    private static void copyLegacySlots(com.google.gson.JsonObject source, ThemeProfile.SlotSet target) {
        if (source == null) return;
        target.primary = ThemeProfileValidator.normalizeColor(nullableString(source, "primary"));
        target.surface = ThemeProfileValidator.normalizeColor(nullableString(source, "surface"));
        target.onSurface = ThemeProfileValidator.normalizeColor(nullableString(source, "onSurface"));
        target.outline = ThemeProfileValidator.normalizeColor(nullableString(source, "outline"));
        target.error = ThemeProfileValidator.normalizeColor(nullableString(source, "error"));
    }

    private static void applyLegacyThemeColor(ThemeProfile profile, int legacyThemeColor) {
        if (legacyThemeColor == -1) {
            if (ThemeProfile.SEED_NONE.equals(profile.seedSource)) profile.seedColor = null;
            return;
        }
        profile.seedSource = legacyThemeColor == 0 ? ThemeProfile.SEED_WALLPAPER : ThemeProfile.SEED_CUSTOM;
        profile.seedColor = legacyThemeColor == 0 ? null : ThemeProfileValidator.formatColor(legacyThemeColor);
        if (legacyThemeColor != 0) {
            profile.id = "webhtv.legacy-custom";
            profile.name = "Custom";
        }
    }

    private static ThemeProfile parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ThemeProfileCodec.parse(raw);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static boolean writeJson(ThemeProfile profile, String json) {
        try {
            android.content.SharedPreferences.Editor editor = Prefers.getPrefers().edit()
                    .putString(KEY_PROFILE, json)
                    .putString(KEY_LAST_GOOD, json)
                    .putInt(KEY_SCHEMA, ThemeProfile.SCHEMA_VERSION)
                    .putInt("theme_color", legacyThemeColor(profile));
            return editor.commit();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static String string(com.google.gson.JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String nullableString(com.google.gson.JsonObject object, String key) {
        return string(object, key);
    }

    private static com.google.gson.JsonObject object(com.google.gson.JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    public record ApplyResult(boolean success, ThemeProfile profile, String error) {

        public static ApplyResult success(ThemeProfile profile) {
            return new ApplyResult(true, profile, "");
        }

        public static ApplyResult failure(String error) {
            return new ApplyResult(false, null, error == null ? "unknown error" : error);
        }
    }
}
