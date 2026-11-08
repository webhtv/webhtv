package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TmdbCinemaThemeTest {

    @Test
    public void resolveLight_usesOnlyLightAndDarkThemeModes() {
        assertTrue(TmdbCinemaTheme.resolveLight(0, false));
        assertTrue(TmdbCinemaTheme.resolveLight(0, true));
        assertFalse(TmdbCinemaTheme.resolveLight(1, false));
        assertTrue(TmdbCinemaTheme.resolveLight(2, true));
    }

    @Test
    public void palette_usesDarkCanvasForDarkTheme() {
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(false);

        assertEquals(0xFF090B0F, palette.background());
        assertEquals(0xFFFFFFFF, palette.primary());
        assertEquals(0xB314202A, palette.card());
    }

    @Test
    public void palette_usesLightCanvasForLightTheme() {
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(true);

        assertEquals(0xFFEBE3DA, palette.background());
        assertEquals(0xFF12202D, palette.primary());
        assertEquals(0xD9FFFFFF, palette.card());
    }

    @Test
    public void lightCinemaPaletteStaysDistinctFromProfileChrome() {
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(true);

        // 清透流彩是冷白/浅绿（F4F7FA / 20B866）；光影剧幕保持暖幕布与金色强调。
        assertNotEquals(0xFFF4F7FA, palette.background());
        assertNotEquals(0xFF20B866, palette.accent());
        assertEquals(0xFFA8702F, palette.accent());
    }
}
