package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.fongmi.android.tv.ui.activity.CrashActivity;
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
import java.util.function.BiConsumer;

/** Writes Java fatal facts and explicitly hands off to the isolated recovery process. */
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

    /** Installed from App.attachBaseContext, before providers and business initialization. */
    public static synchronized void install(Context context) {
        if (installed || isErrorProcess(context)) return;
        Context app = application(context);
        if (app == null) return;
        try {
            Thread.setDefaultUncaughtExceptionHandler(new FatalHandler(
                    (thread, error) -> write(app, thread, error),
                    (thread, error) -> launchRecovery(app, error),
                    CrashReport::terminateProcess));
            installed = true;
        } catch (RuntimeException ignored) {
            // Very early startup may refuse handler replacement; the recovery gate still runs.
        }
    }

    public static boolean isErrorProcess(Context context) {
        return processName().endsWith(":error_activity");
    }

    /** True only for the default process, which owns cross-process recovery state. */
    public static boolean isMainProcess(Context context) {
        try {
            android.content.pm.ApplicationInfo info = context.getApplicationInfo();
            String expected = info == null ? null : info.processName;
            if (expected == null || expected.isEmpty()) expected = context.getPackageName();
            return expected != null && !expected.isEmpty() && expected.equals(processName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void launchRecovery(Context context, Throwable error) {
        Intent intent = new Intent(context, CrashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        String details = "Java fatal exception (details unavailable)";
        try {
            String stack = error == null ? "" : Log.getStackTraceString(error);
            if (stack != null && !stack.isEmpty()) details = stack;
        } catch (Throwable ignored) {
        }
        if (details.length() > 64 * 1024) details = details.substring(0, 64 * 1024);
        intent.putExtra("cat.ereza.customactivityoncrash.EXTRA_STACK_TRACE", details);
        try {
            PreviousProcessCrashRecovery.decorateJavaCrashIntent(context, intent);
        } catch (Throwable ignored) {
        }
        context.startActivity(intent);
    }

    private static void terminateProcess() {
        try {
            Process.killProcess(Process.myPid());
        } finally {
            System.exit(10);
        }
    }

    static final class FatalHandler implements Thread.UncaughtExceptionHandler {
        private final BiConsumer<Thread, Throwable> report;
        private final BiConsumer<Thread, Throwable> handoff;
        private final Runnable terminate;
        private boolean handling;

        FatalHandler(BiConsumer<Thread, Throwable> report, BiConsumer<Thread, Throwable> handoff, Runnable terminate) {
            this.report = report;
            this.handoff = handoff;
            this.terminate = terminate;
        }

        @Override
        public synchronized void uncaughtException(Thread thread, Throwable error) {
            if (handling) return;
            handling = true;
            try {
                try {
                    report.accept(thread, error);
                } catch (Throwable ignored) {
                }
                try {
                    handoff.accept(thread, error);
                } catch (Throwable ignored) {
                }
            } finally {
                terminate.run();
            }
        }
    }

    /** True when a report written by this process can be produced (mostly for tests/diagnostics). */
    static boolean write(Context context, Thread thread, Throwable error) {
        File directory = directory(context);
        if (directory == null) return false;
        String report;
        try {
            report = withIdentity(CrashLogText.format(facts(thread, error)), PreviousProcessCrashRecovery.javaEvent(context));
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

    static String withIdentity(String report,String event) {
        int newline=report.indexOf('\n');
        return report.substring(0,newline+1)+"event="+event+"\n"+report.substring(newline+1);
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