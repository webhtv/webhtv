package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.TmdbItem;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingMigrationDeviceTest {

    private static final String SOURCE_KEY = "source:9:migrate:930001:s0";
    private static final String TARGET_KEY = "tmdb:tv:930001:s1";

    @After
    public void tearDown() {
        // 测试清理用物理删除：墓碑会永久阻止同 identityKey 的迁移断言（resolveTmdb 墓碑防护）。
        FollowingStore.purge(SOURCE_KEY);
        FollowingStore.purge(TARGET_KEY);
    }

    @Test
    public void sourceFallbackMigratesToMatchedTmdbIdentity() {
        FollowingStore.purge(SOURCE_KEY);
        FollowingStore.purge(TARGET_KEY);
        Following source = new Following();
        source.identityKey = SOURCE_KEY;
        source.seriesKey = "source:9:migrate:930001";
        source.cid = 9;
        source.siteKey = "migrate";
        source.vodId = "930001";
        source.vodName = "迁移验证剧集";
        source.mediaType = "tv";
        source.trackedSeason = 0;
        source.watchedEpisode = 1;
        source.readWatermarkEpisode = 1;
        source.enabled = true;
        source.nextCheckAt = Long.MAX_VALUE;
        FollowingSource binding = new FollowingSource();
        binding.followingKey = SOURCE_KEY;
        binding.cid = source.cid;
        binding.siteKey = source.siteKey;
        binding.vodId = source.vodId;
        binding.vodName = source.vodName;
        binding.playableEpisode = 16;
        binding.playableCount = 16;
        binding.preferred = true;
        FollowingStore.saveNew(source, binding);

        FollowingMetadataSnapshot snapshot = new FollowingMetadataSnapshot();
        snapshot.source = "tmdb";
        snapshot.status = FollowingMetadataSnapshot.RETURNING;
        snapshot.latestReleasedSeason = 1;
        snapshot.latestReleasedEpisode = 12;
        snapshot.seasonTotalEpisodes = 18;
        snapshot.seasonReleasedEpisodes = 12;
        snapshot.seriesTotalEpisodes = 18;
        snapshot.fetchedAt = System.currentTimeMillis();

        Following migrated = FollowingStore.resolveTmdb(
                new TmdbItem(930001, "tv", source.vodName, "", "", "", ""), 1,
                source.cid, source.siteKey, source.vodId, snapshot);

        assertNotNull(migrated);
        assertNull(FollowingStore.find(SOURCE_KEY));
        Following target = FollowingStore.find(TARGET_KEY);
        assertNotNull(target);
        assertEquals(930001, target.tmdbId);
        assertEquals(1, target.trackedSeason);
        assertEquals(12, target.latestReleasedEpisode);
        assertEquals(FollowingMetadataSnapshot.RETURNING, target.officialStatus);
        FollowingSource moved = FollowingStore.preferredSource(TARGET_KEY);
        assertNotNull(moved);
        assertEquals(1, moved.playableSeason);
        assertEquals(16, moved.playableEpisode);
    }

    /**
     * 锁定调用方必须依赖的契约：目标已是墓碑时 `resolveTmdb` 会**故意返回墓碑行而不是 null**
     * （C9 防复活守卫）。因此任何“已追更？”判定都不能写成 `item != null`，
     * 否则取消追更后按钮仍显示“已追更”，内联播放中再点只会重复写墓碑、无法重新追更。
     */
    @Test
    public void tombstonedTargetIsReturnedAsTombstoneNotAsNull() throws Exception {
        FollowingStore.purge(SOURCE_KEY);
        FollowingStore.purge(TARGET_KEY);

        Following target = new Following();
        target.identityKey = TARGET_KEY;
        target.seriesKey = "tmdb:tv:930001";
        target.cid = 9;
        target.siteKey = "migrate";
        target.vodId = "930001";
        target.vodName = "墓碑判定验证";
        target.mediaType = "tv";
        target.tmdbId = 930001;
        target.trackedSeason = 1;
        target.enabled = true;
        target.nextCheckAt = Long.MAX_VALUE;
        FollowingStore.saveNew(target, null);
        FollowingStore.delete(TARGET_KEY);

        // 活跃查询看不到墓碑，但原始行仍在。
        assertNull(FollowingStore.find(TARGET_KEY));
        assertNotNull(FollowingStore.findAny(TARGET_KEY));
        assertTrue(FollowingStore.findAny(TARGET_KEY).isDeleted());

        Following resolved = FollowingStore.resolveTmdb(
                new TmdbItem(930001, "tv", target.vodName, "", "", "", ""), 1,
                9, "migrate", "930001", null);

        assertNotNull("resolveTmdb 对墓碑目标必须返回墓碑行而非 null", resolved);
        assertTrue("返回行必须是墓碑，调用方据此判定为未追更", resolved.isDeleted());

        // 完整闭环：UI 的 isFollowed() 会把墓碑判为未追更 → 走 addFollowing →
        // FollowingPlaybackBridge.addAsync → FollowingStore.saveNew 的复活分支。
        // 这条链路必须能把已取消的追更重新追回来。
        Following fresh = new Following();
        fresh.identityKey = TARGET_KEY;
        fresh.seriesKey = "tmdb:tv:930001";
        fresh.cid = 9;
        fresh.siteKey = "migrate";
        fresh.vodId = "930001";
        fresh.vodName = target.vodName;
        fresh.mediaType = "tv";
        fresh.tmdbId = 930001;
        fresh.trackedSeason = 1;
        fresh.enabled = true;
        fresh.createdAt = System.currentTimeMillis();
        fresh.nextCheckAt = Long.MAX_VALUE;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        FollowingPlaybackBridge.addAsync(fresh, null, (saved, error) -> {
            failure.set(error);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());

        Following revived = FollowingStore.find(TARGET_KEY);
        assertNotNull("点击“加入追更”后必须复活为活跃行", revived);
        assertFalse("复活后不得再是墓碑", revived.isDeleted());
        assertTrue(revived.enabled);
        assertEquals(930001, revived.tmdbId);
    }
}
