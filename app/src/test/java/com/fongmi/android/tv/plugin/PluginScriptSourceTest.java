package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 脚本入口解析测试。
 *
 * <p>关键断言是**远程 URL 必须被拒绝**：放开远程加载等于把"插件能执行什么代码"
 * 交给网络，与 §5 安全边界冲突。远程分发是后续切片的事，且必须先有签名校验。
 */
public class PluginScriptSourceTest {

    @Test
    public void rejectsNullAndBlank() {
        assertNull(PluginScriptSource.read(null));
        assertNull(PluginScriptSource.read(""));
        assertNull(PluginScriptSource.read("   "));
    }

    @Test
    public void rejectsRemoteUrls() {
        // 这是安全断言，不是功能缺失：远程加载必须等签名与来源校验就位
        assertNull(PluginScriptSource.read("https://example.com/x.js"));
        assertNull(PluginScriptSource.read("http://example.com/x.js"));
        assertNull(PluginScriptSource.read("ftp://example.com/x.js"));
    }

    @Test
    public void rejectsRelativePaths() {
        assertNull(PluginScriptSource.read("../escape.js"));
        assertNull(PluginScriptSource.read("hello.js"));
        assertNull(PluginScriptSource.read("./hello.js"));
    }

    @Test
    public void rejectsNonExistentFile() {
        assertNull(PluginScriptSource.read("file:///definitely/not/here/x.js"));
        assertNull(PluginScriptSource.read("/definitely/not/here/x.js"));
    }

    @Test
    public void neverThrowsOnGarbageInput() {
        // 审计与装载路径不能被畸形输入中断
        assertNull(PluginScriptSource.read("\u0000"));
        assertNull(PluginScriptSource.read("assets://"));
        assertNull(PluginScriptSource.read("file://"));
        assertNull(PluginScriptSource.read("://"));
    }

    @Test
    public void bareAbsolutePathRequiresUnixRoot() {
        // 裸绝对路径只认 "/" 开头——这是 Android 语义（设备上不存在盘符）。
        // 放宽到任意非空路径会让 "../escape.js" 这类相对路径蒙混过关，
        // 因此这里主动钉住"盘符路径被拒绝"这一契约。
        assertNull(PluginScriptSource.read("C:/temp/x.js"));
        assertNull(PluginScriptSource.read("C:\\temp\\x.js"));
    }

    @Test
    public void readsFileSchemeScript() throws Exception {
        java.io.File temp = java.io.File.createTempFile("plugin-test", ".js");
        temp.deleteOnExit();
        java.nio.file.Files.writeString(temp.toPath(), "webhtv.log('x');",
                java.nio.charset.StandardCharsets.UTF_8);

        String source = PluginScriptSource.read("file://" + temp.getAbsolutePath().replace('\\', '/'));

        assertNotNull("file:// script should be readable", source);
        assertTrue(source.contains("webhtv.log"));
    }

    @Test
    public void rejectsOversizedScript() throws Exception {
        java.io.File temp = java.io.File.createTempFile("plugin-big", ".js");
        temp.deleteOnExit();
        byte[] big = new byte[PluginScriptSource.MAX_SCRIPT_BYTES + 1024];
        java.util.Arrays.fill(big, (byte) 'a');
        java.nio.file.Files.write(temp.toPath(), big);

        assertNull("oversized script must be rejected", PluginScriptSource.read(temp.getAbsolutePath()));
    }

    @Test
    public void rejectsEmptyFile() throws Exception {
        java.io.File temp = java.io.File.createTempFile("plugin-empty", ".js");
        temp.deleteOnExit();
        // 空文件必须被拒绝，而不是读成 "" 继续往下走
        assertNull(PluginScriptSource.read(temp.getAbsolutePath()));
    }

    @Test
    public void maxScriptBytesIsBounded() {
        assertTrue("script cap must stay small enough to protect the JS engine",
                PluginScriptSource.MAX_SCRIPT_BYTES <= 1024 * 1024);
    }
}
