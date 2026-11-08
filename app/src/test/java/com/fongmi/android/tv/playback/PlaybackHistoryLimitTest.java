package com.fongmi.android.tv.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 按站点读历史时的条数夹取契约。
 *
 * <p>为什么值得单独锁：{@code limit} 是对端能直接控制的输入。若不做上限夹取，
 * 一次 {@code limit=999999} 就能让本机一次性把整张表读进内存并序列化成响应体。
 */
public class PlaybackHistoryLimitTest {

    @Test
    public void missingOrNonPositiveLimitFallsBackToDefault() {
        assertEquals(PlaybackApi.DEFAULT_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(0));
        assertEquals(PlaybackApi.DEFAULT_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(-1));
        assertEquals(PlaybackApi.DEFAULT_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(Integer.MIN_VALUE));
    }

    @Test
    public void limitAboveMaximumIsClamped() {
        assertEquals(PlaybackApi.MAX_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(PlaybackApi.MAX_HISTORY_LIMIT + 1));
        assertEquals(PlaybackApi.MAX_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(999_999));
        assertEquals(PlaybackApi.MAX_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(Integer.MAX_VALUE));
    }

    @Test
    public void limitWithinRangeIsPreserved() {
        assertEquals(1, PlaybackApi.effectiveHistoryLimit(1));
        assertEquals(PlaybackApi.DEFAULT_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(PlaybackApi.DEFAULT_HISTORY_LIMIT));
        assertEquals(PlaybackApi.MAX_HISTORY_LIMIT, PlaybackApi.effectiveHistoryLimit(PlaybackApi.MAX_HISTORY_LIMIT));
    }

    @Test
    public void defaultIsNotAboveMaximum() {
        // 常量本身必须自洽：默认值若超过上限，夹取逻辑就自相矛盾。
        org.junit.Assert.assertTrue(PlaybackApi.DEFAULT_HISTORY_LIMIT <= PlaybackApi.MAX_HISTORY_LIMIT);
    }
}
