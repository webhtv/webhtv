package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Set;

/**
 * 配置信封的往返测试。
 *
 * <p>信封存在的唯一理由是 {@link PluginRuntime#init} 只有一个字符串入口，
 * 所以这里要保证三件事：拆开后的参数与写进去的一样、权限集合可还原、非法信封不抛异常。
 */
public class PluginConfigTest {

    private static PluginManifest manifest() {
        String json = """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "douban.comments",
                  "name": "豆瓣评论",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "douban.js", "timeout": 20 },
                  "events": ["ui.contentShown"],
                  "permissions": ["plugin.events", "net.request"]
                }
                """;
        return PluginManifest.parse(json);
    }

    @Test
    public void roundTripsParamsPermissionsAndTimeout() {
        JsonObject params = JsonParser.parseString("{\"TOKEN\":\"x\"}").getAsJsonObject();
        String config = PluginConfig.create(manifest(), params);

        assertEquals("x", PluginConfig.paramsOf(config).get("TOKEN").getAsString());
        assertEquals(Set.of(PluginCapabilityRegistry.PERMISSION_EVENTS, PluginCapabilityRegistry.PERMISSION_NET_REQUEST),
                PluginConfig.permissionsOf(config));
        assertEquals(20, PluginConfig.timeoutOf(config, 15));
    }

    @Test
    public void nullParamsBecomeEmptyObject() {
        String config = PluginConfig.create(manifest(), null);
        assertEquals(0, PluginConfig.paramsOf(config).size());
    }

    @Test
    public void nullConfigDegradesToEmptyEverything() {
        assertEquals(0, PluginConfig.paramsOf(null).size());
        assertTrue(PluginConfig.permissionsOf(null).isEmpty());
        assertEquals(15, PluginConfig.timeoutOf(null, 15));
    }

    @Test
    public void malformedConfigDoesNotThrow() {
        assertEquals(0, PluginConfig.paramsOf("{oops").size());
        assertTrue(PluginConfig.permissionsOf("{oops").isEmpty());
        assertEquals(7, PluginConfig.timeoutOf("{oops", 7));
    }

    @Test
    public void timeoutOfFallsBackWhenValueIsNotANumber() {
        String config = "{\"timeout\":\"abc\"}";
        assertEquals(9, PluginConfig.timeoutOf(config, 9));
    }

    @Test
    public void createToleratesNullManifest() {
        String config = PluginConfig.create(null, null);
        assertNotNull(config);
        assertEquals(0, PluginConfig.paramsOf(config).size());
        assertTrue(PluginConfig.permissionsOf(config).isEmpty());
    }
}
