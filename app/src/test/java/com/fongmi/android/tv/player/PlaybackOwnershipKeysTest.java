package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackOwnershipKeysTest {

    private static final String HISTORY = "php_\u84dd\u8393@@@7694249839906786328@@@3";
    private static final String EPISODE = "VE:" + HISTORY + "|\u84dd\u8393\u1d38\u1d50\u00b7\u6807\u6e05|3";

    @Test
    public void identicalKeysBelongToTheSameSession() {
        assertTrue(PlaybackOwnershipKeys.sameSession(HISTORY, HISTORY));
        assertTrue(PlaybackOwnershipKeys.sameSession(EPISODE, EPISODE));
    }

    @Test
    public void episodeKeyBelongsToItsHistorySession() {
        assertTrue("the browse-next episode key must stay the same playback session",
                PlaybackOwnershipKeys.sameSession(HISTORY, EPISODE));
        assertTrue(PlaybackOwnershipKeys.sameSession(EPISODE, HISTORY));
    }

    @Test
    public void episodeKeyIsNormalisedToItsHistoryKey() {
        assertEquals(HISTORY, PlaybackOwnershipKeys.sessionKeyOf(EPISODE));
        assertEquals(HISTORY, PlaybackOwnershipKeys.sessionKeyOf(HISTORY));
    }

    @Test
    public void anotherVodOrSiteIsNotTheSameSession() {
        assertFalse(PlaybackOwnershipKeys.sameSession(HISTORY, "php_other@@@7694249839906786328@@@3"));
        assertFalse(PlaybackOwnershipKeys.sameSession(HISTORY, "VE:php_other@@@7694249839906786328@@@3|flag|3"));
        assertFalse(PlaybackOwnershipKeys.sameSession(HISTORY, HISTORY + "0"));
    }

    @Test
    public void episodeKeyForAnotherVodIsNotTheSameSession() {
        String other = "VE:php_other@@@1@@@2|flag|3";
        assertFalse(PlaybackOwnershipKeys.sameSession(HISTORY, other));
    }

    @Test
    public void barePrefixIsNotAcceptedAsTheSameSession() {
        // 裸前缀匹配是错的：php_a 不能吃掉 php_ab 的会话。
        assertFalse(PlaybackOwnershipKeys.sameSession("VE:php_a", "VE:php_ab|flag|3"));
        assertFalse(PlaybackOwnershipKeys.sameSession("php_a", "VE:php_ab|flag|3"));
    }

    @Test
    public void missingExpectedSideNeverReportsMismatch() {
        // 既有语义：expected 为空 = 不限制归属；actual 为空但 expected 非空必须判否。
        assertTrue(PlaybackOwnershipKeys.sameSession(null, EPISODE));
        assertTrue(PlaybackOwnershipKeys.sameSession("", HISTORY));
        assertTrue(PlaybackOwnershipKeys.sameSession("", ""));
        assertFalse(PlaybackOwnershipKeys.sameSession(HISTORY, null));
        assertFalse(PlaybackOwnershipKeys.sameSession(EPISODE, null));
    }

    @Test
    public void recordKeyContainingSeparatorIsStillReversible() {
        // 记录键（SiteKey@@@vodId@@@cid）本身不含 '|'；线路名里出现 '|' 是这里的边界情况：
        // 末两段被当作「线路名 + 集序号」，左侧整体即记录键。
        String historyKey = "php_x@@@1@@@2";
        String episodeKey = "VE:" + historyKey + "|a|b|7";
        assertEquals(historyKey + "|a", PlaybackOwnershipKeys.sessionKeyOf(episodeKey));
    }

    @Test
    public void malformedEpisodeKeyStaysItsOwnKey() {
        assertFalse(PlaybackOwnershipKeys.sameSession(HISTORY, "VE:" + HISTORY));
        assertNull(PlaybackOwnershipKeys.historyKeyOfEpisode("VE:" + HISTORY));
        // 结构不完整时退回原字符串，绝不能因为无法解析就把两个不同会话判成同一会话。
        assertEquals("VE:" + HISTORY, PlaybackOwnershipKeys.sessionKeyOf("VE:" + HISTORY));
        assertEquals("VE:|x|1", PlaybackOwnershipKeys.sessionKeyOf("VE:|x|1"));
    }

    @Test
    public void blankAndNonEpisodeKeysAreTheirOwnSession() {
        assertNull(PlaybackOwnershipKeys.sessionKeyOf(null));
        assertNull(PlaybackOwnershipKeys.sessionKeyOf(""));
        assertEquals("VP:php@@@1@@@2", PlaybackOwnershipKeys.sessionKeyOf("VP:php@@@1@@@2"));
        assertNull(PlaybackOwnershipKeys.historyKeyOfEpisode("VP:php@@@1@@@2"));
    }
}
