package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 电视端「状态对」对比度守卫：同一控件的**状态填充**与**同态前景**必须成对达标。
 *
 * <p>背景：搜索页/搜索结果页的范围下拉把两件事分开写在两个资源里——
 * {@code leanback/res/drawable/selector_search_scope_item.xml} 的 {@code state_focused}
 * 填充是 {@code @color/white_90}（{@code #E6FFFFFF}），而
 * {@code leanback/res/color/selector_search_scope_text.xml} 的 {@code state_focused}
 * 前景是 {@code #FFFFFF}。两者单看都「浅色、合法」，合起来是白字压白底：
 * 合成到深色玻璃面板（{@code shape_search_scope_popup} 的 {@code #E62A2955}）上实测
 * <b>1.19:1</b>，用户报告「选中这里看不清」。
 *
 * <p>既有守卫都拦不住这一类：
 * <ul>
 *   <li>{@code LeanbackForegroundContrastTest} 只看布局里 {@code android:textColor} 与
 *       最近祖先背景，看不到 {@code <selector>} 的状态对，也看不到 Java 里成对设置的控件；</li>
 *   <li>{@code SearchScopePopupLayoutTest} 只锁移动端面板取 {@code surfaceContainer}；</li>
 *   <li>{@code check_ui_tokens.py} 只校验 token 配对与硬编码色，不解状态选择器。</li>
 * </ul>
 *
 * <p>本测试做三件事：
 * <ol>
 *   <li><b>状态对清单</b>：逐个解析 drawable/color 选择器 → alpha 合成到声明宿主 → 算 WCAG 对比度；</li>
 *   <li><b>完整性门</b>：全库「恒定浅色状态前景」和「浅色填充 + 恒定浅色焦点环」必须被登记，
 *       或进入带理由的豁免表，否则失败——这样「新增一处旁路写法」也会被点名；</li>
 *   <li><b>接线门</b>：两个搜索页必须同时引用这对选择器，防止只改一边。</li>
 * </ol>
 *
 * <p>阈值：正文 4.5:1（WCAG 2.2 SC 1.4.3），非文本（焦点环）3:1（SC 1.4.11）。
 * 所有场景都按**日/夜两张表**各算一遍；半透明填充必须先压在声明宿主上再算亮度。
 */
public class TvFocusStatePairContrastTest {

    private static final double MIN_TEXT = 4.5;
    private static final double MIN_NON_TEXT = 3.0;

    /** {@code shape_search_scope_popup} 中段渐变色：两张表相同，固定深色玻璃。 */
    private static final String SCOPE_GLASS = "E62A2955";

    /** 面板后面的宿主：三档亮度壁纸（搜索页画布是用户壁纸）。 */
    private static final String[] SCOPE_WALLPAPERS = {"8FA8D0", "5E7FB0", "26365A"};

    /** 播放层暗幕（{@code webhtv_color_player_scrim} 日/夜）压在两档画面亮度上。 */
    private static final String[] PLAYER_SCRIMS = {"7A000000", "8F000000"};
    private static final String[] PLAYER_FRAMES = {"7C7C7C", "404040"};

    /** 透明度合成用的中性宿主（只用于完整性门判断「填充是不是浅色」）。 */
    private static final String NEUTRAL_HOST = "808080";

    /** 判定「恒定浅色」的亮度下限（两张表都超过才算恒定）。 */
    private static final double CONSTANT_LIGHT_LUMINANCE = 0.7;

    /** 判定「浅色填充」的亮度下限（合成到中性宿主之后）。 */
    private static final double LIGHT_FILL_LUMINANCE = 0.55;

    private static final String[] SOURCE_SETS = {"main", "leanback"};

    private static final Pattern COLOR_ENTRY = Pattern.compile("<color name=\"([A-Za-z0-9_]+)\">#([0-9A-Fa-f]{6,8})</color>");
    private static final Pattern ALIAS_ENTRY = Pattern.compile("<color name=\"([A-Za-z0-9_]+)\">([^<]+)</color>");
    private static final Pattern ITEM = Pattern.compile("<item\\b([^>]*?)(/>|>(.*?)</item>)", Pattern.DOTALL);
    private static final Pattern STATE_ATTR = Pattern.compile("android:state_([a-z_]+)=\"(true|false)\"");
    private static final Pattern COLOR_ATTR = Pattern.compile("android:color=\"([^\"]+)\"");
    private static final Pattern DRAWABLE_ATTR = Pattern.compile("android:drawable=\"([^\"]+)\"");
    private static final Pattern SOLID = Pattern.compile("<solid\\b[^>]*android:color=\"([^\"]+)\"");
    private static final Pattern STROKE = Pattern.compile("<stroke\\b[^>]*android:color=\"([^\"]+)\"");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /** 主题属性 → 色板 token 的接线（只映射到本仓库真实存在的属性）。 */
    private static final Map<String, String> THEME_ATTRS = new LinkedHashMap<>();

    static {
        THEME_ATTRS.put("colorPrimary", "@color/webhtv_color_primary");
        THEME_ATTRS.put("colorOnPrimary", "@color/webhtv_color_on_primary");
        THEME_ATTRS.put("colorPrimaryContainer", "@color/webhtv_color_primary_container");
        THEME_ATTRS.put("colorOnPrimaryContainer", "@color/webhtv_color_on_primary_container");
        THEME_ATTRS.put("colorSecondaryContainer", "@color/webhtv_color_secondary_container");
        THEME_ATTRS.put("colorOnSecondaryContainer", "@color/webhtv_color_on_secondary_container");
        THEME_ATTRS.put("colorSurface", "@color/webhtv_color_surface");
        THEME_ATTRS.put("colorSurfaceContainer", "@color/webhtv_color_surface_container");
        THEME_ATTRS.put("colorSurfaceContainerHigh", "@color/webhtv_color_surface_container_high");
        THEME_ATTRS.put("colorSurfaceContainerHighest", "@color/webhtv_color_surface_container_highest");
        THEME_ATTRS.put("colorOnSurface", "@color/webhtv_color_on_surface");
        THEME_ATTRS.put("colorOnSurfaceVariant", "@color/webhtv_color_on_surface_variant");
        THEME_ATTRS.put("tvFocusRing", "@color/tv_item_focus_ring");
        THEME_ATTRS.put("tvPlayerRing", "@color/tv_player_focus_ring");
        THEME_ATTRS.put("tvPlayerCurrentRing", "@color/tv_player_current_ring");
        THEME_ATTRS.put("webhtvColorOnWallpaper", "#FFFFFF");
        THEME_ATTRS.put("webhtvColorOnWallpaperVariant", "#CCFFFFFF");
    }

    // ---------------------------------------------------------------- registry

    /** 已登记的状态对：填充 × 同态前景 × 声明宿主。 */
    private static final List<Pair> PAIRS = new ArrayList<>();

    /**
     * 恒定浅色状态前景里**不构成文字压白底**的家族，逐条写理由。
     * key = "源集/sub/file.xml"。
     */
    private static final Map<String, String> EXEMPT_FOREGROUNDS = new LinkedHashMap<>();

    /** 浅色填充 + 恒定浅色焦点环，但宿主/配对已由别处契约覆盖的家族。 */
    private static final Map<String, String> EXEMPT_FILLS = new LinkedHashMap<>();

    static {
        // F1：TV 搜索页/搜索结果页的范围下拉（宿主 = 固定深色玻璃，压在三档壁纸上）。
        PAIRS.add(new Pair("tv.searchScope.default",
                "leanback/drawable/selector_search_scope_item", states(),
                "leanback/color/selector_search_scope_text", states(), Host.SCOPE_GLASS, MIN_TEXT, true));
        PAIRS.add(new Pair("tv.searchScope.focused",
                "leanback/drawable/selector_search_scope_item", states("focused"),
                "leanback/color/selector_search_scope_text", states("focused"), Host.SCOPE_GLASS, MIN_TEXT, true));
        PAIRS.add(new Pair("tv.searchScope.focusedSelected",
                "leanback/drawable/selector_search_scope_item", states("focused", "selected"),
                "leanback/color/selector_search_scope_text", states("focused"), Host.SCOPE_GLASS, MIN_TEXT, true));
        PAIRS.add(new Pair("tv.searchScope.selected",
                "leanback/drawable/selector_search_scope_item", states("selected"),
                "leanback/color/selector_search_scope_text", states("selected"), Host.SCOPE_GLASS, MIN_TEXT, true));
        PAIRS.add(new Pair("tv.searchScope.pressed",
                "leanback/drawable/selector_search_scope_item", states("pressed"),
                "leanback/color/selector_search_scope_text", states("pressed"), Host.SCOPE_GLASS, MIN_TEXT, true));

        // F2：屏显弹窗芯片（宿主 = 播放层暗幕压在画面上）。
        PAIRS.add(new Pair("tv.displayOption.default",
                "main/drawable/selector_display_option", states(),
                "main/color/display_option_text", states(), Host.PLAYER_SCRIM, MIN_TEXT, false));
        PAIRS.add(new Pair("tv.displayOption.focused",
                "main/drawable/selector_display_option", states("focused"),
                "main/color/display_option_text", states("focused"), Host.PLAYER_SCRIM, MIN_TEXT, false));
        PAIRS.add(new Pair("tv.displayOption.focusedSelected",
                "main/drawable/selector_display_option", states("focused", "selected"),
                "main/color/display_option_text", states("selected"), Host.PLAYER_SCRIM, MIN_TEXT, true));
        PAIRS.add(new Pair("tv.displayOption.selected",
                "main/drawable/selector_display_option", states("selected"),
                "main/color/display_option_text", states("selected"), Host.PLAYER_SCRIM, MIN_TEXT, true));
        PAIRS.add(new Pair("tv.displayOption.pressed",
                "main/drawable/selector_display_option", states("pressed"),
                "main/color/display_option_text", states("pressed"), Host.PLAYER_SCRIM, MIN_TEXT, false));

        // 恒定浅色前景中不是「文字压填充」的家族。
        EXEMPT_FOREGROUNDS.put("leanback/color/selector_control_sheet_text.xml",
                "播放层按钮文字：填充 selector_control_sheet_button 自带亮化层与 ?attr/tvPlayerRing，属视频层分区（审计 §9.8）");
        EXEMPT_FOREGROUNDS.put("main/color/player_control_text.xml",
                "播控条文字：宿主是固定深靛玻璃 shape_dialog_control_glass_panel，配套填充见 selector_control_sheet_button");
        EXEMPT_FOREGROUNDS.put("main/color/selector_dialog_glass_chip_text.xml",
                "深色玻璃芯片文字：宿主 shape_dialog_glass_chip 固定深色（两张表相同）");
        EXEMPT_FOREGROUNDS.put("leanback/color/home_menu_text.xml",
                "焦点/按下取 ?attr/colorOnPrimary，落在 primary 填充上；默认态取 ?attr/colorOnSurface，由色板门禁保证");
        EXEMPT_FOREGROUNDS.put("leanback/color/site_item_text.xml",
                "焦点取 ?attr/colorOnPrimary 配 primary 填充，选中取 ?attr/colorPrimary 配 surface，属语义配对");
        EXEMPT_FOREGROUNDS.put("leanback/color/text.xml",
                "焦点/选中一律 ?attr/colorPrimary，落在应用表面？attr 填充上，由 LeanbackForegroundContrastTest 覆盖");
        EXEMPT_FOREGROUNDS.put("leanback/color/site_item_check.xml",
                "勾选图标 tint，不是文字；配对由站点条目选中态契约覆盖");
        EXEMPT_FOREGROUNDS.put("leanback/color/site_action_icon.xml",
                "站点操作图标 tint，不是文字；落位在 ?attr/colorSurface* 面板");
        EXEMPT_FOREGROUNDS.put("leanback/color/config_history_icon.xml",
                "配置历史图标 tint，不是文字；落位在 ?attr/colorSurface* 面板");
        EXEMPT_FOREGROUNDS.put("main/color/player_settings_icon_tint.xml",
                "屏显/播放设置图标 tint，配套填充 selector_player_settings_row（?attr/colorPrimaryContainer 随表变）");
        EXEMPT_FOREGROUNDS.put("main/color/focus_ring_primary.xml",
                "焦点环色（非文字），与焦点填充的配对由 TvFocusRingContractTest 校验");
        EXEMPT_FOREGROUNDS.put("main/color/focus_ring_primary_container.xml",
                "焦点环色（非文字），与焦点填充的配对由 TvFocusRingContractTest 校验");
        EXEMPT_FOREGROUNDS.put("main/color/focus_ring_secondary.xml",
                "焦点环色（非文字），与焦点填充的配对由 TvFocusRingContractTest 校验");
        EXEMPT_FOREGROUNDS.put("main/color/focus_ring_error.xml",
                "焦点环色（非文字），与焦点填充的配对由 TvFocusRingContractTest 校验");
        EXEMPT_FOREGROUNDS.put("main/color/about_primary_action_text.xml",
                "关于弹窗主按钮文字：填充 about_primary_action_bg 同为 ?attr 族，配对由对话框文字契约覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/about_primary_icon_tint.xml",
                "关于弹窗图标 tint，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_primary_button_text.xml",
                "主按钮文字：填充 dialog_primary_button_bg = ?attr/colorPrimary（随表变），配对由 DialogForegroundTokenTest 覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_tonal_button_text.xml",
                "tonal 按钮文字：填充 ?attr/colorSecondaryContainer，属语义配对");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_outlined_button_text.xml",
                "描边按钮文字：无实心填充（透明底 + 描边），不构成白底白字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_text_button.xml",
                "文字按钮：无实心填充");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_card_action_text.xml",
                "卡片动作文字：卡片填充 selector_card/ dialog_card_bg 随表变");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_toggle_button_text.xml",
                "开关按钮文字：填充 dialog_toggle_button_bg 同族");
        EXEMPT_FOREGROUNDS.put("main/color/following_button_danger_text.xml",
                "追更危害按钮文字：填充 following_button_danger_bg 同族，且由手机端契约测试覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/following_button_secondary_text.xml",
                "追更次要按钮文字：填充 following_button_secondary_bg 同族");
        EXEMPT_FOREGROUNDS.put("main/color/site_button_text.xml",
                "站点按钮文字：填充 site_button_bg 同族");
        EXEMPT_FOREGROUNDS.put("main/color/sync_device_text.xml",
                "同步设备行文字：落位在 ?attr/colorSurface* 行背景");
        EXEMPT_FOREGROUNDS.put("main/color/update_version_item_title.xml",
                "版本条目标题：落位在对话框玻璃面板");
        EXEMPT_FOREGROUNDS.put("main/color/update_version_item_meta.xml",
                "版本条目次要文字：落位在对话框玻璃面板");
        EXEMPT_FOREGROUNDS.put("main/color/update_version_item_accent.xml",
                "版本条目强调文字：落位在对话框玻璃面板");
        EXEMPT_FOREGROUNDS.put("leanback/color/exit_confirm_secondary_text.xml",
                "退出确认次要按钮文字：焦点色 #174EA6 为深色，配浅色填充 selector_exit_confirm_secondary");
        EXEMPT_FOREGROUNDS.put("leanback/color/config_history_text.xml",
                "配置历史文字：焦点取 ?attr/colorPrimary（深色），填充 selector_config_history_item");
        EXEMPT_FOREGROUNDS.put("leanback/color/selector_group_text.xml",
                "分组文字：焦点取 ?attr/colorPrimary，填充 selector_group 随表变");
        EXEMPT_FOREGROUNDS.put("main/color/selector_video_text.xml",
                "视频条目文字：焦点/选中取 ?attr/colorPrimary，填充 selector_video_item 随表变");
        EXEMPT_FOREGROUNDS.put("mobile/color/selector_video_text.xml",
                "手机端视频条目文字：填充 mobile/selector_video_item 同族，由手机端契约覆盖");
        EXEMPT_FOREGROUNDS.put("mobile/color/selector_control_sheet_text.xml",
                "手机端播控文字：视频层分区，属手机端契约");
        EXEMPT_FOREGROUNDS.put("main/color/selector_danmaku_source_tab_text.xml",
                "弹幕源 Tab 文字：填充 selector_danmaku_source_tab 同族");
        EXEMPT_FOREGROUNDS.put("main/color/selector_text.xml",
                "通用文字选择器：落在 ?attr/colorSurface* 面板，由 LeanbackForegroundContrastTest 覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/selector_stroke.xml",
                "描边色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/selector_card.xml",
                "卡片背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_card_bg.xml",
                "卡片背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_card_stroke.xml",
                "卡片描边色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_outlined_button_bg.xml",
                "描边按钮背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_outlined_button_stroke.xml",
                "描边按钮描边色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_primary_button_bg.xml",
                "主按钮背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_tonal_button_bg.xml",
                "tonal 按钮背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_toggle_button_bg.xml",
                "开关按钮背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/dialog_checkbox_tint.xml",
                "复选框 tint，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/following_button_danger_bg.xml",
                "背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/following_button_primary_bg.xml",
                "背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/following_button_secondary_bg.xml",
                "背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/site_button_bg.xml",
                "背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/site_button_stroke.xml",
                "描边色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/about_primary_action_bg.xml",
                "背景色，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/selector_dialog_glass_button_tint.xml",
                "玻璃按钮 tint，不是文字");
        EXEMPT_FOREGROUNDS.put("main/color/webhtv_selector_button_filled.xml",
                "设计系统状态色（当前无消费者），由 UiStyleSourceTest 覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/webhtv_selector_button_outlined.xml",
                "设计系统状态色（当前无消费者），由 UiStyleSourceTest 覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/webhtv_selector_input.xml",
                "设计系统状态色（当前无消费者），由 UiStyleSourceTest 覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/webhtv_selector_list_item.xml",
                "设计系统状态色（当前无消费者），由 UiStyleSourceTest 覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/webhtv_selector_tmdb_card.xml",
                "设计系统状态色（当前无消费者），由 UiStyleSourceTest 覆盖");
        EXEMPT_FOREGROUNDS.put("main/color/webhtv_selector_tmdb_search_item.xml",
                "设计系统状态色（当前无消费者），由 UiStyleSourceTest 覆盖");
        EXEMPT_FOREGROUNDS.put("mobile/color/selector_live_action_icon.xml",
                "手机端直播图标 tint，不是文字");
        EXEMPT_FOREGROUNDS.put("mobile/color/selector_quick_search_item.xml",
                "手机端快捷搜索条目文字：填充随表变，由手机端契约覆盖");

        // 浅色焦点填充 + 恒定浅色环：宿主与配对已由别处契约覆盖。
        EXEMPT_FILLS.put("leanback/drawable/selector_ad_stats_item.xml",
                "焦点填充 #E8F0FE 是固定浅色、环 #1A73E8 为固定深蓝；配对的文字取 ?attr/colorPrimary，"
                        + "由 TvTabFocusRingPaletteContractTest 与 PlayerSettingsContrastTest 覆盖");
    }

    // ------------------------------------------------------------------- tests

    @Test
    public void registeredStatePairsKeepTheirForegroundAboveTheFloor() {
        ensureLoaded();
        List<String> failures = new ArrayList<>();
        for (Pair pair : PAIRS) {
            for (boolean night : new boolean[]{false, true}) {
                String fill = drawableFill(pair.background, pair.states, night);
                assertTrue("填充资源必须可解析：" + pair.id, fill != null);
                for (String[] host : hosts(pair.host, night)) {
                    String bg = composite(fill, host[1]);
                    String fgRef = foregroundRef(pair.foreground, pair.foregroundStates, night);
                    assertTrue("前景资源必须可解析：" + pair.id, fgRef != null);
                    String fg = composite(fgRef, bg);
                    double ratio = contrast(fg, bg);
                    if (ratio + 0.0001 < pair.threshold) {
                        failures.add(String.format(Locale.US,
                                "%s | %s | %s | fill=#%s text=#%s bg=#%s ratio=%.2f:1 < %.1f:1",
                                pair.id, night ? "night" : "day", host[0], fill, fg, bg, ratio, pair.threshold));
                    }
                    // 只有焦点态需要「环对填充」达标；按下/选中态的描边属装饰，不承担焦点指示。
                    if (pair.checkRing && pair.states.contains("focused")) {
                        String ring = drawableStroke(pair.background, pair.states, night);
                        if (ring == null) {
                            failures.add(pair.id + " | 缺少焦点环：焦点态填充必须带 stroke");
                        } else {
                            String ringColor = composite(ring, bg);
                            double ringRatio = contrast(ringColor, bg);
                            if (ringRatio + 0.0001 < MIN_NON_TEXT) {
                                failures.add(String.format(Locale.US,
                                        "%s | ring=#%s bg=#%s ratio=%.2f:1 < %.1f:1",
                                        pair.id, ringColor, bg, ringRatio, MIN_NON_TEXT));
                            }
                        }
                    }
                }
            }
        }
        if (!failures.isEmpty()) {
            fail("以下焦点/选中态「填充 ↔ 同态前景」不达标（正文 4.5:1 / 环 3:1）：\n  "
                    + String.join("\n  ", failures));
        }
    }

    /**
     * 完整性门 1：全库每一个「恒定浅色状态前景」（两张表亮度都 ≥ 0.7 的 focused/pressed/selected）
     * 都必须被登记或显式豁免，否则把「同一类白字压白底」留在了无人看守的地方。
     */
    @Test
    public void everyConstantLightStateForegroundIsRegisteredOrExempt() throws IOException {
        ensureLoaded();
        Set<String> registered = new LinkedHashSet<>();
        for (Pair pair : PAIRS) registered.add(pair.foreground + ".xml");
        List<String> unaccounted = new ArrayList<>();
        for (Path root : ROOTS) {
            Path dir = root.resolve("color");
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                    String key = keyOf(root, "color", file.getFileName().toString());
                    if (registered.contains(key) || EXEMPT_FOREGROUNDS.containsKey(key)) continue;
                    if (hasConstantLightState(file)) unaccounted.add(key);
                }
            }
        }
        if (!unaccounted.isEmpty()) {
            fail("以下 color 选择器在 focused/pressed/selected 里使用恒定浅色前景，却未被登记或豁免：\n  "
                    + String.join("\n  ", unaccounted)
                    + "\n若它确实是「文字压在有色填充上」，请登记到 PAIRS；否则在 EXEMPT_FOREGROUNDS 写明理由。");
        }
    }

    /**
     * 完整性门 2：浅色焦点填充 + 恒定浅色焦点环这一组合（本次缺陷的形态）必须登记或豁免。
     * 反过来，浅色填充配深色环（如 #E8F0FE + #1A73E8）不是缺陷。
     */
    @Test
    public void everyPaleFillWithPaleRingIsRegisteredOrExempt() throws IOException {
        ensureLoaded();
        Set<String> registered = new LinkedHashSet<>();
        for (Pair pair : PAIRS) registered.add(pair.background + ".xml");
        List<String> unaccounted = new ArrayList<>();
        for (Path root : ROOTS) {
            Path dir = root.resolve("drawable");
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                    String key = keyOf(root, "drawable", file.getFileName().toString());
                    if (registered.contains(key) || EXEMPT_FILLS.containsKey(key)) continue;
                    if (hasPaleFocusFillWithPaleRing(file)) unaccounted.add(key);
                }
            }
        }
        if (!unaccounted.isEmpty()) {
            fail("以下 drawable 选择器的焦点态是「浅色填充 + 恒定浅色环」，却未被登记或豁免：\n  "
                    + String.join("\n  ", unaccounted)
                    + "\n这类组合最容易演变成白字压白底；请登记到 PAIRS 或说明为何安全。");
        }
    }

    /** 两个搜索页必须成对使用共享的填充/前景选择器，防止只改一边或改回本地写法。 */
    @Test
    public void bothTvSearchPagesWireTheSameStatePairResources() throws IOException {
        ensureLoaded();
        String search = read(javaFile("leanback", "ui/activity/SearchActivity.java"));
        String collect = read(javaFile("leanback", "ui/activity/CollectActivity.java"));
        for (String source : new String[]{search, collect}) {
            assertTrue("范围/分组下拉必须使用共享填充选择器（否则焦点态会与文字脱钩）",
                    source.contains("R.drawable.selector_search_scope_item"));
            assertTrue("范围/分组下拉必须使用共享文字选择器",
                    source.contains("R.color.selector_search_scope_text"));
        }
    }

    // --------------------------------------------------------------- resolver

    private enum Host {SCOPE_GLASS, PLAYER_SCRIM}

    private static List<String[]> hosts(Host host, boolean night) {
        List<String[]> result = new ArrayList<>();
        if (host == Host.SCOPE_GLASS) {
            for (String wallpaper : SCOPE_WALLPAPERS) {
                result.add(new String[]{"wallpaper #" + wallpaper, composite(SCOPE_GLASS, wallpaper)});
            }
        } else {
            String scrim = PLAYER_SCRIMS[night ? 1 : 0];
            for (String frame : PLAYER_FRAMES) {
                result.add(new String[]{"video #" + frame, composite(scrim, frame)});
            }
        }
        return result;
    }

    private static final class Pair {

        final String id;
        final String background;
        final Set<String> states;
        final String foreground;
        final Set<String> foregroundStates;
        final Host host;
        final double threshold;
        final boolean checkRing;

        Pair(String id, String background, Set<String> states, String foreground, Set<String> foregroundStates,
             Host host, double threshold, boolean checkRing) {
            this.id = id;
            this.background = background;
            this.states = states;
            this.foreground = foreground;
            this.foregroundStates = foregroundStates;
            this.host = host;
            this.threshold = threshold;
            this.checkRing = checkRing;
        }
    }

    private static Set<String> states(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    // ------------------------------------------------------------- resources

    private static Path ROOT_MAIN;
    private static Path ROOT_LEANBACK;
    private static List<Path> ROOTS;
    private static final Map<String, Map<String, String>> RAW_DAY = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> RAW_NIGHT = new LinkedHashMap<>();
    private static boolean loaded;

    private static void ensureLoaded() {
        if (loaded) return;
        ROOT_MAIN = findRoot("app/src/main/res");
        ROOT_LEANBACK = findRoot("app/src/leanback/res");
        ROOTS = List.of(ROOT_MAIN, ROOT_LEANBACK);
        for (String sourceSet : SOURCE_SETS) {
            loadTables(findRoot("app/src/" + sourceSet + "/res"), sourceSet);
        }
        loaded = true;
    }

    private static void loadTables(Path res, String sourceSet) {
        for (String values : new String[]{"values", "values-night"}) {
            Path dir = res.resolve(values);
            if (!Files.isDirectory(dir)) continue;
            for (String name : new String[]{"colors.xml", "webhtv_tokens.xml"}) {
                Path file = dir.resolve(name);
                if (!Files.isRegularFile(file)) continue;
                String xml = stripComments(read(file));
                Matcher literal = COLOR_ENTRY.matcher(xml);
                while (literal.find()) {
                    table(values.equals("values-night")).put(literal.group(1), "#" + literal.group(2));
                }
                Matcher alias = ALIAS_ENTRY.matcher(xml);
                while (alias.find()) {
                    String value = alias.group(2).trim();
                    if (value.startsWith("#")) continue;
                    table(values.equals("values-night")).put(alias.group(1), value);
                }
            }
        }
    }

    private static Map<String, String> table(boolean night) {
        return night ? RAW_NIGHT.computeIfAbsent("all", k -> new LinkedHashMap<>())
                : RAW_DAY.computeIfAbsent("all", k -> new LinkedHashMap<>());
    }

    private static String color(String ref, boolean night) {
        String value = color(ref, night, 0);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String color(String ref, boolean night, int depth) {
        if (ref == null || depth > 12) return null;
        String value = ref.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith("#")) return expand(value);
        if (value.startsWith("@android:color/")) {
            switch (value.substring("@android:color/".length())) {
                case "black": return "FF000000";
                case "white": return "FFFFFFFF";
                case "transparent": return "00000000";
                default: return null;
            }
        }
        if (value.startsWith("@color/")) {
            String name = value.substring("@color/".length());
            // 资源合并语义：夜间表缺项时回落到日间表（与 Android 的 values-night 回落一致）。
            String raw = table(night).get(name);
            if (raw == null && night) raw = table(false).get(name);
            if (raw != null) return color(raw, night, depth + 1);
            return null;
        }
        if (value.startsWith("?attr/")) {
            String name = value.substring("?attr/".length());
            String target = THEME_ATTRS.get(name);
            return target == null ? null : color(target, night, depth + 1);
        }
        return null;
    }

    private static String expand(String hex) {
        if (hex == null) return null;
        String value = (hex.startsWith("#") ? hex.substring(1) : hex).toUpperCase(Locale.ROOT);
        if (value.length() == 6) return "FF" + value;
        if (value.length() == 8) return value;
        return null;
    }

    private static String drawableFill(String path, Set<String> states, boolean night) {
        return resolveDrawable(path, states, night, false, 0);
    }

    private static String drawableStroke(String path, Set<String> states, boolean night) {
        return resolveDrawable(path, states, night, true, 0);
    }

    private static String resolveDrawable(String path, Set<String> states, boolean night, boolean stroke, int depth) {
        if (path == null || depth > 8) return null;
        Path file = findFile(path);
        if (file == null) return null;
        String xml = stripComments(read(file));
        if (!xml.contains("<selector")) {
            Matcher matcher = (stroke ? STROKE : SOLID).matcher(xml);
            return matcher.find() ? color(matcher.group(1), night) : null;
        }
        for (String[] item : items(xml)) {
            if (!matches(item[0], states)) continue;
            Matcher drawable = DRAWABLE_ATTR.matcher(item[0]);
            if (drawable.find()) {
                String nested = drawable.group(1);
                if (nested.startsWith("@drawable/")) {
                    return resolveDrawable(nestedTarget(path, nested.substring("@drawable/".length())),
                            states, night, stroke, depth + 1);
                }
            }
            Matcher matcher = (stroke ? STROKE : SOLID).matcher(item[1] == null ? item[0] : item[1]);
            if (matcher.find()) return color(matcher.group(1), night);
            return null;
        }
        return null;
    }

    private static String foregroundRef(String path, Set<String> states, boolean night) {
        Path file = findFile(path);
        if (file == null) return null;
        String xml = stripComments(read(file));
        if (!xml.contains("<selector") && !xml.contains("<item")) {
            Matcher matcher = Pattern.compile("<color[^>]*>\\s*([^<]+?)\\s*</color>").matcher(xml);
            return matcher.find() ? color(matcher.group(1), night) : null;
        }
        for (String[] item : items(xml)) {
            if (!matches(item[0], states)) continue;
            Matcher matcher = COLOR_ATTR.matcher(item[0]);
            if (matcher.find()) return color(matcher.group(1), night);
            return null;
        }
        return null;
    }

    private static List<String[]> items(String xml) {
        List<String[]> items = new ArrayList<>();
        Matcher matcher = ITEM.matcher(xml);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String body = matcher.group(3);
            items.add(new String[]{attrs, body});
        }
        return items;
    }

    private static boolean matches(String attrs, Set<String> states) {
        Matcher matcher = STATE_ATTR.matcher(attrs);
        while (matcher.find()) {
            boolean required = "true".equals(matcher.group(2));
            boolean present = states.contains(matcher.group(1));
            if (required != present) return false;
        }
        return true;
    }

    private static String nestedTarget(String currentPath, String name) {
        int slash = currentPath.indexOf('/');
        String sourceSet = currentPath.substring(0, slash);
        return sourceSet + "/drawable/" + name;
    }

    private static Path findFile(String path) {
        int slash = path.indexOf('/');
        String sourceSet = path.substring(0, slash);
        String rest = path.substring(slash + 1);
        if (rest.endsWith(".xml")) rest = rest.substring(0, rest.length() - ".xml".length());
        Path root = "main".equals(sourceSet) ? ROOT_MAIN : "leanback".equals(sourceSet) ? ROOT_LEANBACK : null;
        if (root == null) return null;
        Path file = root.resolve(rest + ".xml");
        return Files.isRegularFile(file) ? file : null;
    }

    private static String keyOf(Path root, String sub, String file) {
        String sourceSet = root.equals(ROOT_MAIN) ? "main" : "leanback";
        return sourceSet + "/" + sub + "/" + file;
    }

    private static Path findRoot(String relative) {
        Path direct = Path.of(relative);
        if (Files.isDirectory(direct)) return direct;
        Path up = Path.of("..").resolve(relative);
        if (Files.isDirectory(up)) return up;
        throw new IllegalStateException("无法定位资源目录：" + relative);
    }

    private static Path javaFile(String sourceSet, String relative) {
        String path = "app/src/" + sourceSet + "/java/com/fongmi/android/tv/" + relative;
        Path direct = Path.of(path);
        if (Files.isRegularFile(direct)) return direct;
        return Path.of("..").resolve(path);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取失败：" + path, e);
        }
    }

    private static String stripComments(String xml) {
        return COMMENT.matcher(xml).replaceAll(" ");
    }

    // ------------------------------------------------------- completeness gates

    private static boolean hasConstantLightState(Path file) {
        String xml = stripComments(read(file));
        for (String state : new String[]{"focused", "pressed", "selected"}) {
            String day = foregroundRef(keyOf(file), states(state), false);
            String night = foregroundRef(keyOf(file), states(state), true);
            if (day == null || night == null) continue;
            if (luminance(composite(day, NEUTRAL_HOST)) >= CONSTANT_LIGHT_LUMINANCE
                    && luminance(composite(night, NEUTRAL_HOST)) >= CONSTANT_LIGHT_LUMINANCE) return true;
        }
        return false;
    }

    private static String keyOf(Path colorFile) {
        String path = colorFile.toString().replace('\\', '/');
        int res = path.indexOf("/res/color/");
        if (res < 0) return path;
        String prefix = path.substring(0, res);
        String sourceSet = prefix.endsWith("/main") ? "main" : prefix.endsWith("/leanback") ? "leanback"
                : prefix.endsWith("/mobile") ? "mobile" : "?";
        return sourceSet + "/color/" + colorFile.getFileName();
    }

    private static boolean hasPaleFocusFillWithPaleRing(Path file) {
        String path = file.toString().replace('\\', '/');
        int res = path.indexOf("/res/drawable/");
        if (res < 0) return false;
        String prefix = path.substring(0, res);
        String sourceSet = prefix.endsWith("/main") ? "main" : prefix.endsWith("/leanback") ? "leanback"
                : prefix.endsWith("/mobile") ? "mobile" : "?";
        String key = sourceSet + "/drawable/" + file.getFileName();
        // 只有带焦点分支的选择器才属于本门禁；纯 shape 不在此列。
        if (!stripComments(read(file)).contains("state_focused")) return false;
        String fill = drawableFill(key, states("focused"), false);
        if (fill == null) return false;
        String overHost = composite(fill, NEUTRAL_HOST);
        if (luminance(overHost) < LIGHT_FILL_LUMINANCE) return false;
        String ring = drawableStroke(key, states("focused"), false);
        if (ring == null) return false;
        if (luminance(composite(ring, NEUTRAL_HOST)) < CONSTANT_LIGHT_LUMINANCE) return false;
        return true;
    }

    // ------------------------------------------------------------------- colour

    private static String composite(String foreground, String background) {
        if (foreground == null || background == null) return null;
        String fg = expand(foreground);
        String bg = expand(background);
        if (fg == null || bg == null) return null;
        double alpha = Integer.parseInt(fg.substring(0, 2), 16) / 255.0;
        StringBuilder out = new StringBuilder("FF");
        for (int i = 2; i < 8; i += 2) {
            int f = Integer.parseInt(fg.substring(i, i + 2), 16);
            int b = Integer.parseInt(bg.substring(i, i + 2), 16);
            out.append(String.format(Locale.ROOT, "%02X", Math.round(alpha * f + (1 - alpha) * b)));
        }
        return out.toString();
    }

    private static double luminance(String color) {
        String hex = expand(color);
        if (hex == null) throw new IllegalArgumentException("不是合法颜色值：" + color);
        double r = channel(Integer.parseInt(hex.substring(2, 4), 16));
        double g = channel(Integer.parseInt(hex.substring(4, 6), 16));
        double b = channel(Integer.parseInt(hex.substring(6, 8), 16));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static double contrast(String foreground, String background) {
        double a = luminance(foreground);
        double b = luminance(background);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
}
