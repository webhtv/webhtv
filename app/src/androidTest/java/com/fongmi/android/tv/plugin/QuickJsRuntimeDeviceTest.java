package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * QuickJS 适配器的**真机链路测试**。
 *
 * <p>存在理由：P2c 交付时该适配器只通过了编译与 JVM 单测，但 \`QuickJSLoader.init()\`
 * 加载 .so、\`QuickJSContext.create()\` 建上下文、真实脚本求值这条链路**从未真跑过**。
 * JVM 单测无法覆盖它（依赖原生库与 Android 运行时），所以必须有仪器化测试兜底。
 *
 * <p>本测试直插真实链路，不 mock：装载 → 事件派发 → 动作调用 → 故障隔离 → 卸载。
 */
@RunWith(AndroidJUnit4.class)
public class QuickJsRuntimeDeviceTest {

    private PluginEventBus bus;
    private PluginAuditLog audit;
    private PluginRuntimeFactory factory;
    private PluginHost host;

    @Before
    public void setUp() {
        bus = PluginEventBus.get();
        bus.clear();
        audit = PluginAuditLog.get();
        audit.clear();
        factory = new PluginRuntimeFactory().register("js", new QuickJsPluginRuntime());
        host = new PluginHost(factory, bus, audit);
    }

    @After
    public void tearDown() {
        host.unloadAll();
        bus.clear();
        audit.clear();
    }

    private static String manifest(String id, String entry) {
        // 用 String.format 而非 text block + formatted()：String.formatted 是 API 30+ 的
        // 方法，而本项目 minSdk 更低、模拟器是 SDK 28，直呼会 NoSuchMethodError。
        return String.format(java.util.Locale.US, """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "%s",
                  "name": "%s",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "%s" },
                  "events": ["ui.contentShown"],
                  "permissions": ["plugin.events"]
                }
                """, id, id, entry);
    }

    @Test
    public void loadsAndDispatchesFromAssets() {
        PluginManifest m = PluginManifest.parse(manifest("example.hello", "assets://plugin/example/hello.js"));
        assertNotNull("example manifest must parse", m);

        host.load(java.util.List.of(m), null);

        assertTrue("plugin must load from assets:// on a real device", host.isLoaded("example.hello"));
        assertTrue("no audit failures expected", audit.entriesFor("example.hello").isEmpty());

        // 真实事件派发：走 QuickJS 求值，脚本返回 'ok'
        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "TmdbDetailActivity");

        assertTrue("plugin must not be quarantined after a normal event", host.failedPlugins().isEmpty());
    }

    @Test
    public void actionRoundTripsThroughQuickJs() {
        PluginManifest m = PluginManifest.parse(manifest("example.hello", "assets://plugin/example/hello.js"));
        host.load(java.util.List.of(m), null);

        String result = host.action("example.hello", "hello.show", "{\"x\":1}");

        assertEquals("script return value must round-trip", "ok", result);
    }

    @Test
    public void missingHandlerReturnsNullNotError() {
        PluginManifest m = PluginManifest.parse(manifest("example.hello", "assets://plugin/example/hello.js"));
        host.load(java.util.List.of(m), null);

        // 脚本没有注册这个动作：应返回 null，且不隔离
        assertNull(host.action("example.hello", "never.registered", "{}"));
        assertTrue(host.failedPlugins().isEmpty());
    }

    @Test
    public void brokenScriptIsQuarantinedNotCrashed() {
        // 故意写一个语法错误的脚本，验证"坏脚本不崩应用"
        PluginManifest m = PluginManifest.parse(manifest("broken", "file:///android_asset/plugin/broken.js"));
        assertNotNull(m);

        host.load(java.util.List.of(m), null);

        // 参考实现：资产里没有 broken.js，init 失败 → 不装载 + 审计
        org.junit.Assert.assertFalse("broken plugin must not load", host.isLoaded("broken"));
        assertTrue("failure must be audited", !audit.entriesFor("broken").isEmpty());
    }

    @Test
    public void directRuntimeApiWorks() throws Exception {
        // 绕开 Host，直接验证运行时三方法 —— 缩小故障定位范围
        QuickJsPluginRuntime runtime = new QuickJsPluginRuntime();
        try {
            String init = runtime.init("direct", "assets://plugin/example/hello.js", "{}");
            assertEquals("ok", init);
            assertEquals(1, runtime.activeSessions());

            String event = runtime.onEvent("direct", "ui.contentShown", "{\"activity\":\"HomeActivity\"}");
            assertEquals("ok", event);

            String action = runtime.action("direct", "hello.show", "{}");
            assertEquals("ok", action);
        } finally {
            runtime.destroyAll();
        }
        assertEquals("all contexts must be released", 0, runtime.activeSessions());
    }

    @Test
    public void unknownPluginIdIsRejected() {
        QuickJsPluginRuntime runtime = new QuickJsPluginRuntime();
        try {
            runtime.onEvent("nobody", "e", "{}");
            fail("dispatching to an uninitialized plugin must throw");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("not initialized"));
        } finally {
            runtime.destroyAll();
        }
    }

    @Test
    public void destroyIsIdempotent() {
        QuickJsPluginRuntime runtime = new QuickJsPluginRuntime();
        runtime.destroy("nobody");
        runtime.destroy("nobody");
        runtime.destroy(null);
        assertEquals(0, runtime.activeSessions());
    }
}
