package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.github.catvod.utils.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * 插件系统**端到端真机链路**测试：真实目录 → 真实导入器 → 真实配置落盘 → 真实装载
 * → 真实 QuickJS 执行 → 真实动作返回值 → 关闭开关后真实卸载。
 *
 * <p>存在理由：P2f 之前所有证据都是**分段**的——导入器有单测（但用临时根目录接缝）、
 * 配置有单测（但用假 Store）、QuickJS 有仪器化测试（但直插 factory/host，绕过了导入与
 * 配置）。分段全绿并不能证明"用户选一个目录、装上来、脚本真跑起来"这条链条通。本测试
 * 把四段串成一条，用真实 {@link Path#files()} 与真实 SharedPreferences 跑一遍。
 *
 * <p>它同时覆盖一个真实缺陷的回归：{@code PluginManifest} 曾用同一个 JSON 键
 * {@code "version"} 既判协议版本又当插件版本，导致 {@code getVersion()} 恒为 "1"。
 */
@RunWith(AndroidJUnit4.class)
public class PluginEndToEndDeviceTest {

    private static final String ID = "e2e.probe";
    private static final String PLUGIN_VERSION = "e2e-1";

    private File sourceDir;

    @Before
    public void setUp() {
        PluginController.reset();
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        sourceDir = new File(Path.cache(), "e2e-plugin-src");
        deleteQuietly(sourceDir);
        assertTrue(sourceDir.mkdirs());
        write(new File(sourceDir, "manifest.json"), String.format(java.util.Locale.US, """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "%s",
                  "name": "E2E Probe",
                  "pluginVersion": "%s",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "main.js", "timeout": 15 },
                  "events": ["ui.contentShown"],
                  "permissions": ["plugin.events"]
                }
                """, ID, PLUGIN_VERSION));
        write(new File(sourceDir, "main.js"), """
                webhtv.on('ui.contentShown', function (payload) {
                  webhtv.log('e2e content shown', payload);
                  return 'seen';
                });
                webhtv.action('e2e.probe', function (payload) {
                  return 'pong:' + payload;
                });
                """);
    }

    @After
    public void tearDown() {
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        PluginBootstrap.reloadAll();
        PluginController.reset();
        deleteQuietly(sourceDir);
    }

    @Test
    public void importedPluginRunsRealScriptEndToEnd() {
        // 1. 整包导入：源目录 → 私有目录，entry 改写成复制后的绝对路径
        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(sourceDir);
        assertTrue("import failed: " + result.error(), result.isSuccess());
        assertNotNull(result.entry());
        assertEquals(ID, result.entry().id());
        // 插件版本必须来自 pluginVersion，而不是协议版本 "1"
        assertEquals(PLUGIN_VERSION, result.entry().version());
        assertTrue("imported plugins are enabled by default", result.entry().enabled());
        assertTrue(PluginPackageImporter.isOwnedDirectory(result.entry().directory()));
        assertTrue(new File(result.entry().directory(), "main.js").isFile());

        // 2. 配置真的落盘了（不是只活在内存里）
        assertNotNull(PluginSourceConfig.get().find(ID));

        // 3. 装载：注册运行时 + 按配置读清单 + QuickJS 求值
        assertTrue("reload should load the imported plugin", PluginBootstrap.reloadAll());
        PluginHost host = PluginController.get().host();
        assertTrue(host.isLoaded(ID));

        // 4. 脚本真被执行：动作经宿主路由进 QuickJS 并带回返回值
        assertEquals("pong:{\"x\":1}", host.action(ID, "e2e.probe", "{\"x\":1}"));
    }

    @Test
    public void disablingPluginDropsItFromTheNextReload() {
        assertTrue(PluginPackageImporter.importFrom(sourceDir).isSuccess());
        assertTrue(PluginBootstrap.reloadAll());
        assertTrue(PluginController.get().host().isLoaded(ID));

        PluginSourceConfig.get().setEnabled(ID, false);
        PluginSourceConfig.get().save();
        PluginBootstrap.reloadAll();

        assertFalse(PluginController.get().host().isLoaded(ID));
        assertNull(PluginController.get().host().action(ID, "e2e.probe", "{}"));
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
