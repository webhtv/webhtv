package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.view.ContextThemeWrapper;

import com.fongmi.android.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * 行为级守卫：搜索输入框的焦点环必须在**真实交互**里点亮。
 *
 * <p>此前的缺陷是声明式就能看出来的那种：{@code #keywordField} 挂着的
 * {@code selector_tv_search_field} 有一个 {@code state_focused} 分支，但
 * {@code SearchActivity} 的所有焦点落点都在子控件 {@code #keyword} 上（:185、
 * :489-:500、:612 都是 {@code mBinding.keyword.requestFocus()}），容器自己从来拿不到焦点，
 * 也没有 {@code addStatesFromChildren}，于是那个分支永不点亮——浅色模式下搜索框依旧是
 * 「没有框」的原症状。同时容器 {@code focusable=true} 还多出一个按下无动作的 D-pad 停靠点。
 *
 * <p>本测试因此不读源码字符串，而是把**真实布局**充气出来、把焦点交给子控件，
 * 再检查容器的 drawable state 与栅格化后的背景：容器必须合并子控件状态并画出焦点环。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = com.fongmi.android.tv.App.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SearchFieldFocusStateTest {

    private static final int SIZE = 96;

    @Test
    public void focusedInputLightsUpTheFieldHostRing() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        activity.setTheme(R.style.Theme_App);

        View root = LayoutInflater.from(activity).inflate(R.layout.activity_search, null);
        assertNotNull("搜索页布局必须能充气出来", root);
        View field = root.findViewById(R.id.keywordField);
        View keyword = root.findViewById(R.id.keyword);
        assertNotNull("搜索输入框的实体宿主不存在", field);
        assertNotNull("搜索输入框本身不存在", keyword);

        assertTrue("#keywordField 必须合并子控件的 drawable 状态（ViewGroup.addStatesFromChildren），" +
                        "否则 selector_tv_search_field 的 state_focused 分支永远不会点亮（浅色下依旧看不见框）",
                field instanceof ViewGroup group && group.addStatesFromChildren());
        assertFalse("#keywordField 不允许自己可聚焦：焦点全部落在子控件 #keyword 上，" +
                        "容器可聚焦只会多出一个按下无动作的 D-pad 停靠点",
                field.isFocusable());

        int ringColor = ringColor(activity);
        assertTrue("测试前提：主题必须能解析出 ?attr/tvFocusRing", ringColor != 0);

        // 未挂到窗口之前任何视图都不可能持有焦点，此时才是可靠的「常态」基准。
        Bitmap idle = rasterize(field.getBackground());
        assertFalse("测试前提：未聚焦时不允许已经出现焦点环色 " + Integer.toHexString(ringColor),
                containsColor(idle, ringColor));
        assertFalse("测试前提：未聚焦时 drawable 状态不允许含 state_focused",
                hasFocusedState(field.getDrawableState()));

        activity.setContentView(root, new ViewGroup.LayoutParams(1280, 720));
        controller.visible();

        assertTrue("焦点必须能交给输入控件本身", keyword.requestFocus());
        assertTrue("测试前提：输入控件必须真正持有焦点", keyword.hasFocus());

        assertTrue("子控件获得焦点后，#keywordField 的 drawable 状态必须包含 state_focused",
                hasFocusedState(field.getDrawableState()));
        Bitmap focused = rasterize(field.getBackground());
        assertTrue("子控件获得焦点后，搜索框必须真的画出焦点环色 " + Integer.toHexString(ringColor)
                        + "（这正是浅色模式下「框不见了」的修复目标）",
                containsColor(focused, ringColor));
    }

    private static Context themedContext(Context base) {
        return new ContextThemeWrapper(base, R.style.Theme_App);
    }

    /** 视图主题解析出的 {@code ?attr/tvFocusRing}（与 {@code ThemeBinder} 判定同源）。 */
    private static int ringColor(Context base) {
        Context context = themedContext(base);
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(R.attr.tvFocusRing, value, true)) return 0;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return value.resourceId == 0 ? 0 : context.getResources().getColor(value.resourceId, null);
    }

    private static boolean hasFocusedState(int[] state) {
        if (state == null) return false;
        for (int item : state) {
            if (item == android.R.attr.state_focused) return true;
        }
        return false;
    }

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
}
