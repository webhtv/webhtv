package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * 审计日志测试。重点是脱敏：**query 绝不能进日志**，因为 D2 放弃了域名白名单，
 * 这份记录是唯一能追到"谁把数据发去了哪"的证据链。
 */
public class PluginAuditLogTest {

    private PluginAuditLog log;

    @Before
    public void setUp() {
        log = PluginAuditLog.get();
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

    @Test
    public void clearEmptiesLog() {
        log.recordRequest("p", "1", null, "net.request", "https://e.com", 200, 1L, "ok");
        assertEquals(1, log.size());
        log.clear();
        assertEquals(0, log.size());
        assertTrue(log.entries().isEmpty());
    }
}
