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

    public static final int VERSION = 39;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";
    public static final String AUTO_PREFIX = "bak-auto-";

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
                AppBackup.CreateResult result = AppBackup.create(file, progress);
                App.post(() -> {
                    callback.success();
                    if (result.hasWarning()) Notify.show(result.warning);
                });
            } catch (Exception e) {
                SpiderDebug.log("backup", "local create failed error=%s", e.getMessage());
                App.post(callback::error);
            }
        });
    }

    private static void cleanAutoOnly() {
        File[] files = Path.tv().listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && file.getName().startsWith(AUTO_PREFIX)) {
                Path.clear(file);
            }
        }
    }
    
    public static void autoBackupOnExit() {
        Task.execute(AppDatabase::backupOnExitSync);
    }

    /**
     * 退出前同步备份（调用线程阻塞直到备份完成），供"返回键彻底退出"流程使用，
     * 避免结束进程前异步备份未跑完导致备份丢失。
     */
    public static void backupOnExitSync() {
        try {
            cleanAutoOnly();
            String timePart = AppBackup.fileName().substring(AppBackup.PREFIX.length());
            String fileName = AUTO_PREFIX + timePart;
            File file = new File(Path.tv(), fileName);
            AppBackup.CreateResult result = AppBackup.create(file, null);
            SpiderDebug.log("backup", "auto exit backup complete file=%s", file.getAbsolutePath());
        } catch (Exception e) {
            SpiderDebug.log("backup", "auto exit backup failed error=%s", e.getMessage());
        }
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
