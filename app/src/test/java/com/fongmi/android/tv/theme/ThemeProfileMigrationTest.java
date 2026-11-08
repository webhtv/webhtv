package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeProfileMigrationTest {

    @Test
    public void legacyOffMigratesToDisabledSeed() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(-1, null);
        assertEquals(ThemeProfile.SEED_NONE, profile.seedSource);
        assertNull(profile.seedColor);
        assertEquals(-1, ThemeProfileStore.legacyThemeColor(profile));
    }

    @Test
    public void legacyWallpaperKeepsWallpaperSeedMode() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(0, null);
        assertEquals(ThemeProfile.SEED_WALLPAPER, profile.seedSource);
        assertNull(profile.seedColor);
        assertEquals(0, ThemeProfileStore.legacyThemeColor(profile));
    }

    @Test
    public void legacyCustomSeedRoundTripsAsArgbInt() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(0xFF155DFC, null);
        assertEquals(ThemeProfile.SEED_CUSTOM, profile.seedSource);
        assertEquals("#155DFC", profile.seedColor);
        assertEquals(0xFF155DFC, ThemeProfileStore.legacyThemeColor(profile));
    }

    @Test
    public void legacyWallColorStaysOutOfTheProfileContract() {
        // wall_color is not migrated into the profile: it remains the live wallpaper
        // seed input owned by Setting.getWallColor().
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(-1, null);
        assertNull(profile.seedColor);
        assertEquals(ThemeProfile.MODE_SYSTEM, profile.mode);
    }

    @Test
    public void legacyV1JsonContributesOnlyModeSeedAndFiveSafeSlots() {
        String legacy = "{\"schemaVersion\":1,\"mode\":\"dark\",\"seedSource\":\"custom\",\"seedColor\":\"#155DFC\","
                + "\"colors\":{\"light\":{\"primary\":\"#112233\",\"surface\":\"#F8FAFD\",\"onSurface\":\"#1A1C1E\","
                + "\"outline\":\"#74777F\",\"error\":\"#B3261E\",\"onPrimary\":\"#FFFFFF\",\"script\":\"evil\"}},"
                + "\"metadata\":{\"script\":\"evil\"}}";
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(-1, legacy);
        assertEquals(ThemeProfile.MODE_DARK, profile.mode);
        assertEquals(ThemeProfile.SEED_CUSTOM, profile.seedSource);
        assertEquals("#155DFC", profile.seedColor);
        assertEquals("#112233", profile.slots(false).primary);
        assertEquals("#F8FAFD", profile.slots(false).surface);
        assertEquals("#1A1C1E", profile.slots(false).onSurface);
        assertEquals("#74777F", profile.slots(false).outline);
        assertEquals("#B3261E", profile.slots(false).error);
        assertNull(profile.slots(false).primaryContainer);
    }

    @Test
    public void legacyMirrorWinsOverTheStaleV1Seed() {
        String legacy = "{\"schemaVersion\":1,\"seedSource\":\"custom\",\"seedColor\":\"#111111\"}";
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(0, legacy);
        assertEquals(ThemeProfile.SEED_WALLPAPER, profile.seedSource);
        assertNull(profile.seedColor);

        profile = ThemeProfileStore.migrateLegacy(0xFF0000FF, legacy);
        assertEquals(ThemeProfile.SEED_CUSTOM, profile.seedSource);
        assertEquals("#0000FF", profile.seedColor);
    }

    @Test
    public void malformedLegacyJsonFallsBackToTheDefaultProfile() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(-1, "{not json");
        assertTrue(profile != null);
        assertEquals(ThemeProfile.SCHEMA_VERSION, profile.schemaVersion);
        assertEquals(ThemeProfile.SEED_NONE, profile.seedSource);
    }

    @Test
    public void resetReturnsAnApplicableDefaultProfile() {
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(ThemeProfile.defaultProfile());
        assertTrue(result.valid());
        assertEquals(-1, ThemeProfileStore.legacyThemeColor(result.profile()));
    }
}
