package com.fongmi.android.tv.bean;

import com.github.catvod.utils.Trans;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * TMDB 匹配缓存的键必须与界面语言无关。
 *
 * <p>场景来源：用户反馈「繁体语言的场景下会导致 TMDB 匹配失败」。根因之一在缓存：
 * 站源标题在繁体界面下会被 {@code Vod.trans()} 改成繁体，而缓存键是用标题算出来的。
 * 键若不归一，同一部作品在简体界面写入、切到繁体界面就读不回来，每次切语言都要重新匹配一遍，
 * 表现为「繁体下TMDB 一直匹配不上」。
 *
 * <p>归一方向只能用 {@code Trans.s2t}（简→繁）：反向 {@code t2s} 在「多简对一繁」的
 * 10 组字上会坍缩，把「余罪 / 强势 / 线上」这类常用简体词改写成异体字。
 */
public class TmdbMatchCacheHantTest {

    @After
    public void tearDown() {
        Trans.setTraditional(null);
    }

    @Test
    public void simplifiedWriteIsReadableFromTraditionalTitle() {
        TmdbMatchCache cache = new TmdbMatchCache();

        cache.put("site", "vod", "庆余年", item(100, "庆余年"));

        assertEquals(100, cache.find("site", "vod", "慶餘年").getTmdbId());
    }

    @Test
    public void traditionalWriteIsReadableFromSimplifiedTitle() {
        TmdbMatchCache cache = new TmdbMatchCache();

        Trans.setTraditional(true);
        cache.put("site", "vod", "慶餘年", item(100, "庆余年"));

        assertEquals(100, cache.find("site", "vod", "庆余年").getTmdbId());
    }

    @Test
    public void manualMatchSurvivesScriptSwitch() {
        TmdbMatchCache cache = new TmdbMatchCache();

        cache.putManual("site", "vod", java.util.List.of("庆余年 更新至第30集"), item(100, "庆余年"));

        // 校验手动选择不会被自动匹配覆盖，且繁体标题仍读得到。
        assertNotNull(cache.findManual("site", "vod", "慶餘年"));
        assertEquals(100, cache.findManual("site", "vod", "慶餘年").getTmdbId());
    }

    @Test
    public void legacyCacheWrittenBeforeNormalizationIsStillReadable() {
        TmdbMatchCache cache = new TmdbMatchCache();

        // 旧版本写入的键没有做简繁归一，这里手工铺一条旧格式条目模拟升级前的缓存。
        cache.getItems().put("site@@@vod@@@慶餘年", TmdbMatchCache.Entry.from(item(200, "庆余年"), "zh-CN"));

        assertEquals(200, cache.find("site", "vod", "慶餘年").getTmdbId());
        assertEquals(200, cache.find("site", "vod", "庆余年").getTmdbId());
    }

    @Test
    public void differentTitlesStaySeparatedAfterNormalization() {
        TmdbMatchCache cache = new TmdbMatchCache();

        cache.put("site-a", "vod-a", "庆余年", item(100, "庆余年"));
        cache.put("site-b", "vod-b", "赘婿", item(200, "赘婿"));

        assertEquals(100, cache.find("site-a", "vod-a", "慶餘年").getTmdbId());
        assertEquals(200, cache.find("site-b", "vod-b", "贅婿").getTmdbId());
    }

    private static TmdbItem item(int id, String title) {
        return new TmdbItem(id, "tv", title, "", "", "", "");
    }
}