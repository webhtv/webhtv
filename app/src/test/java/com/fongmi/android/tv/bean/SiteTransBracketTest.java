package com.fongmi.android.tv.bean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.catvod.utils.Trans;

import org.junit.After;
import org.junit.Test;

/**
 * 站点名里的方括号标签是功能性标识（分组名、音频源/短剧/小说/漫画/TMDB 站点规则），
 * 不能跟着界面语言变形，否则繁体界面下 "荐片[采][广]" 会变成 "薦片[采][廣]"，
 * 标签与用户写的规则不再互等，已保存的分组顺序也会被打散。
 */
public class SiteTransBracketTest {

    @After
    public void tearDown() {
        Trans.setTraditional(null);
    }

    private static Site site(String name) {
        Site site = new Site();
        site.setKey("fixture");
        site.setName(name);
        return site;
    }

    @Test
    public void bracketTagKeepsItsOriginalCharactersWhileTextIsTranslated() {
        Trans.setTraditional(true);
        Site site = site("荐片[采][广]").trans();
        assertEquals("薦片[采][广]", site.getName());
    }

    @Test
    public void translatedTagNeverAppearsInTranslatedName() {
        Trans.setTraditional(true);
        String name = site("荐片[采][广]").trans().getName();
        assertTrue(name.contains("[广]"));
        assertFalse(name.contains("[廣]"));
    }

    @Test
    public void fullWidthBracketTagsAreKeptToo() {
        Trans.setTraditional(true);
        assertEquals("木偶「盘」誇克", site("木偶「盘」夸克").trans().getName());
        assertEquals("【漫】漫畫站", site("【漫】漫画站").trans().getName());
    }

    @Test
    public void nameWithoutTagIsStillFullyTranslated() {
        Trans.setTraditional(true);
        assertEquals("電影天堂", site("电影天堂").trans().getName());
    }

    @Test
    public void tagsThatLookTheSameInBothScriptsStayUntouched() {
        Trans.setTraditional(true);
        assertEquals("貝樂虎[官]", site("贝乐虎[官]").trans().getName());
        assertEquals("熱播[采]", site("热播[采]").trans().getName());
        assertEquals("咪咕音樂[音]", site("咪咕音乐[音]").trans().getName());
    }

    @Test
    public void textOutsideTagsIsStillTranslated() {
        Trans.setTraditional(true);
        assertEquals("[官]中國站", site("[官]中国站").trans().getName());
    }

    @Test
    public void unclosedTagFallsBackToTranslatedText() {
        Trans.setTraditional(true);
        assertEquals("測試[未閉合", site("测试[未闭合").trans().getName());
    }

    @Test
    public void simplifiedModeKeepsNameUnchanged() {
        Trans.setTraditional(false);
        assertEquals("荐片[采][广]", site("荐片[采][广]").trans().getName());
    }

    @Test
    public void userRuleWrittenWithSimplifiedTagStillMatchesTranslatedName() {
        Trans.setTraditional(true);
        String name = site("荐片[采][广]").trans().getName().toLowerCase(java.util.Locale.ROOT);
        // 与 AudioConfig / ShortDramaConfig / TmdbConfig 相同的子串匹配语义
        assertTrue(name.contains("[广]"));
        assertFalse(name.contains("[廣]"));
    }
}
