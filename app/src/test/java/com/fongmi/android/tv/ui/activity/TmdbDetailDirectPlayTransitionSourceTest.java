package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TmdbDetailDirectPlayTransitionSourceTest {

    private static final Path SOURCE = Paths.get("src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

    @Test
    public void firstDirectPlayEntersFullscreenBeforeStartingPlayback() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String playMethod = methodBody(source, "private void playDetailFullscreen()");

        int enterFullscreen = playMethod.indexOf("enterInlineFullscreen();");
        int startPlayback = playMethod.indexOf("if (!current) playInline();");
        assertTrue(enterFullscreen >= 0);
        assertTrue(startPlayback > enterFullscreen);
        assertTrue(playMethod.contains("detailPlayerFullscreenPending = false;"));
        assertFalse(playMethod.contains("detailPlayerFullscreenPending = !current;"));
        assertFalse(playMethod.contains("revealDetailPlayerFullscreen();"));
        assertTrue(source.contains("protected void onFirstFrameRendered()"));
    }

    @Test
    public void closingDirectPlayClearsPendingTransitionState() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String closeMethod = methodBody(source, "private void closeDetailFullscreenPlayer()");

        assertTrue(closeMethod.contains("detailPlayerFullscreenPending = false;"));
        assertTrue(closeMethod.contains("inlinePlaybackGeneration++;"));
        assertTrue(closeMethod.contains("currentInlineResult = null;"));
    }

    @Test
    public void directPlaybackSnapshotUsesLanguageIdentityAndUnifiedOverviewPolicy() throws Exception {
        String source = Files.readString(
                Paths.get("src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("TmdbDetailCache.take(getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), getTmdbItem(), currentTmdbLanguage())"));
        assertFalse(source.contains("TmdbDetailCache.take(getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), getTmdbItem())"));
        assertTrue(source.contains("translatedOverview(detail, currentTmdbConfig())"));
        assertFalse(source.contains("cachedTmdbOverviewForLanguage(translations, \"zh-CN\")"));
    }

    /**
     * 被作废的取流请求必须留着释放路径，不能留下永久加载标记。
     *
     * <p>三条作废路径都没有结果回调可用来收圈：换内核/打开外部播放器会自增代际并取消任务
     * （回调可能根本不执行），选择面变更（详情重载/外部播放返回）会让回调因请求失效直接返回。
     * 标记残留会让详情页加载圈永久留在屏上（见 {@code updateInlineLoading} 的首个条件），
     * 并让 {@code isSamePendingInlinePlayback} 恒真、同一集无法再次起播。
     */
    @Test
    public void cancellingAnInlinePlaybackRequestReleasesItsLoadingFlag() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        // 自增代际、不接替请求的两处：必须在投递新请求之前同步释放
        String switchMethod = methodBody(source, "private boolean refreshAndSwitchInlinePlayer(int playerType)");
        assertFalse("refreshAndSwitchInlinePlayer must exist", switchMethod.isEmpty());
        int switchGeneration = switchMethod.indexOf("++inlinePlaybackGeneration");
        int switchRelease = switchMethod.indexOf("releaseInlinePlaybackPending();");
        int switchSubmit = switchMethod.indexOf("detailTasks.submit(");
        assertTrue("refreshAndSwitchInlinePlayer must release the loading flag of the request it invalidates",
                switchGeneration >= 0 && switchRelease > switchGeneration);
        assertTrue("the release must happen synchronously, before the replacement request is submitted",
                switchSubmit > switchRelease);

        String cancelMethod = methodBody(source, "private void cancelPendingInlinePlayerSwitch()");
        assertFalse("cancelPendingInlinePlayerSwitch must exist", cancelMethod.isEmpty());
        assertTrue("cancelPendingInlinePlayerSwitch must release the loading flag of the request it invalidates",
                cancelMethod.contains("inlinePlaybackGeneration++;")
                        && cancelMethod.contains("releaseInlinePlaybackPending();"));

        // 选择面变更不自增代际，只能由回调按归属释放
        String playMethod = methodBody(source, "private void playInline(long resumePosition, String failedUrl, String failureMessage)");
        assertFalse("playInline must exist", playMethod.isEmpty());
        int markGeneration = playMethod.indexOf("++inlinePlaybackGeneration");
        assertTrue("playInline must record the generation that owns the pending flag",
                markGeneration >= 0
                        && playMethod.indexOf("inlinePlaybackPendingGeneration = generation;", markGeneration) > markGeneration);
        int firstStaleRelease = playMethod.indexOf("releaseInlinePlaybackPending(generation);");
        assertTrue("both stale branches (success and failure) must release the flag they own",
                firstStaleRelease >= 0
                        && playMethod.indexOf("releaseInlinePlaybackPending(generation);", firstStaleRelease + 1) > firstStaleRelease);

        String ownedRelease = methodBody(source, "private void releaseInlinePlaybackPending(int generation)");
        assertFalse("the owned release must exist", ownedRelease.isEmpty());
        assertTrue("the owned release must refuse to clear a newer request's flag",
                ownedRelease.contains("inlinePlaybackPendingGeneration != generation"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        return "";
    }
}
