package com.fongmi.android.tv.playback.origin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OriginHistoryRouteTest {

    @Test
    public void tmdbIdentityInTmdbDetailModeOpensTheTmdbDetailPage() {
        for (boolean cinema : new boolean[]{false, true}) {
            assertEquals("standalone TMDB detail mode must keep the TMDB page, cinema=" + cinema,
                    OriginHistoryRoute.TMDB_DETAIL, OriginHistoryRoute.describe(true, true, 603, "tv"));
            assertTrue(OriginHistoryRoute.isTmdbDetail(OriginHistoryRoute.describe(true, true, 603, "movie")));
        }
    }

    @Test
    public void nonTmdbDetailModeKeepsDirectPlayback() {
        assertEquals(OriginHistoryRoute.DIRECT_MODE, OriginHistoryRoute.describe(false, false, 603, "tv"));
        assertEquals(OriginHistoryRoute.DIRECT_MODE, OriginHistoryRoute.describe(true, false, 603, "tv"));
        assertFalse(OriginHistoryRoute.isTmdbDetail(OriginHistoryRoute.describe(false, true, 603, "tv")));
    }

    @Test
    public void historyWithoutTmdbIdentityDegradesToDirectPlayback() {
        assertEquals(OriginHistoryRoute.DIRECT_IDENTITY, OriginHistoryRoute.describe(true, true, 0, "tv"));
        assertEquals(OriginHistoryRoute.DIRECT_IDENTITY, OriginHistoryRoute.describe(true, true, -1, "tv"));
        assertEquals(OriginHistoryRoute.DIRECT_IDENTITY, OriginHistoryRoute.describe(true, true, 603, ""));
        assertEquals(OriginHistoryRoute.DIRECT_IDENTITY, OriginHistoryRoute.describe(true, true, 603, null));
        assertEquals(OriginHistoryRoute.DIRECT_IDENTITY, OriginHistoryRoute.describe(true, true, 603, "   "));
    }

    @Test
    public void degradeReasonIsAlwaysReportedInsteadOfSilentlyReturning() {
        assertTrue(OriginHistoryRoute.DIRECT_MODE.startsWith("direct:"));
        assertTrue(OriginHistoryRoute.DIRECT_IDENTITY.startsWith("direct:"));
        assertFalse(OriginHistoryRoute.DIRECT_MODE.equals(OriginHistoryRoute.DIRECT_IDENTITY));
    }
}
