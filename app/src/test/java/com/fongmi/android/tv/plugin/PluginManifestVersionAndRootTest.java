package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/**
 * 插件身份与包目录归属测试。
 *
 * <p>两个独立主题放在一起，因为它们都是"界面看得见、但解析/导入类各自只占一半"的约定：
 * <b>清单里哪一个是插件版本</b>，以及<b>哪个目录才是本宿主拥有的插件包目录</b>。
 *
 * <p>版本这块的关键是不能让 {@code version}（协议版本）继续兼任插件版本：兼任会让插件版本
 * 恒等于协议版本号，升级与重导入在管理列表里完全看不出差别。回退分支也要测——旧清单不写
 * {@code pluginVersion}，界面依赖"版本列永不为空"。
 *
 * <p>目录这块的关键是"失败即拒绝"：界面拿它当删除前的自检，宽松的 startsWith 会让
 * {@code plugins-x} 这种兄弟目录通过判定。
 */
public class PluginManifestVersionAndRootTest {

    private File workDir;
    private File pluginsRoot;

    @Before
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("plugin-identity").toFile();
        pluginsRoot = new File(workDir, "files/plugins");
        PluginPackageImporter.overrideRootForTest(pluginsRoot);
    }

    @After
    public void tearDown() {
        PluginPackageImporter.overrideRootForTest(null);
        delete(workDir);
    }

    @Test
    public void pluginVersionKeyOverridesProtocolVersion() {
        PluginManifest manifest = PluginManifest.parse(valid().replace("\"sdk\": 1,", "\"sdk\": 1, \"pluginVersion\": \"2.3.0\","));

        assertNotNull(manifest);
        assertEquals("2.3.0", manifest.getVersion());
    }

    @Test
    public void legacyManifestFallsBackToProtocolVersion() {
        PluginManifest manifest = PluginManifest.parse(valid());

        assertNotNull(manifest);
        assertEquals(String.valueOf(PluginManifest.SCHEMA_VERSION), manifest.getVersion());
    }

    @Test
    public void blankPluginVersionFallsBackToProtocolVersion() {
        PluginManifest manifest = PluginManifest.parse(valid().replace("\"sdk\": 1,", "\"sdk\": 1, \"pluginVersion\": \"   \","));

        assertNotNull(manifest);
        assertEquals(String.valueOf(PluginManifest.SCHEMA_VERSION), manifest.getVersion());
    }

    @Test
    public void oversizedPluginVersionFallsBackToProtocolVersion() {
        // 64 > MAX_VERSION_LENGTH(32)：超长的 pluginVersion 视同没写，而不是让清单解析失败。
        String tooLong = "1".repeat(64);
        PluginManifest manifest = PluginManifest.parse(
                valid().replace("\"sdk\": 1,", "\"sdk\": 1, \"pluginVersion\": \"" + tooLong + "\","));

        assertNotNull(manifest);
        assertEquals(String.valueOf(PluginManifest.SCHEMA_VERSION), manifest.getVersion());
    }

    @Test
    public void pluginsRootHonorsTestOverride() throws Exception {
        assertEquals(pluginsRoot.getCanonicalPath(), PluginPackageImporter.pluginsRoot().getCanonicalPath());
    }

    @Test
    public void isOwnedDirectoryAcceptsChildOfRoot() throws Exception {
        File child = new File(pluginsRoot, "demo.hello");
        assertTrue(child.mkdirs());

        assertTrue(PluginPackageImporter.isOwnedDirectory(child.getAbsolutePath()));
    }

    @Test
    public void isOwnedDirectoryRejectsRootItselfAndSibling() throws Exception {
        assertTrue(pluginsRoot.mkdirs());
        File sibling = new File(workDir, "files/plugins-x");
        assertTrue(sibling.mkdirs());

        assertFalse(PluginPackageImporter.isOwnedDirectory(pluginsRoot.getAbsolutePath()));
        assertFalse(PluginPackageImporter.isOwnedDirectory(sibling.getAbsolutePath()));
    }

    @Test
    public void isOwnedDirectoryRejectsUnsafeInputs() {
        assertFalse(PluginPackageImporter.isOwnedDirectory(null));
        assertFalse(PluginPackageImporter.isOwnedDirectory(""));
        assertFalse(PluginPackageImporter.isOwnedDirectory("   "));
        assertFalse(PluginPackageImporter.isOwnedDirectory(
                new File(workDir, "elsewhere/demo.hello").getAbsolutePath()));
    }

    private static String valid() {
        return """
                {
                  "protocol": "webhtv-plugin",
                  "version": 1,
                  "id": "demo.hello",
                  "name": "演示",
                  "sdk": 1,
                  "runtime": { "api": "js", "entry": "hello.js", "timeout": 15 },
                  "events": ["ui.contentShown"],
                  "permissions": ["plugin.events"]
                }
                """;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
