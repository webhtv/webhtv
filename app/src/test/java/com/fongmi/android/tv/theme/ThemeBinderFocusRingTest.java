package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视焦点环「跟随主题 FOCUS 槽」的源码契约。
 *
 * <p>用户报告：电视版选中颜色不管怎么调都是原始颜色。根因是渲染层从未参与主题覆写：
 * 全部焦点环都写在 {@code <selector>} 的 {@code state_focused} 子项里，而
 * {@code ThemeBinder} 过去只改 {@code GradientDrawable} 的实心填充，既不进入
 * {@code StateListDrawable} 子项，也从不处理 {@code android:foreground}（卡片族的环正是
 * foreground）。因此 {@code ThemeEditor.Slot.FOCUS} → {@code ThemeTokens.colorFocus()}
 * 在编辑器预览与 Web 快照里都对，真实界面却纹丝不动。
 *
 * <p>本测试把修法固化成四条：走公开 API（禁反射）、覆盖 selector 子项、覆盖 foreground、
 * 以及**只改写显式白名单内的应用表面焦点环**。
 *
 * <p>最后一条是本轮评审修正的结果：最初版本按「视图主题解析出的 {@code tvFocusRing} 是否等于
 * {@code tv_item_focus_ring}」判定，但 {@code webhtv_styles.xml} 把该属性在所有相关主题上都绑到
 * 同一个资源，判据于是恒真，会把刻意使用 {@code ?attr/tvPlayerRing} 的固定深色玻璃族
 * （弹幕/音轨/字幕/播控条）也改成调色板 FOCUS 深蓝——浅色表在深靛玻璃上只有 1.83:1，
 * 等于把这些焦点环改到看不见；同时会把 3dp 未收敛的卡片族强制改成 1.5dp，重新露出卡片
 * 圆角外的月牙。因此改为按资源身份白名单收窄，白名单外一律不碰。
 */
public class ThemeBinderFocusRingTest {

    private static final String BINDER = "app/src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java";

    @Test
    public void binderWalksSelectorItemsAndForegroundDrawables() throws Exception {
        String source = values(read(BINDER));
        assertTrue("binder 必须进入 StateListDrawable 子项，否则所有 <selector> 焦点环永远不会跟随主题",
                source.contains("StateListDrawable"));
        assertTrue("binder 必须提供 bindFocusRingStates 处理聚焦态子项",
                source.contains("bindFocusRingStates"));
        assertTrue("binder 必须改写 android:foreground，卡片族的焦点环画在 foreground 上",
                source.contains("getForeground()") && source.contains("bindForeground"));
        assertTrue("聚焦态判定必须只匹配 state_focused 子项",
                source.contains("android.R.attr.state_focused"));
    }

    @Test
    public void binderUsesOnlyPublicDrawableApis() throws Exception {
        String source = values(read(BINDER));
        assertFalse("不允许用反射读 GradientDrawable 的 stroke（仓库契约禁止反射/隐藏 API）",
                source.contains("setAccessible"));
        assertFalse("不允许用反射取私有字段",
                source.contains("getDeclaredField"));
        assertTrue("改写走公开的 setStroke 与 mutate",
                source.contains("setStroke(") && source.contains("mutate()"));
    }

    @Test
    public void ringRewritingIsScopedToAnExplicitWhitelist() throws Exception {
        String source = values(read(BINDER));
        assertTrue("焦点环改写必须有显式白名单，否则用户改主题时会误伤 tvPlayerRing 玻璃族与 3dp 未收敛族",
                source.contains("APP_SURFACE_FOCUS_RINGS"));
        assertTrue("白名单必须按资源名字解析（getIdentifier）：这些 selector 只在 leanback 源集，"
                        + "main 源集硬引用 R.drawable.* 会让 mobile 变体 javac 失败",
                source.contains("getIdentifier("));
        assertTrue("环宽必须引用唯一宽度 token，不得写死 1.5dp/2dp/3dp",
                source.contains("R.dimen.webhtv_focus_ring_width"));
        assertTrue("显式 webhtv:focus tag 必须能无条件指定 FOCUS 角色",
                source.contains("ThemeRole.FOCUS"));
        assertFalse("不允许再按「视图主题解析出的 tvFocusRing」作判据：webhtv_styles.xml 把该属性绑到 "
                        + "tv_item_focus_ring 后该判据恒真，会把 tvPlayerRing 玻璃族一并改成 colorFocus"
                        + "（浅色表 #0B57D0 在深靛玻璃上 1.83:1，等于不可见）",
                source.contains("focusRingFollowsTheme"));
        assertTrue("容器 StateListDrawable 必须先 mutate 再改子项，避免共享 ConstantState 连带改写其它宿主",
                source.contains("states.mutate()"));
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
