package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 装配层测试。
 *
 * <p>关键断言是**"没有插件来源时完全静默"**：P2d 没有订阅解析（P6 才做），
 * 因此进程启动绝不能有任何自动装载行为，否则用户会看到来路不明的控件。
 */
public class PluginControllerTest {

    private PluginController controller;
    private PluginEventBus bus;
    private PluginAuditLog audit;

    private interface ActionHook {
        String call(String pluginId, String action, String payload);
    }

    private static final class FakeRuntime implements PluginRuntime {
        volatile boolean failInit;
        volatile ActionHook hook;
        int initCount;
        int destroyCount;

        @Override
        public String init(String pluginId, String entry, String config) {
            initCount++;
            if (failInit) throw new IllegalStateException("init boom");
            return "ok";
        }

        @Override
        public String onEvent(String pluginId, String event, String payload) {
            return "ok";
        }

        @Override
        public String action(String pluginId, String action, String payload) {
            ActionHook current = hook;
            return current == null ? "ok" : current.call(pluginId, action, payload);
        }

        @Override
        public void destroy(String pluginId) {
            destroyCount++;
        }
    }

    private FakeRuntime runtime;

    @Before
    public void setUp() {
        PluginController.reset();
        bus = PluginEventBus.get();
        bus.clear();
        audit = PluginAuditLog.get();
        audit.clear();
        controller = new PluginController();
        runtime = new FakeRuntime();
        controller.registerRuntime("js", runtime);
    }

    @After
    public void tearDown() {
        PluginController.reset();
        bus.clear();
        audit.clear();
    }

    private PluginManifest manifest(String id) {
        return PluginManifest.parse(String.format(java.util.Locale.US, """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "%s",
                  "name": "%s",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "assets://plugin/example/hello.js" },
                  "events": ["ui.contentShown"],
                  "permissions": ["plugin.events"]
                }
                """, id, id));
    }

    @Test
    public void emptyReloadMeansNoPluginsAndNoSubscription() {
        List<PluginHost.LoadedPlugin> loaded = controller.reload(Collections.emptyList(), null);

        assertTrue(loaded.isEmpty());
        assertEquals(0, controller.host().size());
        assertFalse("no plugins must mean no bus subscription", bus.hasSubscribers(PluginEventBus.EVENT_UI_CONTENT_SHOWN));
    }

    @Test
    public void reloadWithNullDoesNotThrow() {
        assertTrue(controller.reload(null, null).isEmpty());
    }

    @Test
    public void reloadLoadsPlugin() {
        PluginManifest m = manifest("a");
        assertNotNull(m);

        List<PluginHost.LoadedPlugin> loaded = controller.reload(List.of(m), null);

        assertEquals(1, loaded.size());
        assertTrue(controller.host().isLoaded("a"));
        assertEquals(1, runtime.initCount);
    }

    @Test
    public void failedInitIsAuditedAndNotLoaded() {
        runtime.failInit = true;
        PluginManifest m = manifest("a");

        assertTrue(controller.reload(List.of(m), null).isEmpty());
        assertFalse(controller.host().isLoaded("a"));
        assertEquals(1, audit.entriesFor("a").size());
    }

    @Test
    public void reloadReplacesPreviousSet() {
        controller.reload(List.of(manifest("a")), null);
        controller.reload(List.of(manifest("b")), null);

        assertFalse(controller.host().isLoaded("a"));
        assertTrue(controller.host().isLoaded("b"));
        assertEquals(1, runtime.destroyCount);
    }

    @Test
    public void resumedWithoutActivityIsSafe() {
        controller.onActivityResumed(null);
        assertEquals(0, controller.presenter().attachedCount());
    }

    @Test
    public void diagnosticsReportsRuntimesAndPlugins() {
        controller.reload(List.of(manifest("a")), null);
        String text = controller.diagnostics();

        assertTrue(text.contains("runtimes="));
        assertTrue(text.contains("plugins="));
        assertTrue("diagnostics must reflect loaded plugin", text.contains("a=ready"));
    }

    /**
     * 点击链路的核心断言：槽位点击必须真正抵达运行时，且**不能在调用线程上执行**。
     *
     * <p>后者是硬要求：插件动作走 JS 运行时，是带 5 秒超时的阻塞调用
     * （{@code QuickJsPluginRuntime.CALL_TIMEOUT_MS}）。若跑在主线程，最长冻住界面 5 秒。
     */
    @Test
    public void slotActionReachesRuntimeOffTheCallerThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicReference<String> gotAction = new AtomicReference<>();
        AtomicReference<String> gotPayload = new AtomicReference<>();

        runtime.hook = (pluginId, action, payload) -> {
            threadName.set(Thread.currentThread().getName());
            gotAction.set(action);
            gotPayload.set(payload);
            latch.countDown();
            return "ok";
        };
        controller.reload(List.of(manifest("a")), null);

        controller.dispatchSlotAction("a", "comments.show");

        assertTrue("runtime must be reached", latch.await(3, TimeUnit.SECONDS));
        assertEquals("comments.show", gotAction.get());
        assertEquals("plugin-action", threadName.get());
        assertTrue("payload must carry the host activity", gotPayload.get().contains("activity"));
    }

    /** 点击未知插件不能抛给 UI 线程，且必须留审计痕迹。 */
    @Test
    public void slotActionForUnknownPluginIsAuditedNotThrown() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        runtime.hook = (pluginId, action, payload) -> {
            latch.countDown();
            return null;
        };
        controller.reload(List.of(manifest("a")), null);
        int before = audit.size();

        controller.dispatchSlotAction("does.not.exist", "whatever");

        // 未装载的插件不会到达运行时；不抛异常是唯一硬要求。
        assertFalse("unknown plugin must not reach the runtime", latch.await(300, TimeUnit.MILLISECONDS));
        assertTrue("a failed dispatch must leave an audit trail", audit.size() > before);
    }

    @Test
    public void resetUnloadsEverything() {
        PluginController.get().reload(List.of(manifest("a")), null);

        PluginController.reset();

        // reset 后新单例应是空状态
        assertEquals(0, PluginController.get().host().size());
    }
}
