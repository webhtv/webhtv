package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import java.util.Collections;
import java.util.List;

/** Replaces only WorkManager's eager initializer; all other Startup entries remain intact. */
public final class FollowingWorkInitializer implements Initializer<FollowingWorkInitializer> {
    @NonNull
    @Override
    public FollowingWorkInitializer create(@NonNull Context context) {
        FollowingWorkManager.initialize(context);
        return this;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
