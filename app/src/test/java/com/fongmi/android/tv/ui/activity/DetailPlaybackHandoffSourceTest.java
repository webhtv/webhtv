package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 详情页出播必须把"用户选中的季-集-版本"作为单一身份传给播放页：
 * 集名不再由刮削 mark 文本冒充，季选择不被"该季第一集"覆盖，从播放页返回不覆盖手选。
 */
public class DetailPlaybackHandoffSourceTest {

    private static final String DETAIL = "app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java";

    @Test
    public void playbackEpisodeIdentityIsTheOnlySourceOfEpisodeNameAndUrl() throws Exception {
        String detail = read(DETAIL);
        String identity = method(detail, "private Episode playbackEpisode()");
        String name = method(detail, "private String playbackEpisodeName()");
        String url = method(detail, "private String playbackEpisodeUrl()");
        String mark = method(detail, "private String playbackMark()");
        String titles = method(detail, "private ArrayList<String> fastPlaybackEpisodeTitles()");

        assertFalse("playbackEpisodeName must not fall back to the scraped mark text", name.contains("playbackMark"));
        assertTrue(name.contains("playbackEpisode()"));
        assertTrue(name.contains("episode.getName()"));
        assertTrue(url.contains("playbackEpisode()"));
        assertTrue(url.contains("episode.getUrl()"));
        assertTrue(mark.contains("playbackEpisode()"));
        assertTrue(titles.contains("playbackEpisode()"));
        assertFalse(titles.contains("episodeNumberForHistory(selectedEpisode)"));

        assertTrue("selected episode wins over every other identity channel", identity.contains("if (selectedEpisode != null) return selectedEpisode;"));
        int intentUrl = identity.indexOf("findEpisodeByUrl(getIntentPlaybackEpisodeUrl()");
        int savedIdentity = identity.indexOf("history.getEpisode()");
        assertTrue("intent episode url is the first identity channel", intentUrl >= 0);
        assertTrue("history episode identity is the fallback channel", savedIdentity > intentUrl);
        assertTrue(identity.contains("selectedFlag.find(saved, true)"));
        assertTrue("history lookup stays null-safe", identity.contains("if (saved == null || episodes == null || episodes.isEmpty()) return null;"));
    }

    @Test
    public void renderEpisodesNeverReplacesTheSelectedEpisodeWithTheFirstVisibleOne() throws Exception {
        String render = method(read(DETAIL), "private void renderEpisodes()");

        assertFalse("the first visible episode must never overwrite an existing selection", render.contains("selectedEpisode = visibleEpisodes.get(0)"));
        assertTrue(render.contains("seasonForEpisode(selectedEpisode, episodes)"));
        assertTrue("the season is derived from the selected episode and is -1 only when no season is available",
                render.contains("availableSeasons.isEmpty() ? -1 : seasonForEpisode(selectedEpisode, episodes)"));
        int intent = render.indexOf("findIntentPlaybackEpisode(selectedFlag)");
        int firstEpisode = render.indexOf("selectedEpisode = episodes.get(0)");
        assertTrue(intent >= 0 && firstEpisode > intent);
    }

    @Test
    public void reloadedHistorySelectionReDerivesTheEpisodeAfterFlagSelection() throws Exception {
        String reloaded = method(read(DETAIL), "private void applyReloadedHistorySelection()");

        assertTrue("manual selection, inline playback and started inline sessions still block the reload",
                reloaded.contains("if (playbackSelectionTouched || detailPlayerActive || inlineStarted"));
        int flagSelection = reloaded.indexOf("renderFlagSelection();");
        int episodes = reloaded.indexOf("renderEpisodes();");
        assertTrue(flagSelection >= 0);
        assertTrue("the reloaded history must re-derive the episode instead of leaving it null", episodes > flagSelection);
    }

    @Test
    public void returningFromThePlayerKeepsTheManualDetailSelection() throws Exception {
        String refresh = method(read(DETAIL), "private void refreshSelectionAfterExternalPlayback()");

        assertTrue("a manual detail-page selection must survive the return from the player",
                refresh.contains("if (playbackSelectionTouched || vod == null || vod.getFlags() == null || vod.getFlags().isEmpty()) return;"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature + " must exist", start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue(signature + " must terminate", end > start);
        return source.substring(start, end);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
