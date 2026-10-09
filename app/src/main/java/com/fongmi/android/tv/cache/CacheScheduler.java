package com.fongmi.android.tv.cache;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.StatFs;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.service.PlaybackService;
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
    static final int JOB_ID = 0x43414301;
    static final long PERIODIC_INTERVAL_MS = TimeUnit.DAYS.toMillis(7);
    private static final long STARTUP_DELAY_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long LIMIT_INTERVAL_MS = TimeUnit.HOURS.toMillis(12);

    private static final CacheScheduler INSTANCE = new CacheScheduler();
    private final AtomicBoolean started = new AtomicBoolean();
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
        if (!CachePolicyStore.isAutoCleanupEnabled()) return;
        schedulePersistent(App.get());
        if (!started.compareAndSet(false, true)) return;
        executor.schedule(this::startupCheck, STARTUP_DELAY_MS, TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(this::periodicCheck, PERIODIC_INTERVAL_MS,
                PERIODIC_INTERVAL_MS, TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(this::limitCheck, LIMIT_INTERVAL_MS,
                LIMIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
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

    private void startupCheck() {
        // The scheduled tasks are queued once for the whole process, while cancelPersistent() only
        // cancels the platform job. Every tick must therefore re-read the switch, otherwise turning
        // auto cleanup off would still delete cache at the next periodic/limit tick.
        if (!CachePolicyStore.isAutoCleanupEnabled()) return;
        trigger("startup", null);
        enforceTotalLimit();
    }

    private void periodicCheck() {
        if (!CachePolicyStore.isAutoCleanupEnabled()) return;
        long last = Prefers.getLong(KEY_LAST_AUTO_MS, 0L);
        if (System.currentTimeMillis() - last < PERIODIC_INTERVAL_MS) return;
        trigger("periodic", null);
    }

    private void limitCheck() {
        if (!CachePolicyStore.isAutoCleanupEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - Prefers.getLong(KEY_LAST_LIMIT_MS, 0L) < LIMIT_INTERVAL_MS) return;
        Prefers.put(KEY_LAST_LIMIT_MS, now);
        CacheCleanupManager.applyConfiguredLimits();
        enforceTotalLimit();
    }

    private void enforceTotalLimit() {
        long configured = CachePolicyStore.getTotalLimitBytes();
        if (configured <= 0) return;
        long effective = CacheTotalLimitPolicy.effectiveLimit(configured,
                CacheCenter.get().systemQuotaBytes());
        if (!CacheTotalLimitPolicy.overLimit(totalCacheBytes(), effective)) return;
        CacheCleanupManager.execute(automaticPlan(CacheCleanupMode.LIGHT), "total-limit", first -> {
            if (PlaybackService.isRunning()) return;
            executor.execute(() -> {
                if (!CacheTotalLimitPolicy.overLimit(totalCacheBytes(), effective)) return;
                CacheCleanupManager.execute(automaticPlan(CacheCleanupMode.STANDARD),
                        "total-limit", ignored -> {
                        });
            });
        });
    }

    private long totalCacheBytes() {
        try {
            return new CacheInventory(App.get()).scan().totalBytes();
        } catch (Throwable ignored) {
            return 0;
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
