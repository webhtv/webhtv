package com.fongmi.android.tv.following;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowingUpdateCoordinatorErrorTest {

    @Test
    public void tmdbDegradationReasonWinsOverSourceProbeFailure() {
        // 既有 device 断言逐字锁定这句文案，探针失败不得覆盖它。
        FollowingSource source = source("来源没有可播放线路");

        assertEquals("TMDB不可用，已回退原站",
                FollowingUpdateCoordinator.composeLastError(new IOException("TMDB unavailable"), true, source));
    }

    @Test
    public void sourceProbeFailureReasonIsPersistedWhenTmdbIsAvailable() {
        FollowingSource source = source("来源没有可播放线路");

        assertEquals("来源没有可播放线路",
                FollowingUpdateCoordinator.composeLastError(null, true, source));
    }

    @Test
    public void blankSourceErrorFallsBackToTheGenericFailureText() {
        // 静默零值（探针跑完但可播集数为 0）没有 lastError，必须落到通用失败原因，
        // 否则卡片只会显示「尚未检查」，用户看不到任何失败信息。
        assertEquals("来源检查失败",
                FollowingUpdateCoordinator.composeLastError(null, true, source("")));
        assertEquals("来源检查失败",
                FollowingUpdateCoordinator.composeLastError(null, true, null));
    }

    @Test
    public void successfulCheckClearsTheStoredError() {
        assertEquals("", FollowingUpdateCoordinator.composeLastError(null, false, source("陈旧原因")));
    }

    @Test
    public void alreadyProbedSourceIsNotProbedTwiceInOneCheck() {
        // device 测试断言探针调用次数==1：首次探针已拿到快照时，后面那次不得再探。
        assertFalse(FollowingUpdateCoordinator.shouldProbeAgain(true, true));
    }

    @Test
    public void dueSourceIsProbedWhenItWasNotProbedYet() {
        assertTrue(FollowingUpdateCoordinator.shouldProbeAgain(false, true));
        assertFalse(FollowingUpdateCoordinator.shouldProbeAgain(false, false));
    }

    @Test
    public void probeThatYieldedNoPlayableEpisodeCountsAsFailure() {
        // 探针跑过但零可播集数（线路解析不出集号）此前被判成成功，必须与「没跑出快照」同判。
        FollowingSourceProbe.FollowingSourceSnapshot zero = snapshot(0);
        FollowingSourceProbe.FollowingSourceSnapshot some = snapshot(3);

        assertTrue(FollowingUpdateCoordinator.probeYieldedNoPlayable(null));
        assertTrue(FollowingUpdateCoordinator.probeYieldedNoPlayable(zero));
        assertFalse(FollowingUpdateCoordinator.probeYieldedNoPlayable(some));
    }

    @Test
    public void zeroPlayableEpisodeFallsBackToTheGenericFailureText() {
        // 零可播集数要真的落库成失败原因，否则 FILTER_FAILED 与卡片都看不到这次失败。
        assertEquals("来源检查失败", FollowingUpdateCoordinator.composeLastError(null,
                FollowingUpdateCoordinator.probeYieldedNoPlayable(snapshot(0)), source("")));
    }

    @Test
    public void sourceCheckFailedIgnoresBlankAndTmdbDegradedText() {
        // 筛选与卡片的共同判据：空白与 TMDB 降级（来源正常）都不算来源检查失败。
        assertFalse(FollowingUpdateCoordinator.sourceCheckFailed(null));
        assertFalse(FollowingUpdateCoordinator.sourceCheckFailed(""));
        assertFalse(FollowingUpdateCoordinator.sourceCheckFailed("  "));
        assertFalse(FollowingUpdateCoordinator.sourceCheckFailed(FollowingUpdateCoordinator.TMDB_DEGRADED));
        assertTrue(FollowingUpdateCoordinator.sourceCheckFailed("来源没有可播放线路"));
    }

    private static FollowingSourceProbe.FollowingSourceSnapshot snapshot(int playableEpisode) {
        FollowingSourceProbe.FollowingSourceSnapshot snapshot = new FollowingSourceProbe.FollowingSourceSnapshot();
        snapshot.playableEpisode = playableEpisode;
        return snapshot;
    }

    private static FollowingSource source(String lastError) {
        FollowingSource source = new FollowingSource();
        source.followingKey = "tmdb:tv:1:s1";
        source.lastError = lastError;
        return source;
    }
}
