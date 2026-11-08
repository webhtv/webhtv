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
 * 持久化配置测试。
 *
 * <p>覆盖三件事：**往返存储**（save/读回后内容一致）、**稳定顺序与覆盖语义**
 * （同 id 不出现两条）、以及 **{@link PluginSourceConfig#toManifests()} 的跳过行为**
 * （缺清单、空清单、非法清单都只跳过自己，不拖累整批）。
 *
 * <p>存储用内存实现（{@link PluginSourceConfig.Store}）：JVM 单测没有 Context，
 * {@code Prefers} 会在桩代码上抛异常。
 */
public class PluginSourceConfigTest {

    private File workDir;
    private MemoryStore store;
    private PluginSourceConfig config;

    @Before
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("plugin-config").toFile();
        store = new MemoryStore();
        config = PluginSourceConfig.useStoreForTest(store);
    }

    @After
    public void tearDown() {
        PluginSourceConfig.useStoreForTest(null);
        delete(workDir);
    }

    @Test
    public void defaultsToEmptyAndNeverNull() {
        assertNotNull(PluginSourceConfig.get());
        assertTrue(config.entries().isEmpty());
        assertTrue(config.enabledEntries().isEmpty());
        assertNull(config.find("nope"));
        assertTrue(config.toManifests().isEmpty());
    }

    @Test
    public void putOverwritesByIdAndKeepsInsertionOrder() {
        config.put(entry("a", true, 1L));
        config.put(entry("b", true, 2L));
        config.put(entry("a", false, 3L));

        List<PluginSourceConfig.Entry> entries = config.entries();
        assertEquals(2, entries.size());
        assertEquals("a", entries.get(0).id());
        assertEquals("b", entries.get(1).id());
        assertFalse("second put for the same id must win", entries.get(0).enabled());
        assertEquals(3L, entries.get(0).importedAt());
        assertEquals(1, config.enabledEntries().size());
    }

    @Test
    public void entriesAreImmutableSnapshots() {
        config.put(entry("a", true, 1L));
        List<PluginSourceConfig.Entry> entries = config.entries();
        try {
            entries.add(entry("b", true, 2L));
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("entries() must be immutable");
    }

    @Test
    public void rejectsUnsafeIds() {
        // 纯点 id 会把写入点抬到 plugins 的父目录，必须在入口就丢掉。
        config.put(new PluginSourceConfig.Entry("..", "..", "1", "js", "/tmp/x", true, 0L));
        config.put(new PluginSourceConfig.Entry("", "empty", "1", "js", "/tmp/x", true, 0L));
        config.put(new PluginSourceConfig.Entry("a/b", "slash", "1", "js", "/tmp/x", true, 0L));

        assertTrue(config.entries().isEmpty());
    }

    @Test
    public void setEnabledAndRemoveWork() {
        config.put(entry("a", true, 1L));
        config.setEnabled("a", false);
        assertFalse(config.find("a").enabled());
        assertEquals(0, config.enabledEntries().size());

        config.setEnabled("does.not.exist", true);
        assertEquals("unknown id must be ignored", 1, config.entries().size());

        config.remove("a");
        assertNull(config.find("a"));
        assertTrue(config.entries().isEmpty());
    }

    /** save → 新实例读回：这是"重启后插件还在"的唯一证据。 */
    @Test
    public void saveRoundTripsThroughStore() {
        config.put(new PluginSourceConfig.Entry("demo.one", "一号", "1.2.3", "js", workDir.getAbsolutePath(), true, 111L));
        config.put(new PluginSourceConfig.Entry("demo.two", "二号", "0.9", "html", workDir.getAbsolutePath(), false, 222L));
        config.save();
        assertFalse("save must actually write", store.json.isEmpty());

        PluginSourceConfig reloaded = PluginSourceConfig.useStoreForTest(store);

        List<PluginSourceConfig.Entry> entries = reloaded.entries();
        assertEquals(2, entries.size());
        PluginSourceConfig.Entry one = reloaded.find("demo.one");
        assertNotNull(one);
        assertEquals("一号", one.name());
        assertEquals("1.2.3", one.version());
        assertEquals("js", one.runtimeApi());
        assertEquals(workDir.getAbsolutePath(), one.directory());
        assertTrue(one.enabled());
        assertEquals(111L, one.importedAt());
        assertFalse(reloaded.find("demo.two").enabled());
        assertEquals(1, reloaded.enabledEntries().size());
    }

    @Test
    public void survivesCorruptStorage() {
        store.json = "{ this is not json";
        PluginSourceConfig broken = PluginSourceConfig.useStoreForTest(store);
        assertTrue("corrupt storage must degrade to empty, not throw", broken.entries().isEmpty());

        store.json = "";
        assertTrue(PluginSourceConfig.useStoreForTest(store).entries().isEmpty());
    }

    @Test
    public void dropsDirtyEntriesOnLoad() {
        store.json = "{\"version\":1,\"entries\":["
                + "{\"id\":\"..\",\"directory\":\"/tmp/x\",\"enabled\":true},"
                + "{\"id\":\"ok\",\"directory\":\"/tmp/y\",\"enabled\":true},"
                + "{\"id\":\"no.dir\",\"enabled\":true}]}";

        PluginSourceConfig loaded = PluginSourceConfig.useStoreForTest(store);

        assertEquals("only the safe entry may survive", 1, loaded.entries().size());
        assertNotNull(loaded.find("ok"));
        assertNull(loaded.find(".."));
        assertNull(loaded.find("no.dir"));
    }

    @Test
    public void toManifestsKeepsOnlyEnabledAndSkipsBadPackages() throws Exception {
        File enabledDir = packageDir("p-enabled", manifestJson("demo.on", "hello.js"));
        write(new File(enabledDir, "hello.js"), "1;");
        File disabledDir = packageDir("p-disabled", manifestJson("demo.off", "hello.js"));
        write(new File(disabledDir, "hello.js"), "1;");
        // 目录存在但没有任何清单文件：Path.read 返回 ""，必须跳过。
        File missingDir = packageDir("p-missing", null);
        // 清单存在但非法。
        File invalidDir = packageDir("p-invalid", "{\"protocol\":\"other\"}");
        // 目录根本不存在。
        File absentDir = new File(workDir, "p-absent");

        config.put(new PluginSourceConfig.Entry("demo.on", "on", "1", "js", enabledDir.getAbsolutePath(), true, 1L));
        config.put(new PluginSourceConfig.Entry("demo.off", "off", "1", "js", disabledDir.getAbsolutePath(), false, 2L));
        config.put(new PluginSourceConfig.Entry("demo.missing", "missing", "1", "js", missingDir.getAbsolutePath(), true, 3L));
        config.put(new PluginSourceConfig.Entry("demo.invalid", "invalid", "1", "js", invalidDir.getAbsolutePath(), true, 4L));
        config.put(new PluginSourceConfig.Entry("demo.absent", "absent", "1", "js", absentDir.getAbsolutePath(), true, 5L));

        List<PluginManifest> manifests = config.toManifests();

        assertEquals("only the healthy enabled package may load", 1, manifests.size());
        assertEquals("demo.on", manifests.get(0).getId());
    }

    @Test
    public void safeIdRuleMatchesDirectoryNameNeeds() {
        assertTrue(PluginSourceConfig.isSafeId("example.hello"));
        assertTrue(PluginSourceConfig.isSafeId("a"));
        assertTrue(PluginSourceConfig.isSafeId("demo_1-x"));
        assertFalse(PluginSourceConfig.isSafeId(null));
        assertFalse(PluginSourceConfig.isSafeId(""));
        assertFalse(PluginSourceConfig.isSafeId(".."));
        assertFalse("dots alone are a traversal", PluginSourceConfig.isSafeId("..."));
        assertFalse(PluginSourceConfig.isSafeId("../x"));
        assertFalse(PluginSourceConfig.isSafeId("a/b"));
        assertFalse(PluginSourceConfig.isSafeId("a b"));
    }

    // ---- helpers ----

    private PluginSourceConfig.Entry entry(String id, boolean enabled, long importedAt) {
        return new PluginSourceConfig.Entry(id, id, "1", "js", new File(workDir, id).getAbsolutePath(), enabled, importedAt);
    }

    private File packageDir(String name, String manifestJson) throws Exception {
        File dir = new File(workDir, name);
        assertTrue(dir.mkdirs());
        if (manifestJson != null) write(new File(dir, "manifest.json"), manifestJson);
        return dir;
    }

    private static String manifestJson(String id, String entry) {
        return "{\"protocol\":\"webhtv-plugin\",\"version\":1,\"id\":\"" + id + "\",\"name\":\"" + id
                + "\",\"sdk\":1,\"runtime\":{\"api\":\"js\",\"entry\":\"" + entry + "\"},"
                + "\"events\":[\"ui.contentShown\"],\"permissions\":[\"plugin.events\"]}";
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

    /** 内存版存储：单测里没有 Context。 */
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
