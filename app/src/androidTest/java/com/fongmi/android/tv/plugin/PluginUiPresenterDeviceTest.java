package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fongmi.android.tv.ui.activity.AppBrandingActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * 槽位渲染的真机测试：把插件声明的 floating 控件真正加到 Activity 上，验证可查、可移除。
 *
 * <p>存在理由：\`attach/detach\` 依赖 \`Window.decorView\`、\`Resources.density\` 与
 * \`ThemeController.focusRingColor\`，都只有 Android 运行时能给；JVM 单测只能锁住计数逻辑。
 *
 * <p>宿主复用已注册的 {@link AppBrandingActivity}，而不是新声明一个测试专用 Activity：
 * 新增 Manifest 条目会扩大测试对生产配置的侵入面，而渲染逻辑对宿主类型并无要求。
 * 渲染只往 decorView 上挂控件，不会触碰宿主的业务视图。
 *
 * <p><b>为什么不用 FollowingActivity。</b>它缺少必需 Intent 参数时会在 onCreate 里直接
 * `finish()`（`FollowingActivity.java:114`），Activity 随即销毁，
 * `onActivity` 抛 "Activity has been destroyed already"。AppBrandingActivity 无入参依赖。
 */
@RunWith(AndroidJUnit4.class)
public class PluginUiPresenterDeviceTest {

    private ActivityScenario<AppBrandingActivity> scenario;
    private PluginUiPresenter presenter;

    @Before
    public void setUp() {
        scenario = ActivityScenario.launch(AppBrandingActivity.class);
        presenter = new PluginUiPresenter();
    }

    @After
    public void tearDown() {
        if (presenter != null) presenter.detach();
        if (scenario != null) scenario.close();
    }

    private static PluginManifest.UiSlot slot(String type, String text) {
        return new PluginManifest.UiSlot(type, text, "probe.action", null, null);
    }

    @Test
    public void floatingSlotIsReallyAttachedAndDetached() {
        scenario.onActivity(activity -> {
            presenter.attach(activity, List.of(slot("floating", "探测")), false);

            assertEquals("floating slot must produce exactly one view", 1, presenter.attachedCount());
            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
            View found = findTagged(root);
            assertNotNull("attached view must be present in the decor view", found);
            assertTrue(found instanceof TextView);
            assertEquals("探测", ((TextView) found).getText().toString());
            assertTrue("phone floating control must be clickable", found.isClickable());

            presenter.detach();
            assertEquals(0, presenter.attachedCount());
            assertTrue("view must be removed from the tree", findTagged(root) == null);
        });
    }

    @Test
    public void leanbackFloatingIsNotFocusable() {
        scenario.onActivity(activity -> {
            presenter.attach(activity, List.of(slot("floating", "探测")), true);

            View found = findTagged((ViewGroup) activity.getWindow().getDecorView());
            assertNotNull(found);
            assertTrue("leanback floating must stay out of the focus chain", !found.isFocusable());
            assertTrue("but still tappable", found.isClickable());
        });
    }

    @Test
    public void toolbarSlotIsNotRenderedAsFloating() {
        scenario.onActivity(activity -> {
            presenter.attach(activity, List.of(slot("toolbar", "工具栏")), false);

            assertEquals("toolbar must not be rendered by the floating presenter", 0, presenter.attachedCount());
            assertTrue(findTagged((ViewGroup) activity.getWindow().getDecorView()) == null);
        });
    }

    @Test
    public void attachWithNullActivityIsSafe() {
        presenter.attach(null, List.of(slot("floating", "x")), false);
        assertEquals(0, presenter.attachedCount());
    }

    private static View findTagged(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String text && text.startsWith("webhtv-plugin-host:")) return child;
            if (child instanceof ViewGroup group) {
                View nested = findTagged(group);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
