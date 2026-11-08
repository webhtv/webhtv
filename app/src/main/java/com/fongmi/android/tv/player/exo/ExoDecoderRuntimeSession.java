package com.fongmi.android.tv.player.exo;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;

import java.util.HashSet;
import java.util.Set;

/** Session isolation, failure attribution and transient quarantine for EXO decoder profiles. */
public final class ExoDecoderRuntimeSession {

    public static final int MAX_RUNTIME_FALLBACKS_PER_PLAYBACK = 1;
    public static final long STABLE_PLAYBACK_WINDOW_MS = 30_000L;
    private static final long MAX_HEALTHY_DROPPED_FRAMES = 23;
    /**
     * The health window is 30s, so 23 frames over that window is what
     * {@link #MAX_HEALTHY_DROPPED_FRAMES} has always meant. Keeping the same budget as a
     * rate lets a shorter or longer window be judged on equal terms: media3 settles its
     * dropped-frame accumulator whenever the renderer stops, so a bare count also carries
     * whatever a pause flushed out.
     */
    private static final double MAX_HEALTHY_DROPPED_FRAMES_PER_SECOND =
            MAX_HEALTHY_DROPPED_FRAMES * 1000d / STABLE_PLAYBACK_WINDOW_MS;

    private final ProfileAccess profiles;
    private final Set<ExoDecoderRuntimeKey.Key> failedThisPlayback;
    private final Set<ExoDecoderRuntimeKey.Key> quarantined;
    private final Set<ExoDecoderRuntimeKey.Key> loggedExclusions;
    private String playbackTraceId;
    private OutputConfig currentOutput;
    private long attemptStartedElapsedMs;
    private int runtimeFallbackCount;
    private boolean attemptFinished;
    private boolean attemptFailureRecorded;
    private boolean firstFrameRecorded;
    private boolean stableRecorded;
    private ExoDecoderRuntimeKey.Key currentFailureKey;
    private ExoDecoderRuntimeKey.Key pendingFallbackKey;

    ExoDecoderRuntimeSession(ProfileAccess profiles) {
        this.profiles = profiles;
        this.failedThisPlayback = new HashSet<>();
        this.quarantined = new HashSet<>();
        this.loggedExclusions = new HashSet<>();
        this.playbackTraceId = "";
        this.currentOutput = OutputConfig.unknown();
    }

    public synchronized void beginPlayback(String traceId) {
        String normalized = traceId == null ? "" : traceId.trim();
        if (!normalized.isEmpty() && normalized.equals(playbackTraceId)) return;
        playbackTraceId = normalized;
        failedThisPlayback.clear();
        quarantined.clear();
        loggedExclusions.clear();
        runtimeFallbackCount = 0;
        pendingFallbackKey = null;
        resetAttempt();
    }

    public synchronized void beginAttempt(OutputConfig output, long nowElapsedMs) {
        currentOutput = output == null ? OutputConfig.unknown() : output;
        attemptStartedElapsedMs = Math.max(0, nowElapsedMs);
        attemptFinished = false;
        attemptFailureRecorded = false;
        firstFrameRecorded = false;
        stableRecorded = false;
        currentFailureKey = null;
    }

    public synchronized boolean recordFatalFailure(
            Evidence evidence,
            int errorCode,
            long nowElapsedMs,
            long nowEpochMs) {
        ExoDecoderRuntimeProfileStore.FailureKind kind = failureKind(errorCode);
        if (kind == ExoDecoderRuntimeProfileStore.FailureKind.NONE || attemptFailureRecorded) {
            return false;
        }
        ExoDecoderRuntimeKey.Key key = key(evidence);
        if (key == null) return false;
        if (pendingFallbackKey != null) {
            profiles.recordFallback(
                    pendingFallbackKey,
                    ExoDecoderRuntimeProfileStore.FallbackResult.FAILURE,
                    nowEpochMs);
            pendingFallbackKey = null;
        }
        if (failedThisPlayback.add(key)) {
            profiles.recordFailure(key, kind, metrics(evidence, nowElapsedMs), nowEpochMs);
        }
        attemptFailureRecorded = true;
        attemptFinished = true;
        currentFailureKey = key;
        pendingFallbackKey = key;
        return true;
    }

    public synchronized boolean prepareRuntimeFallback() {
        if (currentFailureKey == null
                || runtimeFallbackCount >= MAX_RUNTIME_FALLBACKS_PER_PLAYBACK
                || quarantined.contains(currentFailureKey)) {
            return false;
        }
        quarantined.add(currentFailureKey);
        runtimeFallbackCount++;
        return true;
    }

    public synchronized void recordStable(
            Evidence evidence,
            long nowElapsedMs,
            long nowEpochMs) {
        if (stableRecorded || attemptFailureRecorded) return;
        ExoDecoderRuntimeKey.Key key = key(evidence);
        if (key == null) return;
        ExoDecoderRuntimeProfileStore.Metrics metrics = metrics(evidence, nowElapsedMs);
        if (metrics.observedDurationMs() < STABLE_PLAYBACK_WINDOW_MS) return;
        boolean healthy = evidence.recoverableCodecErrors() == 0
                && withinHealthyDropRate(evidence);
        if (healthy) profiles.recordStableSuccess(key, metrics, nowEpochMs);
        else profiles.recordObservation(key, metrics, nowEpochMs);
        if (healthy && pendingFallbackKey != null) {
            profiles.recordFallback(
                    pendingFallbackKey,
                    ExoDecoderRuntimeProfileStore.FallbackResult.SUCCESS,
                    nowEpochMs);
            pendingFallbackKey = null;
        }
        stableRecorded = true;
        attemptFinished = true;
    }

    public synchronized boolean recordFirstFrame(
            Evidence evidence,
            long nowEpochMs) {
        if (firstFrameRecorded || attemptFailureRecorded || attemptFinished) return false;
        ExoDecoderRuntimeKey.Key key = key(evidence);
        if (key == null) return false;
        profiles.recordFirstFrame(key, nowEpochMs);
        firstFrameRecorded = true;
        return true;
    }

    public synchronized void finishAttempt(
            Evidence evidence,
            long nowElapsedMs,
            long nowEpochMs) {
        if (attemptFinished || stableRecorded || attemptFailureRecorded) return;
        ExoDecoderRuntimeKey.Key key = key(evidence);
        if (key == null) {
            attemptFinished = true;
            return;
        }
        ExoDecoderRuntimeProfileStore.Metrics metrics = metrics(evidence, nowElapsedMs);
        if (metrics.droppedFrames() > 0 || metrics.recoverableCodecErrors() > 0) {
            profiles.recordObservation(key, metrics, nowEpochMs);
        }
        attemptFinished = true;
    }

    synchronized boolean shouldExclude(
            String decoderName,
            @Nullable Format format,
            boolean secure,
            OutputConfig output,
            long nowEpochMs) {
        ExoDecoderRuntimeKey.Key key = ExoDecoderRuntimeKey.from(
                profiles.environment(), decoderName, format, secure, output);
        if (key == null) return false;
        boolean transientOnly = quarantined.contains(key);
        boolean excluded = transientOnly || profiles.isBlacklisted(key, nowEpochMs);
        if (excluded && loggedExclusions.add(key)) profiles.logExclusion(key, transientOnly);
        return excluded;
    }

    synchronized int runtimeFallbackCount() {
        return runtimeFallbackCount;
    }

    synchronized boolean isQuarantined(ExoDecoderRuntimeKey.Key key) {
        return key != null && quarantined.contains(key);
    }

    @Nullable
    private ExoDecoderRuntimeKey.Key key(Evidence evidence) {
        if (evidence == null) return null;
        return ExoDecoderRuntimeKey.from(
                profiles.environment(),
                evidence.decoderName(),
                evidence.format(),
                evidence.secure(),
                currentOutput);
    }

    /**
     * Same budget as the old fixed count, measured per played second. Without a measured
     * played time a rate cannot be formed at all, so the historical fixed count still
     * applies: an unmeasured session must not silently become healthier than before.
     */
    private static boolean withinHealthyDropRate(Evidence evidence) {
        if (evidence.playTimeMs() <= 0) return evidence.droppedFrames() <= MAX_HEALTHY_DROPPED_FRAMES;
        double rate = evidence.droppedFrames() * 1000d / evidence.playTimeMs();
        return rate <= MAX_HEALTHY_DROPPED_FRAMES_PER_SECOND;
    }

    private ExoDecoderRuntimeProfileStore.Metrics metrics(
            Evidence evidence,
            long nowElapsedMs) {
        long observedDurationMs = attemptStartedElapsedMs <= 0 || nowElapsedMs < attemptStartedElapsedMs
                ? 0 : nowElapsedMs - attemptStartedElapsedMs;
        return new ExoDecoderRuntimeProfileStore.Metrics(
                evidence == null ? 0 : evidence.droppedFrames(),
                observedDurationMs,
                evidence == null ? 0 : evidence.recoverableCodecErrors());
    }

    private void resetAttempt() {
        currentOutput = OutputConfig.unknown();
        attemptStartedElapsedMs = 0;
        attemptFinished = true;
        attemptFailureRecorded = false;
        firstFrameRecorded = false;
        stableRecorded = false;
        currentFailureKey = null;
    }

    private static ExoDecoderRuntimeProfileStore.FailureKind failureKind(int errorCode) {
        return switch (errorCode) {
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                    ExoDecoderRuntimeProfileStore.FailureKind.INIT;
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                    ExoDecoderRuntimeProfileStore.FailureKind.QUERY;
            case PlaybackException.ERROR_CODE_DECODING_FAILED ->
                    ExoDecoderRuntimeProfileStore.FailureKind.DECODE;
            default -> ExoDecoderRuntimeProfileStore.FailureKind.NONE;
        };
    }

    public enum OutputTarget {
        UNKNOWN,
        SURFACE,
        TEXTURE
    }

    public record OutputConfig(OutputTarget target, boolean tunneling) {

        public OutputConfig {
            target = target == null ? OutputTarget.UNKNOWN : target;
        }

        static OutputConfig unknown() {
            return new OutputConfig(OutputTarget.UNKNOWN, false);
        }
    }

    public record Evidence(
            String decoderName,
            @Nullable Format format,
            boolean secure,
            long droppedFrames,
            int recoverableCodecErrors,
            long playTimeMs) {

        public Evidence {
            decoderName = decoderName == null ? "" : decoderName;
            droppedFrames = Math.max(0, droppedFrames);
            recoverableCodecErrors = Math.max(0, recoverableCodecErrors);
            playTimeMs = Math.max(0, playTimeMs);
        }

        /** Evidence whose played time was not measured; judged against the fixed count. */
        public Evidence(
                String decoderName,
                @Nullable Format format,
                boolean secure,
                long droppedFrames,
                int recoverableCodecErrors) {
            this(decoderName, format, secure, droppedFrames, recoverableCodecErrors, 0);
        }
    }

    interface ProfileAccess {

        ExoDecoderRuntimeKey.Environment environment();

        boolean isBlacklisted(ExoDecoderRuntimeKey.Key key, long nowEpochMs);

        ExoDecoderRuntimeProfileStore.Entry recordFailure(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.FailureKind failureKind,
                ExoDecoderRuntimeProfileStore.Metrics metrics,
                long nowEpochMs);

        ExoDecoderRuntimeProfileStore.Entry recordFirstFrame(
                ExoDecoderRuntimeKey.Key key,
                long nowEpochMs);

        ExoDecoderRuntimeProfileStore.Entry recordStableSuccess(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.Metrics metrics,
                long nowEpochMs);

        ExoDecoderRuntimeProfileStore.Entry recordObservation(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.Metrics metrics,
                long nowEpochMs);

        ExoDecoderRuntimeProfileStore.Entry recordFallback(
                ExoDecoderRuntimeKey.Key key,
                ExoDecoderRuntimeProfileStore.FallbackResult fallbackResult,
                long nowEpochMs);

        void logExclusion(ExoDecoderRuntimeKey.Key key, boolean transientOnly);
    }
}
