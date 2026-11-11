package com.fongmi.android.tv.playback.origin;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OriginHistoryRouteTest {

    @Test
    public void standaloneTmdbDetailModeWithEnabledSiteOpensTheTmdbDetailPage() {
        assertEquals(OriginHistoryRoute.TMDB_DETAIL, OriginHistoryRoute.describe(true, true, true));
        assertTrue(OriginHistoryRoute.isTmdbDetail(OriginHistoryRoute.describe(true, true, true)));
    }

    @Test
    public void nonTmdbDetailModeKeepsDirectPlayback() {
        assertEquals(OriginHistoryRoute.DIRECT_MODE, OriginHistoryRoute.describe(false, false, true));
        assertEquals(OriginHistoryRoute.DIRECT_MODE, OriginHistoryRoute.describe(true, false, true));
        assertEquals(OriginHistoryRoute.DIRECT_MODE, OriginHistoryRoute.describe(false, true, true));
    }

    @Test
    public void sitePolicyDisabledKeepsDirectPlaybackExactlyLikeStandardHistory() {
        assertEquals(OriginHistoryRoute.DIRECT_MODE, OriginHistoryRoute.describe(true, true, false));
        assertFalse(OriginHistoryRoute.isTmdbDetail(OriginHistoryRoute.describe(true, true, false)));
    }

    @Test
    public void describeTakesNoStoredTmdbIdentityBecauseStandardHistoryNeverChecksIt() throws Exception {
        Method describe = OriginHistoryRoute.class.getMethod("describe", boolean.class, boolean.class, boolean.class);
        // 重新引入"历史必须已存 tmdbId/mediaType"这类前置条件会改变签名：参数只能是三个 boolean 判据。
        for (Class<?> type : describe.getParameterTypes()) assertEquals(boolean.class, type);
    }
}
