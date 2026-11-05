package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * 剧集载荷组装的边界测试。
 *
 * <p>核心断言有两条：<b>零值省略而不是填 0</b>（插件靠"键是否存在"区分有无评分），
 * <b>空字符串也省略</b>（否则插件会渲染出一行空标题）。
 */
public class PluginEpisodePayloadTest {

    private static TmdbItem item(int tmdbId, String mediaType, String title, String subtitle, String posterUrl, String originalLanguage, String originCountry, double tmdbRating, double doubanRating) {
        return new TmdbItem(tmdbId, mediaType, title, subtitle, "", posterUrl, "", "", 0.0, originalLanguage, originCountry, new ArrayList<>(), "", tmdbRating, doubanRating, "");
    }

    private static Vod vod(String name, String year, String pic) {
        Vod vod = new Vod();
        vod.setName(name);
        vod.setYear(year);
        vod.setPic(pic);
        return vod;
    }

    private static Episode episode(String name, String url) {
        return Episode.create(name, url);
    }

    @Test
    public void everyFieldAppearsWithTheValueItWasGiven() {
        TmdbItem item = item(93405, "tv", "鱿鱼游戏", "Squid Game", "https://img/poster.jpg", "ko", "KR", 7.8, 6.5);
        Vod vod = vod("鱿鱼游戏 第一季", "2021", "https://img/vod.jpg");

        JsonObject payload = PluginEpisodePayload.of(item, vod, episode("第3集", "https://media/e3.m3u8"));

        assertEquals("鱿鱼游戏", payload.get("title").getAsString());
        assertEquals("Squid Game", payload.get("subtitle").getAsString());
        assertEquals("tv", payload.get("mediaType").getAsString());
        assertEquals("2021", payload.get("year").getAsString());
        assertEquals(93405, payload.get("tmdbId").getAsInt());
        assertEquals(7.8, payload.get("tmdbRating").getAsDouble(), 0.0001);
        assertEquals(6.5, payload.get("doubanRating").getAsDouble(), 0.0001);
        assertEquals("ko", payload.get("originalLanguage").getAsString());
        assertEquals("KR", payload.get("originCountry").getAsString());
        assertEquals("https://img/poster.jpg", payload.get("posterUrl").getAsString());
        assertEquals("第3集", payload.get("episodeName").getAsString());
        assertEquals(3, payload.get("episodeNumber").getAsInt());
    }

    @Test
    public void allThreeInputsNullYieldsNull() {
        assertNull(PluginEpisodePayload.of(null, null, null));
    }

    @Test
    public void zeroRatingsAreOmittedInsteadOfWrittenAsZero() {
        TmdbItem item = item(1, "movie", "无评分的片", "", "", "", "", 0.0, 0.0);

        JsonObject payload = PluginEpisodePayload.of(item, null, null);

        assertFalse("评分 0 必须整键消失，否则插件会渲染出 0 分", payload.has("tmdbRating"));
        assertFalse(payload.has("doubanRating"));
        assertEquals("无评分的片", payload.get("title").getAsString());
    }

    @Test
    public void tmdbIdIsOmittedWhenNotPositive() {
        TmdbItem item = item(0, "movie", "没有 tmdb id", "", "", "", "", 0.0, 0.0);

        JsonObject payload = PluginEpisodePayload.of(item, null, null);

        assertFalse(payload.has("tmdbId"));
    }

    @Test
    public void declaredMediaTypeWins() {
        JsonObject tv = PluginEpisodePayload.of(item(1, "tv", "剧集", "", "", "", "", 0.0, 0.0), null, null);
        JsonObject movie = PluginEpisodePayload.of(item(2, "movie", "电影", "", "", "", "", 0.0, 0.0), null, null);

        assertEquals("tv", tv.get("mediaType").getAsString());
        assertEquals("movie", movie.get("mediaType").getAsString());
    }

    /**
     * mediaType 缺失时按约定兜底为 "movie"。
     *
     * <p>正常链路里上游不会给出空的 mediaType，这条只是防御：真漏了就给一个确定值，
     * 而不是让插件拿到空串去自己猜。
     */
    @Test
    public void emptyDeclaredMediaTypeFallsBackToMovieNeverTv() {
        JsonObject payload = PluginEpisodePayload.of(item(1, "", "剧集但没有类型", "", "", "", "", 0.0, 0.0), null, null);

        assertEquals("movie", payload.get("mediaType").getAsString());
    }

    @Test
    public void titleAndPosterPreferTheTmdbItemOverTheVod() {
        TmdbItem item = item(1, "movie", "TMDB 标题", "", "https://img/tmdb.jpg", "", "", 0.0, 0.0);
        Vod vod = vod("站点标题", "2010", "https://img/site.jpg");

        JsonObject payload = PluginEpisodePayload.of(item, vod, null);

        assertEquals("TMDB 标题", payload.get("title").getAsString());
        assertEquals("https://img/tmdb.jpg", payload.get("posterUrl").getAsString());
    }

    @Test
    public void titleAndPosterFallBackToTheVodWhenTheItemHasNone() {
        TmdbItem item = item(1, "movie", "", "", "", "", "", 0.0, 0.0);
        Vod vod = vod("站点标题", "2010", "https://img/site.jpg");

        JsonObject payload = PluginEpisodePayload.of(item, vod, null);

        assertEquals("站点标题", payload.get("title").getAsString());
        assertEquals("https://img/site.jpg", payload.get("posterUrl").getAsString());
    }

    @Test
    public void emptyStringsAreOmittedRatherThanWrittenAsEmptyValues() {
        TmdbItem item = item(1, "movie", "有标题", "", "", "", "", 0.0, 0.0);
        Vod vod = vod("", "", "");

        JsonObject payload = PluginEpisodePayload.of(item, vod, null);

        assertFalse("空标题不该写出 subtitle 键", payload.has("subtitle"));
        assertFalse(payload.has("year"));
        assertFalse(payload.has("originalLanguage"));
        assertFalse(payload.has("originCountry"));
        assertFalse(payload.has("episodeName"));
        assertEquals("只该留下 title / mediaType / tmdbId 三个键", new TreeSet<>(Set.of("title", "mediaType", "tmdbId")), new TreeSet<>(payload.keySet()));
        assertEquals("movie", payload.get("mediaType").getAsString());
    }

    @Test
    public void episodeWithUnparsableNumberKeepsTheNameAndDropsTheNumber() {
        Episode episode = episode("特别篇", "https://media/special.m3u8");

        JsonObject payload = PluginEpisodePayload.of(null, null, episode);

        assertEquals("特别篇", payload.get("episodeName").getAsString());
        assertFalse("集数解析不出来时不能写 0", payload.has("episodeNumber"));
    }

    @Test
    public void episodeAloneIsEnoughToProduceAPayload() {
        JsonObject payload = PluginEpisodePayload.of(null, null, episode("第12集", "https://media/e12.m3u8"));

        assertTrue(payload != null && payload.size() > 0);
        assertEquals(12, payload.get("episodeNumber").getAsInt());
    }

    @Test
    public void oversizedPayloadIsDroppedWholeInsteadOfTruncated() {
        String huge = "x".repeat(PluginEpisodePayload.MAX_PAYLOAD_BYTES + 1);
        TmdbItem item = item(1, "movie", huge, "", "", "", "", 0.0, 0.0);

        assertNull(PluginEpisodePayload.of(item, null, null));
    }

    @Test
    public void payloadJustUnderTheLimitIsStillAccepted() {
        String title = "x".repeat(PluginEpisodePayload.MAX_PAYLOAD_BYTES - 64);
        TmdbItem item = item(1, "movie", title, "", "", "", "", 0.0, 0.0);

        JsonObject payload = PluginEpisodePayload.of(item, null, null);

        assertTrue("上限以内必须放行", payload != null && payload.get("title").getAsString().length() == title.length());
    }
}