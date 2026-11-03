package com.fongmi.android.tv.ui.helper;

public final class TmdbEpisodeGridPolicy {

    public static final int WRAP_CONTENT = -2;
    public static final int GRID_CARD_HEIGHT_DP = 118;
    public static final int GRID_CARD_BOTTOM_MARGIN_DP = 10;
    public static final int NATIVE_GRID_CARD_HEIGHT_DP = 248;
    public static final int NATIVE_MOBILE_GRID_CARD_HEIGHT_DP = 190;
    public static final int NATIVE_GRID_CARD_BOTTOM_MARGIN_DP = 16;
    public static final int NATIVE_GRID_SCRIM_HEIGHT_DP = 148;
    public static final int NATIVE_MOBILE_GRID_SCRIM_HEIGHT_DP = 104;
    public static final int GRID_FALLBACK_IMAGE_LIMIT = 24;
    public static final int NO_FOCUS_TARGET = -1;

    private static final int MAX_VISIBLE_ROWS = 3;

    private TmdbEpisodeGridPolicy() {
    }

    public static Layout layout(boolean gridMode, int itemCount, int spanCount, int rowHeightPx) {
        if (!gridMode || itemCount <= 0 || spanCount <= 0 || rowHeightPx <= 0) return Layout.wrap();
        int rows = (itemCount + spanCount - 1) / spanCount;
        if (rows <= MAX_VISIBLE_ROWS) return Layout.wrap();
        return new Layout(rowHeightPx * MAX_VISIBLE_ROWS, true);
    }

    public static int nativeEnhancedSpanCount(boolean mobile, boolean pad, boolean landscape, int screenWidthDp) {
        if (mobile) return pad ? landscape ? 4 : 3 : landscape ? 3 : 2;
        return tvAdaptiveSpanCount(screenWidthDp);
    }

    public static int tvAdaptiveSpanCount(int screenWidthDp) {
        if (screenWidthDp >= 1100) return 5;
        if (screenWidthDp >= 600) return 4;
        return 3;
    }

    public static int verticalFocusTarget(int position, int spanCount, int itemCount, boolean down) {
        if (position < 0 || spanCount <= 0 || itemCount <= 0) return NO_FOCUS_TARGET;
        int target = down ? position + spanCount : position - spanCount;
        if (target >= 0 && target < itemCount) return target;
        if (!down) return NO_FOCUS_TARGET;
        int rowStart = position / spanCount * spanCount;
        int lastRowStart = (itemCount - 1) / spanCount * spanCount;
        return rowStart < lastRowStart ? itemCount - 1 : NO_FOCUS_TARGET;
    }

    /**
     * 选集网格内的水平（左右）焦点目标。只在本行内移动，行首向左、行尾向右都返回
     * {@link #NO_FOCUS_TARGET}：集数卡片根视图不可聚焦、也不声明水平 nextFocus，
     * 系统 FocusFinder 找不到水平邻居时会让焦点跳出选集网格（例如落到上方的“简介”按钮）。
     */
    public static int horizontalFocusTarget(int position, int spanCount, int itemCount, boolean right) {
        if (position < 0 || spanCount <= 0 || itemCount <= 0) return NO_FOCUS_TARGET;
        int target = right ? position + 1 : position - 1;
        int rowStart = position / spanCount * spanCount;
        int rowEnd = Math.min(rowStart + spanCount, itemCount) - 1;
        return target >= rowStart && target <= rowEnd ? target : NO_FOCUS_TARGET;
    }

    /**
     * 长按连续移动的基准位置。
     *
     * <p>按住方向键时系统会持续发 {@code ACTION_DOWN}（{@code repeatCount > 0}），而
     * {@code requestFocus()} 是异步的：焦点还没落到上一格时若仍以「当前焦点所在卡片」为基准，
     * 每一个重复事件都会算出同一个目标，表现就是长按只前进一格（用户报告“长按遥控器不能快速选集”）。
     * 这里改用「上一次请求的目标」作为基准，让每个重复事件都真正推进一格；焦点一旦落地，
     * 基准与焦点重合，状态自动收敛，不需要额外清理。
     */
    public static int holdBasePosition(int focusedPosition, int pendingTarget) {
        if (pendingTarget < 0 || pendingTarget == focusedPosition) return focusedPosition;
        return pendingTarget;
    }

    public static int nativeGridCardHeightDp(boolean phoneWidth) {
        return phoneWidth ? NATIVE_MOBILE_GRID_CARD_HEIGHT_DP : NATIVE_GRID_CARD_HEIGHT_DP;
    }

    public static int nativeGridScrimHeightDp(boolean phoneWidth) {
        return phoneWidth ? NATIVE_MOBILE_GRID_SCRIM_HEIGHT_DP : NATIVE_GRID_SCRIM_HEIGHT_DP;
    }

    public static boolean shouldUseFallbackImage(boolean gridMode, int itemCount) {
        return gridMode && itemCount <= GRID_FALLBACK_IMAGE_LIMIT;
    }

    public static boolean shouldUseFallbackImage(boolean gridMode, int itemCount, boolean hasTmdbEpisodeData) {
        return gridMode;
    }

    public record Layout(int heightPx, boolean nestedScrolling) {

        static Layout wrap() {
            return new Layout(WRAP_CONTENT, false);
        }
    }
}
