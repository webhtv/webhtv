package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Set;

/**
 * 电视详情页「插件」行的真实渲染守卫。
 *
 * <p><b>为什么需要它。</b>这条链路此前只有源码断言：那能证明代码写了什么，证明不了
 * 布局里真有那个容器、也证明不了 chip 最终挂在了它下面。用户报"详情页看不到插件区域"
 * 时，源码断言全绿而现象依旧——缺口正在这里。
 *
 * <p>本测试把详情页布局真装一遍，再走 presenter 的挂载路径，断言三件事：
 * 容器存在、标题行被点亮、chip 真的进了 {@code pluginRow}。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class PluginRowRenderTest {

    /**
     * 假详情页。
     *
     * <p>类名**必须含 {@code Detail}**：{@link PluginSurface#of} 就是按类名判页面归属的，
     * 换成别的名字会被判成 {@code other}，于是 {@code surfaces=["detail"]} 的槽位永远落选——
     * 那是测试自己造出来的假阴性，不是产品缺陷。
     */
    public static class FakeDetailActivity extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle state) {
            setTheme(R.style.Theme_App);
            super.onCreate(state);
        }
    }

    private static PluginHost.PluginSlot panelSlot(Set<String> surfaces) {
        PluginManifest.UiSlot slot = new PluginManifest.UiSlot(
                "panel", "豆瓣", "douban.comments", null, null, surfaces);
        return new PluginHost.PluginSlot("douban.comments", slot);
    }

    @Test
    public void detailLayoutHostsThePluginRowContainers() {
        try (var host = Robolectric.buildActivity(FakeDetailActivity.class).setup()) {
            host.get().setContentView(R.layout.activity_tmdb_detail);

            assertNotNull("详情页布局必须有 pluginRow 容器（chip 的落点）",
                    host.get().findViewById(R.id.pluginRow));
            assertNotNull("详情页布局必须有 pluginHeader（『插件』标题行）",
                    host.get().findViewById(R.id.pluginHeader));
            assertNotNull("详情页布局必须有 pluginScroll（横滚容器）",
                    host.get().findViewById(R.id.pluginScroll));

            assertEquals("没有插件时标题行必须不占位",
                    View.GONE, host.get().findViewById(R.id.pluginHeader).getVisibility());
        }
    }

    @Test
    public void panelSlotLandsInThePluginRow() {
        try (var host = Robolectric.buildActivity(FakeDetailActivity.class).setup()) {
            Activity activity = host.get();
            activity.setContentView(R.layout.activity_tmdb_detail);

            PluginUiPresenter presenter = new PluginUiPresenter();
            presenter.attach(activity, List.of(panelSlot(Set.of("detail", "playback"))), true);

            ViewGroup row = activity.findViewById(R.id.pluginRow);
            assertEquals("电视详情页的 panel 槽位必须落进插件行", 1, row.getChildCount());
            assertEquals("chip 的文案应来自清单 label", "豆瓣",
                    ((android.widget.TextView) row.getChildAt(0)).getText().toString());
            assertEquals("有插件时标题行必须现身",
                    View.VISIBLE, activity.findViewById(R.id.pluginHeader).getVisibility());
            assertEquals("有插件时横滚容器必须现身",
                    View.VISIBLE, activity.findViewById(R.id.pluginScroll).getVisibility());

            presenter.detach();
            assertEquals("卸载后 chip 必须被摘掉", 0, row.getChildCount());
            assertEquals("卸载后标题行必须收回 GONE，否则详情页会留一行空标题",
                    View.GONE, activity.findViewById(R.id.pluginHeader).getVisibility());
        }
    }

    /**
     * 电视端详情页布局（activity_video.xml）的插件行必须同样可用。
     *
     * <p><b>这条守护的是一个真实事故。</b>电视详情页用的不是 activity_tmdb_detail.xml，而是
     * activity_video.xml；当年插件行只加进了前者，电视上永远看不到入口。而电视布局里那一行是
     * {@code androidx.appcompat.widget.LinearLayoutCompat}——它不是 {@code LinearLayout} 的子类，
     * 按 LinearLayout 判类型会让 findViewById 的结果被静默丢掉。这条测试同时钉住这两件事。
     */
    @Test
    public void leanbackDetailLayoutAlsoHostsThePluginRow() {
        try (var host = Robolectric.buildActivity(LeanbackVideoHost.class).setup()) {
            Activity activity = host.get();
            activity.setContentView(R.layout.activity_video);

            PluginUiPresenter presenter = new PluginUiPresenter();
            // 空 surfaces 表示"全页显示"（向后兼容语义），这样这条测试只考察
            // "布局里那一行收不收得到 chip"，不被页面归属判定牵扯。
            presenter.attach(activity, List.of(panelSlot(Set.of())), true);

            ViewGroup row = activity.findViewById(R.id.pluginRow);
            assertTrue("电视详情页布局里的插件行必须收得到 chip——它通常是 LinearLayoutCompat，"
                            + "按 LinearLayout 判类型会把这一行静默丢掉",
                    row.getChildCount() > 0);
            assertEquals("电视详情页的『插件』标题行必须现身",
                    View.VISIBLE, activity.findViewById(R.id.pluginHeader).getVisibility());
        }
    }

    /**
     * 沉浸融合播放器的控制条（playerActionRow）必须收到插件 chip。
     *
     * <p><b>为什么单列一条。</b>融合播放器的控制条不是 {@code R.id.control} 那一套——它是
     * {@code playerControls → playerBottom → playerActionRow} 里的按钮排。不专门认它，融合模式下
     * 详情行被 fusion 收起、控制条又不被识别，chip 就无处可去（用户实测反馈过）。
     *
     * <p><b>为什么还断言"插在最前"。</b>这一排按钮在横滚容器里，本身就比屏幕宽——实测末尾按钮
     * 落在 x≈2900，而屏幕只有 1920 宽。追加到末尾等于挂在一个默认看不见的位置。
     */
    @Test
    public void fusionPlayerControlBarHostsThePluginChipAtTheFront() {
        try (var host = Robolectric.buildActivity(FakeDetailActivity.class).setup()) {
            Activity activity = host.get();
            activity.setContentView(R.layout.activity_tmdb_detail);

            ViewGroup actionRow = activity.findViewById(R.id.playerActionRow);
            assertNotNull("前置条件：融合布局必须有 playerActionRow", actionRow);
            ViewGroup buttons = (ViewGroup) actionRow.getChildAt(0);
            int before = buttons.getChildCount();

            PluginUiPresenter presenter = new PluginUiPresenter();
            presenter.attach(activity, List.of(panelSlot(Set.of())), true);

            assertEquals("chip 必须挂进融合控制条那一排", before + 1, buttons.getChildCount());
            View first = buttons.getChildAt(0);
            assertTrue("chip 必须插在最前面：这一排比屏幕宽，追加到末尾等于看不见",
                    first instanceof android.widget.TextView && first.getTag() != null);

            presenter.detach();
            assertEquals("卸载后必须摘干净", before, buttons.getChildCount());
        }
    }

    /** 电视详情页载体。名字不影响断言——本测试走的是空 surfaces，与页面归属无关。 */
    public static class LeanbackVideoHost extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle state) {
            setTheme(R.style.Theme_App);
            super.onCreate(state);
        }
    }
}
