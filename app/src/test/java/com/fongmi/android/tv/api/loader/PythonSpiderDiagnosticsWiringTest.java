package com.fongmi.android.tv.api.loader;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Python 蜘蛛的依赖取回失败必须同时留在「诊断日志」和「Logcat」两条通道上。
 *
 * <p>{@code SpiderDebug.log(tag, …)} 的每一条重载先是 {@code DebugLogStore.acceptsTag(tag)}，
 * 而它等于 {@code enabled && …}；{@code enabled} 默认是 false（{@code Prefers.getBoolean}
 * 的默认值），只有用户在设置里打开「调试日志」才为真。因此只用 SpiderDebug 记录依赖取回失败，
 * 在默认设置下等同于「静默跳过」——用户只会看到某个蜘蛛少了功能却没有任何线索。</p>
 *
 * <p>{@code PyLoader}/{@code JsLoader} 的同类口径由 {@code PyLoaderDiagnosticsWiringTest}
 * 守着；{@code com.fongmi.chaquo.Spider} 属于 chaquo 模块，app 侧单元测试此前完全没有覆盖，
 * 所以这条修复可以被静默回退而 0 个测试失败。本测试把它拉回同一口径。</p>
 */
public class PythonSpiderDiagnosticsWiringTest {

    private static final String SPIDER = "chaquo/src/main/java/com/fongmi/chaquo/Spider.java";

    private static final String STRUCTURED =
            "SpiderDebug.log(TAG, \"depend download failed name=%s error=%s\", item, e);";

    private static String source() throws Exception {
        return code(projectRoot().resolve(SPIDER));
    }

    private static Path projectRoot() {
        return Files.exists(Path.of("chaquo")) ? Path.of("") : Path.of("..");
    }

    /**
     * 去掉注释后再断言：否则 {@code // e.printStackTrace();} 这种注释行也能满足断言，
     * 守卫就退化成恒真检查（与 {@code PyLoaderDiagnosticsWiringTest#code} 同一手法）。
     * 这里比它多一步：先剥掉块注释，因为 {@code /* e.printStackTrace(); *}{@code /} 这种
     * 行内块注释既不以 {@code //} 也不以 {@code *} 开头，却同样能让断言看到那串文本。
     */
    private static String code(Path file) throws Exception {
        String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        StringBuilder plain = new StringBuilder();
        int at = 0;
        while (true) {
            int open = text.indexOf("/*", at);
            if (open < 0) {
                plain.append(text, at, text.length());
                break;
            }
            plain.append(text, at, open);
            int close = text.indexOf("*/", open + 2);
            if (close < 0) {
                // 未闭合的块注释在 Java 里本身就是编译错误；这里保留余下文本而不是丢弃，
                // 以免断言因为「看不到代码」而不明不白地红掉。
                plain.append(text, open, text.length());
                break;
            }
            at = close + 2;
        }
        StringBuilder result = new StringBuilder();
        for (String line : plain.toString().split("\n", -1)) {
            if (!line.trim().startsWith("//") && !line.trim().startsWith("*")) result.append(line).append('\n');
        }
        return result.toString();
    }

    @Test
    public void dependencyFailureKeepsAnUnconditionalLogcatChannel() throws Exception {
        String source = source();
        int diagnose = source.indexOf(STRUCTURED);

        assertTrue("a failed dependency download must still be named in the diagnostic log", diagnose > 0);
        int unconditional = source.indexOf("e.printStackTrace();", diagnose);
        assertTrue("a failed dependency download must also reach Logcat without 调试日志 enabled",
                unconditional > diagnose);
        // 两条通道必须在同一个 catch 里：一旦 printStackTrace 挪到别处，上面的 in-order 断言依然成立——
        // getHeader() 的 catch 里同样有 e 在作用域内，挪过去也能通过 in-order 检查。
        // 所以这里再钉两点：与结构化日志相距 < 200 字符（同一代码块），以及全文只有一处。
        assertTrue("both channels must live in the same catch block",
                unconditional - diagnose < 200);
        assertEquals("the unconditional channel must be the only one in the file",
                -1, source.indexOf("e.printStackTrace();", unconditional + 1));
    }

    @Test
    public void dependencyFailureKeepsTheBridgeCallGuarded() throws Exception {
        String source = source();

        // 这条断言守的是「catch 住谁」：改回 catch (Exception) 会让 JVM 级 Error 重新逃逸成整站降级，
        // 而 Spider 的其它 catch 都不是这个形状，所以直接在依赖循环里定位。
        int download = source.indexOf("download(item + \".py\");");
        int caught = source.indexOf("catch (Throwable e)", download);

        assertTrue("the dependency download call must still be present", download > 0);
        assertTrue("a failed dependency download must not abort init(), including on JVM-level Error",
                caught > download);
        assertTrue("the catch must be adjacent to the download call", caught - download < 200);
        assertEquals("only the dependency loop should log and rethrow nothing", 1, countOf(source, STRUCTURED));
    }

    private static int countOf(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) count++;
        return count;
    }
}
