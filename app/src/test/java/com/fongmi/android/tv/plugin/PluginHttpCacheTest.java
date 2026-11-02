package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

/**
 * 宿主侧 HTTP 缓存测试。
 *
 * <p>全部用真实 HTTP 服务器（{@code MockWebServer}）加请求计数："命中缓存"这件事唯一的证据就是
 * 服务端没收到第二次请求，断言返回体是证明不了的。
 *
 * <p>缓存根目录通过 {@link PluginHttpCache#overrideRootForTest(File)} 注入临时目录，因为
 * {@code Path.cache()} 依赖 {@code Init.context()}，纯 JVM 单测里拿不到。
 */
public class PluginHttpCacheTest {

    private MockWebServer server;
    private File cache;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        cache = cacheDir("plugin-http-cache");
        PluginHttpCache.overrideRootForTest(cache);
        PluginAuditLog.get().clear();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) server.close();
        PluginHttpCache.overrideRootForTest(null);
        PluginAuditLog.get().clear();
        delete(cache);
    }

    /** AC1：不传 ttl 与传 0 都必须与今天的六参调用逐字节相同——每次都真的发出去。 */
    @Test
    public void omittedAndZeroTtlKeepBehavingLikeTodaysCall() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("first").build());
        server.enqueue(new MockResponse.Builder().code(200).body("second").build());
        String url = server.url("/subject").toString();

        assertEquals("first", body(PluginNetBridge.request("p", "GET", url, null, null, 15)));
        assertEquals("second", body(PluginNetBridge.request("p", "GET", url, null, null, 15, 0)));

        assertEquals(2, server.getRequestCount());
        assertNull("ttl 为 0 时一个缓存文件都不该存在", onlyFile(cache));
    }

    /** AC1：ttl 的解析必须容忍脚本传来的字符串、小数与越界值。 */
    @Test
    public void ttlParsingAcceptsNumbersAndRejectsWhatIsNotOne() {
        assertEquals(0, PluginHttpCache.ttlSeconds(null));
        assertEquals(0, PluginHttpCache.ttlSeconds(""));
        assertEquals(0, PluginHttpCache.ttlSeconds("  "));
        assertEquals(0, PluginHttpCache.ttlSeconds("abc"));
        assertEquals(0, PluginHttpCache.ttlSeconds("0"));
        assertEquals(0, PluginHttpCache.ttlSeconds("-60"));
        assertEquals(1, PluginHttpCache.ttlSeconds("0.5"));
        assertEquals(120, PluginHttpCache.ttlSeconds("120"));
        assertEquals(120, PluginHttpCache.ttlSeconds("120.9"));
        assertEquals(PluginHttpCache.MAX_TTL_SECONDS, PluginHttpCache.ttlSeconds("999999999"));
    }

    /** AC2 + AC8：命中时服务端收不到请求，审计记 outcome=cache 且状态码如实是 200。 */
    @Test
    public void cacheHitSkipsTheNetworkAndIsAuditedAsCache() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("{\"rating\":8.7}").build());
        String url = server.url("/subject/1").toString();

        String first = PluginNetBridge.request("p", "GET", url, null, null, 15, 60);
        String second = PluginNetBridge.request("p", "GET", url, null, null, 15, 60);

        assertEquals(1, server.getRequestCount());
        assertEquals(200, status(second));
        assertEquals(body(first), body(second));
        PluginAuditLog.Entry hit = lastEntry();
        assertEquals("cache", hit.outcome());
        assertEquals(200, hit.status());
        assertEquals("GET", hit.method());
    }

    /** AC3：过期条目必须重新请求，并用新响应覆盖旧内容。 */
    @Test
    public void expiredEntryIsRequestedAgainAndOverwritten() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("old").build());
        server.enqueue(new MockResponse.Builder().code(200).body("fresh").build());
        String url = server.url("/subject/2").toString();

        assertEquals("old", body(PluginNetBridge.request("p", "GET", url, null, null, 15, 60)));
        File entry = PluginHttpCache.file("p", PluginHttpCache.key("p", "GET", url, null, null));
        assertTrue("第一次请求必须落盘", entry.isFile());
        assertTrue(entry.setLastModified(System.currentTimeMillis() - 120_000L));

        assertEquals("fresh", body(PluginNetBridge.request("p", "GET", url, null, null, 15, 60)));

        assertEquals(2, server.getRequestCount());
        assertEquals("fresh", new String(Files.readAllBytes(entry.toPath()), StandardCharsets.UTF_8));
    }

    /** AC4：POST 带 ttl 也不读不写缓存——重放一个写请求会真的改变服务端状态。 */
    @Test
    public void postWithTtlNeverTouchesTheCache() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("one").build());
        server.enqueue(new MockResponse.Builder().code(200).body("two").build());
        String url = server.url("/interest").toString();

        assertEquals("one", body(PluginNetBridge.request("p", "POST", url, null, "{}", 15, 600)));
        assertEquals("two", body(PluginNetBridge.request("p", "POST", url, null, "{}", 15, 600)));

        assertEquals(2, server.getRequestCount());
        assertNull("POST 不该留下任何缓存文件", onlyFile(cache));
    }

    /** AC5：两个插件请求同一个 URL 必须各自回源，否则 A 的响应会被 B 当成自己的。 */
    @Test
    public void differentPluginsNeverShareAnEntry() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("from-a").build());
        server.enqueue(new MockResponse.Builder().code(200).body("from-b").build());
        String url = server.url("/subject/3").toString();

        assertEquals("from-a", body(PluginNetBridge.request("plugin-a", "GET", url, null, null, 15, 600)));
        assertEquals("from-b", body(PluginNetBridge.request("plugin-b", "GET", url, null, null, 15, 600)));

        assertEquals(2, server.getRequestCount());
        assertEquals("from-a", body(PluginNetBridge.request("plugin-a", "GET", url, null, null, 15, 600)));
        assertEquals("from-b", body(PluginNetBridge.request("plugin-b", "GET", url, null, null, 15, 600)));
        assertEquals("两个插件各自的缓存互不串用", 2, server.getRequestCount());
    }

    /** AC6：0 字节、超上限、读不动的文件都只能被静默当成未命中。 */
    @Test
    public void corruptEntriesAreTreatedAsMissesAndNeverThrow() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("v1").build());
        server.enqueue(new MockResponse.Builder().code(200).body("v2").build());
        server.enqueue(new MockResponse.Builder().code(200).body("v3").build());
        String url = server.url("/subject/4").toString();
        File entry = PluginHttpCache.file("p", PluginHttpCache.key("p", "GET", url, null, null));
        assertNotNull(entry.getParentFile());
        assertTrue(entry.getParentFile().mkdirs() || entry.getParentFile().isDirectory());

        Files.write(entry.toPath(), new byte[0]);
        assertEquals("v1", body(PluginNetBridge.request("p", "GET", url, null, null, 15, 600)));

        Files.write(entry.toPath(), new byte[PluginNetBridge.MAX_RESPONSE_BYTES + 1]);
        assertEquals("v2", body(PluginNetBridge.request("p", "GET", url, null, null, 15, 600)));

        // 目录冒充文件：读一定抛 IOException，但异常绝不许冒到脚本。
        assertTrue(entry.delete());
        assertTrue(entry.mkdirs());
        assertEquals("v3", body(PluginNetBridge.request("p", "GET", url, null, null, 15, 600)));

        assertEquals(3, server.getRequestCount());
    }

    /** 只有 200 值得复用：一个 403 存下来会让插件在 TTL 内反复看到同一个失败。 */
    @Test
    public void nonOkStatusIsNeverCached() throws Exception {
        server.enqueue(new MockResponse.Builder().code(403).body("denied").build());
        server.enqueue(new MockResponse.Builder().code(200).body("granted").build());
        String url = server.url("/subject/5").toString();

        assertEquals(403, status(PluginNetBridge.request("p", "GET", url, null, null, 15, 600)));
        assertNull(onlyFile(cache));
        assertEquals("granted", body(PluginNetBridge.request("p", "GET", url, null, null, 15, 600)));
    }

    /** 头集合不变、只是书写顺序不同，不该变成两次网络请求。 */
    @Test
    public void headerOrderDoesNotSplitTheEntry() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("once").build());
        String url = server.url("/subject/6").toString();

        assertEquals("once", body(PluginNetBridge.request("p", "GET", url,
                "{\"Referer\":\"https://movie.douban.com/\",\"User-Agent\":\"ua\"}", null, 15, 600)));
        assertEquals("once", body(PluginNetBridge.request("p", "GET", url,
                "{\"user-agent\":\"ua\",\"referer\":\"https://movie.douban.com/\"}", null, 15, 600)));

        assertEquals(1, server.getRequestCount());
    }

    /** 缓存目录名必须能容纳任意清单 id：只做安全化，绝不把 id 当路径用。 */
    @Test
    public void pluginIdIsSanitisedBeforeItBecomesADirectoryName() {
        File hostile = PluginHttpCache.file("../../etc/passwd", "hash");
        assertEquals(new File(cache, ".._.._etc_passwd"), hostile.getParentFile());
        assertEquals(new File(cache, "_"), PluginHttpCache.file(null, "hash").getParentFile());
    }

    private static int status(String text) {
        return parse(text).get("status").getAsInt();
    }

    private static String body(String text) {
        return parse(text).get("body").getAsString();
    }

    private static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static PluginAuditLog.Entry lastEntry() {
        return PluginAuditLog.get().entries().get(PluginAuditLog.get().size() - 1);
    }

    /** 缓存根目录下的第一个普通文件；用来断言「一个文件都没写」。 */
    private static File onlyFile(File root) {
        File[] files = root.listFiles();
        if (files == null || files.length == 0) return null;
        for (File file : files) {
            if (file.isFile()) return file;
            File[] nested = file.listFiles();
            if (nested != null) {
                for (File inner : nested) {
                    if (inner.isFile()) return inner;
                }
            }
        }
        return null;
    }

    private static File cacheDir(String prefix) throws IOException {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        return Files.createTempDirectory(base, prefix).toFile();
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }
}
