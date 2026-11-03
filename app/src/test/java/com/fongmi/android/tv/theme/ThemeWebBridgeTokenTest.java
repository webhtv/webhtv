package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeWebBridgeTokenTest {

    private static final String[] ORIGINAL_13 = {
            "primary", "onPrimary", "surface", "surfaceContainer", "surfaceContainerHigh",
            "onSurface", "onSurfaceVariant", "outline", "outlineVariant", "error",
            "success", "warning", "focus",
    };

    @Test
    public void originalFieldsRemainPresentAndUnchangedInShape() {
        String json = ThemeWebBridge.snapshotJson(ThemeTokens.light());
        for (String key : ORIGINAL_13) {
            assertTrue(key + " must stay in the snapshot", json.contains("\"" + key + "\":"));
        }
        // 焦点缩放已统一为与明暗模式无关的 1.10（见 ThemeTokens 与 TvFocusScale）：
        // 它是动效属性而不是配色属性，此前浅色表为 1.00 只是历史取值。
        assertTrue(json.contains("\"focusScale\":1.10"));
        assertTrue(json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    public void opacityFieldsAreAppendedWithStableFormatting() {
        String light = ThemeWebBridge.snapshotJson(ThemeTokens.light());
        String dark = ThemeWebBridge.snapshotJson(ThemeTokens.dark());
        for (String key : new String[]{"scrimOpacity", "dialogOpacity", "overlayOpacity"}) {
            assertTrue(key, light.contains("\"" + key + "\":"));
            assertTrue(key, dark.contains("\"" + key + "\":"));
        }
        // Two decimals, no locale-dependent separators, no trailing zeros dropped.
        assertTrue(light.matches(".*\"scrimOpacity\":\\d+\\.\\d{2}.*"));
        assertTrue(light.matches(".*\"dialogOpacity\":\\d+\\.\\d{2}.*"));
        assertTrue(light.matches(".*\"overlayOpacity\":\\d+\\.\\d{2}.*"));
    }

    @Test
    public void opacityValuesMatchTheResolvedTokens() {
        ThemeTokens tokens = ThemeTokens.light();
        String json = ThemeWebBridge.snapshotJson(tokens);
        // The snapshot contract is a stable 2-decimal fraction, so compare at that precision.
        assertEquals(round2(alpha(tokens.colorScrim())), value(json, "scrimOpacity"), 0.001);
        assertEquals(round2(tokens.dialogOpacity()), value(json, "dialogOpacity"), 0.001);
        assertEquals(round2(alpha(tokens.colorOverlayLight())), value(json, "overlayOpacity"), 0.001);
    }

    @Test
    public void itemOverlayFieldsMirrorTheResolvedTokens() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.light(), ThemeTokens.dark()}) {
            String json = ThemeWebBridge.snapshotJson(tokens);
            assertTrue(json.contains("\"itemOverlay\":\"" + hex(tokens.colorItemOverlay()) + "\""));
            assertTrue(json.contains("\"onItemOverlay\":\"" + hex(tokens.colorOnItemOverlay()) + "\""));
            assertTrue(json.matches(".*\"itemOverlayOpacity\":\\d+\\.\\d{2}.*"));
            assertEquals(round2(alpha(tokens.colorItemOverlay())), value(json, "itemOverlayOpacity"), 0.001);
        }
    }

    @Test
    public void itemOverlayFieldsFollowTheProfileSlots() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.itemOverlay = "#203040";
        profile.light.itemOverlayOpacity = 0.50f;
        ThemeTokens active = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        String json = ThemeWebBridge.snapshotJson(active);
        // 50% of #203040 is the alpha the resolver applies, so the page sees the strength the list uses.
        assertTrue(json.contains("\"itemOverlay\":\"#80203040\""));
        assertEquals(0.50f, value(json, "itemOverlayOpacity"), 0.001);
        assertTrue(json.contains("\"onItemOverlay\":\"" + hex(active.colorOnItemOverlay()) + "\""));
    }

    @Test
    public void snapshotNeverLeaksProfileOrSeedInternals() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.primary = "#123456";
        ThemeTokens active = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        String json = ThemeWebBridge.snapshotJson(active);
        assertFalse(json.contains("seed"));
        assertFalse(json.contains("wallpaper"));
        assertFalse(json.contains("profile"));
        assertFalse(json.contains("webhtv-theme"));
        assertTrue(json.contains("#" + String.format("%08X", active.colorPrimary())));
    }

    @Test
    public void nullTokensFallBackToTheFrozenLightSnapshot() {
        assertEquals(ThemeWebBridge.snapshotJson(ThemeTokens.light()), ThemeWebBridge.snapshotJson(null));
    }

    private static float round2(float value) {
        return Math.round(value * 100f) / 100f;
    }

    private static float alpha(int color) {
        return ((color >>> 24) & 0xFF) / 255f;
    }

    private static String hex(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }

    private static float value(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker) + marker.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
        return Float.parseFloat(json.substring(start, end));
    }
}
