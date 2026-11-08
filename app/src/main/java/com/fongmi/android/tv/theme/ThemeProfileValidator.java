package com.fongmi.android.tv.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates and normalizes a B-safe theme profile before it is persisted or resolved. */
public final class ThemeProfileValidator {

    public static final int MAX_JSON_BYTES = 128 * 1024;
    public static final int MAX_NESTING_DEPTH = 8;
    public static final float MIN_SCRIM_OPACITY = 0f;
    public static final float MAX_SCRIM_OPACITY = 0.85f;
    public static final float MIN_DIALOG_OPACITY = 0.70f;
    public static final float MAX_DIALOG_OPACITY = 1f;
    public static final float MIN_OVERLAY_OPACITY = 0.05f;
    public static final float MAX_OVERLAY_OPACITY = 0.60f;
    public static final float MIN_PLAYER_SCRIM_OPACITY = 0.20f;
    public static final float MAX_PLAYER_SCRIM_OPACITY = 1f;
    public static final float MIN_LIVE_LIST_SCRIM_OPACITY = 0.20f;
    public static final float MAX_LIVE_LIST_SCRIM_OPACITY = 1f;
    /**
     * 条目遮罩强度的取值范围。上限是 1（而不是环境遮罩槽的 0.60）：遮罩色由用户自选，选浅色
     * 时往往需要压满才能盖住封面上的杂色，而强度只是缩放 alpha，压满等于「遮罩色说了算」；
     * 下限沿用 0.05，低于它在电视观看距离上等于没有遮罩，滑到那里只会让用户以为功能失效。
     */
    public static final float MIN_ITEM_OVERLAY_OPACITY = 0.05f;
    public static final float MAX_ITEM_OVERLAY_OPACITY = 1f;

    private static final Set<String> DANGEROUS_KEYS = Set.of(
            "script", "javascript", "resource", "resourcepath", "intent", "classname", "class", "css",
            "url", "uri", "path", "file", "assets", "src", "srcset", "href", "onload", "onerror");

    private ThemeProfileValidator() {
    }

    public static Result validate(ThemeProfile profile) {
        List<String> errors = new ArrayList<>();
        if (profile == null) return Result.invalid("theme profile is missing");
        if (profile.schemaVersion != ThemeProfile.SCHEMA_VERSION) {
            errors.add("schemaVersion must be " + ThemeProfile.SCHEMA_VERSION);
        }
        if (!ThemeProfile.FORMAT.equals(profile.format)) errors.add("format must be " + ThemeProfile.FORMAT);
        profile.id = text(profile.id, "webhtv.local");
        profile.name = text(profile.name, "Default");
        if (profile.id.length() > 64) errors.add("id is too long");
        if (profile.name.length() > 64) errors.add("name is too long");
        profile.mode = ThemeProfile.normalizeMode(profile.mode);
        profile.paletteStyle = ThemeProfile.normalizePaletteStyle(profile.paletteStyle);
        profile.seedSource = ThemeProfile.normalizeSeedSource(profile.seedSource);
        profile.seedColor = color(profile.seedColor, errors, "seedColor");
        if (ThemeProfile.SEED_CUSTOM.equals(profile.seedSource) && profile.seedColor == null) {
            errors.add("seedColor is required for a custom seed");
        }
        if (ThemeProfile.SEED_NONE.equals(profile.seedSource)) profile.seedColor = null;
        if (profile.light == null) profile.light = new ThemeProfile.SlotSet();
        if (profile.dark == null) profile.dark = new ThemeProfile.SlotSet();
        validateSlots(profile.light, errors, "light");
        validateSlots(profile.dark, errors, "dark");
        return new Result(errors.isEmpty() ? profile : null, errors);
    }

    private static void validateSlots(ThemeProfile.SlotSet slots, List<String> errors, String path) {
        slots.primary = color(slots.primary, errors, path + ".primary");
        slots.primaryContainer = color(slots.primaryContainer, errors, path + ".primaryContainer");
        slots.secondaryContainer = color(slots.secondaryContainer, errors, path + ".secondaryContainer");
        slots.focus = color(slots.focus, errors, path + ".focus");
        slots.playerCurrent = color(slots.playerCurrent, errors, path + ".playerCurrent");
        slots.surface = color(slots.surface, errors, path + ".surface");
        slots.surfaceContainer = color(slots.surfaceContainer, errors, path + ".surfaceContainer");
        slots.surfaceContainerHigh = color(slots.surfaceContainerHigh, errors, path + ".surfaceContainerHigh");
        slots.onSurface = color(slots.onSurface, errors, path + ".onSurface");
        slots.onSurfaceVariant = color(slots.onSurfaceVariant, errors, path + ".onSurfaceVariant");
        slots.outline = color(slots.outline, errors, path + ".outline");
        slots.error = color(slots.error, errors, path + ".error");
        slots.success = color(slots.success, errors, path + ".success");
        slots.warning = color(slots.warning, errors, path + ".warning");
        slots.itemOverlay = color(slots.itemOverlay, errors, path + ".itemOverlay");
        slots.onItemOverlay = color(slots.onItemOverlay, errors, path + ".onItemOverlay");
        slots.scrimOpacity = opacity(slots.scrimOpacity, MIN_SCRIM_OPACITY, MAX_SCRIM_OPACITY, errors, path + ".scrimOpacity");
        slots.dialogOpacity = opacity(slots.dialogOpacity, MIN_DIALOG_OPACITY, MAX_DIALOG_OPACITY, errors, path + ".dialogOpacity");
        slots.overlayOpacity = opacity(slots.overlayOpacity, MIN_OVERLAY_OPACITY, MAX_OVERLAY_OPACITY, errors, path + ".overlayOpacity");
        slots.playerScrimOpacity = opacity(slots.playerScrimOpacity, MIN_PLAYER_SCRIM_OPACITY, MAX_PLAYER_SCRIM_OPACITY, errors, path + ".playerScrimOpacity");
        slots.liveListScrimOpacity = opacity(slots.liveListScrimOpacity, MIN_LIVE_LIST_SCRIM_OPACITY, MAX_LIVE_LIST_SCRIM_OPACITY, errors, path + ".liveListScrimOpacity");
        slots.itemOverlayOpacity = opacity(slots.itemOverlayOpacity, MIN_ITEM_OVERLAY_OPACITY, MAX_ITEM_OVERLAY_OPACITY, errors, path + ".itemOverlayOpacity");
    }

    private static String color(String value, List<String> errors, String path) {
        if (value == null || value.isBlank()) return null;
        String normalized = normalizeColor(value);
        if (normalized == null) errors.add(path + " must be an opaque #RRGGBB color");
        return normalized;
    }

    /** Only opaque #RRGGBB colors are accepted; transparent values must use an opacity slot. */
    static String normalizeColor(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 4 && normalized.charAt(0) == '#') {
            normalized = "#" + normalized.charAt(1) + normalized.charAt(1)
                    + normalized.charAt(2) + normalized.charAt(2)
                    + normalized.charAt(3) + normalized.charAt(3);
        }
        if (normalized.length() != 7 || normalized.charAt(0) != '#') return null;
        for (int index = 1; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            boolean hex = (current >= '0' && current <= '9') || (current >= 'A' && current <= 'F');
            if (!hex) return null;
        }
        return normalized;
    }

    static int parseColor(String value, int fallback) {
        String normalized = normalizeColor(value);
        if (normalized == null) return fallback;
        return (int) (0xFF000000L | Long.parseLong(normalized.substring(1), 16));
    }

    static String formatColor(int value) {
        return String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
    }

    private static Float opacity(Float value, float minimum, float maximum, List<String> errors, String path) {
        if (value == null) return null;
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            errors.add(path + " must be between " + minimum + " and " + maximum);
            return value;
        }
        return value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static boolean isSafeJsonKey(String key) {
        if (key == null) return false;
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return !DANGEROUS_KEYS.contains(normalized);
    }

    public record Result(ThemeProfile profile, List<String> errors) {

        public Result {
            errors = errors == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(errors));
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public String message() {
            return valid() ? "" : String.join("; ", errors);
        }

        public static Result invalid(String error) {
            return new Result(null, List.of(error));
        }
    }
}
