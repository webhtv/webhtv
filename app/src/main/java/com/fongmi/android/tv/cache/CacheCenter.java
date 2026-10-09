package com.fongmi.android.tv.cache;

import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.event.RefreshEvent;
import com.github.catvod.utils.Prefers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class CacheCenter {

    private static final long SNAPSHOT_CACHE_MS = 3_000L;
    private static volatile CacheCenter instance;

    private final Context context;
    private final CacheInventory inventory;
    private final ExecutorService executor;
    private volatile CacheSnapshot snapshot;
    private volatile long snapshotAtMs;

    private CacheCenter(Context context) {
        this.context = context.getApplicationContext();
        CachePolicyStore.migrate();
        this.inventory = new CacheInventory(context);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cache-inventory");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static CacheCenter get() {
        CacheCenter current = instance;
        if (current != null) return current;
        synchronized (CacheCenter.class) {
            if (instance == null) instance = new CacheCenter(App.get());
            return instance;
        }
    }

    public void requestSnapshot(boolean force, Consumer<CacheSnapshot> callback) {
        long now = System.currentTimeMillis();
        CacheSnapshot current = snapshot;
        if (!force && current != null && now - snapshotAtMs < SNAPSHOT_CACHE_MS) {
            App.post(() -> callback.accept(current));
            return;
        }
        executor.execute(() -> {
            long startedAt = System.currentTimeMillis();
            CacheSnapshot next = inventory.scan();
            long durationMs = System.currentTimeMillis() - startedAt;
            snapshot = next;
            snapshotAtMs = System.currentTimeMillis();
            Prefers.put("cache_mgmt_inventory_duration_ms", durationMs);
            Prefers.put("cache_mgmt_inventory_modules", next.modules().size());
            Prefers.put("cache_mgmt_inventory_warnings", next.warnings().size());
            App.post(() -> callback.accept(next));
        });
    }

    /**
     * Drops the cached snapshot so the next read reflects the current disk state.
     *
     * <p>Without this the 3-second snapshot cache keeps serving pre-cleanup numbers, and every
     * other cache surface stays stale until the user leaves and re-enters the screen. Safe to call
     * from any thread: the fields are volatile.</p>
     */
    public void invalidate() {
        snapshot = null;
        snapshotAtMs = 0L;
    }

    /**
     * Asks every cache surface to display the current cache state.
     *
     * <p>Surfaces only re-read the inventory when told to, so a cleanup result would otherwise stay
     * invisible until the page is left and re-entered.</p>
     */
    public void publishChanged() {
        RefreshEvent.cache();
    }

    /**
     * Invalidates the snapshot and notifies every cache surface. Call after any cache mutation
     * (cleanup, configured limits, quota changes).
     */
    public void notifyChanged() {
        invalidate();
        RefreshEvent.cache();
    }

    public long systemQuotaBytes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0;
        try {
            StorageManager manager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            return manager == null ? 0 : Math.max(0, manager.getCacheQuotaBytes(manager.getUuidForPath(context.getCacheDir())));
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
