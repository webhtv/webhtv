package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TrackUtilTest {

    @Test
    public void returnsTheOnlySelectedFormat() {
        Format selected = video("video/avc", 1920, 1080);

        assertEquals(selected, TrackUtil.onlySelectedFormat(List.of(selected)));
    }

    @Test
    public void adaptiveSelectionWithMultipleCandidatesStaysUnknown() {
        Format low = video("video/avc", 1280, 720);
        Format high = video("video/avc", 3840, 2160);

        assertNull(TrackUtil.onlySelectedFormat(List.of(low, high)));
    }

    /**
     * 自适应选轨会把整组候选都标成 selected（视轨列表因此整列高亮）。播放器报告的
     * 正在解码格式必须把范围收敛回快照里的那一条，且返回的必须是快照内的实例——
     * TrackDialog 按引用比对，equals 会把同规格的两条轨道一起点亮。
     */
    @Test
    public void uniqueActiveFormatCollapsesAdaptiveCandidatesToTheDecodingRendition() {
        Format low = rendition("v-low", 426, 240, 550_000);
        Format mid = rendition("v-mid", 854, 480, 1_570_000);
        Format high = rendition("v-high", 1280, 720, 2_970_000);
        Tracks tracks = adaptiveVideo(low, mid, high);
        Format decoding = rendition("v-high", 1280, 720, 2_970_000);

        Format active = TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, decoding);

        assertSame("must return the snapshot instance, not the player's copy", high, active);
    }

    @Test
    public void uniqueActiveFormatLeavesAnUnambiguousSelectionAlone() {
        Format low = rendition("v-low", 426, 240, 550_000);
        Format high = rendition("v-high", 1280, 720, 2_970_000);
        Tracks tracks = new Tracks(List.of(new Tracks.Group(
                new TrackGroup(low, high), true,
                new int[]{C.FORMAT_HANDLED, C.FORMAT_HANDLED},
                new boolean[]{false, true})));

        assertNull("a single selected candidate needs no narrowing",
                TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, rendition("v-high", 1280, 720, 2_970_000)));
    }

    /** 匹配不上时返回 null，让调用方退回播放器原始标记，而不是让列表一项都不高亮。 */
    @Test
    public void uniqueActiveFormatFallsBackWhenTheDecodingRenditionIsNotInTheCandidates() {
        Tracks tracks = adaptiveVideo(
                rendition("v-low", 426, 240, 550_000),
                rendition("v-mid", 854, 480, 1_570_000));

        assertNull(TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, rendition("v-4k", 3840, 2160, 15_000_000)));
    }

    @Test
    public void uniqueActiveFormatIgnoresOtherTrackTypes() {
        Format video = rendition("v-high", 1280, 720, 2_970_000);
        Tracks tracks = new Tracks(List.of(new Tracks.Group(
                new TrackGroup(video), true,
                new int[]{C.FORMAT_HANDLED},
                new boolean[]{true})));

        assertNull(TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_AUDIO, rendition("v-high", 1280, 720, 2_970_000)));
    }

    @Test
    public void sameRenditionMatchesOnWidthHeightAndBitrate() {
        assertTrue(TrackUtil.sameRendition(
                rendition("v-high", 1280, 720, 2_970_000),
                rendition("other-id", 1280, 720, 2_970_000)));
    }

    @Test
    public void sameRenditionMatchesOnTrackIdEvenWhenTheMetadataDisagrees() {
        assertTrue(TrackUtil.sameRendition(
                rendition("v-high", 1280, 720, 2_970_000),
                rendition("v-high", 1920, 1080, 4_000_000)));
    }

    @Test
    public void sameRenditionRejectsTheSameResolutionWithADifferentCodec() {
        assertFalse("同一分辨率可能同时提供 H.264 与 HEVC 两条轨道，编码能比就必须不一致",
                TrackUtil.sameRendition(
                        rendition("v-avc", 1280, 720, 2_970_000, MimeTypes.VIDEO_H264),
                        rendition("v-hevc", 1280, 720, 2_970_000, MimeTypes.VIDEO_H265)));
    }

    @Test
    public void sameRenditionRejectsADifferentBitrateAtTheSameResolution() {
        assertFalse(TrackUtil.sameRendition(
                rendition("v-high", 1280, 720, 2_970_000),
                rendition("v-low", 1280, 720, 1_570_000)));
    }

    private static Tracks adaptiveVideo(Format... formats) {
        boolean[] selected = new boolean[formats.length];
        int[] support = new int[formats.length];
        for (int i = 0; i < formats.length; i++) {
            selected[i] = true;
            support[i] = C.FORMAT_HANDLED;
        }
        return new Tracks(List.of(new Tracks.Group(new TrackGroup(formats), true, support, selected)));
    }

    private static Format rendition(String id, int width, int height, int bitrate) {
        return rendition(id, width, height, bitrate, MimeTypes.VIDEO_H264);
    }

    private static Format rendition(String id, int width, int height, int bitrate, String mimeType) {
        return new Format.Builder()
                .setId(id)
                .setSampleMimeType(mimeType)
                .setWidth(width)
                .setHeight(height)
                .setAverageBitrate(bitrate)
                .build();
    }

    private static Format video(String mimeType, int width, int height) {
        return new Format.Builder()
                .setSampleMimeType(mimeType)
                .setWidth(width)
                .setHeight(height)
                .build();
    }
}
