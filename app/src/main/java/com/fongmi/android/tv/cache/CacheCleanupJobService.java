package com.fongmi.android.tv.cache;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class CacheCleanupJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!CachePolicyStore.isAutoCleanupEnabled()) {
            jobFinished(params, false);
            return false;
        }
        boolean started = CacheScheduler.get().runPersistedCheck(() -> jobFinished(params, false));
        if (!started) jobFinished(params, false);
        return started;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        CacheCleanupManager.cancel();
        return true;
    }
}
