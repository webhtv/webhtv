package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

/**
 * 清单解析的边界测试。重点不是"能解析正常清单"，而是**畸形清单必须被整体拒绝**：
 * 半加载的插件会产生无法解释的行为，比不加载更糟。
 */
public class PluginManifestTest {

    private static String valid() {
        return """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "douban.comments",
                  "name": "豆瓣评论",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "douban.js", "timeout": 15 },
                  "events": ["ui.contentShown", "activity.created"],
                  "ui": [
                    { "slot": "floating", "label": "豆瓣", "action": "comments.show", "event": "ui.contentShown" }
                  ],
                  "permissions": ["plugin.events", "ui.floating"]
                }
                """;
    }

    @Test
    public void parsesWellFormedManifest() {
        PluginManifest manifest = PluginManifest.parse(valid());
        assertNotNull(manifest);
        assertEquals("douban.comments", manifest.getId());
        assertEquals("豆瓣评论", manifest.getName());
        assertEquals("js", manifest.getRuntimeApi());
        assertEquals("douban.js", manifest.getRuntimeEntry());
        assertEquals(15, manifest.getTimeoutSeconds());
        assertEquals(Set.of(PluginCapabilityRegistry.PERMISSION_EVENTS, PluginCapabilityRegistry.PERMISSION_UI_FLOATING),
                manifest.getPermissions());
    }

    @Test
    public void rejectsWrongProtocol() {
        assertNull(PluginManifest.parse(valid().replace("webhtv-plugin", "other-plugin")));
    }

    @Test
    public void rejectsWrongSchemaVersion() {
        assertNull(PluginManifest.parse(valid().replace("\"version\": 1", "\"version\": 2")));
    }

    @Test
    public void rejectsUnsupportedSdk() {
        assertNull(PluginManifest.parse(valid().replace("\"sdk\": 1", "\"sdk\": 99")));
    }

    @Test
    public void rejectsInvalidIdCharacters() {
        assertNull(PluginManifest.parse(valid().replace("douban.comments", "豆瓣 评论")));
    }

    @Test
    public void rejectsUnknownRuntime() {
        assertNull(PluginManifest.parse(valid().replace("\"api\": \"js\"", "\"api\": \"wasm\"")));
    }

    @Test
    public void rejectsMissingRuntimeEntry() {
        assertNull(PluginManifest.parse(valid().replace("\"entry\": \"douban.js\",", "")));
    }

    @Test
    public void rejectsWhenAllPermissionsAreUnknown() {
        // 声明了权限但一个都不认识 → 与本宿主不兼容，必须拒绝而不是降级成无权限插件
        String json = valid().replace(
                "\"permissions\": [\"plugin.events\", \"ui.floating\"]",
                "\"permissions\": [\"unknown.one\", \"unknown.two\"]");
        assertNull(PluginManifest.parse(json));
    }

    @Test
    public void dropsUnknownPermissionsButKeepsKnownOnes() {
        String json = valid().replace(
                "\"permissions\": [\"plugin.events\", \"ui.floating\"]",
                "\"permissions\": [\"plugin.events\", \"future.thing\"]");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertEquals(Set.of(PluginCapabilityRegistry.PERMISSION_EVENTS), manifest.getPermissions());
    }

    @Test
    public void eventWithoutCoveringPermissionIsDropped() {
        // activity.created 只由 plugin.events 覆盖；清单只给了 plugin.data.episode
        String json = valid().replace(
                "\"permissions\": [\"plugin.events\", \"ui.floating\"]",
                "\"permissions\": [\"plugin.data.episode\"]");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertFalse(manifest.subscribesTo(PluginEventBus.EVENT_ACTIVITY_CREATED));
        assertTrue(manifest.subscribesTo(PluginEventBus.EVENT_UI_CONTENT_SHOWN));
    }

    @Test
    public void uiSlotWithoutPermissionIsDropped() {
        // 声明了 floating 槽位但没有 ui.floating 权限
        String json = valid().replace(
                "\"permissions\": [\"plugin.events\", \"ui.floating\"]",
                "\"permissions\": [\"plugin.events\"]");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertTrue(manifest.getUiSlots().isEmpty());
    }

    @Test
    public void keepsLeanbackOverrideSlot() {
        String json = valid().replace(
                "\"permissions\": [\"plugin.events\", \"ui.floating\"]",
                "\"permissions\": [\"plugin.events\", \"ui.floating\", \"ui.toolbar\"]")
                .replace("""
                        { "slot": "floating", "label": "豆瓣", "action": "comments.show", "event": "ui.contentShown" }""",
                        """
                        { "slot": "floating", "label": "豆瓣", "action": "comments.show", "event": "ui.contentShown" },
                        { "slot": "toolbar", "label": "豆瓣", "action": "comments.show", "anchor": "before:episode-list" }""");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertEquals(2, manifest.getUiSlots().size());
        assertEquals("toolbar", manifest.getUiSlots().get(1).slot());
        assertEquals("before:episode-list", manifest.getUiSlots().get(1).anchor());
    }

    @Test
    public void rejectsOversizedManifest() {
        String huge = valid().replace("\"name\": \"豆瓣评论\"", "\"name\": \"" + "x".repeat(70 * 1024) + "\"");
        assertNull(PluginManifest.parse(huge));
    }

    @Test
    public void rejectsNonObjectAndBlank() {
        assertNull(PluginManifest.parse(""));
        assertNull(PluginManifest.parse(null));
        assertNull(PluginManifest.parse("[]"));
        assertNull(PluginManifest.parse("not json"));
    }

    @Test
    public void timeoutIsClampedToSafeRange() {
        String tooLong = valid().replace("\"timeout\": 15", "\"timeout\": 9999");
        PluginManifest manifest = PluginManifest.parse(tooLong);
        assertNotNull(manifest);
        assertEquals(15, manifest.getTimeoutSeconds());
    }

    @Test
    public void absentParamsYieldEmptyObject() {
        PluginManifest manifest = PluginManifest.parse(valid());
        assertNotNull(manifest);
        assertNotNull(manifest.getParams());
        assertEquals(0, manifest.getParams().size());
    }

    @Test
    public void paramsArePreservedVerbatimForLaterSubstitution() {
        String json = valid().replace("\"events\":",
                "\"params\": { \"token\": \"${DOUBAN_TOKEN}\", \"limit\": 20, \"headers\": { \"Referer\": \"https://movie.douban.com/\" } },\n  \"events\":");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertEquals("${DOUBAN_TOKEN}", manifest.getParams().get("token").getAsString());
        assertEquals(20, manifest.getParams().get("limit").getAsInt());
        assertEquals("https://movie.douban.com/",
                manifest.getParams().getAsJsonObject("headers").get("Referer").getAsString());
    }

    @Test
    public void paramsAreNotSharedBetweenReads() {
        // getParams() 每次返回深拷贝：调用方会就地替换占位符，共享会让第二次解析读到已污染的值。
        String json = valid().replace("\"events\":", "\"params\": { \"token\": \"x\" },\n  \"events\":");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        manifest.getParams().addProperty("token", "polluted");
        assertEquals("x", manifest.getParams().get("token").getAsString());
    }

    @Test
    public void absentSurfacesMeansEveryPage() {
        // 向后兼容的命门：早期清单都没有 surfaces 键，空集必须等于"全页显示"。
        PluginManifest manifest = PluginManifest.parse(valid());
        assertNotNull(manifest);
        assertEquals(1, manifest.getUiSlots().size());
        assertTrue(manifest.getUiSlots().get(0).surfaces().isEmpty());
    }

    @Test
    public void surfacesAreParsedLowerCaseAndDeduplicated() {
        String json = valid().replace(
                "\"action\": \"comments.show\", \"event\": \"ui.contentShown\" }",
                "\"action\": \"comments.show\", \"event\": \"ui.contentShown\","
                        + " \"surfaces\": [\"Detail\", \"PLAYBACK\", \"detail\"] }");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertEquals(Set.of("detail", "playback"), manifest.getUiSlots().get(0).surfaces());
    }

    @Test
    public void nonStringAndOversizedSurfacesAreDropped() {
        // 坏元素逐个丢弃，既不抛也不把整份清单判死。
        String json = valid().replace(
                "\"action\": \"comments.show\", \"event\": \"ui.contentShown\" }",
                "\"action\": \"comments.show\", \"event\": \"ui.contentShown\","
                        + " \"surfaces\": [7, {\"a\": 1}, true, \"\", \"" + "x".repeat(17)
                        + "\", \" \", \"detail\"] }");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertEquals(Set.of("detail"), manifest.getUiSlots().get(0).surfaces());
    }

    @Test
    public void surfacesBeyondTheCapAreNotRead() {
        StringBuilder many = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            if (i > 1) many.append(", ");
            many.append('"').append("s").append(i).append('"');
        }
        String json = valid().replace(
                "\"action\": \"comments.show\", \"event\": \"ui.contentShown\" }",
                "\"action\": \"comments.show\", \"event\": \"ui.contentShown\","
                        + " \"surfaces\": [" + many + "] }");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertEquals(8, manifest.getUiSlots().get(0).surfaces().size());
        assertFalse(manifest.getUiSlots().get(0).surfaces().contains("s9"));
    }

    @Test
    public void nonObjectParamsAreIgnoredNotRejected() {
        // params 是可选字段：写成数组说明作者写错了，但整个插件不该因此装不上。
        String json = valid().replace("\"events\":", "\"params\": [1, 2],\n  \"events\":");
        PluginManifest manifest = PluginManifest.parse(json);
        assertNotNull(manifest);
        assertEquals(0, manifest.getParams().size());
    }
}
