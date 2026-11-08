package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small, dialog-local rendering helpers. Never changes the app's active palette. */
public final class ThemeEditorUi {

    private ThemeEditorUi() {
    }

    public static TextView text(Context context, CharSequence text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static Button button(Context context, int label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(0xFF1A1C1E); // Replaced with local tokens before the view is shown.
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setAllCaps(false);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        button.setStateListAnimator(null);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    public static void buttonColors(Button button, int fill, int foreground, ThemeTokens tokens, boolean selected) {
        button.setBackgroundTintList(null);
        int ink = readable(foreground, fill);
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{withAlpha(ink, 0.45f), ink}));
        button.setBackground(interactive(button.getContext(), fill, tokens.colorOutline(), tokens.colorFocus(), selected));
        button.setSelected(selected);
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static void heading(TextView text) {
        text.setTypeface(text.getTypeface(), Typeface.BOLD);
    }

    public static GradientDrawable shape(Context context, int fill, int stroke, int width, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, radius));
        if (width > 0) shape.setStroke(dp(context, width), stroke);
        return shape;
    }

    /** A visible focus ring independent of selection, also usable by a TV remote. */
    public static StateListDrawable interactive(Context context, int fill, int outline, int focus, boolean selected) {
        int ring = ThemeContrast.ratio(focus, fill) >= 3 ? focus : readable(focus, fill);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(context, fill, ring, 4, 14));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(context, fill, ring, 4, 14));
        states.addState(new int[]{}, shape(context, fill, selected ? ring : outline, selected ? 2 : 1, 14));
        return states;
    }

    public static int readable(int preferred, int background) {
        if (ThemeContrast.ratio(preferred, background) >= 4.5) return preferred;
        return ThemeContrast.ratio(0xFF000000, background) >= ThemeContrast.ratio(0xFFFFFFFF, background)
                ? 0xFF000000 : 0xFFFFFFFF;
    }

    public static int withAlpha(int color, float opacity) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, Math.round(opacity * 255))) << 24);
    }

    public static float alpha(int color) {
        return (color >>> 24) / 255f;
    }

    public static String hex(int color) {
        return ThemeProfileValidator.formatColor(color);
    }

    public static LinearLayout.LayoutParams fullWidth(Context context, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topMargin);
        return params;
    }

    public static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    public static void padding(View view, int horizontal, int vertical) {
        view.setPadding(dp(view.getContext(), horizontal), dp(view.getContext(), vertical),
                dp(view.getContext(), horizontal), dp(view.getContext(), vertical));
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
