package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.ResolvedMediaIdentity;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SubtitleRankerTest {

    @Test
    public void pickBest_rejectsDifferentWorksSharingATitlePrefixOrAnEnglishWordFragment() {
        for (String[] pair : new String[][]{{"长津湖", "长津湖之水门桥"}, {"误杀", "误杀瞒天记"}, {"Up", "Superman"}}) {
            SubtitleContext context = SubtitleContext.builder().mediaType("movie")
                    .canonicalTitle(pair[0]).year(2021).preferredLanguage("zh-Hans")
                    .identity(ResolvedMediaIdentity.builder().tmdbId(1).mediaType("movie").build()).build();
            SubtitleCandidate wrong = candidate("wrong", pair[1] + ".2021.1080p.简中.ass", "简体", "ass", 20, "");
            SubtitleRanker ranker = new SubtitleRanker();
            assertNull("另一作品不是同名片的版本", ranker.pickBest(ranker.rank(List.of(wrong), context), context));
        }
    }

    @Test
    public void pickBest_checksEveryExplicitEpisodeMarkerIncludingReleaseInformation() {
        SubtitleContext context = SubtitleContext.builder().mediaType("tv").canonicalTitle("庆余年")
                .year(2019).seasonNumber(1).episodeNumber(5).preferredLanguage("zh-Hans")
                .identity(ResolvedMediaIdentity.builder().tmdbId(1).mediaType("tv").build()).build();
        SubtitleRanker ranker = new SubtitleRanker();
        for (String release : List.of("S02E05.2019.1080p", "S01E06.2019", "S01E05.S02E05.2019")) {
            SubtitleCandidate wrong = candidate("wrong", "庆余年.简中.ass", "简体", "ass", 40, release);
            assertNull(ranker.pickBest(ranker.rank(List.of(wrong), context), context));
        }
        SubtitleCandidate right = candidate("right", "庆余年.简中.ass", "简体", "ass", 40, "S01E05.2019");
        assertEquals("right", ranker.pickBest(ranker.rank(List.of(right), context), context).getCandidateId());
    }

    @Test
    public void pickBest_acceptsAnIdentifiedTitleInReleaseInformation() {
        SubtitleContext context = SubtitleContext.builder().mediaType("movie").canonicalTitle("战狼2")
                .year(2017).preferredLanguage("zh-Hans").build();
        SubtitleCandidate right = candidate("right", "track6.ass", "简体", "ass", 40, "战狼2.2017.1080p");
        SubtitleRanker ranker = new SubtitleRanker();
        assertEquals("right", ranker.pickBest(ranker.rank(List.of(right), context), context).getCandidateId());
    }

    @Test
    public void pickBest_rejectsSymbolOnlyTitlesAndAliasesEvenWithHighProviderConfidence() {
        for (String title : List.of("战狼2", "!!!", "【】")) {
            SubtitleContext context = SubtitleContext.builder().mediaType("movie").canonicalTitle(title)
                    .aliases(List.of("...", "!?")).year(2017).preferredLanguage("zh-Hans").build();
            SubtitleCandidate unrelated = candidate("unrelated", "2017.1080p.简中.ass", "简体", "ass", 120, "");
            SubtitleRanker ranker = new SubtitleRanker();
            assertNull("纯符号不能构成作品身份", ranker.pickBest(ranker.rank(List.of(unrelated), context), context));
        }
    }

    @Test
    public void rank_prefersConfiguredLanguageOverDefaultChineseBias() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("The Last of Us")
                .preferredLanguage("en")
                .build();

        SubtitleCandidate chinese = new SubtitleCandidate("assrt", "1", "The Last of Us 中文字幕", "中文字幕", "srt", "The Last of Us", 60, 0, 0, 0, SubtitleMatchType.METADATA_FUZZY, "query", false, "{}");
        SubtitleCandidate english = new SubtitleCandidate("assrt", "2", "The Last of Us English", "English", "srt", "The Last of Us", 60, 0, 0, 0, SubtitleMatchType.METADATA_FUZZY, "query", false, "{}");

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(chinese, english), context);
        assertEquals("2", ranked.get(0).getCandidateId());
    }
    @Test
    public void normalizeLanguageTag_mapsProviderDescriptionsToBcp47() {
        assertEquals("zh-Hans", SubtitleRanker.normalizeLanguageTag("简体中文"));
        assertEquals("zh-Hans", SubtitleRanker.normalizeLanguageTag("简中"));
        assertEquals("zh-Hans", SubtitleRanker.normalizeLanguageTag("chs"));
        assertEquals("zh-Hans", SubtitleRanker.normalizeLanguageTag("zh-Hans"));
        assertEquals("zh-Hant", SubtitleRanker.normalizeLanguageTag("繁体中文"));
        assertEquals("zh-Hant", SubtitleRanker.normalizeLanguageTag("cht"));
        assertEquals("zh", SubtitleRanker.normalizeLanguageTag("中文"));
        assertEquals("zh", SubtitleRanker.normalizeLanguageTag("默认"));
        assertEquals("en", SubtitleRanker.normalizeLanguageTag("English"));
        assertEquals("ja", SubtitleRanker.normalizeLanguageTag("日语"));
        assertEquals("ko", SubtitleRanker.normalizeLanguageTag("韩语"));
        assertEquals("", SubtitleRanker.normalizeLanguageTag(""));
        assertEquals("", SubtitleRanker.normalizeLanguageTag(null));
    }

    @Test
    public void normalizeLanguageTag_doesNotInventLanguageForUnknownDescription() {
        // 无法识别的描述必须退化成未知语言，不能让播放器拿非法标签去比较
        assertEquals("", SubtitleRanker.normalizeLanguageTag("unknown-script"));
    }
    @Test
    public void pickBest_choosesBestVersionWhenSameMovieHasManySubtitleVersions() {
        // 设备实测场景：战狼2 手动搜索得到 56 个候选，全是同片的简/繁、ASS/SRT、不同压制组
        // 版本。旧实现要求「最高分领先第二名 >= 5」才肯自动应用，同片多版本分差本就接近，
        // 于是自动匹配永远返回 null，只能手动去列表里点。
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("战狼2")
                .originalTitle("Wolf Warrior 2")
                .year(2017)
                .preferredLanguage("zh-Hans")
                .durationMs(123L * 60L * 1000L)
                .build();

        List<SubtitleCandidate> candidates = List.of(
                candidate("1", "[www.hdmayi.com]战狼2.Wolf.Warrior.2.2017.720p.BluRay.x264.DTS-CNXP_track6_eng.ass", "简体&英语", "ass", 75, "1h58m"),
                candidate("2", "战狼2.2017.4K.HDR.H265.简体 雅黑 灰.ass", "简体", "ass", 75, "2h3m"),
                candidate("3", "战狼2.Zhan.Lang.2.2017.PROPER.720p.BluRay.x264.DTS-HDChina.ass", "简体&英语", "ass", 75, "2h3m"),
                candidate("4", "战狼2.2017.1080p.BluRay.x264.DTS-HD.MA.7.1.srt", "简体&英语", "srt", 71, "2h3m"),
                candidate("5", "战狼2.Wolf.Warriors2.2017.1080p.BluRay.x264.DTS-HD.MA.7.1-lzhgzhj.srt", "简体", "srt", 71, "2h3m"));

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(candidates, context);
        SubtitleCandidate best = new SubtitleRanker().pickBest(ranked, context);

        assertNotNull("同片多版本必须能选出最优的一条，而不是放弃", best);
        assertEquals("标题覆盖最好的候选必须排第一", "1", ranked.get(0).getCandidateId());
        assertEquals("pickBest 必须选中排序第一的候选", "1", best.getCandidateId());
        assertTrue("排序必须真正区分候选，而不是全部同分", ranked.get(0).getScore() > ranked.get(ranked.size() - 1).getScore());
    }

    @Test
    public void pickBest_prefersDurationMatchAmongIdenticalTitles() {
        // 标题、年份、语言、格式完全一致时，时长是唯一的区分信号
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("战狼2")
                .year(2017)
                .preferredLanguage("zh-Hans")
                .durationMs(123L * 60L * 1000L)
                .build();

        SubtitleCandidate wrongLength = candidate("short", "战狼2.2017.1080p.简中.srt", "简体", "srt", 60, "1h38m").withDurationMs(98L * 60L * 1000L);
        SubtitleCandidate exactLength = candidate("exact", "战狼2.2017.1080p.简中.srt", "简体", "srt", 60, "2h3m").withDurationMs(123L * 60L * 1000L);

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(wrongLength, exactLength), context);
        assertEquals("exact", new SubtitleRanker().pickBest(ranked, context).getCandidateId());
    }

    @Test
    public void pickBest_rejectsCandidateWhoseTitleDoesNotMatch() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("战狼2")
                .year(2017)
                .preferredLanguage("zh-Hans")
                .build();

        // 标题完全无关时必须拒绝，不能因为「选最优」就随便挂一条别人的字幕
        List<SubtitleCandidate> candidates = List.of(
                candidate("x", "流浪地球.2019.1080p.BluRay.简中.srt", "简体", "srt", 60, "2h5m"));

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(candidates, context);
        assertNull(new SubtitleRanker().pickBest(ranked, context));
    }

    @Test
    public void rank_prefersSimplifiedChineseWhenThatIsPreferred() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("战狼2")
                .year(2017)
                .preferredLanguage("zh-Hans")
                .build();

        SubtitleCandidate traditional = candidate("trad", "战狼2.2017.1080p.繁中.srt", "繁体中文", "srt", 70, "2h3m");
        SubtitleCandidate simplified = candidate("simp", "战狼2.2017.1080p.简中.srt", "简体中文", "srt", 70, "2h3m");

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(traditional, simplified), context);
        assertEquals("simp", ranked.get(0).getCandidateId());
    }

    @Test
    public void pickBest_rejectsSequelTitleThatScoresAboveThreshold() {
        // 《流浪地球》的电影字幕与《流浪地球2》是不同作品。旧判据只看 titleScore>0，
        // 这条续集靠 38+14+15+10=77 越过无 tmdb 的 70 线，正是要拒绝的情况。
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("流浪地球")
                .year(2019)
                .preferredLanguage("zh-Hans")
                .build();
        SubtitleCandidate sequel = candidate("sequel", "流浪地球2.2019.1080p.简中.ass", "简体", "ass", 0, "");

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(sequel), context);

        assertEquals("续集分数线仍可很高", true, ranked.get(0).getScore() >= 70);
        assertNull("续集不是同一部作品，不能自动应用", new SubtitleRanker().pickBest(ranked, context));
    }

    @Test
    public void pickBest_rejectsSequelWhenTmdbIdentityIsKnown() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("误杀")
                .year(2019)
                .preferredLanguage("zh-Hans")
                .identity(ResolvedMediaIdentity.builder().tmdbId(1).mediaType("movie").canonicalTitle("误杀").year(2019).build())
                .build();
        SubtitleCandidate sequel = candidate("sequel", "误杀2.2021.2160p.简中.ass", "简体", "ass", 0, "");

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(sequel), context);

        assertEquals("有 tmdb 时分数线是 55", true, ranked.get(0).getScore() >= 55);
        assertNull("续集不能借 tmdb 低阈值蒙过", new SubtitleRanker().pickBest(ranked, context));
    }

    @Test
    public void pickBest_acceptsEditionSuffixAndAliasesOfTheSameMovie() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("战狼2")
                .originalTitle("Wolf Warrior 2")
                .year(2017)
                .preferredLanguage("zh-Hans")
                .build();

        // provider 基础分给足，让这两条的分数都过准入线：本用例验证的是「身份」不是分数
        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(
                candidate("extended", "战狼2.2017.EXTENDED.2160p.简中.ass", "简体", "ass", 20, ""),
                candidate("alias", "Wolf.Warrior.2.2017.1080p.简中.ass", "简体", "ass", 20, "")), context);

        assertEquals("同片多版本/原名/导演剪辑版都必须能自动应用", 2, countPickable(ranked, context));
        assertNotNull(new SubtitleRanker().pickBest(ranked, context));
    }

    @Test
    public void pickBest_takesBestIdentityQualifiedCandidateEvenWhenAnInvalidOneScoresHigher() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("movie")
                .canonicalTitle("流浪地球")
                .year(2019)
                .preferredLanguage("zh-Hans")
                .build();
        SubtitleCandidate sequel = candidate("sequel", "流浪地球2.2019.1080p.简中.ass", "简体", "ass", 40, "");
        SubtitleCandidate real = candidate("real", "流浪地球.2019.1080p.简中.srt", "简体", "srt", 10, "");

        List<SubtitleCandidate> ranked = new SubtitleRanker().rank(List.of(sequel, real), context);

        assertEquals("错误续集分数更高", "sequel", ranked.get(0).getCandidateId());
        assertEquals("自动选择必须跳过身份不合格的最高分", "real", new SubtitleRanker().pickBest(ranked, context).getCandidateId());
    }

    @Test
    public void pickBest_rejectsExplicitlyDifferentSeasonAndEpisode() {
        SubtitleContext context = SubtitleContext.builder()
                .mediaType("tv")
                .canonicalTitle("庆余年")
                .year(2019)
                .seasonNumber(1)
                .episodeNumber(5)
                .preferredLanguage("zh-Hans")
                .identity(ResolvedMediaIdentity.builder().tmdbId(2).mediaType("tv").canonicalTitle("庆余年").year(2019).seasonNumber(1).episodeNumber(5).build())
                .build();

        SubtitleCandidate otherSeason = new SubtitleCandidate("xunlei", "s2", "庆余年.2019.S02E05.简中.ass", "简体", "ass", "", 0, 0, 2, 5, SubtitleMatchType.METADATA_FUZZY, "庆余年", true, "{}");
        SubtitleCandidate otherEpisode = new SubtitleCandidate("xunlei", "e6", "庆余年.S01E06.简中.ass", "简体", "ass", "", 0, 0, 1, 6, SubtitleMatchType.METADATA_FUZZY, "庆余年", true, "{}");
        SubtitleCandidate right = new SubtitleCandidate("xunlei", "ok", "庆余年.S01E05.简中.ass", "简体", "ass", "", 0, 0, 1, 5, SubtitleMatchType.METADATA_FUZZY, "庆余年", true, "{}");

        assertEquals(new SubtitleRanker().pickBest(new SubtitleRanker().rank(List.of(right), context), context).getCandidateId(), "ok");
        assertNull("明确不同季不能自动应用", new SubtitleRanker().pickBest(new SubtitleRanker().rank(List.of(otherSeason), context), context));
        assertNull("明确不同集不能自动应用", new SubtitleRanker().pickBest(new SubtitleRanker().rank(List.of(otherEpisode), context), context));
    }

    private static int countPickable(List<SubtitleCandidate> ranked, SubtitleContext context) {
        int count = 0;
        for (SubtitleCandidate item : ranked) if (new SubtitleRanker().pickBest(List.of(item), context) != null) count++;
        return count;
    }

    private static SubtitleCandidate candidate(String id, String name, String language, String format, int score, String releaseInfo) {
        return new SubtitleCandidate("xunlei", id, name, language, format, releaseInfo, score, 0, -1, -1, SubtitleMatchType.METADATA_FUZZY, "战狼2 2017", true, "{}");
    }
}