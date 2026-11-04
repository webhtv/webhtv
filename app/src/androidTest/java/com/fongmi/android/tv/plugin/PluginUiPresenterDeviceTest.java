package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.ui.activity.AppBrandingActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 槽位渲染的真机测试：把插件声明的控件真正加到 Activity 上，验证可查、可移除、可点击路由。
 *
 * <p>存在理由：{@code attach/detach} 依赖 {@code Window.decorView}、{@code Resources.density}
 * 与 {@code ThemeController.focusRingColor}，都只有 Android 运行时能给；JVM 单测只能锁住计数逻辑。
 *
 * <p>宿主复用已注册的 {@link AppBrandingActivity}：新增 Manifest 条目会扩大测试对生产配置的
 * 侵入面，而渲染逻辑对宿主类型并无要求。
 *
 * <p><b>为什么不用 FollowingActivity。</b>它缺少必需 Intent 参数时会在 onCreate 里直接
 * {@code finish()}（{@code FollowingActivity.java:114}），Activity 随即销毁，
 * {@code onActivity} 抛 "Activity has been destroyed already"。
 */
@RunWith(AndroidJUnit4.class)
public class PluginUiPresenterDeviceTest {

    private static final String PROBE_PLUGIN_ID = "probe.plugin";

    private ActivityScenario<AppBrandingActivity> scenario;
    private PluginUiPresenter presenter;

    @Before
    public void setUp() {
        scenario = ActivityScenario.launch(AppBrandingActivity.class);
        presenter = new PluginUiPresenter();
    }

    /**
     * 摘控件必须回到主线程。
     *
     * <p>{@code detach} 走的是 {@code ViewGroup.removeView}，错误线程调用时 {@code ViewRootImpl}
     * 的 {@code checkThread} 会抛 {@code CalledFromWrongThreadException}；而此前这一行能过只是
     * 因为摘除发生在 {@code ViewRootImpl} 已挂上窗口的时机之外，是竞态而非安全。本类每个用例都
     * 会 attach 一个真实控件，不能靠时机赌。
     */
    @After
    public void tearDown() {
        if (presenter != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> presenter.detach());
        }
        if (scenario != null) scenario.close();
    }

    private static PluginHost.PluginSlot slot(String type, String action) {
        return new PluginHost.PluginSlot(PROBE_PLUGIN_ID,
                new PluginManifest.UiSlot(type, "探测", action, null, null));
    }

    @Test
    public void floatingSlotIsReallyAttachedAndDetached() {
        scenario.onActivity(activity -> {
            presenter.attach(activity, List.of(slot("floating", "probe.action")), false);

            assertEquals("floating slot must produce exactly one view", 1, presenter.attachedCount());
            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
            View found = findTagged(root);
            assertNotNull("attached view must be present in the decor view", found);
            assertTrue(found instanceof TextView);
            assertEquals("探测", ((TextView) found).getText().toString());
            assertTrue("phone floating control must be clickable", found.isClickable());
            assertTrue("phone floating control must be focusable", found.isFocusable());

            presenter.detach();
            assertEquals(0, presenter.attachedCount());
            assertNull("view must be removed from the tree", findTagged(root));
        });
    }

    @Test
    public void leanbackFloatingIsNotFocusable() {
        scenario.onActivity(activity -> {
            presenter.attach(activity, List.of(slot("floating", "probe.action")), true);

            View found = findTagged((ViewGroup) activity.getWindow().getDecorView());
            assertNotNull(found);
            assertTrue("leanback floating must stay out of the focus chain", !found.isFocusable());
            assertTrue("but still tappable", found.isClickable());
        });
    }

    /**
     * AppBrandingActivity 没有播放控制条，因此 {@code toolbar} 槽位必须降级为 floating，
     * 而不是被静默丢弃——作者声明了入口就该看到入口，且降级必须可查。
     */
    @Test
    public void toolbarWithoutControlBarFallsBackToFloating() {
        scenario.onActivity(activity -> {
            presenter.attach(activity, List.of(slot("toolbar", "probe.action")), false);

            assertEquals("toolbar must still render something", 1, presenter.attachedCount());
            assertEquals("fallback must be recorded", 1, presenter.lastFallbacks().size());
            assertEquals("probe.plugin", presenter.lastFallbacks().get(0).pluginId());
            assertNotNull(findTagged((ViewGroup) activity.getWindow().getDecorView()));
        });
    }

    /** 点击必须把 pluginId 与 action 一起交回装配层，否则无法定位到插件运行时。 */
    @Test
    public void clickRoutesPluginIdAndActionBack() {
        scenario.onActivity(activity -> {
            AtomicReference<String> gotPlugin = new AtomicReference<>();
            AtomicReference<String> gotAction = new AtomicReference<>();
            presenter.setOnSlotAction((pluginId, action) -> {
                gotPlugin.set(pluginId);
                gotAction.set(action);
            });

            presenter.attach(activity, List.of(slot("floating", "comments.show")), false);
            View found = findTagged((ViewGroup) activity.getWindow().getDecorView());
            assertNotNull(found);

            assertTrue("performClick must be accepted", found.performClick());
            assertEquals("probe.plugin", gotPlugin.get());
            assertEquals("comments.show", gotAction.get());
        });
    }

    /** 未被点击时回调不能自己触发。 */
    @Test
    public void noClickMeansNoDispatch() {
        scenario.onActivity(activity -> {
            AtomicReference<String> called = new AtomicReference<>();
            presenter.setOnSlotAction((pluginId, action) -> called.set(pluginId));

            presenter.attach(activity, List.of(slot("floating", "probe.action")), false);
            assertNull(called.get());
        });
    }

    @Test
    public void attachWithNullActivityIsSafe() {
        presenter.attach(null, List.of(slot("floating", "probe.action")), false);
        assertEquals(0, presenter.attachedCount());
    }

    @Test
    public void attachWithEmptySlotsRemovesPreviousViews() {
        scenario.onActivity(activity -> {
            presenter.attach(activity, List.of(slot("floating", "probe.action")), false);
            assertEquals(1, presenter.attachedCount());

            presenter.attach(activity, List.of(), false);
            assertEquals(0, presenter.attachedCount());
            assertNull(findTagged((ViewGroup) activity.getWindow().getDecorView()));
        });
    }

    /**
     * 只找本用例自己挂的那一个控件。
     *
     * <p>不能按 {@code TAG_PREFIX} 前缀匹配：宿主 resume 时 {@code PluginController} 会用它
     * 自己的 {@link PluginUiPresenter} 把已装载插件的槽位也挂到同一个 decorView 上（用户自己
     * 导入的插件就是这样），前缀匹配会先命中别人的控件，让断言
     * {@code expected:<探测> but was:<别的标签>} 变成一条与本节逻辑无关的红。
     */
    private static View findTagged(ViewGroup root) {
        String prefix = PluginUiPresenter.TAG_PREFIX + PROBE_PLUGIN_ID + ":";
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String text && text.startsWith(prefix)) return child;
            if (child instanceof ViewGroup group) {
                View nested = findTagged(group);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
