package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.github.catvod.crawler.DebugLogStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Writes the crash facts from the dying process and hands them to the crash screen process.
 *
 * <p>CustomActivityOnCrash starts the error activity in a separate {@code :error_activity}
 * process and kills the crashing process, so nothing in memory survives. The report is the only
 * hand-off channel; it is written best-effort and must never take over process termination from
 * the configured crash handler.
 */
public final class CrashReport {

    private static final String DIRECTORY = "crash";
    private static final String PREFIX = "webhtv-crash-";
    private static final String SUFFIX = ".txt";
    private static final int MAX_FILES = 3;
    private static final int MAX_READ_CHARS = 256 << 10;
    private static final int MAX_CMDLINE_BYTES = 256;
    private static volatile boolean installed;

    private CrashReport() {
    }

    /**
     * Chain a crash writer in front of the configured handler (CustomActivityOnCrash). Idempotent.
     *
     * <p>Order matters: CAOC's normal crash path shows the error activity and kills the process
     * without calling the handler it wrapped, so this writer must be the outermost handler.
     * CAOC installs itself from {@code CaocInitProvider} with {@code initOrder="101"}, which runs
     * before androidx.startup's {@code InitializationProvider}; this call from {@code Startup}
     * therefore lands on top of CAOC's handler. Do not move it earlier, and do not let the provider
     * order change: the report would silently stop being written while everything else kept working.
     */
    public static void install(Context context) {
        if (installed) return;
        Context app = application(context);
        if (app == null) return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (previous == null) return;
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    write(app, thread, error);
                } catch (Throwable ignored) {
                    // A report failure must not change crash handling.
                } finally {
                    previous.uncaughtException(thread, error);
                }
            });
            installed = true;
        } catch (RuntimeException ignored) {
            // Very early startup may refuse handler changes; the crash screen then falls back to logcat.
        }
    }

    /** True when a report written by this process can be produced (mostly for tests/diagnostics). */
    static boolean write(Context context, Thread thread, Throwable error) {
        File directory = directory(context);
        if (directory == null) return false;
        String report;
        try {
            report = CrashLogText.format(facts(thread, error));
        } catch (Throwable ignored) {
            return false;
        }
        byte[] data = report.getBytes(StandardCharsets.UTF_8);
        if (data.length == 0) return false;
        File target = new File(directory, PREFIX + System.currentTimeMillis() + SUFFIX);
        File temporary = new File(target.getPath() + ".new");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(data);
            output.flush();
            output.getFD().sync();
        } catch (IOException | RuntimeException ignored) {
            temporary.delete();
            return false;
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            return false;
        }
        rotate(directory);
        return true;
    }

    /** Newest report written by the just-crashed process, or null when absent/stale/unreadable. */
    public static String readRecent(Context context) {
        File[] files = reports(context);
        if (files == null || files.length == 0) return null;
        long now = SystemClock.elapsedRealtime();
        for (File file : files) {
            String text = read(file);
            if (text == null || text.isEmpty()) continue;
            // Do not stop at the first stale report: names are wall-clock based, so a clock jump
            // can make an older report sort first while a fresher one still exists.
            if (CrashLogText.isFresh(CrashLogText.reportElapsed(text), now, CrashLogText.FRESH_WINDOW_MS)) return text;
        }
        return null;
    }

    private static CrashLogText.Facts facts(Thread thread, Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return new CrashLogText.Facts(
                AppVersion.fullName(),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date()),
                SystemClock.elapsedRealtime(),
                processName(),
                Process.myPid(),
                Process.myUid(),
                thread == null ? "" : thread.getName(),
                DebugLogStore.isEnabled(),
                safe(Build.MANUFACTURER) + " " + safe(Build.MODEL),
                "Android " + safe(Build.VERSION.RELEASE) + " (sdk " + Build.VERSION.SDK_INT + ")",
                error == null ? "" : error.getClass().getName(),
                message == null ? "" : message,
                error == null ? "" : Log.getStackTraceString(error));
    }

    private static String processName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return safe(android.app.Application.getProcessName());
            byte[] bytes = new byte[MAX_CMDLINE_BYTES];
            int length;
            try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
                length = input.read(bytes);
            }
            if (length <= 0) return "";
            int end = 0;
            while (end < length && bytes[end] != 0) end++;
            return new String(bytes, 0, end, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void rotate(File directory) {
        try {
            File[] files = reports(directory);
            if (files == null) return;
            for (int i = MAX_FILES; i < files.length; i++) files[i].delete();
        } catch (RuntimeException ignored) {
        }
    }

    private static File[] reports(Context context) {
        File directory = directory(context);
        return directory == null ? null : reports(directory);
    }

    private static File[] reports(File directory) {
        if (directory == null || !directory.isDirectory()) return null;
        File[] files = directory.listFiles((dir, name) -> name.startsWith(PREFIX) && name.endsWith(SUFFIX));
        if (files == null) return null;
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        return files;
    }

    private static String read(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(file.length(), MAX_READ_CHARS));
            byte[] buffer = new byte[8192];
            int read;
            while (bytes.size() < MAX_READ_CHARS && (read = input.read(buffer, 0, Math.min(buffer.length, MAX_READ_CHARS - bytes.size()))) > 0) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static File directory(Context context) {
        if (context == null) return null;
        try {
            File base = context.getFilesDir();
            if (base == null) return null;
            File directory = new File(base, DIRECTORY);
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            return directory;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context application(Context context) {
        if (context == null) return null;
        try {
            Context app = context.getApplicationContext();
            return app == null ? context : app;
        } catch (RuntimeException ignored) {
            return context;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
