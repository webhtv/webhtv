package com.fongmi.android.tv.player;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AudioPlaybackDiagnostics {

    private AudioPlaybackDiagnostics() {
    }

    public enum DecodeMode {
        HARDWARE,
        SOFTWARE,
        NONE,
        UNKNOWN
    }

    public enum OutputMode {
        PASSTHROUGH,
        PCM,
        COMPRESSED_DIRECT,
        OFFLOAD,
        UNKNOWN
    }

    /** The strategy actually selected for the current audio path. */
    public enum DecisionLevel {
        EXACT_PASSTHROUGH,
        COMPRESSED_OFFLOAD,
        HARDWARE_PCM,
        SAME_TRACK_COMPATIBLE,
        SOFTWARE_PCM,
        LANGUAGE_OR_STEREO_FALLBACK
    }

    public enum RuntimeState {
        UNKNOWN,
        PENDING,
        ACTIVE,
        FAILED
    }

    /** Stable diagnostic codes. Do not expose exception class names as protocol values. */
    public enum FailureReason {
        NONE(""),
        UNKNOWN("unknown"),
        DIRECT_OUTPUT_INIT("direct-output-init"),
        DIRECT_OUTPUT_WRITE("direct-output-write"),
        OFFLOAD_INIT("offload-init"),
        OFFLOAD_WRITE("offload-write"),
        DECODER_INIT("decoder-init"),
        DECODER_RUNTIME("decoder-runtime"),
        CHANNEL_LAYOUT_UNSUPPORTED("channel-layout-unsupported"),
        SAME_TRACK_COMPATIBLE("same-track-compatible"),
        SAME_LANGUAGE_STEREO("same-language-stereo"),
        MANUAL_TRACK_SELECTION("manual-track-selection");

        private final String code;

        FailureReason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Track(String codec, int channels, int sampleRate,
                        String language, int bitrate) {

        public Track {
            codec = clean(codec);
            language = clean(language);
            channels = Math.max(0, channels);
            sampleRate = Math.max(0, sampleRate);
            bitrate = Math.max(0, bitrate);
        }

        public Track withCodec(String codec) {
            return new Track(codec, channels, sampleRate, language, bitrate);
        }

        public boolean available() {
            return !codec.isEmpty() || channels > 0 || sampleRate > 0;
        }

        public static Track empty() {
            return new Track("", 0, 0, "", 0);
        }
    }

    public record Snapshot(Track originalTrack, Track activeTrack,
                           DecodeMode decodeMode, String decoderName,
                           OutputMode outputMode, int outputChannels,
                           int outputSampleRate, boolean tunneling,
                           String downgradeReason, DecisionLevel decisionLevel,
                           RuntimeState runtimeState, FailureReason failureReason) {

        public Snapshot(Track originalTrack, Track activeTrack,
                        DecodeMode decodeMode, String decoderName,
                        OutputMode outputMode, int outputChannels,
                        int outputSampleRate, boolean tunneling,
                        String downgradeReason) {
            this(originalTrack, activeTrack, decodeMode, decoderName, outputMode,
                    outputChannels, outputSampleRate, tunneling, downgradeReason,
                    AudioPlaybackDiagnostics.decisionLevel(
                            outputMode, decodeMode, downgradeReason),
                    AudioPlaybackDiagnostics.runtimeState(
                            originalTrack, activeTrack, outputMode, false),
                    AudioPlaybackDiagnostics.failureReason(downgradeReason));
        }

        public Snapshot {
            originalTrack = originalTrack == null ? Track.empty() : originalTrack;
            activeTrack = activeTrack == null ? Track.empty() : activeTrack;
            decodeMode = decodeMode == null ? DecodeMode.UNKNOWN : decodeMode;
            decoderName = clean(decoderName);
            outputMode = outputMode == null ? OutputMode.UNKNOWN : outputMode;
            outputChannels = Math.max(0, outputChannels);
            outputSampleRate = Math.max(0, outputSampleRate);
            downgradeReason = clean(downgradeReason);
            decisionLevel = decisionLevel == null
                    ? AudioPlaybackDiagnostics.decisionLevel(
                            outputMode, decodeMode, downgradeReason)
                    : decisionLevel;
            runtimeState = runtimeState == null
                    ? AudioPlaybackDiagnostics.runtimeState(
                            originalTrack, activeTrack, outputMode, false)
                    : runtimeState;
            failureReason = failureReason == null
                    ? AudioPlaybackDiagnostics.failureReason(downgradeReason)
                    : failureReason;
        }

        public boolean available() {
            return originalTrack.available() || activeTrack.available()
                    || outputMode != OutputMode.UNKNOWN
                    || runtimeState != RuntimeState.UNKNOWN;
        }

        public boolean downgraded() {
            return !downgradeReason.isEmpty() && originalTrack.available()
                    && activeTrack.available();
        }

        public static Snapshot empty() {
            return new Snapshot(Track.empty(), Track.empty(), DecodeMode.UNKNOWN,
                    "", OutputMode.UNKNOWN, 0, 0, false, "", null,
                    RuntimeState.UNKNOWN, FailureReason.NONE);
        }
    }

    public static RuntimeState runtimeState(Track originalTrack, Track activeTrack,
                                            OutputMode outputMode, boolean failed) {
        if (failed) return RuntimeState.FAILED;
        boolean trackKnown = (originalTrack != null && originalTrack.available())
                || (activeTrack != null && activeTrack.available());
        if (!trackKnown) return RuntimeState.UNKNOWN;
        return outputMode == null || outputMode == OutputMode.UNKNOWN
                ? RuntimeState.PENDING : RuntimeState.ACTIVE;
    }

    public static DecisionLevel decisionLevel(OutputMode outputMode,
                                              DecodeMode decodeMode,
                                              String downgradeReason) {
        FailureReason reason = failureReason(downgradeReason);
        if (reason == FailureReason.SAME_TRACK_COMPATIBLE) {
            return DecisionLevel.SAME_TRACK_COMPATIBLE;
        }
        if (reason == FailureReason.SAME_LANGUAGE_STEREO) {
            return DecisionLevel.LANGUAGE_OR_STEREO_FALLBACK;
        }
        OutputMode mode = outputMode == null ? OutputMode.UNKNOWN : outputMode;
        return switch (mode) {
            case PASSTHROUGH, COMPRESSED_DIRECT -> DecisionLevel.EXACT_PASSTHROUGH;
            case OFFLOAD -> DecisionLevel.COMPRESSED_OFFLOAD;
            case PCM -> decodeMode == DecodeMode.HARDWARE
                    ? DecisionLevel.HARDWARE_PCM : DecisionLevel.SOFTWARE_PCM;
            case UNKNOWN -> null;
        };
    }

    public static DecisionLevel lastAttemptLevel(FailureReason failureReason,
                                                 OutputMode outputMode,
                                                 DecodeMode decodeMode) {
        FailureReason reason = failureReason == null ? FailureReason.UNKNOWN
                : failureReason;
        return switch (reason) {
            case OFFLOAD_INIT, OFFLOAD_WRITE -> DecisionLevel.COMPRESSED_OFFLOAD;
            case DIRECT_OUTPUT_INIT, DIRECT_OUTPUT_WRITE -> DecisionLevel.EXACT_PASSTHROUGH;
            case DECODER_INIT, DECODER_RUNTIME, CHANNEL_LAYOUT_UNSUPPORTED ->
                    decodeMode == DecodeMode.HARDWARE
                            ? DecisionLevel.HARDWARE_PCM : DecisionLevel.SOFTWARE_PCM;
            case SAME_TRACK_COMPATIBLE -> DecisionLevel.SAME_TRACK_COMPATIBLE;
            case SAME_LANGUAGE_STEREO -> DecisionLevel.LANGUAGE_OR_STEREO_FALLBACK;
            case MANUAL_TRACK_SELECTION, NONE, UNKNOWN ->
                    decisionLevel(outputMode, decodeMode, "");
        };
    }

    public static FailureReason failureReason(String value) {
        String reason = lower(value);
        if (reason.isEmpty()) return FailureReason.NONE;
        if (reason.contains("dts-hd-core") || reason.contains("same-track-compatible")) {
            return FailureReason.SAME_TRACK_COMPATIBLE;
        }
        if (reason.contains("same-language-stereo")
                || reason.contains("same-language-efficient")) {
            return FailureReason.SAME_LANGUAGE_STEREO;
        }
        for (FailureReason candidate : FailureReason.values()) {
            if (!candidate.code.isEmpty() && candidate.code.equals(reason)) return candidate;
        }
        return FailureReason.UNKNOWN;
    }

    public static FailureReason failureReason(int errorCode) {
        return switch (errorCode) {
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
                    FailureReason.DIRECT_OUTPUT_INIT;
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
                    FailureReason.DIRECT_OUTPUT_WRITE;
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED ->
                    FailureReason.OFFLOAD_INIT;
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED ->
                    FailureReason.OFFLOAD_WRITE;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                    FailureReason.DECODER_INIT;
            case PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                    FailureReason.DECODER_RUNTIME;
            default -> FailureReason.UNKNOWN;
        };
    }

    public static Track track(Format format, String profile) {
        if (format == null) return Track.empty();
        return track(format.codecs, format.label, profile, format.sampleMimeType,
                format.channelCount, format.sampleRate, format.language,
                Math.max(format.averageBitrate, format.bitrate));
    }

    public static Track track(String codec, String title, String profile,
                              String mimeType, int channels, int sampleRate,
                              String language, int bitrate) {
        return new Track(codecLabel(codec, title, profile, mimeType), channels,
                sampleRate, language, bitrate);
    }

    public static Track passthroughTrack(Track source, String outputFormat) {
        Track base = source == null ? Track.empty() : source;
        String format = lower(outputFormat);
        if (format.contains("spdif-dts") && !format.contains("dtshd")) {
            return base.withCodec("DTS Core");
        }
        if (format.contains("spdif-dtshd")) {
            String codec = lower(base.codec());
            return codec.contains("dts-hd") || codec.contains("dts:x")
                    ? base : base.withCodec("DTS-HD");
        }
        if (format.contains("spdif-truehd")) return base.withCodec("Dolby TrueHD");
        if (format.contains("spdif-eac3")) return base.withCodec("E-AC-3");
        if (format.contains("spdif-ac3")) return base.withCodec("AC-3");
        return base;
    }

    public static Track encodedTrack(Track source, int encoding) {
        Track base = source == null ? Track.empty() : source;
        return switch (encoding) {
            case C.ENCODING_AC3 -> base.withCodec("AC-3");
            case C.ENCODING_E_AC3 -> base.withCodec("E-AC-3");
            case C.ENCODING_E_AC3_JOC -> base.withCodec("E-AC-3 JOC");
            case C.ENCODING_DTS -> base.withCodec("DTS Core");
            case C.ENCODING_DTS_HD -> base.withCodec("DTS-HD");
            case C.ENCODING_DTS_HD_MA -> base.withCodec("DTS-HD MA");
            case C.ENCODING_DOLBY_TRUEHD -> base.withCodec("Dolby TrueHD");
            default -> base;
        };
    }

    public static OutputMode mpvOutputMode(String outputFormat) {
        return mpvOutputMode(outputFormat, "");
    }

    public static OutputMode mpvOutputMode(String outputFormat, String outputMode) {
        String observed = lower(outputMode);
        if (observed.equals("passthrough")) return OutputMode.PASSTHROUGH;
        if (observed.equals("offload")) return OutputMode.OFFLOAD;
        if (observed.equals("compressed-direct")) return OutputMode.COMPRESSED_DIRECT;
        if (observed.equals("pcm")) return OutputMode.PCM;
        String value = lower(outputFormat);
        if (value.isEmpty()) return OutputMode.UNKNOWN;
        if (value.equals("aac") || value.equals("mp3")
                || value.equals("encoded-aac") || value.equals("encoded-mp3")) {
            return OutputMode.COMPRESSED_DIRECT;
        }
        return value.startsWith("spdif-") ? OutputMode.PASSTHROUGH : OutputMode.PCM;
    }

    public static String format(Snapshot snapshot) {
        if (snapshot == null || !snapshot.available()) return "";
        Track active = snapshot.activeTrack().available()
                ? snapshot.activeTrack() : snapshot.originalTrack();
        String track = formatTrack(active);
        if (snapshot.downgraded()) {
            track = formatTrack(snapshot.originalTrack()) + "/降级" + formatTrack(active);
        }
        List<String> parts = new ArrayList<>();
        add(parts, track);
        if (snapshot.runtimeState() == RuntimeState.FAILED) {
            add(parts, "输出失败");
            add(parts, snapshot.failureReason().code());
        } else if (snapshot.runtimeState() == RuntimeState.PENDING) {
            add(parts, "输出初始化中");
        } else switch (snapshot.outputMode()) {
            case PASSTHROUGH -> add(parts, "直通");
            case COMPRESSED_DIRECT -> {
                add(parts, "硬解");
                add(parts, "直出");
            }
            case OFFLOAD -> {
                add(parts, "硬解");
                add(parts, "卸载");
            }
            case PCM -> {
                add(parts, decodeText(snapshot.decodeMode()));
                add(parts, "PCM" + channelSuffix(snapshot.outputChannels()));
            }
            case UNKNOWN -> {
                add(parts, decodeText(snapshot.decodeMode()));
                add(parts, "输出待确认");
            }
        }
        if (snapshot.tunneling()) add(parts, "隧道");
        int sampleRate = snapshot.outputSampleRate() > 0
                ? snapshot.outputSampleRate() : active.sampleRate();
        if (sampleRate > 0) add(parts, sampleRateText(sampleRate));
        if (active.bitrate() > 0) add(parts, bitrateText(active.bitrate()));
        return String.join(" · ", parts);
    }

    public static String channelLabel(int channels) {
        return switch (channels) {
            case 1 -> "1.0";
            case 2 -> "2.0";
            case 3 -> "3.0";
            case 4 -> "4.0";
            case 5 -> "5.0";
            case 6 -> "5.1";
            case 7 -> "6.1";
            case 8 -> "7.1";
            default -> channels > 0 ? channels + "ch" : "";
        };
    }

    public static String codecLabel(String codec, String title, String profile,
                                    String mimeType) {
        String value = lower(join(codec, title, profile, mimeType));
        if (value.contains("dts:x") || value.contains("dtsx")) return "DTS:X";
        if (containsAny(value, "dts-hd ma", "dts hd ma", "master audio")) return "DTS-HD MA";
        if (containsAny(value, "dts-hd hra", "dts hd hra", "high resolution")) return "DTS-HD HRA";
        if (containsAny(value, "dts-hd", "dts_hd", "dtshd", "dts hd")) return "DTS-HD";
        if (value.contains("truehd") || value.contains("true hd")) {
            return value.contains("atmos") ? "Dolby TrueHD Atmos" : "Dolby TrueHD";
        }
        if (containsAny(value, "eac3-joc", "e-ac-3 joc", "eac3_joc")) return "E-AC-3 JOC";
        if (containsAny(value, "eac3", "e-ac-3", "e-ac3")) return "E-AC-3";
        if (containsAny(value, "ac3", "ac-3")) return "AC-3";
        if (value.contains("dts")) return "DTS";
        // media3 对 MP4/M4A 里的 AAC 上报的是编解码串 "mp4a.40.x"（不含 "aac"），
        // 只匹配 "aac" 会把它原样当成徽标/诊断文本，这里补上 RFC 6381 的等价写法。
        if (value.contains("aac") || value.contains("mp4a.40")) return "AAC";
        if (value.contains("opus")) return "Opus";
        if (value.contains("vorbis")) return "Vorbis";
        if (value.contains("flac")) return "FLAC";
        if (value.contains("alac")) return "ALAC";
        if (containsAny(value, "mp3", "mpeg audio", "audio/mpeg")) return "MP3";
        if (containsAny(value, "pcm", "audio/raw")) return "PCM";
        String fallback = clean(codec);
        if (fallback.isEmpty()) fallback = clean(mimeType);
        return fallback.isEmpty() ? "音频" : fallback.toUpperCase(Locale.US);
    }

    private static String formatTrack(Track track) {
        if (track == null || !track.available()) return "音频";
        return clean(track.codec()) + channelSuffix(track.channels());
    }

    private static String decodeText(DecodeMode mode) {
        return switch (mode) {
            case HARDWARE -> "硬解";
            case SOFTWARE -> "软解";
            case NONE -> "";
            case UNKNOWN -> "解码待确认";
        };
    }

    private static String channelSuffix(int channels) {
        String value = channelLabel(channels);
        return value.isEmpty() ? "" : " " + value;
    }

    private static String sampleRateText(int sampleRate) {
        if (sampleRate % 1000 == 0) return sampleRate / 1000 + "kHz";
        return String.format(Locale.US, "%.1fkHz", sampleRate / 1000f);
    }

    private static String bitrateText(int bitrate) {
        if (bitrate >= 1_000_000) {
            return String.format(Locale.US, "%.1fMbps", bitrate / 1_000_000f);
        }
        return Math.max(1, bitrate / 1000) + "kbps";
    }

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) parts.add(value);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String clean = clean(value);
            if (clean.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(clean);
        }
        return builder.toString();
    }

    private static String lower(String value) {
        return clean(value).toLowerCase(Locale.US);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
