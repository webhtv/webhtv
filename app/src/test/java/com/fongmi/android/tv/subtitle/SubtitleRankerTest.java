package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

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
}