package com.fongmi.android.tv.setting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CustomCspSettingTest {

    @Test
    public void blankSearchMatchesEveryItem() {
        assertTrue(CustomCspSetting.matchesSearch(null, "星河影视", "source-key", "https://example.com/api"));
        assertTrue(CustomCspSetting.matchesSearch("  ", "星河影视", "source-key", "https://example.com/api"));
    }

    @Test
    public void searchMatchesNameKeyAndUrlsIgnoringCase() {
        String[] fields = {"影视仓", "movie-source", "https://Example.com/api", "https://example.com/home"};

        assertTrue(CustomCspSetting.matchesSearch("影视", fields));
        assertTrue(CustomCspSetting.matchesSearch("MOVIE-SOURCE", fields));
        assertTrue(CustomCspSetting.matchesSearch("example.com/api", fields));
        assertTrue(CustomCspSetting.matchesSearch("EXAMPLE.COM/HOME", fields));
        assertFalse(CustomCspSetting.matchesSearch("not-found", fields));
    }

    @Test
    public void searchMatchesLiveUrlAndOtherField() {
        assertTrue(CustomCspSetting.matchesSearch("live.m3u8", "新闻直播", "https://tv.example.com/live.m3u8"));
        assertTrue(CustomCspSetting.matchesSearch("HEADERS", "headers", "token"));
    }
}
