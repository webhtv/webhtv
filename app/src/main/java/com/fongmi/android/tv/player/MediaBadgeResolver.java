package com.fongmi.android.tv.player;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;

import com.fongmi.android.tv.player.MediaBadgeCatalog.Group;
import com.fongmi.android.tv.player.engine.PlayerEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 把播放器的运行时事实与片源文件名转成屏显媒体信息徽标（chip）。
 * <p>
 * 每条徽标由「图形牌 + 事实文本」组成，图形牌全部来自 {@link MediaBadgeCatalog} 的成品徽标，
 * 因此不存在自绘占位图标与内容对不上的情况：没有对应牌的事实（帧率、码率、容器、少见的编码）
 * 只输出文本，靠分组配色区分，不画图标。
 * <p>
 * 证据来源分两类：
 * <ul>
 *   <li>运行时事实（分辨率、动态范围、视频编码、音频编码、声道）优先，只有运行时拿不到时才回退文件名；</li>
 *   <li>发布来源、画质、特别标签、IMAX、流媒体只能由文件名/标题字符串证明，运行时事实给不了这些证据。</li>
 * </ul>
 * 纯逻辑、无 Android 框架依赖，可脱离设备做 JVM 单测。
 */
public final class MediaBadgeResolver {

    private static final String[] EXTENSIONS = {"mkv", "mp4", "m4v", "webm", "mov", "avi", "flv", "ts", "m2ts", "wmv", "mpg", "mpeg", "3gp", "ogv"};

    /** 分类徽标牌键，直接对应 drawable-nodpi 里的成品图形。 */
    private static final String P_RES_8K = "gr_8k";
    private static final String P_RES_4K = "gr_4k";
    private static final String P_RES_1440P = "gr_1440p";
    private static final String P_RES_1080P = "gr_1080p";
    private static final String P_RES_720P = "gr_720p";
    private static final String P_RES_576P = "gr_576p";
    private static final String P_RES_480P = "gr_480p";
    private static final String P_RES_360P = "gr_360p";
    private static final String P_RES_240P = "gr_240p";
    private static final String P_RES_144P = "gr_144p";
    private static final String P_DR_DV = "gv_dolby_vision";
    private static final String P_DR_HDR10 = "gv_hdr10";
    private static final String P_DR_HDR = "gv_hdr";
    private static final String P_DR_HLG = "gv_hlg";
    private static final String P_DR_10BIT = "gv_10bit";
    private static final String P_DR_12BIT = "gv_12bit";
    private static final String P_CODEC_HEVC = "ge_hevc";
    private static final String P_CODEC_AVC = "ge_avc";
    private static final String P_CODEC_AV1 = "ge_av1";
    private static final String P_CODEC_VVC = "ge_vvc";
    private static final String P_AUDIO_DTS_X = "ga_dts_x";
    private static final String P_AUDIO_DTS_X_HD_MA = "ga_dts_x_hd_ma";
    private static final String P_AUDIO_DTS_X_HD = "ga_dts_x_hd";
    private static final String P_AUDIO_DTS_HD_MA = "ga_dts_hd_ma";
    private static final String P_AUDIO_DTS_HD = "ga_dts_hd";
    private static final String P_AUDIO_DTS = "ga_dts";
    private static final String P_AUDIO_ATMOS_TRUEHD = "ga_dolby_atmos_truehd";
    private static final String P_AUDIO_TRUEHD = "ga_dolby_truehd";
    private static final String P_AUDIO_ATMOS_DDP = "ga_dolby_atmos_digital_plus";
    private static final String P_AUDIO_DDP = "ga_dolby_digital_plus";
    private static final String P_AUDIO_DD = "ga_dolby_digital";
    private static final String P_AUDIO_AAC = "ga_aac";
    private static final String P_AUDIO_FLAC = "ga_flac";
    private static final String P_AUDIO_OPUS = "ga_opus";
    private static final String P_AUDIO_LPCM = "ga_lpcm";
    private static final String P_CH_7_1 = "gc_7_1";
    private static final String P_CH_6_1 = "gc_6_1";
    private static final String P_CH_5_1 = "gc_5_1";
    private static final String P_CH_2_0 = "gc_2_0";
    private static final String P_CH_7_1_4 = "gc_7_1_4";
    private static final String P_CH_5_1_4 = "gc_5_1_4";
    private static final String P_CH_5_1_2 = "gc_5_1_2";

    /** 运行时能证明事实的组：这些组先看运行时，只有拿不到时才用文件名兜底。 */
    private static final Set<Group> RUNTIME_GROUPS = EnumSet.of(Group.RESOLUTION, Group.DYNAMIC_RANGE, Group.VIDEO_CODEC, Group.AUDIO, Group.CHANNELS);

    /**
     * 一条徽标。
     *
     * @param text  事实文本；牌已经完整写明该事实时为空串
     * @param group 分组，决定徽标配色与展示顺序
     * @param plate 成品牌键（见 {@link MediaBadgeCatalog}）；没有对应牌时为 null
     */
    public record MediaBadge(String text, Group group, String plate) {

        public MediaBadge {
            text = text == null ? "" : text.trim();
        }
    }

    private MediaBadgeResolver() {
    }

    /**
     * @param video        当前视频轨；可为 null
     * @param videoDetails 引擎给出的视频播放细节（Dolby Vision 等）；可为 null
     * @param audio        当前音频轨；可为 null
     * @param url          播放地址，用于容器扩展名兜底与文件名类徽标；可为 null
     * @param title        当前片名/标题，作为文件名类徽标的第二证据来源；可为 null
     * @return 按展示顺序排列的徽标，可能为空列表
     */
    public static List<MediaBadge> resolve(Format video, PlayerEngine.VideoPlaybackDetails videoDetails, AudioPlaybackDiagnostics.Track audio, String url, String title) {
        List<MediaBadge> badges = new ArrayList<>();
        Set<Group> runtimeGroups = EnumSet.noneOf(Group.class);
        add(badges, runtimeGroups, resolutionBadge(video));
        add(badges, runtimeGroups, dynamicRangeBadge(videoDetails, video));
        add(badges, runtimeGroups, videoCodecBadge(video, videoDetails));
        add(badges, runtimeGroups, frameRateBadge(video));
        add(badges, runtimeGroups, bitrateBadge(video));
        add(badges, runtimeGroups, audioBadge(audio));
        add(badges, runtimeGroups, channelsBadge(audio));
        add(badges, runtimeGroups, containerBadge(video, url));
        addSourceBadges(badges, runtimeGroups, url, title);
        badges.sort(Comparator.comparingInt((MediaBadge badge) -> displayOrder(badge.group())));
        return badges;
    }

    /**
     * 展示顺序：先看画面规格（分辨率、动态范围），再看片源与画质，
     * 然后是编码与音轨，最后是帧率、码率、版本标签与容器。
     * 这样最影响「这片值不值得看」的信息排在最左，纯技术参数收在右侧。
     */
    private static int displayOrder(Group group) {
        switch (group) {
            case RESOLUTION:
                return 0;
            case DYNAMIC_RANGE:
                return 1;
            case VIDEO_TECH:
                return 2;
            case MEDIA_SOURCE:
                return 3;
            case QUALITY:
                return 4;
            case EDITION:
                return 5;
            case VIDEO_CODEC:
                return 6;
            case AUDIO:
                return 7;
            case CHANNELS:
                return 8;
            case FRAME_RATE:
                return 9;
            case BITRATE:
                return 10;
            case SPECIAL_TAG:
                return 11;
            case IMAX:
                return 12;
            case STREAMING:
                return 13;
            default:
                return 14;
        }
    }

    private static void add(List<MediaBadge> badges, Set<Group> runtimeGroups, MediaBadge badge) {
        if (badge == null) return;
        if (badge.text().isEmpty() && badge.plate() == null) return;
        for (MediaBadge existing : badges) {
            if (existing.group() == badge.group() && existing.text().equals(badge.text()) && Objects.equals(existing.plate(), badge.plate())) return;
        }
        badges.add(badge);
        if (RUNTIME_GROUPS.contains(badge.group())) runtimeGroups.add(badge.group());
    }

    /**
     * 文件名类徽标：参考实现里的发布来源、画质、特别标签、IMAX、流媒体。
     * 这些都是文件名事实而不是播放事实，所以只匹配文件名/标题字符串，并且每个分组最多一枚，
     * 避免一个长版本名把整行 OSD 塞满。
     */
    private static void addSourceBadges(List<MediaBadge> badges, Set<Group> runtimeGroups, String url, String title) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String fileName = path(url);
        if (!fileName.isEmpty()) candidates.add(fileName);
        if (!isEmpty(title)) candidates.add(title.trim());
        if (candidates.isEmpty()) return;
        MediaBadgeCatalog.Rule[] rules = MediaBadgeCatalog.rules();
        Pattern[] patterns = MediaBadgeCatalog.patterns();
        Set<Group> taken = EnumSet.noneOf(Group.class);
        for (int i = 0; i < rules.length; i++) {
            MediaBadgeCatalog.Rule rule = rules[i];
            if (rule.runtime()) continue;
            if (taken.contains(rule.group())) continue;
            if (matches(patterns[i], candidates)) {
                taken.add(rule.group());
                badges.add(new MediaBadge("", rule.group(), rule.plate()));
            }
        }
        // 运行时拿不到的运行时组，用文件名里能读到的那一条补齐（例如容器没给分辨率，但文件名写了 2160p）。
        for (int i = 0; i < rules.length; i++) {
            MediaBadgeCatalog.Rule rule = rules[i];
            if (!rule.runtime()) continue;
            if (runtimeGroups.contains(rule.group()) || taken.contains(rule.group())) continue;
            if (matches(patterns[i], candidates)) {
                taken.add(rule.group());
                badges.add(new MediaBadge("", rule.group(), rule.plate()));
            }
        }
    }

    private static boolean matches(Pattern pattern, Set<String> candidates) {
        for (String candidate : candidates) {
            if (pattern.matcher(candidate).find()) return true;
        }
        return false;
    }

    /**
     * 分辨率只按长边分档：竖屏素材的长边就是它的清晰度档位，
     * 这样 656x1280 的竖屏视频归类为 720P 而不是被短边压成低清。
     */
    private static MediaBadge resolutionBadge(Format video) {
        if (video == null) return null;
        int width = video.width;
        int height = video.height;
        if (width <= 0 || height <= 0) return null;
        int longSide = Math.max(width, height);
        if (longSide >= 7000) return new MediaBadge("", Group.RESOLUTION, P_RES_8K);
        if (longSide >= 3840) return new MediaBadge("", Group.RESOLUTION, P_RES_4K);
        if (longSide >= 2560) return new MediaBadge("", Group.RESOLUTION, P_RES_1440P);
        if (longSide >= 1920) return new MediaBadge("", Group.RESOLUTION, P_RES_1080P);
        if (longSide >= 1280) return new MediaBadge("", Group.RESOLUTION, P_RES_720P);
        if (longSide >= 1024) return new MediaBadge("", Group.RESOLUTION, P_RES_576P);
        if (longSide >= 854) return new MediaBadge("", Group.RESOLUTION, P_RES_480P);
        if (longSide >= 640) return new MediaBadge("", Group.RESOLUTION, P_RES_360P);
        if (longSide >= 426) return new MediaBadge("", Group.RESOLUTION, P_RES_240P);
        if (longSide >= 256) return new MediaBadge("", Group.RESOLUTION, P_RES_144P);
        return new MediaBadge(longSide + "X" + Math.min(width, height), Group.RESOLUTION, null);
    }

    private static MediaBadge dynamicRangeBadge(PlayerEngine.VideoPlaybackDetails details, Format video) {
        if (details != null && details.hasDolbyVisionSource()) {
            int profile = details.dolbyVisionProfile();
            return new MediaBadge(profile > 0 ? "P" + profile : "", Group.DYNAMIC_RANGE, P_DR_DV);
        }
        ColorInfo color = details != null && details.outputColorInfo() != null ? details.outputColorInfo() : (video != null ? video.colorInfo : null);
        if (color == null) return null;
        if (color.colorTransfer == C.COLOR_TRANSFER_ST2084) return new MediaBadge("", Group.DYNAMIC_RANGE, P_DR_HDR10);
        if (color.colorTransfer == C.COLOR_TRANSFER_HLG) return new MediaBadge("", Group.DYNAMIC_RANGE, P_DR_HLG);
        if (ColorInfo.isTransferHdr(color)) return new MediaBadge("", Group.DYNAMIC_RANGE, P_DR_HDR);
        // SDR 视频不单独占用一枚徽标，只有高位深时补一枚位深牌。
        int bitdepth = Math.max(color.lumaBitdepth, color.chromaBitdepth);
        if (bitdepth >= 12) return new MediaBadge("", Group.DYNAMIC_RANGE, P_DR_12BIT);
        if (bitdepth >= 10) return new MediaBadge("", Group.DYNAMIC_RANGE, P_DR_10BIT);
        return null;
    }

    private static MediaBadge videoCodecBadge(Format video, PlayerEngine.VideoPlaybackDetails details) {
        String codecs = video != null && !isEmpty(video.codecs) ? video.codecs : details != null ? details.sourceCodecs() : null;
        if (isEmpty(codecs)) return null;
        String value = codecs.toLowerCase(Locale.US);
        // Dolby Vision 是叠加在基础编码上的，动态范围徽标已经说明 DV，这里如实给出底层编码。
        if (value.startsWith("dvhe") || value.startsWith("dvh1") || value.contains("dolby-vision")) return new MediaBadge("", Group.VIDEO_CODEC, P_CODEC_HEVC);
        if (value.startsWith("dav1")) return new MediaBadge("", Group.VIDEO_CODEC, P_CODEC_AV1);
        if (value.startsWith("dvav") || value.startsWith("dva1")) return new MediaBadge("", Group.VIDEO_CODEC, P_CODEC_AVC);
        if (value.contains("hev1") || value.contains("hvc1") || value.contains("hevc") || value.contains("h265")) return new MediaBadge("", Group.VIDEO_CODEC, P_CODEC_HEVC);
        if (value.contains("avc") || value.contains("h264")) return new MediaBadge("", Group.VIDEO_CODEC, P_CODEC_AVC);
        if (value.contains("av01") || value.contains("av1")) return new MediaBadge("", Group.VIDEO_CODEC, P_CODEC_AV1);
        if (value.contains("vvc") || value.contains("h266") || value.contains("h.266") || value.contains("x266")) return new MediaBadge("", Group.VIDEO_CODEC, P_CODEC_VVC);
        if (value.contains("vp09") || value.contains("vp9")) return new MediaBadge("VP9", Group.VIDEO_CODEC, null);
        if (value.contains("vp08") || value.contains("vp8")) return new MediaBadge("VP8", Group.VIDEO_CODEC, null);
        if (value.contains("mp4v") || value.contains("mpeg2") || value.contains("mpeg4")) return new MediaBadge("MPEG-4", Group.VIDEO_CODEC, null);
        int dot = value.indexOf('.');
        return new MediaBadge(upper(dot > 0 ? value.substring(0, dot) : value), Group.VIDEO_CODEC, null);
    }

    private static MediaBadge frameRateBadge(Format video) {
        if (video == null) return null;
        float frameRate = video.frameRate;
        if (frameRate <= 0f) return null;
        String value = frameRate == Math.rint(frameRate) ? String.valueOf((int) frameRate) : trimZeros(String.format(Locale.US, "%.3f", frameRate));
        return new MediaBadge(value + "FPS", Group.FRAME_RATE, null);
    }

    private static MediaBadge bitrateBadge(Format video) {
        if (video == null) return null;
        int bitrate = Math.max(video.averageBitrate, video.bitrate);
        if (bitrate <= 0) return null;
        return new MediaBadge(bitrateText(bitrate), Group.BITRATE, null);
    }

    private static MediaBadge audioBadge(AudioPlaybackDiagnostics.Track audio) {
        if (audio == null || !audio.available()) return null;
        String label = isEmpty(audio.codec()) ? "" : audioCodec(audio.codec());
        String plate = audioPlate(label);
        StringBuilder builder = new StringBuilder();
        if (audio.sampleRate() > 0) append(builder, sampleRateText(audio.sampleRate()));
        if (audio.bitrate() > 0) append(builder, bitrateText(audio.bitrate()));
        String text = builder.toString();
        if (plate == null) {
            // 认不出编码牌时把编码名留在文本里，否则徽标就只剩速率、看不出是什么音轨。
            if (label.isEmpty()) return null;
            text = text.isEmpty() ? label : label + " " + text;
        }
        return new MediaBadge(text, Group.AUDIO, plate);
    }

    private static MediaBadge channelsBadge(AudioPlaybackDiagnostics.Track audio) {
        if (audio == null || !audio.available()) return null;
        String label = AudioPlaybackDiagnostics.channelLabel(audio.channels());
        if (isEmpty(label)) return null;
        String plate = channelsPlate(label);
        return new MediaBadge(plate == null ? label : "", Group.CHANNELS, plate);
    }

    /** 复用音频诊断的编解码归一化，避免把 media3 的 MIME 子类型（mp4a.40.2）直接当徽标文本。 */
    private static String audioCodec(String codec) {
        String label = AudioPlaybackDiagnostics.codecLabel(codec, "", "", "");
        if (label.isEmpty() || "音频".equals(label)) return codec.toUpperCase(Locale.US);
        return label;
    }

    /** 音频牌按归一化后的编解码名匹配；认不出的编码不留牌，只留文本。 */
    private static String audioPlate(String label) {
        switch (label) {
            case "DTS:X":
                return P_AUDIO_DTS_X;
            case "DTS:X HD MA":
                return P_AUDIO_DTS_X_HD_MA;
            case "DTS:X HD":
                return P_AUDIO_DTS_X_HD;
            case "DTS-HD MA":
                return P_AUDIO_DTS_HD_MA;
            case "DTS-HD HRA":
            case "DTS-HD":
                return P_AUDIO_DTS_HD;
            case "DTS":
                return P_AUDIO_DTS;
            case "Dolby TrueHD Atmos":
                return P_AUDIO_ATMOS_TRUEHD;
            case "Dolby TrueHD":
                return P_AUDIO_TRUEHD;
            case "E-AC-3 JOC":
                return P_AUDIO_ATMOS_DDP;
            case "E-AC-3":
                return P_AUDIO_DDP;
            case "AC-3":
                return P_AUDIO_DD;
            case "AAC":
                return P_AUDIO_AAC;
            case "FLAC":
                return P_AUDIO_FLAC;
            case "Opus":
                return P_AUDIO_OPUS;
            case "PCM":
                return P_AUDIO_LPCM;
            default:
                return null;
        }
    }

    private static String channelsPlate(String label) {
        switch (label) {
            case "7.1":
                return P_CH_7_1;
            case "6.1":
                return P_CH_6_1;
            case "5.1":
                return P_CH_5_1;
            case "2.0":
                return P_CH_2_0;
            case "7.1.4":
                return P_CH_7_1_4;
            case "5.1.4":
                return P_CH_5_1_4;
            case "5.1.2":
                return P_CH_5_1_2;
            default:
                return null;
        }
    }

    private static MediaBadge containerBadge(Format video, String url) {
        String container = container(video, url);
        return container == null ? null : new MediaBadge(container, Group.CONTAINER, null);
    }

    private static void append(StringBuilder builder, String value) {
        if (builder.length() > 0) builder.append(' ');
        builder.append(value);
    }

    private static String sampleRateText(int sampleRate) {
        String value = sampleRate % 1000 == 0 ? String.valueOf(sampleRate / 1000) : trimZeros(String.format(Locale.US, "%.1f", sampleRate / 1000f));
        return value + "KHZ";
    }

    private static String bitrateText(int bitrate) {
        if (bitrate >= 1_000_000) return trimZeros(String.format(Locale.US, "%.1f", bitrate / 1_000_000f)) + "MBPS";
        return Math.max(1, bitrate / 1000) + "KBPS";
    }

    private static String container(Format video, String url) {
        String mime = video != null ? video.containerMimeType : null;
        String byMime = containerByMime(mime);
        if (byMime != null) return byMime;
        return containerByExtension(url);
    }

    private static String containerByMime(String mime) {
        if (isEmpty(mime)) return null;
        switch (mime.toLowerCase(Locale.US).trim()) {
            case "video/x-matroska":
            case "audio/x-matroska":
                return "MKV";
            case "video/mp4":
            case "audio/mp4":
            case "video/m4v":
                return "MP4";
            case "video/webm":
            case "audio/webm":
                return "WEBM";
            case "video/quicktime":
                return "MOV";
            case "video/x-msvideo":
                return "AVI";
            case "video/mp2t":
                return "TS";
            case "video/x-flv":
                return "FLV";
            case "video/3gpp":
            case "video/3gpp2":
                return "3GP";
            case "video/ogg":
                return "OGV";
            default:
                return null;
        }
    }

    private static String containerByExtension(String url) {
        String path = path(url);
        if (path.isEmpty()) return null;
        int dot = path.lastIndexOf('.');
        if (dot <= 0 || dot == path.length() - 1) return null;
        String extension = path.substring(dot + 1).toLowerCase(Locale.US);
        if (!isKnown(extension)) return null;
        if ("m4v".equals(extension)) return "MP4";
        if ("m2ts".equals(extension)) return "TS";
        return extension.toUpperCase(Locale.US);
    }

    private static boolean isKnown(String extension) {
        for (String value : EXTENSIONS) {
            if (value.equals(extension)) return true;
        }
        return false;
    }

    /** 去掉查询串与片段，避免把 "?a=b.ts" 误判成扩展名。 */
    private static String path(String url) {
        if (isEmpty(url)) return "";
        String value = url.trim();
        int end = value.length();
        int query = value.indexOf('?');
        if (query >= 0 && query < end) end = query;
        int fragment = value.indexOf('#');
        if (fragment >= 0 && fragment < end) end = fragment;
        value = value.substring(0, end);
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static String trimZeros(String value) {
        if (value.indexOf('.') < 0) return value;
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') end--;
        if (end > 0 && value.charAt(end - 1) == '.') end--;
        return value.substring(0, end);
    }

    private static String upper(String value) {
        return value.toUpperCase(Locale.US);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
