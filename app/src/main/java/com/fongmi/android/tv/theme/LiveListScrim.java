package com.fongmi.android.tv.theme;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

/**
 * 直播页频道抽屉（列表）的黑色遮盖层。
 *
 * <p>强度来自主题 profile 的 {@code liveListScrimOpacity}（见
 * {@link ThemeController#configuredLiveListScrimOpacity(boolean)}）：未设置时回落到
 * {@link #DEFAULT_OPACITY}。遮盖层是整块统一的一块纯黑，与 {@code shape_live_list.xml} 的
 * {@code black_80} 逐值一致；滑块是线性映射——100% 时整块纯黑，20% 时整块只剩 20% 黑，
 * 左右边缘一致。色相恒为纯黑：主题 profile 只能缩放 alpha，不能改色相，
 * {@code shape_live_focused.xml} 的焦点环对比度前提因此始终成立。
 */
public final class LiveListScrim {

    /** 未设置主题槽时的默认强度；0.80 对应整块 80% 黑。 */
    public static final float DEFAULT_OPACITY = 0.80f;

    private LiveListScrim() {
    }

    /** The scrim for the current theme snapshot, or {@code null} when unavailable. */
    public static Drawable create(Context context) {
        if (context == null) return null;
        float opacity = resolveOpacity(ThemeController.configuredLiveListScrimOpacity(!ThemeController.isNight(context)));
        return new ColorDrawable(withAlpha(opacity));
    }

    static float resolveOpacity(Float configured) {
        float value = configured == null ? DEFAULT_OPACITY : configured;
        return Math.max(0f, Math.min(1f, value));
    }

    static int withAlpha(float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return alpha << 24;
    }
}
