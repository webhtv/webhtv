package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * 落盘层测试。重点不是"能写能读"，而是三条会让审计失去证据价值的行为：
 * 坏行不能让整份日志报废、超限只能按整行裁、清空必须真的读回为空。
 */
public class PluginAuditStoreTest {

    /** 内存后端：JVM 单测没有 {@code Init.context()}，真实文件后端在这里根本不可用。 */
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

    @Test
    public void writeIsReadableBack() {
        MemorySink sink = new MemorySink();
        PluginAuditStore store = PluginAuditStore.useSinkForTest(sink);
        try {
            store.write("{\"kind\":\"request\"}\n{\"kind\":\"failure\"}");
            assertEquals("{\"kind\":\"request\"}\n{\"kind\":\"failure\"}", store.read());
        } finally {
            PluginAuditStore.useSinkForTest(null);
        }
    }

    @Test
    public void clearedStoreReadsBackEmpty() {
        MemorySink sink = new MemorySink();
        PluginAuditStore store = PluginAuditStore.useSinkForTest(sink);
        try {
            store.write("{\"kind\":\"request\"}");
            store.write("");
            assertEquals("", store.read());
            assertTrue(sink.read().isEmpty());
        } finally {
            PluginAuditStore.useSinkForTest(null);
        }
    }

    @Test
    public void nullNeverReachesTheSink() {
        MemorySink sink = new MemorySink();
        PluginAuditStore store = PluginAuditStore.useSinkForTest(sink);
        try {
            store.write(null);
            assertEquals("", store.read());
        } finally {
            PluginAuditStore.useSinkForTest(null);
        }
    }

    @Test
    public void brokenLinesAreRejectedNotThrown() {
        assertNull(PluginAuditStore.object("not json at all"));
        assertNull(PluginAuditStore.object(""));
        assertNull(PluginAuditStore.object("[1,2,3]"));
        assertNull(PluginAuditStore.object("{\"kind\":\"request\""));
        JsonObject object = PluginAuditStore.object("{\"kind\":\"request\"}");
        assertTrue(object != null && "request".equals(object.get("kind").getAsString()));
    }

    @Test
    public void tailKeepsWholeLinesOnly() {
        String text = "line1\nline2\nline3";
        // 行数上限优先：只留最后一行。
        assertEquals("line3", PluginAuditStore.tail(text, Long.MAX_VALUE, 1));
        // 字节上限切在第二行中间时，整行丢弃而不是留半条。
        assertEquals("line3", PluginAuditStore.tail(text, 6L, 100));
        assertEquals("line3", PluginAuditStore.tail(text, 11L, 100));
        assertEquals("line2\nline3", PluginAuditStore.tail(text, 12L, 100));
        // 未超限时原样返回。
        assertEquals(text, PluginAuditStore.tail(text, Long.MAX_VALUE, 100));
        assertEquals("", PluginAuditStore.tail(null, 100L, 10));
        assertEquals("", PluginAuditStore.tail("", 100L, 10));
        assertEquals("", PluginAuditStore.tail(text, 0L, 10));
        assertEquals("", PluginAuditStore.tail(text, 100L, 0));
    }

    @Test
    public void tailNeverKeepsMoreThanMaxLines() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            if (i > 0) builder.append('\n');
            builder.append("{\"kind\":\"request\",\"path\":\"/").append(i).append("\"}");
        }
        String text = PluginAuditStore.tail(builder.toString(), 512L * 1024L, PluginAuditLog.MAX_ENTRIES);
        String[] lines = text.split("\n");
        assertTrue(lines.length <= PluginAuditLog.MAX_ENTRIES);
        assertEquals("{\"kind\":\"request\",\"path\":\"/599\"}", lines[lines.length - 1]);
        assertFalse(text.contains("/0\""));
    }
}
