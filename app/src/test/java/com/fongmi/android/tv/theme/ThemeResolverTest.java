package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ThemeResolverTest {

    @Test
    public void noSeedUsesTheFrozenPalettes() {
        assertEquals(ThemeTokens.light(), ThemeResolver.resolve(ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, false));
        assertEquals(ThemeTokens.dark(), ThemeResolver.resolve(ThemeMode.DARK, ThemeSeed.NONE, 0, 0, false));
    }

    @Test
    public void explicitSeedGeneratesAContrastingPaletteInsteadOfReturningTheSeed() {
        int seed = 0xFF0B57D0;
        ThemeTokens tokens = ThemeResolver.resolve(ThemeMode.LIGHT, ThemeSeed.EXPLICIT, seed, 0, false);
        assertNotEquals(seed, tokens.colorPrimary());
        tokens.requireContrast();
        assertEquals("seed:explicit", ThemeResolver.lastDiagnostic());
    }

    @Test
    public void wallpaperSeedRemainsADarkSurfaceAndNeverBecomesText() {
        int wallpaper = 0xFFB7F7D8;
        ThemeTokens tokens = ThemeResolver.resolve(ThemeMode.DARK, ThemeSeed.WALLPAPER, 0, wallpaper, false);
        assertNotEquals(wallpaper, tokens.colorSurface());
        assertNotEquals(wallpaper, tokens.colorOnSurface());
        assertTrue(ThemeContrast.ratio(tokens.colorSurface(), 0xFF000000) < 2.0);
        tokens.requireContrast();
    }

    @Test
    public void androidNineFallbackUsesTheDefaultPalette() {
        assertEquals(ThemeTokens.dark(), ThemeResolver.resolve(ThemeMode.SYSTEM, ThemeSeed.NONE, 0, 0, true));
    }

    @Test
    public void invalidSeedFallsBackWithoutThrowing() {
        assertEquals(ThemeTokens.light(), ThemeResolver.resolve(ThemeMode.LIGHT, ThemeSeed.EXPLICIT, 0x00123456, 0, false));
        assertTrue(ThemeResolver.lastDiagnostic().startsWith("fallback:invalid-seed"));
    }

    @Test
    public void lowContrastCandidateFallsBack() throws Exception {
        ThemeTokens invalid = replacePrimary(ThemeTokens.light(), ThemeTokens.light().colorPrimary());
        assertEquals(invalid, ThemeResolver.requireOrFallback(invalid, ThemeTokens.light()));

        ThemeTokens bad = replacePrimary(ThemeTokens.light(), 0xFFFFFFFF);
        assertEquals(ThemeTokens.light(), ThemeResolver.requireOrFallback(bad, ThemeTokens.light()));
        assertTrue(ThemeResolver.lastDiagnostic().contains("seed-contrast"));
    }

    @Test
    public void webBridgeSnapshotIsBoundedAndReadOnly() {
        String json = ThemeWebBridge.snapshotJson(ThemeTokens.dark());
        assertTrue(json.contains("\"primary\":"));
        assertTrue(json.contains("\"focusScale\":1.1"));
        assertTrue(!json.contains("seed") && !json.contains("wallpaper"));
    }

    private static ThemeTokens replacePrimary(ThemeTokens source, int primary) throws Exception {
        RecordComponent[] components = ThemeTokens.class.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = components[i].getAccessor().invoke(source);
        }
        values[0] = primary;
        Constructor<ThemeTokens> constructor = ThemeTokens.class.getDeclaredConstructor(types);
        return constructor.newInstance(values);
    }
}
