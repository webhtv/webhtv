package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视端 Tab 条焦点环：环色必须跟随宿主面板的明暗性质。
 *
 * <p>判据不是「是不是 Tab」，而是**宿主面板是否固定明暗**：
 * <ul>
 *   <li>宿主是**固定**浅/深色（两张表相同）→ 环色必须与调色板无关，否则夜间表取值会压在同色
 *       底上而看不见（{@code selector_ad_stats_item} 就是这种，它的宿主
 *       {@code shape_ad_stats_content} 固定 {@code #FAFBFC}）；</li>
 *   <li>宿主**随主题变**（引用 {@code ?attr/colorSurface*}）→ 环色也必须跟着变，否则就是
 *       「宿主变了、环没变」的错配。</li>
 * </ul>
 *
 * <p>{@code selector_mpv_tab_focus} 此前属于后者的错配：它的 4 个宿主都用
 * {@code shape_shell_proxy_dialog = ?attr/colorSurfaceContainerHigh}（随主题变），
 * 而环色写死 {@code #1A73E8}。实测非文本 UI 门槛 3:1：写死值在深色面板上只有 3.00:1
 * （刚好卡在门槛上），改用 {@code ?attr/tvFocusRing} 后浅色 5.23:1、深色 12.63:1。
 */
public class TvTabFocusRingPaletteContractTest {

    private static final String TAB_FOCUS = "app/src/main/res/drawable/selector_mpv_tab_focus.xml";
    private static final String AD_STATS = "app/src/leanback/res/drawable/selector_ad_stats_item.xml";
    private static final String HOST_PANEL = "app/src/main/res/drawable/shape_shell_proxy_dialog.xml";
    private static final String AD_STATS_PANEL = "app/src/leanback/res/drawable/shape_ad_stats_content.xml";
    private static final Pattern RAW_HEX = Pattern.compile("\"#[0-9A-Fa-f]{6,8}\"");

    /** 随主题变的面板 → 环色必须走主题属性。 */
    @Test
    public void tabFocusRingFollowsTheThemeBecauseItsHostPanelDoes() throws Exception {
        assertTrue("宿主面板必须走语义 surface（随主题变）",
                read(HOST_PANEL).contains("?attr/colorSurfaceContainerHigh"));
        String ring = read(TAB_FOCUS);
        assertTrue("环色必须走主题焦点环属性", ring.contains("?attr/tvFocusRing"));
        assertTrue("环宽必须走唯一 token", ring.contains("@dimen/webhtv_focus_ring_width"));
        assertFalse("焦点/按下态不得再写死调色板色值", RAW_HEX.matcher(ring).find());
        assertFalse("不得再写死 2dp 宽度", ring.contains("android:width=\"2dp\""));
    }

    /**
     * 固定浅色面板 → 环色必须与调色板无关。这条是**反例守卫**：
     * 若有人「统一」地把 selector_ad_stats_item 也改成 ?attr/tvFocusRing，
     * 夜间表的浅蓝环会压在同色底上看不见（约 1.6:1）。
     *
     * <p>注意：断言前必须剥掉注释——该文件的注释里**故意**提到 {@code ?attr/tvFocusRing}
     * （用来说明为什么不能用它），不剥离就会把解释当成违规。
     */
    @Test
    public void fixedLightPanelKeepsAPaletteIndependentRing() throws Exception {
        assertTrue("广告统计面板必须是固定浅色（两张表相同）",
                read(AD_STATS_PANEL).contains("#FAFBFC"));
        String ring = withoutXmlComments(read(AD_STATS));
        assertFalse("固定浅色面板上的环色不得取主题属性", ring.contains("?attr/tvFocusRing"));
        assertTrue("它应继续用与调色板无关的深蓝", ring.contains("#1A73E8"));
        assertTrue("但宽度仍必须走唯一 token", ring.contains("@dimen/webhtv_focus_ring_width"));
    }

    /**
     * 判据本身要留在注释里，否则下一个人会按「都是 Tab」把它们统一掉。
     */
    @Test
    public void theCriterionStaysDocumented() throws Exception {
        String ring = read(TAB_FOCUS);
        assertTrue("必须记录判据是宿主面板是否固定明暗", ring.contains("宿主面板是否固定明暗"));
        assertTrue("必须点名固定浅色的反例", ring.contains("shape_ad_stats_content"));
        assertTrue("必须记录实测对比度", ring.contains("3.00:1"));
    }

    // ------------------------------------------------------------------ helpers

    private static String withoutXmlComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
