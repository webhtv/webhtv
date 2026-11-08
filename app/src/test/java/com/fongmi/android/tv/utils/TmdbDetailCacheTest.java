package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 锁定「详情页已解析的季集随详情快照交接给播放页」这条契约的两端。
 *
 * <p>前半部分是缓存层（{@link TmdbDetailCache}）的行为用例；末尾三条用源码守卫锁定消费端
 * （{@code TmdbUIAdapter.seedSeasonEpisodes} 与 {@code loadSource} 的补绑定）的形状。
 * 两端合起来才是「详情页有卡片、播放页也有卡片」，所以放在同一个测试类里；mobile 源集
 * 已经存在同名的 {@code TmdbUIAdapterTest}，共享源集不能再建同名类。
 */
public class TmdbDetailCacheTest {

    @Test
    public void putReturnsEmptyKeyForMissingItemOrDetail() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();

        assertTrue(TmdbDetailCache.put(null, detail, List.of()).isEmpty());
        assertTrue(TmdbDetailCache.put(item, null, List.of()).isEmpty());
    }

    @Test
    public void takeReturnsCachedDetailOnceForExpectedTmdbItem() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();
        detail.addProperty("name", "庆余年");
        List<TmdbPerson> cast = new ArrayList<>();
        cast.add(new TmdbPerson(1, "张若昀", "范闲", "", "Acting", ""));

        String key = TmdbDetailCache.put(item, detail, cast, "zh-Hans");
        cast.clear();

        TmdbDetailCache.Entry entry = TmdbDetailCache.take(key, item, "zh-CN");

        assertTrue(!key.isEmpty());
        assertSame(item, entry.getItem());
        assertSame(detail, entry.getDetail());
        assertEquals(1, entry.getCast().size());
        assertEquals("张若昀", entry.getCast().get(0).getName());
        assertNull(TmdbDetailCache.take(key, item));
    }

    @Test
    public void takeRejectsSnapshotFromAnotherLanguage() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();

        String key = TmdbDetailCache.put(item, detail, List.of(), "en-US");

        assertNull(TmdbDetailCache.take(key, item, "zh-CN"));
    }

    @Test
    public void takeAcceptsEquivalentLanguageIdentity() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();

        String key = TmdbDetailCache.put(item, detail, List.of(), "zh-Hans");

        assertEquals("zh-CN", TmdbDetailCache.take(key, item, "zh-CN").getLanguage());
    }

    @Test
    public void takeRejectsRegionalChineseVariantForSimplifiedTarget() {
        TmdbItem item = new TmdbItem(123, "tv", "慶餘年", "", "", "", "");
        JsonObject detail = new JsonObject();

        String key = TmdbDetailCache.put(item, detail, List.of(), "zh-TW");

        assertNull(TmdbDetailCache.take(key, item, "zh-CN"));
    }

    @Test
    public void takeReturnsNullForMismatchedTmdbItem() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        TmdbItem other = new TmdbItem(456, "tv", "庆余年 第二季", "", "", "", "");
        JsonObject detail = new JsonObject();

        String key = TmdbDetailCache.put(item, detail, List.of(), "zh-CN");

        assertNull(TmdbDetailCache.take(key, other, "zh-CN"));
    }

    @Test
    public void takeCarriesResolvedSeasonEpisodesForPlaybackReuse() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();
        Map<Integer, List<TmdbEpisode>> seasons = new LinkedHashMap<>();
        List<TmdbEpisode> seasonOne = new ArrayList<>();
        seasonOne.add(new TmdbEpisode(1, "初入京都", "2026-10-06", "", "", 0d, 44, 123, 1));
        seasons.put(1, seasonOne);

        String key = TmdbDetailCache.put(item, detail, List.of(), "zh-CN", seasons);
        seasonOne.clear();

        TmdbDetailCache.Entry entry = TmdbDetailCache.take(key, item, "zh-CN");

        assertEquals(1, entry.getSeasonEpisodes().size());
        assertEquals(1, entry.getSeasonEpisodes().get(1).size());
        assertEquals("初入京都", entry.getSeasonEpisodes().get(1).get(0).getTitle());
    }

    @Test
    public void seasonEpisodesSnapshotIsDefensiveAndDropsEmptySeasons() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();
        Map<Integer, List<TmdbEpisode>> seasons = new LinkedHashMap<>();
        seasons.put(1, List.of(new TmdbEpisode(1, "初入京都", "", "", "", 0d, 0, 123, 1)));
        seasons.put(2, List.of());

        String key = TmdbDetailCache.put(item, detail, List.of(), "zh-CN", seasons);
        TmdbDetailCache.Entry entry = TmdbDetailCache.take(key, item, "zh-CN");
        Map<Integer, List<TmdbEpisode>> handed = entry.getSeasonEpisodes();

        handed.clear();

        assertEquals(1, entry.getSeasonEpisodes().size());
        assertFalse(entry.getSeasonEpisodes().containsKey(2));
    }

    @Test
    public void negativeSeasonKeysAreDropped() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();
        Map<Integer, List<TmdbEpisode>> seasons = new LinkedHashMap<>();
        seasons.put(-1, List.of(new TmdbEpisode(1, "错季", "", "", "", 0d, 0, 123, -1)));
        seasons.put(1, List.of(new TmdbEpisode(1, "初入京都", "", "", "", 0d, 0, 123, 1)));

        TmdbDetailCache.Entry entry = TmdbDetailCache.take(
                TmdbDetailCache.put(item, detail, List.of(), "zh-CN", seasons), item, "zh-CN");

        assertEquals(1, entry.getSeasonEpisodes().size());
        assertFalse(entry.getSeasonEpisodes().containsKey(-1));
    }

    @Test
    public void handedEpisodeListsAreImmutable() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();
        Map<Integer, List<TmdbEpisode>> seasons = new LinkedHashMap<>();
        List<TmdbEpisode> mutable = new ArrayList<>();
        mutable.add(new TmdbEpisode(1, "初入京都", "", "", "", 0d, 0, 123, 1));
        seasons.put(1, mutable);

        TmdbDetailCache.Entry entry = TmdbDetailCache.take(
                TmdbDetailCache.put(item, detail, List.of(), "zh-CN", seasons), item, "zh-CN");
        // 详情页在交出快照后继续改自己的列表，不得影响播放页看到的内容
        mutable.add(new TmdbEpisode(2, "外部追加", "", "", "", 0d, 0, 123, 1));

        assertEquals(1, entry.getSeasonEpisodes().get(1).size());
        assertThrows(UnsupportedOperationException.class,
                () -> entry.getSeasonEpisodes().get(1).add(new TmdbEpisode(2, "内部追加", "", "", "", 0d, 0, 123, 1)));
    }

    @Test
    public void takeWithoutSeasonEpisodesReportsNoHandoff() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();

        String key = TmdbDetailCache.put(item, detail, List.of(), "zh-CN");

        assertNull(TmdbDetailCache.take(key, item, "zh-CN").getSeasonEpisodes());
    }

    // ── 交接契约的消费端（TmdbUIAdapter 源码守卫） ──

    @Test
    public void adapterSeedsHandedEpisodesWithTheSameKeySeasonEpisodesReads() throws Exception {
        String source = readAdapterSource();
        String load = method(source, "public void load(TmdbItem item, Vod vod) {", "private void attachPrefetch");
        String loadCached = method(source, "public void load(TmdbItem item, Vod vod, TmdbDetailCache.Entry cached)", "private void seedSeasonEpisodes");
        String seed = method(source, "private void seedSeasonEpisodes", "public void loadSource");

        // 两条详情快照入口都要预填，漏掉任意一条都会让对应路径退化成纯文本选集
        assertTrue(load.contains("seedSeasonEpisodes(cached);"));
        assertTrue(loadCached.contains("seedSeasonEpisodes(cached);"));
        // 键必须与 seasonEpisodes() 的读取键同源，否则填了也命中不了
        assertTrue(seed.contains("seasonEpisodeKey(tmdbItem, entry.getKey())"));
        assertTrue(seed.contains("seasonEpisodeCache.put("));
        assertTrue(seed.contains("List.copyOf(entry.getValue())"));
        assertTrue(seed.contains("cached.getSeasonEpisodes()"));
        // 交接只复用详情页快照，不得在播放页再发一次同样的请求
        assertFalse(seed.contains("tmdbService."));
    }

    @Test
    public void adapterRebindsEmbeddedEpisodesWhenLoadingFromSourceBundle() throws Exception {
        String source = readAdapterSource();
        String loadSource = method(source, "public void loadSource(TmdbBundle bundle", "private void applySourceBundle");

        // applySourceBundle 只把内嵌分季写进缓存，并不会给 Episode 挂 tmdbEpisode；
        // 不补一次绑定，内嵌 payload 的站点在播放页永远是纯文本选集。
        assertTrue(loadSource.contains("if (tmdbItem != null && tmdbItem.isTv()) scheduleStartupEpisodeLoad(sourceVod, tmdbItem, generation);"));
        assertFalse(loadSource.contains("tmdbService."));
    }

    @Test
    public void adapterSingleSeasonFallbackIsLimitedToEvidenceFreeSources() throws Exception {
        String source = readAdapterSource();
        String current = method(source, "private Integer currentEpisodeMetadataSeason()", "private Integer soleOrdinarySeason()");
        String sole = method(source, "private Integer soleOrdinarySeason()", "private boolean isMultiSliceResolution()");

        // 解析器已经给出结论时直接采用
        assertTrue(current.contains("TmdbSeasonResolver.Status.RESOLVED"));
        assertTrue(current.contains("return soleOrdinarySeason();"));
        // 恰好一个正片季才兜底；多季剧返回 null，行为与旧实现一致
        assertTrue(sole.contains("if (sole != null) return null;"));
        assertTrue(sole.contains("option.getSeasonNumber() <= 0"));
        // MULTI_SLICE 的分段结论优先：兜底若返回非 null，调度侧会改走单季绑定，
        // 而切片路径的季号一致性校验期望 null，最终整个分段一集都绑不上
        assertTrue(sole.contains("if (isMultiSliceResolution()) return null;"));
        // 兜底只允许「解析器无结论且无相反证据」的来源：
        // MANUAL_FLAT 是用户「保持原始剧集列表」的显式选择，覆盖后播放页会挂 TMDB 集标题并原地重排选集；
        // TITLE/EXPLICIT 冲突表示源站剧名或集名明确指向 TMDB 不存在的季，覆盖会静默绑错季。
        assertTrue(sole.contains("if (source != TmdbSeasonResolver.Source.REQUEST && source != TmdbSeasonResolver.Source.NONE) return null;"));
    }

    private static String readAdapterSource() throws Exception {
        return Files.readString(root().resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java")), StandardCharsets.UTF_8);
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static Path root() {
        return Files.exists(Path.of("app", "src", "main")) ? Path.of(".") : Path.of("..");
    }
}
