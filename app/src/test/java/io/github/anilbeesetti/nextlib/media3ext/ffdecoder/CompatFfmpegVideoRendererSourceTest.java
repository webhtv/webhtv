package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CompatFfmpegVideoRendererSourceTest {

    private static final String SOURCE_PATH = "app/src/main/java/io/github/anilbeesetti/nextlib/media3ext/ffdecoder/CompatFfmpegVideoRenderer.java";
    private static final String EXO_UTIL_PATH = "app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java";

    /**
     * The audio renderer may ask whether the platform supports the exact {@link
     * androidx.media3.common.Format}, but the video renderer must not: a 4K HEVC track that exceeds
     * the decoder's level reports unsupported, which would hand the track back to FFmpeg software
     * decoding and reintroduce the dropped-frame regression.
     */
    @Test
    public void platformOwnershipCheck_asksOnlyAboutMimeTypeNotExactFormat() throws Exception {
        String source = readSource();

        assertTrue(source.contains("getDecoderInfos(mimeType, false, false)"));
        assertFalse(source.contains("isFormatSupported("));
    }

    /**
     * The check must run through the selector the MediaCodec renderers were built with. Hardcoding
     * {@code MediaCodecSelector.DEFAULT} would make FFmpeg yield to a software platform decoder that
     * ExoUtil's hardware-only selector has already filtered out, leaving the track with no renderer
     * at all: a silent black screen with no PlaybackException to trigger any fallback.
     */
    @Test
    public void platformOwnershipCheck_usesTheInjectedSelectorNotTheDefaultOne() throws Exception {
        String source = readSource();

        assertTrue(source.contains("platformDecoderSelector == null ? MediaCodecSelector.DEFAULT : platformDecoderSelector"));
        assertTrue(source.contains("!selector.getDecoderInfos(mimeType, false, false).isEmpty()"));
    }

    /**
     * 硬解 + 「解码器兜底」开（MODE_ON）时，追加的视频回退必须是会「让轨」的
     * {@link CompatFfmpegVideoRenderer}：{@code FfmpegVideoRenderer.supportsFormat} 无条件报
     * {@code FORMAT_HANDLED}，会盖过 MediaCodec 对超出声明等级的 HEVC（例如 L186）给出的
     * {@code FORMAT_EXCEEDS_CAPABILITIES}，于是这类视频在兜底开启时被 FFmpeg 软解抢走，
     * 表现为画面卡顿、声音正常。Compat 版只问平台有没有该 mime 的解码器，有就让轨。
     * 平台根本没有该编码解码器时（AVS2/AVS3）仍由 FFmpeg 接手，所以不会退化成只有声音。
     */
    @Test
    public void hardwareModeWithFallbackAppendsTheYieldingRenderer() throws Exception {
        String exoUtil = readSource(EXO_UTIL_PATH);
        int start = exoUtil.indexOf("private static int getVideoRenderMode(int decode)");
        assertTrue("getVideoRenderMode must exist", start >= 0);
        int end = exoUtil.indexOf("\n    }", start);
        String mode = exoUtil.substring(start, end > start ? end : exoUtil.length());

        // 兜底开启：硬解 = ON（MediaCodec 先接管、FFmpeg 排最后）；兜底关闭：仍为 OFF。
        assertTrue(mode, mode.contains("isDecoderFallbackEnabled()"));
        assertTrue(mode, mode.contains("EXTENSION_RENDERER_MODE_ON"));
        assertTrue(mode, mode.contains("EXTENSION_RENDERER_MODE_OFF"));
        assertTrue(exoUtil.contains("if (videoRenderMode == EXTENSION_RENDERER_MODE_OFF) return;"));
        assertTrue(exoUtil.contains("buildFfmpegVideoRenderer("));
        // ON 分支必须换成让轨版本，并带上与 MediaCodec 渲染器同一个 selector；
        // 其余分支（软解 PREFER）保持原 FfmpegVideoRenderer。
        assertTrue(exoUtil.contains("videoRenderMode == EXTENSION_RENDERER_MODE_ON"));
        assertTrue(exoUtil.contains("new CompatFfmpegVideoRenderer("));
        assertTrue(exoUtil.contains("true, videoCodecSelector"));
        assertTrue(exoUtil.contains("new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)"));
    }

    private static String readSource() throws Exception {
        return readSource(SOURCE_PATH);
    }

    private static String readSource(String relativePath) throws Exception {
        File root = new File(System.getProperty("user.dir"));
        while (root != null && !new File(root, relativePath).isFile()) root = root.getParentFile();
        assertNotNull("Unable to locate project root from " + System.getProperty("user.dir"), root);
        return Files.readString(new File(root, relativePath).toPath(), StandardCharsets.UTF_8);
    }
}
