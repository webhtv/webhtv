package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The video half of the "hard decode has no hardware path" proof, plus the source guards that keep
 * the three participants of that contract aligned: the hardware predicate, the decoder list the
 * hard-decode renderers are built with, and the verdict the OSD prints.
 */
public class ExoVideoHardwareDecodeTest {

    private static final String VIDEO_MIME = "video/hevc";
    private static final String EXO_UTIL_PATH =
            "app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java";
    private static final String PLAYER_MANAGER_PATH =
            "app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java";
    private static final String OSD_PATH =
            "app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java";

    @Test
    public void softwareOnlyDecoderThatClaimsHardware_isNotAHardwarePath() {
        assertFalse(ExoUtil.hasHardwareVideoDecoder(
                fixed(List.of(codec("c2.vendor.hevc.decoder", true, true))), VIDEO_MIME));
    }

    @Test
    public void hardwareDecoder_isAHardwarePath() {
        assertTrue(ExoUtil.hasHardwareVideoDecoder(
                fixed(List.of(codec("c2.vendor.hevc.decoder", true, false))), VIDEO_MIME));
    }

    @Test
    public void allSoftwareDecoders_areNotAHardwarePath() {
        assertFalse(ExoUtil.hasHardwareVideoDecoder(
                fixed(List.of(
                        codec("c2.android.hevc.decoder", false, true),
                        codec("omx.google.hevc.decoder", false, true))),
                VIDEO_MIME));
    }

    @Test
    public void emptyDecoderList_isNotEvidenceOfSoftwareFallback() {
        assertTrue(ExoUtil.hasHardwareVideoDecoder(fixed(List.of()), VIDEO_MIME));
    }

    @Test
    public void nullAndNonVideoMime_areNotThisChecksBusiness() {
        assertTrue(ExoUtil.hasHardwareVideoDecoder(fixed(List.of()), null));
        assertTrue(ExoUtil.hasHardwareVideoDecoder(fixed(List.of()), "audio/mp4a-latm"));
    }

    @Test
    public void failedQuery_keepsTheCurrentMode() throws Exception {
        // DecoderQueryException has no accessible constructor, so this branch is guarded at the
        // source level the way the other untestable branches in this area already are.
        String source = readSource(EXO_UTIL_PATH);

        assertTrue(source.contains("} catch (androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException e) {"));
        assertTrue(source.contains("// A failed query must never change the user-visible decode mode."));
    }

    @Test
    public void videoSelector_reusesTheSharedHardwarePredicate() throws Exception {
        String source = readSource(EXO_UTIL_PATH);

        assertTrue(source.contains("return info.hardwareAccelerated && !info.softwareOnly;"));
        assertEquals(2, occurrences(source, "isHardwareDecoder(info)"));
        assertFalse(source.contains("if (info.hardwareAccelerated) return true;"));
        assertFalse(source.contains("if (info.hardwareAccelerated) hardwareInfos.add(info);"));
    }

    @Test
    public void platformSoftwareFallback_isGatedByTheDecoderFallbackSwitch() throws Exception {
        String source = readSource(EXO_UTIL_PATH);

        assertTrue(source.contains("hardwareInfos.isEmpty() && PlaybackPerformanceSetting.isDecoderFallbackEnabled() ? infos : hardwareInfos"));
    }

    @Test
    public void autoFallback_decidesOnTheSelectedVideoTrack() throws Exception {
        String source = readSource(PLAYER_MANAGER_PATH);

        assertTrue(source.contains("Format selected = getSelectedFormat(tracks, C.TRACK_TYPE_VIDEO);"));
        assertTrue(source.contains("return !ExoUtil.hasHardwareVideoDecoder(selected.sampleMimeType);"));
        assertTrue(source.contains("return hasTrack(tracks, C.TRACK_TYPE_VIDEO) && !hasHandledTrack(tracks, C.TRACK_TYPE_VIDEO);"));
        assertFalse(source.contains("firstDecodableVideoFormat"));
    }

    @Test
    public void videoVerdict_coversEveryUndecodableSupportCode() throws Exception {
        String source = readSource(OSD_PATH);

        assertTrue(source.contains("!videoTrack.selected() && !videoTrack.isHandled()"));
        assertTrue(source.contains("supportText(videoTrack.support())"));
        assertTrue(source.contains("C.FORMAT_UNSUPPORTED_DRM"));
    }

    private static MediaCodecSelector fixed(List<MediaCodecInfo> infos) {
        return (mimeType, secure, tunneling) -> infos;
    }

    private static MediaCodecInfo codec(
            String name, boolean hardwareAccelerated, boolean softwareOnly) {
        return MediaCodecInfo.newInstance(
                name,
                VIDEO_MIME,
                VIDEO_MIME,
                null,
                hardwareAccelerated,
                softwareOnly,
                !softwareOnly,
                false,
                false);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static String readSource(String relativePath) throws Exception {
        File root = new File(System.getProperty("user.dir"));
        while (root != null && !new File(root, relativePath).isFile()) root = root.getParentFile();
        assertNotNull("Unable to locate project root from " + System.getProperty("user.dir"), root);
        return Files.readString(new File(root, relativePath).toPath(), StandardCharsets.UTF_8);
    }
}
