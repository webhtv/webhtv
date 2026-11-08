package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchScopePopupLayoutTest {

    /** WCAG 2.2 §1.4.3 正文阈值。 */
    private static final double MIN_BODY_CONTRAST = 4.5;

    private static final String[] TOKEN_TABLES = {
            "values/webhtv_tokens.xml",
            "values-night/webhtv_tokens.xml",
    };

    @Test
    public void tvScopeMenusShareGlassChromeAndFocusStates() throws Exception {
        String search = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SearchActivity.java")));
        String collect = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java")));
        String popup = read(findLeanbackResPath().resolve(Path.of("drawable", "shape_search_scope_popup.xml")));
        String item = read(findLeanbackResPath().resolve(Path.of("drawable", "selector_search_scope_item.xml")));

        assertTrue("search input menu must use the shared popup surface", search.contains("R.drawable.shape_search_scope_popup"));
        assertTrue("search result menu must use the shared popup surface", collect.contains("R.drawable.shape_search_scope_popup"));
        assertTrue("search input menu must use the shared item selector", search.contains("R.drawable.selector_search_scope_item"));
        assertTrue("search result menu must use the shared item selector", collect.contains("R.drawable.selector_search_scope_item"));
        assertTrue("popup surface must use the TV glass treatment", popup.contains("<gradient"));
        assertTrue("popup items must define a focused state", item.contains("state_focused"));
        assertTrue("popup items must define a selected state", item.contains("state_selected"));
        assertFalse("search input menu must not restore the opaque white panel", search.contains("setBackgroundColor(0xFFFFFFFF)"));
        assertFalse("search result menu must not restore the opaque white panel", collect.contains("drawable.setColor(Color.WHITE)"));
    }

    @Test
    public void scopeTriggersExposeDropdownAffordanceOnBothTvPages() throws Exception {
        String search = read(findLeanbackResPath().resolve(Path.of("layout", "activity_search.xml")));
        String collect = read(findLeanbackResPath().resolve(Path.of("layout", "activity_collect.xml")));
        String searchTrigger = viewTag(search, "@+id/searchScope");
        String collectTrigger = viewTag(collect, "@+id/searchGroup");

        assertTrue("search input scope trigger must expose a dropdown icon", search.contains("@drawable/ic_scope_expand_more"));
        assertTrue("search result group trigger must expose a dropdown icon", collect.contains("@drawable/ic_scope_expand_more"));
        assertTrue("TV scope triggers must use the same control height", searchTrigger.contains("android:layout_height=\"48dp\"") && collectTrigger.contains("android:layout_height=\"48dp\""));
    }

    /**
     * 手机版两个搜索下拉面板必须与 item 文字来自同一张主题表。
     *
     * <p>面板过去写死 {@code Color.WHITE}，而 item 文字取
     * {@code ThemeController.current().colorOnSurface()}；深色模式该角色是近白色
     * {@code #E2E2E9}，压在写死白底上实测 <b>1.29:1</b>，即用户报告的「深色模式搜索界面的
     * 分组下拉界面看不清文字」。面板改用 {@code colorSurfaceContainer} 后昼夜两表均为
     * 12.14:1 / 14.73:1，且主题解析器另有 onSurface 对 surfaceContainer 的 ≥4.5:1 门。
     */
    @Test
    public void mobileScopePopupsTakeTheirSurfaceFromThePalette() throws Exception {
        String search = read(findJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SearchFragment.java")));
        String collect = read(findJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "CollectFragment.java")));

        for (String source : new String[]{search, collect}) {
            assertFalse("search scope popups must not paint an opaque white panel",
                    source.contains("drawable.setColor(Color.WHITE)"));
            assertTrue("the popup surface must come from the semantic container role",
                    source.contains("drawable.setColor(ThemeController.current().colorSurfaceContainer())"));
            assertTrue("the popup item text must come from the matching semantic role",
                    source.contains("view.setTextColor(ThemeController.current().colorOnSurface())"));
        }
    }

    private static String viewTag(String layout, String id) {
        int start = layout.indexOf("android:id=\"" + id + "\"");
        int end = layout.indexOf("/>", start);
        return start < 0 || end < 0 ? "" : layout.substring(start, end);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }

    private static Path findJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    /**
     * 两个搜索下拉面板的底色与文字必须同时满足正文对比度。
     *
     * <p>面板取 {@code colorSurfaceContainer}、文字取 {@code colorOnSurface}，因此这条
     * 不变量就写在两套 token 表里：浅色 14.73:1、深色 12.14:1。设备实测与之一致
     * （搜索页/搜索结果页、日/夜四个组合都等于表中的值）。写死 {@code Color.WHITE} 的旧实现
     * 在深色表下只有 1.29:1，而光靠“面板不写死白色”的源码扫描是拦不住“面板写死
     * colorSurfaceContainer 的字节值”这类回归的，所以这里直接算颜色。
     */
    @Test
    public void scopePopupSurfaceAndTextClearTheBodyContrastFloorInBothPalettes() throws Exception {
        for (String table : TOKEN_TABLES) {
            String xml = read(findMainResPath().resolve(Path.of(table)));
            int panel = tokenColor(xml, "webhtv_color_surface_container");
            int text = tokenColor(xml, "webhtv_color_on_surface");
            double ratio = contrast(text, panel);
            assertTrue(table + " must keep onSurface/surfaceContainer above " + MIN_BODY_CONTRAST
                            + ":1 for the search dropdown but measured "
                            + String.format(Locale.US, "%.2f", ratio),
                    ratio + 0.0001 >= MIN_BODY_CONTRAST);
        }
    }

    private static int tokenColor(String xml, String name) {
        Matcher matcher = Pattern.compile("<color name=\"" + name + "\">#([0-9A-Fa-f]{6,8})</color>").matcher(xml);
        assertTrue("token " + name + " must exist in the palette table", matcher.find());
        String value = matcher.group(1);
        int rgb = Integer.parseInt(value.length() == 8 ? value.substring(2) : value, 16);
        return 0xFF000000 | rgb;
    }

    private static double contrast(int foreground, int background) {
        double light = luminance(foreground);
        double dark = luminance(background);
        return (Math.max(light, dark) + 0.05) / (Math.min(light, dark) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 0xFF)
                + 0.7152 * channel((color >> 8) & 0xFF)
                + 0.0722 * channel(color & 0xFF);
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
