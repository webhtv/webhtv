package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.TintableImageSourceView;

/**
 * Theme-aware rendering for the player OSD/button settings rows.
 *
 * <p>The legacy rows paired a fixed light card with dark-theme foregrounds.
 * This renderer owns both sides of the color pair. Its text and icons opt out
 * of baseline recoloring: an active dark color can equal a different light
 * baseline role and must not be interpreted twice.
 */
public final class PlayerSettingsRowTheme {

    private static final int ROW_RADIUS_DP = 8;
    private static final int SWITCH_RADIUS_DP = 24;

    private PlayerSettingsRowTheme() {
    }

    /** Applies the row surface and the visible/hidden label role. */
    public static void apply(View row, TextView name, ThemeTokens tokens, boolean visible) {
        ThemeTokens safe = tokens == null ? ThemeTokens.dark() : tokens;
        if (row != null) row.setBackground(rowBackground(row.getContext(), safe));
        if (name != null) {
            name.setTag("webhtv:ignore");
            name.setAlpha(1f);
            name.setTextColor(ThemeEditorUi.readable(
                    visible ? safe.colorOnSurface() : safe.colorOnSurfaceVariant(), safe.colorSurfaceContainerHigh()));
        }
    }

    /** Applies the semantic surface and icon roles to the eye/eye-slash control. */
    public static void applySwitch(ImageView view, ThemeTokens tokens) {
        if (view == null) return;
        ThemeTokens safe = tokens == null ? ThemeTokens.dark() : tokens;
        view.setTag("webhtv:ignore");
        view.setBackground(switchBackground(view.getContext(), safe));
        ColorStateList tint = switchTint(safe);
        if (view instanceof TintableImageSourceView support) support.setSupportImageTintList(tint);
        view.setImageTintList(tint);
    }

    static Drawable rowBackground(Context context, ThemeTokens tokens) {
        // Keep the fill stable when a child takes focus: its label does not own
        // the child's drawable state. The ring alone identifies the focused row.
        int fill = tokens.colorSurfaceContainerHigh();
        int ring = ThemeEditorUi.readable(tokens.colorFocus(), fill);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                shape(context, fill, ring, 3, ROW_RADIUS_DP));
        states.addState(new int[]{android.R.attr.state_pressed},
                shape(context, fill, ring, 3, ROW_RADIUS_DP));
        states.addState(new int[]{},
                shape(context, tokens.colorSurfaceContainerHigh(), tokens.colorOutlineVariant(), 1, ROW_RADIUS_DP));
        return states;
    }

    static Drawable switchBackground(Context context, ThemeTokens tokens) {
        int ring = ThemeEditorUi.readable(tokens.colorFocus(), tokens.colorPrimaryContainer());
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                shape(context, tokens.colorPrimaryContainer(), ring, 3, SWITCH_RADIUS_DP));
        states.addState(new int[]{android.R.attr.state_pressed},
                shape(context, tokens.colorPrimaryContainer(), ring, 3, SWITCH_RADIUS_DP));
        states.addState(new int[]{},
                shape(context, tokens.colorSurfaceContainerHighest(), tokens.colorOutlineVariant(), 1, SWITCH_RADIUS_DP));
        return states;
    }

    private static ColorStateList switchTint(ThemeTokens tokens) {
        int[][] states = new int[][]{
                {android.R.attr.state_focused},
                {android.R.attr.state_pressed},
                {}
        };
        int[] colors = new int[]{
                tokens.colorOnPrimaryContainer(),
                tokens.colorOnPrimaryContainer(),
                ThemeEditorUi.readable(tokens.colorOnSurfaceVariant(), tokens.colorSurfaceContainerHighest())
        };
        return new ColorStateList(states, colors);
    }

    private static GradientDrawable shape(Context context, int fill, int stroke, int widthDp, int radiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radiusDp * density);
        drawable.setStroke(Math.max(1, Math.round(widthDp * density)), stroke);
        return drawable;
    }
}
