package com.fongmi.android.tv.bean;

import com.github.catvod.utils.Trans;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 分季缓存键必须与界面语言无关。
 *
 * <p>场景来源：用户反馈「繁体语言的场景下会导致 TMDB 匹配失败」。站源标题在繁体界面下会被
 * {@code Vod.trans()} 逐字转成繁体，而分季缓存键是用站源标题算出来的。键若不归一，
 * 同一部剧在简体界面保存的「分季结论」切到繁体界面就读不回来（反之亦然），
 * 用户看到的就是「繁体的分季选择白做了 / 又要重新选一遍」。
 *
 * <p>用例在改代码前必须失败：{@code sourceKey} 当时只做标点/大小写归一，不做简繁归一。
 */
public class TmdbSeasonMatchCacheHantTest {

    @After
    public void tearDown() {
        Trans.setTraditional(null);
    }

    @Test
    public void simplifiedWriteIsReadableFromTraditionalTitle() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "vod", "庆余年", 100, "tv", 1, TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp", 12, 12);

        assertNotNull("繁体标题必须能读回简体界面保存的分季结论", cache.find("site", "vod", "慶餘年", 100));
        assertEquals(1, cache.find("site", "vod", "慶餘年", 100).getSeasonNumber().intValue());
    }

    @Test
    public void traditionalWriteIsReadableFromSimplifiedTitle() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        Trans.setTraditional(true);
        cache.put("site", "vod", "慶餘年", 100, "tv", 1, TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp", 12, 12);
        Trans.setTraditional(null);

        assertNotNull("切回简体后仍必须读得到繁体界面保存的分季结论", cache.find("site", "vod", "庆余年", 100));
    }

    @Test
    public void legacyKeyWrittenBeforeNormalizationIsStillReadable() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        // 旧版本写入的键没有做简繁归一，这里手工铺一条旧格式条目模拟升级前的缓存。
        cache.getItems().put("site@@@vod@@@庆余年",
                TmdbSeasonMatchCache.Entry.create(200, "tv", 2, TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp", 12, 12));

        assertNotNull("升级前保存的分季结论不能一次性失效", cache.find("site", "vod", "慶餘年", 200));
    }

    @Test
    public void flagScopedKeyIsAlsoLanguageIndependent() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site", "vod", "庆余年", "线路1", 100, "tv", 1,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp", 12, 12);

        assertNotNull(cache.find("site", "vod", "庆余年", "線路1", 100));
    }

    @Test
    public void differentVodsStaySeparatedAfterNormalization() {
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();

        cache.put("site-a", "vod-a", "庆余年", 100, "tv", 1, TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp", 12, 12);
        cache.put("site-b", "vod-b", "赘婿", 200, "tv", 1, TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp", 12, 12);

        // 归一化不能把不同作品折叠到一起；tmdbId 不匹配时也必须读不到。
        assertNull(cache.find("site-a", "vod-a", "慶餘年", 200));
        assertNotNull(cache.find("site-b", "vod-b", "贅婿", 200));
    }
}
