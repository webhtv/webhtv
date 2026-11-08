package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class FollowingScheduler {

    public static final String PERIODIC_WORK = "webhtv.following.periodic";
    public static final String ONE_SHOT_WORK = "webhtv.following.one-shot";
    public static final String ONE_SHOT_PREFIX = ONE_SHOT_WORK + ".";
    public static final String TAG = "webhtv.following";

    private FollowingScheduler() {
    }

    public static void ensurePeriodic(Context context) {
        FollowingWorkManager.withManager(context, manager -> {
            if (!FollowingSettings.isEnabled()) {
                cancelAll(manager);
                return;
            }
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    FollowingUpdateWorker.class, 6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                    .setConstraints(networkConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .build();
            manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
        });
    }

    public static void enqueueDueNow(Context context) {
        FollowingWorkManager.withManager(context, manager -> {
            if (!FollowingSettings.isEnabled()) return;
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FollowingUpdateWorker.class)
                    .setConstraints(networkConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .build();
            manager.enqueueUniqueWork(ONE_SHOT_WORK, ExistingWorkPolicy.KEEP, request);
        });
    }

    public static void scheduleNext(Context context, Following item) {
        if (item == null) return;
        // Snapshot the request instead of retaining a mutable Following or an Activity until unlock.
        String identityKey = item.identityKey;
        boolean enabled = item.enabled;
        long nextCheckAt = item.nextCheckAt;
        FollowingWorkManager.withManager(context, manager -> {
            if (!enabled || !FollowingSettings.isEnabled()) {
                if (identityKey != null && !identityKey.isBlank()) manager.cancelUniqueWork(oneShotName(identityKey));
                return;
            }
            long delay = Math.max(FollowingSchedulePolicy.MIN_ONE_SHOT_DELAY, nextCheckAt - System.currentTimeMillis());
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FollowingUpdateWorker.class)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(networkConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .build();
            manager.enqueueUniqueWork(oneShotName(identityKey), ExistingWorkPolicy.REPLACE, request);
        });
    }

    public static void cancelNext(Context context, String identityKey) {
        if (identityKey == null || identityKey.isBlank()) return;
        FollowingWorkManager.withManager(context, manager -> manager.cancelUniqueWork(oneShotName(identityKey)));
    }

    public static void cancelAll(Context context) {
        FollowingWorkManager.withManager(context, FollowingScheduler::cancelAll);
    }

    private static void cancelAll(WorkManager manager) {
        manager.cancelUniqueWork(PERIODIC_WORK);
        manager.cancelUniqueWork(ONE_SHOT_WORK);
        manager.cancelAllWorkByTag(TAG);
    }

    private static Constraints networkConstraints() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    private static String oneShotName(String identityKey) {
        return String.format(Locale.US, "%s%08x", ONE_SHOT_PREFIX, identityKey.hashCode());
    }
}
