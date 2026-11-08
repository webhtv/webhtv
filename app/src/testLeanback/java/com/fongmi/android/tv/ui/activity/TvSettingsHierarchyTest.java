package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视设置页的信息层级契约，以及详情页「只有标签没有内容」的空元数据契约。
 *
 * <p>用户观察（2026-10-12 实机截图，1920x1080）：设置页 17 项平铺成一张网格，
 * 点播/直播/壁纸（信息源）、增强/播放/弹幕/去广告（播放能力）、TMDB/AI/外观/个性
 * （个性化）、备份/恢复/缓存/关于（数据管理）混排，没有任何分组标题，用户只能整屏扫；
 * 同时每一项的标签与取值都是 18sp 纯白，视觉重量完全一样。
 *
 * <p>根因是 TV 端只有 {@code webhtv_on_wallpaper} 一个壁纸前景角色，而它是纯白：
 * {@code TvFocusRingContractTest} 记录过原因——{@code ?attr/colorOnSurfaceVariant}
 * 画在深色卡片条带上只有 1.35:1，所以唯一可读的选择就是纯白。本次新增
 * {@code webhtvColorOnWallpaperVariant} 作为次级角色，并把取值应用在**有色带承载**的位置
 * （卡片集数行、设置项取值）；分组标题直接画在壁纸上，因此保持纯白、靠字号与间距表达层级。
 *
 * <p>本测试钉住四件事：
 * <ol>
 *   <li>设置页确有四个分组标题，且每个标题落在它那一组的首行之前、顺序与分组一致；</li>
 *   <li>只有设置项的取值使用次级角色，标签与分组标题保持纯白；</li>
 *   <li>重排后所有 {@code nextFocus*} 引用仍然指向存在的行，且纵向焦点图仍然连通
 *       （这是"移动一行却忘了改焦点链"最容易留下的缺陷）；</li>
 *   <li>详情页在值缺失时清空文本，而不是留下一个只有标签的空行。</li>
 * </ol>
 */
public class TvSettingsHierarchyTest {

    private static final String SETTING = "app/src/leanback/res/layout/activity_setting.xml";
    private static final String LEANBACK_STYLES = "app/src/leanback/res/values/styles.xml";
    private static final String STRINGS = "app/src/main/res/values/strings.xml";
    private static final String STRINGS_CN = "app/src/main/res/values-zh-rCN/strings.xml";
    private static final String VIDEO_ACTIVITY =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";

    private static final String HEADER_STYLE = "style=\"@style/Widget.WebHTV.TvGroupHeader\"";
    private static final String HEADER_REFERENCE = "@style/Widget.WebHTV.TvGroupHeader";

    /** 分组顺序：内容源 → 播放与内容 → 个性化与智能 → 数据与网络。 */
    private static final String[] GROUPS = {
            "setting_group_sources", "setting_group_playback", "setting_group_personal", "setting_group_data",
    };

    /** 每一组的首行 id；标题必须声明在该行之前。 */
    private static final String[] FIRST_ROW_OF_GROUP = {"vod", "enhance", "tmdb", "incognito"};

    /** 设置项的取值控件；这些是唯一允许使用次级角色的元素。 */
    private static final String[] VALUE_IDS = {
            "vodUrl", "liveUrl", "wallUrl", "incognitoText", "dohText", "cacheText", "versionText",
    };

    private static final Pattern DECLARATION = Pattern.compile("@\\+id/([A-Za-z0-9_]+)");
    private static final Pattern VERTICAL_REFERENCE =
            Pattern.compile("android:nextFocus(Up|Down)=\"@id/([A-Za-z0-9_]+)\"");

    // ------------------------------------------------------------------ 分组

    @Test
    public void settingsPageIsGroupedUnderFourHeadings() throws Exception {
        String layout = read(SETTING);
        assertEquals("设置页必须有四个分组标题", 4, count(layout, HEADER_STYLE));

        int previousHeader = -1;
        for (int index = 0; index < GROUPS.length; index++) {
            String group = GROUPS[index];
            int header = layout.indexOf("@string/" + group);
            int firstRow = layout.indexOf("@+id/" + FIRST_ROW_OF_GROUP[index]);
            assertTrue(group + " 必须被布局引用", header >= 0);
            assertTrue(group + " 的标题必须声明在该组首行 " + FIRST_ROW_OF_GROUP[index] + " 之前",
                    firstRow > header);
            assertTrue(group + " 的标题顺序必须与分组顺序一致", header > previousHeader);
            previousHeader = header;
        }

        // 字幕设置属于「播放与内容」：它必须落在播放组标题之后、个性化组标题之前。
        int playback = layout.indexOf("@string/setting_group_playback");
        int personal = layout.indexOf("@string/setting_group_personal");
        int subtitle = layout.indexOf("@+id/subtitle");
        assertTrue("字幕设置必须留在播放组内（标题顺序被改动过？）",
                subtitle > playback && subtitle < personal);

        for (String group : GROUPS) {
            assertTrue("默认字符串表必须声明 " + group, read(STRINGS).contains("name=\"" + group + "\""));
            assertTrue("简中字符串表必须声明 " + group, read(STRINGS_CN).contains("name=\"" + group + "\""));
        }
    }

    // ------------------------------------------------------------------ 层级

    @Test
    public void groupHeadingsAndLabelsStayPureWhiteWhileValuesAreSecondary() throws Exception {
        String layout = read(SETTING);
        assertEquals("只有设置项的取值可以使用次级角色（7 个取值 + 去广告摘要）",
                8, count(layout, "?attr/webhtvColorOnWallpaperVariant"));
        for (String id : VALUE_IDS) {
            assertEquals(id + " 的取值必须走壁纸次级角色",
                    "?attr/webhtvColorOnWallpaperVariant", textColourOf(layout, id));
        }

        // 注释里会解释"为什么标题不能用次级角色"，所以负向断言必须先剥掉注释再查。
        String styles = read(LEANBACK_STYLES).replaceAll("(?s)<!--.*?-->", " ");
        assertTrue("分组标题必须存在", styles.contains("name=\"Widget.WebHTV.TvGroupHeader\""));
        assertTrue("分组标题必须用纯白：它直接画在壁纸上，次级角色在实测壁纸上只有 3.84:1",
                styles.contains("<item name=\"android:textColor\">?attr/webhtvColorOnWallpaper</item>"));
        assertFalse("分组标题不得使用次级角色",
                styles.contains("webhtvColorOnWallpaperVariant"));
    }

    // ------------------------------------------------------------------ 焦点链

    /**
     * 重排分组最容易留下的缺陷是"移动了一行、却没有同步它的 {@code nextFocus*} 引用"：
     * 指向已删除的 id 会让该方向的 D-pad 失效，断开的纵向图则会让焦点跳不回上/下一组。
     * 两者都只看 XML 文本即可判定，不需要设备。
     */
    @Test
    public void reorderedRowsKeepEveryFocusReferenceAndAConnectedVerticalGraph() throws Exception {
        String layout = read(SETTING);
        Set<String> declared = new LinkedHashSet<>();
        Matcher declaration = DECLARATION.matcher(layout);
        while (declaration.find()) declared.add(declaration.group(1));
        assertTrue("设置页必须还有可聚焦的行", declared.size() > 10);

        Map<String, Set<String>> vertical = new HashMap<>();
        List<String> dangling = new ArrayList<>();
        Matcher reference = VERTICAL_REFERENCE.matcher(layout);
        String currentRow = null;
        // 逐行扫描：先记住当前行 id，再把它后面的 nextFocusUp/Down 归到该行。
        for (String line : layout.split("\n")) {
            Matcher id = Pattern.compile("android:id=\"@\\+id/([A-Za-z0-9_]+)\"").matcher(line);
            if (id.find()) currentRow = id.group(1);
            Matcher edge = VERTICAL_REFERENCE.matcher(line);
            while (edge.find()) {
                String target = edge.group(2);
                if (!declared.contains(target)) dangling.add(currentRow + " -> " + target);
                if (currentRow == null) continue;
                vertical.computeIfAbsent(currentRow, key -> new LinkedHashSet<>()).add(target);
                vertical.computeIfAbsent(target, key -> new LinkedHashSet<>()).add(currentRow);
            }
        }
        assertTrue("焦点链引用了不存在的 id，D-pad 会在该方向失效: " + dangling, dangling.isEmpty());

        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add("enhance");
        visited.add("enhance");
        while (!queue.isEmpty()) {
            for (String next : vertical.getOrDefault(queue.poll(), Set.of())) {
                if (visited.add(next)) queue.add(next);
            }
        }
        List<String> orphans = new ArrayList<>();
        for (String row : vertical.keySet()) {
            if (!visited.contains(row)) orphans.add(row);
        }
        assertTrue("这些行被移出了纵向焦点图，D-pad 到不了: " + orphans, orphans.isEmpty());
    }

    // ------------------------------------------------------------------ 空元数据

    /**
     * 详情页此前会渲染出只有标签的空行（实机截图：{@code 导演：} / {@code 演员：} 后面什么都没有）。
     *
     * <p>原因是带标签的字段渲染的是 {@code getString(resId, "")}，也就是 {@code "导演："}
     * 这样的非空字符串，随后 {@code setVideoDetailsVisible()} 按 {@code !getText().isEmpty()}
     * 又把它翻回 {@code VISIBLE}；而"空值不清空已有文本"的旧守卫还会在换集后留下上一条目的元数据。
     *
     * <p>{@code resId == 0} 的 remark 不走这条：它的文本可能由 TMDB 集数信息经另一条路径写入。
     */
    @Test
    public void emptyLabelledMetadataIsClearedInsteadOfLeavingALabelOnlyRow() throws Exception {
        String activity = read(VIDEO_ACTIVITY);
        assertTrue("值缺失时必须清空文本，否则空标签会被 setVideoDetailsVisible 翻回可见",
                activity.contains("if (!TextUtils.isEmpty(view.getText())) view.setText(null);"));
        assertTrue("只有带标签的字段（resId > 0）可以清空，remark 由 TMDB 集数信息单独写入",
                activity.contains("if (resId > 0) {"));
        assertFalse("旧的\"空值直接返回\"守卫会让上一条目的元数据留在屏幕上",
                activity.contains("if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;"));
    }

    // ------------------------------------------------------------------ helpers

    private static int count(String source, String needle) {
        int total = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            total++;
            index = source.indexOf(needle, index + needle.length());
        }
        return total;
    }

    /** 取某个 {@code @+id/xxx} 控件上的 {@code android:textColor}。 */
    private static String textColourOf(String layout, String id) {
        Matcher block = Pattern.compile("@\\+id/" + Pattern.quote(id) + "\"(.*?)(?:/>|</)",
                Pattern.DOTALL).matcher(layout);
        assertTrue("布局里必须能找到 @+id/" + id, block.find());
        Matcher colour = Pattern.compile("android:textColor=\"([^\"]+)\"").matcher(block.group(1));
        assertTrue("@+id/" + id + " 必须显式声明 textColor", colour.find());
        return colour.group(1);
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
