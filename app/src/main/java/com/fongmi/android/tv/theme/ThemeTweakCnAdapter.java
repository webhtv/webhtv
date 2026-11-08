package com.fongmi.android.tv.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline allowlist adapter for TweakCN/shadcn theme JSON. */
public final class ThemeTweakCnAdapter {

    private static final int MAX_WARNINGS = 32;

    private ThemeTweakCnAdapter() {
    }

    public static Result parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("theme JSON is empty");
        ThemeProfileCodec.validateJsonBounds(json);
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("theme JSON is invalid", error);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("theme JSON must be an object");
        inspectSafeKeys(root, 0);
        JsonObject object = root.getAsJsonObject();
        if (object.has("format") || object.has("schemaVersion")) {
            return new Result(ThemeProfileCodec.parse(json), List.of());
        }

        ThemeProfile profile = ThemeProfile.defaultProfile();
        List<String> warnings = new ArrayList<>();
        JsonObject cssVars = object.has("cssVars") && object.get("cssVars").isJsonObject()
                ? object.getAsJsonObject("cssVars") : object;
        int recognized = 0;
        if (cssVars.has("light") || cssVars.has("dark")) {
            if (cssVars.has("light") && cssVars.get("light").isJsonObject()) {
                recognized += applyObject(cssVars.getAsJsonObject("light"), profile.light, warnings, "light");
            }
            if (cssVars.has("dark") && cssVars.get("dark").isJsonObject()) {
                recognized += applyObject(cssVars.getAsJsonObject("dark"), profile.dark, warnings, "dark");
            }
            warnUnsupported(cssVars, warnings, "cssVars");
            copyMissing(profile.light, profile.dark);
        } else {
            recognized = applyObject(cssVars, profile.light, warnings, "theme");
            copyMissing(profile.light, profile.dark);
        }
        if (recognized == 0) throw new IllegalArgumentException("no supported TweakCN color tokens");

        String name = object.has("name") && object.get("name").isJsonPrimitive()
                ? object.get("name").getAsString() : "Imported TweakCN theme";
        profile.id = "webhtv.tweakcn.imported";
        profile.name = name == null || name.isBlank() ? "Imported TweakCN theme"
                : name.trim().substring(0, Math.min(64, name.trim().length()));
        String seed = profile.light.primary != null ? profile.light.primary : profile.dark.primary;
        if (seed != null) {
            profile.seedSource = ThemeProfile.SEED_CUSTOM;
            profile.seedColor = seed;
        }
        ThemeProfileValidator.Result checked = ThemeProfileValidator.validate(profile);
        if (!checked.valid()) throw new IllegalArgumentException(checked.message());
        return new Result(checked.profile(), warnings);
    }

    private static void inspectSafeKeys(JsonElement element, int depth) {
        if (depth > ThemeProfileValidator.MAX_NESTING_DEPTH) throw new IllegalArgumentException("theme JSON is too deeply nested");
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) inspectSafeKeys(child, depth + 1);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!ThemeProfileValidator.isSafeJsonKey(entry.getKey())) {
                throw new IllegalArgumentException("unsupported executable theme field: " + entry.getKey());
            }
            inspectSafeKeys(entry.getValue(), depth + 1);
        }
    }

    private static int applyObject(JsonObject object, ThemeProfile.SlotSet target,
                                   List<String> warnings, String path) {
        int recognized = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = normalizeKey(entry.getKey());
            String value = color(entry.getValue());
            if (value == null) {
                addWarning(warnings, path + "." + entry.getKey() + " ignored: unsupported field or color format");
                continue;
            }
            if (apply(key, value, target)) recognized++;
            else addWarning(warnings, path + "." + entry.getKey() + " ignored: unsupported token");
        }
        return recognized;
    }

    private static void warnUnsupported(JsonObject object, List<String> warnings, String path) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (!key.equals("--light") && !key.equals("--dark") && !isColorToken(key) && !"--theme".equals(key)) {
                addWarning(warnings, path + "." + entry.getKey() + " ignored: unsupported field");
            }
        }
    }

    private static void copyMissing(ThemeProfile.SlotSet from, ThemeProfile.SlotSet to) {
        if (to.primary == null) to.primary = from.primary;
        if (to.primaryContainer == null) to.primaryContainer = from.primaryContainer;
        if (to.surface == null) to.surface = from.surface;
        if (to.surfaceContainer == null) to.surfaceContainer = from.surfaceContainer;
        if (to.surfaceContainerHigh == null) to.surfaceContainerHigh = from.surfaceContainerHigh;
        if (to.onSurface == null) to.onSurface = from.onSurface;
        if (to.onSurfaceVariant == null) to.onSurfaceVariant = from.onSurfaceVariant;
        if (to.outline == null) to.outline = from.outline;
        if (to.focus == null) to.focus = from.focus;
        if (to.error == null) to.error = from.error;
        if (to.success == null) to.success = from.success;
        if (to.warning == null) to.warning = from.warning;
    }

    private static boolean apply(String key, String value, ThemeProfile.SlotSet target) {
        switch (key) {
            case "--primary" -> target.primary = value;
            case "--primary-container", "--accent" -> target.primaryContainer = value;
            case "--background", "--foreground" -> {
                if ("--background".equals(key)) target.surface = value;
                else target.onSurface = value;
            }
            case "--card", "--secondary", "--muted" -> target.surfaceContainer = value;
            case "--popover", "--accent-foreground" -> target.surfaceContainerHigh = value;
            case "--card-foreground", "--popover-foreground", "--secondary-foreground" -> target.onSurface = value;
            case "--muted-foreground" -> target.onSurfaceVariant = value;
            case "--border", "--input" -> target.outline = value;
            case "--ring" -> target.focus = value;
            case "--destructive" -> target.error = value;
            default -> { return false; }
        }
        return true;
    }

    private static boolean isColorToken(String key) {
        return key.contains("primary") || key.contains("background") || key.contains("foreground")
                || key.contains("card") || key.contains("popover") || key.contains("muted")
                || key.contains("border") || key.contains("input") || key.contains("ring")
                || key.contains("destructive") || key.contains("secondary") || key.contains("accent");
    }

    private static String normalizeKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return key.startsWith("--") ? key : "--" + key;
    }

    private static String color(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        return ThemeColorUtil.normalizeCss(value.getAsString());
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < MAX_WARNINGS) warnings.add(warning);
    }

    public record Result(ThemeProfile profile, List<String> warnings) {
        public Result {
            warnings = warnings == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(warnings));
        }
    }
}
