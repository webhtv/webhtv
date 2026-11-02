package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    // ---- 页面过滤 ----
    // 拖动与落点记忆依赖真实触摸事件与 SharedPreferences，纯 JVM 无处安放，
    // 只有真机（androidTest）能验；这里只锁住"该页该不该出现这个入口"这条纯函数判定。

    @Test
    public void emptySurfaceSetShowsOnEveryPage() {
        // 历史清单没有 surfaces 字段。若把"未声明"当成"哪都不显示"，
        // 升级一次宿主就会让所有既有插件集体消失，所以缺省必须是全页显示。
        assertTrue(PluginSurface.matches(Set.of(), PluginSurface.HOME));
        assertTrue(PluginSurface.matches(Set.of(), PluginSurface.DETAIL));
        assertTrue(PluginSurface.matches(null, PluginSurface.PLAYBACK));
    }

    @Test
    public void declaredSurfaceMatchesCurrentPage() {
        assertTrue(PluginSurface.matches(Set.of(PluginSurface.DETAIL), PluginSurface.DETAIL));
        assertTrue(PluginSurface.matches(Set.of(PluginSurface.DETAIL, PluginSurface.PLAYBACK), PluginSurface.PLAYBACK));
    }

    @Test
    public void undeclaredCurrentPageIsFilteredOut() {
        // 首页不该出现只在详情页生效的入口：那里没有剧集上下文，点开只能看到"宿主没有传片名"。
        assertFalse(PluginSurface.matches(Set.of(PluginSurface.DETAIL, PluginSurface.PLAYBACK), PluginSurface.HOME));
        assertFalse(PluginSurface.matches(Set.of(PluginSurface.OTHER), PluginSurface.HOME));
    }

    @Test
    public void surfaceMatchingIgnoresCase() {
        // 清单是手写 JSON，作者写成 "Detail" 不该静默失效。
        assertTrue(PluginSurface.matches(Set.of("Detail"), PluginSurface.DETAIL));
        assertTrue(PluginSurface.matches(Set.of(PluginSurface.PLAYBACK), "PLAYBACK"));
    }

    @Test
    public void unknownDeclaredSurfacesAreIgnoredInsteadOfThrowing() {
        // 插件可能声明宿主还不认识的页面名；不认识就当不匹配，不该让渲染路径炸掉。
        assertFalse(PluginSurface.matches(Set.of("settings", "cast"), PluginSurface.HOME));
        assertTrue(PluginSurface.matches(Set.of("settings", PluginSurface.HOME), PluginSurface.HOME));
    }

    @Test
    public void nullActivityIsOtherSurface() {
        // Detail 必须由名字先判出来（TmdbDetailActivity 也是 PlaybackActivity 的子类），
        // 这条只验证判不出东西时不抛、统一落到 other。
        assertEquals(PluginSurface.OTHER, PluginSurface.of(null));
    }
}
