package com.fongmi.android.tv.player.exo;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;

import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.github.catvod.crawler.SpiderDebug;

final class ExoPlaybackDiagnostics {

    private ExoPlaybackDiagnostics() {
    }

    static void logLoadControl(int profile, ExoLoadControlPolicy.BufferDurations durations, ExoBufferBudget.Budget budget, int startBufferMs, int rebufferMs, int backBufferMs, boolean prioritizeTime, boolean dynamicTarget) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("exo-buffer", "loadControl profile=%s min=%d max=%d start=%d rebuffer=%d back=%d targetMode=%s requestedBytes=%d heapBudgetBytes=%d fallbackEffectiveBytes=%d heapLimitBytes=%d reserveBytes=%d availableAfterReserveBytes=%d memoryClassMb=%d largeMemoryClassMb=%d largeHeap=%s lowRam=%s prioritizeTime=%s",
                profileName(profile), durations.minBufferMs(), durations.maxBufferMs(), startBufferMs, rebufferMs, backBufferMs,
                dynamicTarget ? "dynamic" : "fixed",
                budget.requestedTargetBytes(), budget.heapBudgetBytes(), budget.effectiveTargetBytes(), budget.heapLimitBytes(), budget.reservedHeadroomBytes(), budget.availableAfterReserveBytes(),
                budget.memoryClassMb(), budget.largeMemoryClassMb(), budget.largeHeap(), budget.lowRamDevice(), prioritizeTime);
    }

    static void logAutoLoadControl(
            int profile,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            ExoBufferBudget.Budget budget,
            int backBufferMs) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("exo-buffer", "loadControl profile=%s targetMode=dynamic streamingMin=%d streamingMax=%d streamingStart=%d streamingRebufferMax=%d streamingPrioritizeTime=%s localMin=%d localMax=%d localStart=%d localRebuffer=%d localPrioritizeTime=%s back=%d requestedBytes=%d heapBudgetBytes=%d fallbackEffectiveBytes=%d guardBytes=%d",
                profileName(profile),
                configuration.streaming().minBufferMs(), configuration.streaming().maxBufferMs(),
                configuration.streamingStartBufferMs(), configuration.streamingRebufferMs(),
                configuration.streamingPrioritizeTime(),
                configuration.local().minBufferMs(), configuration.local().maxBufferMs(),
                configuration.localStartBufferMs(), configuration.localRebufferMs(),
                configuration.localPrioritizeTime(), backBufferMs,
                budget.requestedTargetBytes(), budget.heapBudgetBytes(), budget.effectiveTargetBytes(),
                ExoTargetBufferPolicy.GUARD_TARGET_BYTES);
    }

    static void logTargetDecision(ExoTargetBufferPolicy.Decision decision) {
        if (!SpiderDebug.isEnabled() || decision == null) return;
        ExoTargetBufferPolicy.MediaDemand media = decision.mediaDemand();
        SpiderDebug.log("exo-buffer", "target selectedBytes=%d mediaTierBytes=%d safeTierBytes=%d factor=%s unknownFallback=%s averageBps=%d averageSource=%s averageConfidence=%s burstBps=%d burstSource=%s burstConfidence=%s averageNeedBytes=%d burstNeedBytes=%d payloadNeedBytes=%d deviceBudgetBytes=%d heapBudgetBytes=%d javaHeadroomBudgetBytes=%d systemBudgetBytes=%d configuredCapBytes=%d guardBytes=%d reserveBytes=%d lowRam=%s snapshotUsable=%s pressureUsable=%s pressure=%s",
                decision.targetBytes(), decision.mediaTierBytes(), decision.safeTierBytes(), decision.limitingFactor().label(), decision.unknownMediaFallback().label(),
                media.averageBitsPerSecond(), media.averageSource().label(), media.averageConfidence().label(),
                media.burstBitsPerSecond(), media.burstSource().label(), media.burstConfidence().label(),
                decision.averageDemandBytes(), decision.burstDemandBytes(), decision.payloadDemandBytes(),
                decision.deviceBudgetBytes(), decision.heapBudgetBytes(), decision.javaHeadroomBudgetBytes(), decision.systemBudgetBytes(),
                decision.configuredCapBytes(), ExoTargetBufferPolicy.GUARD_TARGET_BYTES, decision.reserveBytes(), decision.lowRamDevice(),
                decision.memorySnapshotUsable(), decision.memoryPressureUsable(), decision.memoryPressure().label());
    }

    static void logLoadControlMode(ExoLoadControlModePolicy.Decision decision) {
        if (!SpiderDebug.isEnabled() || decision == null) return;
        ExoLoadControlModePolicy.TrackProfile tracks = decision.tracks();
        SpiderDebug.log("exo-buffer", "mode=%s reason=%s protocol=%s stream=%s prioritizeTime=%s appProxyVodFallback=%s adaptiveVideo=%s selectedVideoCandidates=%d availableVideoFormats=%d manifestVariants=%d bitrateBps=%d targetBytes=%d targetDurationMs=%d rescueBytes=%d hardCapacityBytes=%d hardProtection=%s memoryPressure=%s",
                decision.mode().label(), decision.reason().label(), decision.protocol().label(),
                decision.streamKind().label(), decision.mode().prioritizeTime(), decision.appProxyVodFallback(),
                tracks.adaptiveVideo(), tracks.selectedVideoCandidates(), tracks.availableVideoFormats(),
                decision.manifestVariantCount(), decision.bitrateBitsPerSecond(), decision.targetBytes(),
                decision.targetDurationMs(), decision.rescueBytes(), decision.hardCapacityBytes(),
                decision.hardProtectionAvailable(), decision.memoryPressure().label());
    }

    static void logMemoryPressureDecision(
            ExoMemoryPressurePolicy.Decision decision,
            int allocatorAllocatedBytes,
            int allocatorUnusedBytes) {
        if (!SpiderDebug.isEnabled() || decision == null) return;
        SpiderDebug.log("exo-buffer", "memoryMode=%s reason=%s pressure=%s recoveryClass=%s baselineBytes=%d safeBytes=%d effectiveBytes=%d normalSamples=%d cooldownRemainingMs=%d preloadPaused=%s backSuppressed=%s allocatorAllocatedBytes=%d allocatorUnusedBytes=%d",
                decision.mode().label(), decision.reason().label(),
                decision.observedPressure().label(), decision.recoveryClass().label(),
                decision.baselineTargetBytes(), decision.safeTargetBytes(),
                decision.effectiveTargetBytes(), decision.normalSamples(),
                decision.cooldownRemainingMs(android.os.SystemClock.elapsedRealtime()),
                decision.preloadPaused(), decision.backBufferSuppressed(),
                Math.max(0, allocatorAllocatedBytes), Math.max(0, allocatorUnusedBytes));
    }

    static void logPlaybackThreshold(
            ExoPlaybackThresholdCoordinator.Selection selection) {
        if (!SpiderDebug.isEnabled() || selection == null) return;
        ExoPlaybackThresholdPolicy.Decision policy = selection.policy();
        ExoPlaybackThresholdPolicy.TrendThresholds trend =
                policy.trendThresholds();
        SpiderDebug.log(
                "exo-buffer",
                "threshold episode=%s action=%s startMs=%d rebufferMs=%d protocol=%s stream=%s boundaryMs=%d risk=%s reason=%s loweringEligible=%s conservativePath=%s immediateCap=%s throughputRatioPermille=%d predictionErrorPermille=%d bufferSlopeMsps=%d timeToEmptyMs=%d trendMinWindowMs=%d trendWarningTteMs=%d trendCriticalTteMs=%d trendDrainSlopeMsps=%d",
                selection.episode().label(),
                selection.action().label(),
                selection.startBufferMs(),
                selection.rebufferMs(),
                policy.protocol().label(),
                policy.streamKind().label(),
                policy.boundaryMs(),
                policy.riskLevel().name().toLowerCase(java.util.Locale.US),
                policy.reason().label(),
                policy.loweringEligible(),
                policy.conservativePath(),
                policy.immediateDecrease(),
                policy.throughputRatioPermille(),
                policy.predictionErrorPermille(),
                policy.bufferSlopeMsPerSecond(),
                policy.timeToEmptyMs(),
                trend.minimumWindowMs(),
                trend.warningTimeToEmptyMs(),
                trend.criticalTimeToEmptyMs(),
                trend.drainSlopeMsPerSecond());
    }

    static void logDefaultLoadControl(int profile) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-buffer", "loadControl profile=%s mode=media3-default", profileName(profile));
    }

    /**
     * Records the frame-pacing values that reached the renderer factory.
     *
     * <p>A dropped-frame report is unattributable while the exported log carries no configuration
     * at all, because the same "掉帧" sentence then covers four different switch states. One line
     * per player build closes that gap; it says what was applied, not how playback looked.
     */
    static void logFrameScheduling(
            ExoFrameSchedulingExperimentPolicy.Decision decision,
            String codecQueueMode,
            boolean dynamicSchedulingEnabled,
            long lateDropThresholdUs) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("exo-frame", "renderer %s",
                ExoFrameSchedulingRendererSettings.describe(
                        decision, codecQueueMode, dynamicSchedulingEnabled,
                        lateDropThresholdUs));
    }

    static void logTrackFormats(@Nullable Format video, @Nullable Format audio, int effectiveCapacityBytes) {
        if (!SpiderDebug.isEnabled()) return;
        long videoBitrate = formatBitrate(video);
        long audioBitrate = formatBitrate(audio);
        long totalBitrate = safeAdd(videoBitrate, audioBitrate);
        SpiderDebug.log("exo-buffer", "tracks videoBitrate=%d videoSource=%s audioBitrate=%d audioSource=%s totalBitrate=%d effectiveCapacityBytes=%d estimatedCapacityDurationMs=%d",
                videoBitrate, bitrateSource(video), audioBitrate, bitrateSource(audio), totalBitrate, effectiveCapacityBytes, capacityDurationMs(effectiveCapacityBytes, totalBitrate));
    }

    static void logPreload(String format, Object... args) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-preload", format, args);
    }

    static int formatBitrate(@Nullable Format format) {
        if (format == null) return 0;
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.peakBitrate > 0) return format.peakBitrate;
        if (format.bitrate > 0) return format.bitrate;
        return 0;
    }

    static int trackConstraintBitrate(@Nullable Format format) {
        return format == null ? 0 : Math.max(0, format.bitrate);
    }

    static String trackConstraintBitrateSource(@Nullable Format format) {
        if (format == null) return "missing";
        if (format.peakBitrate > 0) return "peak";
        if (format.averageBitrate > 0) return "average";
        return "unknown";
    }

    static String bitrateSource(@Nullable Format format) {
        if (format == null) return "missing";
        if (format.averageBitrate > 0) return "average";
        if (format.peakBitrate > 0) return "peak";
        if (format.bitrate > 0) return "bitrate";
        return "unknown";
    }

    static long combinedBitrate(@Nullable Format video, @Nullable Format audio) {
        return safeAdd(formatBitrate(video), formatBitrate(audio));
    }

    static long capacityDurationMs(long capacityBytes, long bitrateBitsPerSecond) {
        if (capacityBytes <= 0 || bitrateBitsPerSecond <= 0) return 0;
        long bits = capacityBytes > Long.MAX_VALUE / 8L ? Long.MAX_VALUE : capacityBytes * 8L;
        if (bits > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE;
        return bits * 1_000L / bitrateBitsPerSecond;
    }

    static long estimateBytes(long bitrateBitsPerSecond, long durationMs) {
        if (bitrateBitsPerSecond <= 0 || durationMs <= 0) return 0;
        if (bitrateBitsPerSecond > Long.MAX_VALUE / durationMs) return Long.MAX_VALUE;
        return bitrateBitsPerSecond * durationMs / 8_000L;
    }

    private static long safeAdd(long first, long second) {
        if (first <= 0) return Math.max(0, second);
        if (second <= 0) return first;
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static String profileName(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_AUTO -> "auto";
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE -> "compatible";
            case PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> "lightweight";
            case PlaybackPerformanceSetting.PROFILE_CUSTOM -> "custom";
            default -> "recommended";
        };
    }
}
