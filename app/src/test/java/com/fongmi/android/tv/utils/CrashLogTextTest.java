package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class CrashLogTextTest {

    private static CrashLogText.Facts facts(String stack) {
        return new CrashLogText.Facts(
                "5.6.0-test",
                "2026-10-21T20:00:00.000+0800",
                12345L,
                "com.silent.android.webhtv:error_activity",
                4242,
                10123,
                "main",
                false,
                "Xiaomi TV",
                "Android 14 (sdk 34)",
                "java.lang.IllegalStateException",
                "boom",
                stack);
    }

    @Test
    public void reportCarriesIdentityContextAndStack() {
        String report = CrashLogText.format(facts("java.lang.IllegalStateException: boom\n\tat a.b.C.d(C.java:12)\n\tat e.f.G.h(G.java:3)"));

        assertTrue(report.startsWith(CrashLogText.REPORT_HEADER));
        assertTrue(report.contains("appVersion=5.6.0-test"));
        assertTrue(report.contains("elapsedMs=12345"));
        assertTrue(report.contains("process=com.silent.android.webhtv:error_activity"));
        assertTrue(report.contains("pid=4242"));
        assertTrue(report.contains("thread=main"));
        assertTrue(report.contains("debugLogEnabled=false"));
        assertTrue(report.contains("javaClass=java.lang.IllegalStateException"));
        assertTrue(report.contains("--- stack ---"));
        assertTrue(report.contains("at a.b.C.d(C.java:12)"));
        assertTrue(report.contains("at e.f.G.h(G.java:3)"));
    }

    @Test
    public void reportRedactsUrlsPathsAndSecrets() {
        String report = CrashLogText.format(facts(
                "at leak(Secret.java:1)\n or http://secret.example/path?token=abc123\n at /data/user/0/app/files/report.txt\n socket 192.168.1.5:9978"));

        assertFalse(report.contains("secret.example"));
        assertFalse(report.contains("abc123"));
        assertFalse(report.contains("/data/user/0/"));
        assertFalse(report.contains("192.168."));
    }

    @Test
    public void oversizedStackIsBoundedAndMarked() {
        StringBuilder stack = new StringBuilder();
        while (stack.length() < 300_000) stack.append("at big.Stack.line(File.java:1)\n");

        String report = CrashLogText.format(facts(stack.toString()));

        assertTrue(report.length() <= CrashLogText.MAX_REPORT_CHARS + 4096);
        assertTrue(report.contains("[truncated"));
    }

    @Test
    public void reportElapsedAndPidAreParsedFromHeader() {
        String report = CrashLogText.format(facts("at a.b(C.java:1)"));

        assertEquals(12345L, CrashLogText.reportElapsed(report));
        assertEquals(4242, CrashLogText.reportPid(report));
        assertEquals(-1L, CrashLogText.reportElapsed("no header"));
        assertEquals(-1, CrashLogText.reportPid(null));
    }

    @Test
    public void freshnessRequiresAPositiveBoundedWindow() {
        assertTrue(CrashLogText.isFresh(1_000L, 2_000L, 5_000L));
        assertTrue(CrashLogText.isFresh(1_000L, 6_000L, 5_000L));
        assertFalse(CrashLogText.isFresh(1_000L, 6_001L, 5_000L));
        assertFalse(CrashLogText.isFresh(0L, 2_000L, 5_000L));
        assertFalse(CrashLogText.isFresh(1_000L, 500L, 5_000L));
        assertFalse(CrashLogText.isFresh(1_000L, 2_000L, 0L));
    }

    @Test
    public void logcatFilterKeepsOnlyTheCrashedProcessTail() {
        List<String> lines = List.of(
                "10-21 20:15:01.100   100   200 I Other: a",
                "10-21 20:15:01.200  4242   200 I Crash: b",
                "10-21 20:15:01.300  4242   200 E Crash: c");

        List<String> kept = CrashLogText.filterLogcat(lines, 4242, 2, 4096);
        assertEquals(2, kept.size());
        assertTrue(kept.get(0).contains("Crash: b"));
        assertTrue(kept.get(1).contains("Crash: c"));

        assertEquals(1, CrashLogText.filterLogcat(lines, 4242, 1, 4096).size());
        assertEquals(2, CrashLogText.filterLogcat(lines, -1, 2, 4096).size());
        assertTrue(CrashLogText.filterLogcat(lines, 4242, 2, 10).size() <= 1);
        assertEquals(4242, CrashLogText.linePid(lines.get(1)));
        assertEquals(-1, CrashLogText.linePid("--------- beginning of main"));
    }

    @Test
    public void sessionStatesMissingEvidenceInsteadOfClaimingCoverage() {
        String body = CrashLogText.composeSession(null, List.of(), false, false, false);

        assertTrue(body.startsWith(CrashLogText.SESSION_HEADER));
        assertTrue(body.contains("report=missing-or-stale"));
        assertTrue(body.contains("logcat=unavailable"));
        assertTrue(body.contains("debugLog=off"));
        assertTrue(body.contains("调试日志在崩溃前未开启"));
        assertTrue(body.contains("未找到本次崩溃的堆栈记录"));
        assertTrue(body.contains("# 系统日志不可用。"));
    }

    @Test
    public void sessionIncludesFreshReportAndLogcat() {
        String report = CrashLogText.format(facts("at a.b(C.java:1)"));
        String body = CrashLogText.composeSession(report, List.of("10-21 20:15:01.200  4242   200 E Crash: x"), true, true, true);

        assertTrue(body.contains("report=fresh"));
        assertTrue(body.contains("logcat=own-uid-tail"));
        assertTrue(body.contains("debugLog=on"));
        assertFalse(body.contains("调试日志在崩溃前未开启"));
        assertTrue(body.contains("Crash: x"));
        assertTrue(body.contains(CrashLogText.REPORT_HEADER));
    }

    @Test
    public void oversizedSessionIsTruncated() {
        String[] lines = new String[2000];
        for (int i = 0; i < lines.length; i++) lines[i] = "10-21 20:15:01.200  4242   200 I Crash: " + "x".repeat(200);

        String body = CrashLogText.composeSession(null, List.of(lines), true, false, true);

        assertTrue(body.length() <= CrashLogText.MAX_SESSION_CHARS + 4096);
        assertTrue(body.contains("# [truncated crash session]"));
    }

    @Test
    public void displayUrlKeepsTheRealAddressCopyable() {
        String url = "http://192.168.1.5:9978/debug/logs";
        String display = CrashLogText.wrapUrlForDisplay(url);

        assertTrue(display.contains("\u200B"));
        assertEquals(url, display.replace("\u200B", ""));
        assertEquals("", CrashLogText.wrapUrlForDisplay(null));
    }
}
