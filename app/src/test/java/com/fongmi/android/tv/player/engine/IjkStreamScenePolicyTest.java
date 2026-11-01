package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IjkStreamScenePolicyTest {

    @Test
    public void hlsEndListIsAuthoritativeVod() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n", 100),
                120_000,
                Long.MAX_VALUE);

        assertTrue(decision.authoritative());
        assertTrue(decision.vod());
        assertFalse(decision.live());
        assertFalse(decision.dynamic());
        assertTrue(decision.seekable());
        assertEquals(IjkStreamScenePolicy.Reason.MANIFEST_VOD, decision.reason());
    }

    @Test
    public void regularHlsLiveUsesThreeTargetDurations() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n", 100),
                C.TIME_UNSET,
                200);

        assertTrue(decision.authoritative());
        assertTrue(decision.live());
        assertTrue(decision.dynamic());
        assertFalse(decision.vod());
        assertEquals(18_000, decision.targetOffsetMs());
    }

    @Test
    public void regularHlsLivePrefersDeclaredHoldBack() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:6\n"
                                + "#EXT-X-SERVER-CONTROL:HOLD-BACK=12\n"
                                + "#EXTINF:6,\na.ts\n",
                        100),
                C.TIME_UNSET,
                200);

        assertEquals(12_000, decision.targetOffsetMs());
    }

    @Test
    public void lowLatencyHlsPrefersPartHoldBack() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:2\n"
                                + "#EXT-X-SERVER-CONTROL:HOLD-BACK=6,PART-HOLD-BACK=1.2\n"
                                + "#EXT-X-PART-INF:PART-TARGET=0.333\n"
                                + "#EXT-X-PART:DURATION=0.333,URI=p.m4s\n",
                        100),
                C.TIME_UNSET,
                200);

        assertEquals(PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE,
                decision.streamKind());
        assertEquals(1_200, decision.targetOffsetMs());
        assertEquals(IjkStreamScenePolicy.Reason.MANIFEST_LOW_LATENCY_LIVE,
                decision.reason());
    }

    @Test
    public void lowLatencyHlsFallsBackToThreePartDurations() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:2\n"
                                + "#EXT-X-PART-INF:PART-TARGET=0.333\n"
                                + "#EXT-X-PART:DURATION=0.333,URI=p.m4s\n",
                        100),
                C.TIME_UNSET,
                200);

        assertEquals(999, decision.targetOffsetMs());
    }

    @Test
    public void segmentedUnknownNeverUsesDurationGuess() {
        PlaybackResourceClassifier.Classification hlsRequest =
                PlaybackResourceClassifier.classifyRequest(
                        "https://origin.invalid/live.m3u8", "hls", "hls");

        IjkStreamScenePolicy.Decision shortDuration =
                IjkStreamScenePolicy.resolve(hlsRequest, 10_000, 100);
        IjkStreamScenePolicy.Decision longDuration =
                IjkStreamScenePolicy.resolve(hlsRequest, 120_000, 100);

        for (IjkStreamScenePolicy.Decision decision
                : new IjkStreamScenePolicy.Decision[]{shortDuration, longDuration}) {
            assertTrue(decision.segmented());
            assertFalse(decision.authoritative());
            assertFalse(decision.live());
            assertFalse(decision.vod());
            assertFalse(decision.dynamic());
            assertEquals(PlaybackAutoContext.StreamKind.UNKNOWN,
                    decision.streamKind());
        }
        assertTrue(shortDuration.seekable());
        assertTrue(longDuration.seekable());
    }

    @Test
    public void hlsMasterDoesNotBecomeLiveFromUnknownDuration() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\na.m3u8\n", 100),
                C.TIME_UNSET,
                200);

        assertFalse(decision.live());
        assertFalse(decision.vod());
        assertFalse(decision.dynamic());
        assertEquals(IjkStreamScenePolicy.Reason.MANIFEST_NOT_MEDIA,
                decision.reason());
        assertFalse(IjkStreamScenePolicy.isLive(decision, 120_000));
        assertTrue(IjkStreamScenePolicy.isVod(decision, 120_000));
    }

    @Test
    public void dashStaticAndDynamicUseManifestFacts() {
        PlaybackResourceClassifier.Classification vod =
                PlaybackResourceClassifier.classifyDash(
                        "https://origin.invalid/vod.mpd", null,
                        "<MPD type='static'><Period/></MPD>", 100);
        PlaybackResourceClassifier.Classification live =
                PlaybackResourceClassifier.classifyDash(
                        "https://origin.invalid/live.mpd", null,
                        "<MPD type='dynamic'><Period/></MPD>", 100);

        assertTrue(IjkStreamScenePolicy.resolve(vod, 10_000, Long.MAX_VALUE).vod());
        IjkStreamScenePolicy.Decision liveDecision =
                IjkStreamScenePolicy.resolve(live, C.TIME_UNSET, 200);
        assertTrue(liveDecision.live());
        assertTrue(liveDecision.dynamic());
        assertEquals(C.TIME_UNSET, liveDecision.targetOffsetMs());
    }

    @Test
    public void staleDynamicManifestReturnsUnknownUntilRefreshed() {
        PlaybackResourceClassifier.Classification live =
                PlaybackResourceClassifier.classifyDash(
                        "https://origin.invalid/live.mpd", null,
                        "<MPD type='dynamic'><Period/></MPD>", 100);

        IjkStreamScenePolicy.Decision stale =
                IjkStreamScenePolicy.resolve(live, C.TIME_UNSET, 30_100);

        assertFalse(stale.authoritative());
        assertFalse(stale.live());
        assertFalse(stale.vod());
        assertEquals(IjkStreamScenePolicy.Reason.MANIFEST_STALE, stale.reason());
        assertFalse(IjkStreamScenePolicy.isLive(stale, 120_000));
        assertTrue(IjkStreamScenePolicy.isVod(stale, 120_000));
    }

    @Test
    public void nonSegmentedResourcesKeepLegacyDurationCompatibility() {
        PlaybackResourceClassifier.Classification progressive =
                PlaybackResourceClassifier.classifyRequest(
                        "https://origin.invalid/movie.mp4", "video/mp4", null);

        IjkStreamScenePolicy.Decision unknownDuration =
                IjkStreamScenePolicy.resolve(progressive, C.TIME_UNSET, 100);
        IjkStreamScenePolicy.Decision shortDuration =
                IjkStreamScenePolicy.resolve(progressive, 30_000, 100);
        IjkStreamScenePolicy.Decision exactBoundary =
                IjkStreamScenePolicy.resolve(progressive, 60_000, 100);
        IjkStreamScenePolicy.Decision longDuration =
                IjkStreamScenePolicy.resolve(progressive, 120_000, 100);

        assertFalse(unknownDuration.live());
        assertTrue(unknownDuration.vod());
        assertTrue(unknownDuration.dynamic());
        assertEquals(IjkStreamScenePolicy.Reason.LEGACY_DURATION_VOD, unknownDuration.reason());
        assertTrue(shortDuration.live());
        assertFalse(shortDuration.dynamic());
        assertFalse(exactBoundary.live());
        assertTrue(exactBoundary.vod());
        assertTrue(longDuration.vod());
        assertFalse(longDuration.live());
    }

    @Test
    public void authoritativeManifestBeatsTheDurationHeuristic() {
        IjkStreamScenePolicy.Decision vod = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n", 100),
                30_000,
                Long.MAX_VALUE);

        assertFalse(IjkStreamScenePolicy.isLive(vod, 30_000));
        assertTrue(IjkStreamScenePolicy.isVod(vod, 30_000));
    }

    @Test
    public void unknownDurationIsVodOnceTheManifestGivesNoLiveEvidence() {
        PlaybackResourceClassifier.Classification progressive =
                PlaybackResourceClassifier.classifyRequest(
                        "https://origin.invalid/movie.mp4", "video/mp4", null);
        IjkStreamScenePolicy.Decision decision =
                IjkStreamScenePolicy.resolve(progressive, C.TIME_UNSET, 100);

        assertFalse(IjkStreamScenePolicy.isLive(decision, C.TIME_UNSET));
        assertTrue(IjkStreamScenePolicy.isVod(decision, C.TIME_UNSET));
    }

    @Test
    public void liveManifestStillWinsWhileTheDurationIsUnknown() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n", 100),
                C.TIME_UNSET,
                200);

        assertTrue(IjkStreamScenePolicy.isLive(decision, C.TIME_UNSET));
        assertFalse(IjkStreamScenePolicy.isVod(decision, C.TIME_UNSET));
    }

    @Test
    public void timelineSnapshotContainsOnlySafeFactsAndRefreshIdentity() {
        IjkStreamScenePolicy.Decision first = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\na.ts\n", 100),
                C.TIME_UNSET,
                100);
        IjkStreamScenePolicy.Decision refresh = IjkStreamScenePolicy.resolve(
                hls("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\na.ts\n", 200),
                C.TIME_UNSET,
                200);

        assertNotNull(first.timelineSnapshot());
        assertEquals(100, first.timelineSnapshot().observedAtElapsedMs());
        assertNotEquals(first.timelineSnapshot(), refresh.timelineSnapshot());
        String safe = first.timelineSnapshot().toString();
        assertFalse(safe.contains("origin.invalid"));
        assertFalse(safe.contains("index.m3u8"));
    }

    @Test
    public void progressiveTimelineDoesNotInventManifestObject() {
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                PlaybackResourceClassifier.classifyRequest(
                        "https://origin.invalid/movie.mp4", "video/mp4", null),
                120_000,
                100);

        assertNull(decision.timelineSnapshot());
    }

    @Test
    public void thresholdDurationIsVodOnceItIsNotShorterThanTheLegacyBoundary() {
        long threshold = TimelineStreamKindPolicy.LEGACY_LIVE_DURATION_THRESHOLD_MS;
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(
                PlaybackResourceClassifier.classifyRequest(
                        "https://origin.invalid/movie.mp4", "video/mp4", null),
                threshold,
                100);

        assertFalse(decision.live());
        assertTrue(decision.vod());
        assertFalse(IjkStreamScenePolicy.isLive(decision, threshold));
        assertTrue(IjkStreamScenePolicy.isVod(decision, threshold));
    }

    @Test
    public void liveProtocolStaysLiveWithoutManifestOrDuration() {
        PlaybackResourceClassifier.Classification rtmp =
                PlaybackResourceClassifier.classifyRequest("rtmp://origin.invalid/live/stream", null, null);
        IjkStreamScenePolicy.Decision decision = IjkStreamScenePolicy.resolve(rtmp, C.TIME_UNSET);

        assertTrue(decision.live());
        assertFalse(decision.vod());
        assertEquals(IjkStreamScenePolicy.Reason.PROTOCOL_LIVE, decision.reason());
        assertTrue(IjkStreamScenePolicy.isLive(decision, C.TIME_UNSET));
        assertFalse(IjkStreamScenePolicy.isVod(decision, C.TIME_UNSET));
    }

    private static PlaybackResourceClassifier.Classification hls(
            String text,
            long observedAtMs) {
        return PlaybackResourceClassifier.classifyHls(
                "https://proxy.invalid/index.m3u8",
                "https://origin.invalid/index.m3u8",
                text,
                observedAtMs);
    }
}
