package com.fongmi.android.tv.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.PlaybackDeleteTombstone;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.fongmi.android.tv.db.dao.ConfigDao;
import com.fongmi.android.tv.db.dao.DeviceDao;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import com.fongmi.android.tv.db.dao.LiveDao;
import com.fongmi.android.tv.db.dao.PlaybackDeleteTombstoneDao;
import com.fongmi.android.tv.db.dao.SiteDao;
import com.fongmi.android.tv.db.dao.TrackDao;
import com.fongmi.android.tv.db.dao.TmdbSeasonProgressDao;
import com.fongmi.android.tv.utils.AppBackup;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class, PlaybackDeleteTombstone.class, TmdbSeasonProgress.class}, version = AppDatabase.VERSION, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    // VERSION = 47 was the pre-identity-alias schema; 48 adds migration clues.
    public static final int VERSION = 48;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";
    private static final int BACKUP_KEEP_COUNT = 7;
    private static final int PARTIAL_BACKUP_KEEP_COUNT = 1;
    private static final String BACKUP_TEMP_FILE = ".backup.tmp";
    private static final String QUARANTINE_SUFFIX = ".incompatible-";
    private static final int QUARANTINE_KEEP_COUNT = 2;
    private static final String[] DATABASE_SUFFIXES = {"", "-wal", "-shm", "-journal"};
    private static final Object BACKUP_LOCK = new Object();
    private static final AtomicBoolean AUTO_BACKUP_PENDING = new AtomicBoolean();

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    public static void backup() {
        backup(new com.fongmi.android.tv.impl.Callback());
    }

    public static void autoBackup() {
        if (!beginAutoBackup()) return;
        try {
            backup(new com.fongmi.android.tv.impl.Callback(), null, true);
        } catch (RuntimeException e) {
            finishAutoBackup();
            throw e;
        }
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback) {
        backup(callback, null);
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress) {
        backup(callback, progress, false);
    }

    private static void backup(com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress, boolean automatic) {
        Task.execute(() -> {
            try {
                synchronized (BACKUP_LOCK) {
                    File temporary = new File(Path.tv(), BACKUP_TEMP_FILE);
                    try {
                        AppBackup.CreateResult result = AppBackup.create(temporary, progress);
                        File target = new File(Path.tv(), AppBackup.fileName(result.hasWarning()));
                        publishBackup(temporary, target);
                        App.post(() -> {
                            callback.success();
                            if (result.hasWarning()) Notify.show(result.warning);
                        });
                        cleanOld();
                    } catch (Exception e) {
                        SpiderDebug.log("backup", "local create failed error=%s", e.getMessage());
                        SpiderDebug.log("backup", e);
                        App.post(callback::error);
                    } finally {
                        Path.clear(temporary);
                    }
                }
            } finally {
                if (automatic) finishAutoBackup();
            }
        });
    }

    static boolean beginAutoBackup() {
        return AUTO_BACKUP_PENDING.compareAndSet(false, true);
    }

    static void finishAutoBackup() {
        AUTO_BACKUP_PENDING.set(false);
    }

    static void publishBackup(File temporary, File target) throws IOException {
        if (temporary.renameTo(target)) return;
        if (target.exists() && !target.delete()) throw new IOException("Unable to replace backup: " + target.getAbsolutePath());
        if (!temporary.renameTo(target)) throw new IOException("Unable to publish backup: " + target.getAbsolutePath());
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback) {
        restore(file, callback, null);
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress) {
        Task.execute(() -> {
            try {
                AppBackup.RestoreResult result = AppBackup.restore(file, progress);
                App.post(() -> {
                    callback.success();
                    if (result.hasWarning()) Notify.show(result.warning);
                });
            } catch (Exception e) {
                SpiderDebug.log("backup", "local restore failed file=%s error=%s", file == null ? "" : file.getAbsolutePath(), e.getMessage());
                App.post(callback::error);
            }
        });
    }

    private static void cleanOld() {
        cleanOld(Path.tv());
    }

    static void cleanOld(File directory) {
        List<File> complete = new ArrayList<>();
        List<File> partial = new ArrayList<>();
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!AppBackup.isBackup(file)) continue;
            if (AppBackup.isPartialBackupName(file.getName())) partial.add(file);
            else complete.add(file);
        }
        trimBackups(complete, BACKUP_KEEP_COUNT);
        trimBackups(partial, PARTIAL_BACKUP_KEEP_COUNT);
    }

    private static void trimBackups(List<File> items, int keepCount) {
        items.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        for (int i = keepCount; i < items.size(); i++) Path.clear(items.get(i));
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .addMigrations(Migrations.MIGRATION_35_36)
                .addMigrations(Migrations.MIGRATION_36_37)
                .addMigrations(Migrations.MIGRATION_37_38)
                .addMigrations(Migrations.MIGRATION_38_39)
                .addMigrations(Migrations.MIGRATION_39_40)
                .addMigrations(Migrations.MIGRATION_40_41)
                .addMigrations(Migrations.MIGRATION_41_42)
                .addMigrations(Migrations.MIGRATION_42_43)
                .addMigrations(Migrations.MIGRATION_43_44)
                .addMigrations(Migrations.MIGRATION_44_45)
                .addMigrations(Migrations.MIGRATION_45_46)
                .addMigrations(Migrations.MIGRATION_46_47)
                .addMigrations(Migrations.MIGRATION_47_48)
                .openHelperFactory(new IntegrityRecoveringOpenHelperFactory(new FrameworkSQLiteOpenHelperFactory()))
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries().build();
    }

    /**
     * Keeps Room's data-integrity check from turning into a permanent crash loop.
     *
     * <p>Room refuses to open a database whose stored identity hash does not match the schema this
     * build expects and reports it as an {@link IllegalStateException}. Two builds that publish the
     * same {@code versionCode} but different entities hit exactly that state, and because the
     * failure happens while the database is being opened it also breaks every later launch that
     * touches it. Nothing in the app can catch it at the call sites, so it is handled while the
     * connection is opened: the database is moved aside so the original file survives for manual
     * recovery (its {@code -wal}/{@code -shm}/{@code -journal} sidecars are cleared, and a file that
     * cannot be moved is deleted, because the rebuild needs the path free) and a fresh database is
     * created, which keeps the app usable.
     *
     * <p>Only Room's identity failure is recovered. Any other open failure is rethrown unchanged.
     */
    static final class IntegrityRecoveringOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {

        private final SupportSQLiteOpenHelper.Factory delegate;

        IntegrityRecoveringOpenHelperFactory(SupportSQLiteOpenHelper.Factory delegate) {
            this.delegate = delegate;
        }

        @NonNull
        @Override
        public SupportSQLiteOpenHelper create(@NonNull SupportSQLiteOpenHelper.Configuration configuration) {
            return new IntegrityRecoveringOpenHelper(delegate, configuration);
        }
    }

    private static final class IntegrityRecoveringOpenHelper implements SupportSQLiteOpenHelper {

        private final SupportSQLiteOpenHelper.Factory factory;
        private final SupportSQLiteOpenHelper.Configuration configuration;
        private SupportSQLiteOpenHelper delegate;
        private boolean recovered;

        IntegrityRecoveringOpenHelper(SupportSQLiteOpenHelper.Factory factory, SupportSQLiteOpenHelper.Configuration configuration) {
            this.factory = factory;
            this.configuration = configuration;
            this.delegate = factory.create(configuration);
        }

        @NonNull
        @Override
        public String getDatabaseName() {
            return delegate.getDatabaseName();
        }

        @Override
        public void setWriteAheadLoggingEnabled(boolean enabled) {
            delegate.setWriteAheadLoggingEnabled(enabled);
        }

        @NonNull
        @Override
        public SupportSQLiteDatabase getWritableDatabase() {
            return open(true);
        }

        @NonNull
        @Override
        public SupportSQLiteDatabase getReadableDatabase() {
            return open(false);
        }

        @Override
        public void close() {
            delegate.close();
        }

        private SupportSQLiteDatabase open(boolean writable) {
            try {
                return writable ? delegate.getWritableDatabase() : delegate.getReadableDatabase();
            } catch (IllegalStateException e) {
                if (recovered || !isIdentityFailure(e)) throw e;
                recovered = true;
                SpiderDebug.log("database", "integrity check failed, rebuilding %s error=%s",
                        getDatabaseName(), e.getMessage());
                quarantine(configuration, getDatabaseName());
                try {
                    delegate.close();
                } catch (RuntimeException ignored) {
                    // The failed helper must not mask the rebuild below.
                }
                delegate = factory.create(configuration);
                return writable ? delegate.getWritableDatabase() : delegate.getReadableDatabase();
            }
        }
    }

    private static boolean isIdentityFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("Room cannot verify the data integrity")) return true;
        }
        return false;
    }

    /**
     * Moves the unreadable database aside so the rebuild starts clean while the original file stays
     * on disk; only a file that cannot be moved is deleted.
     */
    static void quarantine(SupportSQLiteOpenHelper.Configuration configuration, String name) {
        File file = databaseFile(configuration, name);
        File directory = file == null ? null : file.getParentFile();
        if (file == null || directory == null) return;
        String stamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
        for (String suffix : DATABASE_SUFFIXES) {
            File source = new File(file.getPath() + suffix);
            if (!source.isFile()) continue;
            if (!suffix.isEmpty()) {
                Path.clear(source);
                continue;
            }
            File target = new File(directory, file.getName() + QUARANTINE_SUFFIX + stamp);
            if (!source.renameTo(target)) Path.clear(source);
        }
        trimQuarantine(directory, file.getName());
    }

    private static void trimQuarantine(File directory, String databaseName) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith(databaseName + QUARANTINE_SUFFIX));
        if (files == null || files.length <= QUARANTINE_KEEP_COUNT) return;
        List<File> items = new ArrayList<>(List.of(files));
        items.sort((left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        for (int index = QUARANTINE_KEEP_COUNT; index < items.size(); index++) Path.clear(items.get(index));
    }

    private static File databaseFile(SupportSQLiteOpenHelper.Configuration configuration, String name) {
        Context context = configuration == null ? null : configuration.context;
        if (context == null || name == null || name.isEmpty()) return null;
        if (configuration.useNoBackupDirectory) return new File(context.getNoBackupFilesDir(), name);
        return context.getDatabasePath(name);
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();

    public abstract PlaybackDeleteTombstoneDao getPlaybackDeleteTombstoneDao();

    public abstract TmdbSeasonProgressDao getTmdbSeasonProgressDao();
}
