package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视端「加载态 / 空态」的规格契约。
 *
 * <p>依据是本仓库自己的设计文档，不是外部偏好：
 * <ul>
 *   <li>§9.9：「空态：<b>标题 + 解释 + 一个主动作</b>；<b>不能只显示一句"暂无数据"</b>」，
 *       「加载使用统一 spinner/progress……不使用多种颜色动画」；</li>
 *   <li>§6.2 字阶表：空态属 <b>Display</b> 档，电视为 <b>40–48sp</b>。</li>
 * </ul>
 *
 * <p>改动前 leanback 的 {@code view_empty.xml} 只有一个 16sp 的单行文案
 * （{@code @string/error_empty} =「这里什麽都没有 👀」），三条都不满足；加载指示器则写死
 * {@code @color/white}，与同屏其它状态元素用的 {@code ?attr/webhtvColorOnWallpaper} 是两份声明。
 *
 * <p>本测试只约束电视端：{@code view_empty.xml} / {@code view_progress.xml} 是分 flavor 的，
 * 手机端同类问题留给手机批次（见任务文档 §7）。
 */
public class TvStateSurfaceContractTest {

    private static final String LEANBACK_EMPTY = "app/src/leanback/res/layout/view_empty.xml";
    private static final String LEANBACK_PROGRESS = "app/src/leanback/res/layout/view_progress.xml";
    private static final String PROGRESS_LAYOUT =
            "app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java";
    private static final String KEEP_ACTIVITY =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/KeepActivity.java";
    private static final String STRINGS = "app/src/main/res/values/strings.xml";
    private static final String STRINGS_CN = "app/src/main/res/values-zh-rCN/strings.xml";

    /** §6.2：电视端空态标题属 Display 档。 */
    private static final int MIN_DISPLAY_SP = 40;
    private static final int MAX_DISPLAY_SP = 48;

    private static final Pattern RAW_COLOR = Pattern.compile("\"#[0-9A-Fa-f]{6,8}\"");

    // ------------------------------------------------------------------ 空态三件套

    @Test
    public void emptyStateCarriesTitleDescriptionAndOnePrimaryAction() throws Exception {
        String layout = read(LEANBACK_EMPTY);
        for (String id : new String[]{"text", "description", "action"}) {
            assertTrue("空态必须声明 @+id/" + id + "（§9.9 的标题/解释/主动作）",
                    layout.contains("@+id/" + id));
        }
        // 只有标题时渲染结果与改动前一致，既有宿主不被强制改变观感。
        assertTrue("description 必须默认 GONE，否则既有宿主会被强制改变观感",
                block(layout, "description").contains("android:visibility=\"gone\""));
        assertTrue("action 必须默认 GONE，同上",
                block(layout, "action").contains("android:visibility=\"gone\""));
    }

    @Test
    public void emptyStateTitleUsesTheDisplayTier() throws Exception {
        String title = block(read(LEANBACK_EMPTY), "text");
        Matcher size = Pattern.compile("android:textSize=\"(\\d+)sp\"").matcher(title);
        assertTrue("空态标题必须显式声明字号", size.find());
        int sp = Integer.parseInt(size.group(1));
        assertTrue("空态标题 " + sp + "sp 低于 §6.2 的电视 Display 档（" + MIN_DISPLAY_SP + "–" + MAX_DISPLAY_SP + "sp）",
                sp >= MIN_DISPLAY_SP && sp <= MAX_DISPLAY_SP);
    }

    @Test
    public void emptyStateUsesWallpaperRolesNotRawColours() throws Exception {
        String layout = withoutComments(read(LEANBACK_EMPTY));
        assertFalse("空态不得出现裸十六进制", RAW_COLOR.matcher(layout).find());
        // 精确比较：webhtvColorOnWallpaperVariant 里包含 webhtvColorOnWallpaper 这个子串，
        // 用 contains 会让「标题误用次级色」也通过。
        assertEquals("标题必须走壁纸前景角色（纯白）",
                "?attr/webhtvColorOnWallpaper", textColourOf(layout, "text"));
        assertEquals("解释必须走壁纸次级角色（否则与标题同权重，解释等于没解释）",
                "?attr/webhtvColorOnWallpaperVariant", textColourOf(layout, "description"));
        assertEquals("主按钮文案必须走壁纸前景角色",
                "?attr/webhtvColorOnWallpaper", textColourOf(layout, "action"));
    }

    /**
     * 主按钮必须可聚焦且带焦点环：电视端焦点不可见的按钮等于没有按钮。
     */
    @Test
    public void emptyStateActionIsFocusableAndDrawsTheAppSurfaceRing() throws Exception {
        String action = block(withoutComments(read(LEANBACK_EMPTY)), "action");
        assertTrue("主按钮必须可聚焦", action.contains("android:focusable=\"true\""));
        assertTrue("主按钮必须有背景 selector", action.contains("@drawable/selector_empty_action"));

        String selector = read("app/src/leanback/res/drawable/selector_empty_action.xml");
        assertTrue("selector 的焦点态必须指向带环的 shape",
                selector.contains("@drawable/shape_empty_action_focused"));
        String focused = read("app/src/leanback/res/drawable/shape_empty_action_focused.xml");
        // 空态画在壁纸/页面背景上，属于「应用表面」族；用 ?attr/tvPlayerRing（固定深玻璃族）
        // 会重现阶段 M 修过的「同一屏两种环色」。
        assertTrue("焦点环必须取应用表面族的 ?attr/tvFocusRing",
                focused.contains("?attr/tvFocusRing"));
        assertTrue("焦点环宽度必须取唯一来源 @dimen/webhtv_focus_ring_width",
                focused.contains("@dimen/webhtv_focus_ring_width"));
    }

    // ------------------------------------------------------------------ 加载态

    @Test
    public void progressIndicatorUsesASemanticRoleInsteadOfARawColour() throws Exception {
        String layout = withoutComments(read(LEANBACK_PROGRESS));
        assertFalse("加载指示器不得写死颜色", layout.contains("app:indicatorColor=\"@color/"));
        assertFalse("加载指示器不得出现裸十六进制", RAW_COLOR.matcher(layout).find());
        assertTrue("加载指示器必须走壁纸前景角色",
                layout.contains("app:indicatorColor=\"?attr/webhtvColorOnWallpaper\""));
    }

    // ------------------------------------------------------------------ 宿主接入

    @Test
    public void progressLayoutExposesEmptyContentAndToleratesFlavourSpecificLayouts() throws Exception {
        String source = read(PROGRESS_LAYOUT);
        assertTrue("必须提供 setEmptyContent 供宿主接入", source.contains("public void setEmptyContent("));
        // view_empty.xml 是分 flavor 的，mobile 版没有 description/action，而本类在 main 源集：
        // 必须按 id 查找并做空值守卫，不能直接用 ViewEmptyBinding 的字段。
        assertTrue("必须用 findViewById 兼容 flavor 差异", source.contains("findViewById(R.id.description)"));
        assertTrue("必须用 findViewById 兼容 flavor 差异", source.contains("findViewById(R.id.action)"));
        assertFalse("不得直接引用 binding 的 flavor 专属字段",
                source.contains("mEmptyBinding.description") || source.contains("mEmptyBinding.action"));
    }

    @Test
    public void keepScreenWiresARealEmptyStateWithAnAction() throws Exception {
        String source = read(KEEP_ACTIVITY);
        assertTrue("收藏页必须接入空态内容", source.contains("setEmptyContent("));
        for (String string : new String[]{
                "keep_empty_title", "keep_empty_description", "keep_empty_action"}) {
            assertTrue("收藏页必须引用 " + string, source.contains(string));
            assertTrue("默认字符串表必须声明 " + string, read(STRINGS).contains("name=\"" + string + "\""));
            assertTrue("简中字符串表必须声明 " + string, read(STRINGS_CN).contains("name=\"" + string + "\""));
        }
        assertTrue("主按钮必须有真实动作（不能是死按钮）", source.contains("view -> goHome()"));
    }

    /**
     * 删除路径必须驱动状态机。
     *
     * <p>实机复现的缺陷：{@code KeepActivity.onItemDelete} 此前只退出删除模式，
     * 从不调用 {@code showContent(true, size)}，于是用户删掉最后一条收藏后看到的是
     * 一片空白——连空态都不出现。{@code HistoryActivity} 本来就做对了，
     * 本测试把两者的写法对齐后钉住，避免再退化。
     */
    @Test
    public void deletePathDrivesTheStateMachineSoTheEmptyStateIsReachable() throws Exception {
        // 「驱动状态机」已收敛到 BaseDiffAdapter 的 OnCountChanged 钩子（宿主注册一次，
        // 之后任何路径改动列表都会自动跟上），所以这里断言**钩子已注册**，而不是某个回调里
        // 手写了一行 showContent——后者正是当初会漏掉「删除全部」的原因。
        // 收敛契约见 app/src/test/java/.../ui/adapter/ListStateConvergenceContractTest.java。
        for (String source : new String[]{KEEP_ACTIVITY,
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java"}) {
            assertTrue(source + " 必须注册 OnCountChanged 钩子（删除路径靠它驱动状态机）",
                    read(source).contains("mAdapter.setOnCountChanged(count -> mBinding.progressLayout.showContent(true, count))"));
        }
    }

    /**
     * 电视端历史页的空态三件套。
     *
     * <p>TV-4 当时只接了收藏页，历史页一直显示布局里的默认单行文案（真机截图里就是
     * 「这里什麽都没有 👀」）。本批补齐——它复用手机端批次已加的三语字符串，因此不需要新文案。
     */
    @Test
    public void historyScreenWiresARealEmptyStateWithAnAction() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java");
        assertTrue("历史页必须接入空态内容", source.contains("setEmptyContent("));
        for (String string : new String[]{
                "history_empty_title", "history_empty_description", "history_empty_action"}) {
            assertTrue("历史页必须引用 " + string, source.contains(string));
            assertTrue("默认字符串表必须声明 " + string, read(STRINGS).contains("name=\"" + string + "\""));
            assertTrue("简中字符串表必须声明 " + string, read(STRINGS_CN).contains("name=\"" + string + "\""));
        }
        assertTrue("主按钮必须有真实动作（不能是死按钮）", source.contains("view -> goHome()"));
        assertTrue("goHome 必须真的回首页", source.contains("HomeActivity.class"));
    }

    // ------------------------------------------------------------------ helpers

    /** 取某个 {@code @+id/xxx} 控件自身的属性文本。 */
    private static String block(String layout, String id) {
        Matcher matcher = Pattern.compile("<[A-Za-z0-9_.]+[^>]*@\\+id/" + Pattern.quote(id)
                + "\"(.*?)(?:/>|>)", Pattern.DOTALL).matcher(layout);
        assertTrue("布局里必须能找到 @+id/" + id, matcher.find());
        return matcher.group(1);
    }

    /** 取某个 {@code @+id/xxx} 控件声明的 {@code android:textColor} 原文。 */
    private static String textColourOf(String layout, String id) {
        Matcher colour = Pattern.compile("android:textColor=\"([^\"]+)\"").matcher(block(layout, id));
        assertTrue("@+id/" + id + " 必须显式声明 textColor", colour.find());
        return colour.group(1);
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
