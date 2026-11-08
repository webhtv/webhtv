package com.fongmi.android.tv.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** JSON codec for the bounded B-safe profile; rejects executable or oversized payloads. */
public final class ThemeProfileCodec {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private ThemeProfileCodec() {
    }

    public static ThemeProfile parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("theme JSON is empty");
        validateJsonBounds(json);
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("theme JSON is invalid", error);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("theme JSON must be an object");
        inspect(root, 0);
        ThemeProfile parsed;
        try {
            parsed = GSON.fromJson(root, ThemeProfile.class);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("theme JSON has an invalid field type", error);
        }
        if (parsed == null) throw new IllegalArgumentException("theme JSON must be an object");
        applyMissingDefaults(parsed);
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(parsed);
        if (!result.valid()) throw new IllegalArgumentException(result.message());
        return result.profile();
    }

    /**
     * Gson allocates without running field initializers, so absent keys arrive as
     * null/0. Bounded defaults keep an old or partial payload loadable.
     */
    static void applyMissingDefaults(ThemeProfile profile) {
        if (profile.format == null) profile.format = ThemeProfile.FORMAT;
        if (profile.schemaVersion == 0) profile.schemaVersion = ThemeProfile.SCHEMA_VERSION;
        if (profile.light == null) profile.light = new ThemeProfile.SlotSet();
        if (profile.dark == null) profile.dark = new ThemeProfile.SlotSet();
    }

    public static String encode(ThemeProfile profile) {
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        if (!result.valid()) throw new IllegalArgumentException(result.message());
        return GSON.toJson(result.profile());
    }

    static void validateJsonBounds(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > ThemeProfileValidator.MAX_JSON_BYTES) {
            throw new IllegalArgumentException("theme JSON is too large");
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char current = json.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '{' || current == '[') {
                if (++depth > ThemeProfileValidator.MAX_NESTING_DEPTH) {
                    throw new IllegalArgumentException("theme JSON is too deeply nested");
                }
            } else if (current == '}' || current == ']') {
                if (--depth < 0) throw new IllegalArgumentException("theme JSON is invalid");
            }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("theme JSON is invalid");
    }

    private static void inspect(JsonElement element, int depth) {
        if (depth > ThemeProfileValidator.MAX_NESTING_DEPTH) throw new IllegalArgumentException("theme JSON is too deeply nested");
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) inspect(child, depth + 1);
            return;
        }
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!ThemeProfileValidator.isSafeJsonKey(entry.getKey())) {
                throw new IllegalArgumentException("unsupported executable theme field: " + entry.getKey());
            }
            inspect(entry.getValue(), depth + 1);
        }
    }
}
