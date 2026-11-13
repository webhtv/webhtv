package com.fongmi.android.tv.ui.helper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

/**
 * 多仓界面的统一配色。
 *
 * <p>电视端取 {@link ThemeTokens} 语义色，与「选择站点」面板同源；手机端改为读取主题
 * Material 属性（colorSurfaceContainer / colorOutline / colorPrimary…），随系统深色/浅色
 * 自动切换，面板背景、卡片与站点选择界面使用同一套 shape_site_dialog + outlined 卡片。
 */
public final class MultiRepoTheme {

    private MultiRepoTheme() {
    }

    public static boolean tv() {
        return Util.isLeanback();
    }

    private static ThemeTokens tokens() {
        return ThemeController.current();
    }

    public static int onSurface(Context context) {
        return tv() ? tokens().colorOnSurface() : attr(context, com.google.android.material.R.attr.colorOnSurface, 0xFF202124);
    }

    public static int onSurfaceVariant(Context context) {
        return tv() ? tokens().colorOnSurfaceVariant() : attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF5F6368);
    }

    /** 说明文字：与站点面板一致用 onSurfaceVariant，深浅色由主题决定。 */
    public static int infoText(Context context) {
        return tv() ? tokens().colorOnSurfaceVariant() : attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF3C4043);
    }

    public static int hint(Context context) {
        return tv() ? tokens().colorOnSurfaceVariant() : attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF9AA0A6);
    }

    public static int danger(Context context) {
        return tv() ? tokens().colorError() : attr(context, androidx.appcompat.R.attr.colorError, 0xFFD93025);
    }

    public static int onPrimary(Context context) {
        return tv() ? tokens().colorOnPrimary() : attr(context, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);
    }

    /** 面板背景：与「选择站点」同一张 shape_site_dialog（colorSurfaceContainer，随主题切换）。 */
    public static void applyPanel(View root) {
        if (tv()) root.setBackground(rounded(root.getContext(), tokens().colorSurfaceContainer(), tokens().colorOutlineVariant(), 22, 1));
        else root.setBackgroundResource(R.drawable.shape_site_dialog);
    }

    /** 顶部仓库标签 / 左栏接口 / 右栏站点卡片。 */
    public static void chip(MaterialButton button, boolean selected, boolean focused) {
        Context context = button.getContext();
        if (tv()) {
            ThemeTokens t = tokens();
            int fill, stroke, text;
            if (focused) {
                fill = t.colorPrimary();
                stroke = t.colorOnPrimary();
                text = t.colorOnPrimary();
            } else if (selected) {
                fill = t.colorPrimaryContainer();
                stroke = t.colorPrimary();
                text = t.colorPrimary();
            } else {
                fill = t.colorSurfaceContainerHigh();
                stroke = t.colorOutlineVariant();
                text = t.colorOnSurface();
            }
            paint(button, fill, stroke, text);
            return;
        }
        // 手机端对齐 adapter_site：常态 colorSurfaceContainer + colorOutline，
        // 选中/聚焦改用 primaryContainer + primary；不再写死蓝色。
        boolean highlight = focused || selected;
        int fill = highlight
                ? attr(context, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFE8F0FE)
                : attr(context, com.google.android.material.R.attr.colorSurfaceContainer, 0xFFFFFFFF);
        int stroke = highlight
                ? attr(context, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8)
                : attr(context, com.google.android.material.R.attr.colorOutline, 0xFFDADCE0);
        int text = highlight
                ? attr(context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF174EA6)
                : attr(context, com.google.android.material.R.attr.colorOnSurface, 0xFF202124);
        paint(button, fill, stroke, text);
    }

    /** 图标按钮（设置齿轮、删除）：与站点的图标的 ?attr/colorPrimary 着色一致。 */
    public static void iconAction(MaterialButton button, boolean focused, boolean dangerIcon) {
        Context context = button.getContext();
        if (tv()) {
            ThemeTokens t = tokens();
            int fill = focused ? t.colorPrimaryContainer() : t.colorSurfaceContainer();
            int stroke = focused ? t.colorPrimary() : t.colorOutline();
            int icon = focused ? t.colorPrimary() : dangerIcon ? t.colorError() : t.colorOnSurfaceVariant();
            button.setBackgroundTintList(ColorStateList.valueOf(fill));
            button.setStrokeColor(ColorStateList.valueOf(stroke));
            button.setIconTint(ColorStateList.valueOf(icon));
            return;
        }
        int fill = focused
                ? attr(context, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFE8F0FE)
                : attr(context, com.google.android.material.R.attr.colorSurfaceContainer, 0xFFFFFFFF);
        int stroke = focused
                ? attr(context, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8)
                : attr(context, com.google.android.material.R.attr.colorOutline, 0xFFDADCE0);
        int icon = focused
                ? attr(context, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8)
                : dangerIcon
                ? attr(context, androidx.appcompat.R.attr.colorError, 0xFFD93025)
                : attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF5F6368);
        button.setBackgroundTintList(ColorStateList.valueOf(fill));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setIconTint(ColorStateList.valueOf(icon));
    }

    /** 主操作按钮（新增仓库、确定）。 */
    public static void filled(MaterialButton button, boolean focused) {
        Context context = button.getContext();
        if (tv()) {
            ThemeTokens t = tokens();
            paint(button, focused ? t.colorPrimary() : t.colorPrimaryContainer(),
                    focused ? t.colorOnPrimary() : t.colorPrimary(),
                    focused ? t.colorOnPrimary() : t.colorPrimary());
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(
                attr(context, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8)));
        button.setTextColor(ColorStateList.valueOf(
                attr(context, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)));
        button.setStrokeWidth(0);
    }

    /** 次操作按钮（取消）：透明底 + 描边。 */
    public static void ghost(MaterialButton button, boolean focused) {
        Context context = button.getContext();
        if (tv()) {
            ThemeTokens t = tokens();
            paint(button, Color.TRANSPARENT, focused ? t.colorPrimary() : t.colorOutline(),
                    focused ? t.colorPrimary() : t.colorOnSurfaceVariant());
            return;
        }
        button.setTextColor(ColorStateList.valueOf(
                focused
                        ? attr(context, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8)
                        : attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF5F6368)));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeWidth(dp(context, 1));
        button.setStrokeColor(ColorStateList.valueOf(
                attr(context, com.google.android.material.R.attr.colorOutline, 0xFFDADCE0)));
    }

    public static void applyInput(TextInputEditText edit) {
        Context context = edit.getContext();
        edit.setTextColor(onSurface(context));
        edit.setHintTextColor(hint(context));
        edit.setBackground(rounded(context,
                tv() ? tokens().colorSurfaceContainerHigh() : attr(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFF3EDF7),
                tv() ? tokens().colorOutline() : attr(context, com.google.android.material.R.attr.colorOutline, 0xFFDADCE0),
                12, 1));
        edit.setPadding(dp(context, 12), edit.getPaddingTop(), dp(context, 12), edit.getPaddingBottom());
    }

    public static void applyLabel(MaterialTextView label) {
        label.setTextColor(onSurfaceVariant(label.getContext()));
    }

    private static void paint(MaterialButton button, int fill, int stroke, int text) {
        button.setBackgroundTintList(ColorStateList.valueOf(fill));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setTextColor(ColorStateList.valueOf(text));
    }

    private static int attr(Context context, int attribute, int fallback) {
        if (context == null) return fallback;
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) return ContextCompat.getColor(context, value.resourceId);
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) return value.data;
        }
        return fallback;
    }

    private static int dp(Context context, int value) {
        return Math.max(1, Math.round(value * context.getResources().getDisplayMetrics().density));
    }

    private static GradientDrawable rounded(Context context, int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radiusDp * context.getResources().getDisplayMetrics().density);
        if (strokeDp > 0) drawable.setStroke(Math.max(1, Math.round(strokeDp * context.getResources().getDisplayMetrics().density)), stroke);
        return drawable;
    }
}
