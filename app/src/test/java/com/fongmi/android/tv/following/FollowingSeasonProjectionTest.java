package com.fongmi.android.tv.following;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowingSeasonProjectionTest {

    /** 锁定回归：季号未知时投影必须回落到追更记录自身的季号，否则 trackedSeason=2 的记录永远匹配不到。 */
    @Test
    public void resolveSeasonFallsBackToTrackedSeason() {
        assertEquals(2, FollowingStore.resolveSeason(-1, 2));
    }

    /** 锁定回归：提示季号也未知（无追更记录）时仍归零，保持旧行为，避免出现 -1 季写入数据库。 */
    @Test
    public void resolveSeasonWithoutHintStaysZero() {
        assertEquals(0, FollowingStore.resolveSeason(-1, -1));
    }

    /** 锁定回归：已确认的第 0 季与未知季的下限一致，不应被回落到别的季号。 */
    @Test
    public void resolveSeasonUnknownWithZeroHintStaysZero() {
        assertEquals(0, FollowingStore.resolveSeason(-1, 0));
    }

    /** 锁定回归：历史已确认是第 0 季时不能被追更记录的季号覆盖。 */
    @Test
    public void resolveSeasonKeepsConfirmedZero() {
        assertEquals(0, FollowingStore.resolveSeason(0, 3));
    }

    /** 锁定回归：历史季号明确时不做任何替换。 */
    @Test
    public void resolveSeasonKeepsKnownSeason() {
        assertEquals(3, FollowingStore.resolveSeason(3, 2));
    }

    /** 锁定回归：未知季历史必须能作为 trackedSeason>=1 记录的候选，否则进度永久停更。 */
    @Test
    public void seasonMatchesAcceptsUnknownForTracked() {
        assertTrue(FollowingStore.seasonMatches(-1, 2));
    }

    /** 锁定回归：两边都未知时也要算候选，保证 reconcile 不会丢弃无季号的历史。 */
    @Test
    public void seasonMatchesAcceptsUnknownForUnknown() {
        assertTrue(FollowingStore.seasonMatches(-1, -1));
    }

    /** 锁定回归：季号相同时照常匹配，修复不能破坏原有正常路径。 */
    @Test
    public void seasonMatchesAcceptsSameSeason() {
        assertTrue(FollowingStore.seasonMatches(2, 2));
    }

    /** 锁定回归：已知季号不同时必须仍然拒之门外，未知季放行不能扩大成跨季误配。 */
    @Test
    public void seasonMatchesRejectsDifferentKnownSeason() {
        assertFalse(FollowingStore.seasonMatches(1, 2));
    }

    /** 锁定回归：已确认的第 0 季不等于其他季，0 不是"未知"的通配值。 */
    @Test
    public void seasonMatchesRejectsZeroAgainstOtherSeason() {
        assertFalse(FollowingStore.seasonMatches(0, 2));
    }
}
