package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.TmdbService;
import com.github.catvod.utils.Trans;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 繁体界面下的 TMDB 匹配回归测试。
 *
 * <p>场景来源（用户反馈）：界面语言设为繁体中文后，部分影片匹配不到 TMDB。
 *
 * <p>根因：站源标题在繁体界面下经 {@code Vod.trans()} 被 {@code Trans.s2t} 逐字改成繁体，
 * 而 TMDB 数据按 {@code TmdbConfig.language}（默认 zh-CN）返回简体标题；匹配器的 strict /
 * contained / smart 判定与相似度打分全部是逐字符字面比较，中间没有任何简繁归一，
 * 于是「繁体站源标题 vs 简体 TMDB 标题」必然落空。
 *
 * <p>归一方向必须是单向转繁 {@code Trans.s2t(false, text)}：反向 {@code t2s} 在 10 组
 * 「多简对一繁」的字上是多对一坍缩（余→餘→馀），会污染简体原文，不能用。
 */
public class TmdbMatcherHantTest {

    @After
    public void tearDown() {
        // Trans 是全局可变状态，避免影响同一 JVM 里的其他用例。
        Trans.setTraditional(null);
    }

    @Test
    public void strictMatch_acceptsTraditionalSourceTitleAgainstSimplifiedTmdbTitle() {
        List<TmdbItem> results = List.of(item(1, "tv", "庆余年", "2019"));
        TmdbMatcher matcher = matcher(results);

        TmdbItem match = matcher.searchAndMatch("慶餘年", "tv", 2019);

        assertNotNull("繁体站源标题应当匹配到简体 TMDB 标题", match);
        assertEquals(1, match.getTmdbId());
    }

    @Test
    public void strictMatch_matchesWithTraditionalInterfaceLanguageEnabled() {
        Trans.setTraditional(true);
        List<TmdbItem> results = List.of(item(1, "tv", "庆余年", "2019"));
        TmdbMatcher matcher = matcher(results);

        TmdbItem match = matcher.searchAndMatch("慶餘年", "tv", 2019);

        assertNotNull("界面处于繁体态时匹配仍应成功", match);
        assertEquals(1, match.getTmdbId());
    }

    @Test
    public void containedYearMatch_acceptsTraditionalSourceTitle() {
        // 包含式匹配要求标题长度 >= 4，所以这里用系列片的片名（真实场景：系列电影/剧集）。
        List<TmdbItem> results = List.of(item(2, "movie", "纳尼亚传奇：狮子、女巫和魔衣橱", "2005"));
        TmdbMatcher matcher = matcher(results);

        TmdbItem match = matcher.searchAndMatch("納尼亞傳奇", "movie", 2005);

        assertNotNull("繁体站源标题应当能在包含式匹配里命中", match);
        assertEquals(2, match.getTmdbId());
    }

    @Test
    public void strictMatch_rejectsGenuinelyDifferentTitle() {
        List<TmdbItem> results = List.of(item(3, "tv", "赘婿", "2021"));
        TmdbMatcher matcher = matcher(results);

        assertNull("归一化不能让真正不同的标题也命中", matcher.searchAndMatch("慶餘年", "tv", 2019));
    }

    @Test
    public void cleanVideoName_keepsTraditionalTitleIntact() {
        Trans.setTraditional(true);
        TmdbMatcher matcher = new TmdbMatcher(null, new TmdbConfig());

        assertEquals("慶餘年", matcher.cleanVideoName("慶餘年"));
    }

    private static TmdbMatcher matcher(List<TmdbItem> results) {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"test\"}");
        return new TmdbMatcher(new FakeTmdbService(results), config);
    }

    private static TmdbItem item(int id, String mediaType, String title, String subtitle) {
        return new TmdbItem(id, mediaType, title, subtitle, "", "", "", "", 8.0, "", "", new ArrayList<>());
    }

    private static final class FakeTmdbService extends TmdbService {

        private final List<TmdbItem> results;

        private FakeTmdbService(List<TmdbItem> results) {
            this.results = results;
        }

        @Override
        public List<TmdbItem> search(String keyword, TmdbConfig config) {
            return new ArrayList<>(results);
        }
    }
}