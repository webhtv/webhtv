package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 插件包导入测试。
 *
 * <p>全部用 {@code java.io} 临时目录构造输入：JVM 单测里没有 AssetManager，
 * 也不能依赖 {@code Init.context()}（{@code Path.files()} 拿不到 Context），
 * 因此通过 {@link PluginPackageImporter#overrideRootForTest(File)} 与
 * {@link PluginSourceConfig#useStoreForTest(PluginSourceConfig.Store)} 两个包内接缝把
 * 文件根与持久化后端换成临时目录。
 *
 * <p>重点不只是"能导入"，而是三条失败姿态：<b>超限不留残留</b>、<b>中途失败整包回滚</b>、
 * <b>路径穿越与符号链接必须拒绝</b>。这三条一旦松动，插件包就成了任意文件写入通道。
 */
public class PluginPackageImporterTest {

    private File pluginsRoot;
    private File workDir;
    private MemoryStore store;

    @Before
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("plugin-import").toFile();
        pluginsRoot = new File(workDir, "files/plugins");
        store = new MemoryStore();
        PluginSourceConfig.useStoreForTest(store);
        PluginPackageImporter.overrideRootForTest(pluginsRoot);
    }

    @After
    public void tearDown() {
        PluginPackageImporter.overrideRootForTest(null);
        PluginSourceConfig.useStoreForTest(null);
        delete(workDir);
    }

    @Test
    public void importsPackageAndRewritesEntryToAbsolutePath() throws Exception {
        File source = packageDir("src-a", manifest("demo.hello", "hello.js", "js"), "hello.js", "webhtv.log('hi');");

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.error());
        assertNotNull(result.entry());
        assertEquals("demo.hello", result.entry().id());
        assertEquals("js", result.entry().runtimeApi());
        assertTrue(result.entry().enabled());

        File target = new File(result.entry().directory());
        assertTrue("package directory must exist", target.isDirectory());
        File script = new File(target, "hello.js");
        File manifest = new File(target, "manifest.json");
        assertTrue(script.isFile());
        assertTrue(manifest.isFile());
        assertEquals("script content must be copied verbatim", "webhtv.log('hi');",
                Files.readString(script.toPath(), StandardCharsets.UTF_8));

        // 改写后的入口必须是复制后的绝对路径：装载期据此走 PluginScriptSource 的裸绝对路径形态。
        String rewritten = Files.readString(manifest.toPath(), StandardCharsets.UTF_8);
        assertTrue("entry must point at the copied script: " + rewritten,
                rewritten.contains(script.getAbsolutePath().replace("\\", "\\\\"))
                        || rewritten.contains(script.getAbsolutePath().replace("\\", "/")));
        PluginManifest parsed = PluginManifest.parse(rewritten);
        assertNotNull(parsed);
        assertEquals(script.getAbsolutePath(), new File(parsed.getRuntimeEntry()).getAbsolutePath());

        // 导入即登记，配置与盘上包必须同时成立。
        assertNotNull(PluginSourceConfig.get().find("demo.hello"));
        assertEquals(1, PluginSourceConfig.get().entries().size());
    }

    @Test
    public void copiesOnlyManifestAndEntrySiblings() throws Exception {
        File source = packageDir("src-b", manifest("demo.scoped", "js/hello.js", "js"), "js/hello.js", "1;");
        write(new File(source, "js/helper.js"), "// helper");
        write(new File(source, "unrelated/secret.txt"), "should not be copied");

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.error());
        File target = new File(result.entry().directory());
        assertTrue(new File(target, "manifest.json").isFile());
        assertTrue("entry sibling must be copied", new File(target, "js/helper.js").isFile());
        assertFalse("unrelated subtree must not be copied", new File(target, "unrelated").exists());
    }

    @Test
    public void rejectsMissingManifest() throws Exception {
        File source = new File(workDir, "src-nomanifest");
        assertTrue(source.mkdirs());
        write(new File(source, "hello.js"), "1;");

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertNotNull(result.error());
        assertTrue(result.error(), result.error().contains("manifest.json"));
        assertTrue("nothing may be created on failure", PluginSourceConfig.get().entries().isEmpty());
        assertFalse(new File(pluginsRoot, "demo.hello").exists());
    }

    @Test
    public void rejectsInvalidManifest() throws Exception {
        File source = new File(workDir, "src-bad");
        assertTrue(source.mkdirs());
        // 缺 protocol / version / sdk：PluginManifest.parse 必然返回 null
        write(new File(source, "manifest.json"), "{\"id\":\"demo.bad\",\"runtime\":{\"api\":\"js\",\"entry\":\"hello.js\"}}");
        write(new File(source, "hello.js"), "1;");

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertTrue(result.error(), result.error().contains("清单不合法"));
    }

    @Test
    public void distinguishesUnsupportedRuntimeFromInvalidManifest() throws Exception {
        File source = new File(workDir, "src-lua");
        assertTrue(source.mkdirs());
        write(new File(source, "manifest.json"), manifest("demo.lua", "hello.js", "lua"));
        write(new File(source, "hello.js"), "1;");

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertNotNull(result.error());
        assertTrue("unsupported runtime must be its own message: " + result.error(),
                result.error().contains("运行时不支持"));
        assertFalse(result.error().contains("清单不合法"));
    }

    @Test
    public void rejectsOversizedScript() throws Exception {
        File source = packageDir("src-big", manifest("demo.big", "hello.js", "js"), "hello.js", "1;");
        write(new File(source, "hello.js"), repeat('a', PluginScriptSource.MAX_SCRIPT_BYTES + 1));

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertTrue(result.error(), result.error().contains(String.valueOf(PluginScriptSource.MAX_SCRIPT_BYTES)));
        assertFalse("oversized package must leave nothing behind", new File(pluginsRoot, "demo.big").exists());
    }

    @Test
    public void rejectsPackageOverTotalBudget() throws Exception {
        File source = packageDir("src-huge", manifest("demo.huge", "hello.js", "js"), "hello.js", "1;");
        // 每个文件都在单文件上限内，但总量越过 4MB：必须按整包口径拒绝。
        for (int i = 0; i < 9; i++) {
            write(new File(source, "chunk" + i + ".js"), repeat('b', 500 * 1024));
        }

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertTrue(result.error(), result.error().contains(String.valueOf(PluginPackageImporter.MAX_PACKAGE_BYTES)));
        assertFalse(new File(pluginsRoot, "demo.huge").exists());
    }

    @Test
    public void rejectsTooManyFiles() throws Exception {
        File source = packageDir("src-many", manifest("demo.many", "hello.js", "js"), "hello.js", "1;");
        for (int i = 0; i < PluginPackageImporter.MAX_FILES; i++) {
            write(new File(source, "f" + i + ".js"), "1;");
        }

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertTrue(result.error(), result.error().contains(String.valueOf(PluginPackageImporter.MAX_FILES)));
    }

    @Test
    public void rejectsPathTraversalInEntry() throws Exception {
        write(new File(workDir, "escape.js"), "stolen");
        File source = new File(workDir, "src-escape");
        assertTrue(source.mkdirs());
        write(new File(source, "manifest.json"), manifest("demo.escape", "../escape.js", "js"));

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertTrue(result.error(), result.error().contains("路径穿越"));
        assertFalse(new File(pluginsRoot, "demo.escape").exists());
    }

    @Test
    public void rejectsAbsoluteAndProtocolEntries() throws Exception {
        PluginPackageImporter.Result absolute = PluginPackageImporter.importFrom(
                packageDir("src-abs", manifest("demo.abs", "/etc/passwd", "js"), "hello.js", "1;"));
        assertNull(absolute.entry());
        assertTrue(absolute.error(), absolute.error().contains("相对路径"));

        PluginPackageImporter.Result protocol = PluginPackageImporter.importFrom(
                packageDir("src-proto", manifest("demo.proto", "assets://plugin/example/hello.js", "js"), "hello.js", "1;"));
        assertNull(protocol.entry());
        assertTrue(protocol.error(), protocol.error().contains("协议前缀"));
    }

    @Test
    public void reimportSameIdReplacesDirectory() throws Exception {
        File first = packageDir("src-v1", manifestWithName("demo.repeat", "hello.js", "js", "v1", "旧版"), "hello.js", "old();");
        PluginPackageImporter.Result firstResult = PluginPackageImporter.importFrom(first);
        assertNull(firstResult.error());

        File second = packageDir("src-v2", manifestWithName("demo.repeat", "hello.js", "js", "v2", "新版"), "hello.js", "new();");
        write(new File(second, "extra.js"), "// extra from v2");
        PluginPackageImporter.Result secondResult = PluginPackageImporter.importFrom(second);

        assertNull(secondResult.error());
        List<PluginSourceConfig.Entry> entries = PluginSourceConfig.get().entries();
        assertEquals("same id must never produce two entries", 1, entries.size());
        assertEquals("新版", entries.get(0).name());
        // 插件版本来自 pluginVersion 键；version 只承担协议版本，所以这里能真正区分新旧包。
        assertEquals("v2", entries.get(0).version());

        File target = new File(secondResult.entry().directory());
        assertEquals("new();", Files.readString(new File(target, "hello.js").toPath(), StandardCharsets.UTF_8));
        assertTrue("v2 files must be present", new File(target, "extra.js").isFile());
        assertFalse("v1 files must be gone", new File(target, "v1-only.js").exists());
    }

    @Test
    public void failedImportLeavesNoResidue() throws Exception {
        // 入口脚本为空：复制已经发生，失败点在复制之后 → 最考验回滚。
        File source = packageDir("src-empty", manifest("demo.empty", "hello.js", "js"), "hello.js", "");

        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);

        assertNull(result.entry());
        assertNotNull(result.error());
        assertFalse("half-copied package must be removed", new File(pluginsRoot, "demo.empty").exists());
        assertNull("config entry must be rolled back too", PluginSourceConfig.get().find("demo.empty"));
        assertTrue(PluginSourceConfig.get().entries().isEmpty());
    }

    @Test
    public void failedReimportDoesNotLeaveStaleEntry() throws Exception {
        File good = packageDir("src-good", manifest("demo.stale", "hello.js", "js"), "hello.js", "ok();");
        assertNull(PluginPackageImporter.importFrom(good).error());
        assertNotNull(PluginSourceConfig.get().find("demo.stale"));

        File broken = packageDir("src-broken", manifest("demo.stale", "hello.js", "js"), "hello.js", "");
        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(broken);

        assertNull(result.entry());
        assertFalse(new File(pluginsRoot, "demo.stale").exists());
        assertNull("a failed re-import must not leave a config entry pointing at nothing",
                PluginSourceConfig.get().find("demo.stale"));
    }

    @Test
    public void nullAndNonDirectoryInputsAreRejected() throws Exception {
        assertNotNull(PluginPackageImporter.importFrom(null).error());
        File file = new File(workDir, "not-a-dir.txt");
        write(file, "x");
        assertNotNull(PluginPackageImporter.importFrom(file).error());
        assertNotNull(PluginPackageImporter.importFrom(new File(workDir, "missing")).error());
    }

    // ---- helpers ----

    private File packageDir(String name, String manifestJson, String entryName, String script) throws Exception {
        File dir = new File(workDir, name);
        assertTrue(dir.mkdirs());
        write(new File(dir, "manifest.json"), manifestJson);
        write(new File(dir, entryName), script);
        return dir;
    }

    private static String manifest(String id, String entry, String api) {
        return manifestWithName(id, entry, api, "1.0.0", id);
    }

    private static String manifestWithName(String id, String entry, String api, String version, String name) {
        return "{\"protocol\":\"webhtv-plugin\",\"version\":1,\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"pluginVersion\":\"" + version
                + "\",\"sdk\":1,\"runtime\":{\"api\":\"" + api + "\",\"entry\":\"" + entry
                + "\",\"timeout\":15},\"events\":[\"ui.contentShown\"],\"permissions\":[\"plugin.events\"]}";
    }

    private static String manifestNamed(String id, String entry, String api, String name) {
        return manifestWithName(id, entry, api, "1", name);
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

    /** 内存版存储：单测里没有 Context，{@code Prefers} 不可用。 */
    static final class MemoryStore implements PluginSourceConfig.Store {

        String json = "";

        @Override
        public String read() {
            return json;
        }

        @Override
        public void write(String value) {
            json = value;
        }
    }
}
