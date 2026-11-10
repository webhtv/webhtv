package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import androidx.annotation.RequiresApi;

import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Confirmed-death recovery for the main process.
 *
 * <p>The state file is the only cross-process authority: the dying process persists a Java fact,
 * the crash screen confirms it, and the next launch consumes it once before any configuration is
 * loaded. Platform history is queried on a worker thread; the caller continues only when the gate
 * releases. Every failure releases the gate instead of blocking or looping.
 */
public final class PreviousProcessCrashRecovery {

    private static final String EXTRA_EVENT = "webhtv.recovery.event";
    private static final String EXTRA_DETAILS = "webhtv.recovery.details";
    private static final String CAOC_STACK = "cat.ereza.customactivityoncrash.EXTRA_STACK_TRACE";
    private static final String STATE_DIRECTORY = "crash";
    private static final String STATE_NAME = "previous-process.state";
    private static final long GATE_TIMEOUT_MS = 3000;
    private static final int MAX_DETAILS_CHARS = 64 * 1024;

    private static final Object LOCK = new Object();
    private static final Map<Activity, List<Runnable>> WAITING = new WeakHashMap<>();
    private static final AtomicBoolean SETTLED = new AtomicBoolean();
    private static volatile WeakReference<Activity> host;
    private static volatile Context application;
    private static volatile CrashRecoveryState.Run run;
    private static volatile CrashRecoveryState.Recovery pending;
    private static volatile boolean decided;
    private static boolean queried;

    private PreviousProcessCrashRecovery() {
    }

    /** Earliest point of the default process; idempotent, performs no I/O. */
    public static synchronized void initialize(Context context) {
        if (application != null || context == null) return;
        try {
            Context app = context.getApplicationContext();
            if (app == null || !CrashReport.isMainProcess(app)) return;
            application = app;
            if (run == null) run = newRun(app);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Runs {@code proceed} once the recovery decision is known.
     *
     * <p>While the decision is open every caller waits, so no entry point can load configuration
     * ahead of it. A confirmed death is handed to the crash screen and {@code proceed} is dropped
     * for that launch; anything else releases the parked callers in order. The decision is taken
     * once per process run, so rotations and singleTop deliveries never recover twice.
     */
    public static void checkBeforeConfig(Activity activity, Runnable proceed) {
        Runnable next = proceed == null ? () -> { } : proceed;
        try {
            if (activity == null || activity.isFinishing()) {
                next.run();
                return;
            }
            boolean ready;
            synchronized (LOCK) {
                ready = decided;
                if (!ready) {
                    List<Runnable> queued = WAITING.get(activity);
                    if (queued == null) {
                        queued = new ArrayList<>();
                        WAITING.put(activity, queued);
                    }
                    queued.add(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) next.run();
                    });
                    if (host == null || host.get() == null || host.get().isFinishing() || host.get().isDestroyed()) host = new WeakReference<>(activity);
                }
            }
            if (!ready) {
                query(activity);
                return;
            }
            deliver(activity, next);
        } catch (Throwable error) {
            SpiderDebug.log("crash-recovery", "gate failed open error=%s", error.getClass().getSimpleName());
            next.run();
        }
    }

    /** Persists the Java fatal fact of this process and tags the intent with its event id. */
    public static void decorateJavaCrashIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        try {
            Context app = context.getApplicationContext();
            if (app == null || !CrashReport.isMainProcess(app)) return;
            if (run == null) run = newRun(app);
            String stack = intent.getStringExtra(CAOC_STACK);
            String details = stack == null ? "" : stack;
            if (details.length() > MAX_DETAILS_CHARS) details = details.substring(0, MAX_DETAILS_CHARS);
            long timestamp = System.currentTimeMillis();
            String event = "java:" + run.id();
            new CrashRecoveryState(stateFile(app)).recordJava(run, event, timestamp, details);
            intent.putExtra(EXTRA_EVENT, event);
            intent.putExtra(EXTRA_DETAILS, "# Java fatal recovery\nprocess=" + run.main() + "\npid=" + run.pid()
                    + "\ntimestamp=" + timestamp + "\nreason=JAVA_FATAL\n" + details);
        } catch (Throwable error) {
            SpiderDebug.log("crash-recovery", "decorate failed error=%s", error.getClass().getSimpleName());
        }
    }

    /** Consumes the event of this intent; safe to call repeatedly and from the error process. */
    public static void acknowledgeShown(Context context, Intent intent) {
        if (context == null || intent == null) return;
        try {
            String event = intent.getStringExtra(EXTRA_EVENT);
            if (event == null || event.isEmpty()) return;
            Context app = context.getApplicationContext();
            if (app == null) return;
            new CrashRecoveryState(stateFile(app)).acknowledge(event);
        } catch (Throwable ignored) {
        }
    }

    /** History details carried by this intent, or null when the intent is not a recovery entry. */
    public static String getRecoveryDetails(Intent intent) {
        if (intent == null) return null;
        try {
            String details = intent.getStringExtra(EXTRA_DETAILS);
            return details == null || details.isEmpty() ? null : details;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void query(Activity activity) {
        synchronized (LOCK) {
            if (queried || decided) return;
            queried = true;
        }
        Handler main = new Handler(Looper.getMainLooper());
        Context app = activity.getApplicationContext();
        main.postDelayed(PreviousProcessCrashRecovery::expire, GATE_TIMEOUT_MS);
        Task.execute(() -> {
            CrashRecoveryState.Recovery recovery = null;
            try {
                recovery = decide(app, exits(app, Build.VERSION.SDK_INT));
            } catch (Throwable error) {
                SpiderDebug.log("crash-recovery", "decision failed open error=%s", error.getClass().getSimpleName());
            }
            CrashRecoveryState.Recovery result = recovery;
            main.post(() -> settle(result));
        });
    }

    /**
     * Releases every parked caller at the deadline, regardless of what the worker is still doing.
     *
     * <p>The deadline must not depend on the worker's own commit step: a slow or blocked state
     * transaction would otherwise keep configuration loading parked forever. A decision that
     * arrives after the deadline is still shown, because its event was already claimed and can
     * therefore never loop on the next launch.
     */
    private static void expire() {
        List<Runnable> queued;
        synchronized (LOCK) {
            if (decided) return;
            decided = true;
            queued = drain();
        }
        SpiderDebug.log("crash-recovery", "gate deadline reached; releasing %d waiting step(s)", queued.size());
        runAll(queued);
    }

    private static void settle(CrashRecoveryState.Recovery recovery) {
        if (!SETTLED.compareAndSet(false, true)) return;
        List<Runnable> queued;
        Activity target;
        synchronized (LOCK) {
            decided = true;
            pending = null;
            target = host == null ? null : host.get();
            if (target == null || target.isFinishing() || target.isDestroyed()) {
                for (Activity candidate : WAITING.keySet()) {
                    if (candidate != null && !candidate.isFinishing() && !candidate.isDestroyed()) {
                        target = candidate;
                        break;
                    }
                }
            }
            host = null;
            queued = drain();
        }
        if (recovery == null || target == null || target.isFinishing() || target.isDestroyed()) {
            pending = null;
            runAll(queued);
            return;
        }
        show(target, recovery, queued);
    }

    private static List<Runnable> drain() {
        List<Runnable> queued = new ArrayList<>();
        for (List<Runnable> list : WAITING.values()) if (list != null) queued.addAll(list);
        WAITING.clear();
        return Collections.unmodifiableList(queued);
    }

    private static void runAll(List<Runnable> queued) {
        for (Runnable runnable : queued) {
            try {
                runnable.run();
            } catch (Throwable error) {
                SpiderDebug.log("crash-recovery", "released step failed error=%s", error.getClass().getSimpleName());
            }
        }
    }

    private static void deliver(Activity activity, Runnable next) {
        CrashRecoveryState.Recovery recovery = pending;
        pending = null;
        if (recovery == null) {
            next.run();
            return;
        }
        show(activity, recovery, List.of(next));
    }

    private static void show(Activity activity, CrashRecoveryState.Recovery recovery, List<Runnable> queued) {
        try {
            Intent intent = new Intent(activity, com.fongmi.android.tv.ui.activity.CrashActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_EVENT, recovery.eventId())
                    .putExtra(EXTRA_DETAILS, recovery.details());
            activity.startActivity(intent);
            SpiderDebug.log("crash-recovery", "handoff event=%s pid=%d", recovery.eventId(), recovery.pid());
            activity.finish();
            // The error process owns the log service; do not leave this startup running behind it.
            Process.killProcess(Process.myPid());
        } catch (Throwable error) {
            // The page could not start: the user still needs an app that loads its configuration.
            SpiderDebug.log("crash-recovery", "handoff failed error=%s", error.getClass().getSimpleName());
            runAll(queued);
        }
    }

    private static CrashRecoveryState.Recovery decide(Context context, List<CrashRecoveryState.Exit> history) {
        try {
            Context app = context.getApplicationContext();
            if (app == null) return null;
            if (run == null) run = newRun(app);
            int api = Build.VERSION.SDK_INT;
            return new CrashRecoveryState(stateFile(app)).begin(run, api, history);
        } catch (Throwable error) {
            SpiderDebug.log("crash-recovery", "decision failed open error=%s", error.getClass().getSimpleName());
            return null;
        }
    }

    private static List<CrashRecoveryState.Exit> exits(Context context, int api) {
        if (api < Build.VERSION_CODES.R) return List.of();
        try {
            return exitsApi30(context);
        } catch (Throwable error) {
            SpiderDebug.log("crash-recovery", "history unavailable error=%s", error.getClass().getSimpleName());
            return List.of();
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static List<CrashRecoveryState.Exit> exitsApi30(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return List.of();
        List<ApplicationExitInfo> reasons = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 0);
        if (reasons == null || reasons.isEmpty()) return List.of();
        List<CrashRecoveryState.Exit> list = new ArrayList<>(reasons.size());
        for (ApplicationExitInfo info : reasons) {
            if (info == null) continue;
            list.add(new CrashRecoveryState.Exit(info.getProcessName(), info.getPid(), info.getTimestamp(),
                    info.getReason(), info.getStatus(), safe(info.getDescription()), summary(info)));
        }
        return list;
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static String summary(ApplicationExitInfo info) {
        byte[] bytes = info.getProcessStateSummary();
        return bytes == null || bytes.length > 128 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private static CrashRecoveryState.Run newRun(Context context) {
        String name = context.getApplicationInfo().processName;
        if (name == null || name.isEmpty()) name = context.getPackageName();
        CrashRecoveryState.Run current = new CrashRecoveryState.Run(
                UUID.randomUUID().toString(), Process.myPid(), System.currentTimeMillis(), name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (manager != null) manager.setProcessStateSummary(current.id().getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
        }
        return current;
    }
    private static File stateFile(Context context) {
        return new File(new File(context.getFilesDir(), STATE_DIRECTORY), STATE_NAME);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
