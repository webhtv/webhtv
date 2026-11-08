package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ThemePaletteStyleTest {

    @Test
    public void unknownOrMissingStylesKeepTheTonalSpotDefault() {
        assertEquals(ThemePaletteStyle.TONAL_SPOT, ThemePaletteStyle.from(null));
        assertEquals(ThemePaletteStyle.TONAL_SPOT, ThemePaletteStyle.from("not-a-style"));
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.paletteStyle = "not-a-style";
        assertTrue(ThemeProfileValidator.validate(profile).valid());
        assertEquals(ThemePaletteStyle.TONAL_SPOT.id(), profile.paletteStyle);
    }

    @Test
    public void everyBuiltInStyleProducesAContrastingLightAndDarkScheme() {
        for (ThemePaletteStyle style : ThemePaletteStyle.values()) {
            ThemeProfile profile = ThemeProfile.defaultProfile();
            profile.paletteStyle = style.id();
            ThemeTokens light = ThemeResolver.resolve(ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
            ThemeTokens dark = ThemeResolver.resolve(ThemeMode.DARK, ThemeSeed.NONE, 0, 0, profile, null, true);
            light.requireContrast();
            dark.requireContrast();
            if (style != ThemePaletteStyle.TONAL_SPOT) {
                assertNotEquals(style.name() + " should affect the light palette",
                        ThemeTokens.light().colorPrimary(), light.colorPrimary());
                assertNotEquals(style.name() + " should affect the dark palette",
                        ThemeTokens.dark().colorPrimary(), dark.colorPrimary());
            }
        }
    }

    @Test
    public void profileRoundTripKeepsPaletteStyle() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.paletteStyle = ThemePaletteStyle.EXPRESSIVE.id();
        ThemeProfile parsed = ThemeProfileCodec.parse(ThemeProfileCodec.encode(profile));
        assertEquals(ThemePaletteStyle.EXPRESSIVE.id(), parsed.paletteStyle);
    }
}
