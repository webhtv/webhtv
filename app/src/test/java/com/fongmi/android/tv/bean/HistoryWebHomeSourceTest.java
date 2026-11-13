package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** WebHome 嗅探历史是合成站点，不能套普通站点的失效判定。 */
public class HistoryWebHomeSourceTest {

    @Test
    public void webHomeInlineHistoryDoesNotUseRegularSiteAvailability() throws Exception {
        String history = read(source("main", "java", "com", "fongmi", "android", "tv", "bean", "History.java"));
        String siteName = method(history, "public String getSiteName()", "public boolean isCurrentSourceAvailable()");
        assertTrue(siteName.contains("WebHomeInlineVodStore.KEY.equals(getSiteKey())"));
        assertTrue(siteName.contains("history_source_webhome"));
        assertFalse("WebHome inline 分支不能落到普通站点的失效文案",
                slice(siteName, "WebHomeInlineVodStore.KEY.equals(getSiteKey())",
                        "VodConfig.get().getSites().isEmpty()").contains("history_source_unavailable"));

        String available = method(history, "public boolean isCurrentSourceAvailable()", "public String getSiteKey()");
        assertTrue(available.contains("WebHomeInlineVodStore.KEY.equals(getSiteKey())"));
    }

    @Test
    public void historyArtworkFallsBackToWebHomeDetailPicture() throws Exception {
        String history = read(source("main", "java", "com", "fongmi", "android", "tv", "bean", "History.java"));
        String pic = method(history, "public String getVodPic()", "public void setVodPic");
        assertTrue(pic.contains("if (!TextUtils.isEmpty(vodPic)) return vodPic;"));
        assertTrue(pic.contains("WebHomeInlineVodStore.getPic(getVodId())"));
    }

    @Test
    public void webHomeMissingArtworkFallsBackToRenderedFrameSnapshot() throws Exception {
        String base = read(source("main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        assertTrue(base.contains("WEBHOME_SNAPSHOT_DELAYS = {2500L, 2000L, 2000L}"));
        assertTrue(base.contains("scheduleWebHomeSnapshot()"));
        assertTrue(base.contains("PlaybackFrameSnapshot.capture"));
        assertTrue(base.contains("WebHomeInlineVodStore.setPic"));

        String mobile = read(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        assertTrue(mobile.contains("protected History getPlaybackHistory()"));
        assertTrue(leanback.contains("protected History getPlaybackHistory()"));

        String snapshot = read(source("main", "java", "com", "fongmi", "android", "tv", "player", "PlaybackFrameSnapshot.java"));
        assertTrue(snapshot.contains("PixelCopy.request"));
        assertTrue(snapshot.contains("isMostlyBlack"));
        assertTrue(snapshot.contains("webhome_snapshot"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing slice start: " + start, from >= 0);
        assertTrue("Missing slice end: " + end, to > from);
        return source.substring(from, to);
    }

    private static String method(String source, String start, String end) {
        return slice(source, start, end);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path source(String... parts) {
        Path path = Path.of("src");
        for (String part : parts) path = path.resolve(part);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src");
        for (String part : parts) path = path.resolve(part);
        return path;
    }
}
