package com.fongmi.android.tv.playback.origin;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OriginFallbackPolicyTest {

    @Test
    public void onlyFiveCompletedBusinessFailuresAllowCurrentSourceMatching() throws Exception {
        for (String reason : new String[]{"CONFIG_MISSING", "CONFIG_LOAD_FAILED", "SITE_MISSING",
                "DETAIL_FAILED", "EPISODE_MISSING"}) {
            assertTrue(reason + " must allow the existing current-source matcher", shouldFallback(reason));
        }
    }

    @Test
    public void infrastructureAuthenticationAndPlaybackErrorsNeverMasqueradeAsUnavailable() throws Exception {
        for (String reason : new String[]{"ISOLATION_ERROR", "ORIGIN_AUTH_REQUIRED",
                "PORT_OWNERSHIP_AMBIGUOUS", "SNAPSHOT_INVALID", "WORKER_DIED",
                "PARSE_FAILED", "PLAYER_FAILED", "TIMEOUT", "BINDER_DIED", "HTTP_401",
                "HTTP_404", "SEGMENT_404", "UNSUPPORTED_RUNTIME", "WEBVIEW_UNAVAILABLE",
                "NODE_PORT_CONFLICT", "ERROR", "UNAVAILABLE"}) {
            assertFalse(reason + " must be surfaced, not rematched", shouldFallback(reason));
        }
    }

    @Test
    public void cancellationLateRepliesAndSuccessfulPlaybackNeverStartFallback() throws Exception {
        for (String reason : new String[]{"CANCELLED", "STALE", "READY", "PLAY_READY",
                "PLAY_STARTED", "CLOSED", "ACTIVITY_DESTROYED", "GENERATION_MISMATCH",
                "LEASE_MISMATCH", "CONFIG_REVISION_CHANGED", "RESUME_VERSION_CHANGED"}) {
            assertFalse(reason + " must not start current-source matching", shouldFallback(reason));
        }
    }

    @Test
    public void missingUnknownOrMalformedReasonFailsClosed() throws Exception {
        assertFalse(shouldFallback(null));
        for (String reason : new String[]{"", "unknown", "config_missing", "Config_Missing",
                " CONFIG_MISSING", "CONFIG_MISSING ", "DETAIL_FAILED\n", "CONFIG_MISSING|WORKER_DIED",
                "NOT_CONFIG_MISSING", "SITE_MISSING_EXTRA"}) {
            assertFalse("unrecognised reason must fail closed: " + reason, shouldFallback(reason));
        }
    }

    private static boolean shouldFallback(String reason) throws Exception {
        Class<?> contract;
        try {
            contract = Class.forName("com.fongmi.android.tv.playback.origin.OriginSessionContract");
        } catch (ClassNotFoundException missing) {
            fail("Stage 1 implementation missing: OriginSessionContract.shouldFallback(String); "
                    + "this is a runnable red baseline, not a skipped test");
            return false;
        }
        Method policy = contract.getMethod("shouldFallback", String.class);
        try {
            return (Boolean) policy.invoke(null, reason);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw failure;
        }
    }
}
