package com.fongmi.android.tv.playback.origin;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.view.LayoutInflater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OriginStorageContext extends ContextWrapper {
    private final File root;
    private final Map<String, SharedPreferences> preferences = new ConcurrentHashMap<>();

    public OriginStorageContext(Context base, String leaseId) {
        super(base);
        UUID.fromString(leaseId);
        root = new File(base.getNoBackupFilesDir(), "origin/" + leaseId);
        directory(root);
    }

    public File root() { return root; }
    private static File directory(File file) {
        if (!file.isDirectory() && !file.mkdirs()) throw new IllegalStateException("ISOLATION_ERROR: " + file);
        return file;
    }
    private File child(String name) { return directory(new File(root, name)); }
    private static String name(String value) {
        if (value == null || value.isEmpty() || value.contains("/") || value.contains("\\") || value.equals("..")) throw new IllegalArgumentException("SNAPSHOT_INVALID");
        return value;
    }
    @Override public Context getApplicationContext() { return this; }
    @Override public File getDataDir() { return root; }
    @Override public File getFilesDir() { return child("files"); }
    @Override public File getCacheDir() { return child("cache"); }
    @Override public File getCodeCacheDir() { return child("code_cache"); }
    @Override public File getNoBackupFilesDir() { return child("no_backup"); }
    @Override public File getDir(String value, int mode) { return child("app_" + name(value)); }
    @Override public File getExternalFilesDir(String type) { return type == null ? child("external_files") : directory(new File(child("external_files"), name(type))); }
    @Override public File[] getExternalFilesDirs(String type) { return new File[]{getExternalFilesDir(type)}; }
    @Override public File getExternalCacheDir() { return child("external_cache"); }
    @Override public File[] getExternalCacheDirs() { return new File[]{getExternalCacheDir()}; }
    @Override public File[] getExternalMediaDirs() { return new File[]{child("media")}; }
    @Override public File getFileStreamPath(String value) { return new File(getFilesDir(), name(value)); }
    @Override public FileInputStream openFileInput(String value) throws FileNotFoundException { return new FileInputStream(getFileStreamPath(value)); }
    @Override public FileOutputStream openFileOutput(String value, int mode) throws FileNotFoundException { return new FileOutputStream(getFileStreamPath(value), (mode & MODE_APPEND) != 0); }
    @Override public boolean deleteFile(String value) { return getFileStreamPath(value).delete(); }
    @Override public String[] fileList() { String[] result = getFilesDir().list(); return result == null ? new String[0] : result; }
    @Override public File getDatabasePath(String value) { return new File(child("databases"), name(value)); }
    @Override public String[] databaseList() { String[] result = child("databases").list(); return result == null ? new String[0] : result; }
    @Override public boolean deleteDatabase(String value) { return SQLiteDatabase.deleteDatabase(getDatabasePath(value)); }
    @Override public SQLiteDatabase openOrCreateDatabase(String value, int mode, SQLiteDatabase.CursorFactory factory) { return openOrCreateDatabase(value, mode, factory, null); }
    @Override public SQLiteDatabase openOrCreateDatabase(String value, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
        int flags = SQLiteDatabase.CREATE_IF_NECESSARY;
        if ((mode & MODE_ENABLE_WRITE_AHEAD_LOGGING) != 0) flags |= SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
        return SQLiteDatabase.openDatabase(getDatabasePath(value).getPath(), factory, flags, handler);
    }
    @Override public ApplicationInfo getApplicationInfo() { ApplicationInfo info = new ApplicationInfo(super.getApplicationInfo()); info.dataDir = root.getPath(); return info; }
    @Override public Context createConfigurationContext(android.content.res.Configuration configuration) { return this; }
    @Override public Context createDeviceProtectedStorageContext() { return this; }
    @Override public Object getSystemService(String service) {
        Object value = super.getSystemService(service);
        return LAYOUT_INFLATER_SERVICE.equals(service) ? ((LayoutInflater) value).cloneInContext(this) : value;
    }
    @Override public SharedPreferences getSharedPreferences(String value, int mode) {
        return preferences.computeIfAbsent(name(value), key -> new PrivatePreferences(new File(child("shared_prefs"), key + ".bin")));
    }
    @Override public boolean deleteSharedPreferences(String value) { preferences.remove(name(value)); return new File(child("shared_prefs"), value + ".bin").delete(); }

    private static final class PrivatePreferences implements SharedPreferences {
        private final File file;
        private final Map<String, Object> values = new HashMap<>();
        private final Set<OnSharedPreferenceChangeListener> listeners = new HashSet<>();
        PrivatePreferences(File file) {
            this.file = file;
            if (file.isFile()) try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                Object stored = in.readObject();
                if (stored instanceof Map<?, ?> map) for (Map.Entry<?, ?> entry : map.entrySet()) if (entry.getKey() instanceof String key) values.put(key, entry.getValue());
            } catch (Exception e) { throw new IllegalStateException("ISOLATION_ERROR", e); }
        }
        @Override public synchronized Map<String, ?> getAll() { return new HashMap<>(values); }
        @Override public synchronized String getString(String key, String fallback) { Object value = values.get(key); return value == null ? fallback : (String) value; }
        @Override public synchronized Set<String> getStringSet(String key, Set<String> fallback) { Object value = values.get(key); return value == null ? fallback : new HashSet<>((Set<String>) value); }
        @Override public synchronized int getInt(String key, int fallback) { Object value = values.get(key); return value == null ? fallback : ((Number) value).intValue(); }
        @Override public synchronized long getLong(String key, long fallback) { Object value = values.get(key); return value == null ? fallback : ((Number) value).longValue(); }
        @Override public synchronized float getFloat(String key, float fallback) { Object value = values.get(key); return value == null ? fallback : ((Number) value).floatValue(); }
        @Override public synchronized boolean getBoolean(String key, boolean fallback) { Object value = values.get(key); return value == null ? fallback : (Boolean) value; }
        @Override public synchronized boolean contains(String key) { return values.containsKey(key); }
        @Override public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.add(listener); }
        @Override public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.remove(listener); }
        @Override public Editor edit() { return new PrivateEditor(); }
        private final class PrivateEditor implements Editor {
            private final Map<String, Object> changed = new HashMap<>();
            private boolean clear;
            @Override public Editor putString(String key, String value) { changed.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> value) { changed.put(key, value == null ? null : new HashSet<>(value)); return this; }
            @Override public Editor putInt(String key, int value) { changed.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { changed.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { changed.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { changed.put(key, value); return this; }
            @Override public Editor remove(String key) { changed.put(key, null); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public void apply() { if (!commit()) throw new IllegalStateException("ISOLATION_ERROR"); }
            @Override public boolean commit() {
                Set<OnSharedPreferenceChangeListener> notify;
                synchronized (PrivatePreferences.this) {
                    if (clear) values.clear();
                    for (Map.Entry<String, Object> entry : changed.entrySet()) { if (entry.getValue() == null) values.remove(entry.getKey()); else values.put(entry.getKey(), entry.getValue()); }
                    File temporary = new File(file.getPath() + ".tmp");
                    try {
                        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(temporary))) { out.writeObject(values); }
                        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) { return false; }
                    notify = new HashSet<>(listeners);
                }
                for (String key : changed.keySet()) for (OnSharedPreferenceChangeListener listener : notify) listener.onSharedPreferenceChanged(PrivatePreferences.this, key);
                return true;
            }
        }
    }
}
