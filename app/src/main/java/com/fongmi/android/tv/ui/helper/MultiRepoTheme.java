package com.fongmi.android.tv.ui.helper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

/**
 * 多仓界面的统一配色。
 *
 * <p>电视端取 {@link ThemeTokens} 语义色，与「选择站点」面板（SiteDialog / SiteDialogTheme）同源：
 * 面板 colorSurfaceContainer + outlineVariant 描边，卡片常态 colorSurfaceContainerHigh，
 * 选中 colorPrimaryContainer + primary 描边，聚焦 colorPrimary + onPrimary。
 * 手机端保留原有 Material 蓝，避免改动手机端既有外观。
 */
public final class MultiRepoTheme {

    private static final int LEGACY_ACCENT = 0xFF1A73E8;
    private static final int LEGACY_ACCENT_STROKE = 0xFF174EA6;
    private static final int LEGACY_CONTAINER = 0xFFE8F0FE;
    private static final int LEGACY_CONTAINER_STROKE = 0xFF8AB4F8;
    private static final int LEGACY_CONTAINER_TEXT = 0xFF174EA6;
    private static final int LEGACY_SURFACE = Color.WHITE;
    private static final int LEGACY_STROKE = 0xFFDADCE0;
    private static final int LEGACY_ON_SURFACE = 0xFF202124;
    private static final int LEGACY_ON_SURFACE_VARIANT = 0xFF5F6368;
    private static final int LEGACY_HINT = 0xFF9AA0A6;
    private static final int LEGACY_DANGER = 0xFFD93025;

    private MultiRepoTheme() {
    }

    public static boolean tv() {
        return Util.isLeanback();
    }

    private static ThemeTokens tokens() {
        return ThemeController.current();
    }

    public static int onSurface() {
        return tv() ? tokens().colorOnSurface() : LEGACY_ON_SURFACE;
    }

    public static int onSurfaceVariant() {
        return tv() ? tokens().colorOnSurfaceVariant() : LEGACY_ON_SURFACE_VARIANT;
    }

    /** 说明文字（TV 与站点面板一致用 onSurfaceVariant；手机保留原 #3C4043）。 */
    public static int infoText() {
        return tv() ? tokens().colorOnSurfaceVariant() : 0xFF3C4043;
    }

    public static int hint() {
        return tv() ? tokens().colorOnSurfaceVariant() : LEGACY_HINT;
    }

    public static int danger() {
        return tv() ? tokens().colorError() : LEGACY_DANGER;
    }

    public static int onPrimary() {
        return tv() ? tokens().colorOnPrimary() : Color.WHITE;
    }

    /** 面板背景：电视端对齐站点选择面板，手机端保持原玻璃壳。 */
    public static void applyPanel(View root) {
        if (tv()) root.setBackground(rounded(root.getContext(), tokens().colorSurfaceContainer(), tokens().colorOutlineVariant(), 22, 1));
        else root.setBackgroundResource(com.fongmi.android.tv.R.drawable.shape_shell_proxy_dialog);
    }

    /** 顶部仓库标签 / 左栏接口 / 右栏站点卡片。 */
    public static void chip(MaterialButton button, boolean selected, boolean focused) {
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
        // 手机端保持原行为：已选中项即使获得焦点也继续显示选中态。
        boolean highlight = focused && !selected;
        int fill = highlight ? LEGACY_ACCENT : selected ? LEGACY_CONTAINER : LEGACY_SURFACE;
        int stroke = highlight ? LEGACY_ACCENT_STROKE : selected ? LEGACY_CONTAINER_STROKE : LEGACY_STROKE;
        int text = highlight ? Color.WHITE : selected ? LEGACY_CONTAINER_TEXT : LEGACY_ON_SURFACE;
        paint(button, fill, stroke, text);
    }

    /** 图标按钮（设置齿轮、删除）：电视端常态为 outline 描边的容器色。 */
    public static void iconAction(MaterialButton button, boolean focused, boolean dangerIcon) {
        int icon;
        if (tv()) {
            ThemeTokens t = tokens();
            int fill = focused ? t.colorPrimaryContainer() : t.colorSurfaceContainer();
            int stroke = focused ? t.colorPrimary() : t.colorOutline();
            icon = focused ? t.colorPrimary() : dangerIcon ? t.colorError() : t.colorOnSurfaceVariant();
            button.setBackgroundTintList(ColorStateList.valueOf(fill));
            button.setStrokeColor(ColorStateList.valueOf(stroke));
            button.setIconTint(ColorStateList.valueOf(icon));
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(focused ? LEGACY_ACCENT : LEGACY_SURFACE));
        button.setStrokeColor(ColorStateList.valueOf(focused ? LEGACY_ACCENT_STROKE : LEGACY_STROKE));
        button.setIconTint(ColorStateList.valueOf(focused ? Color.WHITE : dangerIcon ? LEGACY_DANGER : LEGACY_ON_SURFACE_VARIANT));
    }

    /** 主操作按钮（新增仓库、确定）。 */
    public static void filled(MaterialButton button, boolean focused) {
        if (tv()) {
            ThemeTokens t = tokens();
            paint(button, focused ? t.colorPrimary() : t.colorPrimaryContainer(),
                    focused ? t.colorOnPrimary() : t.colorPrimary(),
                    focused ? t.colorOnPrimary() : t.colorPrimary());
            return;
        }
        button.setBackgroundColor(LEGACY_ACCENT);
        button.setTextColor(Color.WHITE);
        button.setStrokeWidth(0);
    }

    /** 次操作按钮（取消）：透明底 + 描边。 */
    public static void ghost(MaterialButton button, boolean focused) {
        if (tv()) {
            ThemeTokens t = tokens();
            paint(button, Color.TRANSPARENT, focused ? t.colorPrimary() : t.colorOutline(),
                    focused ? t.colorPrimary() : t.colorOnSurfaceVariant());
            return;
        }
        button.setTextColor(LEGACY_ON_SURFACE_VARIANT);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setStrokeWidth(1);
        button.setStrokeColorResource(com.fongmi.android.tv.R.color.dialog_outlined_button_stroke);
    }

    public static void applyInput(TextInputEditText edit) {
        edit.setTextColor(onSurface());
        edit.setHintTextColor(hint());
        if (tv()) combine(edit);
        else edit.setBackgroundResource(android.R.drawable.edit_text);
    }

    public static void applyLabel(MaterialTextView label) {
        label.setTextColor(onSurfaceVariant());
    }

    private static void paint(MaterialButton button, int fill, int stroke, int text) {
        button.setBackgroundTintList(ColorStateList.valueOf(fill));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setTextColor(ColorStateList.valueOf(text));
    }

    private static void combine(TextInputEditText edit) {
        edit.setBackground(rounded(edit.getContext(), tokens().colorSurfaceContainerHigh(), tokens().colorOutline(), 12, 1));
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
