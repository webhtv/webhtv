package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TmdbImageSelectorTierTest {

    private static final String POSTER_ORIGINAL = "https://image.tmdb.org/t/p/original/abc.jpg";

    @Test
    public void tierUrlReplacesTmdbSizeSegment() {
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg",
                TmdbImageSelector.tierUrl(POSTER_ORIGINAL, "w500"));
        assertEquals("https://image.tmdb.org/t/p/w1280/abc.jpg",
                TmdbImageSelector.tierUrl("https://image.tmdb.org/t/p/w780/abc.jpg", "w1280"));
    }

    @Test
    public void tierUrlLeavesUnknownHostsAndEmptyValuesAlone() {
        assertEquals("https://cdn.example.com/poster.jpg",
                TmdbImageSelector.tierUrl("https://cdn.example.com/poster.jpg", "w500"));
        assertEquals("", TmdbImageSelector.tierUrl("", "w500"));
        assertEquals(POSTER_ORIGINAL, TmdbImageSelector.tierUrl(POSTER_ORIGINAL, ""));
    }

    /**
     * 代理线路（wsrv 等）把原始地址放在 query 里，仍包含 /t/p/ 段，必须同样能换档，
     * 否则开启图片代理后所有 rail 会退回原图。
     */
    @Test
    public void tierUrlAlsoWorksForProxyWrappedTmdbUrls() {
        String wrapped = "https://wsrv.nl/?url=https://image.tmdb.org/t/p/original/abc.jpg";
        assertEquals("https://wsrv.nl/?url=https://image.tmdb.org/t/p/w780/abc.jpg",
                TmdbImageSelector.tierUrl(wrapped, "w780"));
    }

    @Test
    public void railTiersUseOfficialSizesAndStayRestorableToOriginal() {
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", TmdbImageSelector.posterTier(POSTER_ORIGINAL));
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", TmdbImageSelector.photoTier(POSTER_ORIGINAL));
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", TmdbImageSelector.heroPosterTier(POSTER_ORIGINAL));
        assertEquals(POSTER_ORIGINAL, TmdbImageSelector.originalUrl(TmdbImageSelector.posterTier(POSTER_ORIGINAL)));
        assertEquals(POSTER_ORIGINAL, TmdbImageSelector.originalUrl(TmdbImageSelector.photoTier(POSTER_ORIGINAL)));
        assertEquals(POSTER_ORIGINAL, TmdbImageSelector.originalUrl(TmdbImageSelector.heroPosterTier(POSTER_ORIGINAL)));
    }
}
