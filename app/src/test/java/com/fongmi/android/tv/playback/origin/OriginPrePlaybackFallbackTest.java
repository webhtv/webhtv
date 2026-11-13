package com.fongmi.android.tv.playback.origin;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OriginPrePlaybackFallbackTest {
    @Test
    public void blockedBeforeAPlayerResultContinuesGlobalResume() throws Exception {
        for (String reason : new String[]{"PARSE_FAILED", "PLAYER_FAILED", "WORKER_DIED",
                "ISOLATION_ERROR", "SNAPSHOT_INVALID", "ORIGIN_AUTH_REQUIRED"}) {
            assertTrue(reason + " before setPlayer must continue global resume", resume(false, "BLOCKED", reason));
        }
    }

    @Test
    public void noFailureAfterAPlayerResultCanRematchCurrentSources() throws Exception {
        for (String status : new String[]{"BLOCKED", "UNAVAILABLE"}) {
            for (String reason : new String[]{"PARSE_FAILED", "PLAYER_FAILED", "WORKER_DIED",
                    "SEGMENT_404", "DETAIL_FAILED", "EPISODE_MISSING", "CONFIG_MISSING"}) {
                assertFalse(reason + " after setPlayer must stay with the existing player", resume(true, status, reason));
            }
        }
    }

    @Test
    public void unavailableStillMeansExactlyFiveBusinessReasons() throws Exception {
        for (String reason : new String[]{"CONFIG_MISSING", "CONFIG_LOAD_FAILED", "SITE_MISSING",
                "DETAIL_FAILED", "EPISODE_MISSING"}) {
            assertTrue(resume(false, "UNAVAILABLE", reason));
        }
        for (String reason : new String[]{"PARSE_FAILED", "WORKER_DIED", "SEGMENT_404", "unknown", ""}) {
            assertFalse(resume(false, "UNAVAILABLE", reason));
        }
    }

    @Test
    public void successfulCancelledAndStaleRepliesNeverStartGlobalResume() throws Exception {
        for (String status : new String[]{"READY", "PLAY_READY", "CANCELLED", "STALE", "unknown", ""}) {
            assertFalse(resume(false, status, "CONFIG_MISSING"));
        }
        assertFalse(resume(false, null, null));
    }

    private static boolean resume(boolean resultDelivered, String status, String reason) throws Exception {
        Method policy;
        try {
            policy = OriginSessionContract.class.getMethod("shouldResumeGlobally", boolean.class, String.class, String.class);
        } catch (NoSuchMethodException missing) {
            fail("Missing pre-player-result global-resume policy; the old BLOCKED branch only shows an error");
            return false;
        }
        return (Boolean) policy.invoke(null, resultDelivered, status, reason);
    }
}
