package com.fongmi.android.tv.subtitle;

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
        // 五个候选都是这部片子的字幕，选谁取决于标题覆盖比例；要保证的是选中的是分最高的那条
        assertEquals(ranked.get(0).getCandidateId(), best.getCandidateId());
        assertTrue("选中的必须是列表里分数最高的候选", best.getScore() >= ranked.get(ranked.size() - 1).getScore());
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

    private static SubtitleCandidate candidate(String id, String name, String language, String format, int score, String releaseInfo) {
        return new SubtitleCandidate("xunlei", id, name, language, format, releaseInfo, score, 0, -1, -1, SubtitleMatchType.METADATA_FUZZY, "战狼2 2017", true, "{}");
    }
}