package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视版搜索页「浅色模式下搜索那一栏的框看不见 / 字看不见」契约。
 *
 * <p>用户报告：调到浅色模式后，搜索那一栏的框不知道去哪了。真实原因是两个叠加缺陷：
 * <ol>
 *   <li>该行输入框 {@code CustomSearchView} 的 {@code android:background} 是 {@code @null}，
 *       整行只画文字、没有任何宿主轮廓，所以"框"本就不存在；</li>
 *   <li>该行 8 处前景写死 {@code @color/white}，而 2026-10-05 的 TV 浅色工程只迁移了 405 处
 *       前景、漏掉了本文件（leanback 其余布局已有 367 处 {@code ?attr/webhtvColorOnWallpaper}），
 *       于是浅色壁纸上是白字。</li>
 * </ol>
 *
 * <p>判据与 {@code shape_keyboard_normal.xml} 同族（恒定浅色前景压在壁纸上的半透明宿主）：
 * 白字在 {@code black_40} 键底实测约 6.6:1，而 {@code black_20} 只有 4.36:1，故本页底色取
 * {@code black_40}。焦点环沿用电视唯一宽度 token 与唯一环色 token，因此两种模式下都有焦点线索。
 */
public class TvSearchSurfaceContractTest {

    private static final String SEARCH_LAYOUT = "app/src/leanback/res/layout/activity_search.xml";
    private static final String SEARCH_FIELD = "app/src/leanback/res/drawable/shape_tv_search_field.xml";
    private static final String SEARCH_FIELD_FOCUSED = "app/src/leanback/res/drawable/shape_tv_search_field_focused.xml";
    private static final String SEARCH_FIELD_SELECTOR = "app/src/leanback/res/drawable/selector_tv_search_field.xml";

    private static final String WIDTH = "@dimen/webhtv_focus_ring_width";
    private static final String RING_COLOR = "?attr/tvFocusRing";
    private static final String ON_WALLPAPER = "?attr/webhtvColorOnWallpaper";
    /** 条目遮罩槽位（本批把两个输入框 shape 的填充接到它）。 */
    private static final String ITEM_OVERLAY = "?attr/webhtvColorItemOverlay";
    private static final String TOKENS_DAY = "app/src/main/res/values/webhtv_tokens.xml";
    private static final String STYLES = "app/src/main/res/values/webhtv_styles.xml";
    private static final String BLACK_40 = "#66000000";

    /** 搜索页不允许再有写死的白色前景：浅色壁纸下它不可读。 */
    @Test
    public void searchScreenForegroundsFollowTheWallpaperTokens() throws Exception {
        String xml = values(read(SEARCH_LAYOUT));
        assertFalse("搜索页不允许再写死 @color/white 前景（浅色壁纸下白字不可读）",
                xml.contains("android:textColor=\"@color/white\""));
        assertFalse("搜索页的 hint 不允许再写死 @color/white（浅色壁纸下不可读）",
                xml.contains("android:textColorHint=\"@color/white\""));
        assertTrue("搜索页标签必须使用随壁纸恒定的前景角色",
                xml.contains("android:textColor=\"" + ON_WALLPAPER + "\""));
        assertTrue("输入框 hint 必须使用玩家控件弱化色（与 keyboard/adapter 同族）",
                xml.contains("android:textColorHint=\"@color/webhtv_color_player_control_muted\""));
    }

    /**
     * 输入框必须有实体宿主：此前 {@code #keyword} 自己是 {@code @null} 背景，整行没有轮廓。
     * 现在宿主是 {@code #keywordField}，它的背景必须指向带轮廓的 selector。
     */
    @Test
    public void searchInputHasAVisibleFieldHost() throws Exception {
        String xml = values(read(SEARCH_LAYOUT));
        assertTrue("搜索输入框必须有实体宿主视图 #keywordField",
                xml.contains("android:id=\"@+id/keywordField\""));
        assertTrue("#keywordField 必须使用带轮廓与焦点环的 selector",
                xml.contains("android:background=\"@drawable/selector_tv_search_field\""));
        assertTrue("selector 必须同时提供聚焦与常态两个 item",
                values(read(SEARCH_FIELD_SELECTOR))
                        .contains("<item android:drawable=\"@drawable/shape_tv_search_field_focused\" android:state_focused=\"true\" />"));
        assertTrue("#keywordField 必须合并子控件状态：焦点全部落在子控件 #keyword 上"
                        + "（SearchActivity:185/:489-:500/:612 都只 requestFocus keyword），"
                        + "容器不合并就永远拿不到 state_focused，聚焦态分支等于死代码",
                xml.contains("android:addStatesFromChildren=\"true\""));
        assertFalse("#keywordField 不允许自己可聚焦：容器可聚焦只会在 D-pad 导航里多出一个按下无动作的停靠点",
                xml.contains("android:focusable=\"true\"") && fieldHostIsFocusable(xml));
    }

    /** 只检查 {@code #keywordField} 这一段声明是否仍写着 {@code focusable=true}。 */
    private static boolean fieldHostIsFocusable(String xml) {
        int start = xml.indexOf("android:id=\"@+id/keywordField\"");
        if (start < 0) return false;
        int end = xml.indexOf('>', start);
        if (end < 0) end = xml.length();
        return xml.substring(start, end).contains("android:focusable=\"true\"");
    }

    /**
     * 输入框底色、焦点环的唯一取值来源。
     *
     * <p>底色从 {@code @color/black_40} 改成 {@code ?attr/webhtvColorItemOverlay}：那就是
     * 「条目遮罩」这一个用户槽位的语义载体（列表/按钮压在表面上的那层半透明深色），默认取值
     * 仍是 {@code #66000000}，因此未改过主题时外观与键盘同族不变。
     *
     * <p><b>契约没有变松</b>：本条现在改为断言「默认解析结果仍是 black_40」——即
     * {@code webhtv_color_item_overlay} 必须等于 {@code @color/black_40}、且主题必须把它赋给
     * {@code webhtvColorItemOverlay}。这才是原本要守的不变量（能在浅色壁纸上压住恒定浅色前景），
     * 而不是某个字面量写法。运行时把用户槽位灌进这两个 drawable 的机制由
     * {@code ThemeBinderItemOverlayTest} 覆盖。
     */
    @Test
    public void searchFieldKeepsTheSharedRingAndBackdropContract() throws Exception {
        String normal = values(read(SEARCH_FIELD));
        String focused = values(read(SEARCH_FIELD_FOCUSED));

        for (String body : new String[]{normal, focused}) {
            assertTrue("输入框底色必须走条目遮罩槽位 ?attr/webhtvColorItemOverlay",
                    body.contains("android:color=\"" + ITEM_OVERLAY + "\""));
        }
        assertTrue("条目遮罩 token 的默认值必须仍是 black_40（否则默认外观已经变了）",
                values(read(TOKENS_DAY)).contains("<color name=\"webhtv_color_item_overlay\">" + BLACK_40 + "</color>"));
        assertTrue("主题必须把 webhtvColorItemOverlay 赋给该 token（只声明、没赋值会让 ?attr/ 静默退化）",
                values(read(STYLES)).contains("<item name=\"webhtvColorItemOverlay\">@color/webhtv_color_item_overlay</item>"));
        assertTrue("常态必须有可见的 1dp 调色板轮廓（否则浅色下仍然没有框）",
                normal.contains("android:width=\"1dp\"") && normal.contains("android:color=\"?attr/colorOutline\""));
        assertTrue("聚焦态环宽必须引用唯一宽度 token " + WIDTH,
                focused.contains("android:width=\"" + WIDTH + "\""));
        assertTrue("聚焦态环色必须引用唯一环色 token " + RING_COLOR,
                focused.contains("android:color=\"" + RING_COLOR + "\""));
    }

    /** 圆角必须足够容纳 24sp 文字行高，并与键盘按键族保持同一族外形。 */
    @Test
    public void searchFieldUsesTheSharedCornerFamily() throws Exception {
        String normal = values(read(SEARCH_FIELD));
        assertTrue("输入框圆角取 28dp（与 shape_danmaku_search_keyword 的胶囊外形同族）",
                normal.contains("android:radius=\"28dp\""));
        String layout = values(read(SEARCH_LAYOUT));
        assertTrue("宿主必须有内边距，否则 24sp 文字会贴到轮廓上",
                layout.contains("android:paddingStart=\"16dp\"") && layout.contains("android:paddingEnd=\"16dp\""));
    }

    private static String values(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ").replaceAll("\\s+", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
