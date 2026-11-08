package com.fongmi.android.tv.lab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 输出页「清除」必须真的清干净，且不得丢新输出。
 *
 * <p>三条不变量：清内存与截落盘文件落在同一个 {@code synchronized} 块里；清 UI 与既有 append 走同一条
 * 主线程队列；截断而非删除日志文件。这里只锁会导致回归的源码/资源形态；设备上的点按效果未做设备验证
 * （本任务的证据只有编译与单元测试）。
 *
 * <p>凡涉及先后顺序的断言（清 UI 与清缓冲的投递顺序、{@code truncate} 的空判先于 {@code exists()}、
 * 写盘先于 flush、缓冲 append 先于回调、读者线程先于关闭线程）一律用位置比较；其余断言按花括号作用域
 * 判断「在某块之内」——文本先后会把搬到块外的调用判成通过。
 */
public class LabClearLogSourceTest {

    private static final String ACTIVITY = "src/main/java/com/fongmi/android/tv/lab/LabOutputActivity.java";
    private static final String RUNNER = "src/main/java/com/fongmi/android/tv/lab/LabRunner.java";
    private static final String LAYOUT = "src/main/res/layout/activity_lab_output.xml";
    /** pump 与 clearLog 必须共用同一个缓冲来源，否则清除清的是没人写的那一个。 */
    private static final String BUFFER_LOOKUP = "LOGS.computeIfAbsent(key, k -> new StringBuilder())";

    /** 清 UI 先入主线程队列，清缓冲/截文件去后台：反序会抹掉只存在于界面的行。 */
    @Test
    public void clearLogPostsToTheMainThread() throws Exception {
        String activity = stripComments(read(ACTIVITY));
        assertTrue("the clear button is the only entry point and must call clearLog()",
                activity.contains("btnClear.setOnClickListener(v -> clearLog())"));
        String body = methodBody(activity, "private void clearLog()");
        int post = body.indexOf("App.post(");
        assertTrue("clearLog() must hand the clearing to App.post", post >= 0);
        assertTrue("the UI clear must be queued before anything waits on the pump lock",
                post < body.indexOf("new Thread("));
        assertTrue("the output text, not another view, must be cleared inside that posted runnable",
                blockAfter(body, "App.post(").contains("mBinding.outputText.setText(\"\")"));
        assertFalse("clearing the title would leave the log on screen",
                blockAfter(body, "App.post(").contains("outputTitle.setText("));
        assertEquals("the buffer clear must run exactly once, on the background thread",
                1, countOf(body, "LabRunner.clearLog("));
        assertFalse("the shared app thread pool can be saturated by web work, so it must not gate a tap",
                body.contains("Task.execute("));
        assertFalse("the click thread must only queue the UI clear, otherwise old lines flash before vanishing",
                body.substring(0, post).contains("setText(\"\")"));
        String background = body.substring(body.indexOf("new Thread("));
        assertTrue("the disk truncation must run off the main thread",
                background.contains("LabRunner.clearLog(logKey)"));
        assertTrue("the background clear must receive the key the button read, not an empty one",
                body.contains("String logKey = key();"));
        assertTrue("the truncation thread must actually be started, or the buffer and file are never cleared",
                background.contains(".start()"));
        assertFalse("the background work must not post the UI clear too, or it lands after the click",
                background.contains("App.post("));
        assertFalse("backfilling would duplicate a line whose own append is already queued",
                blockAfter(body, "App.post(").contains("getLog("));
    }

    /** 内存缓冲一律原地清零：换实例会让 pump 之后的输出写进没人再读的旧引用。 */
    @Test
    public void clearLogKeepsTheSameBufferInstance() throws Exception {
        String runner = stripComments(read(RUNNER));
        String body = methodBody(runner, "public static void clearLog(String key)");
        assertFalse("clearLog must not replace the buffer instance", body.contains("LOGS.remove("));
        assertTrue("setLength(0) keeps the reference pump already holds", body.contains("setLength(0)"));
        assertTrue("clearLog must take the buffer from LOGS, not a fresh one",
                body.contains(BUFFER_LOOKUP));
        String locked = blockAfter(body, "synchronized (sb)");
        assertTrue("the buffer must be cleared inside the pump lock", locked.contains("setLength(0)"));
        assertTrue("the disk truncation must live in the same critical section",
                locked.contains("truncate(logFileFor(key))"));
        assertFalse("a get-then-branch would race pump over the startup window",
                body.contains("LOGS.get(key)"));
    }

    /** 截断而不是删除：删掉文件会让运行中的写者继续写进已 unlink 的 inode，日志永久消失。 */
    @Test
    public void truncateKeepsTheFileInPlace() throws Exception {
        String runner = stripComments(read(RUNNER));
        String body = methodBody(runner, "private static void truncate(File file)");
        assertFalse("truncate must not unlink the log file", body.contains(".delete()"));
        assertFalse("unlinking through NIO escapes just as badly",
                body.contains("Files.delete") || body.contains("deleteIfExists"));
        assertTrue("reopening the file truncates it in place",
                body.contains("new java.io.FileOutputStream(file)"));
        assertFalse("an append flag would keep every byte and clear nothing",
                body.contains("new java.io.FileOutputStream(file, true)"));
        assertTrue("the stream must be closed even when the close itself throws",
                body.contains("try (java.io.FileOutputStream"));
        int guard = body.indexOf("file == null");
        int exists = body.indexOf("file.exists()");
        assertTrue("a null file must be rejected before exists() is called, or clearing an unresolvable key throws on the background clear thread",
                guard >= 0 && exists > guard);
        assertTrue("the null branch must return, or it only logs and still falls through to exists()",
                blockAfter(body, "file == null").contains("return;"));
    }

    /** 写盘与写内存同处一把锁：否则 clearLog 的窗口会丢行或让内存与落盘分叉。 */
    @Test
    public void pumpWritesTheFileInsideTheBufferLock() throws Exception {
        String body = methodBody(stripComments(read(RUNNER)), "private static void pump(");
        assertTrue("pump must append to the buffer under its lock", body.contains("synchronized (log)"));
        assertTrue("pump must lock the very buffer clearLog clears", body.contains(BUFFER_LOOKUP));
        String locked = blockAfter(body, "while ((line = reader.readLine()) != null)");
        assertTrue("the per-line file write must stay inside that lock",
                blockAfter(locked, "synchronized (log)").contains("writer.write(line"));
        assertTrue("the buffer append must share the same critical section",
                blockAfter(locked, "synchronized (log)").contains("log.append(line)"));
        String inLock = blockAfter(locked, "synchronized (log)");
        int write = inLock.indexOf("writer.write(line");
        int flush = inLock.indexOf("writer.flush()");
        assertTrue("every persisted line must be flushed inside the same critical section, after the write",
                write >= 0 && flush > write);
    }

    /** 投递 UI 也必须落在同一把锁里，否则清除前已读出的行会在清除之后才冒到界面与文件。 */
    @Test
    public void pumpPostsOutputInsideTheBufferLock() throws Exception {
        String body = methodBody(stripComments(read(RUNNER)), "private static void pump(");
        String locked = blockAfter(body, "while ((line = reader.readLine()) != null)");
        String inLock = blockAfter(locked, "synchronized (log)");
        assertTrue("the callback must fire under the same lock as the buffer append",
                inLock.contains("listener.onOutput("));
        assertTrue("the callback must fire after the buffer append, not before",
                inLock.indexOf("log.append(line)") < inLock.indexOf("listener.onOutput("));
    }

    /** 流只能由读者线程关：waitFor 线程上的 close 会在 wrapper 先退出时掐断仍在写的 stdout。 */
    @Test
    public void pumpClosesTheWriterOnTheReaderThread() throws Exception {
        String body = methodBody(stripComments(read(RUNNER)), "private static void pump(");
        int afterReader = body.indexOf("} finally {");
        assertTrue("the writer must be closed in the reader thread's finally", afterReader >= 0);
        assertTrue("that close must run under the buffer lock",
                blockAfter(body.substring(afterReader), "synchronized (log)").contains("writer.close()"));
        assertEquals("the writer must be closed exactly once", 1, countOf(body, "writer.close()"));
        int started = body.indexOf("out.start()");
        assertTrue("the reader thread must be started", started >= 0);
        assertFalse("the waitFor watcher must not close the stream too",
                body.substring(started).contains("writer.close()"));
        int threadStart = body.indexOf("Thread out = new Thread(");
        assertTrue("the reader thread must exist", threadStart >= 0);
        assertFalse("the calling thread (main) must not touch the file",
                body.substring(0, threadStart).contains("new java.io.FileWriter("));
        String open = blockAfter(body.substring(threadStart), "synchronized (log)");
        assertTrue("the stream must be opened on the reader thread", open.contains("new java.io.FileWriter("));
        assertTrue("the command header must be written under the same lock",
                open.contains("writer.write(\"$ \""));
        assertTrue("the writer must keep appending: a reused pid can hit an existing log, and truncating on open would drop what an earlier run already persisted",
                open.contains("new java.io.FileWriter(logFile, true)"));
    }

    /** 退出/停止后 logFor 被摘掉，清除必须回落到本进程记下的文件，否则对磁盘静默失效。 */
    @Test
    public void clearLogFallsBackToTheRememberedFile() throws Exception {
        String runner = stripComments(read(RUNNER));
        assertTrue("pump must remember the file it writes, or a later clear cannot find it",
                methodBody(runner, "private static void pump(").contains("LOG_FILES.put(key"));
        String resolve = blockAfter(methodBody(runner, "private static File logFileFor(String key)"), "");
        assertTrue("clearLog must resolve the file through that fallback", resolve.contains("LOG_FILES.get(key)"));
        assertTrue("reading also remembers the file, so a later clear still finds it",
                resolve.contains("LOG_FILES.put(key"));
        String body = methodBody(runner, "public static void clearLog(String key)");
        assertFalse("half the branches must not bypass the fallback",
                body.contains("LabProcManager.logFor(key)"));
    }

    /** 读也走同一把锁：本次新增了一个写者，无锁 toString() 会返回刚被清掉的快照。 */
    @Test
    public void getLogReadsUnderTheBufferLock() throws Exception {
        String body = methodBody(stripComments(read(RUNNER)), "public static String getLog(String key)");
        assertTrue("getLog must read the buffer under the pump lock",
                blockAfter(body, "synchronized (sb)").contains("sb.toString()"));
        assertTrue("the empty-buffer fallback must resolve the log file through logFileFor",
                body.contains("File file = logFileFor(key)"));
        assertTrue("the fallback must actually read that file, or a restart loses the log",
                blockAfter(body, "if (file != null && file.exists())").contains("Files.readAllBytes("));
    }

    /** 清除是非破坏性操作，不能复用「停止按钮」的红色语义。 */
    @Test
    public void clearButtonDoesNotBorrowTheStopShape() throws Exception {
        String layout = stripComments(read(LAYOUT));
        int clear = layout.indexOf("@+id/btnClear");
        assertTrue("btnClear is missing", clear >= 0);
        int next = layout.indexOf("android:id=", clear + "@+id/btnClear".length());
        assertTrue("the toolbar must have a control after btnClear", next > clear);
        String block = layout.substring(clear, next);
        assertFalse("the red stop shape means \"stop\", not \"clear\"",
                block.contains("@drawable/shape_lab_stop_btn"));
        assertTrue("the clear button must use the secondary button shape",
                block.contains("@drawable/shape_lab_click_btn"));
    }

    // ------------------------------------------------------------------ helpers

    /** 取出方法体，避免断言被同文件其它方法的文本意外满足。 */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature + " is missing", start >= 0);
        return blockAfter(source.substring(start), "");
    }

    /** 从 {@code marker} 之后的第一个 {@code {} 起截出配平的块，用于判断「在某作用域之内」。 */
    private static String blockAfter(String source, String marker) {
        int start = source.indexOf(marker);
        assertTrue(marker + " is missing", start >= 0);
        int open = source.indexOf('{', start);
        assertTrue(marker + " is not followed by a block", open >= 0);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError(marker + " has no closing brace");
    }

    private static int countOf(String source, String needle) {
        int count = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?sm)^\\s*/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}