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
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 插件包索引测试（外加 {@code importBundle} 的端到端用例）。
 *
 * <p>所有输入都是本测试现造的 zip，不依赖任何真实下载内容：索引的契约是关于"包里有什么"，
 * 而真实 GitHub 包的形态（{@code repo-main/} 包装层、{@code __MACOSX/} 残留、多插件共存）
 * 在这里用几行就能精确复现，比抓一个真包更可读也更稳定。
 *
 * <p>{@code importBundle} 的用例也放在本文件：本次改动的文件预算只允许两个新测试文件，
 * 而"整批导入"与"索引"共用同一批夹具（zip 构造 + 插件目录覆盖），合在一处比拆开更省样板。
 *
 * <p>三类断言贯穿全文：<b>坏输入只降级不抛异常</b>、<b>越界条目名一律不写盘</b>、
 * <b>临时解压目录用完即删</b>。
 */
public class PluginBundleIndexTest {

    private File workDir;
    private File pluginsRoot;

    @Before
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("plugin-bundle").toFile();
        pluginsRoot = new File(workDir, "files/plugins");
        PluginSourceConfig.useStoreForTest(new PluginPackageImporterTest.MemoryStore());
        PluginPackageImporter.overrideRootForTest(pluginsRoot);
    }

    @After
    public void tearDown() {
        PluginPackageImporter.overrideRootForTest(null);
        PluginSourceConfig.useStoreForTest(null);
        delete(workDir);
    }

    // ---- 索引：发现 ----

    @Test
    public void findsPluginAtZipRoot() throws Exception {
        File zip = zip("root.zip", "manifest.json", manifest("demo.root", "hello.js", "js"),
                "hello.js", "webhtv.log('hi');");

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertNull(result.error());
        assertTrue(result.isSuccess());
        assertEquals(1, result.candidates().size());
        PluginBundleIndex.Candidate candidate = result.candidates().get(0);
        assertEquals("根目录插件必须报空串 entryPath，而不是 \"/\" 或 \".\"", "", candidate.entryPath());
        assertEquals("demo.root", candidate.id());
        assertEquals("js", candidate.runtimeApi());
    }

    @Test
    public void findsPluginInSubdirectory() throws Exception {
        File zip = zip("sub.zip", "douban/manifest.json", manifest("demo.douban", "hello.js", "js"),
                "douban/hello.js", "1;");

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(1, result.candidates().size());
        assertEquals("douban", result.candidates().get(0).entryPath());
    }

    @Test
    public void findsPluginInsideCodeloadWrapper() throws Exception {
        // GitHub 源码包的典型形态：整棵树被包在 repo-main/ 之下。
        File zip = zip("codeload.zip",
                "repo-main/README.md", "# readme",
                "repo-main/douban/manifest.json", manifest("demo.douban", "hello.js", "js"),
                "repo-main/douban/hello.js", "1;");

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(1, result.candidates().size());
        assertEquals("包装层不得被剥掉：entryPath 必须指向包内真实位置",
                "repo-main/douban", result.candidates().get(0).entryPath());
        assertEquals("demo.douban", result.candidates().get(0).id());
    }

    @Test
    public void sortsCandidatesByEntryPath() throws Exception {
        File zip = zip("three.zip",
                "c/manifest.json", manifest("demo.c", "hello.js", "js"),
                "a/manifest.json", manifest("demo.a", "hello.js", "js"),
                "b/manifest.json", manifest("demo.b", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        List<String> paths = new ArrayList<>();
        for (PluginBundleIndex.Candidate candidate : result.candidates()) paths.add(candidate.entryPath());
        assertEquals(Arrays.asList("a", "b", "c"), paths);
    }

    @Test
    public void recordsManifestMetadata() throws Exception {
        File zip = zip("meta.zip", "p/manifest.json",
                manifest("demo.meta", "豆瓣", "2.3.4", "hello.js", "js",
                        "[\"" + PluginCapabilityRegistry.PERMISSION_EVENTS + "\",\""
                                + PluginCapabilityRegistry.PERMISSION_NET_REQUEST + "\"]"),
                "p/hello.js", "1;");

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(1, result.candidates().size());
        PluginBundleIndex.Candidate candidate = result.candidates().get(0);
        assertEquals("豆瓣", candidate.name());
        assertEquals("2.3.4", candidate.version());
        // 权限按能力注册表过滤后原样透出给界面，界面据此展示"这个插件要什么"。
        assertTrue(candidate.permissions().contains(PluginCapabilityRegistry.PERMISSION_EVENTS));
        assertTrue(candidate.permissions().contains(PluginCapabilityRegistry.PERMISSION_NET_REQUEST));
    }

    // ---- 索引：逐个跳过，而不是整体失败 ----

    @Test
    public void keepsValidCandidateBesideUnparseableManifest() throws Exception {
        File zip = zip("mixed.zip",
                "broken/manifest.json", "{ not json at all",
                "good/manifest.json", manifest("demo.good", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertNull("一份坏清单不该让整包变成失败", result.error());
        assertEquals(1, result.candidates().size());
        assertEquals("demo.good", result.candidates().get(0).id());
    }

    @Test
    public void skipsUnsupportedRuntime() throws Exception {
        File zip = zip("lua.zip",
                "lua/manifest.json", manifest("demo.lua", "hello.js", "lua"),
                "js/manifest.json", manifest("demo.js", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals("只应留下 js 插件", 1, result.candidates().size());
        assertEquals("demo.js", result.candidates().get(0).id());
    }

    @Test
    public void deduplicatesById() throws Exception {
        File zip = zip("dup.zip",
                "b/manifest.json", manifest("demo.dup", "hello.js", "js"),
                "a/manifest.json", manifest("demo.dup", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals("同一个 id 只能给出一个可导入候选", 1, result.candidates().size());
        assertEquals("保留排序在前的那个（a < b）", "a", result.candidates().get(0).entryPath());
    }

    @Test
    public void skipsTraversalEntries() throws Exception {
        File zip = zip("evil.zip",
                "../evil/manifest.json", manifest("demo.evil", "hello.js", "js"),
                "ok/../../evil2/manifest.json", manifest("demo.evil2", "hello.js", "js"),
                "good/manifest.json", manifest("demo.good", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(1, result.candidates().size());
        assertEquals("demo.good", result.candidates().get(0).id());
        assertFalse(result.candidates().get(0).entryPath().contains(".."));
    }

    @Test
    public void ignoresGitAndMacosxEntries() throws Exception {
        File zip = zip("noise.zip",
                ".git/manifest.json", manifest("demo.git", "hello.js", "js"),
                "__MACOSX/._x/manifest.json", manifest("demo.mac", "hello.js", "js"),
                "good/.hidden/manifest.json", manifest("demo.hidden", "hello.js", "js"),
                "good/manifest.json", manifest("demo.good", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(1, result.candidates().size());
        assertEquals("demo.good", result.candidates().get(0).id());
    }

    @Test
    public void skipsUnsafeEntryNames() throws Exception {
        File zip = zip("unsafe.zip",
                "/abs/manifest.json", manifest("demo.abs", "hello.js", "js"),
                "C:/drive/manifest.json", manifest("demo.drive", "hello.js", "js"),
                "win\\path\\manifest.json", manifest("demo.win", "hello.js", "js"),
                "good/manifest.json", manifest("demo.good", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(1, result.candidates().size());
        assertEquals("demo.good", result.candidates().get(0).id());
    }

    @Test
    public void skipsOversizedManifest() throws Exception {
        // 50KB 的合法 JSON 后面再补空白，越过 64KB 上限：必须在读取阶段就放弃，而不是解析。
        StringBuilder huge = new StringBuilder(manifest("demo.huge", "hello.js", "js"));
        while (huge.length() <= PluginManifest.MAX_MANIFEST_BYTES) huge.append(' ');
        File zip = zip("huge.zip",
                "huge/manifest.json", huge.toString(),
                "good/manifest.json", manifest("demo.good", "hello.js", "js"));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(1, result.candidates().size());
        assertEquals("demo.good", result.candidates().get(0).id());
    }

    @Test
    public void capsCandidateCount() throws Exception {
        List<String> entries = new ArrayList<>();
        int total = PluginBundleIndex.MAX_CANDIDATES + 3;
        for (int index = 0; index < total; index++) {
            String dir = String.format("p%03d", index);
            entries.add(dir + "/manifest.json");
            entries.add(manifest("demo." + dir, "hello.js", "js"));
        }
        File zip = zip("many.zip", entries.toArray(new String[0]));

        PluginBundleIndex.Result result = PluginBundleIndex.inspect(zip);

        assertEquals(PluginBundleIndex.MAX_CANDIDATES, result.candidates().size());
        assertEquals("截断必须发生在排序之后，返回的应是最前面的那些", "p000",
                result.candidates().get(0).entryPath());
    }

    // ---- 索引：坏输入只给原因，不抛异常 ----

    @Test
    public void reportsReadableErrorForBadInput() throws Exception {
        File notZip = new File(workDir, "not-a-zip.zip");
        Files.writeString(notZip.toPath(), "this is definitely not a zip", StandardCharsets.UTF_8);
        File missing = new File(workDir, "missing.zip");
        File directory = new File(workDir, "a-directory.zip");
        assertTrue(directory.mkdirs());

        PluginBundleIndex.Result broken = PluginBundleIndex.inspect(notZip);
        assertFalse(broken.isSuccess());
        assertNotNull(broken.error());
        assertFalse(broken.error(), broken.error().isEmpty());
        assertTrue("失败时不该给出半个候选列表", broken.candidates().isEmpty());

        assertFalse(PluginBundleIndex.inspect(missing).isSuccess());
        assertFalse(PluginBundleIndex.inspect(directory).isSuccess());
        assertFalse(PluginBundleIndex.inspect(null).isSuccess());
    }

    // ---- importBundle：整批导入 ----

    @Test
    public void importBundleInstallsOnlySelectedCandidates() throws Exception {
        File zip = zip("bundle.zip",
                "a/manifest.json", manifest("demo.a", "hello.js", "js"),
                "a/hello.js", "webhtv.log('a');",
                "b/manifest.json", manifest("demo.b", "hello.js", "js"),
                "b/hello.js", "webhtv.log('b');");
        PluginBundleIndex.Result indexed = PluginBundleIndex.inspect(zip);
        assertEquals(2, indexed.candidates().size());

        PluginBundleIndex.Candidate selected = null;
        for (PluginBundleIndex.Candidate candidate : indexed.candidates()) {
            if ("demo.a".equals(candidate.id())) selected = candidate;
        }
        assertNotNull(selected);

        PluginPackageImporter.BundleResult result = PluginPackageImporter.importBundle(zip, List.of(selected));

        assertNull(result.error());
        assertTrue(result.isSuccess());
        assertEquals(1, result.items().size());
        PluginPackageImporter.ItemResult item = result.items().get(0);
        assertNull(item.error());
        assertNotNull(item.entry());
        assertEquals("demo.a", item.entry().id());

        assertTrue("选中的插件必须真的落到插件根目录", new File(pluginsRoot, "demo.a/manifest.json").isFile());
        assertFalse("未选中的插件一个字节都不该写盘", new File(pluginsRoot, "demo.b").exists());
        assertNoTempLeftover();
    }

    @Test
    public void importBundleKeepsGoingAfterOneItemFails() throws Exception {
        File zip = zip("partial.zip",
                "good/manifest.json", manifest("demo.good", "hello.js", "js"),
                "good/hello.js", "1;",
                "empty/manifest.json", manifest("demo.empty", "hello.js", "js"),
                "empty/hello.js", "");
        PluginBundleIndex.Result indexed = PluginBundleIndex.inspect(zip);

        // 手工构造一个指向不存在目录的候选：模拟"界面传来的选择与包内容已经不一致"。
        List<PluginBundleIndex.Candidate> selected = new ArrayList<>();
        selected.add(new PluginBundleIndex.Candidate("missing", "demo.missing", "demo.missing", "1", "js",
                java.util.Set.of()));
        selected.add(candidateOf(indexed, "demo.empty"));
        selected.add(candidateOf(indexed, "demo.good"));

        PluginPackageImporter.BundleResult result = PluginPackageImporter.importBundle(zip, selected);

        assertNull("单项失败不代表整批失败", result.error());
        assertEquals(3, result.items().size());
        assertEquals("demo.missing", result.items().get(0).candidate().id());
        assertNotNull(result.items().get(0).error());
        assertNull(result.items().get(0).entry());

        assertEquals("demo.empty", result.items().get(1).candidate().id());
        assertNotNull("空入口脚本必须被 importFrom 拒掉", result.items().get(1).error());
        assertNull(result.items().get(1).entry());

        assertEquals("demo.good", result.items().get(2).candidate().id());
        assertNull(result.items().get(2).error());
        assertNotNull("前面的失败不该影响后面这一项", result.items().get(2).entry());
        assertTrue(new File(pluginsRoot, "demo.good/manifest.json").isFile());
        assertFalse("失败项不得留下半个包", new File(pluginsRoot, "demo.empty").exists());
        assertNoTempLeftover();
    }

    @Test
    public void importBundleRejectsBadInputWithoutThrowing() throws Exception {
        File zip = zip("ok.zip", "a/manifest.json", manifest("demo.a", "hello.js", "js"), "a/hello.js", "1;");
        File notZip = new File(workDir, "not-a-zip.zip");
        Files.writeString(notZip.toPath(), "nope", StandardCharsets.UTF_8);
        List<PluginBundleIndex.Candidate> some = PluginBundleIndex.inspect(zip).candidates();

        assertNotNull(PluginPackageImporter.importBundle(null, some).error());
        assertNotNull(PluginPackageImporter.importBundle(new File(workDir, "missing.zip"), some).error());
        assertNotNull(PluginPackageImporter.importBundle(notZip, some).error());
        assertNotNull(PluginPackageImporter.importBundle(zip, List.of()).error());
        assertNotNull(PluginPackageImporter.importBundle(zip, null).error());
        assertTrue("失败时不该声称有任何一项成功",
                PluginPackageImporter.importBundle(notZip, some).items().isEmpty());
        assertNoTempLeftover();
    }

    // ---- helpers ----

    private static PluginBundleIndex.Candidate candidateOf(PluginBundleIndex.Result indexed, String id) {
        for (PluginBundleIndex.Candidate candidate : indexed.candidates()) {
            if (id.equals(candidate.id())) return candidate;
        }
        throw new AssertionError("candidate not indexed: " + id);
    }

    private void assertNoTempLeftover() {
        File tempRoot = new File(workDir, "files/plugin-bundle-tmp");
        File[] children = tempRoot.listFiles();
        assertTrue("临时解压目录必须清空，实际残留：" + (children == null ? "none" : children.length),
                children == null || children.length == 0);
    }

    private File zip(String name, String... entries) throws Exception {
        File file = new File(workDir, name);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            // 逐对传入 path/content：把"条目名"直接暴露在用例里，越界名才能被写成字面量。
            for (int index = 0; index + 1 < entries.length; index += 2) {
                output.putNextEntry(new ZipEntry(entries[index]));
                output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return file;
    }

    private static String manifest(String id, String entry, String api) {
        return manifest(id, id, "1.0.0", entry, api, "[\"plugin.events\"]");
    }

    private static String manifest(String id, String name, String version, String entry, String api,
            String permissionsJson) {
        return "{\"protocol\":\"webhtv-plugin\",\"version\":1,\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"pluginVersion\":\"" + version + "\",\"sdk\":1,\"runtime\":{\"api\":\"" + api
                + "\",\"entry\":\"" + entry + "\",\"timeout\":15},\"permissions\":" + permissionsJson + "}";
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
