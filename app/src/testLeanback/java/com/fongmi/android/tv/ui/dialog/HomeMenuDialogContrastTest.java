package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 锁住 TV 版首页菜单键「选项弹窗」的对比度修复。
 *
 * <p>条目背景 {@code selector_config_history_item} 的焦点项是
 * {@code shape_config_history_item_focused} 的 colorPrimary 实心填充，所以焦点文字
 * 必须取 colorOnPrimary。修复前两者都是 colorPrimary（同一角色），默认浅色表下
 * 对比度 1.00:1（完全看不见）；自定义主题下 ThemeBinder 只改写颜色状态列表、不改写
 * StateListDrawable 内的填充，于是文字变成主题主色而底色仍是基线蓝色，实测 1.01:1。
 */
public class HomeMenuDialogContrastTest {

    /** WCAG 2.2 §1.4.3 正文阈值。 */
    private static final double MIN_BODY_CONTRAST = 4.5;

    private static final String ADAPTER = "app/src/leanback/res/layout/adapter_home_menu.xml";
    private static final String ITEM_TEXT = "app/src/leanback/res/color/home_menu_text.xml";
    private static final String SHARED_TEXT = "app/src/leanback/res/color/config_history_text.xml";
    private static final String ITEM_BACKGROUND = "app/src/leanback/res/drawable/selector_config_history_item.xml";
    private static final String FOCUSED_FILL = "app/src/leanback/res/drawable/shape_config_history_item_focused.xml";
    private static final String NORMAL_FILL = "app/src/leanback/res/drawable/shape_config_history_item_normal.xml";
    private static final String THEME = "app/src/main/res/values/webhtv_styles.xml";
    private static final String[] TOKEN_TABLES = {
            "app/src/main/res/values/webhtv_tokens.xml",
            "app/src/main/res/values-night/webhtv_tokens.xml",
    };

    @Test
    public void menuItemTextUsesOnPrimaryForThePrimaryFilledFocusState() throws Exception {
        String text = read(ITEM_TEXT);
        assertTrue("焦点文字必须取 colorOnPrimary", text.contains("android:color=\"?attr/colorOnPrimary\" android:state_focused=\"true\""));
        assertTrue("按下文字必须取 colorOnPrimary", text.contains("android:color=\"?attr/colorOnPrimary\" android:state_pressed=\"true\""));
        assertTrue("常态文字取 colorOnSurface", text.contains("android:color=\"?attr/colorOnSurface\" />"));
        assertFalse("焦点文字不能再取 colorPrimary（与焦点底色同角色）", text.contains("?attr/colorPrimary"));
        // 与同弹窗家族里已经正确的图标约定保持一致
        assertTrue(read("app/src/leanback/res/color/config_history_icon.xml").contains("?attr/colorOnPrimary"));
    }

    @Test
    public void adapterBindsTheOnPrimarySelectorAndLeavesTheSharedOneAlone() throws Exception {
        String adapter = read(ADAPTER);
        assertTrue(adapter.contains("android:textColor=\"@color/home_menu_text\""));
        assertFalse("@color/config_history_text 的焦点色服务于 primaryContainer 描边按钮", adapter.contains("@color/config_history_text"));
        assertTrue(adapter.contains("android:background=\"@drawable/selector_config_history_item\""));
        // 焦点底色确实是 colorPrimary 实心填充
        assertTrue(read(ITEM_BACKGROUND).contains("@drawable/shape_config_history_item_focused"));
        assertTrue(solidAttr(read(FOCUSED_FILL)).equals("colorPrimary"));
        // 共享 selector 不能一并改成 onPrimary：dialog_history 的描边按钮焦点底色是 primaryContainer
        assertTrue(read(SHARED_TEXT).contains("?attr/colorPrimary"));
    }

    @Test
    public void focusedAndNormalTextMeetWcagAaInBothPalettes() throws Exception {
        String text = read(ITEM_TEXT);
        Map<String, String> attributes = attributeColors(read(THEME));
        String focusedTextAttr = focusedAttr(text);
        String pressedTextAttr = pressedAttr(text);
        String normalTextAttr = defaultAttr(text);
        String focusedFillAttr = solidAttr(read(FOCUSED_FILL));
        String normalFillAttr = solidAttr(read(NORMAL_FILL));
        assertFalse("焦点文字与焦点底色不能是同一个角色", focusedTextAttr.equals(focusedFillAttr));
        for (String table : TOKEN_TABLES) {
            String tokens = read(table);
            double focused = contrast(colorOf(tokens, attributes, focusedTextAttr), colorOf(tokens, attributes, focusedFillAttr));
            double pressed = contrast(colorOf(tokens, attributes, pressedTextAttr), colorOf(tokens, attributes, focusedFillAttr));
            double normal = contrast(colorOf(tokens, attributes, normalTextAttr), colorOf(tokens, attributes, normalFillAttr));
            assertTrue(table + " 焦点文字对比度 " + focused + ":1", focused >= MIN_BODY_CONTRAST);
            assertTrue(table + " 按下文字对比度 " + pressed + ":1", pressed >= MIN_BODY_CONTRAST);
            assertTrue(table + " 常态文字对比度 " + normal + ":1", normal >= MIN_BODY_CONTRAST);
            // 把回归钉在数字上：修复前的 colorPrimary-on-colorPrimary 本来就不可读
            double legacy = contrast(colorOf(tokens, attributes, "colorPrimary"), colorOf(tokens, attributes, focusedFillAttr));
            assertTrue(table + " 修复前的写法应当是 1:1", legacy < MIN_BODY_CONTRAST);
        }
    }

    private static String focusedAttr(String selector) {
        Matcher matcher = Pattern.compile("android:color=\"\\?attr/(\\w+)\"[^/]*?android:state_focused=\"true\"").matcher(selector);
        assertTrue("选择器缺少 state_focused 颜色项", matcher.find());
        return matcher.group(1);
    }

    private static String pressedAttr(String selector) {
        Matcher matcher = Pattern.compile("android:color=\"\\?attr/(\\w+)\"[^/]*?android:state_pressed=\"true\"").matcher(selector);
        assertTrue("选择器缺少 state_pressed 颜色项", matcher.find());
        return matcher.group(1);
    }

    private static String defaultAttr(String selector) {
        Matcher matcher = Pattern.compile("<item\\s+android:color=\"\\?attr/(\\w+)\"\\s*/>").matcher(selector);
        assertTrue("选择器缺少无状态默认颜色项", matcher.find());
        return matcher.group(1);
    }

    private static String solidAttr(String shape) {
        Matcher matcher = Pattern.compile("<solid\\s+android:color=\"\\?attr/(\\w+)\"").matcher(shape);
        assertTrue("shape 缺少 solid 颜色", matcher.find());
        return matcher.group(1);
    }

    /** Theme.WebHTV 把每个 Material 角色映射到 webhtv token 资源名。 */
    private static Map<String, String> attributeColors(String styles) {
        Map<String, String> mapping = new HashMap<>();
        Matcher matcher = Pattern.compile("<item name=\"(color\\w+)\">@color/(\\w+)</item>").matcher(styles);
        while (matcher.find()) mapping.putIfAbsent(matcher.group(1), matcher.group(2));
        assertTrue("未解析到语义色映射", mapping.containsKey("colorPrimary") && mapping.containsKey("colorOnPrimary"));
        return mapping;
    }

    private static int colorOf(String tokens, Map<String, String> attributes, String attribute) {
        String resource = attributes.get(attribute);
        assertTrue("主题未映射 " + attribute, resource != null);
        Matcher matcher = Pattern.compile("<color name=\"" + Pattern.quote(resource) + "\">#([0-9A-Fa-f]{6,8})</color>").matcher(tokens);
        assertTrue("token 未定义 " + resource, matcher.find());
        String hex = matcher.group(1);
        if (hex.length() == 8) hex = hex.substring(2);
        return Integer.parseInt(hex, 16);
    }

    private static double contrast(int first, int second) {
        double high = Math.max(luminance(first), luminance(second));
        double low = Math.min(luminance(first), luminance(second));
        return (high + 0.05) / (low + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 0xFF)
                + 0.7152 * channel((color >> 8) & 0xFF)
                + 0.0722 * channel(color & 0xFF);
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve(".git"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root not found");
        return Files.readString(path.resolve(relative), StandardCharsets.UTF_8);
    }
}
