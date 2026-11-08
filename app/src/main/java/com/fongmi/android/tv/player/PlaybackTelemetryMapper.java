package com.fongmi.android.tv.player;

import java.util.Locale;

/** Converts runtime observations to the shared fact model without inventing missing values. */
public final class PlaybackTelemetryMapper {

    static final long VOLATILE_FACT_TTL_MS = 15_000L;
    static final long MEDIA_FACT_TTL_MS = 30_000L;

    private PlaybackTelemetryMapper() {
    }

    public static PlaybackAutoContext.RuntimeFacts map(
            PlaybackTelemetry.RuntimeObservation observation,
            long sampledAtElapsedMs) {
        PlaybackTelemetry.RuntimeObservation source = observation == null
                ? PlaybackTelemetry.RuntimeObservation.unknown() : observation;
        long sampledAt = Math.max(0, sampledAtElapsedMs);
        return new PlaybackAutoContext.RuntimeFacts(
                untilReplaced(source.phase(), PlaybackAutoContext.PlaybackPhase.UNKNOWN, sampledAt),
                ttl(nonNegativeLong(source.bufferedDurationMs()), 0L, sampledAt, VOLATILE_FACT_TTL_MS),
                ttl(nonNegativeLong(source.bandwidthBitsPerSecond()), 0L, sampledAt, VOLATILE_FACT_TTL_MS),
                ttl(nonNegativeLong(source.mediaBitrateBitsPerSecond()), 0L, sampledAt, MEDIA_FACT_TTL_MS),
                ttl(positive(source.renderedFrameRate()), 0f, sampledAt, VOLATILE_FACT_TTL_MS),
                session(nonNegativeLong(source.droppedFrames()), 0L, sampledAt),
                session(nonNegativeInt(source.rebufferCount()), 0, sampledAt),
                session(nonNegativeLong(source.rebufferTotalMs()), 0L, sampledAt),
                session(nonNegativeLong(source.firstFrameElapsedMs()), 0L, sampledAt),
                ttl(nonNegativeLong(source.liveLagMs()), 0L, sampledAt, VOLATILE_FACT_TTL_MS),
                ttl(source.loading(), false, sampledAt, VOLATILE_FACT_TTL_MS),
                ttl(nonNegativeLong(source.positionMs()), 0L, sampledAt, VOLATILE_FACT_TTL_MS),
                untilReplaced(nonNegativeLong(source.durationMs()), 0L, sampledAt));
    }

    private static <T> PlaybackAutoContext.Fact<T> untilReplaced(
            PlaybackTelemetry.Metric<T> metric, T unknown, long sampledAtElapsedMs) {
        return metric == null || !metric.known()
                ? PlaybackAutoContext.Fact.unknown(unknown)
                : PlaybackAutoContext.Fact.untilReplaced(metric.value(), metric.source(), metric.confidence(), sampledAtElapsedMs);
    }

    private static <T> PlaybackAutoContext.Fact<T> session(
            PlaybackTelemetry.Metric<T> metric, T unknown, long sampledAtElapsedMs) {
        return metric == null || !metric.known()
                ? PlaybackAutoContext.Fact.unknown(unknown)
                : PlaybackAutoContext.Fact.forSession(metric.value(), metric.source(), metric.confidence(), sampledAtElapsedMs);
    }

    private static <T> PlaybackAutoContext.Fact<T> ttl(
            PlaybackTelemetry.Metric<T> metric, T unknown, long sampledAtElapsedMs, long validForMs) {
        return metric == null || !metric.known()
                ? PlaybackAutoContext.Fact.unknown(unknown)
                : PlaybackAutoContext.Fact.withTtl(metric.value(), metric.source(), metric.confidence(), sampledAtElapsedMs, validForMs);
    }

    private static PlaybackTelemetry.Metric<Long> nonNegativeLong(PlaybackTelemetry.Metric<Long> metric) {
        return metric == null || !metric.known() || metric.value() < 0 ? PlaybackTelemetry.Metric.unknown() : metric;
    }

    private static PlaybackTelemetry.Metric<Integer> nonNegativeInt(PlaybackTelemetry.Metric<Integer> metric) {
        return metric == null || !metric.known() || metric.value() < 0 ? PlaybackTelemetry.Metric.unknown() : metric;
    }

    private static PlaybackTelemetry.Metric<Float> positive(PlaybackTelemetry.Metric<Float> metric) {
        return metric == null || !metric.known() || metric.value() <= 0 || !Float.isFinite(metric.value())
                ? PlaybackTelemetry.Metric.unknown() : metric;
    }

    static String factValue(PlaybackAutoContext.Fact<?> fact) {
        return fact != null && fact.hasValue() ? String.valueOf(fact.value()) : "unknown";
    }

    static String factWithEvidence(PlaybackAutoContext.Fact<?> fact) {
        if (fact == null || !fact.hasValue()) return "unknown";
        return factValue(fact) + ":" + fact.source().label() + ":" + fact.confidence().label();
    }

    static String runtimeLogSummary(PlaybackAutoContext.RuntimeFacts runtime) {
        if (runtime == null) return "phase=unknown";
        return "phase=" + factWithEvidence(runtime.phase())
                + " loading=" + factWithEvidence(runtime.loading())
                + " positionMs=" + factWithEvidence(runtime.positionMs())
                + " durationMs=" + factWithEvidence(runtime.durationMs())
                + " bufferedMs=" + factWithEvidence(runtime.bufferedDurationMs())
                + " bandwidthBps=" + factWithEvidence(runtime.bandwidthBitsPerSecond())
                + " mediaBps=" + factWithEvidence(runtime.mediaBitrateBitsPerSecond())
                + " renderedFps=" + safeFloat(runtime.renderedFrameRate())
                // This number is the median presentation-timestamp interval, i.e. the source
                // cadence. It is not a rendered-throughput measurement: a 4K60 stream that drops a
                // tenth of its frames still reports ~58.8 here. Naming the scope in the line stops
                // the next reader from treating it as "playback was smooth".
                + " renderedFpsScope=pts-cadence-estimate;not-throughput"
                + " dropped=" + factWithEvidence(runtime.droppedFrames())
                + " rebufferCount=" + factWithEvidence(runtime.rebufferCount())
                + " rebufferTotalMs=" + factWithEvidence(runtime.rebufferTotalMs())
                + " firstFrameMs=" + factWithEvidence(runtime.firstFrameElapsedMs())
                + " liveLagMs=" + factWithEvidence(runtime.liveLagMs());
    }

    private static String safeFloat(PlaybackAutoContext.Fact<Float> fact) {
        if (fact == null || !fact.hasValue() || fact.value() == null || !Float.isFinite(fact.value())) return "unknown";
        return String.format(Locale.US, "%.3f:%s:%s", fact.value(), fact.source().label(), fact.confidence().label());
    }
}
