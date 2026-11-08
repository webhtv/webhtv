package com.fongmi.android.tv.ui.dialog;

import com.fongmi.android.tv.theme.ThemeTokens;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression contracts for the TV player-settings screenshots.
 *
 * <p>The old rows reused selector_git_cloud_card: its fixed light fill was
 * measured at 1.22:1 against the TV dark-table label and the focused switch
 * used the same fixed-light palette. These tests keep the player-settings
 * surfaces on one active semantic palette and cover the disabled-label alpha.
 */
public class PlayerSettingsContrastTest {

    @Test
    public void playerSettingsRowsUseTheDedicatedThemeRenderer() throws Exception {
        String leanbackOsdLayout = read("src/leanback/res/layout/adapter_player_osd.xml");
        String mobileOsdLayout = read("src/mobile/res/layout/adapter_player_osd.xml");
        String buttonLayout = read("src/main/res/layout/adapter_player_button_config.xml");
        assertFalse("leanback OSD rows must not use the fixed light GitCloud card", leanbackOsdLayout.contains("selector_git_cloud_card"));
        assertFalse("mobile OSD rows must not use the fixed light GitCloud card", mobileOsdLayout.contains("selector_git_cloud_card"));
        assertFalse("button rows must not use the fixed light GitCloud card", buttonLayout.contains("selector_git_cloud_card"));
        assertTrue("leanback OSD row must expose the row name for palette application", leanbackOsdLayout.contains("@+id/name"));
        assertTrue("mobile OSD row must expose the row name for palette application", mobileOsdLayout.contains("@+id/name"));
        assertTrue("button row must expose the row name for palette application", buttonLayout.contains("@+id/name"));

        assertUsesRenderer("src/leanback/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java");
        assertUsesRenderer("src/mobile/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java");
        assertUsesRenderer("src/main/java/com/fongmi/android/tv/ui/dialog/PlayerButtonConfigDialog.java");
    }

    private static void assertUsesRenderer(String path) throws Exception {
        String source = read(path);
        assertTrue(path + " must apply the active row palette",
                source.contains("PlayerSettingsRowTheme.apply(holder.binding.root, holder.binding.name"));
        assertTrue(path + " must use the active palette for the switch control",
                source.contains("PlayerSettingsRowTheme.applySwitch(holder.binding.visible"));
        assertFalse(path + " must not restore the old low-contrast alpha", source.contains("0.55f"));
    }

    @Test
    public void rowTextAndHiddenStateClearWcagAaOnBothPalettes() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.light(), ThemeTokens.dark()}) {
            int normal = tokens.colorSurfaceContainerHigh();
            int focused = tokens.colorPrimaryContainer();
            assertTrue("normal label must be readable", ratio(tokens.colorOnSurface(), normal) >= 4.5);
            assertTrue("hidden label must be readable", ratio(tokens.colorOnSurfaceVariant(), normal) >= 4.5);
            assertTrue("focused label must be readable", ratio(tokens.colorOnSurface(), focused) >= 4.5);
            assertTrue("focused hidden label must be readable", ratio(tokens.colorOnSurfaceVariant(), focused) >= 4.5);
        }
    }

    @Test
    public void visibilitySwitchClearsTextContrastOnBothPalettes() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.light(), ThemeTokens.dark()}) {
            assertTrue("focused switch icon must be readable",
                    ratio(tokens.colorOnPrimaryContainer(), tokens.colorPrimaryContainer()) >= 4.5);
            assertTrue("normal switch icon must be readable",
                    ratio(tokens.colorOnSurfaceVariant(), tokens.colorSurfaceContainerHighest()) >= 4.5);
        }
    }

    @Test
    public void relatedTvRowsUseSemanticSurfacePairs() throws Exception {
        assertContains("src/leanback/res/layout/adapter_vod.xml", "android:textColor=\"?attr/webhtvColorOnWallpaper\"", "TV history episode remarks must stay readable on the wallpaper-backed card");
        assertContains("src/leanback/res/drawable/shape_ad_rule_card.xml", "?attr/colorSurfaceContainerHigh", "ad-rule cards must use the active surface token");
        assertContains("src/leanback/res/drawable/selector_ad_stats_item.xml", "@dimen/webhtv_focus_ring_width", "ad-stat rows must use the unified focus-ring width");
        assertContains("src/leanback/res/drawable/selector_exit_confirm_secondary.xml", "@dimen/webhtv_focus_ring_width", "exit secondary controls must use the unified focus-ring width");
        assertContains("src/main/res/drawable/selector_dialog_surface_row.xml", "?attr/colorOutlineVariant", "shared dialog rows must use semantic outline");
        assertContains("src/main/res/color/following_button_primary_text.xml", "@color/webhtv_color_on_primary", "following primary actions must use the paired foreground");
        assertContains("src/main/res/color/following_button_secondary_text.xml", "@color/webhtv_color_on_primary", "following focused actions must use the paired foreground");
        assertContains("src/main/res/drawable/shape_login_state_badge.xml", "?attr/colorPrimaryContainer", "login-state badges must use the active primary container");
        assertContains("src/main/java/com/fongmi/android/tv/ui/dialog/CustomCspDialog.java", "ThemeController.current()", "custom CSP dialog must use the active palette");
        assertFalse("custom CSP dialog must not retain fixed light input colors", read("src/main/java/com/fongmi/android/tv/ui/dialog/CustomCspDialog.java").contains("Color.WHITE"));
        assertFalse("custom CSP dialog must not retain fixed Google light row colors", read("src/main/java/com/fongmi/android/tv/ui/dialog/CustomCspDialog.java").contains("#E8F0FE"));
    }

    @Test
    public void dynamicLoginStateDialogColorsUseTheActivePalette() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/ui/dialog/LoginStatePathDialog.java");
        assertTrue(source.contains("ThemeController.current().colorOnSurfaceVariant()"));
        assertTrue(source.contains("ThemeController.current().colorSurfaceContainerHigh()"));
        assertTrue(source.contains("ThemeController.current().colorOutlineVariant()"));
        assertFalse(source.contains("parseColor(\"#5F6368\")"));
        assertFalse(source.contains("parseColor(\"#202124\")"));
    }

    private static void assertContains(String path, String expected, String message) throws Exception {
        assertTrue(message, read(path).contains(expected));
    }


    private static double ratio(int foreground, int background) {
        double first = luminance(foreground);
        double second = luminance(background);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(int color) {
        double red = channel((color >>> 16) & 0xFF);
        double green = channel((color >>> 8) & 0xFF);
        double blue = channel(color & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static int composite(int foreground, int background, float alpha) {
        int a = Math.round(alpha * 255f);
        int r = blend((foreground >>> 16) & 0xFF, (background >>> 16) & 0xFF, a);
        int g = blend((foreground >>> 8) & 0xFF, (background >>> 8) & 0xFF, a);
        int b = blend(foreground & 0xFF, background & 0xFF, a);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int blend(int foreground, int background, int alpha) {
        return (foreground * alpha + background * (255 - alpha) + 127) / 255;
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
