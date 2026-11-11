package com.fongmi.android.tv.cache;

import static org.junit.Assert.*;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.MediaSourceFactory;
import com.fongmi.android.tv.service.PlaybackService;
import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class, shadows = {
        CacheSchedulerConvergenceTest.AppShadow.class,
        CacheSchedulerConvergenceTest.CenterShadow.class,
        CacheSchedulerConvergenceTest.InventoryShadow.class,
        CacheSchedulerConvergenceTest.CleanupShadow.class,
        CacheSchedulerConvergenceTest.PlayerShadow.class,
        CacheSchedulerConvergenceTest.MediaShadow.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class CacheSchedulerConvergenceTest {
    private CacheScheduler scheduler;
    private QueueExecutor executor;

    @Before public void setup() throws Exception {
        Init.set(RuntimeEnvironment.getApplication());
        Prefers.getPrefers().edit().clear().commit();
        Constructor<CacheScheduler> constructor = CacheScheduler.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        scheduler = constructor.newInstance();
        executor = new QueueExecutor();
        Field field = CacheScheduler.class.getDeclaredField("executor");
        field.setAccessible(true);
        field.set(scheduler, executor);
        CleanupShadow.callback = null;
        CleanupShadow.maintenance = 0;
        CleanupShadow.running = false;
        CleanupShadow.throwOnExecute = false;
        PlayerShadow.playing = false;
        CenterShadow.notified = false;
        CenterShadow.throwOnQuota = false;
        InventoryShadow.throwOnScan = false;
        InventoryShadow.snapshot = snapshot(200, 0);
        CachePolicyStore.putTotalLimitBytes(100);
    }

    @Test public void autoOffRegistersOneTotalBudgetTickWithoutPeriodicJob() {
        scheduler.start();
        scheduler.start();
        assertEquals(List.of(TimeUnit.HOURS.toMillis(12)), executor.intervals);
        assertEquals(1, executor.delayed.size());
        assertTrue(((android.app.job.JobScheduler) App.get().getSystemService(
                android.content.Context.JOB_SCHEDULER_SERVICE)).getAllPendingJobs().isEmpty());
    }

    @Test public void autoOffTickConvergesWithoutModuleMaintenance() throws Exception {
        invoke("limitCheck");
        executor.drain();
        assertNotNull("total budget must execute even with auto disabled", CleanupShadow.callback);
        assertEquals(0, CleanupShadow.maintenance);
    }

    @Test public void finishedWaitsForCleanupThenBackgroundVerificationExactlyOnce() {
        int[] calls = {0};
        scheduler.convergeNow(() -> {
            assertTrue(CenterShadow.notified);
            assertSame(android.os.Looper.getMainLooper(), android.os.Looper.myLooper());
            calls[0]++;
        });
        executor.drain();
        ShadowLooper.idleMainLooper();
        assertEquals("must not finish when asynchronous cleanup only starts", 0, calls[0]);
        assertNotNull(CleanupShadow.callback);
        InventoryShadow.snapshot = snapshot(80, 0);
        CleanupShadow.callback.accept(result(CacheCleanupStatus.COMPLETED));
        ShadowLooper.idleMainLooper();
        assertEquals("verification must run on scheduler first", 0, calls[0]);
        executor.drain();
        ShadowLooper.idleMainLooper();
        assertEquals(1, calls[0]);
    }

    @Test public void finishedRunsOnceForSkippedAndRejectedSubmissions() {
        int[] calls = {0};
        PlayerShadow.playing = true;
        scheduler.convergeNow(() -> calls[0]++);
        executor.drain();
        ShadowLooper.idleMainLooper();
        assertEquals(1, calls[0]);
        executor.reject = true;
        scheduler.convergeNow(() -> calls[0]++);
        ShadowLooper.idleMainLooper();
        assertEquals(2, calls[0]);
    }

    @Test public void raisingLimitDuringPlaybackClearsOutdatedBlockedState() {
        settle(150, 150, result(CacheCleanupStatus.COMPLETED));
        assertEquals(50L, CacheScheduler.blockedOverBytes());

        CachePolicyStore.putTotalLimitBytes(256);
        PlayerShadow.playing = true;
        scheduler.convergeNow(null);
        executor.drain();

        assertEquals(0L, CacheScheduler.blockedOverBytes());
    }

    @Test public void raisingLimitWithInitialScanFailureClearsBlockedOnce() {
        prepareRaisedLimit();
        InventoryShadow.throwOnScan = true;
        int[] calls = {0};
        scheduler.convergeNow(() -> calls[0]++);
        executor.drain();
        assertFinishedWithoutBlocked(calls);
        assertNull(CleanupShadow.callback);
    }

    @Test public void raisingLimitWithConvergenceExceptionClearsBlockedOnce() {
        prepareRaisedLimit();
        CleanupShadow.throwOnExecute = true;
        int[] calls = {0};
        scheduler.convergeNow(() -> calls[0]++);
        executor.drain();
        assertFinishedWithoutBlocked(calls);
    }

    @Test public void raisingLimitWithInitialSubmissionRejectedClearsBlockedOnce() {
        prepareRaisedLimit();
        executor.reject = true;
        int[] calls = {0};
        scheduler.convergeNow(() -> calls[0]++);
        assertFinishedWithoutBlocked(calls);
    }

    @Test public void raisingLimitWithVerificationScanFailureClearsBlockedOnce() {
        int[] calls = startRaisedLimitCleanup();
        InventoryShadow.throwOnScan = true;
        CleanupShadow.callback.accept(result(CacheCleanupStatus.COMPLETED));
        ShadowLooper.idleMainLooper();
        assertEquals("must wait for queued verification", 0, calls[0]);
        executor.drain();
        assertFinishedWithoutBlocked(calls);
    }

    @Test public void raisingLimitWithVerificationSubmissionRejectedClearsBlockedOnce() {
        int[] calls = startRaisedLimitCleanup();
        executor.reject = true;
        CleanupShadow.callback.accept(result(CacheCleanupStatus.COMPLETED));
        CleanupShadow.callback.accept(result(CacheCleanupStatus.COMPLETED));
        assertFinishedWithoutBlocked(calls);
    }

    @Test public void raisingLimitWithVerificationExceptionClearsBlockedOnce() {
        int[] calls = startRaisedLimitCleanup();
        CenterShadow.throwOnQuota = true;
        CleanupShadow.callback.accept(result(CacheCleanupStatus.COMPLETED));
        ShadowLooper.idleMainLooper();
        assertEquals("must wait for queued verification", 0, calls[0]);
        assertThrows(IllegalStateException.class, executor::drain);
        assertFinishedWithoutBlocked(calls);
    }

    private void prepareRaisedLimit() {
        settle(150, 150, result(CacheCleanupStatus.COMPLETED));
        assertEquals(50L, CacheScheduler.blockedOverBytes());
        CachePolicyStore.putTotalLimitBytes(256);
        InventoryShadow.snapshot = snapshot(300, 0);
        CleanupShadow.callback = null;
    }

    private int[] startRaisedLimitCleanup() {
        prepareRaisedLimit();
        int[] calls = {0};
        scheduler.convergeNow(() -> calls[0]++);
        executor.drain();
        ShadowLooper.idleMainLooper();
        assertEquals("must not finish when cleanup only starts", 0, calls[0]);
        assertNotNull(CleanupShadow.callback);
        return calls;
    }

    private void assertFinishedWithoutBlocked(int[] calls) {
        ShadowLooper.idleMainLooper();
        assertEquals("finished must run exactly once", 1, calls[0]);
        assertEquals("failed measurement cannot prove the old limit is blocked", 0L,
                CacheScheduler.blockedOverBytes());
    }
    @Test public void deferredCancelledOrFailedCleanupIsNotPermanentProtection() {
        for (CacheCleanupStatus status : List.of(CacheCleanupStatus.DEFERRED,
                CacheCleanupStatus.CANCELLED, CacheCleanupStatus.FAILED,
                CacheCleanupStatus.PARTIAL, CacheCleanupStatus.NOT_ALLOWED)) {
            settle(200, 200, result(status));
            assertEquals(status + " must stay retryable", 0L, CacheScheduler.blockedOverBytes());
        }
    }

    @Test public void playbackResumedDuringCleanupIsNotPermanentProtection() {
        drainConverge(result(CacheCleanupStatus.COMPLETED), () -> PlayerShadow.playing = true);
        assertEquals(0L, CacheScheduler.blockedOverBytes());
    }

    @Test public void excessExplainedByProtectedModulesIsRecorded() {
        settle(150, 150, result(CacheCleanupStatus.COMPLETED));
        assertEquals(50L, CacheScheduler.blockedOverBytes());
    }

    @Test public void reclaimableExcessIsNotRecordedAsProtected() {
        settle(200, 80, result(CacheCleanupStatus.COMPLETED));
        assertEquals("claimable bytes remained, so the excess is retryable", 0L,
                CacheScheduler.blockedOverBytes());
    }

    @Test public void sixtyProtectedBytesFitInsideLimitDespiteFiftyBytesExcess() {
        settle(150, 60, result(CacheCleanupStatus.COMPLETED));
        assertEquals("total 150, limit 100, protected 60 is reachable", 0L,
                CacheScheduler.blockedOverBytes());
    }

    @Test public void eightyProtectedBytesFitInsideLimitDespiteFiftyBytesExcess() {
        settle(150, 80, result(CacheCleanupStatus.COMPLETED));
        assertEquals("total 150, limit 100, protected 80 is reachable", 0L,
                CacheScheduler.blockedOverBytes());
    }

    @Test public void protectedBytesEqualToLimitStillAllowConvergence() {
        settle(150, 100, result(CacheCleanupStatus.COMPLETED));
        assertEquals("total 150, limit 100, protected 100 is reachable", 0L,
                CacheScheduler.blockedOverBytes());
    }

    private void settle(long afterTotal, long protectedBytes, CacheCleanupResult result) {
        drainConverge(afterTotal, protectedBytes, result, () -> { });
    }

    private void drainConverge(CacheCleanupResult result, Runnable beforeCallback) {
        drainConverge(150, 150, result, beforeCallback);
    }

    private void drainConverge(long afterTotal, long protectedBytes, CacheCleanupResult result,
                               Runnable beforeCallback) {
        scheduler.convergeNow(null);
        executor.drain();
        assertNotNull(CleanupShadow.callback);
        InventoryShadow.snapshot = snapshot(afterTotal, protectedBytes);
        beforeCallback.run();
        CleanupShadow.callback.accept(result);
        executor.drain();
    }

    private static CacheCleanupResult result(CacheCleanupStatus status) {
        return new CacheCleanupResult(CacheModuleId.EXO, status, 200, 80, 1, 0, List.of());
    }

    private void invoke(String method) throws Exception {
        Method target = CacheScheduler.class.getDeclaredMethod(method);
        target.setAccessible(true);
        target.invoke(scheduler);
    }

    private static CacheSnapshot snapshot(long total, long protectedBytes) {
        return new CacheSnapshot(0, total, 1000, 1000, 1000, List.of(
                new CacheMeasurement(CacheModuleId.EXO, total - protectedBytes, 1, 0, 0,
                        CacheAvailability.AVAILABLE, List.of()),
                new CacheMeasurement(CacheModuleId.PLUGIN_SCRIPTS, protectedBytes, 1, 0, 0,
                        CacheAvailability.AVAILABLE, List.of())), List.of());
    }

    static class QueueExecutor extends ScheduledThreadPoolExecutor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final List<Runnable> delayed = new ArrayList<>();
        final List<Long> intervals = new ArrayList<>();
        boolean reject;
        QueueExecutor() { super(1); }
        @Override public void execute(Runnable task) {
            if (reject) throw new java.util.concurrent.RejectedExecutionException();
            queue.add(task);
        }
        @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            delayed.add(task);
            return null;
        }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initial,
                                                                   long delay, TimeUnit unit) {
            intervals.add(unit.toMillis(delay));
            return null;
        }
        void drain() { while (!queue.isEmpty()) queue.remove().run(); }
    }

    @Implements(App.class) public static class AppShadow extends org.robolectric.shadows.ShadowApplication {
        @Implementation protected void onCreate() { }
    }
    @Implements(CacheCenter.class) public static class CenterShadow {
        static boolean notified, throwOnQuota;
        @Implementation protected void requestSnapshot(boolean force, Consumer<CacheSnapshot> callback) { }
        @Implementation protected long systemQuotaBytes() {
            if (throwOnQuota) throw new IllegalStateException("quota fixture");
            return 1000;
        }
        @Implementation protected void notifyChanged() { notified = true; }
    }
    @Implements(CacheInventory.class) public static class InventoryShadow {
        static CacheSnapshot snapshot;
        static boolean throwOnScan;
        @Implementation protected CacheSnapshot scan() {
            if (throwOnScan) throw new IllegalStateException("inventory fixture");
            return snapshot;
        }
    }
    @Implements(CacheCleanupManager.class) public static class CleanupShadow {
        static Consumer<CacheCleanupResult> callback;
        static int maintenance;
        static boolean running, throwOnExecute;
        @Implementation protected static boolean isRunning() { return running; }
        @Implementation protected static void applyConfiguredLimits() { maintenance++; }
        @Implementation protected static void execute(CacheCleanupPlan plan, String reason,
                                                       Consumer<CacheCleanupResult> finished) {
            if (throwOnExecute) throw new IllegalStateException("fixture");
            callback = finished;
        }
    }
    @Implements(PlaybackService.class) public static class PlayerShadow {
        static boolean playing;
        @Implementation protected static boolean isRunning() { return playing; }
    }
    @Implements(MediaSourceFactory.class) public static class MediaShadow {
        @Implementation protected static void enforceCapacityNow() { }
    }
}
