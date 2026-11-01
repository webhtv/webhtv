package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 内置样本登记的真机验证。
 *
 * <p>JVM 单测把资产后端换成了内存实现，因此"真机上 {@code Asset.read} 能不能读到
 * {@code assets/plugin/douban}、写出去的文件是不是合法 UTF-8、装载器能不能凭改写后的
 * 清单把它跑起来"这三件事在单测里全是空白。本类只换两样东西——记账后端和插件根目录——
 * 资产读取与文件落盘走真机真实路径。
 */
@RunWith(AndroidJUnit4.class)
public class PluginSampleInstallerDeviceTest {

    private static final String PLUGIN_ID = "douban.comments";

    private File pluginsRoot;

    @Before
    public void setUp() {
        pluginsRoot = new File(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir(), "plugin-sample-device");
        delete(pluginsRoot);
        PluginSampleInstaller.useBackendsForTest(new MemoryStore(), null);
        PluginSourceConfig.useStoreForTest(new MemoryConfigStore());
        PluginPackageImporter.overrideRootForTest(pluginsRoot);
        PluginController.reset();
        PluginBootstrap.install();
    }

    @After
    public void tearDown() {
        PluginSampleInstaller.useBackendsForTest(null, null);
        PluginSourceConfig.useStoreForTest(null);
        PluginPackageImporter.overrideRootForTest(null);
        delete(pluginsRoot);
    }

    @Test
    public void bundledSampleIsReadableFromAssetsAndCanBeLoaded() throws Exception {
        assertEquals("首次调起必须真正种下一条样本", 1, PluginSampleInstaller.seedOnce());

        PluginSourceConfig.Entry entry = PluginSourceConfig.get().find(PLUGIN_ID);
        assertNotNull(entry);
        assertEquals("js", entry.runtimeApi());

        File directory = new File(entry.directory());
        File script = new File(directory, "douban.js");
        assertTrue("脚本必须真的落盘", script.isFile());
        String scriptText = Files.readString(script.toPath(), StandardCharsets.UTF_8);
        assertTrue("资产读取失败会得到一个空脚本，这里必须拦住",
                scriptText.getBytes(StandardCharsets.UTF_8).length > 1000);
        assertTrue("脚本内容必须是豆瓣插件本体", scriptText.contains("movie.douban.com"));

        JsonObject manifest = JsonParser
                .parseString(Files.readString(new File(directory, "manifest.json").toPath()))
                .getAsJsonObject();
        String entryPath = manifest.getAsJsonObject("runtime").get("entry").getAsString();
        assertEquals("清单 entry 必须指向落盘后的绝对路径", script.getAbsolutePath(), entryPath);

        assertTrue("改写后的清单必须能被装载器跑起来", PluginBootstrap.reloadAll());
        assertTrue(PluginController.get().host().isLoaded(PLUGIN_ID));
        assertNotNull("panel 槽位必须随清单一起生效",
                PluginController.get().host().slotOfAction(PLUGIN_ID, "douban.comments"));
    }

    @Test
    public void secondRunInsideTheSameProcessIsANoOp() {
        assertEquals(1, PluginSampleInstaller.seedOnce());
        assertEquals(0, PluginSampleInstaller.seedOnce());
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

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
