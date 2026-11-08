package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.github.catvod.utils.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 参数配置与 {@code net.request} 的**真机链路**测试。
 *
 * <p>JVM 单测能证明 {@link PluginEnvironment} 替换得对、{@link PluginNetBridge} 会记账，
 * 但证不了这两样东西**真的接进了 QuickJS 的全局对象**：宿主注入用的是
 * {@code JSObject.setProperty(...) + JSCallFunction}，那条路只在真机上有原生实现。
 * 因此这里跑的是完整链路：清单 params → 用户填的环境 → 占位符替换 → 信封 →
 * {@code runtime.init} → 注入 {@code webhtv.config} → 脚本读它 → 脚本调 {@code webhtv.request}
 * → 宿主 OkHttp 打到本地 socket 服务器 → 结果回传脚本。
 */
@RunWith(AndroidJUnit4.class)
public class PluginParamsNetDeviceTest {

    private static final String ID = "e2e.params";

    private File sourceDir;
    private ServerSocket server;
    private Thread serverThread;

    @Before
    public void setUp() throws Exception {
        PluginController.reset();
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        PluginEnvironmentStore.get().remove(ID);
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        sourceDir = new File(Path.cache(), "e2e-params-src");
        deleteQuietly(sourceDir);
        assertTrue(sourceDir.mkdirs());
        PluginAuditLog.get().clear();
    }

    @After
    public void tearDown() throws Exception {
        stopServer();
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        PluginEnvironmentStore.get().remove(ID);
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        PluginBootstrap.reloadAll();
        PluginController.reset();
        deleteQuietly(sourceDir);
        PluginAuditLog.get().clear();
    }

    @Test
    public void paramsReachTheScriptWithPlaceholdersResolved() {
        install("[\"plugin.events\"]", """
                webhtv.action('probe.config', function () {
                  return JSON.stringify(webhtv.config);
                });
                """);
        assertTrue(PluginEnvironmentStore.get().put(ID, "{\"TOKEN\":\"abc123\"}"));

        assertTrue(PluginBootstrap.reloadAll());
        String config = PluginController.get().host().action(ID, "probe.config", "{}");

        assertNotNull(config);
        // 逐字段断言而不是 contains：contains 在 config 意外为空串时也会漏判。
        com.google.gson.JsonObject parsed = com.google.gson.JsonParser.parseString(config).getAsJsonObject();
        assertEquals("placeholder must be replaced by the user value", "abc123", parsed.get("token").getAsString());
        assertEquals("non-placeholder params must survive", 20, parsed.get("limit").getAsInt());
    }

    @Test
    public void missingEnvironmentValueBecomesEmptyStringNotLiteral() {
        install("[\"plugin.events\"]", """
                webhtv.action('probe.config', function () {
                  return JSON.stringify(webhtv.config);
                });
                """);

        assertTrue(PluginBootstrap.reloadAll());
        String config = PluginController.get().host().action(ID, "probe.config", "{}");

        assertNotNull(config);
        com.google.gson.JsonObject parsed = com.google.gson.JsonParser.parseString(config).getAsJsonObject();
        assertTrue("the param key must still exist", parsed.has("token"));
        assertEquals("unfilled placeholder must collapse to empty string", "", parsed.get("token").getAsString());
    }

    @Test
    public void netRequestReachesARealServer() throws Exception {
        serveOnce("hello-from-server");
        install("[\"plugin.events\",\"net.request\"]", String.format(java.util.Locale.US, """
                webhtv.action('probe.net', function () {
                  var res = webhtv.request('GET', 'http://127.0.0.1:%d/probe?token=secret');
                  return res.status + ':' + res.body;
                });
                """, server.getLocalPort()));

        assertTrue(PluginBootstrap.reloadAll());
        String result = PluginController.get().host().action(ID, "probe.net", "{}");

        assertEquals("200:hello-from-server", result);
        boolean audited = false;
        for (PluginAuditLog.Entry entry : PluginAuditLog.get().entriesFor(ID)) {
            if ("request".equals(entry.kind()) && "GET".equals(entry.method()) && entry.status() == 200) audited = true;
        }
        assertTrue("a real request must be audited under the plugin id", audited);
    }

    @Test
    public void netRequestWithoutPermissionIsBlockedNotCrashed() throws Exception {
        serveOnce("should-not-be-read");
        install("[\"plugin.events\"]", String.format(java.util.Locale.US, """
                webhtv.action('probe.net', function () {
                  var res = webhtv.request('GET', 'http://127.0.0.1:%d/probe');
                  return String(res.status) + ':' + res.error;
                });
                """, server.getLocalPort()));

        assertTrue(PluginBootstrap.reloadAll());
        String result = PluginController.get().host().action(ID, "probe.net", "{}");

        assertNotNull("a blocked call must still return a result, not throw", result);
        assertTrue("blocked call must report status 0: " + result, result.startsWith("0:"));
        assertTrue("blocked call must name the missing permission: " + result, result.contains("net.request"));
        assertTrue("plugin must stay loaded", PluginController.get().host().isLoaded(ID));
        assertTrue(PluginController.get().host().failedPlugins().isEmpty());
    }

    // ---- 装置 ----

    private void install(String permissionsJson, String script) {
        write(new File(sourceDir, "manifest.json"), String.format(java.util.Locale.US, """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "%s",
                  "name": "Params Probe",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "main.js", "timeout": 15 },
                  "params": { "token": "${TOKEN}", "limit": 20 },
                  "events": ["ui.contentShown"],
                  "permissions": %s
                }
                """, ID, permissionsJson));
        write(new File(sourceDir, "main.js"), script);
        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(sourceDir);
        assertTrue("import failed: " + result.error(), result.isSuccess());
    }

    /** 起一个只应答一次的裸 socket 服务器：够验证"真发出去、真读回来"，且不引入新依赖。 */
    private void serveOnce(String body) throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        serverThread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                InputStream in = socket.getInputStream();
                byte[] buffer = new byte[4096];
                //noinspection ResultOfMethodCallIgnored
                in.read(buffer);
                byte[] payload = body.getBytes("UTF-8");
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "
                        + payload.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                out.write(payload);
                out.flush();
            } catch (Exception ignored) {
            }
        }, "probe-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopServer() throws Exception {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    private static void write(File file, String text) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            try {
                out.write(text.getBytes(java.nio.charset.Charset.forName("UTF-8")));
            } finally {
                out.close();
            }
        } catch (Exception e) {
            throw new AssertionError("cannot write fixture " + file, e);
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteQuietly(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
