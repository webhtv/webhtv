package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 插件宿主的行为测试：权限门禁、故障隔离、零插件零开销。
 *
 * <p>用假运行时（{@link FakeRuntime}）而非 QuickJS——后者要加载原生库，普通 JVM 跑不了。
 * 这也正是 {@link PluginRuntime} 保持纯 JVM 的原因。
 */
public class PluginHostTest {

    private PluginEventBus bus;
    private PluginAuditLog audit;
    private PluginRuntimeFactory factory;
    private PluginHost host;
    private FakeRuntime runtime;

    @Before
    public void setUp() {
        bus = PluginEventBus.get();
        bus.clear();
        audit = PluginAuditLog.get();
        audit.clear();
        runtime = new FakeRuntime();
        factory = new PluginRuntimeFactory().register("js", runtime);
        host = new PluginHost(factory, bus, audit);
    }

    @After
    public void tearDown() {
        host.unloadAll();
        bus.clear();
        audit.clear();
    }

    private static String manifestJson(String id, String permissions, String events) {
        return """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "%s",
                  "name": "%s",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "p.js" },
                  "events": %s,
                  "permissions": %s
                }
                """.formatted(id, id, events, permissions);
    }

    private PluginManifest manifest(String id, String permissions, String events) {
        PluginManifest parsed = PluginManifest.parse(manifestJson(id, permissions, events));
        assertNotNull("manifest should parse: " + id, parsed);
        return parsed;
    }

    // ---------- 装载 ----------

    @Test
    public void loadsPluginAndRegistersWithBus() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        List<PluginHost.LoadedPlugin> loaded = host.load(List.of(m), null);

        assertEquals(1, loaded.size());
        assertTrue(host.isLoaded("a"));
        assertTrue(bus.hasSubscribers(PluginEventBus.EVENT_UI_CONTENT_SHOWN));
    }

    @Test
    public void failedInitIsNotLoadedAndIsAudited() {
        runtime.failInit = true;
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        List<PluginHost.LoadedPlugin> loaded = host.load(List.of(m), null);

        assertTrue(loaded.isEmpty());
        assertFalse(host.isLoaded("a"));
        assertEquals(1, audit.entriesFor("a").size());
        assertEquals("blocked", audit.entriesFor("a").get(0).outcome());
    }

    @Test
    public void unsupportedRuntimeFailsClosedAndIsAudited() {
        // 清单声明 py，但只注册了 js
        String json = manifestJson("p", "[\"plugin.events\"]", "[\"ui.contentShown\"]")
                .replace("\"api\": \"js\"", "\"api\": \"py\"");
        PluginManifest m = PluginManifest.parse(json);
        assertNotNull(m);

        List<PluginHost.LoadedPlugin> loaded = host.load(List.of(m), null);

        assertTrue("unsupported runtime must not load", loaded.isEmpty());
        assertEquals(1, audit.entriesFor("p").size());
        assertTrue(audit.entriesFor("p").get(0).path().contains("py"));
    }

    @Test
    public void noPluginsMeansNoBusSubscription() {
        host.load(List.of(), null);
        assertFalse(bus.hasSubscribers(PluginEventBus.EVENT_UI_CONTENT_SHOWN));
        assertEquals(0, host.size());
    }

    // ---------- 权限门禁（安全边界 1）----------

    @Test
    public void eventNotDeclaredIsNeverDelivered() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        host.load(List.of(m), null);
        runtime.events.clear();

        bus.emitActivity(PluginEventBus.EVENT_ACTIVITY_CREATED, "VideoActivity");

        assertTrue("undeclared event must not reach plugin", runtime.events.isEmpty());
    }

    @Test
    public void eventDeclaredButUnauthorizedIsNeverDelivered() {
        // 声明了 activity.created，但只给了 plugin.data.episode 权限（不覆盖该事件）
        PluginManifest m = manifest("a", "[\"plugin.data.episode\"]", "[\"activity.created\"]");
        host.load(List.of(m), null);
        runtime.events.clear();

        bus.emitActivity(PluginEventBus.EVENT_ACTIVITY_CREATED, "VideoActivity");

        assertTrue("event without covering permission must not reach plugin", runtime.events.isEmpty());
    }

    @Test
    public void declaredAndAuthorizedEventIsDelivered() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        host.load(List.of(m), null);
        runtime.events.clear();

        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "TmdbDetailActivity");

        assertEquals(1, runtime.events.size());
        assertTrue(runtime.events.get(0).contains("ui.contentShown"));
        assertTrue("payload must carry activity identity", runtime.events.get(0).contains("TmdbDetailActivity"));
    }

    @Test
    public void actionWithoutSlotPermissionIsBlockedAndAudited() {
        // 清单里没有 ui 槽位，因此 action 无法映射到槽位权限；这里构造有槽位但权限被剥的情况
        String json = """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "a",
                  "name": "a",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "p.js" },
                  "events": ["ui.contentShown"],
                  "ui": [ { "slot": "floating", "label": "X", "action": "open" } ],
                  "permissions": ["plugin.events", "ui.floating"]
                }
                """;
        PluginManifest m = PluginManifest.parse(json);
        assertNotNull(m);
        host.load(List.of(m), null);

        String result = host.action("a", "open", "{}");

        assertEquals("ok", result);
        assertEquals(1, runtime.actions.size());
    }

    // ---------- 故障隔离（安全边界 2）----------

    @Test
    public void throwingPluginIsQuarantinedNotPropagated() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        host.load(List.of(m), null);
        runtime.failEvent = true;

        // 关键断言：这次调用不能抛异常出来
        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "HomeActivity");

        assertTrue(host.failedPlugins().contains("a"));
        assertEquals(1, audit.entriesFor("a").size());
        assertEquals("error", audit.entriesFor("a").get(0).outcome());
    }

    @Test
    public void quarantinedPluginStopsReceivingEvents() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        host.load(List.of(m), null);
        runtime.failEvent = true;
        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "HomeActivity");
        runtime.events.clear();

        // 第二次事件：已被隔离，不应再尝试
        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "HomeActivity");

        assertTrue("quarantined plugin must not be retried", runtime.events.isEmpty());
            assertEquals(1, audit.entriesFor("a").size());
    }

    @Test
    public void oneBadPluginDoesNotAffectAnother() {
        PluginManifest bad = manifest("bad", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        PluginManifest good = manifest("good", "[\"plugin.events\"]", "[\"ui.contentShown\"]");

        // 让第一个插件的事件派发抛错，第二个正常
        runtime.failEventFor = Set.of("bad");
        host.load(List.of(bad, good), null);
        runtime.events.clear();

        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "HomeActivity");

        assertTrue(host.failedPlugins().contains("bad"));
        assertFalse(host.failedPlugins().contains("good"));
        assertEquals(1, runtime.events.size());
        assertTrue(runtime.events.get(0).contains("good"));
    }

    @Test
    public void actionOnQuarantinedPluginReturnsNull() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        host.load(List.of(m), null);
        runtime.failEvent = true;
        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "HomeActivity");

        assertNull(host.action("a", "anything", "{}"));
    }

    // ---------- 生命周期 ----------

    @Test
    public void unloadAllDestroysRuntimesAndDetaches() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        host.load(List.of(m), null);

        host.unloadAll();

        assertEquals(1, runtime.destroyed.size());
        assertEquals(0, host.size());
        assertFalse(bus.hasSubscribers(PluginEventBus.EVENT_UI_CONTENT_SHOWN));
    }

    @Test
    public void reloadReplacesPreviousPlugins() {
        host.load(List.of(manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]")), null);
        host.load(List.of(manifest("b", "[\"plugin.events\"]", "[\"ui.contentShown\"]")), null);

        assertFalse(host.isLoaded("a"));
        assertTrue(host.isLoaded("b"));
        assertEquals(1, runtime.destroyed.size());
    }

    @Test
    public void subscribersOfReflectsPermissionAndDeclaration() {
        PluginManifest m = manifest("a", "[\"plugin.events\"]", "[\"ui.contentShown\"]");
        host.load(List.of(m), null);

        assertEquals(List.of("a"), host.subscribersOf(PluginEventBus.EVENT_UI_CONTENT_SHOWN));
        assertTrue(host.subscribersOf(PluginEventBus.EVENT_ACTIVITY_CREATED).isEmpty());
        assertTrue(host.subscribersOf(null).isEmpty());
    }

    @Test
    public void uiSlotsExcludeQuarantinedPlugins() {
        String json = """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "a",
                  "name": "a",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "p.js" },
                  "events": ["ui.contentShown"],
                  "ui": [ { "slot": "floating", "label": "X", "action": "open" } ],
                  "permissions": ["plugin.events", "ui.floating"]
                }
                """;
        PluginManifest m = PluginManifest.parse(json);
        host.load(List.of(m), null);
        assertEquals(1, host.uiSlots().size());

        runtime.failEvent = true;
        bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "HomeActivity");

        assertTrue("quarantined plugin must not contribute UI", host.uiSlots().isEmpty());
    }

    // ---------- 假运行时 ----------

    static final class FakeRuntime implements PluginRuntime {
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<String> actions = Collections.synchronizedList(new ArrayList<>());
        final List<String> destroyed = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger inits = new AtomicInteger();
        volatile boolean failInit;
        volatile boolean failEvent;
        volatile Set<String> failEventFor = Set.of();

        @Override
        public String init(String pluginId, String entry, String config) {
            inits.incrementAndGet();
            if (failInit) throw new IllegalStateException("init boom");
            return "ok";
        }

        @Override
        public String onEvent(String pluginId, String event, String payload) {
            if (failEvent || failEventFor.contains(pluginId)) throw new IllegalStateException("event boom");
            events.add(pluginId + "|" + event + "|" + payload);
            return "ok";
        }

        @Override
        public String action(String pluginId, String action, String payload) {
            actions.add(pluginId + "|" + action);
            return "ok";
        }

        @Override
        public void destroy(String pluginId) {
            destroyed.add(pluginId);
        }
    }
}
