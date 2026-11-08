package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;

/** Applies one immutable frame scheduling decision to every MediaCodec renderer path. */
final class ExoFrameSchedulingRendererSettings {

    private final long earlySchedulingThresholdUs;
    private final boolean durationToProgressEnabled;

    private ExoFrameSchedulingRendererSettings(
            long earlySchedulingThresholdUs,
            boolean durationToProgressEnabled) {
        this.earlySchedulingThresholdUs = earlySchedulingThresholdUs;
        this.durationToProgressEnabled = durationToProgressEnabled;
    }

    static ExoFrameSchedulingRendererSettings from(
            ExoFrameSchedulingExperimentPolicy.Decision decision) {
        ExoFrameSchedulingExperimentPolicy.RendererSettings settings =
                decision == null
                        ? new ExoFrameSchedulingExperimentPolicy.RendererSettings(
                        ExoFrameSchedulingExperimentPolicy
                                .BASELINE_EARLY_SCHEDULING_THRESHOLD_US,
                        false)
                        : decision.rendererSettings();
        return new ExoFrameSchedulingRendererSettings(
                settings.earlySchedulingThresholdUs(),
                settings.durationToProgressEnabled());
    }

    DefaultRenderersFactory apply(DefaultRenderersFactory factory) {
        factory.setVideoRendererEarlySchedulingThresholdUs(
                earlySchedulingThresholdUs);
        factory.setEnableMediaCodecVideoRendererDurationToProgressUs(
                durationToProgressEnabled);
        return factory;
    }

    MediaCodecVideoRenderer.Builder apply(
            MediaCodecVideoRenderer.Builder builder) {
        return builder
                .setEarlySchedulingThresholdUs(earlySchedulingThresholdUs)
                .setEnableDurationToProgressUs(durationToProgressEnabled);
    }

    long earlySchedulingThresholdUs() {
        return earlySchedulingThresholdUs;
    }

    boolean durationToProgressEnabled() {
        return durationToProgressEnabled;
    }

    /**
     * One greppable line naming the frame-pacing values that actually reached the renderer factory.
     *
     * <p>Exported diagnostics carried no {@code perf_*} values before this, so a dropped-frame
     * report could not be attributed to a configuration without asking the user to read the
     * settings UI back. Joining these values into the player-build log turns "unknown" into an
     * observation.
     *
     * <p>It proves only that the values were applied, never that playback looked smooth.
     */
    static String describe(
            ExoFrameSchedulingExperimentPolicy.Decision decision,
            String codecQueueMode,
            boolean dynamicSchedulingEnabled,
            long lateDropThresholdUs) {
        ExoFrameSchedulingExperimentPolicy.Decision safeDecision = decision == null
                ? ExoFrameSchedulingExperimentPolicy.stableDecision(false, false, false)
                : decision;
        ExoFrameSchedulingExperimentPolicy.RendererSettings renderer =
                safeDecision.rendererSettings();
        return "dynamicScheduling=" + dynamicSchedulingEnabled
                + " codecQueue=" + codecQueueMode
                + " decision=" + safeDecision.reason().label()
                + " decisionUnit=" + safeDecision.sessionUnitId()
                + " durationToProgress=" + renderer.durationToProgressEnabled()
                + " earlySchedulingUs=" + renderer.earlySchedulingThresholdUs()
                + " lateDropInputUs=" + (lateDropThresholdUs == C.TIME_UNSET
                ? "unset" : String.valueOf(lateDropThresholdUs))
                + " metricScope=settings-applied-only; not a smoothness measurement";
    }
}
