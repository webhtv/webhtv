package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 详情页的 TMDB 匹配必须先在比较前把简繁字形压到同一侧。
 *
 * <p>根因：站源标题在繁体界面会被 {@code Vod.trans()} 转成繁体，而 TMDB 返回的标题字形由
 * {@code TmdbConfig.language}（默认 zh-CN）决定，是简体。详情页原来自己有一套
 * {@code normalize()}，只做标点/大小写归一，于是「喜劇之王 vs 喜剧之王」逐字符比较必然失败，
 * 日志表现为「候选过滤：标题不一致」刷屏后放弃匹配。
 *
 * <p>同时这里锁住两条边界：噪声词表与类型关键词改成「归一后 + 单套繁体词表」，
 * 而归一化结果绝不能被当成搜索关键词发给 TMDB（实测会把召回打成 0）。
 */
public class TmdbDetailActivityHantMatchTest {

    @Test
    public void normalizeAppliesScriptCanonicalization() throws Exception {
        String source = readMainActivity();

        assertTrue(source.contains("return plainNormalize(canonicalTitle(text));"));
        assertTrue("归一必须显式传 pass=false，否则简体态会短路成 no-op",
                source.contains("Trans.s2t(false, Objects.toString(text, \"\"))"));
    }

    @Test
    public void noiseTagDetectionCoversTraditionalTags() throws Exception {
        String source = readMainActivity();
        String method = method(source, "private boolean isTmdbNoiseTag(String text)", "private Flag findInitialFlag");

        assertTrue("标签判定要走归一后的字形", method.contains("String value = normalize(text);"));
        assertTrue("繁体标签「高碼」必须被识别", method.contains("高碼"));
        assertTrue("繁体标签「無水印」必须被识别", method.contains("無水印"));
        assertTrue("繁体标签「未刪減」必须被识别", method.contains("未刪減"));
    }

    @Test
    public void searchQueryCleanupStripsTraditionalNoiseTags() throws Exception {
        String source = readMainActivity();
        String query = method(source, "private String cleanTmdbSearchQuery(String text)", "private String removeNoiseBrackets");

        assertTrue("清洗词表要同时覆盖繁体写法，否则繁体标题的标签会混进搜索词", query.contains("高碼"));
        assertTrue(query.contains("無水印"));
        assertTrue(query.contains("連載至") || query.contains("連載"));
    }

    @Test
    public void mediaTypeScoringMatchesBothScripts() throws Exception {
        String source = readMainActivity();
        String method = method(source, "private int scoreTmdbMediaType", "private int scoreTmdbPeople(TmdbItem item");

        assertTrue("类型判定要用归一后的字形", method.contains("normalize(sourceVod.getTypeName()"));
        assertTrue("必须认得繁体「電視劇」", method.contains("電視劇"));
        assertTrue("必须认得繁体「電影」", method.contains("電影"));
    }

    @Test
    public void normalizedTextIsNeverUsedAsTmdbQueryWord() throws Exception {
        String source = readMainActivity();

        assertFalse("归一化产物只能用于比较，不能当查询词", source.contains("tmdbService.search(normalize("));
        assertFalse(source.contains("tmdbService.search(canonicalTitle("));
    }

    private static String readMainActivity() throws Exception {
        return readMain(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String readMain(Path relative) throws Exception {
        return Files.readString(root().resolve("app").resolve("src").resolve("main").resolve("java").resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path root() {
        return Files.exists(Path.of("app", "src", "main")) ? Path.of(".") : Path.of("..");
    }
}
