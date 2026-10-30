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

    private static final class FakeRuntime implements PluginRuntime {
        volatile boolean failInit;
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
            return "ok";
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

    @Test
    public void resetUnloadsEverything() {
        PluginController.get().reload(List.of(manifest("a")), null);

        PluginController.reset();

        // reset 后新单例应是空状态
        assertEquals(0, PluginController.get().host().size());
    }
}
