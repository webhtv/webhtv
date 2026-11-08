package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingUpdateCoordinatorDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private Context context;
    private String identityKey;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        FollowingSettings.setEnabled(true);
        identityKey = "tmdb:tv:991234:s1";
        FollowingDatabase database = FollowingDatabase.get();
        database.getFollowingSourceDao().deleteAll();
        database.getFollowingDao().deleteAll();

        Following item = new Following();
        item.identityKey = identityKey;
        item.seriesKey = "tmdb:tv:991234";
        item.cid = 1;
        item.siteKey = "test-site";
        item.vodId = "test-vod";
        item.vodName = "TMDB 降级测试";
        item.mediaType = "tv";
        item.tmdbId = 991234;
        item.trackedSeason = 1;
        item.latestReleasedSeason = 1;
        item.latestReleasedEpisode = 6;
        item.seasonReleasedEpisodes = 6;
        item.officialStatus = FollowingMetadataSnapshot.RETURNING;
        item.metadataUpdatedAt = 1_000;
        item.enabled = true;
        item.nextCheckAt = Long.MAX_VALUE;
        item.createdAt = 1_000;
        item.updatedAt = 1_000;

        FollowingSource source = new FollowingSource();
        source.followingKey = identityKey;
        source.cid = item.cid;
        source.siteKey = item.siteKey;
        source.vodId = item.vodId;
        source.vodName = item.vodName;
        source.preferred = true;
        FollowingStore.saveNew(item, source);
    }

    @After
    public void tearDown() {
        FollowingScheduler.cancelNext(context, identityKey);
        FollowingStore.delete(identityKey);
    }

    @Test
    public void tmdbFailureFallsBackToBoundSourceWithoutOverwritingOfficialState() {
        AtomicInteger probes = new AtomicInteger();
        FollowingMetadataClient metadata = new FollowingMetadataClient() {
            @Override
            public FollowingMetadataSnapshot fetch(Following item, boolean refresh) throws Exception {
                throw new IOException("TMDB unavailable");
            }
        };
        FollowingSourceProbe probe = new FollowingSourceProbe() {
            @Override
            public FollowingSourceSnapshot probe(Following item, FollowingSource source) {
                probes.incrementAndGet();
                source.playableSeason = item.trackedSeason;
                source.playableEpisode = 8;
                source.playableCount = 8;
                source.lastProbeAt = System.currentTimeMillis();
                source.lastError = "";
                FollowingSourceSnapshot snapshot = new FollowingSourceSnapshot();
                snapshot.source = source;
                snapshot.playableEpisode = 8;
                snapshot.playableCount = 8;
                snapshot.probedAt = source.lastProbeAt;
                return snapshot;
            }
        };

        Following item = FollowingStore.find(identityKey);
        assertTrue(new FollowingUpdateCoordinator(metadata, probe).check(item, false));

        Following stored = FollowingStore.find(identityKey);
        FollowingSource source = FollowingStore.preferredSource(identityKey);
        assertEquals(FollowingMetadataSnapshot.RETURNING, stored.officialStatus);
        assertEquals(6, stored.latestReleasedEpisode);
        assertEquals(0, stored.failureCount);
        assertEquals("TMDB不可用，已回退原站", stored.lastError);
        assertEquals(8, source.playableEpisode);
        assertEquals(1, probes.get());
    }

    @Test
    public void failureKeepsPreviousSourceAndOfficialSnapshot() {
        // 只锁「失败不覆盖旧快照 + 失败态记账」；失败分支内 update→reconcile 的先后顺序不在此用例覆盖范围，
        // 由 FollowingFailureReconcileContractTest#reconcileRunsAfterTheFailureWrite 锁定。
        AtomicInteger probes = new AtomicInteger();
        FollowingSource seeded = FollowingStore.preferredSource(identityKey);
        seeded.playableSeason = 1;
        seeded.playableEpisode = 8;
        seeded.playableCount = 8;
        seeded.lastProbeAt = 1_000L;
        seeded.lastError = "";
        FollowingStore.updateSource(seeded);

        FollowingMetadataClient metadata = new FollowingMetadataClient() {
            @Override
            public FollowingMetadataSnapshot fetch(Following item, boolean refresh) throws Exception {
                throw new IOException("TMDB unavailable");
            }
        };
        FollowingSourceProbe probe = new FollowingSourceProbe() {
            @Override
            public FollowingSourceSnapshot probe(Following item, FollowingSource source) {
                probes.incrementAndGet();
                throw new IllegalStateException("来源没有可播放线路");
            }
        };

        long before = System.currentTimeMillis();
        Following item = FollowingStore.find(identityKey);
        assertFalse(new FollowingUpdateCoordinator(metadata, probe).check(item, true, false));

        Following stored = FollowingStore.find(identityKey);
        FollowingSource source = FollowingStore.preferredSource(identityKey);
        assertEquals(1, source.playableSeason);
        assertEquals(8, source.playableEpisode);
        assertEquals(8, source.playableCount);
        assertEquals(FollowingMetadataSnapshot.RETURNING, stored.officialStatus);
        assertEquals(6, stored.latestReleasedEpisode);
        assertEquals(1, stored.failureCount);
        assertTrue(stored.lastCheckedAt >= before);
        assertTrue(stored.lastError != null && !stored.lastError.isBlank());
        assertTrue(stored.nextCheckAt > before);
        assertEquals(1, probes.get());
        assertEquals("来源没有可播放线路", source.lastError);
    }
}
