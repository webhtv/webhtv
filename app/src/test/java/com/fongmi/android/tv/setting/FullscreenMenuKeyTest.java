package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「全屏菜单键」新增「下一集」取值的源契约回归：三语言数组必须同长，
 * 播放页与内嵌播放器都必须把新取值接到既有的下一集链路，存量值 0/1 语义不变。
 */
public class FullscreenMenuKeyTest {

    @Test
    public void everyLocaleOffersTheSameThreeOptions() throws Exception {
        String[] locales = {"values", "values-zh-rCN", "values-zh-rTW"};
        String[] expected = {"Next episode", "下一集", "下一集"};
        for (int i = 0; i < locales.length; i++) {
            String block = array(read(res(locales[i], "strings.xml")), "select_fullscreen_menu_key");
            assertEquals(locales[i] + " must offer exactly 3 fullscreen menu key options",
                    3, count(block, "<item>"));
            assertTrue(locales[i] + " must keep the new option last so stored values 0/1 keep their meaning",
                    block.contains("<item>" + expected[i] + "</item>"));
        }
    }

    @Test
    public void storedValueIsClampedToKnownOptions() throws Exception {
        String setting = read(main("java", "com", "fongmi", "android", "tv", "setting", "Setting.java"));

        assertTrue(setting.contains("public static final int FULLSCREEN_MENU_KEY_CONTROL = 0;"));
        assertTrue(setting.contains("public static final int FULLSCREEN_MENU_KEY_EPISODES = 1;"));
        assertTrue(setting.contains("public static final int FULLSCREEN_MENU_KEY_NEXT_EPISODE = 2;"));
        assertTrue("an unknown stored value must fall back to the control bar instead of indexing past the array",
                setting.contains("menuKey > FULLSCREEN_MENU_KEY_NEXT_EPISODE ? FULLSCREEN_MENU_KEY_CONTROL : menuKey"));
    }

    @Test
    public void leanbackMenuKeyAdvancesThroughTheExistingNextEpisodePath() throws Exception {
        String source = read(leanback("java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));

        String branch = window(source, "if (isFullscreen() && KeyUtil.isMenuKey(event)) {", 500);
        assertTrue("the menu key must dispatch the new option", branch.contains("FULLSCREEN_MENU_KEY_NEXT_EPISODE"));
        assertTrue("the new option must use its own entry point", branch.contains("onMenuNextEpisode()"));
        assertTrue("the existing options must keep working", branch.contains("FULLSCREEN_MENU_KEY_EPISODES) onEpisodes()")
                && branch.contains("else onToggle();"));

        String helper = window(source, "private void onMenuNextEpisode() {", 500);
        assertTrue("advancing must reuse the control-bar/auto-advance path", helper.contains("checkNext();"));
        assertTrue("a detail-less/fast playback page must not crash on a null flag", helper.contains("flag == null"));
        assertTrue("an empty episode list must not fake a next episode", helper.contains("flag.getEpisodes().isEmpty()"));
        assertFalse("advancing must go through checkNext() so reverse play still picks the previous index", helper.contains("onNext("));
    }

    @Test
    public void inlinePlayerHonoursTheSameOption() throws Exception {
        String source = read(main("java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));

        String branch = window(source,
                "if (KeyUtil.isMenuKey(event) && (inlineFullscreen || isInlineControlsVisible())) {", 600);
        assertTrue("the inline player must dispatch the new option", branch.contains("FULLSCREEN_MENU_KEY_NEXT_EPISODE"));
        assertTrue("the inline player must reuse its null-safe next-episode path", branch.contains("checkInlineNext();"));
    }

    private static String array(String strings, String name) {
        String start = "<string-array name=\"" + name + "\">";
        int from = strings.indexOf(start);
        int to = strings.indexOf("</string-array>", from);
        assertTrue(name + " must stay a string-array", from >= 0 && to > from);
        return strings.substring(from, to);
    }

    private static int count(String source, String needle) {
        int total = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) total++;
        return total;
    }

    private static String window(String source, String marker, int size) {
        int from = source.indexOf(marker);
        assertTrue("missing source marker: " + marker, from >= 0);
        return source.substring(from, Math.min(source.length(), from + size));
    }

    private static Path res(String values, String name) {
        return root().resolve(Path.of("src", "main", "res", values, name));
    }

    private static Path main(String... rest) {
        return root().resolve(Path.of("src", "main", String.join("/", rest)));
    }

    private static Path leanback(String... rest) {
        return root().resolve(Path.of("src", "leanback", String.join("/", rest)));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path root() {
        return Files.exists(Path.of("src", "main", "java")) ? Path.of(".") : Path.of("app");
    }
}
