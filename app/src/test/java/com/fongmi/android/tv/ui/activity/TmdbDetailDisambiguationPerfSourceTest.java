package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 锁定 TMDB 详情加载的两项性能契约（不涉及判定阈值）：
 * <ol>
 *   <li>自动匹配消歧只拉 lite 详情（credits/aggregate_credits）并有界并行；赢家才回到完整详情；</li>
 *   <li>rail 缩略图走官方小档位，hero/viewer/保存仍拿 original。</li>
 * </ol>
 */
public class TmdbDetailDisambiguationPerfSourceTest {

    private static final Path SOURCE =
            Paths.get("src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

    @Test
    public void disambiguationFetchesLiteDetailsConcurrentlyAndUpgradesOnlyTheWinner() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertTrue("bounded concurrency constant must exist",
                source.contains("private static final int DISAMBIGUATION_CONCURRENCY = 3;"));

        String fetch = methodBody(source, "private JsonObject[] fetchDisambiguationDetails(");
        assertFalse("fetch helper must exist", fetch.isEmpty());
        assertTrue("candidate details must come from the lite endpoint",
                fetch.contains("detailForDisambiguation(item, tmdbConfig)"));
        assertTrue("candidate fetches must be bounded to the concurrency window",
                fetch.contains("start += DISAMBIGUATION_CONCURRENCY"));
        assertFalse("candidate fetches must not load the full bundle", fetch.contains("loadTmdbBundle("));

        String strict = methodBody(source, "private TmdbBundle chooseTmdbBundle(");
        assertFalse("chooseTmdbBundle must exist", strict.isEmpty());
        assertFalse("strict disambiguation must not fetch full details per candidate", strict.contains("loadTmdbBundle("));
        assertTrue(strict.contains("fetchDisambiguationDetails(candidates, count)"));
        assertTrue(strict.contains("loadDisambiguationWinner(best.item)"));

        String smart = methodBody(source, "private TmdbBundle chooseSmartTmdbBundle(");
        assertFalse("chooseSmartTmdbBundle must exist", smart.isEmpty());
        assertFalse("smart disambiguation must not fetch full details per candidate", smart.contains("loadTmdbBundle("));
        assertTrue(smart.contains("fetchDisambiguationDetails(candidates, count)"));
        assertTrue(smart.contains("loadDisambiguationWinner(best.item)"));

        String upgrade = methodBody(source, "private TmdbBundle loadDisambiguationWinner(");
        assertFalse("winner upgrade helper must exist", upgrade.isEmpty());
        assertTrue("the winner must be upgraded to the full detail bundle", upgrade.contains("loadTmdbBundle(item)"));
    }

    @Test
    public void railsUseOfficialSmallTiersWhileHeroViewerAndSavingKeepOriginal() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        String posters = methodBody(source, "private void setRailPosters(");
        assertFalse("setRailPosters must exist", posters.isEmpty());
        assertTrue(posters.contains("TmdbImageSelector.posterTier(url)"));

        String photos = methodBody(source, "private void setRailPhotos(");
        assertFalse("setRailPhotos must exist", photos.isEmpty());
        assertTrue(photos.contains("TmdbImageSelector.photoTier(url)"));

        String bundle = methodBody(source, "private void applyTmdbBundle(");
        assertTrue("detail bundle must route rails through the tiering helpers",
                bundle.contains("setRailPosters(") && bundle.contains("setRailPhotos("));

        String initialArtwork = methodBody(source, "private void bindInitialArtwork(");
        assertTrue("initial hero poster must use the tiered URL", initialArtwork.contains("heroPosterTier("));
        assertFalse("initial hero poster must not download the original",
                initialArtwork.contains("TmdbImageSelector.originalUrl("));

        String posterUrl = methodBody(source, "private String tmdbPosterUrl(");
        assertTrue("hero poster URL must be tiered", posterUrl.contains("heroPosterTier("));

        String highRes = methodBody(source, "private String highResTmdbImage(");
        assertTrue("fullscreen viewer/save path must keep upgrading to original",
                highRes.contains("TmdbImageSelector.originalUrl(url)"));

        String viewer = methodBody(source, "private void loadPhotoImage(");
        assertTrue("photo viewer must still request the original", viewer.contains("highResTmdbImage(url)"));
        assertTrue("saving must still request the original",
                source.contains("TmdbImageSaver.save(this, highResTmdbImage(url)"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        return "";
    }
}
