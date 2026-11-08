package com.fongmi.android.tv.theme;

import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.SchemeContent;
import com.google.android.material.color.utilities.SchemeExpressive;
import com.google.android.material.color.utilities.SchemeFidelity;
import com.google.android.material.color.utilities.SchemeFruitSalad;
import com.google.android.material.color.utilities.SchemeMonochrome;
import com.google.android.material.color.utilities.SchemeNeutral;
import com.google.android.material.color.utilities.SchemeRainbow;
import com.google.android.material.color.utilities.SchemeTonalSpot;
import com.google.android.material.color.utilities.SchemeVibrant;

/** Material palette variants inspired by aShellYou's palette-style selector. */
public enum ThemePaletteStyle {
    TONAL_SPOT("tonalSpot"),
    VIBRANT("vibrant"),
    EXPRESSIVE("expressive"),
    RAINBOW("rainbow"),
    FRUIT_SALAD("fruitSalad"),
    FIDELITY("fidelity"),
    CONTENT("content"),
    NEUTRAL("neutral"),
    MONOCHROME("monochrome");

    private final String id;

    ThemePaletteStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ThemePaletteStyle from(String value) {
        if (value == null) return TONAL_SPOT;
        for (ThemePaletteStyle style : values()) {
            if (style.id.equalsIgnoreCase(value) || style.name().equalsIgnoreCase(value)) return style;
        }
        return TONAL_SPOT;
    }

    public int defaultSeed() {
        return 0xFF6750A4;
    }

    public DynamicScheme create(int seedColor, boolean dark) {
        Hct source = Hct.fromInt(seedColor);
        return switch (this) {
            case TONAL_SPOT -> new SchemeTonalSpot(source, dark, 0.0);
            case VIBRANT -> new SchemeVibrant(source, dark, 0.0);
            case EXPRESSIVE -> new SchemeExpressive(source, dark, 0.0);
            case RAINBOW -> new SchemeRainbow(source, dark, 0.0);
            case FRUIT_SALAD -> new SchemeFruitSalad(source, dark, 0.0);
            case FIDELITY -> new SchemeFidelity(source, dark, 0.0);
            case CONTENT -> new SchemeContent(source, dark, 0.0);
            case NEUTRAL -> new SchemeNeutral(source, dark, 0.0);
            case MONOCHROME -> new SchemeMonochrome(source, dark, 0.0);
        };
    }
}
