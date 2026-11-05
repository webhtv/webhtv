package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

/**
 * 插件网络桥测试。
 *
 * <p>用真实 HTTP 服务器（{@code MockWebServer}）而不是 mock 掉 {@code OkHttp}：
 * 桥的职责里有一半是"把 okhttp 的失败翻译成脚本能读的 JSON"，mock 掉客户端就测不到这一半。
 *
 * <p>断言的核心不变量是**永不抛异常**——脚本侧没有 try/catch 的习惯，一次未捕获的 Java 异常
 * 会被 {@link PluginHost} 判成运行时就故障，把整个插件隔离掉。
 */
public class PluginNetBridgeTest {

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        PluginAuditLog.get().clear();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) server.close();
        PluginAuditLog.get().clear();
    }

    private static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    public void getReturnsStatusAndBody() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("{\"rating\":8.7}").build());

        JsonObject result = parse(PluginNetBridge.request("p", "GET", server.url("/subject").toString(), null, null, 15));

        assertEquals(200, result.get("status").getAsInt());
        assertTrue(result.get("body").getAsString().contains("8.7"));
    }

    @Test
    public void headersAreForwardedFromJsonText() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());

        PluginNetBridge.request("p", "GET", server.url("/x").toString(),
                "{\"Referer\":\"https://movie.douban.com/\",\"User-Agent\":\"WebHtv\"}", null, 15);

        RecordedRequest request = server.takeRequest();
        assertEquals("https://movie.douban.com/", request.getHeaders().get("Referer"));
        assertEquals("WebHtv", request.getHeaders().get("User-Agent"));
    }

    @Test
    public void postCarriesBodyAndContentType() throws Exception {
        server.enqueue(new MockResponse.Builder().code(201).body("created").build());

        PluginNetBridge.request("p", "POST", server.url("/x").toString(),
                "{\"Content-Type\":\"application/json; charset=utf-8\"}", "{\"a\":1}", 15);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("{\"a\":1}", request.getBody().utf8());
        assertTrue(request.getHeaders().get("Content-Type").startsWith("application/json"));
    }

    @Test
    public void errorStatusIsReportedNotThrown() throws Exception {
        server.enqueue(new MockResponse.Builder().code(403).body("forbidden").build());

        JsonObject result = parse(PluginNetBridge.request("p", "GET", server.url("/x").toString(), null, null, 15));

        assertEquals(403, result.get("status").getAsInt());
        assertEquals("forbidden", result.get("body").getAsString());
    }

    @Test
    public void unsupportedMethodIsBlocked() {
        JsonObject result = parse(PluginNetBridge.request("p", "TRACE", "https://example.com/", null, null, 15));
        assertEquals(0, result.get("status").getAsInt());
        assertTrue(result.get("error").getAsString().contains("unsupported method"));
        assertEquals("blocked", lastOutcome());
    }

    @Test
    public void nonHttpSchemeIsBlocked() {
        JsonObject result = parse(PluginNetBridge.request("p", "GET", "file:///etc/passwd", null, null, 15));
        assertEquals(0, result.get("status").getAsInt());
        assertTrue(result.get("error").getAsString().contains("scheme"));
        assertEquals("blocked", lastOutcome());
    }

    @Test
    public void connectionFailureBecomesErrorJson() {
        // 端口 1 上没有服务；断言的是"失败被翻译成 JSON"而不是具体异常类型。
        JsonObject result = parse(PluginNetBridge.request("p", "GET", "http://127.0.0.1:1/x", null, null, 2));
        assertEquals(0, result.get("status").getAsInt());
        assertNotNull(result.get("error"));
        assertEquals("error", lastOutcome());
    }

    @Test
    public void malformedHeadersJsonIsRejected() {
        JsonObject result = parse(PluginNetBridge.request("p", "GET", server.url("/x").toString(), "{not json", null, 15));
        assertEquals(0, result.get("status").getAsInt());
        assertTrue(result.get("error").getAsString().contains("headers"));
    }

    @Test
    public void oversizedResponseBodyIsTruncated() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200)
                .body("x".repeat(PluginNetBridge.MAX_RESPONSE_BYTES + 4096)).build());

        JsonObject result = parse(PluginNetBridge.request("p", "GET", server.url("/big").toString(), null, null, 15));

        assertEquals(PluginNetBridge.MAX_RESPONSE_BYTES, result.get("body").getAsString().length());
    }

    @Test
    public void requestIsAuditedWithoutQueryString() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());

        PluginNetBridge.request("p", "GET", server.url("/subject/1?token=secret").toString(), null, null, 15);

        PluginAuditLog.Entry entry = PluginAuditLog.get().entries().get(0);
        assertEquals("p", entry.pluginId());
        assertEquals("GET", entry.method());
        assertEquals(200, entry.status());
        assertEquals("ok", entry.outcome());
        // query 里常带签名，审计只留 host + path。
        assertFalse(entry.path().contains("secret"));
    }

    @Test
    public void blockedCallIsAudited() {
        PluginNetBridge.blocked("p", "GET", "https://example.com/x", "missing permission net.request");
        PluginAuditLog.Entry entry = PluginAuditLog.get().entries().get(0);
        assertEquals("blocked", entry.outcome());
        assertEquals("p", entry.pluginId());
    }

    private static String lastOutcome() {
        return PluginAuditLog.get().entries().get(PluginAuditLog.get().size() - 1).outcome();
    }
}
