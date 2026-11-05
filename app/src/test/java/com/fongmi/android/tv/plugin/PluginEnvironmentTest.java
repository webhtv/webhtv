package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

/**
 * 参数模板替换的边界测试。
 *
 * <p>核心断言只有两条：**整值占位符**被替换、**缺失变量落成空串而不是保留字面量**。
 * 后者曾经是字幕源上的真实事故——没填 token 时脚本把 {@code ${ASSRT_TOKEN}} 当真实值发了出去。
 */
public class PluginEnvironmentTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    public void replacesWholeValuePlaceholder() {
        JsonObject template = json("{\"token\":\"${DOUBAN_TOKEN}\"}");
        JsonObject environment = json("{\"DOUBAN_TOKEN\":\"abc123\"}");
        assertEquals("abc123", PluginEnvironment.resolve(template, environment).get("token").getAsString());
    }

    @Test
    public void missingVariableBecomesEmptyStringNotLiteral() {
        JsonObject template = json("{\"token\":\"${DOUBAN_TOKEN}\"}");
        JsonObject resolved = PluginEnvironment.resolve(template, new JsonObject());
        assertEquals("", resolved.get("token").getAsString());
    }

    @Test
    public void nestedObjectsAndArraysAreResolved() {
        JsonObject template = json("{\"headers\":{\"Cookie\":\"${COOKIE}\"},\"list\":[\"${A}\",\"literal\"]}");
        JsonObject environment = json("{\"COOKIE\":\"c=1\",\"A\":\"a\"}");
        JsonObject resolved = PluginEnvironment.resolve(template, environment);
        assertEquals("c=1", resolved.getAsJsonObject("headers").get("Cookie").getAsString());
        assertEquals("a", resolved.getAsJsonArray("list").get(0).getAsString());
        assertEquals("literal", resolved.getAsJsonArray("list").get(1).getAsString());
    }

    @Test
    public void partialPlaceholderIsLeftUntouched() {
        // 半截占位符（字符串内插）刻意不替换：替换了用户就看不见自己漏填了。
        JsonObject template = json("{\"auth\":\"Bearer ${TOKEN}\"}");
        JsonObject resolved = PluginEnvironment.resolve(template, json("{\"TOKEN\":\"t\"}"));
        assertEquals("Bearer ${TOKEN}", resolved.get("auth").getAsString());
    }

    @Test
    public void nonStringValuesSurviveUntouched() {
        JsonObject template = json("{\"limit\":20,\"flag\":true}");
        JsonObject resolved = PluginEnvironment.resolve(template, new JsonObject());
        assertEquals(20, resolved.get("limit").getAsInt());
        assertTrue(resolved.get("flag").getAsBoolean());
    }

    @Test
    public void nullTemplateYieldsEmptyObject() {
        assertEquals(0, PluginEnvironment.resolve(null, json("{\"A\":\"a\"}")).size());
    }

    @Test
    public void templateIsNotMutated() {
        JsonObject template = json("{\"token\":\"${T}\"}");
        PluginEnvironment.resolve(template, json("{\"T\":\"v\"}"));
        assertEquals("${T}", template.get("token").getAsString());
    }

    @Test
    public void placeholderNameRejectsMalformed() {
        assertNull(PluginEnvironment.placeholderName(null));
        assertNull(PluginEnvironment.placeholderName("${a}"));
        assertNull(PluginEnvironment.placeholderName("${A"));
        assertNull(PluginEnvironment.placeholderName("${}"));
        assertNull(PluginEnvironment.placeholderName("${A-B}"));
        assertEquals("A_1", PluginEnvironment.placeholderName("${A_1}"));
    }
}
