package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeGridPolicyTest {

    @Test
    public void layout_wrapsWhenListModeOrShortGrid() {
        assertEquals(TmdbEpisodeGridPolicy.WRAP_CONTENT, TmdbEpisodeGridPolicy.layout(false, 120, 2, 128).heightPx());
        assertEquals(TmdbEpisodeGridPolicy.WRAP_CONTENT, TmdbEpisodeGridPolicy.layout(true, 6, 2, 128).heightPx());
        assertFalse(TmdbEpisodeGridPolicy.layout(true, 6, 2, 128).nestedScrolling());
    }

    @Test
    public void layout_limitsLongGridToThreeRows() {
        TmdbEpisodeGridPolicy.Layout layout = TmdbEpisodeGridPolicy.layout(true, 80, 2, 128);

        assertEquals(384, layout.heightPx());
        assertTrue(layout.nestedScrolling());
    }

    @Test
    public void tvAdaptiveSpanCountUsesFourColumnsOn1080pTvDensity() {
        assertEquals(3, TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(599));
        assertEquals(4, TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(686));
        assertEquals(5, TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(1100));
    }

    @Test
    public void verticalFocusTargetMovesBySpanInsideGrid() {
        assertEquals(4, TmdbEpisodeGridPolicy.verticalFocusTarget(0, 4, 20, true));
        assertEquals(8, TmdbEpisodeGridPolicy.verticalFocusTarget(4, 4, 20, true));
        assertEquals(0, TmdbEpisodeGridPolicy.verticalFocusTarget(4, 4, 20, false));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.verticalFocusTarget(0, 4, 20, false));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.verticalFocusTarget(16, 4, 20, true));
        assertEquals(5, TmdbEpisodeGridPolicy.verticalFocusTarget(3, 4, 6, true));
    }

    @Test
    public void verticalFocusTargetReachesShortLastRowFromEveryColumn() {
        assertEquals(3, TmdbEpisodeGridPolicy.verticalFocusTarget(0, 3, 4, true));
        assertEquals(3, TmdbEpisodeGridPolicy.verticalFocusTarget(1, 3, 4, true));
        assertEquals(3, TmdbEpisodeGridPolicy.verticalFocusTarget(2, 3, 4, true));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.verticalFocusTarget(3, 3, 4, true));
    }

    @Test
    public void horizontalFocusTargetStaysInsideTheCurrentRow() {
        assertEquals(1, TmdbEpisodeGridPolicy.horizontalFocusTarget(0, 4, 20, true));
        assertEquals(0, TmdbEpisodeGridPolicy.horizontalFocusTarget(1, 4, 20, false));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.horizontalFocusTarget(0, 4, 20, false));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.horizontalFocusTarget(3, 4, 20, true));
        assertEquals(5, TmdbEpisodeGridPolicy.horizontalFocusTarget(4, 4, 20, true));
        assertEquals(6, TmdbEpisodeGridPolicy.horizontalFocusTarget(7, 4, 20, false));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.horizontalFocusTarget(-1, 4, 20, true));
    }

    @Test
    public void horizontalFocusTargetUsesTheShortLastRowBoundary() {
        // 最后一行为 4 项中的第 4 项（行内只有 1 个）：左右都不应再移动。
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.horizontalFocusTarget(3, 3, 4, true));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.horizontalFocusTarget(3, 3, 4, false));
        // 第一行满行时向右正常推进。
        assertEquals(1, TmdbEpisodeGridPolicy.horizontalFocusTarget(0, 3, 4, true));
        assertEquals(2, TmdbEpisodeGridPolicy.horizontalFocusTarget(1, 3, 4, true));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.horizontalFocusTarget(2, 3, 4, true));
    }

    @Test
    public void holdBasePositionAdvancesFromTheLastRequestedCard() {
        // 焦点已在 4，且上一次请求的目标就是它：基准应为焦点本身。
        assertEquals(4, TmdbEpisodeGridPolicy.holdBasePosition(4, 4));
        // 焦点还没落到 4（仍停在 0）时，上一次请求的 4 才是基准，重复按键才能继续推进到 8。
        assertEquals(4, TmdbEpisodeGridPolicy.holdBasePosition(0, 4));
        // 没有待定目标（首次按键、松手、或已收敛）时退回焦点位。
        assertEquals(7, TmdbEpisodeGridPolicy.holdBasePosition(7, TmdbEpisodeGridPolicy.NO_FOCUS_TARGET));
    }

    @Test
    public void holdBasePositionKeepsRepeatingFromThePendingTarget() {
        int focused = 2;
        int pending = TmdbEpisodeGridPolicy.NO_FOCUS_TARGET;
        int[] expected = {6, 10, 14};
        for (int step : expected) {
            int base = TmdbEpisodeGridPolicy.holdBasePosition(focused, pending);
            pending = TmdbEpisodeGridPolicy.verticalFocusTarget(base, 4, 40, true);
            assertEquals(step, pending);
        }
    }

    @Test
    public void shouldUseFallbackImageOnlyForLegacySmallGridPolicy() {
        assertFalse(TmdbEpisodeGridPolicy.shouldUseFallbackImage(false, 120));
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 24));
        assertFalse(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 25));
    }

    @Test
    public void shouldUseFallbackImageForAnyEpisodeInGridMode() {
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 100, false));
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 100, true));
        assertFalse(TmdbEpisodeGridPolicy.shouldUseFallbackImage(false, 100, false));
    }
}
