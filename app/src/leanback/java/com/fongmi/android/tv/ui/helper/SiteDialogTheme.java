package com.fongmi.android.tv.ui.helper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.google.android.material.checkbox.MaterialCheckBox;

/** Applies the active semantic palette to the TV site selector's programmatic states. */
public final class SiteDialogTheme {

    private SiteDialogTheme() {
    }

    public static void applyShell(View root, TextView keyword, ImageView... actions) {
        ThemeTokens tokens = ThemeController.current();
        if (root != null) root.setBackground(shell(root.getContext(), tokens));
        if (keyword != null) {
            keyword.setTextColor(tokens.colorOnSurface());
            keyword.setHintTextColor(tokens.colorOnSurfaceVariant());
            keyword.setBackground(input(keyword.getContext(), tokens));
        }
        if (actions != null) for (ImageView action : actions) applyAction(action);
    }

    public static void applySiteItem(View root, TextView text, MaterialCheckBox check) {
        ThemeTokens tokens = ThemeController.current();
        if (root != null) root.setBackground(siteItem(root.getContext(), tokens));
        if (text != null) text.setTextColor(textColors(tokens));
        if (check != null) check.setButtonTintList(checkColors(tokens));
    }

    public static void updateSiteText(TextView text, boolean focused, boolean selected) {
        if (text == null) return;
        ThemeTokens tokens = ThemeController.current();
        text.setTextColor(focused ? tokens.colorOnPrimary() : selected ? tokens.colorPrimary() : tokens.colorOnSurface());
    }

    public static void applyGroup(TextView button) {
        if (button == null) return;
        ThemeTokens tokens = ThemeController.current();
        button.setTextColor(textColors(tokens));
        button.setBackground(groupButton(button.getContext(), tokens));
    }

    public static void applyAction(ImageView action) {
        if (action == null) return;
        ThemeTokens tokens = ThemeController.current();
        action.setImageTintList(actionColors(tokens));
        action.setBackground(actionBackground(action.getContext(), tokens));
    }

    private static GradientDrawable shell(Context context, ThemeTokens tokens) {
        // 面板是浮在暗化背景上的独立层，需要一条可见的轮廓把它和背景分开；
        // 全屏时代面板边缘就是屏幕边缘，0 描边看不出来，收窄成浮层后必须补回来。
        return rounded(context, tokens.colorSurfaceContainer(), tokens.colorOutlineVariant(), 22, 1);
    }

    private static StateListDrawable input(Context context, ThemeTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[]{android.R.attr.state_focused}, rounded(context, tokens.colorPrimaryContainer(), tokens.colorPrimary(), 12, 1));
        result.addState(new int[]{}, rounded(context, tokens.colorSurfaceContainerHigh(), tokens.colorOutline(), 12, 1));
        return result;
    }

    private static StateListDrawable siteItem(Context context, ThemeTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[]{android.R.attr.state_focused}, rounded(context, tokens.colorPrimary(), tokens.colorOnPrimary(), 12, 2));
        result.addState(new int[]{android.R.attr.state_selected}, rounded(context, tokens.colorPrimaryContainer(), tokens.colorPrimary(), 12, 2));
        // 常态填充必须是比面板高一层的容器色：面板用 colorSurfaceContainer，
        // 卡片若也用同一色，卡片边界在面板上完全消失（只剩文字）。
        result.addState(new int[]{}, rounded(context, tokens.colorSurfaceContainerHigh(), tokens.colorOutlineVariant(), 12, 1));
        return result;
    }

    private static StateListDrawable groupButton(Context context, ThemeTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[]{android.R.attr.state_focused}, rounded(context, tokens.colorPrimary(), tokens.colorOnPrimary(), 20, 2));
        result.addState(new int[]{android.R.attr.state_selected}, rounded(context, tokens.colorPrimaryContainer(), tokens.colorPrimary(), 20, 2));
        result.addState(new int[]{}, rounded(context, tokens.colorSurface(), tokens.colorOutline(), 20, 1));
        return result;
    }

    private static StateListDrawable actionBackground(Context context, ThemeTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[]{android.R.attr.state_focused}, rounded(context, tokens.colorPrimaryContainer(), tokens.colorPrimary(), 12, 2));
        result.addState(new int[]{android.R.attr.state_selected}, rounded(context, tokens.colorPrimaryContainer(), tokens.colorPrimary(), 12, 2));
        result.addState(new int[]{}, rounded(context, tokens.colorSurfaceContainer(), tokens.colorOutline(), 12, 1));
        return result;
    }

    private static ColorStateList textColors(ThemeTokens tokens) {
        return new ColorStateList(
                new int[][]{
                        {android.R.attr.state_focused},
                        {android.R.attr.state_selected},
                        {},
                },
                new int[]{tokens.colorOnPrimary(), tokens.colorPrimary(), tokens.colorOnSurface()});
    }

    private static ColorStateList checkColors(ThemeTokens tokens) {
        return new ColorStateList(
                new int[][]{
                        {android.R.attr.state_focused},
                        {android.R.attr.state_checked},
                        {},
                },
                new int[]{tokens.colorOnPrimary(), tokens.colorPrimary(), tokens.colorOnSurfaceVariant()});
    }

    private static ColorStateList actionColors(ThemeTokens tokens) {
        return new ColorStateList(
                new int[][]{
                        {android.R.attr.state_focused},
                        {android.R.attr.state_selected},
                        {},
                },
                new int[]{tokens.colorPrimary(), tokens.colorPrimary(), tokens.colorOnSurfaceVariant()});
    }

    private static GradientDrawable rounded(Context context, int fill, int stroke, int radiusDp, int strokeDp) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radiusDp * density);
        if (strokeDp > 0) drawable.setStroke(Math.max(1, Math.round(strokeDp * density)), stroke);
        return drawable;
    }
}
