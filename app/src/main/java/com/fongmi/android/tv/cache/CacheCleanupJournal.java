package com.fongmi.android.tv.cache;

import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CacheCleanupJournal {

    private static final String KEY_HISTORY = "cache_mgmt_cleanup_history";
    private static final int MAX_RECORDS = 20;
    private static final Object LOCK = new Object();

    private CacheCleanupJournal() {
    }

    public static void record(CacheCleanupRecord record) {
        if (record == null) return;
        synchronized (LOCK) {
            ArrayList<CacheCleanupRecord> records = new ArrayList<>(load());
            records.add(record);
            while (records.size() > MAX_RECORDS) records.remove(0);
            try {
                Prefers.put(KEY_HISTORY, new Gson().toJson(records));
            } catch (Throwable ignored) {
            }
        }
    }

    public static List<CacheCleanupRecord> recent() {
        synchronized (LOCK) {
            return Collections.unmodifiableList(load());
        }
    }

    private static List<CacheCleanupRecord> load() {
        try {
            String json = Prefers.getString(KEY_HISTORY, "[]");
            CacheCleanupRecord[] values = new Gson().fromJson(json, CacheCleanupRecord[].class);
            return values == null ? List.of() : List.of(values);
        } catch (Throwable ignored) {
            return List.of();
        }
    }
}
