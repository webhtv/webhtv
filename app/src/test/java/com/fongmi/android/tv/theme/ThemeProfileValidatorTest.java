package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeProfileValidatorTest {

    @Test
    public void acceptsTheEmptyDefaultProfile() {
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(ThemeProfile.defaultProfile());
        assertTrue(result.message(), result.valid());
        assertEquals(ThemeProfile.SCHEMA_VERSION, result.profile().schemaVersion);
    }

    @Test
    public void rejectsNullAndWrongSchemaAndFormat() {
        assertFalse(ThemeProfileValidator.validate(null).valid());
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.schemaVersion = 1;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        profile = ThemeProfile.defaultProfile();
        profile.format = "tweakcn";
        assertFalse(ThemeProfileValidator.validate(profile).valid());
    }

    @Test
    public void normalizesModeAndSeedAndRequiresAColorForCustomSeed() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.mode = "DARK";
        profile.seedSource = "weird";
        assertTrue(ThemeProfileValidator.validate(profile).valid());
        assertEquals(ThemeProfile.MODE_SYSTEM, profile.mode);
        assertEquals(ThemeProfile.SEED_NONE, profile.seedSource);

        profile.seedSource = ThemeProfile.SEED_CUSTOM;
        ThemeProfileValidator.Result missingSeed = ThemeProfileValidator.validate(profile);
        assertFalse(missingSeed.valid());
        assertTrue(missingSeed.message().contains("seedColor"));
    }

    @Test
    public void acceptsOnlyOpaqueRgbHexForEveryColorSlot() {
        for (String value : new String[]{"#FFF", "#ABCDEF", "#abcdef"}) {
            ThemeProfile profile = ThemeProfile.defaultProfile();
            profile.light.primary = value;
            ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
            assertTrue(value, result.valid());
            assertTrue(value, result.profile().light.primary.startsWith("#"));
            assertEquals(value, 7, result.profile().light.primary.length());
        }
        for (String value : new String[]{"red", "#12", "#1234", "#80112233", "112233", " #123456 "}) {
            ThemeProfile profile = ThemeProfile.defaultProfile();
            profile.light.primary = value;
            ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
            if (value.startsWith(" ")) assertTrue(result.valid());
            else assertFalse(value, result.valid());
        }
    }

    @Test
    public void rejectsAlphaBearingColorsSoOpacityStaysInItsOwnSlots() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.surface = "#80112233";
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        assertFalse(result.valid());
        assertTrue(result.message().contains("light.surface"));
    }

    @Test
    public void enforcesOpacityRangesOnEveryModeIndependently() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.scrimOpacity = 0.85f;
        profile.light.dialogOpacity = 0.70f;
        profile.light.overlayOpacity = 0.05f;
        assertTrue(ThemeProfileValidator.validate(profile).valid());

        profile.dark.scrimOpacity = 0.86f;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        profile.dark.scrimOpacity = 0f;
        profile.dark.dialogOpacity = 0.69f;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        profile.dark.dialogOpacity = 1.0f;
        profile.dark.overlayOpacity = 0.61f;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        ThemeProfile dark = ThemeProfile.defaultProfile();
        dark.dark.overlayOpacity = Float.NaN;
        assertFalse(ThemeProfileValidator.validate(dark).valid());
    }

    @Test
    public void normalizesNullSlotSetsAndTrimsText() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light = null;
        profile.dark = null;
        profile.id = "  id  ";
        profile.name = "  Name  ";
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        assertTrue(result.valid());
        assertEquals("id", result.profile().id);
        assertEquals("Name", result.profile().name);
        assertTrue(result.profile().light != null);
        assertTrue(result.profile().dark != null);
    }

    @Test
    public void enforcesThePlaybackMenuScrimRangeOnEveryMode() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.playerScrimOpacity = ThemeProfileValidator.MIN_PLAYER_SCRIM_OPACITY;
        profile.dark.playerScrimOpacity = ThemeProfileValidator.MAX_PLAYER_SCRIM_OPACITY;
        assertTrue(ThemeProfileValidator.validate(profile).valid());

        profile.light.playerScrimOpacity = 0.19f;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        profile.light.playerScrimOpacity = 1.01f;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        profile.light.playerScrimOpacity = Float.NaN;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
    }

    @Test
    public void parsesAndFormatsOpaqueColorsForTheLegacyMirror() {
        assertEquals(0xFF155DFC, ThemeProfileValidator.parseColor("#155DFC", -1));
        assertEquals(-1, ThemeProfileValidator.parseColor("#801155FC", -1));
        assertEquals("#155DFC", ThemeProfileValidator.formatColor(0xFF155DFC));
        assertNull(ThemeProfileValidator.normalizeColor("nope"));
    }

    @Test
    public void enforcesTheLiveListScrimRangeOnEveryMode() {
        assertEquals(0.20f, ThemeProfileValidator.MIN_LIVE_LIST_SCRIM_OPACITY, 0.0001f);
        assertEquals(1f, ThemeProfileValidator.MAX_LIVE_LIST_SCRIM_OPACITY, 0.0001f);
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.liveListScrimOpacity = ThemeProfileValidator.MIN_LIVE_LIST_SCRIM_OPACITY;
        profile.dark.liveListScrimOpacity = ThemeProfileValidator.MAX_LIVE_LIST_SCRIM_OPACITY;
        assertTrue(ThemeProfileValidator.validate(profile).valid());

        profile.light.liveListScrimOpacity = 0.19f;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        profile.light.liveListScrimOpacity = 1.01f;
        assertFalse(ThemeProfileValidator.validate(profile).valid());
        profile.light.liveListScrimOpacity = Float.NaN;
        assertFalse(ThemeProfileValidator.validate(profile).valid());

        ThemeProfile dark = ThemeProfile.defaultProfile();
        dark.dark.liveListScrimOpacity = 0.19f;
        assertFalse(ThemeProfileValidator.validate(dark).valid());
        dark = ThemeProfile.defaultProfile();
        dark.dark.liveListScrimOpacity = 1.01f;
        assertFalse(ThemeProfileValidator.validate(dark).valid());
        dark = ThemeProfile.defaultProfile();
        dark.dark.liveListScrimOpacity = Float.NaN;
        assertFalse(ThemeProfileValidator.validate(dark).valid());
    }

    @Test
    public void anUnsetLiveListScrimSlotStaysNullThroughValidation() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        assertTrue(result.message(), result.valid());
        assertNull(result.profile().light.liveListScrimOpacity);
        assertNull(result.profile().dark.liveListScrimOpacity);
    }
}
