package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    // ---- 全屏范围 ----

    /**
     * 全屏是**排他**的：进入沉浸态后生效范围里只剩 fullscreen。
     *
     * <p>这条是全屏隐藏能成立的前提。若全屏时集合里仍含 playback，所有声明 playback 的
     * 插件都会照旧显示——也就是用户报的"全屏后豆瓣 chip 还在"。
     */
    @Test
    public void immersiveScopeReplacesPageScope() {
        assertEquals(Set.of(PluginSurface.FULLSCREEN), PluginSurface.scopesOf(PluginSurface.PLAYBACK, true));
        assertEquals(Set.of(PluginSurface.FULLSCREEN), PluginSurface.scopesOf(PluginSurface.DETAIL, true));
        // 非沉浸态保持原页面，不能顺手把其他页面也改掉。
        assertEquals(Set.of(PluginSurface.PLAYBACK), PluginSurface.scopesOf(PluginSurface.PLAYBACK, false));
        assertEquals(Set.of(PluginSurface.DETAIL), PluginSurface.scopesOf(PluginSurface.DETAIL, false));
    }

    /** 只声明 detail/playback 的插件（豆瓣短评就是这种）在全屏下必须落选。 */
    @Test
    public void playbackScopedEntranceIsHiddenInFullscreen() {
        Set<String> active = PluginSurface.scopesOf(PluginSurface.PLAYBACK, true);
        assertFalse(PluginSurface.matchesAny(Set.of(PluginSurface.DETAIL, PluginSurface.PLAYBACK), active));
        assertFalse(PluginSurface.matchesAny(Set.of(PluginSurface.PLAYBACK), active));
    }

    /** 显式声明 fullscreen 的插件在全屏下要能显示，否则这个能力等于不存在。 */
    @Test
    public void fullscreenScopedEntranceIsShownInFullscreen() {
        Set<String> active = PluginSurface.scopesOf(PluginSurface.PLAYBACK, true);
        assertTrue(PluginSurface.matchesAny(Set.of(PluginSurface.FULLSCREEN), active));
        assertTrue(PluginSurface.matchesAny(Set.of(PluginSurface.PLAYBACK, PluginSurface.FULLSCREEN), active));
        // 只在全屏用的入口，退回内嵌小窗后应当消失。
        assertFalse(PluginSurface.matchesAny(Set.of(PluginSurface.FULLSCREEN),
                PluginSurface.scopesOf(PluginSurface.PLAYBACK, false)));
    }

    /** 空集仍是"全页显示"：老清单不会因为这次改动在全屏下凭空消失。 */
    @Test
    public void undeclaredSurfacesStayVisibleInFullscreen() {
        Set<String> active = PluginSurface.scopesOf(PluginSurface.PLAYBACK, true);
        assertTrue(PluginSurface.matchesAny(Set.of(), active));
        assertTrue(PluginSurface.matchesAny(null, active));
    }

    // ---- 电视端范围（详情与播放同在一页） ----

    /**
     * 电视端 playback 必须同时算 detail。
     *
     * <p>电视端没有独立详情页：详情与播放同在一个 {@code VideoActivity} 里，类名不含 Detail，
     * {@code PluginSurface.of} 只能把它判成 playback。若这里仍按单页算，清单写
     * {@code surfaces:["detail"]} 的插件在电视上永远没有入口——刚加的电视插件行正好收不到它们。
     */
    @Test
    public void leanbackPlaybackScopeAlsoCoversDetail() {
        assertEquals(Set.of(PluginSurface.DETAIL, PluginSurface.PLAYBACK),
                PluginSurface.scopesOf(PluginSurface.PLAYBACK, false, true));
        // 手机端保持单页语义：只在详情页生效的入口不该跑到播放页上。
        assertEquals(Set.of(PluginSurface.PLAYBACK), PluginSurface.scopesOf(PluginSurface.PLAYBACK, false, false));
        // 非播放页不受端影响。
        assertEquals(Set.of(PluginSurface.HOME), PluginSurface.scopesOf(PluginSurface.HOME, false, true));
        // 全屏仍然排他：两参与三参都只返回 fullscreen，否则声明 playback 的入口会在全屏下照旧显示。
        assertEquals(Set.of(PluginSurface.FULLSCREEN), PluginSurface.scopesOf(PluginSurface.PLAYBACK, true, true));
        assertEquals(Set.of(PluginSurface.FULLSCREEN), PluginSurface.scopesOf(PluginSurface.PLAYBACK, true));
        assertEquals(Set.of(PluginSurface.FULLSCREEN), PluginSurface.scopesOf(PluginSurface.DETAIL, true));
    }

    /** 声明 detail 的入口：电视上要能出现，手机播放页上仍要消失。 */
    @Test
    public void detailOnlyEntranceIsVisibleOnTvVideoPageOnly() {
        assertTrue(PluginSurface.matchesAny(Set.of(PluginSurface.DETAIL),
                PluginSurface.scopesOf(PluginSurface.PLAYBACK, false, true)));
        assertFalse(PluginSurface.matchesAny(Set.of(PluginSurface.DETAIL),
                PluginSurface.scopesOf(PluginSurface.PLAYBACK, false, false)));
    }

    /**
     * 接线断言：{@code attach} 必须把 leanback 一起交给纯函数。
     *
     * <p>回退成两参 {@code scopesOf(page, immersive)} 或更早的
     * {@code matches(surfaces, of(activity))} 时这条会红——那时电视端的 {@code detail} 声明
     * 也就同时消失了。纯 JVM 拿不到 Activity/View，只能读源码钉住这条接线。
     */
    @Test
    public void attachPassesLeanbackIntoScopesOf() throws Exception {
        String body = methodBody(source("src", "main", "java", "com", "fongmi", "android", "tv", "plugin",
                "PluginUiPresenter.java"), "public void attach(");

        assertTrue("attach 必须走集合判定 scopesOf", body.contains("PluginSurface.scopesOf("));
        assertFalse("单页面判定 matches(...) 会让电视端丢掉 detail 声明", body.contains("PluginSurface.matches("));
        int call = body.indexOf("PluginSurface.scopesOf(");
        assertTrue("scopesOf 必须收到 attach 的 leanback 形参",
                body.substring(call, body.indexOf(';', call)).contains("leanback"));
    }

    // ---- 连点抑制（PluginController） ----
    // 本来该落在 PluginControllerTest：本轮只允许改这 4 个文件，而这条行为又是防连点的**唯一**
    // 可 JVM 验证点，所以借用本文件。装置很小：一份 panel 槽位清单 + 一个会阻塞的 fake runtime。

    @Test
    public void repeatedPanelClickRunsThePluginOnce() throws Exception {
        PluginController.reset();
        PluginController controller = new PluginController();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch again = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        controller.registerRuntime("js", new PluginRuntime() {
            @Override
            public String init(String pluginId, String entry, String config) {
                return "ok";
            }

            @Override
            public String onEvent(String pluginId, String event, String payload) {
                return "ok";
            }

            @Override
            public String action(String pluginId, String action, String payload) {
                if (runs.incrementAndGet() > 1) again.countDown();
                entered.countDown();
                try {
                    release.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                return "ok";
            }

            @Override
            public void destroy(String pluginId) {
            }
        });
        controller.reload(List.of(panelManifest()), null);

        controller.dispatchSlotAction("a", "comments.show");
        assertTrue("动作必须先抵达运行时", entered.await(3, TimeUnit.SECONDS));
        // 第一次还在跑时再点一次：必须直接返回，既不重跑插件也不开第二个壳。
        controller.dispatchSlotAction("a", "comments.show");
        release.countDown();

        assertFalse("进行中的同一 panel 动作不得再派发", again.await(1, TimeUnit.SECONDS));
        assertEquals(1, runs.get());
        // 动作结束后 key 必须归还，否则这颗入口永久失效。
        controller.dispatchSlotAction("a", "comments.show");
        assertTrue("动作结束后必须能再次派发", again.await(3, TimeUnit.SECONDS));
        assertEquals(2, runs.get());
        PluginController.reset();
    }

    private static PluginManifest panelManifest() {
        return PluginManifest.parse("""
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "a",
                  "name": "a",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "assets://plugin/example/hello.js" },
                  "permissions": ["plugin.events", "ui.panel"],
                  "ui": [ { "slot": "panel", "label": "评论", "action": "comments.show" } ]
                }
                """);
    }

    // ---- 结果落点（panel 的结果必须有去处） ----
    // 同样本该落在 PluginControllerTest：本轮只允许改 PluginController 与这个文件。行为断言做不到——
    // fillPanel 把工作投给 mainHandler()，而 JVM 下 Looper 是桩、mainHandler() 返回 null，整段分支
    // 根本跑不到；所以这里改用源码断言把两条接线钉死。

    /**
     * 开不出壳（token 为 null）时，回来的 html 必须开成一个有内容的面板，不能静默丢弃。
     *
     * <p>回退点：删掉 {@code PluginController} 里
     * {@code if (token == null) { PluginPanelDialog.show(...) }} 这个退化分支（或把它改成
     * {@code return}）本用例立刻变红——那正是"用户点了 chip 屏幕上毫无反应"。
     */
    @Test
    public void panelResultWithoutAShellStillOpensAContentPanel() throws Exception {
        String body = methodBody(controllerSource(), "private void fillPanel(");

        int fallback = body.indexOf("token == null");
        assertTrue("token 为 null 时必须有退化分支", fallback >= 0);
        // 空 html 仍然只收尾、不弹空壳：这条必须排在退化分支之前。
        assertTrue("空 html 不能借退化分支弹出空面板", body.indexOf("html == null || html.isEmpty()") < fallback);
        int show = body.indexOf("PluginPanelDialog.show(fragment, title, html");
        assertTrue("退化分支必须真的开面板，而不是静默丢弃", show > fallback);
        assertTrue("有壳时仍然走 fill，不能被退化分支顶掉",
                show < body.indexOf("PluginPanelDialog.fill(fragment, token, html)"));
    }

    /**
     * 结果侧不得再回查槽位：panel 判定与标题必须随 token 从派发侧传下来。
     *
     * <p>回退点：把 {@code routeSlotResult} 改回内部 {@code host.slotOfAction(...)}（第二个断言红），
     * 或把 {@code onSlotAction} 的调用改回 {@code routeSlotResult(activity, pluginId, action, ...)}
     * （最后两个断言红）。那种写法在插件被卸载/清单变化时会把 panel 结果掉进预览分支，
     * 已开的壳永不收尾。
     */
    @Test
    public void resultRoutingDoesNotReLookUpTheSlot() throws Exception {
        String controller = controllerSource();
        String route = methodBody(controller, "private void routeSlotResult(");
        assertFalse("结果侧不得回查槽位", route.contains("slotOfAction("));
        assertTrue("panel 的判定要真的用上传下来的参数", route.contains("if (panel)"));

        int signature = controller.indexOf("private void routeSlotResult(");
        assertTrue("找不到 routeSlotResult 签名", signature >= 0);
        String params = controller.substring(signature, controller.indexOf('{', signature));
        assertTrue("panel 判定必须由派发侧传下来: " + params, params.contains("boolean panel"));
        assertTrue("标题必须随 token 一起传下来: " + params, params.contains("String label"));

        String onSlot = methodBody(controller, "private void onSlotAction(");
        int call = onSlot.indexOf("routeSlotResult(");
        assertTrue("派发侧必须真的把判定结果传下去", call >= 0);
        String args = onSlot.substring(call, onSlot.indexOf(';', call));
        assertTrue("缺少 panel 实参: " + args, args.contains("panel"));
        assertTrue("缺少 label 实参: " + args, args.contains("label"));
    }

    private static String controllerSource() throws Exception {
        return source("src", "main", "java", "com", "fongmi", "android", "tv", "plugin", "PluginController.java");
    }

    /** 读源码：工作目录可能是 app/ 也可能是仓库根，两种都要能读到。 */
    private static String source(String... parts) throws Exception {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(Path.of("", parts)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("Missing method body: " + signature, open >= 0);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }
}
