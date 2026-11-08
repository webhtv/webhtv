package com.fongmi.android.tv.playback;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 「按站点读历史」这条新读取路径的失败必须同时留在「诊断日志」和「Logcat」两条通道上。
 *
 * <p>为什么需要这个守卫：{@code SpiderDebug.log(tag, …)} 的每一条重载先是
 * {@code DebugLogStore.acceptsTag(tag)}，而它等于 {@code enabled && …}；
 * {@code DebugLogStore.enabled} 默认是 {@code false}（要用户在设置里打开「调试日志」）。
 * 因此只用 {@code SpiderDebug} 记录读库失败，在默认设置下等同于**完全静默**。
 *
 * <p>而这条路径恰恰是「对端看到空历史却查不出原因」的唯一解释点：
 * 空数组既可能是「这个站点真没历史」，也可能是「库挂了」或「单条记录坏了被跳过」。
 * 两者在响应里长得一模一样，只有日志能区分。仓库既有同类口径：
 * {@code PyLoaderDiagnosticsWiringTest}（C73 修复）与 {@code JsLoader}。
 */
public class PlaybackHistoryDiagnosticsWiringTest {

    private static final Path API = Path.of("app/src/main/java/com/fongmi/android/tv/playback/PlaybackApi.java");
    private static final Path ENDPOINT = Path.of("app/src/main/java/com/fongmi/android/tv/server/process/PlaybackHistoryApi.java");

    /**
     * 去掉注释行后再断言：否则把语句注释掉（{@code // e.printStackTrace();}）也能满足
     * 「有兜底日志」的断言，守卫就退化成恒真检查。
     *
     * <p>路径从 {@code user.dir} 逐级向上找项目根：Gradle 的 JVM 单测工作目录可能是
     * 仓库根，也可能是模块目录（{@code app/}，见 {@code PyLoaderDiagnosticsWiringTest}
     * 用模块相对路径），写死一种布局会在换布局后静默失效。
     */
    private static String code(Path relative) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(relative))) root = root.getParent();
        if (root == null) throw new IllegalStateException("找不到项目根，无法读取 " + relative);
        StringBuilder result = new StringBuilder();
        for (String line : Files.readString(root.resolve(relative)).replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    /**
     * 取第 {@code index} 个 {@code catch (Throwable e) {} } 块体。
     *
     * <p>为什么必须按块取：整批读库失败与单条坏记录是两个相邻的 catch。
     * 若只做「从某处往后找 printStackTrace」，第一个 catch 被删掉时断言会匹配到
     * **第二个** catch 的兜底日志而恒真——这正是本守卫第一版踩过的坑。
     */
    private static String catchBlock(String source, int index) {
        int start = -1;
        for (int i = 0; i <= index; i++) start = source.indexOf("catch (Throwable e) {", start + 1);
        if (start < 0) throw new IllegalStateException("找不到第 " + index + " 个 catch 块");
        // 用花括号配平定位块尾，而不是匹配字面缩进（原先找 "\n        }"）。
        // 字面缩进有两个失效面：失配时回退到 source.length() 会让块体延伸到文件尾，
        // 于是"块里含某段文本"对后续任何 catch 都恒真；即便改成显式失败，缩进一变
        // 也会匹配到**更靠后的**同缩进花括号，块体同样被撑大。配平扫描对缩进免疫。
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new IllegalStateException("第 " + index + " 个 catch 块缺少配对的结束花括号");
    }

    @Test
    public void databaseReadFailureReachesLogcatWithoutDebugLoggingEnabled() throws Exception {
        String block = catchBlock(code(API), 0);

        assertTrue("读库失败必须进诊断日志", block.contains("SpiderDebug.log(\"playback-api\", e);"));
        assertTrue("读库失败还必须无条件进 Logcat（SpiderDebug 默认不落盘）", block.contains("e.printStackTrace();"));
    }

    @Test
    public void perRecordFailureReachesLogcatToo() throws Exception {
        String block = catchBlock(code(API), 1);

        // 单条坏记录被跳过是「历史里少了一条」的唯一解释点，同样必须有兜底通道。
        assertTrue("单条坏记录必须进诊断日志", block.contains("SpiderDebug.log(\"playback-api\", e);"));
        assertTrue("单条坏记录的跳过路径也必须留下无条件日志", block.contains("e.printStackTrace();"));
    }

    @Test
    public void endpointFailureReachesLogcat() throws Exception {
        String block = catchBlock(code(ENDPOINT), 0);

        assertTrue("端点 500 必须进诊断日志", block.contains("SpiderDebug.log(\"playback-api\", e);"));
        assertTrue("端点 500 还必须无条件进 Logcat", block.contains("e.printStackTrace();"));
    }

    @Test
    public void emptyResultIsStillDistinguishableFromFailureInTheResponse() throws Exception {
        // 契约的另一半：读库失败返回空数组是**有意的**（对端据此不推进游标），
        // 因此不能靠「把失败改成 500」来省掉日志——两者必须同时成立。
        String block = catchBlock(code(API), 0);

        assertTrue("读库失败后仍返回空数组，不抛给调用方", block.contains("return Collections.emptyList();"));
    }
}
