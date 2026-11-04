package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视端「打开历史播放」走的是 TmdbDetailActivity 的内联播放，不是 leanback VideoActivity。
 * 设置里的播放内核与历史记录记住的内核不一致时，startInlinePlayer() 里的 preparePlayer() 会在
 * PlaySpec 建立之前重建引擎，那一刻归属判定为假，PlaybackActivity.onPlayerRebuild() 整段跳过
 * （其中包含 setProgressPlayer），进度条便继续读已释放的旧引擎，左右都显示 00:00。
 * 这里钉住内联起播这条自持路径也必须在内核重建后显式跟上新引擎。
 */
public class TmdbDetailSeekProgressSourceTest {

    @Test
    public void inlineKernelRebuildRebindsSeekView() throws Exception {
        String body = methodBody(readActivity(), "private void startInlinePlayer(Result result, long resumePosition)");

        int prepare = body.indexOf("player().preparePlayer(inlineHistoryPlayerKernel());");
        int rebind = body.indexOf("getSeekView().setProgressPlayer(player().getPlayer());", prepare);
        int start = body.indexOf("startPlayer(getHistoryKey(), result,", prepare);

        assertTrue("startInlinePlayer must still prepare the remembered kernel", prepare >= 0);
        assertTrue("the inline seek view must be rebound to the rebuilt engine, otherwise the TV progress bar "
                + "keeps reading the released player and shows 00:00", rebind > prepare);
        assertTrue("the rebind must happen before startPlayer() establishes the new PlaySpec, because "
                + "onPlayerRebuild() runs while the ownership key is still empty", start > rebind);
    }

    @Test
    public void pendingInlinePlaybackReusesTheSameStartPath() throws Exception {
        String activity = readActivity();
        String body = methodBody(activity, "private void startPendingInlinePlayer()");

        assertTrue("a delayed inline start must keep delegating to startInlinePlayer(), so the rebind above "
                + "also covers the pending branch", body.contains("startInlinePlayer(pendingInlineResult)"));

        String pending = methodBody(activity, "private void restoreInlinePlaybackIfNeeded()");
        assertTrue("resume-after-service-reconnect must also go through startInlinePlayer()",
                pending.contains("startInlinePlayer(currentInlineResult"));
    }

    @Test
    public void inlineRebindGoesThroughTheSharedSeekAccessor() throws Exception {
        String activity = readActivity();
        assertTrue("getSeekView() must keep resolving the inline seek view",
                methodBody(activity, "protected CustomSeekView getSeekView()").contains("return inlineSeek();"));
        assertFalse("the inline rebind must go through getSeekView() rather than binding the seek view directly",
                methodBody(activity, "private void startInlinePlayer(Result result, long resumePosition)")
                        .contains("binding.seek"));
    }

    private static String readActivity() throws Exception {
        Path path = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing method: " + signature, start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue("unterminated method: " + signature, end > start);
        return source.substring(start, end);
    }
}
