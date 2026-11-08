package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;

import com.fongmi.android.tv.player.MediaBadgeCatalog.Group;
import com.fongmi.android.tv.player.engine.PlayerEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 徽标解析的 JVM 单测：只覆盖纯逻辑，不加载 Android 框架。
 * <p>
 * 断言分两层：运行时事实（真的在播什么）与文件名事实（片源叫什么）。
 */
public class MediaBadgeResolverTest {

    @Test
    public void fourKHevcHdrKeepsTheDisplayOrder() {
        Format video = video(3840, 2160, "video/hevc", 23.976f, 15_000_000, C.COLOR_TRANSFER_ST2084);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video, null, null, null, null);

        assertEquals(List.of(Group.RESOLUTION, Group.DYNAMIC_RANGE, Group.VIDEO_CODEC, Group.FRAME_RATE, Group.BITRATE), groups(badges));
        assertEquals(Arrays.asList("gr_4k", "gv_hdr10", "ge_hevc", null, null), plates(badges));
        assertEquals(List.of("23.976FPS", "15MBPS"), texts(badges));
    }

    /** 牌自己已经写明了事实时右侧不再重复同一段文字，否则会出现 "4K 4K"。 */
    @Test
    public void plateBadgesDoNotRepeatTheirOwnText() {
        Format video = video(3840, 2160, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR);
        assertEquals(List.of(), texts(MediaBadgeResolver.resolve(video, null, null, null, null)));
    }

    @Test
    public void dolbyVisionReplacesTheHdrBadge() {
        PlayerEngine.VideoPlaybackDetails details = new PlayerEngine.VideoPlaybackDetails(
                "dvhe.07.06", 8, 6, "dvhe.08.06", "c2.qti.hevc.decoder", "", null, false);
        Format video = video(3840, 2160, "video/dolby-vision", 0f, 0, C.COLOR_TRANSFER_ST2084);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video, details, null, null, null);

        assertEquals(Arrays.asList("gr_4k", "gv_dolby_vision", "ge_hevc"), plates(badges));
        // DV 牌只写 "Dolby Vision"，Profile 号仍作为文本保留，信息不丢。
        assertEquals(List.of("P8"), texts(badges));
        assertFalse(plates(badges).contains("gv_hdr10"));
    }

    @Test
    public void fullHdH264KeepsIntegerFrameRate() {
        Format video = video(1920, 1080, "video/avc", 60f, 0, C.COLOR_TRANSFER_SDR);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video, null, null, null, null);

        assertEquals(Arrays.asList("gr_1080p", "ge_avc", null), plates(badges));
        assertEquals(List.of("60FPS"), texts(badges));
    }

    @Test
    public void hlgIsNamedAndSdrIsOmitted() {
        Format hlg = video(1920, 1080, "video/hevc", 0f, 0, C.COLOR_TRANSFER_HLG);
        Format sdr = video(1920, 1080, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR);

        assertEquals(Arrays.asList("gr_1080p", "gv_hlg", "ge_hevc"), plates(MediaBadgeResolver.resolve(hlg, null, null, null, null)));
        assertEquals(Arrays.asList("gr_1080p", "ge_hevc"), plates(MediaBadgeResolver.resolve(sdr, null, null, null, null)));
    }

    /** 竖屏素材按长边分档，不能被短边压成低清档位。 */
    @Test
    public void portraitVideoIsGradedByItsLongSide() {
        Format video = video(656, 1280, "video/hevc", 29.97f, 0, C.COLOR_TRANSFER_SDR);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video, null, null, null, null);

        assertEquals("gr_720p", badges.get(0).plate());
        assertEquals("ge_hevc", badges.get(1).plate());
        assertEquals("29.97FPS", badges.get(2).text());
    }

    /** 低分辨率档位也全部有牌，不能退化成 "256X144" 这种纯文本。 */
    @Test
    public void lowResolutionTiersHaveTheirOwnPlates() {
        assertEquals("gr_1440p", MediaBadgeResolver.resolve(video(2560, 1440, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null, null, null).get(0).plate());
        assertEquals("gr_576p", MediaBadgeResolver.resolve(video(1024, 576, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null, null, null).get(0).plate());
        assertEquals("gr_480p", MediaBadgeResolver.resolve(video(854, 480, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null, null, null).get(0).plate());
        assertEquals("gr_360p", MediaBadgeResolver.resolve(video(640, 360, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null, null, null).get(0).plate());
        assertEquals("gr_240p", MediaBadgeResolver.resolve(video(426, 240, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null, null, null).get(0).plate());
        assertEquals("gr_144p", MediaBadgeResolver.resolve(video(256, 144, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null, null, null).get(0).plate());
    }

    /** 只显示可证事实：认不出的尺寸宁可写 "长边X短边"，也不硬套一个档位牌。 */
    @Test
    public void unknownSizeFallsBackToTextWithoutAPlate() {
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video(200, 120, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null, null, null);
        assertEquals(null, badges.get(0).plate());
        assertEquals("200X120", badges.get(0).text());
    }

    @Test
    public void audioBadgeCarriesCodecAndRateFacts() {
        AudioPlaybackDiagnostics.Track audio = new AudioPlaybackDiagnostics.Track("E-AC-3", 6, 48000, "en", 640000);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, audio, null, null);

        assertEquals(List.of(Group.AUDIO, Group.CHANNELS), groups(badges));
        assertEquals(Arrays.asList("ga_dolby_digital_plus", "gc_5_1"), plates(badges));
        assertEquals(List.of("48KHZ 640KBPS"), texts(badges));
    }

    @Test
    public void audioBadgeOmitsFactsThatAreMissing() {
        AudioPlaybackDiagnostics.Track audio = new AudioPlaybackDiagnostics.Track("AAC", 2, 0, "", 0);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, audio, null, null);

        assertEquals(Arrays.asList("ga_aac", "gc_2_0"), plates(badges));
        assertEquals(List.of(), texts(badges));
    }

    @Test
    public void audioBadgeWithoutAPlateKeepsTheCodecAsText() {
        AudioPlaybackDiagnostics.Track audio = new AudioPlaybackDiagnostics.Track("Vorbis", 2, 0, "", 0);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, audio, null, null);

        assertEquals(Arrays.asList((String) null, "gc_2_0"), plates(badges));
        assertEquals(List.of("Vorbis"), texts(badges));
    }

    /** media3 对 MP4 里的 AAC 上报 RFC 6381 串 mp4a.40.2，徽标必须归一成品牌名。 */
    @Test
    public void mp4aCodecIsShownAsAac() {
        AudioPlaybackDiagnostics.Track audio = new AudioPlaybackDiagnostics.Track("mp4a.40.2", 2, 44100, "", 64000);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, audio, null, null);

        assertEquals("ga_aac", badges.get(0).plate());
        assertEquals(List.of("44.1KHZ 64KBPS"), texts(badges));
    }

    @Test
    public void containerComesFromTheMimeType() {
        Format video = new Format.Builder().setContainerMimeType("video/x-matroska").build();
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video, null, null, null, null);

        assertEquals(List.of("MKV"), texts(badges));
        assertEquals(List.of(Group.CONTAINER), groups(badges));
    }

    @Test
    public void streamingManifestNeverProducesAContainerBadge() {
        Format video = new Format.Builder().setContainerMimeType("application/x-mpegURL").build();

        assertEquals(List.of(), texts(MediaBadgeResolver.resolve(video, null, null, "https://example.com/list/index.m3u8", null)));
        assertEquals(List.of(), texts(MediaBadgeResolver.resolve(null, null, null, "https://example.com/list/index.mpd", null)));
    }

    @Test
    public void missingMimeTypeFallsBackToTheFileExtension() {
        assertEquals(List.of("MKV"), texts(MediaBadgeResolver.resolve(null, null, null, "https://example.com/movie.mkv", null)));
        assertEquals(List.of(), texts(MediaBadgeResolver.resolve(null, null, null, "https://example.com/play/123456", null)));
        assertEquals(List.of("MKV"), texts(MediaBadgeResolver.resolve(null, null, null, "https://example.com/movie.mkv?token=abc.ts", null)));
    }

    @Test
    public void emptyFactsProduceNoBadges() {
        assertEquals(List.of(), MediaBadgeResolver.resolve(null, null, null, null, null));
        assertEquals(List.of(), MediaBadgeResolver.resolve(new Format.Builder().build(),
                PlayerEngine.VideoPlaybackDetails.empty(), AudioPlaybackDiagnostics.Track.empty(), "", ""));
    }

    @Test
    public void duplicateFactsAreCollapsed() {
        Format video = video(3840, 2160, "dvhe.07.06", 0f, 0, C.COLOR_TRANSFER_SDR);
        PlayerEngine.VideoPlaybackDetails details = new PlayerEngine.VideoPlaybackDetails(
                "dvhe.07.06", 7, 6, "dvhe.07.06", "c2.qti.hevc.decoder", "", null, false);
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video, details, null, null, null);

        assertEquals(Arrays.asList("gr_4k", "gv_dolby_vision", "ge_hevc"), plates(badges));
        assertEquals(List.of("P7"), texts(badges));
    }

    /**
     * 文件名事实：发布来源与画质只能由片源名证明，运行时事实给不了。
     * 这是「全量实现参考徽标」的核心用例。
     */
    @Test
    public void fileNameSuppliesSourceAndQualityPlates() {
        String url = "https://example.com/Movie.2024.2160p.WEB-DL.DV.HDR10+.DDP5.1.Atmos.HEVC-FraMeSToR.mkv";
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, null, url, null);
        List<String> plates = plates(badges);

        assertTrue(plates.contains("gq_web_dl"));
        assertTrue(plates.contains("ga_dolby_atmos_digital_plus"));
        // 运行时事实缺失时，文件名里写明的分辨率/编码/声道也要补上。
        assertTrue(plates.contains("gr_4k"));
        assertTrue(plates.contains("ge_hevc"));
        assertTrue(plates.contains("gc_5_1"));
        // 非运行时组每个分组最多一枚，不会把整行 OSD 塞满。
        assertFalse(plates.contains("gr_1080p"));
    }

    @Test
    public void remuxBluRayAndSpecialTagsComeFromTheFileName() {
        // REMUX 牌靠发布组名辨认，与参考实现一致：没有已知发布组就只剩 Remux 画质牌。
        String url = "https://example.com/Movie.2024.2160p.UHD.BluRay.REMUX.HDR10.DTS-X.TrueHD.7.1.Atmos.PROPER-FraMeSToR.mkv";
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, null, url, null);
        List<String> plates = plates(badges);

        assertTrue(plates.contains("gms_remux_1"));
        assertTrue(plates.contains("gst_proper"));
        assertTrue(plates.contains("gc_7_1"));
        assertEquals(1, countGroup(badges, Group.SPECIAL_TAG));
        assertEquals(1, countGroup(badges, Group.MEDIA_SOURCE));
    }

    @Test
    public void streamingPlatformIsRecognisedFromTheReleaseName() {
        String url = "https://example.com/Anime.S02E03.1080p.CR.WEB-DL.AAC2.0.H.264.mkv";
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, null, url, null);

        assertTrue(plates(badges).contains("gs_crunchyroll"));
    }

    @Test
    public void imaxEnhancedIsRecognisedFromTheTitle() {
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/play/9988", "IMAX Enhanced 演示片");
        assertTrue(plates(badges).contains("imax_imax_enhanced"));
    }

    /** 运行时事实优先：真的在播 1080p 时，不能因为文件名写了 2160p 就显示 4K。 */
    @Test
    public void runtimeFactsWinOverTheFileName() {
        String url = "https://example.com/Movie.2024.2160p.WEB-DL.HEVC.mkv";
        Format video = video(1920, 1080, "video/avc", 0f, 0, C.COLOR_TRANSFER_SDR);
        List<String> plates = plates(MediaBadgeResolver.resolve(video, null, null, url, null));

        assertTrue(plates.contains("gr_1080p"));
        assertFalse(plates.contains("gr_4k"));
        assertTrue(plates.contains("ge_avc"));
        assertFalse(plates.contains("ge_hevc"));
    }

    /** 普通直链不带任何版本标签时，只出运行时事实，不凭空编造来源牌。 */
    @Test
    public void plainUrlAddsNoSourcePlates() {
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(
                video(1920, 1080, "video/hevc", 0f, 0, C.COLOR_TRANSFER_SDR), null, null,
                "https://example.com/path/plain/index.m3u8", null);

        assertEquals(2, badges.size());
        assertEquals(Arrays.asList("gr_1080p", "ge_hevc"), plates(badges));
    }


    /** 扩充包：8K 分辨率、12bit 位深、VVC 编码都来自播放器运行时事实。 */
    @Test
    public void expansionRuntimeFactsAreRecognised() {
        Format video = new Format.Builder()
                .setWidth(7680)
                .setHeight(4320)
                .setCodecs("video/vvc")
                .setColorInfo(new ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_SDR).setLumaBitdepth(12).setChromaBitdepth(12).build())
                .build();
        List<String> plates = plates(MediaBadgeResolver.resolve(video, null, null, null, null));

        assertTrue(plates.contains("gr_8k"));
        assertFalse(plates.contains("gr_4k"));
        assertTrue(plates.contains("gv_12bit"));
        assertTrue(plates.contains("ge_vvc"));
    }

    /** 扩充包：中文版别、HDR Vivid、3D、高码率、ISO 原盘镜像只由片源名证明。 */
    @Test
    public void expansionFileNameFactsAreRecognised() {
        List<String> edition = plates(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.港版.1080p.BluRay.mkv", null));
        assertTrue(edition.contains("ged_hk"));
        assertEquals(1, countGroup(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.港版.台版.1080p.mkv", null), Group.EDITION));

        // 非运行时分组每组只留一枚，HDR Vivid 排在 3D 之前，同一文件名里只出前者。
        List<String> videoTech = plates(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.HDR.Vivid.3D.1080p.mkv", null));
        assertTrue(videoTech.contains("gvf_hdrvivid"));
        assertFalse(videoTech.contains("gvf_3d"));
        assertTrue(plates(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.3D.1080p.mkv", null)).contains("gvf_3d"));

        assertTrue(plates(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.高码率.1080p.mkv", null)).contains("gms_high_bitrate"));
        assertTrue(plates(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.1080p.ISO", null)).contains("gms_iso"));
    }

    /** 扩充包：LPCM / 无损与高度声道只能靠文件名兜底，运行时 Track 给不出这些事实。 */
    @Test
    public void expansionAudioFactsComeFromTheFileName() {
        List<String> lossless = plates(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.1080p.LPCM.2.0.mkv", null));
        assertTrue(lossless.contains("ga_lpcm"));

        List<String> heightChannels = plates(MediaBadgeResolver.resolve(null, null, null,
                "https://example.com/Movie.2024.1080p.TrueHD.7.1.4.Atmos.mkv", null));
        assertTrue(heightChannels.contains("gc_7_1_4"));
        assertFalse(heightChannels.contains("gc_7_1"));
    }

    /** 全表自检：每一条牌的图形键都能在 Catalog 里找到，且运行时标记与分组一致。 */
    @Test
    public void catalogIsConsistent() {
        MediaBadgeCatalog.Rule[] rules = MediaBadgeCatalog.rules();
        assertEquals(127, rules.length);
        assertTrue(MediaBadgeCatalog.patterns().length == rules.length);
        List<String> keys = new ArrayList<>();
        for (MediaBadgeCatalog.Rule rule : rules) {
            keys.add(rule.plate());
            boolean runtimeGroup = rule.group() == Group.RESOLUTION || rule.group() == Group.DYNAMIC_RANGE
                    || rule.group() == Group.VIDEO_CODEC || rule.group() == Group.AUDIO || rule.group() == Group.CHANNELS;
            assertEquals(rule.plate(), runtimeGroup, rule.runtime());
        }
        assertEquals(keys.size(), keys.stream().distinct().count());
    }

    private static int countGroup(List<MediaBadgeResolver.MediaBadge> badges, Group group) {
        int count = 0;
        for (MediaBadgeResolver.MediaBadge badge : badges) if (badge.group() == group) count++;
        return count;
    }

    /** 牌已写明的空文本不参与断言。 */
    private static List<String> texts(List<MediaBadgeResolver.MediaBadge> badges) {
        List<String> values = new ArrayList<>();
        for (MediaBadgeResolver.MediaBadge badge : badges) {
            if (badge.text() == null || badge.text().isEmpty()) continue;
            values.add(badge.text());
        }
        return values;
    }

    private static List<String> plates(List<MediaBadgeResolver.MediaBadge> badges) {
        List<String> values = new ArrayList<>();
        for (MediaBadgeResolver.MediaBadge badge : badges) values.add(badge.plate());
        return values;
    }

    private static List<Group> groups(List<MediaBadgeResolver.MediaBadge> badges) {
        List<Group> values = new ArrayList<>();
        for (MediaBadgeResolver.MediaBadge badge : badges) values.add(badge.group());
        return values;
    }

    private static Format video(int width, int height, String codecs, float frameRate, int bitrate, int colorTransfer) {
        Format.Builder builder = new Format.Builder()
                .setWidth(width)
                .setHeight(height)
                .setCodecs(codecs)
                .setColorInfo(new ColorInfo.Builder().setColorTransfer(colorTransfer).build());
        // media3 的 setFrameRate 只接受正数或 NO_VALUE；0 表示测试里没有帧率事实。
        if (frameRate > 0) builder.setFrameRate(frameRate);
        if (bitrate > 0) builder.setAverageBitrate(bitrate);
        return builder.build();
    }
}
