package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 槽位选择逻辑的纯 JVM 测试。真正把 View 加上屏幕需要 Android 运行时，
 * 由 {@code PluginUiPresenterDeviceTest} 覆盖；这里只锁住"哪些槽位会被渲染"这条判定。
 */
public class PluginUiPresenterTest {

    private static PluginHost.PluginSlot slot(String pluginId, String type, String action) {
        return new PluginHost.PluginSlot(pluginId, new PluginManifest.UiSlot(type, "标签", action, null, null));
    }

    @Test
    public void countsFloatingToolbarAndPanelButNotPage() {
        // panel 有自己的入口控件（与 floating 同形），因此计入可渲染数量；
        // page 是整页接管，不由本渲染器挂控件，仍排除。
        List<PluginHost.PluginSlot> slots = List.of(
                slot("p", "floating", "a"),
                slot("p", "toolbar", "b"),
                slot("p", "panel", "c"),
                slot("p", "page", "d"),
                slot("q", "floating", "e"));

        assertEquals(4, PluginUiPresenter.countRenderable(slots));
    }

    @Test
    public void nullAndEmptyAreSafe() {
        assertEquals(0, PluginUiPresenter.countRenderable(null));
        assertEquals(0, PluginUiPresenter.countRenderable(new ArrayList<>()));
    }

    @Test
    public void nullEntriesInsideListAreSkipped() {
        List<PluginHost.PluginSlot> slots = new ArrayList<>();
        slots.add(null);
        slots.add(slot("p", "floating", "a"));
        assertEquals(1, PluginUiPresenter.countRenderable(slots));
    }

    @Test
    public void tagCarriesPluginIdSoClicksCanBeRouted() {
        // pluginId 必须在 tag 里：点击时宿主只有 action 字符串，
        // 而 PluginHost.action(pluginId, action, payload) 需要 pluginId 才能定位运行时。
        assertEquals("webhtv-plugin-host:douban.comments:open",
                PluginUiPresenter.tagOf(slot("douban.comments", "floating", "open")));
    }

    @Test
    public void detachOnFreshPresenterIsSafe() {
        PluginUiPresenter presenter = new PluginUiPresenter();
        presenter.detach();
        presenter.detach();
        assertEquals(0, presenter.attachedCount());
        assertTrue(presenter.lastFallbacks().isEmpty());
    }

    @Test
    public void clickBeforeAttachIsHarmless() {
        PluginUiPresenter presenter = new PluginUiPresenter();
        presenter.setOnSlotAction((pluginId, action) -> {
            throw new AssertionError("must not be invoked without a rendered view");
        });
        assertFalse(presenter.attachedCount() > 0);
    }
}
