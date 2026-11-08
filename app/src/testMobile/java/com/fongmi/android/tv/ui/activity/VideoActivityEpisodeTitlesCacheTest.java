package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * mobile 的播放页在进入时会三次调用 {@code getEpisodeTitles()}、两次调用
 * {@code isIntentTmdbPlayback()}，而每次调用都是一次 {@code getStringArrayListExtra}
 * 反序列化，并且各自打一行 Log.d；一部几十集的剧还会被 {@code applyTmdbEpisodeTitles}
 * 逐集再打一行。这些日志和数据解析都落在首帧窗口的主线程上。
 * 这些测试锁住「intent 只读一次 + 日志不再逐集刷屏」，并锁住换条目时缓存必须失效。
 */
public class VideoActivityEpisodeTitlesCacheTest {

    @Test
    public void theIntentExtrasAreReadOnce() throws Exception {
        String source = readSource();
        String body = methodBody(source, "private java.util.Map<Integer, String> getEpisodeTitles()");
        assertTrue("getEpisodeTitles must serve the cached map", body.contains("if (episodeTitlesReady) return episodeTitles;"));
        assertFalse("getEpisodeTitles must not deserialize the intent extras again", body.contains("getIntent().getStringArrayListExtra("));
        String values = methodBody(source, "private java.util.ArrayList<String> episodeTitleValues()");
        assertTrue("the extras must be read through the memoized accessor",
                values.contains("episodeTitleValues = getIntent().getStringArrayListExtra(\"tmdb_episode_titles\");"));
        assertTrue("the extras must be memoized", values.contains("if (!episodeTitleValuesReady)"));
        assertTrue("the hot predicate must use the memoized extras, not a fresh Intent read",
                methodBody(source, "private boolean isIntentTmdbPlayback()").contains("episodeTitleValues()"));
    }

    @Test
    public void switchingItemsInvalidatesTheCache() throws Exception {
        String source = readSource();
        String onNewIntent = methodBody(source, "protected void onNewIntent(Intent intent)");
        int putExtras = onNewIntent.indexOf("getIntent().putExtras(intent);");
        int invalidate = onNewIntent.indexOf("invalidateEpisodeTitlesCache();");
        assertTrue("onNewIntent must replace the intent extras", putExtras >= 0);
        assertTrue("onNewIntent must drop the cached episode titles when the intent changes", invalidate > putExtras);
        String invalidator = methodBody(source, "private void invalidateEpisodeTitlesCache()");
        assertTrue("the invalidator must clear both cached layers",
                invalidator.contains("episodeTitleValuesReady = false;")
                        && invalidator.contains("episodeTitleValues = null;")
                        && invalidator.contains("episodeTitlesReady = false;")
                        && invalidator.contains("episodeTitles = null;"));
    }

    @Test
    public void episodeTitleLoggingIsNoLongerPerEpisode() throws Exception {
        String body = methodBody(readSource(), "private void applyTmdbEpisodeTitles(Vod vod)");
        assertTrue("the applied count must be logged once", body.contains("applyTmdbEpisodeTitles applied="));
        assertFalse("per-episode title logging must not come back", body.contains("\"应用标题: \""));
        assertFalse("the pre-scan count log must not come back", body.contains("\"applyTmdbEpisodeTitles - 集数标题数量: \""));
        assertFalse("reading cached titles must not log on every call",
                methodBody(readSource(), "private java.util.Map<Integer, String> getEpisodeTitles()").contains("Log.d"));
        assertFalse("the predicate must not log on every call",
                methodBody(readSource(), "private boolean isIntentTmdbPlayback()").contains("Log.d"));
    }

    @Test
    public void theCallableMethodNamesAreUnchanged() throws Exception {
        String source = readSource();
        assertTrue("other sources and tests depend on getEpisodeTitles()", source.contains("getEpisodeTitles()"));
        assertTrue("other sources and tests depend on isIntentTmdbPlayback()", source.contains("isIntentTmdbPlayback()"));
        assertTrue("the cached map must still be keyed by episode number",
                methodBody(source, "private java.util.Map<Integer, String> getEpisodeTitles()").contains("titles.put(Integer.parseInt(parts[0]), parts[1]);"));
    }

    private static String readSource() throws Exception {
        Path moduleRelative = Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
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
