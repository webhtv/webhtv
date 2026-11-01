package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置样本登记测试。
 *
 * <p>覆盖四件事：**首次启动真的把资产落盘并登记**（含清单 entry 被改写成绝对路径）、
 * **只做一次**（第二次启动不再碰盘）、**用户删掉之后不复活**、以及**已经存在的条目
 * 一行都不覆盖**。另外两条是失败路径：资产缺失与清单非法都不能记账——否则下一次启动
 * 就没有机会补上。
 *
 * <p>三个后端都注入内存实现：{@code Prefers} / {@code Asset} / {@code Path.files()} 在
 * JVM 单测里都没有 Context 可用。
 */
public class PluginSampleInstallerTest {

    /** 与 app/src/main/assets/plugin/douban/manifest.json 同形状（entry 故意保留 assets:// 形态）。 */
    private static final String MANIFEST = """
            {
              "protocol": "webhtv-plugin",
              "version": 1,
              "id": "douban.comments",
              "name": "豆瓣短评",
              "sdk": 1,
              "runtime": {
                "api": "js",
                "entry": "assets://plugin/douban/douban.js",
                "timeout": 20
              },
              "permissions": ["plugin.events", "plugin.data.episode", "net.request", "ui.panel"],
              "ui": [
                { "slot": "panel", "label": "豆瓣", "action": "douban.comments" }
              ]
            }
            """;

    private static final String SCRIPT = "webhtv.action('douban.comments', function () { return 'ok'; });";

    private File root;
    private File pluginsRoot;
    private MemoryStore store;
    private MapSource source;
    private PluginSourceConfig config;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("plugin-sample").toFile();
        pluginsRoot = new File(root, "plugins");
        store = new MemoryStore();
        source = new MapSource();
        source.put("plugin/douban/manifest.json", MANIFEST);
        source.put("plugin/douban/douban.js", SCRIPT);
        config = PluginSourceConfig.useStoreForTest(new MemoryConfigStore());
        PluginPackageImporter.overrideRootForTest(pluginsRoot);
        PluginSampleInstaller.useBackendsForTest(store, source);
    }

    @After
    public void tearDown() {
        PluginSampleInstaller.useBackendsForTest(null, null);
        PluginSourceConfig.useStoreForTest(null);
        PluginPackageImporter.overrideRootForTest(null);
        delete(root);
    }

    @Test
    public void firstRunCopiesTheSampleAndRegistersIt() throws Exception {
        assertEquals(1, PluginSampleInstaller.seedOnce());

        PluginSourceConfig.Entry entry = config.find("douban.comments");
        assertNotNull("首次启动必须把内置样本登记进配置", entry);
        assertEquals("豆瓣短评", entry.name());
        assertEquals("js", entry.runtimeApi());
        assertTrue("内置样本默认可用，否则等于没内置", entry.enabled());

        File directory = new File(entry.directory());
        assertTrue("样本目录必须在插件根目录下", directory.getAbsolutePath()
                .startsWith(pluginsRoot.getAbsolutePath()));
        File script = new File(directory, "douban.js");
        assertTrue(script.isFile());
        assertEquals(SCRIPT, Files.readString(script.toPath()));

        String manifestText = Files.readString(new File(directory, "manifest.json").toPath());
        JsonObject manifest = JsonParser.parseString(manifestText).getAsJsonObject();
        String entryPath = manifest.getAsJsonObject("runtime").get("entry").getAsString();
        assertEquals("清单 entry 必须被改写成脚本绝对路径，否则装载期读不到",
                script.getAbsolutePath(), entryPath);
        assertFalse("assets:// 前缀不能留到设备上的清单里", entryPath.startsWith("assets://"));
    }

    @Test
    public void secondRunDoesNotTouchTheDisk() throws Exception {
        PluginSampleInstaller.seedOnce();

        File script = new File(config.find("douban.comments").directory(), "douban.js");
        Files.writeString(script.toPath(), "TAMPERED");

        assertEquals("修订号已记下，第二次启动什么都不该做", 0, PluginSampleInstaller.seedOnce());
        assertEquals("TAMPERED", Files.readString(script.toPath()));
    }

    @Test
    public void sampleDeletedByTheUserIsNotResurrected() throws Exception {
        PluginSampleInstaller.seedOnce();
        String directory = config.find("douban.comments").directory();
        delete(new File(directory));
        config.remove("douban.comments");
        config.save();

        assertEquals(0, PluginSampleInstaller.seedOnce());
        assertNull("用户删掉的插件不能在下次启动时复活", config.find("douban.comments"));
        assertFalse(new File(directory).exists());
    }

    @Test
    public void existingRegistrationIsNeverOverwritten() throws Exception {
        config.put(new PluginSourceConfig.Entry("douban.comments", "用户自己的", "9", "js",
                new File(root, "user-picked").getAbsolutePath(), false, 123L));
        config.save();

        assertEquals(0, PluginSampleInstaller.seedOnce());

        PluginSourceConfig.Entry entry = config.find("douban.comments");
        assertEquals("用户自己的", entry.name());
        assertEquals(new File(root, "user-picked").getAbsolutePath(), entry.directory());
        assertFalse("同 id 已存在时连启用状态都不该被改写", entry.enabled());
        assertEquals(123L, entry.importedAt());
        assertFalse(new File(pluginsRoot, "douban.comments").exists());
    }

    @Test
    public void missingAssetIsSettledInsteadOfRetriedForever() {
        source.clear();

        assertEquals(0, PluginSampleInstaller.seedOnce());
        // 资产读空有两种含义，而 {@code Asset.read} 分不出来：这条样本不在本包里（永久），
        // 或读失败（瞬时）。资产内容随版本固定，重试没有意义，所以按"已处理"记账；
        // 将来真要补新样本，靠 SEED_REVISION +1 而不是靠反复重试。
        assertEquals(PluginSampleInstaller.SEED_REVISION, store.revision());
        assertNull(config.find("douban.comments"));
    }

    @Test
    public void unreadableManifestKeepsTheRevisionUnadvanced() {
        source.put("plugin/douban/manifest.json", "not a manifest");

        assertEquals(0, PluginSampleInstaller.seedOnce());
        assertEquals(0, store.revision());
        assertNull(config.find("douban.comments"));
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

    /** 记账后端：JVM 单测里的 {@code Prefers} 替身。 */
    private static final class MemoryStore implements PluginSampleInstaller.Store {

        private int revision;

        @Override
        public int revision() {
            return revision;
        }

        @Override
        public void mark(int revision) {
            this.revision = revision;
        }
    }

    /** 资产后端：缺失返回空串，与 {@code Asset.read} 的约定一致。 */
    private static final class MapSource implements PluginSampleInstaller.Source {

        private final Map<String, String> files = new HashMap<>();

        void put(String name, String text) {
            files.put(name, text);
        }

        void clear() {
            files.clear();
        }

        @Override
        public String read(String name) {
            String text = files.get(name);
            return text == null ? "" : text;
        }
    }

    /** 配置后端：与 {@link PluginSourceConfig.Store} 同形，但每次写都覆盖内存值。 */
    private static final class MemoryConfigStore implements PluginSourceConfig.Store {

        private String json = "";

        @Override
        public String read() {
            return json;
        }

        @Override
        public void write(String json) {
            this.json = json;
        }
    }
}
