package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.setting.PlayerSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.List;

/** Process-level access to persistent EXO video decoder runtime profiles. */
public final class ExoDecoderRuntimeProfiles implements ExoDecoderRuntimeSession.ProfileAccess {

    private static final String PREFERENCE_KEY = "perf_exo_decoder_runtime_v1";

    private final ExoDecoderRuntimeProfileStore store;
    private final ExoDecoderRuntimeKey.Environment environment;

    ExoDecoderRuntimeProfiles(
            ExoDecoderRuntimeProfileStore.Backend backend,
            ExoDecoderRuntimeKey.Environment environment) {
        this.store = new ExoDecoderRuntimeProfileStore(backend);
        this.environment = environment;
    }

    public static ExoDecoderRuntimeProfiles process() {
        return Holder.INSTANCE;
    }

    public ExoDecoderRuntimeSession newSession() {
        return new ExoDecoderRuntimeSession(this);
    }

    @Override
    public ExoDecoderRuntimeKey.Environment environment() {
        return environment;
    }

    @Override
    public boolean isBlacklisted(ExoDecoderRuntimeKey.Key key, long nowEpochMs) {
        return store.lookup(key, nowEpochMs).blacklisted();
    }

    @Override
    public ExoDecoderRuntimeProfileStore.Entry recordFailure(
            ExoDecoderRuntimeKey.Key key,
            ExoDecoderRuntimeProfileStore.FailureKind failureKind,
            ExoDecoderRuntimeProfileStore.Metrics metrics,
            long nowEpochMs) {
        ExoDecoderRuntimeProfileStore.RecordResult result =
                store.recordFailure(key, failureKind, metrics, nowEpochMs);
        log("failure", key, result.entry());
        return result.entry();
    }

    @Override
    public ExoDecoderRuntimeProfileStore.Entry recordFirstFrame(
            ExoDecoderRuntimeKey.Key key,
            long nowEpochMs) {
        ExoDecoderRuntimeProfileStore.RecordResult result =
                store.recordFirstFrame(key, nowEpochMs);
        log("first-frame", key, result.entry());
        return result.entry();
    }

    @Override
    public ExoDecoderRuntimeProfileStore.Entry recordStableSuccess(
            ExoDecoderRuntimeKey.Key key,
            ExoDecoderRuntimeProfileStore.Metrics metrics,
            long nowEpochMs) {
        ExoDecoderRuntimeProfileStore.RecordResult result =
                store.recordStableSuccess(key, metrics, nowEpochMs);
        log("stable", key, result.entry());
        return result.entry();
    }

    @Override
    public ExoDecoderRuntimeProfileStore.Entry recordObservation(
            ExoDecoderRuntimeKey.Key key,
            ExoDecoderRuntimeProfileStore.Metrics metrics,
            long nowEpochMs) {
        ExoDecoderRuntimeProfileStore.RecordResult result =
                store.recordObservation(key, metrics, nowEpochMs);
        if (result.recorded()) log("observation", key, result.entry());
        return result.entry();
    }

    @Override
    public ExoDecoderRuntimeProfileStore.Entry recordFallback(
            ExoDecoderRuntimeKey.Key key,
            ExoDecoderRuntimeProfileStore.FallbackResult fallbackResult,
            long nowEpochMs) {
        ExoDecoderRuntimeProfileStore.RecordResult result =
                store.recordFallback(key, fallbackResult, nowEpochMs);
        log(fallbackResult == ExoDecoderRuntimeProfileStore.FallbackResult.SUCCESS
                ? "fallback-success" : "fallback-failure", key, result.entry());
        return result.entry();
    }

    @Override
    public void logExclusion(ExoDecoderRuntimeKey.Key key, boolean transientOnly) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log(
                "exo-decoder-profile",
                "action=exclude key=%s scope=%s mime=%s size=%dx%d fpsMilli=%d secure=%s output=%s tunneling=%s",
                ExoDecoderRuntimeKey.shortId(key),
                transientOnly ? "session" : "persistent",
                key.mimeType(),
                key.width(),
                key.height(),
                key.frameRateMilli(),
                key.secure(),
                key.outputTarget().name().toLowerCase(java.util.Locale.US),
                key.tunneling());
    }

    /** Number of decoder+format combinations currently excluded for this device. */
    public int blacklistedCount() {
        return activeBlacklist().size();
    }

    /**
     * Human-readable description of the current persistent decoder exclusions, or {@code ""} when
     * none is active. The exclusions are otherwise silent: playback simply falls back to software
     * decoding, which on 4K content looks like unexplained frame drops.
     */
    public String blacklistSummary() {
        List<ExoDecoderRuntimeProfileStore.Entry> active = activeBlacklist();
        if (active.isEmpty()) return "";
        ExoDecoderRuntimeKey.Key latest = active.get(0).key();
        String size = latest.width() > 0 && latest.height() > 0
                ? " " + latest.width() + "×" + latest.height() : "";
        return "硬解已排除 " + active.size() + " 条（" + mimeLabel(latest.mimeType()) + size + "）";
    }

    /**
     * Exclusions recorded under the current playback environment only. Entries survive an app or
     * media3 upgrade in the store (30-day TTL) but can never match a key built with the new
     * environment digest, so reporting them as active would point at a hardware decode fallback
     * that is not happening.
     */
    private List<ExoDecoderRuntimeProfileStore.Entry> activeBlacklist() {
        String environmentDigest = environment.digest();
        List<ExoDecoderRuntimeProfileStore.Entry> result = new ArrayList<>();
        for (ExoDecoderRuntimeProfileStore.Entry entry
                : store.activeBlacklist(System.currentTimeMillis())) {
            if (environmentDigest.equals(entry.key().environmentDigest())) result.add(entry);
        }
        return result;
    }

    private static String mimeLabel(String mimeType) {
        if (mimeType == null) return "未知格式";
        return switch (mimeType) {
            case "video/hevc" -> "HEVC";
            case "video/avc" -> "H.264";
            case "video/av01" -> "AV1";
            case "video/x-vnd.on2.vp9" -> "VP9";
            case "video/dolby-vision" -> "DV";
            default -> mimeType;
        };
    }

    private static void log(
            String action,
            ExoDecoderRuntimeKey.Key key,
            ExoDecoderRuntimeProfileStore.Entry entry) {
        if (!SpiderDebug.isEnabled() || key == null || entry == null) return;
        SpiderDebug.log(
                "exo-decoder-profile",
                "action=%s key=%s firstFrames=%d failures=%d consecutive=%d stable=%d blacklisted=%s drops=%d observedMs=%d recoverableErrors=%d fallbackOk=%d fallbackFailed=%d kind=%s",
                action,
                ExoDecoderRuntimeKey.shortId(key),
                entry.firstFrameCount(),
                entry.failureCount(),
                entry.consecutiveFailures(),
                entry.successCount(),
                entry.blacklisted(),
                entry.droppedFrames(),
                entry.observedDurationMs(),
                entry.recoverableCodecErrors(),
                entry.fallbackSuccessCount(),
                entry.fallbackFailureCount(),
                entry.lastFailureKind().name().toLowerCase(java.util.Locale.US));
    }

    public static ExoDecoderRuntimeSession.OutputConfig currentOutput(boolean tunneling) {
        return new ExoDecoderRuntimeSession.OutputConfig(
                PlayerSetting.getRender() == PlayerSetting.RENDER_TEXTURE
                        ? ExoDecoderRuntimeSession.OutputTarget.TEXTURE
                        : ExoDecoderRuntimeSession.OutputTarget.SURFACE,
                tunneling);
    }

    private static final class Holder {

        private static final ExoDecoderRuntimeProfiles INSTANCE =
                new ExoDecoderRuntimeProfiles(
                        new PreferencesBackend(),
                        ExoDecoderRuntimeKey.currentEnvironment());
    }

    private static final class PreferencesBackend
            implements ExoDecoderRuntimeProfileStore.Backend {

        @Override
        public String read() {
            return Prefers.getString(PREFERENCE_KEY);
        }

        @Override
        public void write(String value) {
            Prefers.put(PREFERENCE_KEY, value);
        }

        @Override
        public void clear() {
            Prefers.remove(PREFERENCE_KEY);
        }
    }
}
