package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackSelectionLocatorSourceTest {

    @Test
    public void bothPlayersLocateSeamlessEpisodeByIdentityFirst() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String body = methodBody(readVideo(mode), "private boolean seamless(Flag flag)");
            assertTrue(mode + " must resolve the history episode identity first", body.contains("Episode episode = mHistory == null ? null : flag.find(mHistory.getEpisode(), true);"));
            assertTrue(mode + " must keep the scraped mark only as a fallback", body.contains("if (episode == null && !getMark().isEmpty()) episode = flag.find(getMark(), false);"));
            assertFalse(mode + " must not branch on an empty mark before the identity lookup", body.contains("getMark().isEmpty() ?"));
            assertFalse(mode + " must not resolve by remarks text without the episode url", body.contains("flag.find(mHistory.getVodRemarks(), false)"));
            assertTrue(mode + " must keep the existing playback guard", body.contains("(isPlaybackFlag(flag) && episode.isSelected())"));
        }
    }

    @Test
    public void mobileSeamlessOnlyRewritesHistoryUrlForTheSameIdentity() throws Exception {
        String body = methodBody(readVideo("mobile"), "private boolean seamless(Flag flag)");
        assertTrue("mobile must gate the url write on the history identity", body.contains("boolean sameHistoryIdentity = TextUtils.isEmpty(mHistory.getEpisodeUrl()) || episode.matchesPlayback(mHistory.getEpisode(), flag.containsEpisodeUrl(mHistory.getEpisode()));"));
        assertTrue("mobile must write the url only through that gate", body.contains("if (sameHistoryIdentity) mHistory.setEpisodeUrl(episode.getUrl());"));
        assertFalse("mobile must not write the resolved url unconditionally", body.contains("\n        mHistory.setEpisodeUrl"));
        assertTrue("the identity gate must be evaluated before any history field is written", body.indexOf("boolean sameHistoryIdentity") < body.indexOf("mHistory.setVodRemarks("));
        assertTrue(body.contains("onItemClick(episode);"));
    }

    @Test
    public void mobileSelectedPositionUsesLeanbackThreeLevelFallback() throws Exception {
        String mobile = methodBody(readVideo("mobile"), "private int getSelectedEpisodePosition(List<Episode> items)");
        String leanback = methodBody(readVideo("leanback"), "private int getSelectedEpisodePosition(List<Episode> episodes)");
        assertTrue("mobile must keep the url exact match channel", mobile.contains("if (!TextUtils.equals(historyEpisode.getUrl(), items.get(i).getUrl())) continue;"));
        assertTrue("mobile must keep the tmdb number cross check", mobile.contains("!items.get(i).matchesNumber(historyEpisode)) continue;"));
        assertTrue("mobile must keep the playback identity fallback", mobile.contains("if (items.get(i).matchesPlayback(historyEpisode)) return i;"));
        assertTrue("mobile must keep the history short circuit", mobile.contains("if (mHistory == null) return 0;"));
        assertTrue("mobile must keep the empty url break", mobile.contains("if (TextUtils.isEmpty(historyEpisode.getUrl())) break;"));
        assertTrue("url exact match must precede the identity fallback", mobile.indexOf("matchesNumber(historyEpisode)") < mobile.indexOf("matchesPlayback(historyEpisode)"));
        assertTrue("the identity fallback must precede the default position", mobile.indexOf("matchesPlayback(historyEpisode)") < mobile.lastIndexOf("return 0;"));
        assertTrue("leanback must keep the tmdb number cross check", leanback.contains("!episodes.get(i).matchesNumber(historyEpisode)) continue;"));
        assertTrue("leanback must keep the playback identity fallback", leanback.contains("if (episodes.get(i).matchesPlayback(historyEpisode)) return i;"));
    }

    @Test
    public void fastTmdbPlaybackNoLongerFallsBackToTheFirstItem() throws Exception {
        String source = readVideo("leanback");
        assertTrue(methodBody(source, "private Flag findFastTmdbPlaybackFlag(Vod item)").contains("return findIntentPlaybackFlag("));
        assertTrue(methodBody(source, "private Episode findFastTmdbPlaybackEpisode(Flag flag)").contains("return findIntentPlaybackEpisode("));
        String pending = methodBody(source, "private void startPendingFastTmdbPlayback()");
        assertTrue("the unresolved fast playback must keep the normal chain fallback", pending.contains("if (flag == null || episode == null || TextUtils.isEmpty(episode.getUrl())) {") && pending.contains("applyCachedTmdbDetailNextFrame(item);"));
    }

    @Test
    public void intentPlaybackEpisodeFallsBackToTheHistoryIdentity() throws Exception {
        String body = methodBody(readVideo("leanback"), "private Episode findIntentPlaybackEpisode(Flag flag, String playName, String playUrl)");
        int url = body.indexOf("if (!TextUtils.isEmpty(playUrl)) {");
        int name = body.indexOf("Episode episode = flag.find(playName, true);");
        int history = body.indexOf("return mHistory == null ? null : flag.find(mHistory.getEpisode(), true);");
        assertTrue("url exact match must come first", url >= 0 && url < name);
        assertTrue("the play name must be tried before the history rescue", name > 0 && name < history);
        assertTrue("the history episode identity must be the last channel", history > 0);
    }

    private static String readVideo(String mode) throws Exception {
        return read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing method: " + signature, start >= 0);
        int open = source.indexOf('{', start + signature.length());
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced method body: " + signature);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
