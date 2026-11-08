package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The unified theme entry point must resolve the persisted WebHTV appearance
 * preference into the shared semantic tokens so every module (following, lab,
 * dialogs, web pages) reads one source of truth.
 */
public class ThemeControllerContractTest {

    @Test
    public void controllerExposesPreferenceBackedResolution() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(source.contains("public static ThemeTokens resolveFromPreferences()"));
        assertTrue(source.contains("public static void applyFromPreferences(AppCompatActivity activity)"));
        assertTrue(source.contains("Setting.getThemeColor()"));
        assertTrue(source.contains("Setting.getWallColor()"));
        assertTrue(source.contains("ThemeSeed.WALLPAPER"));
        assertTrue(source.contains("ThemeSeed.EXPLICIT"));
    }

    @Test
    public void appearanceModeDrivesAppCompatNightMode() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("public static void applyNightModeToApp()"));
        assertTrue(controller.contains("AppCompatDelegate.MODE_NIGHT_NO"));
        assertTrue(controller.contains("AppCompatDelegate.MODE_NIGHT_YES"));
        assertTrue(controller.contains("AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM"));
        assertTrue(controller.contains("ThemeMode themeMode = currentThemeMode();"));
        assertTrue(read("src/main/java/com/fongmi/android/tv/App.java").contains("ThemeController.applyNightModeToApp();"));
        for (String flavour : new String[]{"mobile", "leanback"}) {
            String dialog = read("src/" + flavour + "/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java");
            assertTrue(dialog.contains("setting_theme_mode"));
            assertTrue(dialog.contains("Setting.putThemeMode(mode);"));
            assertTrue(dialog.contains("ThemeController.applyNightModeToApp();"));
        }
        assertTrue(read("src/main/java/com/fongmi/android/tv/setting/Setting.java").contains("public static int getThemeMode()"));
    }

    @Test
    public void everyActivityAppliesThePersistedThemeSnapshot() throws Exception {
        String mobile = read("src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        String leanback = read("src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        assertTrue(mobile.contains("ThemeController.applyFromPreferences(this);"));
        assertTrue(leanback.contains("ThemeController.applyFromPreferences(this);"));
    }

    @Test
    public void legacyPalettesDelegateToSemanticTokens() throws Exception {
        String colors = read("src/main/res/values/colors.xml");
        assertTrue(colors.contains("<color name=\"following_page_bg\">@color/webhtv_color_surface</color>"));
        assertTrue(colors.contains("<color name=\"following_accent\">@color/webhtv_color_primary</color>"));
        assertTrue(colors.contains("<color name=\"following_danger\">@color/webhtv_color_error</color>"));
        assertTrue(colors.contains("<color name=\"site_health_good\">@color/webhtv_color_health_good</color>"));
        assertTrue(colors.contains("<color name=\"site_health_bad\">@color/webhtv_color_health_bad</color>"));
        String lab = read("src/main/res/values/lab_colors.xml");
        assertTrue(lab.contains("<color name=\"lab_surface\">@color/webhtv_color_surface_container_high</color>"));
        assertTrue(lab.contains("<color name=\"lab_text_primary\">@color/webhtv_color_on_surface</color>"));
    }

    @Test
    public void followingAndDetailSurfacesUseSemanticAttributes() throws Exception {
        String following = read("src/main/res/layout/activity_following.xml");
        // 追更页改为壁纸背景：根布局必须透明，靠半透明面板承载文字。
        assertFalse(following.contains("android:background=\"?attr/colorSurface\""));
        assertTrue(following.contains("@drawable/shape_following_panel"));
        assertTrue(following.contains("app:backgroundTint=\"?attr/colorPrimary\""));
        assertTrue(following.contains("android:textColor=\"?attr/colorOnSurfaceVariant\""));
        String card = read("src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbCastPresenter.java");
        assertTrue(card.contains("ThemeController.current()"));
        assertTrue(card.contains("tokens.colorSurfaceContainerHigh()"));
        // 焦点环已由前景 selector（?attr/tvFocusRing，取值 tv_item_focus_ring）统一绘制，
        // presenter 只维护常态描边与卡面 token；不再自己画焦点描边以免双重描边。
        assertFalse(card.contains("tokens.colorFocus()"));
        assertTrue(card.contains("STROKE_NORMAL"));
        String video = read("src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbVideoPresenter.java");
        assertTrue(video.contains("selector_tmdb_media_focus"));
        String dialog = read("src/mobile/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java");
        // 行的底色/描边与两个文字色必须来自同一个调色板：此前行底是固定浅色
        // selector_git_cloud_card，而文字已跟随 ThemeController.current()，在 TV 深色表上
        // 实测 1.16:1 / 1.36:1。现在两者都经由 AppearanceRowTheme 取当前 token。
        assertTrue(dialog.contains("AppearanceRowTheme.apply(row, title, summary, ThemeController.current())"));
        String rowTheme = read("src/main/java/com/fongmi/android/tv/theme/AppearanceRowTheme.java");
        assertTrue(rowTheme.contains("safe.colorOnSurface()"));
        assertTrue(rowTheme.contains("safe.colorOnSurfaceVariant()"));
    }

    @Test
    public void snapshotIsResolvedBeforeTheFirstContentView() throws Exception {
        for (String flavour : new String[]{"mobile", "leanback"}) {
            String source = read("src/" + flavour + "/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
            int apply = source.indexOf("ThemeController.applyFromPreferences(this);");
            int content = source.indexOf("setContentView(");
            assertTrue(flavour + " must apply the persisted snapshot", apply > 0);
            assertTrue(flavour + " must set a content view", content > 0);
            assertTrue(flavour + " must resolve the theme snapshot before its first content view", apply < content);
        }
    }

    @Test
    public void controllerReadsTheV2ProfileAndKeepsABaselineSnapshot() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("ThemeProfileStore.load()"));
        assertTrue(controller.contains("private static ThemeTokens resolveWith(ThemeProfile profile, Context context)"));
        assertTrue(controller.contains("baseline = frozenPalette(activity);"));
        assertFalse("the binder baseline must never be seed-derived",
                controller.contains("baseline = resolveWith("));
        assertTrue(controller.contains("private static ThemeTokens frozenPalette(Context context)"));
        assertTrue(controller.contains("current = resolveWith(profile, activity);"));
        assertTrue(controller.contains("ThemeBinder.bind(root, baseline, current);"));
        assertTrue(controller.contains("public static ThemeTokens baseline()"));
    }

    @Test
    public void ordinaryUiNightDecisionComesFromTheThemeController() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("public static boolean isNight(Context context)"));
        String chrome = read("src/mobile/java/com/fongmi/android/tv/ui/activity/WebHomeChromeController.java");
        assertTrue(chrome.contains("ThemeController.isNight(activity)"));
        assertFalse(chrome.contains("UI_MODE_NIGHT_MASK"));
    }

    /**
     * TV 产品默认深色（与上游一致：上游 TV 主题直接继承 {@code Theme.Material3.Dark}）。
     *
     * <p>理由：电视多在暗环境观看，大面积浅色亮底在夜间刺眼；而且 TV 常见的深色底上，
     * 上游那套**白色焦点环**（对深色 surface 17–18:1）才能成立——
     * 白环对浅色表 surface 只有 1.05:1，等于看不见。
     *
     * <p>“跟随系统”对 TV 不再等于“跟随一个通常恒为浅色的系统默认”，否则电视上会默认落到
     * 浅色表。用户仍可在外观设置里显式选浅色/深色；mobile 保持“跟随系统”不变。
     */
    @Test
    public void tvDefaultsToDarkWhileMobileKeepsFollowingTheSystem() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue("TV 默认必须落到深色：follow-system 分支要按 flavour 分流",
                controller.contains("Util.isLeanback()")
                        && controller.contains("AppCompatDelegate.MODE_NIGHT_YES")
                        && controller.contains("AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM"));
        // 显式选择仍然优先（浅色/深色都能生效）
        assertTrue("显式浅色/深色必须仍然优先",
                controller.contains("case 0 -> AppCompatDelegate.MODE_NIGHT_NO;")
                        && controller.contains("case 1 -> AppCompatDelegate.MODE_NIGHT_YES;"));
    }

    /**
     * 深色表焦点环是「近白」（与上游的白色焦点环观感一致），但不能是纯白。
     *
     * <p>本仓库有 {@link ThemeBinder} 运行时改写通道，它按「颜色 → 语义角色」精确匹配；
     * 而 {@code webhtv_on_wallpaper} 两张表恒为 {@code #FFFFFF}。若焦点也取纯白，
     * {@code ThemeColorIndex} 会把纯白映射到 FOCUS 角色，用户切换/自定义主题时
     * 壁纸上的白色文字会被改写成焦点色（{@code ThemeBaseWiringTest} 钉住该不变量）。
     * 所以这里断言：深色焦点必须亮（对 surface 高对比）且**不等于**纯白。
     */
    @Test
    public void darkFocusRingIsNearWhiteButNeverPureWhite() throws Exception {
        String night = read("src/main/res/values-night/webhtv_tokens.xml");
        Matcher matcher = java.util.regex.Pattern
                .compile("<color name=\"webhtv_color_focus\">#([0-9A-Fa-f]{6})</color>")
                .matcher(night);
        assertTrue("深色表必须声明 webhtv_color_focus", matcher.find());
        int focus = (int) Long.parseLong(matcher.group(1), 16);

        assertFalse("深色焦点环不得为纯白：会与 webhtv_on_wallpaper 碰撞，binder 会把壁纸白字改写成焦点色",
                focus == 0xFFFFFF);
        double ratio = ThemeContrast.ratio(0xFF000000 | focus, ThemeTokens.dark().colorSurface());
        assertTrue("深色焦点环必须对 surface 保持高对比（实测 " + String.format("%.2f", ratio) + ":1）",
                ratio >= 10.0);
        // 与 ThemeTokens.dark() 的字面量保持一致
        assertEquals("深色表资源必须与 ThemeTokens.dark().colorFocus() 同值",
                0xFF000000 | focus, ThemeTokens.dark().colorFocus());
    }

    /**
     * 追更页按钮的「填充 ↔ 文本」必须始终配对。
     *
     * <p>这两个按钮的 backgroundTint 在 focused/pressed 时会换成
     * {@code following_button_focus}（= {@code webhtv_color_focus}）。此前文本写死
     * {@code @android:color/white}，在浅色表下凑巧可读，但深色表下填充是浅色而文本仍为白
     * （1.00:1，完全不可读）；TV 默认改深色后该缺陷会直接显形。
     */
    @Test
    public void followingButtonTextPairsWithItsFocusFill() throws Exception {
        for (String file : new String[]{"following_button_primary_text.xml", "following_button_secondary_text.xml"}) {
            // 去注释后再断言：注释里会引用历史值作说明，不是实际取值。
            String body = read("src/main/res/color/" + file).replaceAll("(?s)<!--.*?-->", "");
            assertFalse(file + " 不得再用写死白色文本（深色表下与浅色填充 1.00:1 不可读）",
                    body.contains("@android:color/white"));
            assertTrue(file + " 的焦点/按下文本必须取与填充配对的 on-色",
                    body.contains("@color/webhtv_color_on_primary"));
        }
        // 两张表的配对都必须过对比度门（文本 4.5:1）
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.light(), ThemeTokens.dark()}) {
            double ratio = ThemeContrast.ratio(tokens.colorOnPrimary(), tokens.colorFocus());
            assertTrue("焦点填充配 on-色文本必须 ≥4.5:1（实测 " + String.format("%.2f", ratio) + ":1）",
                    ratio >= 4.5);
        }
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
