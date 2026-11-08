package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 验证自定义线路保存→重读后 imageRouteMode/apiRouteMode 仍为 custom 且不被误判为内置线路。 */
public class TmdbConfigCustomRouteModeTest {

    private static TmdbConfig roundTrip(String json) {
        return TmdbConfig.objectFrom(TmdbConfig.objectFrom(json).toJson());
    }

    @Test
    public void customImageRoute_survivesRoundTrip_withHttpsUrl() {
        TmdbConfig config = roundTrip("{\"imageBase\":\"https://images.mytest.example.com\",\"imageRouteMode\":\"custom\",\"imageRouteConfigured\":true,\"apiKey\":\"k\"}");
        // sanitize 会把 /t/p/w342 附加到自定义图片 base 上，getImageHost 则剥回主机
        assertEquals("https://images.mytest.example.com/t/p/w342", config.getConfiguredImageBase());
        assertFalse(config.isImageAuto());
        assertFalse(config.isImageRouteDefault());
        assertEquals("https://images.mytest.example.com", config.getImageHost());
    }

    @Test
    public void customImageRoute_survivesRoundTrip_withBareDomain() {
        TmdbConfig config = roundTrip("{\"imageBase\":\"images.mytest.example.com\",\"imageRouteMode\":\"custom\",\"imageRouteConfigured\":true,\"apiKey\":\"k\"}");
        assertFalse(config.isImageAuto());
        assertFalse(config.isImageRouteDefault());
        assertEquals("https://images.mytest.example.com", config.getImageHost());
    }

    @Test
    public void customApiRoute_survivesRoundTrip() {
        TmdbConfig config = roundTrip("{\"apiBase\":\"https://api.mytest.example.com\",\"apiRouteMode\":\"custom\",\"apiRouteConfigured\":true,\"apiKey\":\"k\"}");
        assertFalse(config.isApiAuto());
        assertFalse(config.isApiRouteDefault());
        assertEquals("https://api.mytest.example.com", config.getApiHost());
    }

    @Test
    public void itv666ImageRoute_roundTrip_staysCustom() {
        // 从 itv666 切到自定义的保存路径: onSave 写入自定义 base + custom mode
        TmdbConfig config = roundTrip("{\"imageBase\":\"http://tmdb.itv666.cc\",\"imageRouteMode\":\"custom\",\"imageRouteConfigured\":true,\"apiKey\":\"k\"}");
        assertEquals("http://tmdb.itv666.cc/t/p/w342", config.getConfiguredImageBase());
        assertEquals("http://tmdb.itv666.cc", config.getImageHost());
    }

    @Test
    public void wsrvWrapperImageRoute_roundTrip() {
        TmdbConfig config = roundTrip("{\"imageBase\":\"https://wsrv.nl/?url=https://image.tmdb.org\",\"imageRouteMode\":\"custom\",\"imageRouteConfigured\":true,\"apiKey\":\"k\"}");
        assertTrue(config.getConfiguredImageBase().startsWith("https://wsrv.nl/?url="));
        assertFalse(config.isImageAuto());
    }
}
