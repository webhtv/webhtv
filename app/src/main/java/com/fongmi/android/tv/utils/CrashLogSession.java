package com.fongmi.android.tv.utils;

import android.content.Context;

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
            String report = CrashReport.readRecent(context);
            List<String> logcat = logcatTail(CrashLogText.reportPid(report));
            String body = CrashLogText.composeSession(report, logcat, logcat != null && !logcat.isEmpty(), report != null, !temporary);
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
