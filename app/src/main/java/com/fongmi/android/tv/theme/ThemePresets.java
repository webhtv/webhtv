package com.fongmi.android.tv.theme;

import com.fongmi.android.tv.R;
import com.google.android.material.color.utilities.Blend;
import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;

import java.util.Objects;

/** Complete, independently editable light/dark palettes. No preferences or UI state are written here. */
public final class ThemePresets {

    public enum Preset {
        DEFAULT("webhtv.local", "Default", R.string.theme_editor_preset_default, 0, 0, 0, 0, ThemePaletteStyle.TONAL_SPOT),
        OCEAN("webhtv.preset.ocean", "Ocean", R.string.theme_editor_preset_blue,
                0xFF1565C0, 0xFF006B74, 0xFF25734A, 0xFF845B00, ThemePaletteStyle.VIBRANT),
        LAGOON("webhtv.preset.lagoon", "Lagoon", R.string.theme_editor_preset_teal,
                0xFF007F7A, 0xFF535DA8, 0xFF23704F, 0xFF8B5900, ThemePaletteStyle.TONAL_SPOT),
        FOREST("webhtv.preset.forest", "Forest", R.string.theme_editor_preset_green,
                0xFF386A20, 0xFF80561D, 0xFF3F6B24, 0xFF7D6200, ThemePaletteStyle.TONAL_SPOT),
        AMBER("webhtv.preset.amber", "Amber", R.string.theme_editor_preset_orange,
                0xFFA65D13, 0xFF456A51, 0xFF526B27, 0xFF975200, ThemePaletteStyle.FIDELITY),
        ROSE("webhtv.preset.rose", "Rose", R.string.theme_editor_preset_red,
                0xFFAE315B, 0xFF705289, 0xFF247456, 0xFF946000, ThemePaletteStyle.TONAL_SPOT),
        IRIS("webhtv.preset.iris", "Iris", R.string.theme_editor_preset_purple,
                0xFF7043A6, 0xFF8B4A65, 0xFF317450, 0xFF8F6500, ThemePaletteStyle.VIBRANT),
        GRAPHITE("webhtv.preset.graphite", "Graphite", R.string.theme_editor_preset_graphite,
                0xFF596168, 0xFF596168, 0xFF426957, 0xFF78613A, ThemePaletteStyle.NEUTRAL),
        WALLPAPER("webhtv.wallpaper", "Wallpaper", R.string.theme_editor_preset_wallpaper,
                0, 0, 0, 0, ThemePaletteStyle.TONAL_SPOT);

        public final String id;
        public final String name;
        public final int label;
        public final ThemePaletteStyle style;
        private final int seed;
        private final int secondary;
        private final int success;
        private final int warning;

        Preset(String id, String name, int label, int seed, int secondary, int success, int warning,
               ThemePaletteStyle style) {
            this.id = id;
            this.name = name;
            this.label = label;
            this.seed = seed;
            this.secondary = secondary;
            this.success = success;
            this.warning = warning;
            this.style = style;
        }

        public boolean isComplete() {
            return this != DEFAULT && this != WALLPAPER;
        }

        public ThemeProfile profile() {
            return profile(style);
        }

        public ThemeProfile profile(ThemePaletteStyle selectedStyle) {
            ThemeProfile profile = ThemeProfile.defaultProfile();
            profile.id = id;
            profile.name = name;
            profile.paletteStyle = selectedStyle.id();
            if (this == DEFAULT) return profile;
            if (this == WALLPAPER) {
                // Keep this genuinely dynamic: freezing slots here would stop following wallpaper changes.
                profile.seedSource = ThemeProfile.SEED_WALLPAPER;
                return profile;
            }
            profile.seedSource = ThemeProfile.SEED_CUSTOM;
            profile.seedColor = ThemeProfileValidator.formatColor(seed);
            profile.light = slots(false, selectedStyle);
            profile.dark = slots(true, selectedStyle);
            // Store the same safe values the runtime will display, not a misleading pre-correction swatch.
            for (boolean dark : new boolean[]{false, true}) {
                ThemeTokens resolved = new ThemeEditor(profile).preview(dark, 0);
                if (ThemeResolver.lastDiagnostic().startsWith("fallback")) {
                    throw new IllegalStateException("Invalid built-in palette: " + id + "/" + selectedStyle);
                }
                ThemeProfile.SlotSet slots = profile.slots(dark);
                slots.primary = hex(resolved.colorPrimary());
                slots.focus = slots.primary; // Preserve the binder's shared primary/focus baseline mapping.
                // 遮罩上的文字沿用该预设自己的 onPrimary：这里必须落成具体值，否则该槽为空，
                // 预设就不是「完整调色板」了。用户改过遮罩色后它仍会在复合底色上被夹到 ≥4.5:1。
                slots.onItemOverlay = hex(resolved.colorOnPrimary());
            }
            return profile;
        }

        private ThemeProfile.SlotSet slots(boolean dark, ThemePaletteStyle selectedStyle) {
            DynamicScheme scheme = selectedStyle.create(seed, dark);
            ThemeProfile.SlotSet slots = new ThemeProfile.SlotSet();
            boolean monochrome = selectedStyle == ThemePaletteStyle.MONOCHROME;
            double neutralChroma = monochrome ? 0 : selectedStyle == ThemePaletteStyle.NEUTRAL ? 4 : 14;
            double hue = Hct.fromInt(scheme.getSurface()).getHue();
            slots.primary = hex(scheme.getPrimary());
            slots.primaryContainer = hex(scheme.getPrimaryContainer());
            slots.secondaryContainer = hex(monochrome ? scheme.getSecondaryContainer()
                    : Hct.from(Hct.fromInt(secondary).getHue(), 24, dark ? 28 : 90).toInt());
            slots.focus = slots.primary;
            slots.surface = hex(Hct.from(hue, neutralChroma, dark ? 7 : 98).toInt());
            slots.surfaceContainer = hex(Hct.from(hue, neutralChroma, dark ? 12 : 94).toInt());
            slots.surfaceContainerHigh = hex(Hct.from(hue, neutralChroma, dark ? 18 : 90).toInt());
            slots.onSurface = hex(Hct.from(hue, neutralChroma / 2, dark ? 94 : 12).toInt());
            slots.onSurfaceVariant = hex(Hct.from(hue, neutralChroma / 2, dark ? 80 : 32).toInt());
            slots.outline = hex(Hct.from(hue, neutralChroma, dark ? 62 : 48).toInt());
            slots.success = semantic(success, dark);
            // 播放页「当前播放」环色：默认沿用成功色的语义绿，但保持独立槽位可单独编辑。
            slots.playerCurrent = slots.success;
            slots.warning = semantic(warning, dark);
            slots.error = semantic(0xFFBA1A1A, dark);
            // 条目遮罩：40% 黑刚好等于编译期 @color/black_40（#66000000），所以写清这两个槽
            // 不会改变遮罩观感，只是让「完整预设」名下有全部颜色槽（ThemePresetsTest 的契约）。
            slots.itemOverlay = hex(0xFF000000);
            slots.itemOverlayOpacity = 0.40f;
            return slots;
        }

        private String semantic(int color, boolean dark) {
            // Material harmonization shifts hue by at most 15 degrees; green/amber/red remain semantic.
            Hct harmonized = Hct.fromInt(Blend.harmonize(color, seed));
            return hex(Hct.from(harmonized.getHue(), harmonized.getChroma(), dark ? 80 : 36).toInt());
        }
    }

    private ThemePresets() {
    }

    public static Preset find(ThemeProfile profile) {
        if (profile == null) return null;
        for (Preset preset : Preset.values()) {
            if (!preset.id.equals(profile.id)) continue;
            if (preset == Preset.DEFAULT && !ThemeProfile.SEED_NONE.equals(profile.seedSource)) return null;
            return preset;
        }
        return null;
    }

    /** A style change updates template-generated slots, but never discards individual user edits. */
    public static ThemeProfile withPaletteStyle(ThemeProfile source, ThemePaletteStyle style) {
        Preset preset = find(source);
        if (preset == null || !preset.isComplete()) {
            ThemeProfile copy = source.copy();
            copy.paletteStyle = style.id();
            return copy;
        }
        ThemeEditor before = new ThemeEditor(preset.profile(ThemePaletteStyle.from(source.paletteStyle)));
        ThemeEditor current = new ThemeEditor(source);
        ThemeEditor after = new ThemeEditor(preset.profile(style));
        for (boolean dark : new boolean[]{false, true}) {
            for (ThemeEditor.Slot slot : ThemeEditor.Slot.values()) {
                String value = current.valueOf(slot, dark);
                if (Objects.equals(value, before.valueOf(slot, dark))) continue;
                if (value == null) after.clear(slot, dark);
                else if (slot.isColor()) after.set(slot, dark, value, 0);
                else after.set(slot, dark, null, Float.parseFloat(value));
            }
        }
        ThemeProfile result = after.draft();
        result.name = source.name;
        result.mode = source.mode;
        return result;
    }

    private static String hex(int color) {
        return ThemeProfileValidator.formatColor(color);
    }
}
