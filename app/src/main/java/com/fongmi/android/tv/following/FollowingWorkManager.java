package com.fongmi.android.tv.following;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;

import androidx.core.content.ContextCompat;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Keeps WorkManager's own storage credential-protected without relocating app data. */
public final class FollowingWorkManager {
    private static final FollowingWorkManager INSTANCE = new FollowingWorkManager();

    private static final long RETRY_AFTER_UNLOCK_MS = 1_000L;
    // Hidden framework flag used by createPackageContext; available since API 24.
    private static final int CONTEXT_CREDENTIAL_PROTECTED_STORAGE = 0x00000010;

    private final ArrayDeque<Consumer<WorkManager>> pending = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context receiverContext;
    private BroadcastReceiver unlockReceiver;
    private boolean retryScheduled;

    FollowingWorkManager() {
    }

    /**
     * Initializes WorkManager lazily with the context supplied by the framework.
     * The adapter changes only the storage context used by WorkManager; app data remains untouched.
     */
    public static void initialize(Context context) {
        INSTANCE.start(context);
    }

    static void withManager(Context context, Consumer<WorkManager> operation) {
        INSTANCE.submit(context, operation);
    }

    synchronized void submit(Context context, Consumer<WorkManager> operation) {
        pending.addLast(operation);
        start(context);
    }

    synchronized void start(Context context) {
        Context app = context.getApplicationContext();
        // ACTION_USER_UNLOCKED is never replayed, so the state is re-checked after registering;
        // an unlock that lands inside the registration window would otherwise be lost for this process.
        if (!isUserUnlocked(app)) {
            registerUnlockReceiver(app);
            if (!isUserUnlocked(app)) return;
        }
        unregisterUnlockReceiver();
        try {
            Context workContext = storageContext(app);
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(workContext, new Configuration.Builder().build());
            }
            WorkManager manager = WorkManager.getInstance(workContext);
            retryScheduled = false;
            // Preserve enqueue/cancel ordering, including when several threads wait for unlock.
            while (!pending.isEmpty()) pending.removeFirst().accept(manager);
        } catch (IllegalStateException error) {
            if (!isDirectBootFailure(error)) throw error;
            registerUnlockReceiver(app);
            scheduleRetry(app);
        }
    }

    private static boolean isUserUnlocked(Context context) {
        UserManager manager = context.getSystemService(UserManager.class);
        return manager != null && manager.isUserUnlocked();
    }

    private static boolean isDirectBootFailure(IllegalStateException error) {
        return error.getMessage() != null && error.getMessage().contains("direct boot mode");
    }

    private void registerUnlockReceiver(Context app) {
        if (unlockReceiver != null) return;
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) start(context);
            }
        };
        receiverContext = app;
        ContextCompat.registerReceiver(app, unlockReceiver,
                new IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_EXPORTED);
    }

    private void unregisterUnlockReceiver() {
        if (unlockReceiver == null) return;
        try {
            receiverContext.unregisterReceiver(unlockReceiver);
        } catch (IllegalArgumentException ignored) {
            // The process may have been torn down after the user-unlocked broadcast.
        }
        unlockReceiver = null;
        receiverContext = null;
    }

    private void scheduleRetry(Context app) {
        if (retryScheduled) return;
        retryScheduled = true;
        handler.postDelayed(() -> {
            synchronized (FollowingWorkManager.this) {
                retryScheduled = false;
                start(app);
            }
        }, RETRY_AFTER_UNLOCK_MS);
    }

    static Context storageContext(Context context) {
        Context app = context.getApplicationContext();
        if (!app.isDeviceProtectedStorage()) return app;
        try {
            Context credential = app.createPackageContext(
                    app.getPackageName(), CONTEXT_CREDENTIAL_PROTECTED_STORAGE);
            return new ContextWrapper(credential) {
                @Override
                public Context getApplicationContext() {
                    // WorkManager normalizes Context again; keep the CE wrapper as the application Context.
                    return this;
                }
            };
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("Credential-protected storage is unavailable", error);
        }
    }
}
