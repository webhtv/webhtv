package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 运行时工厂测试。核心是：**未实现的引擎必须明确失败，而不是静默变成空实现**。
 *
 * <p>这条看似琐碎，但它决定了故障归因：如果未实现引擎返回一个什么都不做的运行时，
 * 用户会看到"插件装了但没反应"，无法区分是插件写错了还是宿主没实现。
 */
public class PluginRuntimeFactoryTest {

    private static PluginRuntime noop() {
        return new PluginRuntime() {
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
                return "ok";
            }

            @Override
            public void destroy(String pluginId) {
            }
        };
    }

    @Test
    public void registeredApiResolvesToItsRuntime() {
        PluginRuntime runtime = noop();
        PluginRuntimeFactory factory = new PluginRuntimeFactory().register("js", runtime);

        assertTrue(factory.isSupported("js"));
        assertEquals(runtime, factory.create("js"));
        assertEquals(runtime, factory.create("JS"));
        assertEquals(runtime, factory.create(" js "));
    }

    @Test
    public void unregisteredApiReturnsFailingPlaceholderNotNull() {
        PluginRuntimeFactory factory = new PluginRuntimeFactory();

        PluginRuntime created = factory.create("js");
        assertNotNull("factory must never return null", created);
        assertFalse(factory.isSupported("js"));
        // 必须抛错而不是静默返回空值
        assertThrows(UnsupportedOperationException.class, () -> created.init("p", "p.js", "{}"));
        assertThrows(UnsupportedOperationException.class, () -> created.onEvent("p", "e", "{}"));
        assertThrows(UnsupportedOperationException.class, () -> created.action("p", "a", "{}"));
        // destroy 必须安全
        created.destroy("p");
    }

    @Test
    public void unsupportedRuntimeMessageDistinguishesUnknownFromUnimplemented() {
        PluginRuntimeFactory factory = new PluginRuntimeFactory();

        UnsupportedOperationException known = assertThrows(UnsupportedOperationException.class,
                () -> factory.create("py").init("p", "p.py", "{}"));
        assertTrue("known engine says not implemented: " + known.getMessage(),
                known.getMessage().contains("not implemented"));

        UnsupportedOperationException unknown = assertThrows(UnsupportedOperationException.class,
                () -> factory.create("wasm").init("p", "p.wasm", "{}"));
        assertTrue("unknown engine says unknown: " + unknown.getMessage(),
                unknown.getMessage().contains("unknown runtime"));
    }

    @Test
    public void nullAndBlankApisAreHandled() {
        PluginRuntimeFactory factory = new PluginRuntimeFactory();
        assertFalse(factory.isSupported(null));
        assertFalse(factory.isSupported(""));
        assertFalse(PluginRuntimeFactory.isKnownApi(null));
        assertNotNull(factory.create(null));
    }

    @Test
    public void registerIgnoresInvalidInput() {
        PluginRuntimeFactory factory = new PluginRuntimeFactory();
        factory.register(null, noop());
        factory.register("", noop());
        factory.register("js", null);

        assertTrue(factory.registeredApis().isEmpty());
    }

    @Test
    public void describeReportsReadinessPerEngine() {
        PluginRuntimeFactory factory = new PluginRuntimeFactory().register("js", noop());
        var described = factory.describe();

        assertEquals("ready", described.get("js"));
        assertEquals("not implemented", described.get("py"));
        assertEquals("not implemented", described.get("html"));
    }

    @Test
    public void registeredApisSnapshotIsImmutableCopy() {
        PluginRuntimeFactory factory = new PluginRuntimeFactory().register("js", noop());
        var apis = factory.registeredApis();
        assertEquals(1, apis.size());
        factory.clear();
        assertEquals("snapshot must not reflect later mutation", 1, apis.size());
        assertTrue(factory.registeredApis().isEmpty());
    }
}
