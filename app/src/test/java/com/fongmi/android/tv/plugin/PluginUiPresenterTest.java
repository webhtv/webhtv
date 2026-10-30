package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 槽位计数的纯逻辑测试。真正把 View 加上屏幕的逻辑需要 Android 运行时，
 * 由仪器化测试覆盖；这里只锁住"哪些槽位会被渲染"这条判定。
 */
public class PluginUiPresenterTest {

    private static PluginManifest.UiSlot slot(String type, String action) {
        return new PluginManifest.UiSlot(type, "标签", action, null, null);
    }

    @Test
    public void countsOnlyFloatingSlots() {
        List<PluginManifest.UiSlot> slots = List.of(
                slot("floating", "a"),
                slot("toolbar", "b"),
                slot("panel", "c"),
                slot("floating", "d"));

        assertEquals(2, PluginUiPresenter.countFloating(slots));
    }

    @Test
    public void nullAndEmptyAreSafe() {
        assertEquals(0, PluginUiPresenter.countFloating(null));
        assertEquals(0, PluginUiPresenter.countFloating(new ArrayList<>()));
    }

    @Test
    public void tagIsStableAndActionScoped() {
        assertEquals("webhtv-plugin-host:open", PluginUiPresenter.tagOf(slot("floating", "open")));
    }

    @Test
    public void detachOnFreshPresenterIsSafe() {
        PluginUiPresenter presenter = new PluginUiPresenter();
        presenter.detach();
        presenter.detach();
        assertEquals(0, presenter.attachedCount());
    }
}
