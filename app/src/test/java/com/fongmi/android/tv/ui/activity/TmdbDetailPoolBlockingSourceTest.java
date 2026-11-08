package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 锁定 W2：详情页等待 TMDB 结果的阻塞段不得占用共享 5 线程池。
 *
 * <p>{@code Task.executor()} 只有 5 个线程且被首页、搜索、站点加载等共用；详情页一次等待可达
 * 1–3 秒。协调任务只负责发起请求并派发收尾任务，真正阻塞在 {@code tmdbFuture.get()} 的
 * {@code completeTmdbLoad} 必须提交到 {@code Task.largeExecutor()}。
 */
public class TmdbDetailPoolBlockingSourceTest {

    private static final Path SOURCE =
            Paths.get("src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

    @Test
    public void coordinatorDoesNotBlockTheSharedPoolOnTheTmdbFuture() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String loadContent = methodBody(source, "private void loadContent(");

        assertFalse("loadContent must exist", loadContent.isEmpty());
        assertFalse("the coordinator must not wait on the TMDB future itself",
                loadContent.contains("tmdbFuture.get()"));
        assertTrue("the waiting continuation must be dispatched to the large executor",
                loadContent.contains("detailTasks.submit(Task.largeExecutor(),"));
        assertTrue("the dispatched continuation must be completeTmdbLoad",
                loadContent.contains("completeTmdbLoad(generation, mode, finalVod, finalError, singlePassStandaloneTmdb, tmdbFuture, decision, loadStart)"));
    }

    @Test
    public void continuationKeepsGenerationAndCredentialGuards() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String complete = methodBody(source, "private void completeTmdbLoad(");

        assertFalse("completeTmdbLoad must exist", complete.isEmpty());
        assertTrue("the continuation owns the blocking wait", complete.contains("tmdbFuture.get()"));
        assertTrue("every UI handoff must still check the load generation",
                count(complete, "generation != loadGeneration") >= 4);
        assertTrue("the single-pass branch must still preload the initial season",
                complete.contains("preloadInitialStandaloneSeason(loadedResult, finalVod)"));
        assertTrue("the failure path must still fall back to the source detail",
                complete.contains("tmdb wait failed"));
    }

    private static int count(String text, String token) {
        int total = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) total++;
        return total;
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
