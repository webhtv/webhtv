package com.fongmi.android.tv.api.loader;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PyLoader 的初始化失败必须同时留在「诊断日志」和「Logcat」两条通道上。
 *
 * <p>{@code SpiderDebug.log(tag, …)} 的每一条重载先是 {@code DebugLogStore.acceptsTag(tag)}，
 * 而它等于 {@code enabled && …}；{@code enabled} 默认是 false（{@code Prefers.getBoolean}
 * 的默认值），只有用户在设置里打开「调试日志」才为真。因此只用 SpiderDebug 记录初始化失败，
 * 在默认设置下等同于「完全静默」——而这里正是「站点全空却没有任何线索」的唯一解释点。</p>
 *
 * <p>兄弟实现 {@code JsLoader} 同时保留了两条通道，本测试把 PyLoader 拉回同一口径。</p>
 */
public class PyLoaderDiagnosticsWiringTest {

    private static final Path SOURCE = Path.of("src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java");

    private static String source() throws Exception {
        return code(SOURCE);
    }

    /**
     * 去掉注释行后再断言：否则 {@code // e.printStackTrace();} 这种注释也能满足「有兜底日志」的断言，
     * 守卫就退化成恒真检查。
     */
    private static String code(Path file) throws Exception {
        StringBuilder result = new StringBuilder();
        for (String line : Files.readString(file).replace("\r\n", "\n").split("\n", -1)) {
            if (!line.trim().startsWith("//") && !line.trim().startsWith("*")) result.append(line).append('\n');
        }
        return result.toString();
    }

    @Test
    public void initFailureKeepsAnUnconditionalLogcatChannel() throws Exception {
        String source = source();
        int diagnose = source.indexOf("SpiderDebug.log(PY_TAG, e);");
        int unconditional = source.indexOf("e.printStackTrace();", diagnose);

        assertTrue("init failure must still be recorded in the diagnostic log", diagnose > 0);
        assertTrue("init failure must also reach Logcat without 调试日志 enabled", unconditional > diagnose);
        assertEquals("the unconditional channel must live in the same catch block",
                -1, source.indexOf("e.printStackTrace();", unconditional + 1));
    }

    @Test
    public void initFailureStillGoesThroughTheDiagnosticStore() throws Exception {
        String source = source();
        int named = source.indexOf("SpiderDebug.log(PY_TAG, \"init failed key=%s api=%s error=%s:%s\"");

        // 两条通道缺一不可：诊断日志带 key/api/异常类型，Logcat 无条件可见。
        assertTrue("init failure must name the site and the failing callable", named > 0);
        assertTrue(source.contains("SpiderDebug.log(PY_TAG, e);"));
    }

    @Test
    public void siblingJsLoaderUsesTheSameTwoChannels() throws Exception {
        String source = code(Path.of("src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java"));

        // 这个断言的意义是：一旦别的 loader 改了口径，PyLoader 的「两条通道」也就不再是少数派特例。
        assertTrue(source.contains("SpiderDebug.log(\"JsLoader\", e);"));
        assertTrue(source.contains("e.printStackTrace();"));
    }
}
