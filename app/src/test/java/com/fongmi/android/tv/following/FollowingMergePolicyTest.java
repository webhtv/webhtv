package com.fongmi.android.tv.following;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowingMergePolicyTest {

    @Test
    public void mergeKeepsNewerProgressAndMaximumWatermarks() {
        Following local = following();
        local.watchedEpisode = 4;
        local.updatedAt = 200;
        local.readWatermarkEpisode = 3;
        local.nextCheckAt = 999;

        Following remote = following();
        remote.watchedEpisode = 7;
        remote.updatedAt = 100;
        remote.readWatermarkEpisode = 6;
        remote.lastNotifiedEpisode = 5;
        remote.nextCheckAt = 111;

        Following merged = FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0);
        assertEquals(7, merged.watchedEpisode);
        assertEquals(6, merged.readWatermarkEpisode);
        assertEquals(5, merged.lastNotifiedEpisode);
        assertEquals(999, merged.nextCheckAt);
    }

    @Test
    public void explicitDisableIntentWinsOnEitherDevice() {
        Following local = following();
        local.enabled = true;
        Following remote = following();
        remote.enabled = false;

        assertFalse(FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0).enabled);
    }

    @Test
    public void tombstoneSpreadsToActivePeerAndWins() {
        Following local = following();
        local.deletedAt = 500;
        local.enabled = false;
        Following remote = following();
        remote.updatedAt = 900;

        Following merged = FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0);
        assertTrue(merged.isDeleted());
        assertEquals(500, merged.deletedAt);
        assertFalse(merged.enabled);
    }

    @Test
    public void newerTombstoneWinsBetweenTwoTombstones() {
        Following local = following();
        local.deletedAt = 500;
        Following remote = following();
        remote.deletedAt = 800;

        assertEquals(800, FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0).deletedAt);
    }

    @Test
    public void reFollowAfterDeletionRevivesWhenCreatedAtIsNewer() {
        Following local = following();
        local.deletedAt = 500;
        Following remote = following();
        remote.createdAt = 700; // 重新追更发生在取消之后
        remote.updatedAt = 700;

        Following merged = FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0);
        assertFalse(merged.isDeleted());
        assertTrue(merged.enabled);
    }

    @Test
    public void deletionAfterReFollowReTombstonesTheRow() {
        Following local = following();
        local.createdAt = 700;
        Following remote = following();
        remote.deletedAt = 900; // 取消发生在重新追更之后

        Following merged = FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0);
        assertTrue(merged.isDeleted());
        assertEquals(900, merged.deletedAt);
    }

    @Test
    public void legacyActiveRowWithoutCreatedAtDoesNotReviveTombstone() {
        Following local = following();
        local.deletedAt = 500;
        Following remote = following();
        remote.createdAt = 0; // 旧版本快照的活跃行，createdAt 缺失

        Following merged = FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0);
        assertTrue(merged.isDeleted());
    }

    private static Following following() {
        Following item = new Following();
        item.identityKey = "tmdb:tv:1:s1";
        item.trackedSeason = 1;
        item.latestReleasedSeason = 1;
        item.latestReleasedEpisode = 8;
        item.seasonReleasedEpisodes = 8;
        return item;
    }
}
