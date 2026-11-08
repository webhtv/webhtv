package com.fongmi.android.tv.ui.style;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Freezes the list/card geometry contracts introduced by stages C and D. */
public class UiLayoutSourceTest {

    private static final Pattern DIMENSION = Pattern.compile("android:layout_(?:width|height)=\"([0-9]+)dp\"");

    @Test
    public void posterCardsKeepTheTwoByThreeRatio() throws Exception {
        assertRatio("src/main/res/layout/adapter_tmdb_recommendation.xml", 108, 162, 2.0 / 3.0);
        assertRatio("src/main/res/layout/adapter_tmdb_rail_item.xml", 132, 184, 132.0 / 184.0);
    }

    @Test
    public void landscapeCardsKeepTheSixteenByNineRatio() throws Exception {
        assertRatio("src/main/res/layout/adapter_tmdb_recommendation_landscape.xml", 276, 156, 16.0 / 9.0);
        assertRatio("src/main/res/layout/adapter_tmdb_video.xml", 276, 156, 16.0 / 9.0);
        assertRatio("src/leanback/res/layout/adapter_episode_card.xml", 280, 160, 7.0 / 4.0);
    }

    @Test
    public void tvCardsUseTheSharedCardGapTokenAndRoundedFamily() throws Exception {
        String card = withoutComments(
                Files.readString(Path.of("src/leanback/res/layout/adapter_episode_card.xml"), StandardCharsets.UTF_8));
        assertTrue("TV episode cards must keep the shared rounded family",
                card.contains("app:cardCornerRadius=\"@dimen/webhtv_card_radius_default\""));
        assertFalse("TV episode card colours must be token-only", card.matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
        // 间距必须来自 token，不能是第二个会漂移的硬编码数字：webhtv_size_tv_card_gap 曾经
        // 只声明、从未被任何布局或调用点引用，于是 TV 卡片间距实际是各点自定的 8/10/12/16dp。
        assertTrue("TV episode cards must take their gap from the shared token",
                card.contains("android:layout_marginEnd=\"@dimen/webhtv_size_tv_card_gap\""));
        assertFalse("TV episode cards must not hardcode their gap",
                card.matches("(?s).*layout_marginEnd=\"[0-9]+dp\".*"));
        for (String presenter : new String[]{
                "src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java",
                "src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java",
                "src/leanback/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java"
        }) {
            assertTrue(presenter + " must build its card row gap from the shared token",
                    Files.readString(Path.of(presenter), StandardCharsets.UTF_8)
                            .contains("ResUtil.dpOf(R.dimen.webhtv_size_tv_card_gap)"));
        }
    }

    @Test
    public void cardsKeepImageTextOverContentSafeScrim() throws Exception {
        for (String file : new String[]{
                "src/main/res/layout/adapter_tmdb_recommendation.xml",
                "src/main/res/layout/adapter_tmdb_recommendation_landscape.xml",
                "src/leanback/res/layout/adapter_episode_card.xml"
        }) {
            String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            assertTrue(file + " must keep a scrim behind poster text",
                    source.contains("shape_tmdb_episode_overlay")
                            || source.contains("shape_episode_card_overlay")
                            || source.contains("shape_tmdb_recommendation_info"));
        }
    }

    /**
     * XML comments are documentation, not rendered values.
     *
     * <p>An audit comment that quotes a measured hex (the palette it replaced, the contrast it
     * scored) is not a hard-coded colour. Scanning raw text reports a false positive for any file
     * whose only hex sits in a comment, and the fix an author is pressured into is deleting the
     * evidence. Stripping comments first keeps this guard aimed at what actually renders, matching
     * {@code UiStyleSourceTest} and the other source guards in this repository.
     */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
    }

    private static void assertRatio(String file, int width, int height, double expected) throws Exception {
        String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        Matcher matcher = DIMENSION.matcher(source);
        int actualWidth = -1;
        int actualHeight = -1;
        while (matcher.find()) {
            if (actualWidth < 0) actualWidth = Integer.parseInt(matcher.group(1));
            else if (actualHeight < 0) actualHeight = Integer.parseInt(matcher.group(1));
        }
        if (width > 0) assertEquals(file + " width", width, actualWidth);
        if (height > 0) assertEquals(file + " height", height, actualHeight);
        if (expected > 0) assertEquals(file + " ratio", expected, (double) actualWidth / actualHeight, 0.05);
    }
}
