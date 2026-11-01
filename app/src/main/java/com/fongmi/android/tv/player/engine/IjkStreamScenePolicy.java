package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;

/** Pure, conservative mapping from observed resource facts to IJK timeline semantics. */
public final class IjkStreamScenePolicy {

    private IjkStreamScenePolicy() {
    }

    public static Decision resolve(
            PlaybackResourceClassifier.Classification classification,
            long durationMs) {
        long observedAt = classification == null ? -1 : classification.observedAtElapsedMs();
        return resolve(classification, durationMs, Math.max(0, observedAt));
    }

    public static Decision resolve(
            PlaybackResourceClassifier.Classification classification,
            long durationMs,
            long nowElapsedMs) {
        PlaybackAutoContext.Protocol protocol = classification == null
                ? PlaybackAutoContext.Protocol.UNKNOWN : classification.protocol();
        long duration = durationMs > 0 ? durationMs : C.TIME_UNSET;
        boolean seekable = duration > 0;
        boolean segmented = protocol == PlaybackAutoContext.Protocol.HLS
                || protocol == PlaybackAutoContext.Protocol.DASH;
        TimelineSnapshot timeline = segmented
                ? TimelineSnapshot.from(classification) : null;

        if (segmented) {
            Reason rejection = manifestRejection(classification, protocol, nowElapsedMs);
            if (rejection != null) {
                return new Decision(protocol, PlaybackAutoContext.StreamKind.UNKNOWN,
                        true, false, false, false, false, seekable,
                        C.TIME_UNSET, timeline, rejection);
            }
            PlaybackAutoContext.StreamKind stream = classification.streamKind();
            if (stream == PlaybackAutoContext.StreamKind.VOD) {
                return new Decision(protocol, stream, true, true,
                        false, true, false, seekable, C.TIME_UNSET,
                        timeline, Reason.MANIFEST_VOD);
            }
            if (stream == PlaybackAutoContext.StreamKind.LIVE
                    || stream == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE) {
                return new Decision(protocol, stream, true, true,
                        true, false, true, seekable,
                        liveTargetOffsetMs(stream, classification.manifest()),
                        timeline,
                        stream == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE
                                ? Reason.MANIFEST_LOW_LATENCY_LIVE
                                : Reason.MANIFEST_LIVE);
            }
            return new Decision(protocol, PlaybackAutoContext.StreamKind.UNKNOWN,
                    true, false, false, false, false, seekable,
                    C.TIME_UNSET, timeline, Reason.MANIFEST_UNKNOWN);
        }

        // Unknown duration is negative (C.TIME_UNSET), so the legacy "< 1 minute is live"
        // rule turned every not-yet-reported VOD into a live stream. Unknown is not live
        // evidence: without live-manifest evidence, an unknown duration is treated as VOD.
        // RTSP/RTMP are the one exception, because the resource classifier already reports
        // them as live protocol endpoints (PlaybackResourceClassifier protocolFromRequest).
        // LOW_LATENCY_LIVE is deliberately absent: it needs a parsed manifest, so it is segmented.
        boolean protocolLive = classification != null
                && classification.streamKind() == PlaybackAutoContext.StreamKind.LIVE;
        boolean unknown = duration == C.TIME_UNSET;
        boolean live = protocolLive
                || (!unknown && duration < TimelineStreamKindPolicy.LEGACY_LIVE_DURATION_THRESHOLD_MS);
        boolean vod = !live
                && (unknown || duration >= TimelineStreamKindPolicy.LEGACY_LIVE_DURATION_THRESHOLD_MS);
        Reason reason = protocolLive ? Reason.PROTOCOL_LIVE
                : live ? Reason.LEGACY_DURATION_LIVE
                : Reason.LEGACY_DURATION_VOD;
        return new Decision(protocol,
                live ? PlaybackAutoContext.StreamKind.LIVE
                        : vod ? PlaybackAutoContext.StreamKind.VOD
                        // Unreachable on the non-segmented path: vod is the complement of live.
                        : PlaybackAutoContext.StreamKind.UNKNOWN,
                false, false, live, vod,
                // Non-segmented resources have no timeline to follow; only an unknown
                // duration makes the scene dynamic.
                unknown,
                seekable, C.TIME_UNSET, null, reason);
    }

    /**
     * Manifest evidence decides when it is decisive. A rejected or stale manifest is no
     * evidence at all, so those decisions fall through to the duration heuristic, where an
     * unknown duration means VOD. Live and VOD stay complementary on purpose: the
     * "neither" state silently disabled subtitles, speed and resume everywhere.
     */
    public static boolean isLive(Decision decision, long durationMs) {
        if (decision != null && decision.authoritative()) {
            if (decision.live()) return true;
            if (decision.vod()) return false;
        }
        return TimelineStreamKindPolicy.isLive(durationMs, decision != null && decision.live());
    }

    public static boolean isVod(Decision decision, long durationMs) {
        return !isLive(decision, durationMs);
    }

    private static Reason manifestRejection(
            PlaybackResourceClassifier.Classification classification,
            PlaybackAutoContext.Protocol protocol,
            long nowElapsedMs) {
        if (classification == null || !classification.actualManifest()) {
            return Reason.MANIFEST_UNKNOWN;
        }
        PlaybackAutoContext.ManifestKind kind = classification.manifest().kind();
        boolean compatible = protocol == PlaybackAutoContext.Protocol.HLS
                ? kind == PlaybackAutoContext.ManifestKind.HLS_MEDIA
                : kind == PlaybackAutoContext.ManifestKind.DASH_STATIC
                || kind == PlaybackAutoContext.ManifestKind.DASH_DYNAMIC;
        if (!compatible) return Reason.MANIFEST_NOT_MEDIA;
        PlaybackAutoContext.StreamKind stream = classification.streamKind();
        if (stream == PlaybackAutoContext.StreamKind.UNKNOWN) {
            return Reason.MANIFEST_UNKNOWN;
        }
        if ((stream == PlaybackAutoContext.StreamKind.LIVE
                || stream == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE)
                && isStale(classification, nowElapsedMs)) {
            return Reason.MANIFEST_STALE;
        }
        return null;
    }

    private static boolean isStale(
            PlaybackResourceClassifier.Classification classification,
            long nowElapsedMs) {
        long observedAt = classification.observedAtElapsedMs();
        if (observedAt < 0) return false;
        long now = Math.max(0, nowElapsedMs);
        if (now < observedAt) return true;
        return now - observedAt >= PlaybackResourceClassifier.manifestTtlMs(
                classification.manifest());
    }

    private static long liveTargetOffsetMs(
            PlaybackAutoContext.StreamKind stream,
            PlaybackAutoContext.ManifestFacts manifest) {
        if (manifest == null) return C.TIME_UNSET;
        Long holdBack = positive(manifest.holdBackMs());
        if (holdBack != null) return holdBack;
        if (stream == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE) {
            Long part = positive(manifest.partDurationMs());
            if (part != null) return multiplySaturated(part, 3);
        }
        Long target = positive(manifest.targetDurationMs());
        return target == null ? C.TIME_UNSET : multiplySaturated(target, 3);
    }

    private static Long positive(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private static long multiplySaturated(long value, long multiplier) {
        if (value <= 0 || multiplier <= 0) return C.TIME_UNSET;
        return value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : value * multiplier;
    }

    /**
     * {@code live()} and {@code vod()} are the evidence collected by this layer. Both are
     * false only on a segmented resource whose manifest is not authoritative
     * ({@code MANIFEST_NOT_MEDIA}, {@code MANIFEST_UNKNOWN} or {@code MANIFEST_STALE}); on a
     * non-segmented resource {@code vod = !live}, so the two are always complementary. The
     * externally visible verdict is always complementary because {@code isVod = !isLive}.
     */
    public record Decision(
            PlaybackAutoContext.Protocol protocol,
            PlaybackAutoContext.StreamKind streamKind,
            boolean segmented,
            boolean authoritative,
            boolean live,
            boolean vod,
            boolean dynamic,
            boolean seekable,
            long targetOffsetMs,
            TimelineSnapshot timelineSnapshot,
            Reason reason) {

        public Decision {
            protocol = protocol == null
                    ? PlaybackAutoContext.Protocol.UNKNOWN : protocol;
            streamKind = streamKind == null
                    ? PlaybackAutoContext.StreamKind.UNKNOWN : streamKind;
            if (targetOffsetMs < 0 && targetOffsetMs != C.TIME_UNSET) {
                targetOffsetMs = C.TIME_UNSET;
            }
            reason = reason == null ? Reason.MANIFEST_UNKNOWN : reason;
        }
    }

    /** URL-free value object used as the Media3 timeline manifest identity. */
    public record TimelineSnapshot(
            PlaybackAutoContext.Protocol protocol,
            PlaybackAutoContext.StreamKind streamKind,
            PlaybackAutoContext.ManifestFacts manifest,
            boolean actualManifest,
            long observedAtElapsedMs) {

        public TimelineSnapshot {
            protocol = protocol == null
                    ? PlaybackAutoContext.Protocol.UNKNOWN : protocol;
            streamKind = streamKind == null
                    ? PlaybackAutoContext.StreamKind.UNKNOWN : streamKind;
            manifest = manifest == null
                    ? PlaybackAutoContext.ManifestFacts.unknown() : manifest;
            observedAtElapsedMs = Math.max(-1, observedAtElapsedMs);
        }

        private static TimelineSnapshot from(
                PlaybackResourceClassifier.Classification classification) {
            if (classification == null) {
                return new TimelineSnapshot(
                        PlaybackAutoContext.Protocol.UNKNOWN,
                        PlaybackAutoContext.StreamKind.UNKNOWN,
                        PlaybackAutoContext.ManifestFacts.unknown(),
                        false,
                        -1);
            }
            return new TimelineSnapshot(
                    classification.protocol(),
                    classification.streamKind(),
                    classification.manifest(),
                    classification.actualManifest(),
                    classification.observedAtElapsedMs());
        }
    }

    public enum Reason {
        MANIFEST_VOD("manifest-vod"),
        MANIFEST_LIVE("manifest-live"),
        MANIFEST_LOW_LATENCY_LIVE("manifest-ll-live"),
        MANIFEST_UNKNOWN("manifest-unknown"),
        MANIFEST_NOT_MEDIA("manifest-not-media"),
        MANIFEST_STALE("manifest-stale"),
        PROTOCOL_LIVE("protocol-live"),
        LEGACY_DURATION_LIVE("legacy-duration-live"),
        LEGACY_DURATION_VOD("legacy-duration-vod");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
