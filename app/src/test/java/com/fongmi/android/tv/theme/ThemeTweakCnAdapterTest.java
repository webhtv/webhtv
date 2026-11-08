package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class ThemeTweakCnAdapterTest {

    @Test
    public void importsTweakCnCssVarsIntoTheCurrentV2Profile() {
        String json = "{\"name\":\"Nord-ish\",\"cssVars\":{\"light\":{"
                + "\"--primary\":\"#5E81AC\",\"--background\":\"#ECEFF4\","
                + "\"--foreground\":\"#2E3440\",\"--ring\":\"oklch(0.55 0.12 245)\"},"
                + "\"dark\":{\"--primary\":\"rgb(136,192,208)\",\"--background\":\"#2E3440\"}}}";
        ThemeTweakCnAdapter.Result result = ThemeTweakCnAdapter.parse(json);
        assertEquals("Nord-ish", result.profile().name);
        assertEquals("#5E81AC", result.profile().light.primary);
        assertEquals("#88C0D0", result.profile().dark.primary);
        assertEquals(ThemeProfile.SEED_CUSTOM, result.profile().seedSource);
        assertTrue(result.warnings().isEmpty());
        ThemeResolver.resolve(ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, result.profile(), null, false).requireContrast();
    }

    @Test
    public void ignoresUnsupportedColorTokensButRejectsExecutableKeysAndOversizedInput() {
        ThemeTweakCnAdapter.Result result = ThemeTweakCnAdapter.parse(
                "{\"--primary\":\"#123456\",\"--radius\":\"0.5rem\",\"--font\":\"Inter\"}");
        assertEquals("#123456", result.profile().light.primary);
        assertFalse(result.warnings().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ThemeTweakCnAdapter.parse(
                "{\"script\":\"alert(1)\",\"--primary\":\"#123456\"}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeTweakCnAdapter.parse(
                "{\"--primary\":\"" + "x".repeat(ThemeProfileValidator.MAX_JSON_BYTES) + "\"}"));
    }

    @Test
    public void transportAcceptsOnlyPublicHttpsAndBoundsLocalReads() throws Exception {
        assertTrue(ThemeTransfer.isHttps("https://example.com/theme.json"));
        assertFalse(ThemeTransfer.isHttps("http://example.com/theme.json"));
        assertFalse(ThemeTransfer.isHttps("https://127.0.0.1/theme.json"));
        assertFalse(ThemeTransfer.isHttps("https://localhost/theme.json"));
        assertEquals("{\"--primary\":\"#123456\"}", ThemeTransfer.read(
                new ByteArrayInputStream("{\"--primary\":\"#123456\"}".getBytes(StandardCharsets.UTF_8))));
        byte[] oversized = new byte[ThemeTransfer.MAX_BYTES + 1];
        assertThrows(java.io.IOException.class, () -> ThemeTransfer.read(new ByteArrayInputStream(oversized)));
    }
}
