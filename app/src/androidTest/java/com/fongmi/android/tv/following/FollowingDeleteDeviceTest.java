package com.fongmi.android.tv.following;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingDeleteDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private static final String IDENTITY = "tmdb:tv:993344:s1";

    @Before
    public void setUp() {
        FollowingStore.replaceAll(List.of(), List.of());
        Following item = new Following();
        item.identityKey = IDENTITY;
        item.seriesKey = "tmdb:tv:993344";
        item.vodName = "取消追更测试";
        item.trackedSeason = 1;
        item.enabled = true;
        FollowingSource source = new FollowingSource();
        source.followingKey = IDENTITY;
        source.siteKey = "site";
        source.vodId = "vod";
        FollowingStore.saveNew(item, source);
    }

    @After
    public void tearDown() {
        FollowingStore.replaceAll(List.of(), List.of());
    }

    @Test
    public void deleteStartedOnMainThreadCompletesWithoutRoomMainThreadCrash() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                FollowingPlaybackBridge.deleteAsync(IDENTITY, error -> {
                    failure.set(error);
                    latch.countDown();
                }));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        // 删除写墓碑：UI 查不到，但原始行仍在（墓碑随同步传播）。
        assertNull(FollowingStore.find(IDENTITY));
        assertNotNull(FollowingStore.findAny(IDENTITY));
        assertTrue(FollowingStore.findAny(IDENTITY).isDeleted());
    }

    @Test
    public void tombstoneSurvivesRemoteSnapshotMergeWithoutDeletedRow() throws Exception {
        // 对端快照不含已取消项（旧版本设备）：本地墓碑不能被 union 合并复活。
        awaitDelete();
        FollowingStore.mergeAll(List.of(), List.of());
        assertNotNull(FollowingStore.findAny(IDENTITY));
        assertTrue(FollowingStore.findAny(IDENTITY).isDeleted());
        assertNull(FollowingStore.find(IDENTITY));

        // 对端快照携带同一墓碑：墓碑保留，不产生重复行。
        Following tombstone = FollowingStore.findAny(IDENTITY).copy();
        FollowingStore.mergeAll(List.of(tombstone), List.of());
        assertNotNull(FollowingStore.findAny(IDENTITY));
        assertTrue(FollowingStore.findAny(IDENTITY).isDeleted());
        assertEquals(tombstone.deletedAt, FollowingStore.findAny(IDENTITY).deletedAt);
    }

    @Test
    public void reFollowRevivesTombstoneWithFreshCreatedAt() throws Exception {
        awaitDelete();
        long deletedAt = FollowingStore.findAny(IDENTITY).deletedAt;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Following> revived = new AtomicReference<>();
        Following fresh = FollowingDeviceDataRule.newFollowing(IDENTITY);
        FollowingPlaybackBridge.addAsync(fresh, null, (saved, error) -> {
            revived.set(saved);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(revived.get());

        Following active = FollowingStore.find(IDENTITY);
        assertNotNull(active);
        assertFalse(active.isDeleted());
        assertTrue(active.createdAt > deletedAt);
    }

    @Test
    public void alistSubscriptionImportSkipsTombstonedIdentity() throws Exception {
        // 用户已取消的追更，订阅导入不得复活墓碑，也不得新建同 identityKey 行。
        awaitDelete();
        AlistSubscriptionImporter.Candidate candidate = new AlistSubscriptionImporter.Candidate();
        candidate.title = "取消追更测试";
        candidate.season = 1;
        candidate.tmdbId = 993344;

        AlistSubscriptionImporter.ImportResult result = AlistSubscriptionImporter.importCandidates(List.of(candidate));

        assertEquals(0, result.created);
        assertNull(FollowingStore.find(IDENTITY));
        assertTrue(FollowingStore.findAny(IDENTITY).isDeleted());
    }

    private void awaitDelete() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        FollowingPlaybackBridge.deleteAsync(IDENTITY, error -> {
            failure.set(error);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
    }
}
