package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ThemeProfileCodecTest {

    @Test
    public void roundTripNormalizesShortAndLowercaseColors() {
        String json = "{\"schemaVersion\":2,\"format\":\"webhtv-theme\",\"name\":\"Demo\","
                + "\"seedSource\":\"custom\",\"seedColor\":\"#abc\","
                + "\"light\":{\"primary\":\"#112233\",\"dialogOpacity\":0.8}}";
        ThemeProfile profile = ThemeProfileCodec.parse(json);
        assertEquals("#AABBCC", profile.seedColor);
        assertEquals("#112233", profile.light.primary);
        assertEquals(0.8f, profile.light.dialogOpacity, 0.0001f);
        assertEquals("#AABBCC", ThemeProfileCodec.parse(ThemeProfileCodec.encode(profile)).seedColor);
    }

    @Test
    public void missingFieldsFallBackToInheritedDefaults() {
        ThemeProfile profile = ThemeProfileCodec.parse("{}");
        assertEquals(ThemeProfile.SCHEMA_VERSION, profile.schemaVersion);
        assertEquals(ThemeProfile.FORMAT, profile.format);
        assertEquals(ThemeProfile.MODE_SYSTEM, profile.mode);
        assertEquals(ThemeProfile.SEED_NONE, profile.seedSource);
        assertNull(profile.light.primary);
    }

    @Test
    public void unknownBenignFieldsAreIgnoredButDangerousFieldsAreRejected() {
        ThemeProfile profile = ThemeProfileCodec.parse("{\"futureSlot\":\"#123456\"}");
        assertEquals(ThemeProfile.SEED_NONE, profile.seedSource);
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"script\":\"alert(1)\"}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"onLoad\":\"x\"}}"));
    }

    @Test
    public void rejectsMalformedTransparentAndWrongTypedValues() {
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"seedColor\":\"#GGG\"}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"seedSource\":\"wallpaper\",\"light\":{\"primary\":\"#80112233\"}}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":\"not-an-object\"}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(""));
    }

    @Test
    public void rejectsOpacityOutsideTheSafeRangeAndWrongSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"dialogOpacity\":0.10}}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"scrimOpacity\":0.99}}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"overlayOpacity\":0.90}}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"schemaVersion\":1}"));
    }

    @Test
    public void rejectsOversizedAndDeeplyNestedPayloads() {
        StringBuilder deep = new StringBuilder();
        for (int index = 0; index < 12; index++) deep.append('{');
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(deep.toString()));
        String huge = "{\"name\":\"" + "x".repeat(ThemeProfileValidator.MAX_JSON_BYTES) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(huge));
    }

    @Test
    public void playbackMenuScrimOpacityRoundTripsAndRejectsOutOfRange() {
        ThemeProfile profile = ThemeProfileCodec.parse("{\"light\":{\"playerScrimOpacity\":0.65}}");
        assertEquals(0.65f, profile.light.playerScrimOpacity, 0.0001f);
        assertEquals(0.65f, ThemeProfileCodec.parse(ThemeProfileCodec.encode(profile)).light.playerScrimOpacity, 0.0001f);
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"playerScrimOpacity\":0.19}}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"playerScrimOpacity\":1.01}}"));
    }

    @Test
    public void encodeRejectsAnInvalidProfileInsteadOfWritingIt() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.primary = "#12";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ThemeProfileCodec.encode(profile));
        assertTrue(error.getMessage().contains("light.primary"));
    }

    @Test
    public void liveListScrimOpacityRoundTripsAndRejectsOutOfRange() {
        ThemeProfile profile = ThemeProfileCodec.parse("{\"light\":{\"liveListScrimOpacity\":0.65}}");
        assertEquals(0.65f, profile.light.liveListScrimOpacity, 0.0001f);
        assertEquals(0.65f, ThemeProfileCodec.parse(ThemeProfileCodec.encode(profile)).light.liveListScrimOpacity, 0.0001f);
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"liveListScrimOpacity\":0.19}}"));
        assertThrows(IllegalArgumentException.class, () -> ThemeProfileCodec.parse(
                "{\"light\":{\"liveListScrimOpacity\":1.01}}"));
    }

    @Test
    public void anUnsetLiveListScrimSlotStaysUnsetInsteadOfDefaultingToANumber() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        assertNull(profile.light.liveListScrimOpacity);
        assertNull(profile.dark.liveListScrimOpacity);
        // A round trip must not bake a number into the slot: the shipped look comes from
        // the DEFAULT_OPACITY fallback inside LiveListScrim, not from the profile.
        assertNull(ThemeProfileCodec.parse(ThemeProfileCodec.encode(profile)).light.liveListScrimOpacity);
        assertNull(ThemeProfileCodec.parse(ThemeProfileCodec.encode(profile)).dark.liveListScrimOpacity);
    }
}
