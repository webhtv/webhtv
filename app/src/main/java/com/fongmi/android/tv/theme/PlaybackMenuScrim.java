package com.fongmi.android.tv.theme;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/**
 * 播放页底部菜单的黑色遮盖层。
 *
 * <p>强度来自主题 profile 的 {@code playerScrimOpacity}（见
 * {@link ThemeController#configuredPlayerScrimOpacity(boolean)}）：未设置时回落到
 * {@link #DEFAULT_OPACITY}，顶部恒为底部的 {@link #TOP_RATIO}，保留从视频到控制条的
 * 柔和过渡。色相固定取 {@code colorPlayerScrim} 的 RGB：主题 profile 只能改 alpha，
 * 不能把播放菜单染成不可读的配色。
 */
public final class PlaybackMenuScrim {

    /** 未设置主题槽时的默认强度；0.80 对应顶部 60% / 底部 80% 黑。 */
    public static final float DEFAULT_OPACITY = 0.80f;

    /** 顶部相对底部的强度比例。 */
    static final float TOP_RATIO = 0.75f;

    private static final float CORNER_DP = 8f;

    private PlaybackMenuScrim() {
    }

    /** The scrim for the current theme snapshot, or {@code null} when unavailable. */
    public static Drawable create(Context context) {
        if (context == null) return null;
        int rgb = ThemeController.current().colorPlayerScrim() & 0x00FFFFFF;
        float opacity = resolveOpacity(ThemeController.configuredPlayerScrimOpacity(!ThemeController.isNight(context)));
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, gradientColors(rgb, opacity));
        float radius = CORNER_DP * context.getResources().getDisplayMetrics().density;
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        return drawable;
    }

    static float resolveOpacity(Float configured) {
        float value = configured == null ? DEFAULT_OPACITY : configured;
        return Math.max(0f, Math.min(1f, value));
    }

    static int[] gradientColors(int rgb, float opacity) {
        float clamped = Math.max(0f, Math.min(1f, opacity));
        return new int[]{withAlpha(rgb, clamped * TOP_RATIO), withAlpha(rgb, clamped)};
    }

    static int withAlpha(int rgb, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return alpha << 24 | (rgb & 0x00FFFFFF);
    }
}
