package com.fongmi.android.tv.following;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class FollowingDatabaseTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private Context context;
    private FollowingDatabase database;
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, FollowingDatabase.class).build();
    }

    @After
    public void tearDown() {
        if (database != null) database.close();
    }

    @Test
    public void createsIndependentDatabaseAndStoresFollowAndSource() {
        Following item = new Following();
        item.identityKey = "tmdb:tv:1:s1";
        item.seriesKey = "tmdb:tv:1";
        item.vodName = "Example";
        item.trackedSeason = 1;
        item.enabled = true;
        item.nextCheckAt = 10;
        database.getFollowingDao().insertOrUpdate(item);

        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.siteKey = "site";
        source.vodId = "vod";
        source.preferred = true;
        database.getFollowingSourceDao().insertOrUpdate(source);

        assertNotNull(database.getFollowingDao().find(item.identityKey));
        assertEquals(item.identityKey, database.getFollowingDao().findDue(20).get(0).identityKey);
        assertEquals("vod", database.getFollowingSourceDao().findPreferred(item.identityKey).vodId);
    }

    @Test
    public void migratesV1FollowingDataToV2PreservingRows() throws Exception {
        String identityKey = "tmdb:tv:1:s1";
        String databaseName = FollowingDatabase.NAME + "-migration-test.db";
        context.deleteDatabase(databaseName);

        SQLiteDatabase raw = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null);
        try {
            raw.execSQL("CREATE TABLE IF NOT EXISTS `following` (`identity_key` TEXT NOT NULL, `series_key` TEXT NOT NULL, "
                    + "`cid` INTEGER NOT NULL, `site_key` TEXT NOT NULL, `vod_id` TEXT NOT NULL, `vod_name` TEXT NOT NULL, "
                    + "`vod_pic` TEXT NOT NULL, `media_type` TEXT NOT NULL, `tmdb_id` INTEGER NOT NULL, "
                    + "`tracked_season` INTEGER NOT NULL, `tracked_episode` INTEGER NOT NULL, `watched_season` INTEGER NOT NULL, "
                    + "`watched_episode` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, "
                    + "`official_status` TEXT NOT NULL, `latest_released_season` INTEGER NOT NULL, "
                    + "`latest_released_episode` INTEGER NOT NULL, `season_total_episodes` INTEGER NOT NULL, "
                    + "`season_released_episodes` INTEGER NOT NULL, `series_total_episodes` INTEGER NOT NULL, "
                    + "`next_air_season` INTEGER NOT NULL, `next_air_episode` INTEGER NOT NULL, `next_air_at` INTEGER NOT NULL, "
                    + "`last_observed_episode` INTEGER NOT NULL, `read_watermark_episode` INTEGER NOT NULL, "
                    + "`last_notified_episode` INTEGER NOT NULL, `last_notified_at` INTEGER NOT NULL, `has_update` INTEGER NOT NULL, "
                    + "`unwatched_count` INTEGER NOT NULL, `notify_enabled` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, "
                    + "`metadata_updated_at` INTEGER NOT NULL, `last_checked_at` INTEGER NOT NULL, `next_check_at` INTEGER NOT NULL, "
                    + "`failure_count` INTEGER NOT NULL, `last_error` TEXT NOT NULL, `created_at` INTEGER NOT NULL, "
                    + "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`identity_key`))");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_following_series_key` ON `following` (`series_key`)");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_following_enabled_next_check_at` ON `following` (`enabled`, `next_check_at`)");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_following_has_update_updated_at` ON `following` (`has_update`, `updated_at`)");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_following_cid_site_key_vod_id` ON `following` (`cid`, `site_key`, `vod_id`)");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_following_tmdb_id_media_type_tracked_season` ON `following` (`tmdb_id`, `media_type`, `tracked_season`)");
            raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            raw.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7e868fdbdbc43809b2189d43aa0f766b')");
            raw.execSQL("CREATE TABLE IF NOT EXISTS `following_source` (`following_key` TEXT NOT NULL, `cid` INTEGER NOT NULL, "
                    + "`site_key` TEXT NOT NULL, `vod_id` TEXT NOT NULL, `vod_name` TEXT NOT NULL, `vod_pic` TEXT NOT NULL, "
                    + "`vod_flag` TEXT NOT NULL, `playable_season` INTEGER NOT NULL, `playable_episode` INTEGER NOT NULL, "
                    + "`playable_count` INTEGER NOT NULL, `preferred` INTEGER NOT NULL, `last_probe_at` INTEGER NOT NULL, "
                    + "`last_error` TEXT NOT NULL, PRIMARY KEY(`following_key`, `cid`, `site_key`, `vod_id`))");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_following_source_following_key_preferred` ON `following_source` (`following_key`, `preferred`)");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_following_source_cid_site_key_vod_id` ON `following_source` (`cid`, `site_key`, `vod_id`)");
            raw.setVersion(1);
            raw.execSQL("INSERT INTO following (identity_key, series_key, cid, site_key, vod_id, vod_name, vod_pic, media_type, "
                    + "tmdb_id, tracked_season, tracked_episode, watched_season, watched_episode, position, duration, "
                    + "official_status, latest_released_season, latest_released_episode, season_total_episodes, "
                    + "season_released_episodes, series_total_episodes, next_air_season, next_air_episode, next_air_at, "
                    + "last_observed_episode, read_watermark_episode, last_notified_episode, last_notified_at, has_update, "
                    + "unwatched_count, notify_enabled, enabled, metadata_updated_at, last_checked_at, next_check_at, "
                    + "failure_count, last_error, created_at, updated_at) VALUES (?, '', 0, '', '', 'Example', '', 'tv', 1, 1, 0, "
                    + "0, 0, 0, 0, 'UNKNOWN', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 10, '', 0, 0)",
                    new Object[]{identityKey});
        } finally {
            raw.close();
        }

        FollowingDatabase migrated = null;
        try {
            migrated = Room.databaseBuilder(context, FollowingDatabase.class, databaseName)
                    .addMigrations(FollowingDatabase.MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build();
            Following item = migrated.getFollowingDao().find(identityKey);
            assertNotNull(item);
            assertEquals(0, item.nextAirWeekday);
        } finally {
            if (migrated != null) migrated.close();
            context.deleteDatabase(databaseName);
        }
    }

}
