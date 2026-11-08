package com.fongmi.android.tv.utils;

import com.github.catvod.crawler.diagnostics.DiagnosticText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure text assembly for the crash-time log session. No Android types: every Android fact is
 * passed in, so the bounds, redaction and freshness rules stay unit-testable.
 */
public final class CrashLogText {

    public static final String REPORT_HEADER = "# webhtv-crash-report v1";
    public static final String SESSION_HEADER = "# crash-session v1";
    public static final int MAX_REPORT_CHARS = 96 * 1024;
    public static final int MAX_STACK_CHARS = 48 * 1024;
    public static final long FRESH_WINDOW_MS = 5 * 60_000L;
    public static final int MAX_SESSION_LINES = 1200;
    public static final int MAX_SESSION_CHARS = 256 * 1024;
    private static final int MAX_FIELD_CHARS = 4 * 1024;
    private static final Pattern LINES = Pattern.compile("\\r\\n|\\r|\\n");

    private CrashLogText() {
    }

    public record Facts(
            String appVersion,
            String wallTime,
            long elapsedRealtimeMs,
            String processName,
            int pid,
            int uid,
            String thread,
            boolean debugLogEnabled,
            String device,
            String system,
            String javaClass,
            String message,
            String stack) {
    }

    /** Crash report written by the dying process. Every field is redacted and bounded. */
    public static String format(Facts facts) {
        StringBuilder out = new StringBuilder(MAX_REPORT_CHARS >> 2);
        line(out, REPORT_HEADER);
        line(out, "# Written by the crashed process; read by the crash screen process.");
        line(out, "appVersion=" + oneLine(facts == null ? null : facts.appVersion()));
        line(out, "wallTime=" + oneLine(facts == null ? null : facts.wallTime()));
        line(out, "elapsedMs=" + (facts == null ? 0L : facts.elapsedRealtimeMs()));
        line(out, "process=" + oneLine(facts == null ? null : facts.processName()));
        line(out, "pid=" + (facts == null ? 0 : facts.pid()));
        line(out, "uid=" + (facts == null ? 0 : facts.uid()));
        line(out, "thread=" + oneLine(facts == null ? null : facts.thread()));
        line(out, "debugLogEnabled=" + (facts != null && facts.debugLogEnabled()));
        line(out, "device=" + oneLine(facts == null ? null : facts.device()));
        line(out, "system=" + oneLine(facts == null ? null : facts.system()));
        line(out, "javaClass=" + oneLine(facts == null ? null : facts.javaClass()));
        line(out, "message=" + oneLine(facts == null ? null : facts.message()));
        line(out, "--- stack ---");
        appendStack(out, facts == null ? null : facts.stack());
        return out.toString();
    }

    private static void appendStack(StringBuilder out, String stack) {
        int budget = Math.min(MAX_STACK_CHARS, MAX_REPORT_CHARS - out.length());
        int used = 0;
        boolean truncated = false;
        if (stack != null) {
            for (String raw : LINES.split(stack)) {
                String text = oneLine(raw);
                if (text.isEmpty()) continue;
                if (used + text.length() + 1 > budget) {
                    truncated = true;
                    break;
                }
                line(out, text);
                used += text.length() + 1;
            }
        }
        if (stack == null || stack.isEmpty()) line(out, "# stack unavailable");
        else if (truncated) line(out, "# [truncated stack]");
    }

    /** elapsedRealtime recorded in a report, or -1 when absent/invalid. */
    public static long reportElapsed(String report) {
        return reportValue(report, "elapsedMs=", -1L);
    }

    /** pid recorded in a report, or -1 when absent/invalid. */
    public static int reportPid(String report) {
        long value = reportValue(report, "pid=", -1L);
        return value > 0 && value <= Integer.MAX_VALUE ? (int) value : -1;
    }

    private static long reportValue(String report, String prefix, long fallback) {
        if (report == null || report.isEmpty()) return fallback;
        for (String line : LINES.split(report)) {
            if (!line.startsWith(prefix)) continue;
            try {
                return Long.parseLong(line.substring(prefix.length()).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean isFresh(long reportElapsedMs, long nowElapsedMs, long windowMs) {
        if (reportElapsedMs <= 0 || nowElapsedMs <= 0 || windowMs <= 0) return false;
        long delta = nowElapsedMs - reportElapsedMs;
        return delta >= 0 && delta <= windowMs;
    }

    /** Keep only lines emitted by the crashed process and return its bounded tail. */
    public static List<String> filterLogcat(List<String> lines, int pid, int maxLines, int maxBytes) {
        if (lines == null || lines.isEmpty() || maxLines <= 0 || maxBytes <= 0) return List.of();
        List<String> kept = new ArrayList<>();
        int bytes = 0;
        for (int i = lines.size() - 1; i >= 0 && kept.size() < maxLines; i--) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) continue;
            if (pid > 0 && linePid(line) != pid) continue;
            int cost = line.length() + 1;
            if (bytes + cost > maxBytes) break;
            bytes += cost;
            kept.add(line);
        }
        Collections.reverse(kept);
        return kept;
    }

    /** System-logcat process id in a {@code -v threadtime} line, or -1. */
    static int linePid(String line) {
        int field = 0;
        int start = -1;
        for (int i = 0; i <= line.length(); i++) {
            boolean separator = i == line.length() || line.charAt(i) == ' ' || line.charAt(i) == '\t';
            if (!separator) {
                if (start < 0) start = i;
                continue;
            }
            if (start < 0) continue;
            if (field == 2) return positiveInt(line, start, i);
            field++;
            start = -1;
        }
        return -1;
    }

    private static int positiveInt(String text, int start, int end) {
        int value = 0;
        if (end <= start) return -1;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + (c - '0');
            if (value > Integer.MAX_VALUE / 10) return -1;
        }
        return value > 0 ? value : -1;
    }

    /** Body injected into the existing diagnostic log buffer for the crash session. */
    public static String composeSession(String report, List<String> logcat, boolean logcatAvailable,
                                        boolean reportFresh, boolean debugLogEnabled) {
        StringBuilder out = new StringBuilder(4096);
        line(out, SESSION_HEADER);
        line(out, "# 崩溃会话日志：由崩溃界面自动开启，仅在本界面存活期间提供下载。");
        line(out, "# coverage: report=" + (reportFresh && report != null && !report.isEmpty() ? "fresh" : "missing-or-stale")
                + " logcat=" + (logcatAvailable ? "own-uid-tail" : "unavailable")
                + " debugLog=" + (debugLogEnabled ? "on" : "off"));
        if (!debugLogEnabled) {
            line(out, "# 调试日志在崩溃前未开启：崩溃前只能提供系统日志（同应用 UID 尾部），可能不完整。");
        }
        line(out, "# === crash report ===");
        if (report != null && !report.isEmpty()) {
            for (String raw : LINES.split(report)) line(out, oneLine(raw));
        } else {
            line(out, "# 未找到本次崩溃的堆栈记录（可能为 native 崩溃、被系统杀死或写入失败）。");
        }
        line(out, "# === system logcat (own uid tail) ===");
        if (logcatAvailable && logcat != null && !logcat.isEmpty()) {
            for (String raw : logcat) line(out, oneLine(raw));
        } else {
            line(out, "# 系统日志不可用。");
        }
        return truncateSession(out);
    }

    private static String truncateSession(StringBuilder source) {
        String text = source.toString();
        if (text.length() <= MAX_SESSION_CHARS) return text;
        String[] lines = LINES.split(text);
        StringBuilder out = new StringBuilder(MAX_SESSION_CHARS + 128);
        int count = 0;
        for (String line : lines) {
            if (count >= MAX_SESSION_LINES || out.length() + line.length() + 1 > MAX_SESSION_CHARS) {
                line(out, "# [truncated crash session]");
                break;
            }
            line(out, line);
            count++;
        }
        return out.toString();
    }

    /**
     * Insert zero-width spaces after URL delimiters so TV text views can wrap a long address.
     * Copying always uses the original URL, never this display form.
     */
    public static String wrapUrlForDisplay(String url) {
        if (url == null || url.isEmpty()) return "";
        StringBuilder out = new StringBuilder(url.length() + 16);
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            out.append(c);
            if (c == ':' || c == '/' || c == '?' || c == '&' || c == '=') out.append('\u200B');
        }
        return out.toString();
    }

    /** Redacted, single-line form used for every untrusted text field. */
    public static String oneLine(String value) {
        if (value == null || value.isEmpty()) return "";
        String clean = DiagnosticText.clean(value).text();
        return clean.length() > MAX_FIELD_CHARS ? clean.substring(0, MAX_FIELD_CHARS) : clean;
    }

    private static void line(StringBuilder out, String text) {
        out.append(text).append('\n');
    }
}
