package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;

import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.card.MaterialCardView;

final class TmdbCardFocusHelper {

    private static final int FOCUS_ELEVATION_DP = 8;

    /**
     * 焦点环宽度（px）：唯一来源 {@code @dimen/webhtv_focus_ring_width}。
     *
     * <p>此前这里写死 {@code FOCUS_STROKE_DP = 3}，而详情页/播放页的卡片是**代码挂环**
     * （不走 drawable 层），所以前几轮统一 drawable 宽度时漏掉了这批卡片，
     * 用户报告「个性推荐等卡片的边框粗细没有改小没有统一」。
     */
    private static int focusStrokePx(MaterialCardView card) {
        return card.getContext().getResources()
                .getDimensionPixelSize(com.fongmi.android.tv.R.dimen.webhtv_focus_ring_width);
    }

    interface FocusCallback {
        void onFocus(boolean focused);
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor) {
        bind(card, backgroundColor, strokeColor, 1);
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor, int strokeWidthDp) {
        bind(card, backgroundColor, strokeColor, strokeWidthDp, null);
    }

    static void bind(MaterialCardView card, int backgroundColor, int strokeColor, int strokeWidthDp, FocusCallback callback) {
        clearStateOverlay(card);
        card.setOnFocusChangeListener(null);
        apply(card, card.hasFocus(), backgroundColor, strokeColor, strokeWidthDp);
        card.setOnFocusChangeListener((view, focused) -> {
            apply(card, focused, backgroundColor, strokeColor, strokeWidthDp);
            if (callback != null) callback.onFocus(focused);
        });
    }

    private static void clearStateOverlay(MaterialCardView card) {
        card.setSelected(false);
        card.setActivated(false);
        card.setChecked(false);
        card.setForeground(null);
        card.setRippleColor(ColorStateList.valueOf(0x00000000));
        card.setStateListAnimator(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) card.setDefaultFocusHighlightEnabled(false);
    }

    private static void apply(MaterialCardView card, boolean focused, int backgroundColor, int strokeColor, int strokeWidthDp) {
        int focus = ThemeController.focusRingColor(card.getContext());
        int focusWidth = focusStrokePx(card);
        card.setCardBackgroundColor(backgroundColor);
        card.setStrokeColor(focused ? focus : strokeColor);
        card.setStrokeWidth(focused ? focusWidth : ResUtil.dp2px(strokeWidthDp));
        card.setCardElevation(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
        card.setTranslationZ(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
        card.setForeground(focused ? foregroundBorder(card, focus, focusWidth) : null);
        card.animate().cancel();
        card.setScaleX(1f);
        card.setScaleY(1f);
    }

    /** 描边宽度接受**像素**值（调用方自己从 token 或 dp 换算），避免再一次写死 dp。 */
    static GradientDrawable foregroundBorder(MaterialCardView card, int strokeColor, int strokeWidthPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(card.getRadius());
        drawable.setStroke(strokeWidthPx, strokeColor);
        return drawable;
    }
}
