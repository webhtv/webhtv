package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExoDecoderRuntimeSessionTest {

    private static final long ELAPSED = 10_000L;
    private static final long EPOCH = 1_800_000_000_000L;
    private static final ExoDecoderRuntimeSession.OutputConfig SURFACE =
            new ExoDecoderRuntimeSession.OutputConfig(
                    ExoDecoderRuntimeSession.OutputTarget.SURFACE, false);

    @Test
    public void unknownCapabilityIsPassedThroughWithoutPositiveProbing() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);

        assertFalse(session.shouldExclude(
                "c2.vendor.hevc.decoder", format(3840, 2160), false, SURFACE, EPOCH));
        assertEquals(0, profiles.failureKeys.size());
        assertEquals(0, profiles.successKeys.size());
        assertEquals(0, profiles.observationKeys.size());
    }

    @Test
    public void persistentBlacklistExcludesOnlyExactCombination() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeKey.Key key = profiles.key(
                "c2.vendor.hevc.decoder", format(3840, 2160), false, SURFACE);
        profiles.blacklisted.add(key);

        assertTrue(session.shouldExclude(
                "c2.vendor.hevc.decoder", format(3840, 2160), false, SURFACE, EPOCH));
        assertFalse(session.shouldExclude(
                "c2.vendor.hevc.decoder", format(1920, 1080), false, SURFACE, EPOCH));
        assertFalse(session.shouldExclude(
                "c2.vendor.other.decoder", format(3840, 2160), false, SURFACE, EPOCH));
    }

    @Test
    public void oneFailureQuarantinesOnlyCurrentPlaybackCombination() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence = evidence("c2.vendor.hevc.decoder", format(3840, 2160));

        assertTrue(session.recordFatalFailure(
                evidence,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                ELAPSED + 1_000,
                EPOCH));
        assertTrue(session.prepareRuntimeFallback());

        assertTrue(session.shouldExclude(
                evidence.decoderName(), evidence.format(), false, SURFACE, EPOCH));
        assertFalse(session.shouldExclude(
                evidence.decoderName(), format(1920, 1080), false, SURFACE, EPOCH));
        assertEquals(1, profiles.failureKeys.size());
    }

    @Test
    public void duplicateFatalCallbackDoesNotDoubleCount() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence = evidence("decoder", format(3840, 2160));

        assertTrue(session.recordFatalFailure(evidence,
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 1, EPOCH));
        assertFalse(session.recordFatalFailure(evidence,
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 2, EPOCH + 1));
        assertEquals(1, profiles.failureKeys.size());
    }

    @Test
    public void resourceReclaimDoesNotPolluteDecoderRuntimeProfiles() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);

        assertFalse(session.recordFatalFailure(
                evidence("decoder", format(3840, 2160)),
                PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
                ELAPSED + 1,
                EPOCH));
        assertFalse(session.prepareRuntimeFallback());
        assertEquals(0, session.runtimeFallbackCount());
        assertEquals(0, profiles.failureKeys.size());
        assertEquals(0, profiles.fallbackResults.size());
    }

    @Test
    public void firstFrameIsRecordedOnceWithoutMarkingStable() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence =
                evidence("decoder", format(3840, 2160));

        assertTrue(session.recordFirstFrame(evidence, EPOCH));
        assertFalse(session.recordFirstFrame(evidence, EPOCH + 1));

        assertEquals(1, profiles.firstFrameKeys.size());
        assertEquals(0, profiles.successKeys.size());
        assertEquals(0, profiles.failureKeys.size());
    }

    @Test
    public void sameKeyFailsOnlyOncePerPlaybackAcrossAttempts() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence = evidence("decoder", format(3840, 2160));
        session.recordFatalFailure(evidence,
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 1, EPOCH);
        session.beginAttempt(SURFACE, ELAPSED + 2);

        assertTrue(session.recordFatalFailure(evidence,
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 3, EPOCH + 1));
        assertEquals(1, profiles.failureKeys.size());
        assertEquals(List.of(ExoDecoderRuntimeProfileStore.FallbackResult.FAILURE),
                profiles.fallbackResults);
    }

    @Test
    public void runtimeFallbackIsLimitedToOnePerPlayback() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        session.recordFatalFailure(evidence("decoder-a", format(3840, 2160)),
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 1, EPOCH);

        assertTrue(session.prepareRuntimeFallback());
        session.beginAttempt(SURFACE, ELAPSED + 2);
        session.recordFatalFailure(evidence("decoder-b", format(3840, 2160)),
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 3, EPOCH + 1);
        assertFalse(session.prepareRuntimeFallback());
        assertEquals(1, session.runtimeFallbackCount());
    }

    @Test
    public void earlyStableCallbackDoesNotEndObservationWindow() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence = evidence("decoder", format(3840, 2160));

        session.recordStable(evidence,
                ELAPSED + ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS - 1,
                EPOCH);
        assertEquals(0, profiles.successKeys.size());
        assertEquals(0, profiles.observationKeys.size());

        session.recordStable(evidence,
                ELAPSED + ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS,
                EPOCH + 1);
        assertEquals(1, profiles.successKeys.size());
    }

    @Test
    public void unhealthyWindowRecordsObservationWithoutBlacklist() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence =
                new ExoDecoderRuntimeSession.Evidence(
                        "decoder", format(3840, 2160), false, 24, 1,
                        ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS);

        session.recordStable(
                evidence,
                ELAPSED + ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS,
                EPOCH);

        assertEquals(0, profiles.successKeys.size());
        assertEquals(1, profiles.observationKeys.size());
        assertEquals(0, profiles.failureKeys.size());
    }

    @Test
    public void slowDropsOverALongWindowStayHealthy() {
        // 40 drops is well past the old fixed count of 23, but spread over two minutes it is
        // 0.33/s, below the 0.77/s the 23-frames-per-30s budget has always meant. Reading the
        // rate instead of the raw total is what keeps a long playback from being condemned for
        // the tail media3 settles at every pause.
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence =
                new ExoDecoderRuntimeSession.Evidence(
                        "decoder", format(3840, 2160), false, 40, 0, 120_000L);

        session.recordStable(
                evidence,
                ELAPSED + ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS,
                EPOCH);

        assertEquals(1, profiles.successKeys.size());
        assertEquals(0, profiles.observationKeys.size());
    }

    @Test
    public void shortWindowWithTheSameTotalIsUnhealthy() {
        // Identical dropped-frame total in only 10 played seconds is 4/s: the rate holds the
        // line the fixed count drew, instead of the window length deciding the verdict.
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence =
                new ExoDecoderRuntimeSession.Evidence(
                        "decoder", format(3840, 2160), false, 40, 0, 10_000L);

        session.recordStable(
                evidence,
                ELAPSED + ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS,
                EPOCH);

        assertEquals(0, profiles.successKeys.size());
        assertEquals(1, profiles.observationKeys.size());
    }

    @Test
    public void unmeasuredPlayTimeFallsBackToTheFixedCount() {
        // Played time is only measured while the analysis listener sees isPlaying; if that
        // never happened the old fixed count still applies, so an unmeasured session cannot
        // become healthier than it was before.
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence =
                new ExoDecoderRuntimeSession.Evidence(
                        "decoder", format(3840, 2160), false, 40, 0);

        session.recordStable(
                evidence,
                ELAPSED + ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS,
                EPOCH);

        assertEquals(0, profiles.successKeys.size());
        assertEquals(1, profiles.observationKeys.size());
    }

    @Test
    public void healthyFallbackRecordsSuccessForFailedKey() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        session.recordFatalFailure(evidence("decoder-a", format(3840, 2160)),
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 1, EPOCH);
        session.prepareRuntimeFallback();
        session.beginAttempt(SURFACE, ELAPSED + 2);

        session.recordStable(evidence("decoder-b", format(3840, 2160)),
                ELAPSED + 2 + ExoDecoderRuntimeSession.STABLE_PLAYBACK_WINDOW_MS,
                EPOCH + 1);

        assertEquals(List.of(ExoDecoderRuntimeProfileStore.FallbackResult.SUCCESS),
                profiles.fallbackResults);
        assertEquals(1, profiles.successKeys.size());
    }

    @Test
    public void playbackChangeClearsTransientQuarantineAndRetryBudget() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence = evidence("decoder", format(3840, 2160));
        session.recordFatalFailure(evidence,
                PlaybackException.ERROR_CODE_DECODING_FAILED, ELAPSED + 1, EPOCH);
        session.prepareRuntimeFallback();
        assertTrue(session.shouldExclude(
                evidence.decoderName(), evidence.format(), false, SURFACE, EPOCH));

        session.beginPlayback("trace-b");
        session.beginAttempt(SURFACE, ELAPSED + 2);

        assertFalse(session.shouldExclude(
                evidence.decoderName(), evidence.format(), false, SURFACE, EPOCH));
        assertEquals(0, session.runtimeFallbackCount());
    }

    @Test
    public void finishPersistsDropsAndRecoverableErrorsWithoutFailure() {
        FakeProfiles profiles = new FakeProfiles();
        ExoDecoderRuntimeSession session = session(profiles);
        ExoDecoderRuntimeSession.Evidence evidence = new ExoDecoderRuntimeSession.Evidence(
                "decoder", format(3840, 2160), false, 24, 2);

        session.finishAttempt(evidence, ELAPSED + 5_000, EPOCH);

        assertEquals(1, profiles.observationKeys.size());
        assertEquals(0, profiles.failureKeys.size());
        assertEquals(24, profiles.lastMetrics.droppedFrames());
        assertEquals(2, profiles.lastMetrics.recoverableCodecErrors());
    }

    private static ExoDecoderRuntimeSession session(FakeProfiles profiles) {
        ExoDecoderRuntimeSession session = new ExoDecoderRuntimeSession(profiles);
        session.beginPlayback("trace-a");
        session.beginAttempt(SURFACE, ELAPSED);
        return session;
    }

    private static ExoDecoderRuntimeSession.Evidence evidence(
            String decoder,
            Format format) {
        return new ExoDecoderRuntimeSession.Evidence(decoder, format, false, 0, 0);
    }

    private static Format format(int width, int height) {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs("hvc1.2.4.L153.B0")
                .setWidth(width)
                .setHeight(height)
                .setFrameRate(60f)
                .build();
    }

    private static final class FakeProfiles
            implements ExoDecoderRuntimeSession.ProfileAccess {

        private final ExoDecoderRuntimeKey.Environment environment =
                ExoDecoderRuntimeKey.environment("fp", 1, "1", "media");
        private final Set<ExoDecoderRuntimeKey.Key> blacklisted = new HashSet<>();
        private final List<ExoDecoderRuntimeKey.Key> failureKeys = new ArrayList<>();
        private final List<ExoDecoderRuntimeKey.Key> firstFrameKeys = new ArrayList<>();
        private final List<ExoDecoderRuntimeKey.Key> successKeys = new ArrayList<>();
        private final List<ExoDecoderRuntimeKey.Key> observationKeys = new ArrayList<>();
        private final List<ExoDecoderRuntimeProfileStore.FallbackResult> fallbackResults = new ArrayList<>();
        private ExoDecoderRuntimeProfileStore.Metrics lastMetrics =
                ExoDecoderRuntimeProfileStore.Metrics.EMPTY;

        @Override
        public ExoDecoderRuntimeKey.Environment environment() {
            return environment;
        }

        @Override
        public boolean isBlacklisted(ExoDecoderRuntimeKey.Key key, long nowEpochMs) {
            return blacklisted.contains(key);
        }

        @Override
        public ExoDecoderRuntimeProfileStore.Entry recordFailure(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.FailureKind failureKind,
                ExoDecoderRuntimeProfileStore.Metrics metrics,
                long nowEpochMs) {
            failureKeys.add(key);
            lastMetrics = metrics;
            return null;
        }

        @Override
        public ExoDecoderRuntimeProfileStore.Entry recordStableSuccess(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.Metrics metrics,
                long nowEpochMs) {
            successKeys.add(key);
            lastMetrics = metrics;
            return null;
        }

        @Override
        public ExoDecoderRuntimeProfileStore.Entry recordFirstFrame(
                ExoDecoderRuntimeKey.Key key,
                long nowEpochMs) {
            firstFrameKeys.add(key);
            return null;
        }

        @Override
        public ExoDecoderRuntimeProfileStore.Entry recordObservation(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.Metrics metrics,
                long nowEpochMs) {
            observationKeys.add(key);
            lastMetrics = metrics;
            return null;
        }

        @Override
        public ExoDecoderRuntimeProfileStore.Entry recordFallback(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.FallbackResult fallbackResult,
                long nowEpochMs) {
            fallbackResults.add(fallbackResult);
            return null;
        }

        @Override
        public void logExclusion(ExoDecoderRuntimeKey.Key key, boolean transientOnly) {
        }

        private ExoDecoderRuntimeKey.Key key(
                String decoder,
                Format format,
                boolean secure,
                ExoDecoderRuntimeSession.OutputConfig output) {
            ExoDecoderRuntimeKey.Key key = ExoDecoderRuntimeKey.from(
                    environment, decoder, format, secure, output);
            assertNotNull(key);
            return key;
        }
    }
}
