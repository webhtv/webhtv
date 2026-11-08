package com.fongmi.android.tv.following;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.SQLiteConnection;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.fongmi.android.tv.App;

@Database(entities = {Following.class, FollowingSource.class}, version = 3, exportSchema = true)
public abstract class FollowingDatabase extends RoomDatabase {

    public static final String NAME = "following";
    public static final int VERSION = 3;
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `following` ADD COLUMN `next_air_weekday` INTEGER NOT NULL DEFAULT 0");
        }

        @Override
        public void migrate(@NonNull SQLiteConnection connection) {
            connection.prepare("ALTER TABLE `following` ADD COLUMN `next_air_weekday` INTEGER NOT NULL DEFAULT 0").step();
        }
    };
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        private static final String RECREATE = "CREATE TABLE IF NOT EXISTS `following_new` (`identity_key` TEXT NOT NULL, `series_key` TEXT NOT NULL, `cid` INTEGER NOT NULL, `site_key` TEXT NOT NULL, `vod_id` TEXT NOT NULL, `vod_name` TEXT NOT NULL, `vod_pic` TEXT NOT NULL, `media_type` TEXT NOT NULL, `tmdb_id` INTEGER NOT NULL, `tracked_season` INTEGER NOT NULL, `tracked_episode` INTEGER NOT NULL, `watched_season` INTEGER NOT NULL, `watched_episode` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `official_status` TEXT NOT NULL, `latest_released_season` INTEGER NOT NULL, `latest_released_episode` INTEGER NOT NULL, `season_total_episodes` INTEGER NOT NULL, `season_released_episodes` INTEGER NOT NULL, `series_total_episodes` INTEGER NOT NULL, `next_air_season` INTEGER NOT NULL, `next_air_episode` INTEGER NOT NULL, `next_air_at` INTEGER NOT NULL, `next_air_weekday` INTEGER NOT NULL, `last_observed_episode` INTEGER NOT NULL, `read_watermark_episode` INTEGER NOT NULL, `last_notified_episode` INTEGER NOT NULL, `last_notified_at` INTEGER NOT NULL, `has_update` INTEGER NOT NULL, `unwatched_count` INTEGER NOT NULL, `notify_enabled` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `deleted_at` INTEGER NOT NULL, `metadata_updated_at` INTEGER NOT NULL, `last_checked_at` INTEGER NOT NULL, `next_check_at` INTEGER NOT NULL, `failure_count` INTEGER NOT NULL, `last_error` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`identity_key`))";
        private static final String[] REINDEX = {
                "CREATE INDEX IF NOT EXISTS `index_following_series_key` ON `following` (`series_key`)",
                "CREATE INDEX IF NOT EXISTS `index_following_enabled_next_check_at` ON `following` (`enabled`, `next_check_at`)",
                "CREATE INDEX IF NOT EXISTS `index_following_has_update_updated_at` ON `following` (`has_update`, `updated_at`)",
                "CREATE INDEX IF NOT EXISTS `index_following_cid_site_key_vod_id` ON `following` (`cid`, `site_key`, `vod_id`)",
                "CREATE INDEX IF NOT EXISTS `index_following_tmdb_id_media_type_tracked_season` ON `following` (`tmdb_id`, `media_type`, `tracked_season`)",
                "CREATE INDEX IF NOT EXISTS `index_following_deleted_at` ON `following` (`deleted_at`)"
        };

        private void recreate(@NonNull SupportSQLiteDatabase db) {
            // 全量重建：ADD COLUMN NOT NULL DEFAULT 会让 Room 迁移校验因 defaultValue 不匹配而失败，
            // 官方标准姿势是建新表→拷贝→换名。同时把 v2 时代 enabled=0 的行（旧版墓碑传播的痕迹）转为墓碑。
            long now = System.currentTimeMillis();
            db.execSQL(RECREATE);
            db.execSQL("INSERT INTO `following_new` (`identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, `deleted_at`, `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at`) "
                    + "SELECT `identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, "
                    + "0, `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at` FROM `following` WHERE `enabled` = 1");
            db.execSQL("INSERT INTO `following_new` (`identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, `deleted_at`, `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at`) "
                    + "SELECT `identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, "
                    + now + ", `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at` FROM `following` WHERE `enabled` = 0");
            db.execSQL("DROP TABLE `following`");
            db.execSQL("ALTER TABLE `following_new` RENAME TO `following`");
            for (String sql : REINDEX) db.execSQL(sql);
        }

        private void recreate(@NonNull SQLiteConnection connection) {
            long now = System.currentTimeMillis();
            connection.prepare(RECREATE).step();
            connection.prepare("INSERT INTO `following_new` (`identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, `deleted_at`, `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at`) "
                    + "SELECT `identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, "
                    + "0, `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at` FROM `following` WHERE `enabled` = 1").step();
            connection.prepare("INSERT INTO `following_new` (`identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, `deleted_at`, `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at`) "
                    + "SELECT `identity_key`, `series_key`, `cid`, `site_key`, `vod_id`, `vod_name`, `vod_pic`, `media_type`, `tmdb_id`, `tracked_season`, `tracked_episode`, `watched_season`, `watched_episode`, `position`, `duration`, `official_status`, `latest_released_season`, `latest_released_episode`, `season_total_episodes`, `season_released_episodes`, `series_total_episodes`, `next_air_season`, `next_air_episode`, `next_air_at`, `next_air_weekday`, `last_observed_episode`, `read_watermark_episode`, `last_notified_episode`, `last_notified_at`, `has_update`, `unwatched_count`, `notify_enabled`, `enabled`, "
                    + now + ", `metadata_updated_at`, `last_checked_at`, `next_check_at`, `failure_count`, `last_error`, `created_at`, `updated_at` FROM `following` WHERE `enabled` = 0").step();
            connection.prepare("DROP TABLE `following`").step();
            connection.prepare("ALTER TABLE `following_new` RENAME TO `following`").step();
            for (String sql : REINDEX) connection.prepare(sql).step();
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            recreate(database);
        }

        @Override
        public void migrate(@NonNull SQLiteConnection connection) {
            recreate(connection);
        }
    };
    private static volatile FollowingDatabase instance;

    public static FollowingDatabase get() {
        if (instance == null) {
            synchronized (FollowingDatabase.class) {
                if (instance == null) instance = create(App.get());
            }
        }
        return instance;
    }

    public static FollowingDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), FollowingDatabase.class, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
    }

    public abstract FollowingDao getFollowingDao();

    public abstract FollowingSourceDao getFollowingSourceDao();
}
