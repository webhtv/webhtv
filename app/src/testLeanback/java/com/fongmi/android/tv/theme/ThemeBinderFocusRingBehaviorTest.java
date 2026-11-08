package com.fongmi.android.tv.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.view.ContextThemeWrapper;

import com.fongmi.android.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 行为级守卫：焦点环改写**只**发生在应用表面白名单内，且双宿主资源按视图主题分流。
 *
 * <p>为什么需要它：{@code ThemeBinderFocusRingTest} 只做源码字符串断言，实现是否真的只碰白名单、
 * 白名单外是否真的不动，靠读代码看不出来。本轮评审就是这样漏掉了两个真实回归：
 * <ol>
 *   <li>把 4 个 focus 态为 3dp 的卡片环强制改成 1.5dp，而它们的声明圆角是按「宿主圆角 − 环宽/2」
 *       以 3dp 推导的，改宽度会重新露出卡片圆角外的月牙；</li>
 *   <li>把刻意使用 {@code ?attr/tvPlayerRing} 的固定深色玻璃族（弹幕/音轨/字幕/播控条）改成
 *       调色板 FOCUS 深蓝 {@code #0B57D0}，浅色表在深靛玻璃上只有 1.83:1，焦点环等于看不见。</li>
 * </ol>
 *
 * <p>本测试因此直接比较「bind 前 / bind 后」的栅格化位图：
 * <ul>
 *   <li>白名单内（{@link #WHITELISTED} 全部 9 项）：焦点态环色必须变成活动主题的 FOCUS 槽；</li>
 *   <li>白名单外（玻璃族、3dp 卡片族、本来无描边的图标按钮、成对语义族）：位图必须**逐像素完全相同**；</li>
 *   <li>双宿主资源 {@code selector_video_item}：同一份 drawable 在应用表面宿主被改写，在
 *       {@code ThemeOverlay.WebHTV.GlassFocusRings} 子树里保持视频层环色，且结论与 bind 顺序无关。</li>
 * </ul>
 *
 * <p>注意白名单资源只存在于 leanback 源集，因此本测试类放在 {@code src/testLeanback}
 * （只在电视变体编译，可以硬引用 {@code R.style.ThemeOverlay_WebHTV_GlassFocusRings}）；
 * 资源仍按名字查找，是为了在没有该资源的配置下跳过而不是把整类变成空断言。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = com.fongmi.android.tv.App.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemeBinderFocusRingBehaviorTest {

    private static final int SIZE = 96;

    /** 白名单内全部项（必须与 {@code ThemeBinder.APP_SURFACE_FOCUS_RINGS} 逐项一致）。 */
    private static final String[] WHITELISTED = {
            "selector_item",
            "selector_item_round",
            "selector_vod",
            "selector_vod_oval",
            "selector_keyboard",
            "selector_search_hot_word",
            "selector_video_item",
            "selector_tv_search_field",
            "selector_empty_action",
    };

    /**
     * 环画在 {@code android:foreground} 的资源。电视卡片/OU 控件把焦点环画在内容之上，
     * 只走 {@code getBackground()} 的实现永远碰不到它们——这正是 {@code bindForeground}
     * 存在的理由，也是本条必须覆盖 foreground 路径的原因。
     */
    private static final Set<String> FOREGROUND_HOSTED = new HashSet<>(Arrays.asList(
            "selector_vod",
            "selector_vod_oval",
            "selector_tmdb_media_focus",
            "selector_tmdb_cast_focus",
            "shape_episode_photo_focused"));

    /**
     * 双宿主资源：普通页面按应用表面语义用，{@code dialog_episode_list} /
     * {@code dialog_quick_search} 两处固定深色玻璃对话框的根节点用
     * {@code @style/ThemeOverlay.WebHTV.GlassFocusRings} 把 {@code tvFocusRing} 重绑到视频层环色。
     */
    private static final String DUAL_HOSTED = "selector_video_item";

    /**
     * 白名单外，必须逐像素不变。
     * glass 族 = 刻意使用 ?attr/tvPlayerRing（浅色表夹取到 #447BF5）；card3dp = 焦点态 3dp 未收敛；
     * strokeless = 焦点态本就没有描边。
     */
    private static final String[] EXCLUDED = {
            // 固定深色玻璃上、刻意用 ?attr/tvPlayerRing 的族（浅色表夹取到 #447BF5）。
            "selector_glass_icon_action",
            "selector_control_sheet_button",
            "shape_subtitle_focused",
            "shape_subtitle_pressed",
            // 固定深色播放器弹窗芯片：焦点环用 ?attr/tvPlayerRing，且把「焦点+选中」合并态
            // 特意换成黑色环（白环压琥珀底不可见），改写会破坏这个成对设计。
            "selector_display_option",
            // 焦点态 3dp、圆角按 3dp 推导的未收敛卡片族。
            "selector_episode_card",
            "selector_tmdb_media_focus",
            "selector_tmdb_cast_focus",
            "shape_episode_photo_focused",
            // 焦点态本就没有描边的控件（改了就凭空多出一圈环）。
            "about_primary_icon_button",
            "selector_ad_rule",
            "selector_dialog_step_button",
            // 「colorPrimary 填充 + colorOnPrimary 环」的成对语义：环色必须跟随填充的 on-色，
            // 改写成 colorFocus() 会让环与填充同色（默认色板 focus == primary）而消失。
            "selector_config_history_item",
            "selector_site_item",
            "selector_group_button",
            // 与 shape_search_hot_word_focused 的填充/圆角/内边距完全相同（black_40 + 8dp + 无内边距），
            // 只有描边不同（2dp @color/accent vs token ?attr/tvFocusRing）。描边无公开 getter，
            // 因此靠位图指纹区分——本项就是防止「签名相同即改写」的碰撞回归。
            "selector_lab_icon_btn",
    };

    @Test
    public void whitelistedAppSurfaceRingsFollowTheThemeFocusSlot() {
        Context context = themedContext();
        // 用真实的 ThemeTokens.light() 作基线、dark() 作活动值：两者 FOCUS 槽必然不同
        // （浅色 #0B57D0 / 深色 #F5F7FF），从而 binder 不会因为 baseline.equals(active) 而整体 no-op。
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = ThemeTokens.dark();
        int focusSlot = active.colorFocus();
        assertNotEquals("测试前提：活动主题的 FOCUS 槽必须与基线不同，否则 binder 整体 no-op",
                baseline.colorFocus(), focusSlot);

        int checked = 0;
        for (String name : WHITELISTED) {
            int resId = drawableId(context, name);
            if (resId == 0) continue; // 该配置下没有此资源：见类注释。
            checked++;

            FrameLayout root = new FrameLayout(context);
            View view = new View(context);
            attach(view, context, name, resId);
            root.addView(view);

            Bitmap before = rasterize(focusedState(slot(view)));
            ThemeBinder.bind(root, baseline, active);
            Bitmap after = rasterize(focusedState(slot(view)));

            assertNotEquals(name + " 的焦点环必须在 bind 后被改写（否则用户改焦点色对它无效）",
                    describe(before), describe(after));
            assertTrue(name + " 改写后的焦点环必须出现活动主题的 FOCUS 槽颜色 "
                            + Integer.toHexString(focusSlot),
                    containsColor(after, focusSlot));
            assertFalse(name + " 改写前不应已含 FOCUS 槽颜色，否则本条断言无意义",
                    containsColor(before, focusSlot));
        }
        assertEquals("白名单在本源集必须全部可解析（leanback 源集才有这些资源）",
                WHITELISTED.length, checked);
    }

    @Test
    public void excludedRingsArePiecewiseUntouched() {
        Context context = themedContext();
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = ThemeTokens.dark();
        int focusSlot = active.colorFocus();
        int checked = 0;
        for (String name : EXCLUDED) {
            int resId = drawableId(context, name);
            if (resId == 0) continue; // 该配置下没有此资源
            checked++;

            FrameLayout root = new FrameLayout(context);
            View view = new View(context);
            attach(view, context, name, resId);
            root.addView(view);

            Bitmap before = rasterize(focusedState(slot(view)));
            ThemeBinder.bind(root, baseline, active);
            Bitmap after = rasterize(focusedState(slot(view)));

            assertEquals("白名单外的焦点环必须完全不被改写（玻璃族/3dp 卡片族/无描边族/成对语义族）：" + name,
                    describe(before), describe(after));
            assertFalse(name + " 属于白名单外，不允许出现 FOCUS 槽颜色 "
                            + Integer.toHexString(focusSlot),
                    containsColor(after, focusSlot));
        }
        assertTrue("白名单外的排除集在本源集内必须至少命中一个资源，否则本条是空断言", checked > 0);
        assertEquals("排除集必须全部可解析，否则断言被静默跳过", EXCLUDED.length, checked);
    }

    /**
     * 同一个 {@code selector_video_item} 被两类宿主共用：普通页面（应用表面语义）与固定深色玻璃
     * 对话框子树（{@code GlassFocusRings} 重绑到视频层环色）。只看内容签名的实现会把玻璃子树
     * 也改写成 FOCUS 深蓝（浅色表在深靛玻璃上 1.83:1），因此这里对两个宿主**同时**断言，
     * 并且跑两遍相反顺序，防止「按 Resources 缓存」让结论与首次调用顺序绑定。
     */
    @Test
    public void dualHostedSelectorKeepsTheGlassSubtreeAtTheVideoLayerRing() {
        verifyDualHost(true);
        verifyDualHost(false);
    }

    private void verifyDualHost(boolean appSurfaceFirst) {
        Context appContext = themedContext();
        Context glassContext = new ContextThemeWrapper(appContext, R.style.ThemeOverlay_WebHTV_GlassFocusRings);
        int appRing = ringColor(appContext);
        int glassRing = ringColor(glassContext);
        assertTrue("测试前提：应用表面主题必须能解析出焦点环色", appRing != 0);
        assertNotEquals("测试前提：玻璃子树必须把 tvFocusRing 重绑到视频层环色，否则本条无意义",
                appRing, glassRing);

        int resId = drawableId(appContext, DUAL_HOSTED);
        if (resId == 0) return;

        FrameLayout root = new FrameLayout(appContext);
        View appSurface = new View(appContext);
        appSurface.setBackground(appContext.getDrawable(resId));
        View glass = new View(glassContext);
        glass.setBackground(glassContext.getDrawable(resId));
        if (appSurfaceFirst) {
            root.addView(appSurface);
            root.addView(glass);
        } else {
            root.addView(glass);
            root.addView(appSurface);
        }

        Bitmap appBefore = rasterize(focusedState(appSurface.getBackground()));
        Bitmap glassBefore = rasterize(focusedState(glass.getBackground()));

        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = ThemeTokens.dark();
        int focusSlot = active.colorFocus();
        ThemeBinder.bind(root, baseline, active);

        Bitmap appAfter = rasterize(focusedState(appSurface.getBackground()));
        Bitmap glassAfter = rasterize(focusedState(glass.getBackground()));

        assertNotEquals("应用表面宿主上的 " + DUAL_HOSTED + " 必须跟随主题 FOCUS 槽（appSurfaceFirst="
                        + appSurfaceFirst + "）",
                describe(appBefore), describe(appAfter));
        assertTrue("应用表面宿主必须出现 FOCUS 槽颜色 " + Integer.toHexString(focusSlot),
                containsColor(appAfter, focusSlot));

        assertEquals("玻璃子树里的同一 selector 必须保持视频层环色：改写会让深蓝环贴在固定深靛玻璃上"
                        + "（实测 1.83:1）而不可见（appSurfaceFirst=" + appSurfaceFirst + "）",
                describe(glassBefore), describe(glassAfter));
        assertFalse("玻璃子树不允许出现 FOCUS 槽颜色 " + Integer.toHexString(focusSlot)
                        + "（appSurfaceFirst=" + appSurfaceFirst + "）",
                containsColor(glassAfter, focusSlot));
    }

    @Test
    public void whitelistIsNotEmptyOnTheTvSourceSet() {
        Context context = themedContext();
        int resolved = 0;
        for (String name : WHITELISTED) {
            if (drawableId(context, name) != 0) resolved++;
        }
        assertEquals("leanback 源集上白名单资源必须全部可解析（否则 binder 静默不改写任何焦点环）",
                WHITELISTED.length, resolved);
    }

    // ------------------------------------------------------------------ helpers

    private static Context themedContext() {
        RuntimeEnvironment.setQualifiers("");
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_App);
    }

    /** 按名字查找：白名单资源只在 leanback 源集，main 源集不能硬引用 R.drawable.*。 */
    private static int drawableId(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    /** 把这层 drawable 挂到宿主真正使用的插槽上（foreground 还是 background）。 */
    private static void attach(View view, Context context, String name, int resId) {
        Drawable drawable = context.getDrawable(resId);
        if (FOREGROUND_HOSTED.contains(name)) view.setForeground(drawable);
        else view.setBackground(drawable);
    }

    private static Drawable slot(View view) {
        Drawable foreground = view.getForeground();
        return foreground != null ? foreground : view.getBackground();
    }

    /** 视图主题解析出的 {@code ?attr/tvFocusRing}（与 {@code ThemeBinder} 的判定同源）。 */
    private static int ringColor(Context context) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(R.attr.tvFocusRing, value, true)) return 0;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return value.resourceId == 0 ? 0 : context.getResources().getColor(value.resourceId, null);
    }

    /**
     * 取焦点态要栅格化的那层 drawable：
     * selector 取 state_focused 子项；非 selector（如 layer-list 的图标按钮）原样返回。
     */
    private static Drawable focusedState(Drawable drawable) {
        if (!(drawable instanceof StateListDrawable states)) return drawable;
        for (int i = 0; i < states.getStateCount(); i++) {
            for (int state : states.getStateSet(i)) {
                if (state == android.R.attr.state_focused) return states.getStateDrawable(i);
            }
        }
        return states.getStateCount() > 0 ? states.getStateDrawable(0) : null;
    }

    /** 位图里是否出现该颜色（忽略 alpha 差异，按 RGB 比较）。 */
    private static boolean containsColor(Bitmap bitmap, int color) {
        int rgb = color & 0x00FFFFFF;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if ((bitmap.getPixel(x, y) & 0x00FFFFFF) == rgb) return true;
            }
        }
        return false;
    }

    private static Bitmap rasterize(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        if (drawable != null) {
            drawable.setBounds(0, 0, SIZE, SIZE);
            drawable.draw(new Canvas(bitmap));
        }
        return bitmap;
    }

    /** 逐像素指纹：稀疏采样会漏掉 1-2px 的描边差异，所以每像素都算，只把结果压成哈希串。 */
    private static String describe(Bitmap bitmap) {
        long hash = 1125899906842597L;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                hash = 31 * hash + bitmap.getPixel(x, y);
            }
        }
        return Long.toHexString(hash) + '-' + bitmap.getWidth() + 'x' + bitmap.getHeight();
    }
}
