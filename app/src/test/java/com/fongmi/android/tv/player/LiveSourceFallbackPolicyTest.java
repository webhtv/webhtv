package com.fongmi.android.tv.player;

import static com.fongmi.android.tv.player.LiveSourceFallbackPolicy.Action.NEXT_LINE;
import static com.fongmi.android.tv.player.LiveSourceFallbackPolicy.Action.NEXT_SOURCE;
import static com.fongmi.android.tv.player.LiveSourceFallbackPolicy.Action.NONE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LiveSourceFallbackPolicyTest {

    // ---------- 线路播放失败：decideLineFailure ----------

    @Test
    public void playbackFailurePrefersNextLineOfCurrentChannel() {
        assertEquals(NEXT_LINE, LiveSourceFallbackPolicy.decideLineFailure(true, true, true));
    }

    @Test
    public void playbackFailureOnTheLastLineStillRollsToTheNextLine() {
        // 最后一条线路失败也要绕回换线（绕圈停止由 Activity 的 anchor 保证），绝不直接跳接口。
        assertEquals(NEXT_LINE, LiveSourceFallbackPolicy.decideLineFailure(true, true, true));
    }

    @Test
    public void playbackFailureDoesNotJumpToAnotherLiveSource() {
        // 播放失败没有任何参数组合可以产生 NEXT_SOURCE：即使接口回退开启且有下一个接口。
        assertEquals(NEXT_LINE, LiveSourceFallbackPolicy.decideLineFailure(true, true, true));
        assertEquals(NONE, LiveSourceFallbackPolicy.decideLineFailure(false, true, true));
        assertEquals(NONE, LiveSourceFallbackPolicy.decideLineFailure(true, false, true));
        assertEquals(NONE, LiveSourceFallbackPolicy.decideLineFailure(true, true, false));
    }

    @Test
    public void playbackFailureWithDisabledAutoLineChangeStops() {
        assertEquals(NONE, LiveSourceFallbackPolicy.decideLineFailure(false, true, true));
    }

    @Test
    public void playbackFailureOnSingleLineChannelStops() {
        assertEquals(NONE, LiveSourceFallbackPolicy.decideLineFailure(true, true, false));
    }

    @Test
    public void playbackFailureWithoutChannelStops() {
        assertEquals(NONE, LiveSourceFallbackPolicy.decideLineFailure(true, false, true));
    }

    // ---------- 节目列表拉取失败：decideSourceFailure ----------

    @Test
    public void sourceListFailureJumpsToNextLiveSourceWhenAvailable() {
        assertEquals(NEXT_SOURCE, LiveSourceFallbackPolicy.decideSourceFailure(true, true));
    }

    @Test
    public void sourceListFailureStopsWhenNoNextSourceExists() {
        assertEquals(NONE, LiveSourceFallbackPolicy.decideSourceFailure(true, false));
    }

    @Test
    public void sourceListFailureStopsWhenSourceFallbackDisabled() {
        assertEquals(NONE, LiveSourceFallbackPolicy.decideSourceFailure(false, true));
        assertEquals(NONE, LiveSourceFallbackPolicy.decideSourceFailure(false, false));
    }
}
