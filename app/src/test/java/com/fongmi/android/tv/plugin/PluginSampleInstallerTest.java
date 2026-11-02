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
 * <p>覆盖五件事：**首次启动真的把资产落盘并登记**（含清单 entry 被改写成绝对路径）、
 * **同一修订号下只做一次**（第二次启动不再碰盘）、**用户删掉之后不复活**、**修订号前进时
 * 只刷新宿主自己种下的那一份**（启用状态与导入时间沿用旧值）、以及**用户自己导入的同 id
 * 包一行都不覆盖**。另外两条是失败路径：资产缺失与清单非法都不能记账——否则下一次启动
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

    @Test
    public void revisionBumpRefreshesTheOwnedCopyInPlace() throws Exception {
        // 造一台"已经种过修订号 1 的旧设备"：目录在 plugins/douban.comments，脚本是旧内容，
        // 条目被用户关掉了，记账停在 0。
        File owned = new File(pluginsRoot, "douban.comments");
        assertTrue(owned.mkdirs());
        File script = new File(owned, "douban.js");
        Files.writeString(script.toPath(), "OLD SCRIPT");
        config.put(new PluginSourceConfig.Entry("douban.comments", "豆瓣短评", "0.9", "py",
                owned.getAbsolutePath(), false, 123L));
        config.save();
        store.mark(0);

        assertEquals("刷新也算本类做的事，返回值必须有别于不动", 1, PluginSampleInstaller.seedOnce());

        PluginSourceConfig.Entry entry = config.find("douban.comments");
        assertEquals("新样本的元信息必须覆盖旧的", "js", entry.runtimeApi());
        assertEquals("脚本内容必须被换成新资产", SCRIPT, Files.readString(script.toPath()));
        assertFalse("用户关掉的插件不能被升级偷偷重新打开", entry.enabled());
        assertEquals("刷新不是重新导入，导入时间沿用旧值", 123L, entry.importedAt());
        String manifestText = Files.readString(new File(owned, "manifest.json").toPath());
        assertEquals("清单也要重写成脚本绝对路径", script.getAbsolutePath(),
                JsonParser.parseString(manifestText).getAsJsonObject()
                        .getAsJsonObject("runtime").get("entry").getAsString());
    }

    @Test
    public void refreshIsNotRepeatedWithinTheSameRevision() throws Exception {
        PluginSampleInstaller.seedOnce();
        File script = new File(config.find("douban.comments").directory(), "douban.js");
        Files.writeString(script.toPath(), "TAMPERED");

        assertEquals("刷新完就记账，同一修订号下不再动盘", 0, PluginSampleInstaller.seedOnce());
        assertEquals(PluginSampleInstaller.SEED_REVISION, store.revision());
        assertEquals("TAMPERED", Files.readString(script.toPath()));
    }

    @Test
    public void deletedSampleIsNotResurrectedByARevisionBump() throws Exception {
        // 用户已经删掉了内置样本：目录与条目都不在，只剩记账停在旧修订号。
        store.mark(1);

        assertEquals("升级不是首次装机，没有可刷新的对象就什么都不建",
                0, PluginSampleInstaller.seedOnce());
        assertFalse("用户删掉的样本不能因为修订号前进而复活",
                new File(pluginsRoot, "douban.comments").exists());
        assertNull("也不该凭空造出一条配置", config.find("douban.comments"));
        assertEquals("按已处理记账，否则每次启动都白试一遍",
                PluginSampleInstaller.SEED_REVISION, store.revision());
    }

    @Test
    public void ownedEntryWhoseDirectoryIsGoneIsNotRecreated() throws Exception {
        // 僵尸条目：配置说装着，目录其实已被删掉。
        File owned = new File(pluginsRoot, "douban.comments");
        assertFalse(owned.exists());
        config.put(new PluginSourceConfig.Entry("douban.comments", "豆瓣短评", "0.9", "py",
                owned.getAbsolutePath(), false, 123L));
        config.save();
        store.mark(0);

        assertEquals("目录不在时的刷新是空操作，落盘流程一步都不进",
                0, PluginSampleInstaller.seedOnce());
        assertFalse("刷新分支里的 mkdirs 不能把用户删掉的目录重新造出来", owned.exists());
        PluginSourceConfig.Entry entry = config.find("douban.comments");
        assertNotNull("僵尸条目归管理页清理，本类不越界删配置", entry);
        assertEquals("豆瓣短评", entry.name());
        assertEquals("0.9", entry.version());
        assertEquals("py", entry.runtimeApi());
        assertFalse(entry.enabled());
        assertEquals(123L, entry.importedAt());
    }

    @Test
    public void ownedDirectoryIsRecognisedThroughPathSpelling() throws Exception {
        PluginSampleInstaller.seedOnce();
        File owned = new File(config.find("douban.comments").directory());
        // 配置是明文可改的，可能是别处写进来的等价拼法：多一段 "." 与一个尾斜杠。
        String spelled = pluginsRoot.getAbsolutePath() + File.separator + "."
                + File.separator + "douban.comments" + File.separator;
        config.put(new PluginSourceConfig.Entry("douban.comments", "豆瓣短评", "1", "js",
                spelled, true, 123L));
        config.save();
        store.mark(0);
        Files.writeString(new File(owned, "douban.js").toPath(), "OLD SCRIPT");

        assertEquals("等价拼法不能被当成'别人的目录'，否则升级永远不发生",
                1, PluginSampleInstaller.seedOnce());
        assertEquals(SCRIPT, Files.readString(new File(owned, "douban.js").toPath()));
    }

    @Test
    public void foreignCopyWithTheSameIdIsLeftAlone() throws Exception {
        File foreign = new File(root, "user-picked");
        assertTrue(foreign.mkdirs());
        File foreignScript = new File(foreign, "douban.js");
        Files.writeString(foreignScript.toPath(), "USER SCRIPT");
        config.put(new PluginSourceConfig.Entry("douban.comments", "用户自己的", "9", "js",
                foreign.getAbsolutePath(), true, 456L));
        config.save();

        assertEquals("用户自己导入的同 id 包不归本类管", 0, PluginSampleInstaller.seedOnce());

        assertEquals("USER SCRIPT", Files.readString(foreignScript.toPath()));
        assertFalse("连清单都不该被写进去", new File(foreign, "manifest.json").exists());
        assertFalse("也不该在插件根目录下另种一份", new File(pluginsRoot, "douban.comments").exists());
        PluginSourceConfig.Entry entry = config.find("douban.comments");
        assertEquals("用户自己的", entry.name());
        assertEquals(456L, entry.importedAt());
        assertTrue(entry.enabled());
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
