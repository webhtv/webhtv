package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;

import org.junit.Test;

import java.io.IOException;

public class LiveManifestRetryPolicyTest {

    @Test
    public void emptyBodyAndParseFailuresAreTransient() {
        assertTrue(LiveManifestRetryPolicy.isTransientManifestFailure(new IOException()));
        assertTrue(LiveManifestRetryPolicy.isTransientManifestFailure(new IOException("empty body")));
        assertTrue(LiveManifestRetryPolicy.isTransientManifestFailure(new IOException("failed to parse manifest")));
        assertTrue(LiveManifestRetryPolicy.isTransientManifestFailure(
                new IOException("load failed", new IOException("unexpected eof"))));
    }

    @Test
    public void definitiveHttpFailuresStayFatal() {
        assertFalse(LiveManifestRetryPolicy.isTransientManifestFailure(new IOException("Response code: 404")));
        assertFalse(LiveManifestRetryPolicy.isTransientManifestFailure(new IOException("upstream reset")));
        assertFalse(LiveManifestRetryPolicy.isTransientManifestFailure(null));
    }

    @Test
    public void longerRetryWindowIsScopedToManifests() {
        LiveManifestRetryPolicy policy = new LiveManifestRetryPolicy();

        assertTrue(policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST) >= 8);
        // 点播分片不能跟着 manifest 一起延长重试，否则死链会晚十几秒才失败。
        assertTrue(policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA)
                < policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST));
    }
}
