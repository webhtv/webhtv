package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Player;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreCachePolicyTest {

    @Test
    public void initialAndRecoveryUseIndependentWatermarks() {
        assertEquals(5_000, target(false, -1, 0, 128));
        assertEquals(8_000, target(true, -1, 0, 128));
    }

    @Test
    public void highBitrateTargetFitsInsideEffectiveCapacity() {
        assertEquals(3_435, target(false, -1, 100, 51.2));
        assertEquals(3_435, target(true, -1, 100, 51.2));
    }

    @Test
    public void targetNeverExtendsPastMediaEnd() {
        assertEquals(1_500, target(false, 1_500, 0, 128));
        assertEquals(2_500, target(true, 2_500, 0, 128));
    }

    @Test
    public void loadingRequiresFullWatermark() {
        assertFalse(PreCachePolicy.hasSafeBuffer(4_999, true, 5_000, false));
        assertTrue(PreCachePolicy.hasSafeBuffer(5_000, true, 5_000, false));
        assertFalse(PreCachePolicy.hasSafeBuffer(7_999, true, 8_000, true));
    }

    @Test
    public void preloadWaitsForAStablePlayingWindow() {
        assertFalse(PreCachePolicy.isPlaybackStableForPreload(4_999, 5_000, true, false));
        assertFalse(PreCachePolicy.isPlaybackStableForPreload(5_000, 5_000, false, false));
        assertFalse(PreCachePolicy.isPlaybackStableForPreload(5_000, 5_000, true, true));
        assertTrue(PreCachePolicy.isPlaybackStableForPreload(5_001, 5_000, true, false));
    }

    @Test
    public void seekSuppressionRequiresReadyIdleAndSafeBuffer() {
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_BUFFERING, true, false, true));
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, false, false, true));
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, true, true, true));
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, true, false, false));
        assertTrue(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, true, false, true));
    }

    @Test
    public void repeatedPreloadFailuresOpenCircuitAtThreshold() {
        assertFalse(PreCache.shouldOpenPreloadFailureCircuit(1));
        assertTrue(PreCache.shouldOpenPreloadFailureCircuit(2));
    }

    @Test
    public void loadControlIdleFallbackStillKeepsAReserve() {
        assertFalse(PreCachePolicy.hasSafeBuffer(1_999, false, 5_000, false));
        assertTrue(PreCachePolicy.hasSafeBuffer(2_000, false, 5_000, false));
        assertFalse(PreCachePolicy.hasSafeBuffer(2_999, false, 8_000, true));
        assertTrue(PreCachePolicy.hasSafeBuffer(3_000, false, 8_000, true));
    }

    @Test
    public void preloadRangeUsesAtMostHalfOfDiskCache() {
        assertEquals(20_000, preloadLength(20_000, -1, 50, 512));
        assertEquals(10_737, preloadLength(20_000, -1, 100, 256));
        assertEquals(5_368, preloadLength(120_000, -1, 100, 128));
    }

    @Test
    public void unknownBitrateUsesConservativeRangeEstimate() {
        assertEquals(10_737, preloadLength(20_000, -1, 0, 512));
        assertEquals(3_000, preloadLength(20_000, 3_000, 0, 512));
    }

    @Test
    public void aheadTargetUsesMostOfDiskBudgetWithoutCrossingIt() {
        assertEquals(300_000, aheadTarget(300_000, -1, 8, 512));
        assertEquals(34_359, aheadTarget(300_000, -1, 100, 512));
        assertEquals(68_719, aheadTarget(300_000, -1, 0, 512));
    }

    @Test
    public void wholeMediaTargetStillHonorsRemainingDurationAndCapacity() {
        assertEquals(120_000, aheadTarget(Long.MAX_VALUE, 120_000, 8, 512));
        assertEquals(429_496, aheadTarget(Long.MAX_VALUE, -1, 8, 512));
    }

    @Test
    public void refillWatermarkCreatesAStableHysteresisBand() {
        assertEquals(240_000, PreCachePolicy.preloadResumeWatermarkMs(300_000, 20_000));
        assertEquals(40_000, PreCachePolicy.preloadResumeWatermarkMs(60_000, 20_000));
        assertEquals(0, PreCachePolicy.preloadResumeWatermarkMs(10_000, 20_000));
    }

    @Test
    public void completedRangesWaitBeforeStartingAnotherTask() {
        assertEquals(5_000, PreCachePolicy.nextRangeDelayMs(true));
        assertEquals(0, PreCachePolicy.nextRangeDelayMs(false));
    }

    /**
     * 硬暂停名单必须与 {@link AutoPreloadPolicy.Reason} 的语义一一对应。
     *
     * <p>为什么需要这条守卫：{@code PreCache.effectiveAutoDecision()} 会把
     * {@code evaluate()} 的暂停结论**改回**「1 线程 / 10 s 继续预载」，唯一的否决点就是
     * {@code isHardAutomaticPause()}。新增一个语义上属于硬暂停的原因却忘了登记，
     * 策略单测仍会全绿（它们直接调 {@code evaluate()}，不经过 {@code PreCache}），
     * 生产上却完全失效——{@code FOREGROUND_EOF_RECOVERY} 就是这样被漏掉的，
     * 「让预加载给前台恢复让路」退化成「照旧抢同一条脆弱连接」。
     *
     * <p>双向断言：枚举里的每个原因都必须被明确分类，既不能漏登记硬暂停，也不能把
     * 缓冲区/吞吐类原因误升为硬暂停（那会停掉既有的「降级继续跑」行为）。
     *
     * <p>本用例把枚举的**每一个**原因都显式归入「硬暂停」或「降级继续跑」两张表，
     * 再断言两表并集恰好覆盖枚举全集。因此新增原因时本用例会**立即**失败（新标签
     * 两张表都不在），迫使作者显式决定它属于哪一类——这正是漏登
     * {@code FOREGROUND_EOF_RECOVERY} 时缺失的保护。
     *
     * <p>为什么不能只写「硬暂停表」再逐个比对：那样新增的原因在两张表里都是
     * {@code false}，{@code false == false} 恒真，用例照样全绿，守卫形同虚设。
     */
    @Test
    public void hardAutomaticPauseMatchesTheResourceReasonSet() {
        Set<String> hard = Set.of(
                AutoPreloadPolicy.Reason.SESSION_MISMATCH.label(),
                AutoPreloadPolicy.Reason.MEMORY_PRESSURE.label(),
                AutoPreloadPolicy.Reason.NETWORK_UNAVAILABLE.label(),
                AutoPreloadPolicy.Reason.NETWORK_UNVALIDATED.label(),
                AutoPreloadPolicy.Reason.DATA_SAVER.label(),
                AutoPreloadPolicy.Reason.POWER_SAVE.label(),
                AutoPreloadPolicy.Reason.THERMAL_PRESSURE.label(),
                AutoPreloadPolicy.Reason.FOREGROUND_EOF_RECOVERY.label());
        // 其余原因只降级（缩任务/降线程），不停磁盘预载。这张表同样必须显式维护。
        Set<String> degraded = Set.of(
                AutoPreloadPolicy.Reason.REBUFFER.label(),
                AutoPreloadPolicy.Reason.NETWORK_CHANGED.label(),
                AutoPreloadPolicy.Reason.METERED.label(),
                AutoPreloadPolicy.Reason.ROAMING.label(),
                AutoPreloadPolicy.Reason.DATA_SAVER_WHITELISTED.label(),
                AutoPreloadPolicy.Reason.THERMAL_MODERATE.label(),
                AutoPreloadPolicy.Reason.FOREGROUND_RECOVERY.label(),
                AutoPreloadPolicy.Reason.FRONT_BUFFER_LOW.label(),
                AutoPreloadPolicy.Reason.FRONT_BUFFER_MARGIN.label(),
                AutoPreloadPolicy.Reason.BUFFER_DECLINING.label(),
                AutoPreloadPolicy.Reason.BUFFER_EVIDENCE_UNKNOWN.label(),
                AutoPreloadPolicy.Reason.TIME_TO_EMPTY.label(),
                AutoPreloadPolicy.Reason.THROUGHPUT_DEFICIT.label(),
                AutoPreloadPolicy.Reason.SHORT_WINDOW_DEFICIT.label(),
                AutoPreloadPolicy.Reason.THROUGHPUT_MARGIN.label(),
                AutoPreloadPolicy.Reason.SHORT_WINDOW_DECLINE.label(),
                AutoPreloadPolicy.Reason.THROUGHPUT_EVIDENCE_UNKNOWN.label(),
                AutoPreloadPolicy.Reason.MEDIA_BITRATE_UNKNOWN.label(),
                AutoPreloadPolicy.Reason.PREDICTION_ERROR.label(),
                AutoPreloadPolicy.Reason.PATH_LIMITED.label(),
                AutoPreloadPolicy.Reason.VPN.label(),
                AutoPreloadPolicy.Reason.PRELOAD_CONTENTION.label(),
                AutoPreloadPolicy.Reason.SYSTEM_EVIDENCE_UNKNOWN.label(),
                AutoPreloadPolicy.Reason.EXTERNAL_BUFFER_MARGIN.label(),
                AutoPreloadPolicy.Reason.EVIDENCE_UNKNOWN.label(),
                AutoPreloadPolicy.Reason.RECOVERY_STABILIZING.label(),
                AutoPreloadPolicy.Reason.STABLE_NORMAL.label(),
                AutoPreloadPolicy.Reason.STABLE_FAST.label(),
                AutoPreloadPolicy.Reason.FAST_FALLBACK.label(),
                AutoPreloadPolicy.Reason.DISRUPTED.label());

        Set<String> classified = new HashSet<>(hard);
        classified.addAll(degraded);
        assertEquals("every Reason must be classified as hard-pause or degraded",
                AutoPreloadPolicy.Reason.values().length, classified.size());

        for (AutoPreloadPolicy.Reason reason : AutoPreloadPolicy.Reason.values()) {
            assertEquals("hard-pause classification of " + reason.label(),
                    hard.contains(reason.label()), PreCache.isHardAutomaticPause(reason.label()));
        }
        assertFalse(PreCache.isHardAutomaticPause(null));
        assertFalse(PreCache.isHardAutomaticPause("no-such-reason"));
    }

    /**
     * 前台 EOF 恢复期间必须真的停预载，而不是只降级。
     *
     * <p>这条用例锁的是 {@code effectiveAutoDecision()} 里「降级为 1 线程 / 10 s 继续跑」
     * 那条分支的**否决条件**：只要原因没被登记为硬暂停，{@code evaluate()} 的暂停结论
     * 就会被改写成「1 线程 / 10 s / degraded / *-continuous」继续预载。因此这里断言
     * {@code isHardAutomaticPause} 对该原因返回 true，并同时用缓冲区/吞吐类原因作对照，
     * 确保既有的「降级继续跑」行为没有被误伤。
     *
     * <p>注意本用例不直接调用 {@code effectiveAutoDecision()}（它需要 Android 上下文与
     * 播放器实例，纯 JVM 单测无法构造），而是锁定它唯一的否决点。该否决点一旦失守，
     * 生产上「让预加载给前台恢复让路」就退化成「照旧抢同一条脆弱连接」。
     */
    @Test
    public void foregroundEofRecoveryIsNotDowngradedBackToContinuousPreload() {
        assertTrue(PreCache.isHardAutomaticPause(
                AutoPreloadPolicy.Reason.FOREGROUND_EOF_RECOVERY.label()));
        // 对照组：缓冲区类原因必须仍然走「降级继续跑」，否则会停掉既有行为。
        assertFalse(PreCache.isHardAutomaticPause(
                AutoPreloadPolicy.Reason.FRONT_BUFFER_LOW.label()));
        assertFalse(PreCache.isHardAutomaticPause(
                AutoPreloadPolicy.Reason.THROUGHPUT_DEFICIT.label()));
    }

    private static long target(boolean recovery, long remainingMs, double bitrateMbps, double capacityMib) {
        long bitrate = Math.round(bitrateMbps * 1_000_000);
        int capacity = (int) Math.round(capacityMib * 1024 * 1024);
        return PreCachePolicy.safeBufferTargetMs(recovery, remainingMs, bitrate, capacity);
    }

    private static long preloadLength(long configuredMs, long remainingMs, double bitrateMbps, double capacityMib) {
        long bitrate = Math.round(bitrateMbps * 1_000_000);
        long capacity = Math.round(capacityMib * 1024 * 1024);
        return PreCachePolicy.preloadLengthMs(configuredMs, remainingMs, bitrate, capacity);
    }

    private static long aheadTarget(long configuredMs, long remainingMs, double bitrateMbps, double capacityMib) {
        long bitrate = Math.round(bitrateMbps * 1_000_000);
        long capacity = Math.round(capacityMib * 1024 * 1024);
        return PreCachePolicy.preloadAheadTargetMs(configuredMs, remainingMs, bitrate, capacity);
    }
}
