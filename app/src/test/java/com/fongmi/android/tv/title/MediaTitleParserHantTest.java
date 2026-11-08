package com.fongmi.android.tv.title;

import com.github.catvod.utils.Trans;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 繁体界面下站源标题已经被 {@code Vod.trans()} 转成繁体，标题清洗词表必须同时覆盖繁体写法。
 *
 * <p>场景来源：用户反馈「繁体语言的场景下会导致 TMDB 匹配失败」。繁体态下「慶餘年（高碼）」
 * 这类标题的尾部标记若清洗不掉，会连标记一起当作搜索词发给 TMDB，直接搜出 0 条结果。
 */
public class MediaTitleParserHantTest {

    @After
    public void tearDown() {
        Trans.setTraditional(null);
    }

    @Test
    public void cleanTitle_removesTraditionalQualityTag() {
        assertEquals("慶餘年", new MediaTitleParser().cleanTitle("慶餘年（高碼）"));
    }

    @Test
    public void cleanTitle_removesTraditionalLanguageTag() {
        assertEquals("與鳳行", new MediaTitleParser().cleanTitle("與鳳行 國語中字"));
    }

    @Test
    public void cleanTitle_removesTraditionalSerialTag() {
        assertEquals("贅婿", new MediaTitleParser().cleanTitle("贅婿 連載至第30集"));
    }

    @Test
    public void cleanTitle_removesTraditionalRegionTag() {
        assertEquals("甄嬛傳", new MediaTitleParser().cleanTitle("甄嬛傳 大陸版"));
    }

    @Test
    public void cleanTitle_removesTraditionalBilingualTag() {
        assertEquals("長相思", new MediaTitleParser().cleanTitle("長相思 雙語字幕"));
    }

    @Test
    public void cleanTitle_keepsTraditionalTitleIntact() {
        assertEquals("慶餘年", new MediaTitleParser().cleanTitle("慶餘年"));
    }
}