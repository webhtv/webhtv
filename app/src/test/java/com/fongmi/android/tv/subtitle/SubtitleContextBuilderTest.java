package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubtitleContextBuilderTest {

    @Test
    public void refreshDuration_usesTimeDiscoveredDuringProviderSearchAndPreservesIdentity() {
        long[] known = {0L};
        SubtitleRequest request = SubtitleRequest.builder().vodName("战狼2").vodYear("2017")
                .preferredLanguage("zh-Hans").allowTmdbLookup(false).build()
                .withDurationSource(() -> known[0]);
        SubtitleContext beforeSearch = new SubtitleContextBuilder().build(request);
        assertEquals(0L, beforeSearch.getDurationMs());
        known[0] = 123L * 60000L;
        SubtitleContext beforeRank = SubtitleMatchService.refreshDuration(request, beforeSearch);
        assertEquals("查询期间发现的时长必须参与排序", known[0], beforeRank.getDurationMs());
        assertEquals(beforeSearch.getCanonicalTitle(), beforeRank.getCanonicalTitle());
        assertEquals(beforeSearch.getAliases(), beforeRank.getAliases());
        assertEquals(beforeSearch.getIdentity(), beforeRank.getIdentity());
        SubtitleCandidate wrong = candidate("short", "战狼2.2017.1080p.简中.srt", "1h38m").withDurationMs(98L * 60000L);
        SubtitleCandidate right = candidate("exact", "战狼2.2017.1080p.简中.srt", "2h3m").withDurationMs(known[0]);
        SubtitleRanker ranker = new SubtitleRanker();
        assertEquals("exact", ranker.pickBest(ranker.rank(List.of(wrong, right), beforeRank), beforeRank).getCandidateId());
    }

    @Test
    public void buildWithoutTmdb_usesSourceMetadataFallbackForMovie() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你 (2019) 1080P")
                .vodYear("2019")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("movie", context.getMediaType());
        assertEquals("想见你", context.getCanonicalTitle());
        assertEquals(2019, context.getYear());
        assertFalse(context.hasTmdbIdentity());
    }

    @Test
    public void buildWithoutTmdb_usesEpisodeMetadataFallbackForTv() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("tv-1")
                .vodName("最后生还者 第二季")
                .episodeName("S02E03 漫长漫长时光")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("tv", context.getMediaType());
        assertEquals("最后生还者", context.getCanonicalTitle());
        assertEquals(2, context.getSeasonNumber());
        assertEquals(3, context.getEpisodeNumber());
    }

    @Test
    public void build_carriesPreferredLanguageThroughToContext() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你")
                .preferredLanguage("en")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("en", context.getPreferredLanguage());
    }

    @Test
    public void build_extractsLocalMediaPathFromFileUrl() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你")
                .playUrl("file:///sdcard/Movies/movie.mkv")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("/sdcard/Movies/movie.mkv", context.getMediaPath());
        assertFalse(context.isNetworkStream());
    }

    @Test
    public void build_readsDurationAtSearchTimeNotAtRequestCreation() {
        // 起播瞬间时长往往还是未知；这里是「请求创建时 0、真正建 context 时已知」的时序。
        long[] known = {0L};
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("战狼2")
                .vodYear("2017")
                .allowTmdbLookup(false)
                .build()
                .withDurationSource(() -> known[0]);

        assertEquals("创建时未知", 0L, request.getDurationMs());
        known[0] = 123L * 60L * 1000L;

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("搜索阶段必须拿到最新已知时长", 123L * 60L * 1000L, context.getDurationMs());
    }

    @Test
    public void build_treatsStillUnknownDurationAsZeroWithoutBlocking() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("live-1")
                .vodName("新闻直播")
                .allowTmdbLookup(false)
                .build()
                .withDurationSource(() -> 0L);

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertEquals("未知时长/直播必须退化为 0 而不是卡住", 0L, context.getDurationMs());
    }

    @Test
    public void build_prefersAlreadyKnownDurationOverTheSource() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你")
                .durationMs(100L)
                .allowTmdbLookup(false)
                .build()
                .withDurationSource(() -> 999L);

        assertEquals(100L, new SubtitleContextBuilder().build(request).getDurationMs());
    }

    @Test
    public void build_durationDiscoveredLaterChangesAutoPickAmongVersions() {
        // 同一部片子的两个版本标题/语言/格式一致，只有时长不同：时长必须真的参与排序。
        long[] known = {0L};
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("战狼2")
                .vodYear("2017")
                .allowTmdbLookup(false)
                .build()
                .withDurationSource(() -> known[0]);
        known[0] = 123L * 60L * 1000L;
        SubtitleContext context = new SubtitleContextBuilder().build(request);

        SubtitleCandidate wrong = candidate("short", "战狼2.2017.1080p.简中.srt", "1h38m").withDurationMs(98L * 60L * 1000L);
        SubtitleCandidate exact = candidate("exact", "战狼2.2017.1080p.简中.srt", "2h3m").withDurationMs(123L * 60L * 1000L);
        SubtitleRanker ranker = new SubtitleRanker();
        List<SubtitleCandidate> ranked = ranker.rank(List.of(wrong, exact), context);

        assertEquals("搜索阶段拿到的时长必须让「时长对得上」的那条胜出", "exact", ranker.pickBest(ranked, context).getCandidateId());
    }

    private static SubtitleCandidate candidate(String id, String name, String releaseInfo) {
        return new SubtitleCandidate("xunlei", id, name, "简体", "srt", releaseInfo, 0, 2017, -1, -1, SubtitleMatchType.METADATA_FUZZY, "战狼2 2017", true, "{}");
    }

    @Test
    public void build_leavesNetworkUrlOutOfMediaPath() {
        SubtitleRequest request = SubtitleRequest.builder()
                .siteKey("test")
                .vodId("movie-1")
                .vodName("想见你")
                .playUrl("https://example.com/movie.mkv")
                .allowTmdbLookup(false)
                .build();

        SubtitleContext context = new SubtitleContextBuilder().build(request);
        assertTrue(context.getMediaPath().isEmpty());
        assertTrue(context.isNetworkStream());
    }
}
