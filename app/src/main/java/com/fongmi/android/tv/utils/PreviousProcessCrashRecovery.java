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
import java.util.List;

/**
 * Confirmed-death recovery for the main process.
 *
 * <p>The state file is the only cross-process authority: the dying process persists a Java fact, the
 * crash screen confirms it, and the next launch hands a claim over once before any configuration is
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

    private static final Gate GATE = new Gate();
    private static final Gate.Liveness ACTIVITY_ALIVE = owner -> owner instanceof Activity
            && !((Activity) owner).isFinishing() && !((Activity) owner).isDestroyed();
    private static volatile WeakReference<Activity> host;
    private static volatile Context application;
    private static volatile CrashRecoveryState.Run run;

    private PreviousProcessCrashRecovery() {
    }

    /** Earliest point of the default process; idempotent, performs no I/O. */
    public static synchronized void initialize(Context context) {
        if (application != null || context == null) return;
        try {
            // attachBaseContext runs before the Application context exists; keep the given base then.
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            if (!CrashReport.isMainProcess(app)) return;
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
            if (!alive(activity)) {
                next.run();
                return;
            }
            host = new WeakReference<>(activity);
            if (GATE.parkOrDecided(activity, next)) {
                query(activity);
                return;
            }
            deliver(activity, next);
        } catch (Throwable error) {
            SpiderDebug.log("crash-recovery", "gate failed open error=%s", error.getClass().getSimpleName());
            next.run();
        }
    }

    /**
     * Drops every step parked for a host that is going away.
     *
     * <p>The parked step is a caller lambda that still references its activity, so an entry that
     * outlived the activity would both keep it reachable and run configuration loading on a dead
     * screen. Releasing here is what removes the last strong reference.
     */
    public static void release(Activity activity) {
        try {
            if (activity == null) return;
            GATE.release(activity);
            WeakReference<Activity> current = host;
            if (current != null && current.get() == activity) host = null;
        } catch (Throwable ignored) {
        }
    }

    static synchronized String javaEvent(Context context) {
        if(run==null) run=newRun(context);
        return "java:"+run.id();
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
            String event = javaEvent(app);
            new CrashRecoveryState(stateFile(app)).recordJava(run, event, timestamp, details);
            intent.putExtra(EXTRA_EVENT, event);
            intent.putExtra(EXTRA_DETAILS, "# Java fatal recovery\nevent=" + event + "\nrun=" + run.id() + "\nprocess=" + run.main() + "\npid=" + run.pid()
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
        if (!GATE.queryOnce()) return;
        GATE.submit(() -> {
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
        }, ACTIVITY_ALIVE);
    }

    /**
     * Releases every parked caller at the deadline, regardless of what the worker is still doing.
     *
     * <p>The deadline must not depend on the worker's own commit step: a slow or blocked state
     * transaction would otherwise keep configuration loading parked forever. A decision that
     * arrives after the deadline is still shown, because its event was claimed on disk before the
     * decision and can therefore never loop on the next launch.
     */
    private static void expire() {
        List<Gate.Step> released = GATE.finish();
        if (released == null) return;
        SpiderDebug.log("crash-recovery", "gate deadline reached; releasing %d waiting step(s)", released.size());
        Gate.runAll(released, ACTIVITY_ALIVE);
    }

    private static void settle(CrashRecoveryState.Recovery recovery) {
        List<Gate.Step> released = GATE.finish();
        if (released == null) {
            // The deadline already released every caller: hold the late decision for the next entry
            // point instead of dropping it or re-releasing the released steps.
            if (recovery != null && GATE.hold(recovery)) deliverHeld();
            return;
        }
        if (recovery == null) {
            Gate.runAll(released, ACTIVITY_ALIVE);
            return;
        }
        Activity target = firstAlive(released);
        if (target == null) {
            // No live host: the claim on disk survives this process, and the next entry point of this
            // process picks it up from the kernel instead of losing the only remaining chance.
            GATE.hold(recovery);
            Gate.runAll(released, ACTIVITY_ALIVE);
            return;
        }
        show(target, recovery, released);
    }

    private static void deliver(Activity activity, Runnable next) {
        CrashRecoveryState.Recovery recovery = (CrashRecoveryState.Recovery) GATE.claim();
        if (recovery == null) {
            next.run();
            return;
        }
        show(activity, recovery, List.of(new Gate.Step(activity, next)));
    }

    private static boolean deliverHeld() {
        Activity target = liveHost();
        if (target == null) return false;
        CrashRecoveryState.Recovery recovery = (CrashRecoveryState.Recovery) GATE.claim();
        if (recovery == null) return false;
        show(target, recovery, List.of());
        return true;
    }

    private static void show(Activity activity, CrashRecoveryState.Recovery recovery, List<Gate.Step> steps) {
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
            // The page could not start: the claim stays on disk and is handed over again on the next
            // launch, while this startup continues so the user still gets a working application.
            SpiderDebug.log("crash-recovery", "handoff failed error=%s", error.getClass().getSimpleName());
            Gate.runAll(steps, ACTIVITY_ALIVE);
        }
    }

    private static Activity firstAlive(List<Gate.Step> steps) {
        for (Gate.Step step : steps) {
            Object owner = step.owner();
            if (owner instanceof Activity && alive((Activity) owner)) return (Activity) owner;
        }
        return null;
    }

    private static Activity liveHost() {
        WeakReference<Activity> current = host;
        Activity activity = current == null ? null : current.get();
        return alive(activity) ? activity : null;
    }

    private static boolean alive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
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

    /**
     * Delivery kernel: parks callers until one decision is published, releases them at a bounded
     * deadline, and re-checks each owner right before its step runs.
     *
     * <p>It holds no Android type, so the JVM tests drive exactly the code the app runs: the
     * deadline, the once-per-run decision and the liveness rules are all decided here.
     */
    static final class Gate {

        interface Liveness { boolean alive(Object owner); }

        static final class Step {
            private final Object owner;
            private final Runnable action;

            Step(Object owner, Runnable action) {
                this.owner = owner;
                this.action = action;
            }

            Object owner() {
                return owner;
            }

            Runnable action() {
                return action;
            }
        }

        private final List<Step> waiting = new ArrayList<>();
        private boolean decided;
        private boolean queried;
        private Object held;

        synchronized boolean decided() {
            return decided;
        }

        /** Parks a step, or answers false when the decision is already known. */
        synchronized boolean parkOrDecided(Object owner, Runnable action) {
            if (decided) return false;
            if (action != null) waiting.add(new Step(owner, action));
            return true;
        }

        void submit(Runnable submission,Liveness liveness) {
            try { submission.run(); }
            catch(Throwable rejected) { runAll(finish(),liveness); }
        }
        /** Starts the single platform query of this process run. */
        synchronized boolean queryOnce() {
            if (queried || decided) return false;
            queried = true;
            return true;
        }

        /** Forgets a destroyed owner: its steps can never run and must not keep it reachable. */
        synchronized void release(Object owner) {
            if (owner == null) return;
            waiting.removeIf(step -> step.owner == owner);
        }

        /** Publishes the decision and hands every parked step back exactly once. */
        synchronized List<Step> finish() {
            if (decided) return null;
            decided = true;
            List<Step> steps = new ArrayList<>(waiting);
            waiting.clear();
            return steps;
        }

        /** Holds a claimed decision for a later entry point; false when one is already held. */
        synchronized boolean hold(Object recovery) {
            if (recovery == null || held != null) return false;
            held = recovery;
            return true;
        }

        synchronized Object claim() {
            Object recovery = held;
            held = null;
            return recovery;
        }

        static void runAll(List<Step> steps, Liveness liveness) {
            if (steps == null) return;
            for (Step step : steps) {
                // A previous step may have finished its host: it can never load configuration again.
                if (!liveness.alive(step.owner)) continue;
                try {
                    step.action.run();
                } catch (Throwable error) {
                    SpiderDebug.log("crash-recovery", "released step failed error=%s", error.getClass().getSimpleName());
                }
            }
        }
    }
}
