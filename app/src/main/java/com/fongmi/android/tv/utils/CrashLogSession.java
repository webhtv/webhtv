package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.github.catvod.crawler.DebugLogStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the crash-session log served by the existing debug log page.
 *
 * <p>Runs on a worker thread: it reads the report dropped by the crashed process, appends the
 * same-UID system logcat tail (the only pre-crash evidence when the debug log was off), starts a
 * temporary in-memory diagnostic session and seeds it. Everything is best-effort; the crash
 * screen must survive any failure here.
 */
public final class CrashLogSession {

    private static final int MAX_LOG_LINES = 800;
    private static final int MAX_LOG_CHARS = 192 << 10;
    private static final long LOGCAT_TIMEOUT_MS = 1500;
    private static boolean prepared;

    private CrashLogSession() {
    }

    public static synchronized void prepare(Context context) {
        if (prepared) return;
        prepared = true;
        try {
            boolean temporary = DebugLogStore.beginCrashSession();
            String history = PreviousProcessCrashRecovery.getRecoveryDetails(intentOf(context));
            int historyPid = historyPid(history);
            String report = CrashReport.readRecent(context);
            int reportPid = CrashLogText.reportPid(report);
            // A report written by a Java fatal handler is only this entry's evidence when the entry
            // itself is that Java death; a platform recovery has no report of its own.
            if (discardReport(history, report)) report = null;
            List<String> logcat = logcatTail(historyPid > 0 ? historyPid : reportPid);
            String body = withHistory(CrashLogText.composeSession(report, logcat, logcat != null && !logcat.isEmpty(), report != null, !temporary), history);
            for (String line : body.split("\n", -1)) {
                if (!line.isEmpty()) DebugLogStore.appendCrashContext(line);
            }
        } catch (Throwable error) {
            try {
                DebugLogStore.appendCrashContext("# crash-session unavailable: " + error.getClass().getName());
            } catch (Throwable ignored) {
                // The debug service still starts; the page then reports the original state.
            }
        }
    }

    /** The crash screen passes itself; its intent carries the recovery history of this entry. */
    private static Intent intentOf(Context context) {
        return context instanceof Activity ? ((Activity) context).getIntent() : null;
    }

    /**
     * Whether the recent report belongs to a different death than this entry.
     *
     * <p>A Java fatal handoff keeps a report only when its reliable event identity matches. A
     * platform recovery (CRASH/CRASH_NATIVE/ANR) produced no Java report at all: a report left by an
     * older Java death must not be presented as its evidence even when the pid was reused, and an
     * unprovable identity is never mixed in. Without a history the recent report stays as before.
     */
    static boolean discardReport(String history,String report) {
        if(history==null || history.isEmpty() || report==null || report.isEmpty()) return false;
        String event=metadata(history,"event");
        return !"JAVA_FATAL".equals(metadata(history,"reason")) || event.isEmpty()
                || !event.equals(metadata(report,"event"))
                || !metadata(history,"process").equals(metadata(report,"process"));
    }

    private static String metadata(String text,String key) {
        for(String line:text.split("\n")) {
            if(line.startsWith("---")) break;
            if(line.startsWith(key+"=")) return line.substring(key.length()+1);
            if(line.startsWith("reason=")) break;
        }
        return "";
    }
    private static int historyPid(String history) {
        if (history == null) return -1;
        for (String line : history.split("\n")) {
            if (!line.startsWith("pid=")) continue;
            try {
                return Integer.parseInt(line.substring(4).trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String withHistory(String body, String history) {
        if (history == null || history.isEmpty()) return body;
        List<String> lines = new ArrayList<>();
        String[] existing = body.split("\n", -1);
        lines.add(existing.length > 0 ? existing[0] : "");
        lines.add("# === previous process recovery ===");
        for (String line : history.split("\n")) if (!line.isEmpty()) lines.add(line);
        for (int i = 1; i < existing.length; i++) lines.add(existing[i]);
        return String.join("\n", lines);
    }

    private static List<String> logcatTail(int pid) {
        Process process = null;
        Thread watchdog = null;
        StringBuilder output = new StringBuilder();
        try {
            process = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "4000")
                    .redirectErrorStream(true).start();
            watchdog = timeout(process);
            watchdog.start();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while (output.length() < MAX_LOG_CHARS && (read = input.read(buffer)) > 0) {
                    output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException | RuntimeException error) {
            return null;
        } finally {
            if (watchdog != null) watchdog.interrupt();
            if (process != null) {
                try {
                    process.destroy();
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (output.length() == 0) return List.of();
        List<String> raw = new ArrayList<>();
        for (String line : output.toString().split("\n")) if (!line.isEmpty()) raw.add(line);
        if (raw.isEmpty()) return List.of();
        return CrashLogText.filterLogcat(raw, pid, MAX_LOG_LINES, MAX_LOG_CHARS);
    }

    private static Thread timeout(Process process) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(LOGCAT_TIMEOUT_MS);
            } catch (InterruptedException ignored) {
                return;
            }
            try {
                process.destroy();
            } catch (RuntimeException ignored) {
            }
        }, "crash-logcat-timeout");
        watchdog.setDaemon(true);
        return watchdog;
    }
}
