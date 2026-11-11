package com.fongmi.android.tv.cache;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.StatFs;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.MediaSourceFactory;
import com.fongmi.android.tv.service.PlaybackService;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CacheScheduler {

    private static final String KEY_LAST_AUTO_MS = "cache_mgmt_last_auto_ms";
    private static final String KEY_LOW_SPACE_STREAK = "cache_mgmt_low_space_streak";
    private static final String KEY_LAST_LIMIT_MS = "cache_mgmt_last_limit_ms";
    /**
     * Bytes that stayed above the effective total limit because they sit in modules the registry
     * forbids cleaning. Persisted so the panel can say why the total did not reach its limit
     * instead of showing a number that quietly disagrees with the configured value.
     */
    private static final String KEY_TOTAL_BLOCKED_BYTES = "cache_mgmt_total_blocked_bytes";
    static final int JOB_ID = 0x43414301;
    static final long PERIODIC_INTERVAL_MS = TimeUnit.DAYS.toMillis(7);
    private static final long STARTUP_DELAY_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long LIMIT_INTERVAL_MS = TimeUnit.HOURS.toMillis(12);

    private static final CacheScheduler INSTANCE = new CacheScheduler();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean convergenceStarted = new AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cache-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private CacheScheduler() {
    }

    public static CacheScheduler get() {
        return INSTANCE;
    }

    public void start() {
        CachePolicyStore.migrate();
        // A configured total limit is the user's own statement about the whole cache directory, so
        // it converges even while the background auto-cleanup switch is off: gating it behind that
        // switch is what made an explicit limit look ignored until the switch was turned on too.
        if (convergenceStarted.compareAndSet(false, true)) {
            // Warm the snapshot: the capacity budget reads module sizes from it, and an absent
            // snapshot would hand the playback cache the whole total limit.
            CacheCenter.get().requestSnapshot(true, ignored -> {
            });
            executor.schedule(() -> submitConvergence("startup", null), STARTUP_DELAY_MS, TimeUnit.MILLISECONDS);
            executor.scheduleWithFixedDelay(this::limitCheck, LIMIT_INTERVAL_MS,
                    LIMIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
        if (!CachePolicyStore.isAutoCleanupEnabled()) return;
        schedulePersistent(App.get());
        if (!started.compareAndSet(false, true)) return;
        executor.schedule(this::startupCheck, STARTUP_DELAY_MS, TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(this::periodicCheck, PERIODIC_INTERVAL_MS,
                PERIODIC_INTERVAL_MS, TimeUnit.MILLISECONDS);

    }

    public void schedulePersistent(Context context) {
        if (context == null || !CachePolicyStore.isAutoCleanupEnabled()) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, CacheCleanupJobService.class))
                    .setPersisted(true)
                    .setPeriodic(PERIODIC_INTERVAL_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setRequiresBatteryNotLow(true)
                    .build();
            scheduler.schedule(job);
        } catch (Throwable ignored) {
        }
    }

    public void cancelPersistent(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) scheduler.cancel(JOB_ID);
        } catch (Throwable ignored) {
        }
    }

    public boolean runPersistedCheck(Runnable finished) {
        if (!CachePolicyStore.isAutoCleanupEnabled()) return false;
        boolean periodic = System.currentTimeMillis() - Prefers.getLong(KEY_LAST_AUTO_MS, 0L) >= PERIODIC_INTERVAL_MS;
        return trigger(periodic ? "periodic" : "low-space", finished);
    }

    /**
     * Converges the total limit once the last player released.
     *
     * <p>This is the playback half of the total limit: the playback caches are the largest
     * consumers and are only reclaimable while nothing plays, so the moment playback ends is the
     * moment the configured total can actually be reached.</p>
     */
    public void onPlaybackEnded() {
        submitConvergence("playback-end", null);
    }

    /**
     * Applies a freshly configured total limit without waiting for the next playback.
     *
     * <p>{@code finished} runs on the main thread after the pass, so the panel refreshes against
     * the disk state the pass left instead of the pre-cleanup numbers.</p>
     */
    public void convergeNow(Runnable finished) {
        submitConvergence("settings", finished);
    }

    /** Bytes the last convergence left above the effective limit, or 0 when it converged. */
    public static long blockedOverBytes() {
        return Math.max(0L, Prefers.getLong(KEY_TOTAL_BLOCKED_BYTES, 0L));
    }

    private void startupCheck() {
        // The scheduled tasks are queued once for the whole process, while cancelPersistent() only
        // cancels the platform job. Every tick must therefore re-read the switch, otherwise turning
        // auto cleanup off would still delete cache at the next automatic tick.
        if (!CachePolicyStore.isAutoCleanupEnabled()) return;
        CacheCenter.get().requestSnapshot(true, ignored -> {
        });
        trigger("startup", null);
    }

    private void periodicCheck() {
        if (!CachePolicyStore.isAutoCleanupEnabled()) return;
        long last = Prefers.getLong(KEY_LAST_AUTO_MS, 0L);
        if (System.currentTimeMillis() - last < PERIODIC_INTERVAL_MS) return;
        trigger("periodic", null);
    }

    private void limitCheck() {
        long now = System.currentTimeMillis();
        if (now - Prefers.getLong(KEY_LAST_LIMIT_MS, 0L) < LIMIT_INTERVAL_MS) return;
        Prefers.put(KEY_LAST_LIMIT_MS, now);
        if (CachePolicyStore.isAutoCleanupEnabled()) CacheCleanupManager.applyConfiguredLimits();
        // The playback cache converges on its own when the last player session is released; this is
        // the backstop for a device that keeps a player alive for days.
        MediaSourceFactory.enforceCapacityNow();
        submitConvergence("limit-interval", null);
    }

    private void submitConvergence(String reason, Runnable finished) {
        AtomicBoolean completed = new AtomicBoolean();
        Runnable complete = () -> {
            if (completed.compareAndSet(false, true) && finished != null) App.post(finished);
        };
        try {
            executor.execute(() -> {
                boolean pending = false;
                try {
                    // A new measurement invalidates the previous limit before any fallible work.
                    clearBlocked();
                    pending = converge(reason, complete);
                } catch (Throwable error) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("cache-scheduler", "total-limit failed reason=%s error=%s", reason, error.getClass().getSimpleName());
                } finally {
                    if (!pending) complete.run();
                }
            });
        } catch (Throwable error) {
            clearBlocked();
            complete.run();
        }
    }

    /**
     * One convergence pass over the whole cache directory.
     *
     * <p>Order of the guards matters: a running cleanup already owns the disk, a running player
     * owns the playback cache, and only then is the limit worth computing. The plan is the
     * registry-driven {@link CachePolicyEngine#convergencePlan(File)} set, so the playback caches
     * participate while plugin scripts and unclassified cache stay untouched; when those two alone
     * exceed the limit the pass records the blocked remainder and does nothing else, instead of
     * burning a full inventory every twelve hours for a number that can not move.</p>
     */
    private boolean converge(String reason, Runnable complete) {
        // A busy disk or a running player leaves the total unverified, so the last blocked figure is
        // stale rather than proof: it was measured against a limit that may since have changed.
        // Reporting it as permanently protected content would make the panel disagree with the
        // configured total, so the same rule as settleBlocked's retryable case applies here.
        if (CacheCleanupManager.isRunning()) {
            clearBlocked();
            return false;
        }
        if (PlaybackService.isRunning()) {
            clearBlocked();
            return false;
        }
        long configured = CachePolicyStore.getTotalLimitBytes();
        if (configured <= 0) {
            clearBlocked();
            return false;
        }
        long effective = CacheTotalLimitPolicy.effectiveLimit(configured,
                CacheCenter.get().systemQuotaBytes());
        CacheSnapshot snapshot = scanQuietly();
        if (snapshot == null) return false;
        if (!CacheTotalLimitPolicy.overLimit(snapshot.totalBytes(), effective)) {
            clearBlocked();
            return false;
        }
        CacheCleanupPlan plan = CachePolicyEngine.convergencePlan(App.get().getCacheDir());
        if (plan.modules().isEmpty()) return false;
        long protectedBytes = unclaimableBytes(snapshot, plan);
        if (protectedBytes > effective) {
            recordBlocked(snapshot.totalBytes() - effective);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("cache-scheduler", "total-limit blocked reason=%s effectiveLimit=%d totalBytes=%d protectedBytes=%d", reason, effective, snapshot.totalBytes(), protectedBytes);
            return false;
        }
        CacheCleanupManager.execute(plan, reason, result -> {
            try {
                executor.execute(() -> {
                    try {
                        verifyConvergence(reason, result);
                    } finally {
                        complete.run();
                    }
                });
            } catch (Throwable error) {
                complete.run();
            }
        });
        return true;
    }

    /**
     * Re-reads the disk after a pass and records what the limit still leaves above itself.
     *
     * <p>Runs off the main thread because it is a full inventory, and it never starts a second
     * pass: one convergence per trigger is the contract, and a limit that stays unreachable is
     * reported rather than retried in a loop.</p>
     */
    private void verifyConvergence(String reason, CacheCleanupResult result) {
        long configured = CachePolicyStore.getTotalLimitBytes();
        if (configured <= 0) {
            clearBlocked();
            return;
        }
        long effective = CacheTotalLimitPolicy.effectiveLimit(configured,
                CacheCenter.get().systemQuotaBytes());
        CacheSnapshot after = scanQuietly();
        if (after == null) return;
        CacheCleanupPlan plan = CachePolicyEngine.convergencePlan(App.get().getCacheDir());
        long over = Math.max(0L, after.totalBytes() - effective);
        long protectedBytes = unclaimableBytes(after, plan);
        boolean retryable = PlaybackService.isRunning()
                || result == null || result.status() != CacheCleanupStatus.COMPLETED;
        long blocked = settleBlocked(over, protectedBytes, effective, retryable);
        if (blocked > 0) recordBlocked(blocked);
        else clearBlocked();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("cache-scheduler", "total-limit settled reason=%s effectiveLimit=%d totalBytes=%d over=%d protectedBytes=%d status=%s blocked=%d", reason, effective, after.totalBytes(), over, protectedBytes, result == null ? "none" : result.status(), blocked);
        CacheCenter.get().notifyChanged();
    }

    /**
     * How many bytes the panel may report as permanently blocked.
     *
     * <p>A pass that only deferred (a player restarted), was cancelled, failed, or was not allowed
     * proves nothing about the limit, so the same condition is reported as retryable instead of
     * being remembered as protected content.</p>
     *
     * <p>Even after a completed pass the excess is permanent only when the content the pass may not
     * delete is itself above the effective limit. Comparing the excess against the protected bytes
     * is not that test: a protected set that still fits under the limit leaves reclaimable bytes
     * for the next trigger, so reporting that excess as unreachable would blame the user's own
     * limit on content that never exceeded it.</p>
     */
    static long settleBlocked(long overBytes, long protectedBytes, long effectiveBytes, boolean retryable) {
        if (overBytes <= 0 || retryable) return 0L;
        return protectedBytes > effectiveBytes ? overBytes : 0L;
    }
    /**
     * Bytes the convergence plan may not delete, so a limit below this floor is unreachable by
     * definition.
     */
    private long unclaimableBytes(CacheSnapshot snapshot, CacheCleanupPlan plan) {
        long protectedBytes = 0;
        for (CacheMeasurement module : snapshot.modules()) {
            if (plan.modules().contains(module.id())) continue;
            protectedBytes += Math.max(0, module.bytes());
        }
        return protectedBytes;
    }

    private static void recordBlocked(long overBytes) {
        Prefers.put(KEY_TOTAL_BLOCKED_BYTES, Math.max(0L, overBytes));
    }

    private static void clearBlocked() {
        if (blockedOverBytes() != 0L) Prefers.put(KEY_TOTAL_BLOCKED_BYTES, 0L);
    }

    private CacheSnapshot scanQuietly() {
        try {
            return new CacheInventory(App.get()).scan();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The module list an automatic run may touch.
     *
     * <p>The tiered sets describe what a user may clean when pressing L1/L2/L3. An automatic run is
     * narrower by design (§13.2: "只对 allowAutomaticCleanup 模块生成 plan"): it keeps only the
     * modules the registry declares automatically cleanable. That drops the report-only diagnostic
     * log module and also {@link CacheModuleId#GLIDE}/{@link CacheModuleId#WEBHOME_RAW}, which the
     * STANDARD tier includes for manual cleanup but whose registry entries declare
     * {@code allowAutomaticCleanup=false}.</p>
     */
    static CacheCleanupPlan automaticPlan(CacheCleanupMode mode) {
        CacheCleanupMode resolved = mode == null ? CacheCleanupMode.LIGHT : mode;
        List<CacheModuleId> modules = CachePolicyEngine.plan(resolved).modules().stream()
                .filter(CachePolicyEngine::allowsAutomatic)
                .toList();
        return new CacheCleanupPlan(resolved, modules);
    }

    private boolean trigger(String reason, Runnable finished) {
        boolean periodic = "periodic".equals(reason);
        boolean lowSpace = sampleLowSpace();
        boolean playing = PlaybackService.isRunning();
        CacheCleanupMode mode = CacheAutoCleanupPolicy.cleanupMode(playing, lowSpace);
        if (!CacheAutoCleanupPolicy.shouldTrigger(Prefers.getInt(KEY_LOW_SPACE_STREAK, 0), periodic)
                || CacheCleanupManager.isRunning()) {
            if (finished != null) finished.run();
            return false;
        }
        CacheCleanupManager.execute(automaticPlan(mode), reason, result -> {
            if (result.status() == CacheCleanupStatus.COMPLETED
                    || result.status() == CacheCleanupStatus.PARTIAL) {
                Prefers.put(KEY_LAST_AUTO_MS, System.currentTimeMillis());
                Prefers.put(KEY_LOW_SPACE_STREAK, 0);
            }
            if (finished != null) finished.run();
        });
        return true;
    }

    private boolean sampleLowSpace() {
        File cache = App.get().getCacheDir();
        try {
            StatFs stat = new StatFs(existingPath(cache).getAbsolutePath());
            long available = stat.getAvailableBytes();
            long total = stat.getTotalBytes();
            boolean low = CacheAutoCleanupPolicy.isLowSpace(available, total);
            int streak = Prefers.getInt(KEY_LOW_SPACE_STREAK, 0);
            streak = low ? Math.min(2, streak + 1) : 0;
            Prefers.put(KEY_LOW_SPACE_STREAK, streak);
            return low && streak >= 2;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static File existingPath(File file) {
        File current = file;
        while (current != null && !current.exists()) current = current.getParentFile();
        return current == null ? file : current;
    }
}
