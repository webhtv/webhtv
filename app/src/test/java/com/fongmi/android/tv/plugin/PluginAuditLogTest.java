package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 审计日志测试。重点是脱敏：**query 绝不能进日志**，因为 D2 放弃了域名白名单，
 * 这份记录是唯一能追到"谁把数据发去了哪"的证据链。
 */
public class PluginAuditLogTest {

    /** 内存后端：JVM 单测没有 {@code Init.context()}，真实文件后端在这里不可用。 */
    private static final class MemorySink implements PluginAuditStore.Sink {
        private String text = "";

        @Override
        public String read() {
            return text;
        }

        @Override
        public void write(String text) {
            this.text = text == null ? "" : text;
        }
    }

    /**
     * 记录"每次写入发生在哪条线程、写了什么"的计数后端。
     *
     * <p>主线程改造要防的正是"记账线程自己写了文件"，所以线程名和内容必须一起记下来：
     * 只数次数证明不了写盘没发生在调用方线程上。</p>
     */
    private static final class CountingSink implements PluginAuditStore.Sink {
        private final List<String> texts = Collections.synchronizedList(new ArrayList<>());
        private final List<String> threads = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String read() {
            return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
        }

        @Override
        public void write(String text) {
            texts.add(text == null ? "" : text);
            threads.add(Thread.currentThread().getName());
        }

        String last() {
            return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
        }

        int writes() {
            return texts.size();
        }
    }

    /**
     * 可阻塞的计数后端：把"写入器正在写"变成可确定观察的状态。
     *
     * <p>核心断言是"记账调用自己没碰文件"。只靠时间窗去猜必然会 flaky——写入器完全可能在
     * 断言执行之前就写完了。这里把写入器钉在 write() 里，计数就不可能在中途变化。</p>
     */
    private static final class BlockingSink implements PluginAuditStore.Sink {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);
        private final List<String> texts = Collections.synchronizedList(new ArrayList<>());
        private final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        /** 已"写完"的次数（真正走完 write()），用来与"进到 write() 里被钉住"区分开。 */
        private final int[] completed = new int[1];

        @Override
        public String read() {
            return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
        }

        @Override
        public void write(String text) {
            texts.add(text == null ? "" : text);
            threads.add(Thread.currentThread().getName());
            entered.countDown();
            try {
                proceed.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            // 只有真正写完才算一次完成的写入；"已经进来但还卡着"必须与"写完了"区分开，
            // 否则断言分不清"调用方自己写了"和"写入器正在写"。
            synchronized (completed) {
                completed[0]++;
            }
        }

        int completedWrites() {
            synchronized (completed) {
                return completed[0];
            }
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(5, TimeUnit.SECONDS);
        }

        void proceed() {
            proceed.countDown();
        }

        int writes() {
            return texts.size();
        }

        String last() {
            return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
        }

        String lastThread() {
            return threads.isEmpty() ? "" : threads.get(threads.size() - 1);
        }
    }

    private PluginAuditLog log;

    @Before
    public void setUp() {
        log = PluginAuditLog.get();
        // 每条用例都从"真实主线程判定"和"空日志"开始，避免上一条用例留下的强制异步开关。
        PluginAuditLog.forceAsyncWriterForTest(null);
        log.clear();
    }

    @Test
    public void recordsRequestWithMaskedUrl() {
        log.recordRequest("douban.comments", "1.0.0", PluginEventBus.EVENT_UI_CONTENT_SHOWN,
                "net.request", "https://m.douban.com/rexxar/api/v2/movie/123/comments?start=0&token=SECRET#frag",
                200, 123L, "ok");

        List<PluginAuditLog.Entry> entries = log.entries();
        assertEquals(1, entries.size());
        PluginAuditLog.Entry entry = entries.get(0);
        assertEquals("douban.comments", entry.pluginId());
        assertEquals("1.0.0", entry.pluginVersion());
        assertEquals("m.douban.com", entry.host());
        assertEquals("/rexxar/api/v2/movie/123/comments", entry.path());
        assertEquals(200, entry.status());
        assertEquals("ok", entry.outcome());
        assertEquals("request", entry.kind());
        assertFalse(entry.path().contains("SECRET"));
    }

    @Test
    public void queryIsNeverRetained() {
        PluginAuditLog.Target target = PluginAuditLog.target("https://example.com/a/b?api_key=SECRET&x=1");
        assertEquals("example.com", target.host());
        assertEquals("/a/b", target.path());
        assertFalse(target.path().contains("SECRET"));
        assertFalse(target.path().contains("api_key"));
    }

    @Test
    public void malformedUrlsDoNotThrow() {
        assertEquals("", PluginAuditLog.target(null).host());
        assertEquals("", PluginAuditLog.target("").host());
        assertEquals("", PluginAuditLog.target("   ").host());
        // 无 scheme、无 path
        assertEquals("example.com", PluginAuditLog.target("example.com").host());
        assertEquals("/", PluginAuditLog.target("example.com").path());
        // 只有 scheme
        assertEquals("", PluginAuditLog.target("https://").host());
    }

    @Test
    public void uuidSizedPathsAreTruncatedNotDropped() {
        String longPath = "/" + "a".repeat(1000);
        PluginAuditLog.Target target = PluginAuditLog.target("https://example.com" + longPath);
        assertEquals("example.com", target.host());
        assertTrue(target.path().length() <= 256);
        assertTrue(target.path().startsWith("/aaa"));
    }

    @Test
    public void entriesAreBoundedAndDropOldest() {
        for (int i = 0; i < PluginAuditLog.MAX_ENTRIES + 25; i++) {
            log.recordRequest("p", "1", null, "net.request", "https://example.com/" + i, 200, 1L, "ok");
        }
        assertEquals(PluginAuditLog.MAX_ENTRIES, log.size());
        // 最旧的已被丢弃：第一条应该是 index=25
        assertTrue(log.entries().get(0).path().endsWith("/25"));
    }

    @Test
    public void entriesForIsolatesOnePlugin() {
        log.recordRequest("a", "1", null, "net.request", "https://a.com/x", 200, 1L, "ok");
        log.recordRequest("b", "1", null, "net.request", "https://b.com/y", 200, 1L, "ok");
        log.recordFailure("a", "1", null, "net.request", "blocked", "missing permission");

        assertEquals(2, log.entriesFor("a").size());
        assertEquals(1, log.entriesFor("b").size());
        assertTrue(log.entriesFor("nobody").isEmpty());
        assertTrue(log.entriesFor(null).isEmpty());
    }

    @Test
    public void failureRecordsCapabilityNotUrl() {
        log.recordFailure("p", "2", PluginEventBus.EVENT_UI_CONTENT_SHOWN, "net.request", "blocked",
                "permission not granted");

        PluginAuditLog.Entry entry = log.entries().get(0);
        assertEquals("failure", entry.kind());
        assertEquals("net.request", entry.method());
        assertEquals("blocked", entry.outcome());
        assertEquals("", entry.host());
    }

    /**
     * 进程重启后必须还能读到上次的记录——崩溃现场正是最需要证据的时刻。
     *
     * <p>{@link PluginAuditLog#reloadForTest()} 表达"重新打开日志"：单例是 {@code static final}，
     * 测试造不出第二个实例，所以用丢弃内存副本 + 重新装载来模拟同一件事。</p>
     */
    @Test
    public void entriesSurviveReload() {
        MemorySink sink = new MemorySink();
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            log.recordRequest("persist.probe", "2.0.0", null, "net.request",
                    "https://example.com/a?token=SECRET", 200, 42L, "ok");

            PluginAuditLog.reloadForTest();

            List<PluginAuditLog.Entry> restored = log.entries();
            assertEquals(1, restored.size());
            PluginAuditLog.Entry entry = restored.get(0);
            assertEquals("persist.probe", entry.pluginId());
            assertEquals("2.0.0", entry.pluginVersion());
            assertEquals("example.com", entry.host());
            assertEquals("/a", entry.path());
            assertEquals(200, entry.status());
            assertEquals(42L, entry.durationMs());
            assertEquals("ok", entry.outcome());
            assertEquals("request", entry.kind());
            // 可空字段必须原样还原为 null，而不是变成字符串 "null"。
            assertNull(entry.event());
            assertEquals("net.request", entry.method());
            assertFalse(sink.read().contains("SECRET"));
        } finally {
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    /**
     * 装载时必须跳过坏行并裁到环形容量：落盘文件可能被截断、被别的版本写过、或比内存容量更大。
     *
     * <p>坏行让整份日志报废是最糟的结果——日志被截断恰恰发生在崩溃现场，而那正是唯一需要它的时刻。</p>
     */
    @Test
    public void reloadSkipsBrokenLinesAndTrimsToCapacity() {
        StringBuilder persisted = new StringBuilder();
        persisted.append("not json at all").append('\n');
        persisted.append("{\"kind\":\"request\"}").append('\n'); // 缺字段但可解析，kind 在
        persisted.append("[1,2,3]").append('\n');
        int total = PluginAuditLog.MAX_ENTRIES + 40;
        for (int i = 0; i < total; i++) {
            if (persisted.length() > 0) persisted.append('\n');
            persisted.append("{\"kind\":\"request\",\"pluginId\":\"p\",\"pluginVersion\":\"1\",")
                    .append("\"host\":\"e.com\",\"path\":\"/").append(i).append("\",")
                    .append("\"status\":200,\"durationMs\":1,\"outcome\":\"ok\"}");
        }

        MemorySink sink = new MemorySink();
        sink.write(persisted.toString());
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            PluginAuditLog.reloadForTest();

            List<PluginAuditLog.Entry> restored = log.entries();
            assertEquals(PluginAuditLog.MAX_ENTRIES, restored.size());
            // 最新一条在末尾，最旧的已被裁掉。
            assertEquals("/" + (total - 1), restored.get(restored.size() - 1).path());
            assertEquals("/" + (total - PluginAuditLog.MAX_ENTRIES), restored.get(0).path());
            for (PluginAuditLog.Entry entry : restored) assertEquals("p", entry.pluginId());
        } finally {
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    /** 用户清空之后必须连落盘一起清；否则下次启动会把刚删掉的记录原样读回来。 */
    @Test
    public void clearAlsoDropsPersistedCopy() {
        MemorySink sink = new MemorySink();
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            log.recordRequest("p", "1", null, "net.request", "https://e.com/x", 200, 1L, "ok");
            assertFalse(sink.read().isEmpty());

            log.clear();
            PluginAuditLog.reloadForTest();

            assertEquals(0, log.size());
            assertTrue(sink.read().isEmpty());
        } finally {
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    /**
     * 落盘失败不能影响记账本身，更不能把异常冒到网络请求的返回路径上
     * （{@code PluginNetBridge.request} 只 catch {@code IOException|RuntimeException}）。
     */
    @Test
    public void writeFailureKeepsMemoryEntry() {
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(new PluginAuditStore.Sink() {
            @Override
            public String read() {
                return "";
            }

            @Override
            public void write(String text) {
                throw new IllegalStateException("disk full");
            }
        }));
        try {
            log.recordRequest("p", "1", null, "net.request", "https://e.com/x", 200, 1L, "ok");

            assertEquals(1, log.size());
            assertEquals("e.com", log.entries().get(0).host());
        } finally {
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    /**
     * 主线程记账不许碰文件：调用方返回之后 Sink 一次都不该被写，只有写入器线程才写。
     *
     * <p>这是整个改造的目的本身——事件派发在主线程，插件一抛错就会走到这里；同步重写整份
     * 文件就是 StrictMode 磁盘违规加掉帧。</p>
     */
    @Test
    public void mainThreadDelegatesFileWorkToTheWriter() throws Exception {
        BlockingSink sink = new BlockingSink();
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            PluginAuditLog.forceAsyncWriterForTest(true);
            int baseline = sink.completedWrites();

            log.recordRequest("p", "1", null, "net.request", "https://e.com/x", 200, 1L, "ok");

            // 记账调用已经返回。此刻没有任何一次写入"完成"过：若调用方自己同步写了文件，
            // 它会被 Sink 的 proceed 钉住 5 秒，然后以 completed=baseline+1 返回，这里立刻红。
            assertEquals(1, log.size());
            assertEquals("记账调用本身不许写文件", baseline, sink.completedWrites());
            assertTrue("写入器应当已经因为待写内容启动", sink.awaitEntered());
            assertEquals("写入必须发生在别的线程上", "plugin-audit-writer", sink.lastThread());

            sink.proceed();
            PluginAuditLog.flushForTest();

            assertEquals(baseline + 1, sink.completedWrites());
            assertTrue(sink.last().contains("/x"));
        } finally {
            PluginAuditLog.forceAsyncWriterForTest(null);
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    /**
     * 异步连发之后磁盘必须收敛到最后那一版，且过程里不许出现"旧覆盖新"。
     *
     * <p>没有版本号时这是真会发生的：主线程排队的旧快照与网络线程同步写的新快照交错，
     * 磁盘最终可能停在少了最后几条的旧内容上——而那几条往往正是刚发生的失败。</p>
     */
    @Test
    public void asyncWritesNeverLetAnOlderSnapshotWin() {
        CountingSink sink = new CountingSink();
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            log.clear();
            PluginAuditLog.forceAsyncWriterForTest(true);

            int rounds = 5;
            for (int i = 0; i < rounds; i++) {
                log.recordRequest("p", "1", null, "net.request", "https://e.com/" + i, 200, 1L, "ok");
            }
            PluginAuditLog.flushForTest();

            String last = sink.last();
            assertTrue(last.contains("/" + (rounds - 1)));
            assertEquals("最后一版必须包含全部 " + rounds + " 条", rounds,
                    last.split("\n").length);

            // 顺序性：每次写入的行数只能单调不减，出现回退就说明旧快照盖了新快照。
            List<String> texts = new ArrayList<>(sink.texts);
            for (int i = 1; i < texts.size(); i++) {
                assertTrue("第 " + i + " 次写入比上一次少了记录，旧快照覆盖了新快照",
                        texts.get(i).split("\n").length >= texts.get(i - 1).split("\n").length);
            }
        } finally {
            PluginAuditLog.forceAsyncWriterForTest(null);
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    /**
     * 待写期间用户点了清空：落盘最终必须是空串，不许被那份过期快照写回来。
     *
     * <p>这是版本号带来的直接收益——清空会拿到更大的版本号，任何更旧的排队内容自动失效。</p>
     */
    @Test
    public void clearWhileWriteIsPendingStillEndsEmpty() throws Exception {
        BlockingSink sink = new BlockingSink();
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            PluginAuditLog.forceAsyncWriterForTest(true);

            // 先让写入器卡在"写那份有记录的旧快照"里，再让用户清空：
            // 这正是旧快照可能盖掉新状态的窗口。
            log.recordRequest("p", "1", null, "net.request", "https://e.com/x", 200, 1L, "ok");
            assertTrue(sink.awaitEntered());
            log.clear();
            assertEquals(0, log.size());

            sink.proceed();
            PluginAuditLog.flushForTest();

            assertEquals("用户清空后落盘必须收敛到空串", "", sink.last());
            assertEquals(0, log.size());

            PluginAuditLog.reloadForTest();
            assertEquals("清空之后重装载不许把旧记录读回来", 0, log.size());
        } finally {
            PluginAuditLog.forceAsyncWriterForTest(null);
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    @Test
    public void clearEmptiesLog() {
        log.recordRequest("p", "1", null, "net.request", "https://e.com", 200, 1L, "ok");
        assertEquals(1, log.size());
        log.clear();
        assertEquals(0, log.size());
        assertTrue(log.entries().isEmpty());
    }

    /** bodySize 必须真的落盘并原样回读：它是"插件发出去多少字节"的唯一证据，只在内存里对不算数。 */
    @Test
    public void bodySizeSurvivesReload() {
        MemorySink sink = new MemorySink();
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            log.recordRequest("body.probe", "3.0.0", null, "net.request",
                    "https://example.com/upload", 200, 7L, "ok", 12L);
            PluginAuditLog.reloadForTest();
            List<PluginAuditLog.Entry> restored = log.entries();
            assertEquals(1, restored.size());
            assertEquals(12L, restored.get(0).bodySize());
        } finally {
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }

    /** 加 bodySize 之前的旧快照里没有这个键：必须照读并回落 0，不许整行报废。 */
    @Test
    public void legacySnapshotWithoutBodySizeFallsBackToZero() {
        MemorySink sink = new MemorySink();
        sink.write("{\"kind\":\"request\",\"pluginId\":\"legacy\",\"pluginVersion\":\"1\","
                + "\"host\":\"e.com\",\"path\":\"/x\",\"status\":200,\"durationMs\":5,"
                + "\"outcome\":\"ok\"}");
        PluginAuditLog.storeForTest(PluginAuditStore.useSinkForTest(sink));
        try {
            PluginAuditLog.reloadForTest();
            List<PluginAuditLog.Entry> restored = log.entries();
            assertEquals(1, restored.size());
            assertEquals(0L, restored.get(0).bodySize());
        } finally {
            PluginAuditLog.storeForTest(null);
            PluginAuditStore.useSinkForTest(null);
        }
    }
}
