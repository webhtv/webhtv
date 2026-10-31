package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 启动接线测试。
 *
 * <p>契约只有一条但很硬：**装载链路的任何失败都不能抛给调用方**。调用点是
 * {@code App.onCreate} 投递的后台任务与设置页的"重新装载"按钮，前者抛异常等于启动崩溃
 * （后台线程的异常连现场都留不下），后者等于界面直接挂掉。
 *
 * <p>因此这里刻意在**没有原生库**的 JVM 环境里跑完整链路：QuickJS 的 {@code so} 加载必然失败，
 * 而这正是"最坏情况"——{@link PluginBootstrap#reloadAll()} 必须把它变成一次安静的
 * {@code false} 而不是异常。
 */
public class PluginBootstrapTest {

    private File workDir;
    private File pluginsRoot;
    private MemoryStore store;

    @Before
    public void setUp() throws Exception {
        PluginController.reset();
        PluginEventBus.get().clear();
        PluginAuditLog.get().clear();
        workDir = Files.createTempDirectory("plugin-bootstrap").toFile();
        pluginsRoot = new File(workDir, "files/plugins");
        store = new MemoryStore();
        PluginSourceConfig.useStoreForTest(store);
        PluginPackageImporter.overrideRootForTest(pluginsRoot);
    }

    @After
    public void tearDown() {
        PluginPackageImporter.overrideRootForTest(null);
        PluginSourceConfig.useStoreForTest(null);
        PluginController.reset();
        PluginEventBus.get().clear();
        PluginAuditLog.get().clear();
        delete(workDir);
    }

    @Test
    public void installRegistersJsRuntimeAndIsIdempotent() {
        PluginBootstrap.install();
        String first = PluginController.get().diagnostics();
        PluginBootstrap.install();
        String second = PluginController.get().diagnostics();

        assertTrue("js runtime must be registered: " + first, first.contains("js=ready"));
        assertEquals("install must be idempotent", first, second);
    }

    @Test
    public void reloadAllWithNoPluginsReturnsFalseQuietly() {
        assertFalse(PluginBootstrap.reloadAll());
        assertEquals(0, PluginController.get().host().size());
    }

    /**
     * 有启用插件但装载必然失败时（清单合法、入口脚本缺失，且 JVM 里没有 QuickJS 原生库），
     * 必须是"quiet false"而不是异常。这两条失败路径任取其一都足以证明包装层生效。
     */
    @Test
    public void reloadAllSwallowsRuntimeFailure() throws Exception {
        File pkg = new File(workDir, "pkg");
        assertTrue(pkg.mkdirs());
        Files.writeString(new File(pkg, "manifest.json").toPath(), manifestJson("demo.boot"), StandardCharsets.UTF_8);
        // 故意不写 entry 指向的 hello.js：装载期必然失败。
        PluginSourceConfig.get().put(new PluginSourceConfig.Entry("demo.boot", "boot", "1", "js",
                pkg.getAbsolutePath(), true, 1L));
        assertEquals(1, PluginSourceConfig.get().toManifests().size());

        boolean loaded = PluginBootstrap.reloadAll();

        assertFalse("a failed engine must not be reported as loaded", loaded);
        // 失败必须留审计：否则"插件没生效"完全无从诊断。
        assertTrue("failed init must be audited", PluginAuditLog.get().size() > 0);
    }

    @Test
    public void installAndReloadQuietlyNeverThrows() {
        PluginBootstrap.installAndReloadQuietly();
        PluginBootstrap.installAndReloadQuietly();
        // 能跑到这里就说明没有异常；顺带确认单例仍然可用。
        assertEquals(0, PluginController.get().host().size());
    }

    private static String manifestJson(String id) {
        return "{\"protocol\":\"webhtv-plugin\",\"version\":1,\"id\":\"" + id
                + "\",\"name\":\"boot\",\"sdk\":1,\"runtime\":{\"api\":\"js\",\"entry\":\"hello.js\"},"
                + "\"events\":[\"ui.contentShown\"],\"permissions\":[\"plugin.events\"]}";
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
