package com.fongmi.android.tv.lab;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实验室「配置源」设置弹窗必须内容与面板同源，并且必须跟随应用主题。
 *
 * <p>缺陷实测（dev1 {@code 192.168.50.3:5555}，mobile arm64 debug，浅色系统）：
 * {@code LabActivity.showSettings()} 用 Activity 的 LayoutInflater inflate
 * {@code dialog_lab_settings.xml}，其中的 {@code ?attr/colorOnSurface} 由固定深色的
 * {@code Theme.App.Lab}（{@code colorOnSurface = #FFFFFFFF}）解析成白字，
 * 而弹窗面板来自 {@code ThemeOverlay.WebHTV.Dialog} 的日/夜双表（浅色 {@code #E7E8EF}）。
 * 结果正文与面板对比度只有 <b>1.05:1</b>，默认主题下整页看不清。
 *
 * <p>同时，这些固定白字/固定 {@code @color/accent} 都不等于冻结基线的语义角色值，
 * {@link com.fongmi.android.tv.theme.ThemeBinder} 的精确匹配改写无法命中，
 * 于是「主题色彩」对这个设置页完全没有作用。
 *
 * <p>本测试锁定两条不变量：内容用弹窗主题上下文 inflate；弹窗主题的强调色是语义 token。
 * 面板/正文的对比度由设备像素实测覆盖（见任务文档），这里只锁住会导致回归的源码形态。
 */
public class LabSettingsDialogThemeTest {

    private static final String ACTIVITY = "src/main/java/com/fongmi/android/tv/lab/LabActivity.java";
    private static final String STYLES = "src/main/res/values/lab_styles.xml";
    private static final String LAYOUT = "src/main/res/layout/dialog_lab_settings.xml";

    /** {@code ?attr/} 颜色属性 → 该属性在语义 token 表中的角色资源。 */
    private static final Map<String, String> ATTR_TO_TOKEN = new LinkedHashMap<>();

    static {
        ATTR_TO_TOKEN.put("colorOnSurface", "webhtv_color_on_surface");
        ATTR_TO_TOKEN.put("colorOnSurfaceVariant", "webhtv_color_on_surface_variant");
        ATTR_TO_TOKEN.put("colorPrimary", "webhtv_color_primary");
    }

    /**
     * 弹窗内容必须用弹窗主题上下文 inflate。
     *
     * <p>用 {@code getLayoutInflater()} 会落到 Activity 主题：{@code Theme.App.Lab} 是固定深色表，
     * 正文永远是白字，而面板跟着日/夜表走 —— 这正是「默认主题下就看不清」的成因。
     */
    @Test
    public void settingsContentIsInflatedWithTheDialogTheme() throws Exception {
        String source = stripComments(read(ACTIVITY));
        assertTrue("the settings dialog must build a dialog-themed context",
                source.contains("new ContextThemeWrapper(this, R.style.Theme_App_Lab_DayNight_Dialog)"));
        assertTrue("the settings layout must be inflated from the dialog theme, not the activity",
                source.contains("LayoutInflater.from(dialogContext).inflate(R.layout.dialog_lab_settings, null)"));
        assertFalse("the activity inflater would re-resolve the layout against Theme.App.Lab",
                source.contains("getLayoutInflater().inflate(R.layout.dialog_lab_settings"));
    }

    /**
     * 弹窗主题的强调色必须是语义 token。
     *
     * <p>{@code @color/accent} 固定为 {@code #2196F3}：它既不能跟随深浅色，
     * 也不满足 binder 的精确匹配改写，因此主题色永远作用不到这个弹窗的输入框描边、hint 与图标。
     */
    @Test
    public void dialogThemeAccentIsASemanticToken() throws Exception {
        String style = styleBody("Theme.App.Lab.DayNight.Dialog");
        assertFalse("the dialog accent must not be the fixed lab blue",
                style.contains("<item name=\"colorPrimary\">@color/accent</item>"));
        assertTrue("the dialog accent must resolve the semantic primary token",
                style.contains("<item name=\"colorPrimary\">@color/webhtv_color_primary</item>"));
        assertTrue("the activated control colour must be the semantic primary",
                style.contains("<item name=\"colorControlActivated\">@color/webhtv_color_primary</item>"));
    }

    /**
     * 弹窗面板必须与内容读同一张表。
     *
     * <p>面板由 {@code colorSurfaceContainerHigh}（M3 对话框容器色）决定，所以该槽必须也被
     * 映射到 {@code lab_surface}，否则内容跟随 {@code lab_surface} 而面板回落到 Material 基线表，
     * 又变成两套颜色。
     */
    @Test
    public void dialogPanelAndContentShareOneTable() throws Exception {
        String style = styleBody("Theme.App.Lab.DayNight.Dialog");
        assertTrue(style.contains("<item name=\"colorSurface\">@color/lab_surface</item>"));
        assertTrue(style.contains("<item name=\"colorSurfaceContainerHigh\">@color/lab_surface</item>"));
        assertTrue(style.contains("<item name=\"colorOnSurface\">@color/lab_text_primary</item>"));
        assertTrue(style.contains("<item name=\"colorOnSurfaceVariant\">@color/lab_text_secondary</item>"));

        String colors = read("src/main/res/values/lab_colors.xml");
        assertTrue("lab_surface must alias the semantic container role",
                colors.contains("<color name=\"lab_surface\">@color/webhtv_color_surface_container_high</color>"));
        assertTrue("lab_text_primary must alias the semantic foreground role",
                colors.contains("<color name=\"lab_text_primary\">@color/webhtv_color_on_surface</color>"));
        assertTrue("lab_text_secondary must alias the semantic variant role",
                colors.contains("<color name=\"lab_text_secondary\">@color/webhtv_color_on_surface_variant</color>"));
    }

    /**
     * 布局里的颜色属性必须都是语义角色。
     *
     * <p>设备实测的对比度只有在「内容属性全部解析成语义 token」时成立：任何一条落回固定字面量
     * 都会重新引入一个只能在一张表里正确的颜色。
     */
    @Test
    public void settingsLayoutOnlyUsesSemanticColorAttributes() throws Exception {
        String layout = stripComments(read(LAYOUT));
        List<String> unknown = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?:android:)?(?:textColor|textColorHint|boxStrokeColor|hintTextColor|tint)=\"([^\"]+)\"")
                .matcher(layout);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value.startsWith("?attr/color")) {
                String attr = value.substring("?attr/".length());
                if (!ATTR_TO_TOKEN.containsKey(attr)) unknown.add(value);
                continue;
            }
            if (value.startsWith("?android:attr/")) continue;
            unknown.add(value);
        }
        assertTrue("dialog_lab_settings.xml pins a colour outside the semantic roles: " + unknown,
                unknown.isEmpty());
    }

    /**
     * 语义角色必须在深浅两张表里都达到正文对比度门槛。
     *
     * <p>这是对「面板与内容同源」这一修复的直接量化：只要两张表各自满足 4.5:1，
     * 弹窗在跟随系统浅色或强制深色时都保持可读。
     */
    @Test
    public void bothTablesClearTheBodyTextContrastFloor() throws Exception {
        for (String file : new String[]{"src/main/res/values/webhtv_tokens.xml",
                "src/main/res/values-night/webhtv_tokens.xml"}) {
            Map<String, String> colors = colorsOf(read(file));
            String surface = colors.get("webhtv_color_surface_container_high");
            for (String foreground : new String[]{"webhtv_color_on_surface", "webhtv_color_on_surface_variant"}) {
                double ratio = contrast(colors.get(foreground), surface);
                assertTrue(file + ": " + foreground + " on " + surface + " is only "
                                + String.format(Locale.ROOT, "%.2f:1", ratio),
                        ratio >= 4.5);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String styleBody(String name) throws Exception {
        String styles = stripComments(read(STYLES));
        Matcher matcher = Pattern.compile("<style name=\"" + Pattern.quote(name) + "\"[^>]*>(.*?)</style>", Pattern.DOTALL)
                .matcher(styles);
        assertTrue(name + " is missing from lab_styles.xml", matcher.find());
        return matcher.group(1);
    }

    private static Map<String, String> colorsOf(String source) {
        Map<String, String> colors = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("<color name=\"([^\"]+)\">([^<]+)</color>").matcher(stripComments(source));
        while (matcher.find()) colors.put(matcher.group(1), matcher.group(2).trim());
        return colors;
    }

    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() == 8) digits = digits.substring(2);
        double[] channels = new double[3];
        for (int index = 0; index < 3; index++) {
            double value = Integer.parseInt(digits.substring(index * 2, index * 2 + 2), 16) / 255.0;
            channels[index] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    /**
     * Strips comments for the source-shape assertions.
     *
     * <p>Block comments are matched only at the start of a line: this file's production code
     * contains {@code setType} with a star-slash-star pattern, whose block-comment opener would
     * otherwise swallow the very method under test.
     */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?sm)^\\s*/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }

    /**
     * 下拉菜单必须由 Material 的 AutoCompleteTextView 提供。
     *
     * <p>{@code android.widget.AutoCompleteTextView} 不应用
     * {@code Widget.Material3.AutoCompleteTextView.OutlinedBox}，因此既没有
     * {@code dropDownBackgroundTint}（= colorSurfaceContainer），也不会把 item 文字交给主题，
     * 弹出列表落到 {@code @android:layout/simple_dropdown_item_1line} 的 Material 基线字色。
     * 设备实测（浅色系统、默认主题）：弹出面板 {@code #F3EDF7}，item 文字近白，<b>1.76:1</b>。
     */
    @Test
    public void dropdownUsesTheThemedMaterialAutoComplete() throws Exception {
        String layout = stripComments(read(LAYOUT));
        assertTrue("the dropdown must be the Material control so its popup follows the palette",
                layout.contains("com.google.android.material.textfield.MaterialAutoCompleteTextView"));
        assertFalse("a plain android.widget.AutoCompleteTextView would drop the themed popup",
                layout.contains("<AutoCompleteTextView"));
    }

    /**
     * 下拉列表项必须用自己的语义布局，且适配器必须用弹窗主题上下文。
     *
     * <p>{@code ArrayAdapter} 用自身 context 解析 item 布局。此前用的是
     * {@code android.R.layout.simple_dropdown_item_1line} + Activity 上下文
     * （{@code Theme.App.Lab}，固定深色 → 白字），而弹出面板来自日/夜双表：
     * 浅色系统下实测弹出面板 {@code #F3EDF7}、item 文字近白，<b>1.19:1</b>。
     */
    @Test
    public void dropdownItemsFollowTheDialogPalette() throws Exception {
        String activity = stripComments(read(ACTIVITY));
        assertTrue("the adapter must resolve its item layout against the dialog theme",
                activity.contains("new ArrayAdapter<>(dialogContext, R.layout.item_lab_dropdown, items)"));
        assertFalse("the framework item layout keeps Material's baseline text colour",
                activity.contains("android.R.layout.simple_dropdown_item_1line"));

        String item = stripComments(read("src/main/res/layout/item_lab_dropdown.xml"));
        assertTrue("the dropdown item must take its foreground from the semantic role",
                item.contains("android:textColor=\"?attr/colorOnSurface\""));
        assertTrue("the dropdown item must expose the framework list item id",
                item.contains("@android:id/text1"));
    }

    /**
     * 角色表必须覆盖布局真正用到的每一条颜色属性。
     *
     * <p>上一条测试只对表内已知角色放行；这条把「表是否漏项」也锁住，
     * 否则新增一条 {@code ?attr/colorX} 会被静默当成未知项而失败信息含糊。
     */
    @Test
    public void attributeTableCoversEveryColourAttributeUsedByTheLayout() throws Exception {
        String layout = stripComments(read(LAYOUT));
        Matcher matcher = Pattern.compile("\\?attr/(color[A-Za-z]+)").matcher(layout);
        int seen = 0;
        while (matcher.find()) {
            seen++;
            assertTrue("no expected token mapped for " + matcher.group(1),
                    ATTR_TO_TOKEN.containsKey(matcher.group(1)));
        }
        assertTrue("the layout must actually use semantic colour attributes", seen >= 5);
    }
}
