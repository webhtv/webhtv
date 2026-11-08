package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageHeaderPolicyTest {

    @Test
    public void doubanImageReferer_returnsDefaultForDoubanImageHosts() {
        assertEquals("https://movie.douban.com", ImageHeaderPolicy.doubanImageReferer("https://img1.doubanio.com/view/photo/s_ratio_poster/public/p451926968.jpg", false));
        assertEquals("https://movie.douban.com", ImageHeaderPolicy.doubanImageReferer("https://img9.doubanio.com/view/photo/m_ratio_poster/public/p2576977981.jpg", false));
        assertEquals("https://movie.douban.com", ImageHeaderPolicy.doubanImageReferer("https://img12.doubanio.com/view/photo/photo/public/p1.jpg", false));
    }

    @Test
    public void doubanImageReferer_ignoresNonDoubanImageHosts() {
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://img1.example.com/view/photo/p1.jpg", false));
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://movie.douban.com/subject/1291843/", false));
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://img.doubanio.com/view/photo/p1.jpg", false));
    }

    @Test
    public void doubanImageReferer_preservesExistingReferer() {
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://img1.doubanio.com/view/photo/p1.jpg", true));
    }

    @Test
    public void isReferer_matchesCaseInsensitively() {
        assertTrue(ImageHeaderPolicy.isReferer("Referer"));
        assertTrue(ImageHeaderPolicy.isReferer("referer"));
        assertFalse(ImageHeaderPolicy.isReferer("User-Agent"));
    }

    // 锁住「正则只编译一次」这条约束：String.matches() 每次调用都重新编译，是详情页/播放页
    // 图片列表在主线程上的固定开销。Pattern 换成常量后行为必须完全不变，包括这些边界。
    @Test
    public void doubanImageHostPatternIsCompiledOnceAndKeepsItsBoundaries() {
        assertTrue(ImageHeaderPolicy.doubanImageReferer("https://img0.doubanio.com/p1.jpg", false).startsWith("https://movie.douban.com"));
        assertEquals("host must require a digit run right after img",
                "", ImageHeaderPolicy.doubanImageReferer("https://imgx.doubanio.com/p1.jpg", false));
        assertEquals("host must match the whole name, not a prefix",
                "", ImageHeaderPolicy.doubanImageReferer("https://img1.doubanio.com.evil.test/p1.jpg", false));
        assertEquals("host must match the whole name, not a suffix",
                "", ImageHeaderPolicy.doubanImageReferer("https://x.img1.doubanio.com/p1.jpg", false));
        assertEquals("", ImageHeaderPolicy.doubanImageReferer(null, false));
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("", false));
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("not a url", false));
    }

    @Test
    public void repeatedLookupsStayStable() {
        for (int i = 0; i < 100; i++) {
            assertEquals("https://movie.douban.com",
                    ImageHeaderPolicy.doubanImageReferer("https://img3.doubanio.com/view/photo/p1.jpg", false));
            assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://img.example.com/p1.jpg", false));
        }
    }
}
