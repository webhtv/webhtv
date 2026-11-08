package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ThemePresetsTest {

    @Test
    public void defaultIsFirstAndStaysByteIdenticalToTheFrozenPalette() {
        assertEquals(ThemePresets.Preset.DEFAULT, ThemePresets.Preset.values()[0]);
        ThemeProfile profile = ThemePresets.Preset.DEFAULT.profile();
        assertEquals(ThemeProfileCodec.encode(ThemeProfile.defaultProfile()), ThemeProfileCodec.encode(profile));
        ThemeEditor editor = new ThemeEditor(profile);
        assertEquals(ThemeTokens.light(), editor.preview(false, 0));
        assertEquals(ThemeTokens.dark(), editor.preview(true, 0));
    }

    @Test
    public void everyNamedPaletteDefinesAllThirteenColorsInBothModes() {
        for (ThemePresets.Preset preset : ThemePresets.Preset.values()) {
            if (!preset.isComplete()) continue;
            ThemeProfile profile = preset.profile();
            ThemeEditor editor = new ThemeEditor(profile);
            for (boolean dark : new boolean[]{false, true}) {
                for (ThemeEditor.Slot slot : ThemeEditor.Slot.values()) {
                    if (slot.isColor()) assertNotNull(preset + "/" + dark + "/" + slot, editor.valueOf(slot, dark));
                }
                ThemeTokens tokens = editor.preview(dark, 0);
                assertFalse(preset + ": " + ThemeResolver.lastDiagnostic(), ThemeResolver.lastDiagnostic().startsWith("fallback"));
                tokens.requireContrast();
                assertEquals(tokens.colorPrimary(), tokens.colorFocus());
                assertNotEquals(tokens.colorSurface(), tokens.colorSurfaceContainer());
                assertNotEquals(tokens.colorSurfaceContainer(), tokens.colorSurfaceContainerHigh());
                for (int background : new int[]{tokens.colorSurface(), tokens.colorSurfaceContainer(), tokens.colorSurfaceContainerHigh()}) {
                    assertTrue(preset + " secondary text contrast", ThemeContrast.ratio(tokens.colorOnSurfaceVariant(), background) >= 4.5);
                }
            }
            String encoded = ThemeProfileCodec.encode(profile);
            assertEquals(encoded, ThemeProfileCodec.encode(ThemeProfileCodec.parse(encoded)));
        }
    }

    @Test
    public void palettesDifferInSurfacesSecondaryAndSemanticColorsNotOnlyPrimary() {
        for (boolean dark : new boolean[]{false, true}) {
            Set<Integer> surfaces = new HashSet<>();
            Set<Integer> secondary = new HashSet<>();
            Set<Integer> success = new HashSet<>();
            Set<Integer> warning = new HashSet<>();
            ThemeTokens baseline = dark ? ThemeTokens.dark() : ThemeTokens.light();
            for (ThemePresets.Preset preset : ThemePresets.Preset.values()) {
                if (!preset.isComplete()) continue;
                ThemeTokens tokens = new ThemeEditor(preset.profile()).preview(dark, 0);
                surfaces.add(tokens.colorSurfaceContainerHigh());
                secondary.add(tokens.colorSecondaryContainer());
                success.add(tokens.colorSuccess());
                warning.add(tokens.colorWarning());
                assertNotEquals(preset + " success must be intentional", baseline.colorSuccess(), tokens.colorSuccess());
                assertNotEquals(preset + " warning must be intentional", baseline.colorWarning(), tokens.colorWarning());
                double greenHue = com.google.android.material.color.utilities.Hct.fromInt(tokens.colorSuccess()).getHue();
                double amberHue = com.google.android.material.color.utilities.Hct.fromInt(tokens.colorWarning()).getHue();
                assertTrue("success remains green: " + greenHue, greenHue >= 100 && greenHue <= 190);
                assertTrue("warning remains amber: " + amberHue, amberHue >= 40 && amberHue <= 110);
            }
            assertEquals(7, surfaces.size());
            assertEquals(7, secondary.size());
            assertTrue(success.size() >= 6);
            assertTrue(warning.size() >= 6);
        }
    }

    @Test
    public void everyPaletteStyleIsSafeForEveryCompleteTemplate() {
        for (ThemePresets.Preset preset : ThemePresets.Preset.values()) {
            if (!preset.isComplete()) continue;
            for (ThemePaletteStyle style : ThemePaletteStyle.values()) {
                ThemeEditor editor = new ThemeEditor(preset.profile(style));
                for (boolean dark : new boolean[]{false, true}) {
                    ThemeTokens tokens = editor.preview(dark, 0);
                    assertFalse(preset + "/" + style + ": " + ThemeResolver.lastDiagnostic(), ThemeResolver.lastDiagnostic().startsWith("fallback"));
                    tokens.requireContrast();
                }
            }
        }
    }

    @Test
    public void changingStyleActuallyChangesTheFullTemplateAndKeepsManualOverrides() {
        ThemeEditor editor = new ThemeEditor(ThemePresets.Preset.OCEAN.profile());
        editor.set(ThemeEditor.Slot.SUCCESS, false, "#126B42", 0);
        editor.clear(ThemeEditor.Slot.OUTLINE, true);
        editor.set(ThemeEditor.Slot.DIALOG_OPACITY, true, null, 0.85f);
        ThemeProfile before = editor.draft();
        ThemeProfile after = ThemePresets.withPaletteStyle(before, ThemePaletteStyle.MONOCHROME);
        assertNotEquals(before.light.primary, after.light.primary);
        assertNotEquals(before.light.surfaceContainerHigh, after.light.surfaceContainerHigh);
        assertEquals("#126B42", after.light.success);
        assertNull(after.dark.outline);
        assertEquals(Float.valueOf(0.85f), after.dark.dialogOpacity);
        assertEquals(before.name, after.name);
        assertEquals(before.mode, after.mode);
        assertEquals("#126B42", before.light.success);
        assertEquals(ThemePaletteStyle.VIBRANT.id(), before.paletteStyle);
    }

    @Test
    public void wallpaperRemainsDynamicAndUsesTheActualWallpaperColor() {
        ThemeProfile profile = ThemePresets.Preset.WALLPAPER.profile();
        assertEquals(ThemeProfile.SEED_WALLPAPER, profile.seedSource);
        assertNull(profile.light.surface);
        ThemeEditor editor = new ThemeEditor(profile);
        ThemeTokens blue = editor.preview(false, 0xFF1565C0);
        ThemeTokens red = editor.preview(false, 0xFFAE315B);
        assertNotEquals(blue.colorPrimary(), red.colorPrimary());
        assertFalse(ThemeResolver.lastDiagnostic().startsWith("fallback"));
    }

    @Test
    public void templatesAndDraftReplacementAreDeepCopiesAndKeepTheOriginalDirtyBaseline() {
        ThemeProfile original = ThemePresets.Preset.OCEAN.profile();
        String snapshot = ThemeProfileCodec.encode(original);
        ThemeEditor editor = new ThemeEditor(original);
        assertFalse(editor.isDirty());
        ThemeProfile rose = ThemePresets.Preset.ROSE.profile();
        assertTrue(editor.replace(rose).success());
        assertTrue(editor.isDirty());
        rose.light.surface = "#000000";
        assertNotEquals(rose.light.surface, editor.draft().light.surface);
        assertEquals(snapshot, ThemeProfileCodec.encode(original));
        assertTrue(editor.reset().success());
        assertTrue(editor.isDirty());
        assertEquals(ThemeTokens.light(), editor.preview(false, 0));
        editor.replace(original);
        assertFalse(editor.isDirty());
        assertEquals(snapshot, ThemeProfileCodec.encode(ThemePresets.Preset.OCEAN.profile()));
    }

    @Test
    public void invalidReplacementCannotPoisonTheDraft() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        ThemeProfile invalid = ThemeProfile.defaultProfile();
        invalid.light.surface = "not-a-color";
        assertFalse(editor.replace(invalid).success());
        assertFalse(editor.replace(null).success());
        assertFalse(editor.isDirty());
    }

    @Test
    public void readableTextUsesRealContrastNotWeightedRgbBrightness() {
        for (int color : new int[]{0xFF6750A4, 0xFF007F7A, 0xFFFFCC55, 0xFFFCFAF8, 0xFF1F272D}) {
            int on = ThemeEditorUi.readable(0xFF888888, color);
            assertTrue(ThemeContrast.ratio(on, color) >= 4.5);
        }
    }
}
