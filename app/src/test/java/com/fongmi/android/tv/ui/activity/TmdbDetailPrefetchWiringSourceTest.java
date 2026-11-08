package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 锁定 TMDB 详情页的 W1 契约（`docs/C16-tmdb-source-detail-contract.md` §6.3）：
 * identity 已知时 TMDB 详情在站点详情之前并行预热；预热结果只在需要 TMDB 详情的路径被消费，
 * 源 payload 路径丢弃；页面销毁/重载/换源时取消单槽。
 */
public class TmdbDetailPrefetchWiringSourceTest {

    private static final Path SOURCE =
            Paths.get("src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

    @Test
    public void tmdbDetailPrefetchStartsBeforeTheSiteDetailRequest() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String loadContent = methodBody(source, "private void loadContent(");

        int startPrefetch = loadContent.indexOf("startTmdbDetailPrefetch(initialTmdbItem, reusableBundle);");
        int submit = loadContent.indexOf("detailTasks.submit(");
        assertTrue("prefetch start must exist", startPrefetch >= 0);
        assertTrue("prefetch must start before the worker that performs SiteApi.detailContent", submit > startPrefetch);

        String starter = methodBody(source, "private void startTmdbDetailPrefetch(");
        assertFalse("startTmdbDetailPrefetch must exist", starter.isEmpty());
        assertTrue("must not prefetch without a known identity or with a reusable bundle",
                starter.contains("if (item == null || reusableBundle != null) return;"));
        assertTrue("must require a ready TMDB config", starter.contains("!config.isReady()"));
        assertTrue("must respect the per-site TMDB switch", starter.contains("isTmdbAllowedForCurrentSite()"));
        assertTrue("prefetch must use the shared single-slot prefetch",
                starter.contains("detailPrefetch.start(item,"));
        assertTrue("prefetch must load the detail by identity", starter.contains("tmdbService.detail(item, config)"));
    }

    @Test
    public void identityPathConsumesThePrefetchedDetailInsteadOfRequestingAgain() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String loadResult = methodBody(source, "private TmdbLoadResult loadTmdbResult()");

        assertTrue("identity path must consume the prefetch",
                loadResult.contains("tmdbBundle = loadPrefetchedTmdbBundle(initialTmdbItem);"));
        assertFalse("identity path must not issue a second detail request",
                loadResult.contains("loadTmdbBundle(initialTmdbItem)"));

        String consume = methodBody(source, "private TmdbBundle loadPrefetchedTmdbBundle(");
        assertFalse("loadPrefetchedTmdbBundle must exist", consume.isEmpty());
        assertTrue("must take the single-slot future for this identity",
                consume.contains("detailPrefetch.take(item)"));
        assertTrue("missing/unusable prefetch must fall back to the regular fetch",
                consume.contains("return loadTmdbBundle(item);"));
        assertTrue("prefetched details must be assembled by the shared bundle builder",
                consume.contains("toTmdbBundle("));

        String bundle = methodBody(source, "private TmdbBundle loadTmdbBundle(");
        assertTrue("regular fetch must delegate to the shared bundle builder",
                bundle.contains("toTmdbBundle(item, tmdbService.detail(item, tmdbConfig))"));
    }

    @Test
    public void sourcePayloadPathDiscardsThePrefetchAndLifecycleCancelsIt() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String loadContent = methodBody(source, "private void loadContent(");

        int sourceBranch = loadContent.indexOf("if (sourceBundle != null) {");
        int cancel = loadContent.indexOf("detailPrefetch.cancel();", sourceBranch);
        int merge = loadContent.indexOf("TmdbSourceMerger.merge(sourceBundle, sourcePayload, reusableBundle", sourceBranch);
        assertTrue("source payload branch must exist", sourceBranch >= 0);
        assertTrue("source payload path must discard the prefetch", cancel > sourceBranch);
        assertTrue("source payload path must not consume the prefetch before merging", merge > cancel);

        String destroy = methodBody(source, "protected void onDestroy()");
        assertTrue("onDestroy must cancel the in-flight prefetch", destroy.contains("detailPrefetch.cancel();"));
        assertTrue("every load must reset the single slot before starting",
                loadContent.indexOf("detailPrefetch.cancel();") < loadContent.indexOf("startTmdbDetailPrefetch(initialTmdbItem, reusableBundle);"));
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
