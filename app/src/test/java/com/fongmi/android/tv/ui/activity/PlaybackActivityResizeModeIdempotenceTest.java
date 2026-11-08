package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 进入播放页时 {@code applyResizeMode} 会重复执行 14-16 次：每次调用自己先跑一次
 * {@code applyResizeModeNow}，再 post 一个二次调用，而 {@code onPlayerRenderRequired} /
 * {@code onPlayerRebuild} 又会各调一次。每次执行都请求两次 layout 并打两行长日志。
 * 这些测试锁住「参数没变就直接返回」这条幂等约束，同时锁住真正的应用动作没被删掉。
 */
public class PlaybackActivityResizeModeIdempotenceTest {

    @Test
    public void repeatedApplyWithTheSameParametersDoesNothing() throws Exception {
        String source = readSource();
        String body = methodBody(source, "private void applyResizeModeNow(int resizeMode)");
        int guard = body.indexOf("if (isResizeModeApplied(");
        assertTrue("applyResizeModeNow must consult the applied-parameter guard", guard >= 0);
        int before = body.indexOf("logSurfaceState(\"applyResizeMode before");
        int setAspect = body.indexOf("setVideoAspect(");
        int requestLayout = body.indexOf("view.requestLayout();");
        assertTrue("the guard must run before the surface-state log", before > guard);
        assertTrue("the guard must run before the player aspect update", setAspect > guard);
        assertTrue("the guard must run before the layout requests", requestLayout > guard);
        assertTrue("the real resize must still be applied when parameters change", requestLayout >= 0);
    }

    @Test
    public void theGuardComparesEveryParameterThatChangesTheSurface() throws Exception {
        String source = readSource();
        String body = methodBody(source, "private boolean isResizeModeApplied(");
        assertTrue("resize mode must be part of the identity", body.contains("resizeAppliedMode == resizeMode"));
        assertTrue("effective resize mode must be part of the identity", body.contains("resizeAppliedEffectiveMode == effectiveResizeMode"));
        assertTrue("a float aspect ratio must be compared by bits, not by ==", body.contains("Float.floatToIntBits(targetAspectRatio)"));
        assertTrue("the resolved content aspect ratio must be part of the identity", body.contains("Float.floatToIntBits(contentAspectRatio)"));
        assertTrue("stretch must be part of the identity", body.contains("resizeAppliedStretch == stretch"));
        assertTrue("the guard must report a hit only when everything matches", body.contains("if (resizeApplied &&"));
    }

    @Test
    public void surfaceRebindsInvalidateTheGuard() throws Exception {
        String source = readSource();
        String decoderSwitch = methodBody(source, "private void resetVideoSurfaceForDecoderSwitch()");
        assertTrue("a decoder switch forces a rebind, so the cached resize state must be dropped",
                decoderSwitch.contains("invalidateAppliedResizeMode();"));
        String attach = methodBody(source, "private void attachSurface(boolean restoreExoShutter)");
        assertTrue("attaching a new surface must drop the cached resize state",
                attach.contains("invalidateAppliedResizeMode();"));
        int setPlayer = attach.indexOf("setPlayer(");
        int invalidate = attach.indexOf("invalidateAppliedResizeMode();");
        assertTrue("the cache must be dropped right after the surface binding is replaced", invalidate > setPlayer);
    }

    @Test
    public void theDeferredSecondPassIsKept() throws Exception {
        String body = methodBody(readSource(), "protected void applyResizeMode(int resizeMode)");
        assertTrue("applyResizeMode must still schedule the post-layout second pass",
                body.contains("view.post(") && body.contains("requestedAspectMode == mode"));
        assertFalse("applyResizeMode must not loop on itself", body.contains("applyResizeMode(mode);"));
    }

    private static String readSource() throws Exception {
        Path moduleRelative = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java");
        Path path = Files.exists(moduleRelative) ? moduleRelative : Path.of("app").resolve(moduleRelative);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("source is missing " + signature, start >= 0);
        int open = source.indexOf('{', start + signature.length());
        assertTrue("source is missing the body of " + signature, open > start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("unclosed body for " + signature);
    }
}
