package com.fongmi.android.tv.following;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.db.AppDatabase;

import java.util.List;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FollowingStore {

    private static final Object MIGRATION_LOCK = new Object();
    private static final ExecutorService PROJECTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "following-projector");
        thread.setDaemon(true);
        return thread;
    });

    private FollowingStore() {
    }

    public static FollowingDatabase database() {
        return FollowingDatabase.get();
    }

    public static List<Following> list() {
        return database().getFollowingDao().findActive();
    }

    public static List<FollowingSource> sources(String identityKey) {
        return database().getFollowingSourceDao().findForFollowing(identityKey);
    }

    public static FollowingSource preferredSource(String identityKey) {
        return database().getFollowingSourceDao().findPreferred(identityKey);
    }

    public static Following find(String identityKey) {
        return TextUtils.isEmpty(identityKey) ? null : database().getFollowingDao().findActive(identityKey);
    }

    /** 含墓碑行的原始查找：仅同步/合并代码使用。 */
    public static Following findAny(String identityKey) {
        return TextUtils.isEmpty(identityKey) ? null : database().getFollowingDao().find(identityKey);
    }

    public static Following findByTmdb(TmdbItem item, int season) {
        if (item == null || item.getTmdbId() <= 0) return null;
        return database().getFollowingDao().findByTmdb(item.getTmdbId(), FollowingIdentity.normalizeMediaType(item.getMediaType()), season);
    }

    public static Following findBySource(int cid, String siteKey, String vodId, int season) {
        return database().getFollowingDao().findBySource(cid, FollowingIdentity.normalize(siteKey), FollowingIdentity.normalize(vodId), season);
    }

    public static Following resolveTmdb(TmdbItem tmdb, int season, int cid, String siteKey, String vodId,
                                        FollowingMetadataSnapshot snapshot) {
        if (tmdb == null || tmdb.getTmdbId() <= 0 || !"tv".equals(FollowingIdentity.normalizeMediaType(tmdb.getMediaType()))) return null;
        String identityKey = FollowingIdentity.identityKey(tmdb, season);
        synchronized (MIGRATION_LOCK) {
            return runInTransaction(() -> {
                // 目标已是墓碑：身份迁移不得复活已取消的追更。
                Following target = database().getFollowingDao().find(identityKey);
                if (target != null && target.deletedAt > 0) return target;
                List<Following> sources = database().getFollowingDao().findUnmatchedBySource(
                        cid, FollowingIdentity.normalize(siteKey), FollowingIdentity.normalize(vodId));
                Following source = selectMigrationSource(sources, season);
                if (source == null) return target;
                return migrateSourceToTmdb(source, tmdb, season, snapshot);
            });
        }
    }

    private static Following selectMigrationSource(List<Following> sources, int season) {
        if (sources == null || sources.isEmpty()) return null;
        for (Following item : sources) if (item.trackedSeason == season) return item;
        for (Following item : sources) if (item.trackedSeason <= 0) return item;
        return sources.get(0);
    }

    private static Following migrateSourceToTmdb(Following source, TmdbItem tmdb, int season,
                                                 FollowingMetadataSnapshot snapshot) {
        long now = System.currentTimeMillis();
        String identityKey = FollowingIdentity.identityKey(tmdb, season);
        Following migrated = source.copy();
        boolean fallbackSeason = migrated.trackedSeason <= 0 && season > 0;
        migrated.identityKey = identityKey;
        migrated.seriesKey = FollowingIdentity.seriesKey(tmdb);
        migrated.mediaType = FollowingIdentity.normalizeMediaType(tmdb.getMediaType());
        migrated.tmdbId = tmdb.getTmdbId();
        migrated.trackedSeason = season;
        if (fallbackSeason) {
            if (migrated.watchedEpisode > 0) migrated.watchedSeason = season;
            if (migrated.lastNotifiedEpisode > 0 || migrated.readWatermarkEpisode > 0) migrated.watchedSeason = season;
        }

        Following target = database().getFollowingDao().find(identityKey);
        Following result = target == null ? migrated : FollowingMergePolicy.mergeOne(target, migrated);
        if (snapshot != null) FollowingUpdatePolicy.applyMetadata(result, snapshot, now);
        else FollowingUpdatePolicy.refreshDerived(result, now);
        result.nextCheckAt = FollowingSchedulePolicy.nextCheckAt(now, result.officialStatus, result.nextAirAt);
        database().getFollowingDao().insertOrUpdate(result);

        List<FollowingSource> bindings = database().getFollowingSourceDao().findForFollowing(source.identityKey);
        for (FollowingSource binding : bindings) {
            if (binding == null) continue;
            binding.followingKey = identityKey;
            if (binding.playableSeason <= 0) binding.playableSeason = season;
            database().getFollowingSourceDao().insertOrUpdate(binding);
        }
        database().getFollowingSourceDao().deleteForFollowing(source.identityKey);
        database().getFollowingDao().delete(source.identityKey);
        return result;
    }

    public static int unreadCount() {
        try {
            return database().getFollowingDao().unreadCount();
        } catch (Throwable e) {
            return 0;
        }
    }

    public static void saveNew(Following item, FollowingSource source) {
        if (item == null || TextUtils.isEmpty(item.identityKey)) throw new IllegalArgumentException("following identity is empty");
        runInTransaction(() -> {
            // 复活墓碑：取消后重新追更，清除墓碑并保留进度水线（与现有 updatedAt 进度合并语义一致）。
            Following existing = database().getFollowingDao().find(item.identityKey);
            if (existing != null && existing.deletedAt > 0) {
                long now = System.currentTimeMillis();
                Following revived = mergeRevived(existing, item);
                database().getFollowingDao().insertOrUpdate(revived);
                if (source != null) {
                    source.followingKey = item.identityKey;
                    database().getFollowingSourceDao().insertOrUpdate(source);
                }
                return null;
            }
            database().getFollowingDao().insertOrUpdate(item);
            if (source != null) {
                source.followingKey = item.identityKey;
                database().getFollowingSourceDao().insertOrUpdate(source);
            }
            return null;
        });
    }

    private static Following mergeRevived(Following tombstone, Following fresh) {
        Following result = FollowingMergePolicy.mergeOne(tombstone, fresh);
        result.deletedAt = 0;
        result.enabled = true;
        result.createdAt = fresh.createdAt > 0 ? fresh.createdAt : System.currentTimeMillis();
        return result;
    }

    public static void update(Following item) {
        if (item == null || TextUtils.isEmpty(item.identityKey)) return;
        database().getFollowingDao().insertOrUpdate(item);
    }

    public static void updateSource(FollowingSource source) {
        if (source == null || TextUtils.isEmpty(source.followingKey)) return;
        database().getFollowingSourceDao().insertOrUpdate(source);
    }

    public static void markRead(String identityKey) {
        Following item = find(identityKey);
        if (item == null) return;
        FollowingUpdatePolicy.markRead(item, System.currentTimeMillis());
        update(item);
    }

    public static void markReadAll(Collection<String> identityKeys) {
        if (identityKeys == null || identityKeys.isEmpty()) return;
        long now = System.currentTimeMillis();
        runInTransaction(() -> {
            for (String identityKey : identityKeys) {
                Following item = find(identityKey);
                if (item == null || !item.hasUpdate) continue;
                FollowingUpdatePolicy.markRead(item, now);
                update(item);
            }
            return null;
        });
    }

    public static void setNotifyEnabled(String identityKey, boolean enabled) {
        database().getFollowingDao().setNotifyEnabled(identityKey, enabled, System.currentTimeMillis());
    }

    public static void setEnabled(String identityKey, boolean enabled) {
        database().getFollowingDao().setEnabled(identityKey, enabled, System.currentTimeMillis());
    }

    public static void delete(String identityKey) {
        runInTransaction(() -> {
            database().getFollowingDao().markDeleted(identityKey, System.currentTimeMillis(), System.currentTimeMillis());
            return null;
        });
    }

    /** 物理删除墓碑行（仅保留窗口后清理或全量恢复使用）。 */
    public static void purge(String identityKey) {
        runInTransaction(() -> {
            database().getFollowingSourceDao().deleteForFollowing(identityKey);
            database().getFollowingDao().delete(identityKey);
            return null;
        });
    }

    public static void project(History history) {
        if (history == null || !FollowingSettings.isEnabled()) return;
        History snapshot = history.copy();
        PROJECTOR.execute(() -> {
            try {
                projectHistory(snapshot);
            } catch (Throwable ignored) {
                // Projection is best effort and must never affect playback history writes.
            }
        });
    }

    public static void reconcile(Following item) {
        if (item == null) return;
        if (item.tmdbId > 0) {
            List<History> histories = AppDatabase.get().getHistoryDao().findByTmdbIdentity(
                    item.cid, FollowingIdentity.normalizeMediaType(item.mediaType), item.tmdbId);
            History latest = null;
            for (History history : histories) {
                if (!seasonMatches(history.getTmdbSeasonNumber(), item.trackedSeason)) continue;
                if (latest == null || history.getCreateTime() > latest.getCreateTime()) latest = history;
            }
            if (latest != null) projectHistory(latest, item.trackedSeason);
        } else {
            History history = History.find(item.cid, sourceHistoryKey(item.siteKey, item.vodId));
            if (history != null) projectHistory(history);
        }
    }

    public static History historyFor(Following item, FollowingSource source) {
        if (item == null) return null;
        if (item.tmdbId > 0) {
            List<History> histories = AppDatabase.get().getHistoryDao().findByTmdbIdentity(
                    item.cid, FollowingIdentity.normalizeMediaType(item.mediaType), item.tmdbId);
            History preferred = null;
            History anySource = null;
            for (History history : histories) {
                if (!seasonMatches(history.getTmdbSeasonNumber(), item.trackedSeason)) continue;
                if (anySource == null || history.getCreateTime() > anySource.getCreateTime()) anySource = history;
                if (source == null || TextUtils.equals(history.getSiteKey(), source.siteKey)) {
                    if (preferred == null || history.getCreateTime() > preferred.getCreateTime()) preferred = history;
                }
            }
            if (preferred != null) return preferred;
            if (anySource != null) return anySource;
        }
        if (source != null) return History.find(item.cid, sourceHistoryKey(source.siteKey, source.vodId));
        return History.find(item.cid, sourceHistoryKey(item.siteKey, item.vodId));
    }

    public static <T> T runInTransaction(Callable<T> action) {
        return database().runInTransaction(action);
    }

    public static void replaceAll(List<Following> following, List<FollowingSource> sources) {
        runInTransaction(() -> {
            database().getFollowingSourceDao().deleteAll();
            database().getFollowingDao().deleteAll();
            if (following != null && !following.isEmpty()) database().getFollowingDao().insertOrUpdate(following);
            if (sources != null && !sources.isEmpty()) database().getFollowingSourceDao().insertOrUpdate(sources);
            return null;
        });
    }

    public static void mergeAll(List<Following> remoteFollowing, List<FollowingSource> remoteSources) {
        runInTransaction(() -> {
            // 本地墓碑必须参与合并（否则收到不含该行的旧快照会复活它）；同时保留对端墓碑。
            List<Following> merged = FollowingMergePolicy.mergeFollowing(
                    database().getFollowingDao().findAll(), remoteFollowing);
            List<FollowingSource> sources = FollowingMergePolicy.mergeSources(
                    database().getFollowingSourceDao().findAll(), remoteSources);
            database().getFollowingSourceDao().deleteAll();
            database().getFollowingDao().deleteAll();
            if (!merged.isEmpty()) database().getFollowingDao().insertOrUpdate(merged);
            if (!sources.isEmpty()) database().getFollowingSourceDao().insertOrUpdate(sources);
            return null;
        });
    }

    /** 未知季（-1）不能归零后去匹配，否则会漏掉 trackedSeason>=1 的追更记录，必须回落到记录自身的季号。 */
    static int resolveSeason(int historySeason, int trackedSeason) {
        return historySeason >= 0 ? historySeason : Math.max(0, trackedSeason);
    }

    /** 未知季（-1）无法判定归属，对任何追踪季都算候选，否则这些历史会被季号比较直接过滤掉，进度永久停更。 */
    static boolean seasonMatches(int historySeason, int trackedSeason) {
        return historySeason < 0 || historySeason == trackedSeason;
    }

    private static void projectHistory(History history) {
        projectHistory(history, -1);
    }

    private static void projectHistory(History history, int trackedSeasonHint) {
        if (history == null) return;
        int episodeNumber = history.getTmdbEpisodeNumber();
        if (episodeNumber <= 0) episodeNumber = Episode.create(history.getVodRemarks(), history.getEpisodeUrl()).getNumber();
        if (episodeNumber <= 0) return;
        int season = resolveSeason(history.getTmdbSeasonNumber(), trackedSeasonHint);
        Following item = null;
        if (history.getTmdbId() > 0 && "tv".equals(FollowingIdentity.normalizeMediaType(history.getMediaType()))) {
            item = database().getFollowingDao().findByTmdb(
                    history.getTmdbId(), FollowingIdentity.normalizeMediaType(history.getMediaType()), season);
        }
        if (item == null) {
            item = database().getFollowingDao().findBySource(
                    history.getCid(), FollowingIdentity.normalize(history.getSiteKey()),
                    FollowingIdentity.normalize(history.getVodId()), season);
        }
        if (item == null) return;
        if (item.isDeleted()) return;
        boolean changed = episodeNumber > item.watchedEpisode;
        if (episodeNumber == item.watchedEpisode) {
            changed = history.getPosition() != item.position || history.getDuration() != item.duration;
        }
        if (!changed) return;
        item.watchedSeason = season;
        item.watchedEpisode = episodeNumber;
        item.position = Math.max(0, history.getPosition());
        item.duration = Math.max(0, history.getDuration());
        item.trackedSeason = season;
        if (item.trackedEpisode <= 0) item.trackedEpisode = item.watchedEpisode;
        FollowingUpdatePolicy.refreshDerived(item, System.currentTimeMillis());
        database().getFollowingDao().insertOrUpdate(item);
    }

    public static String sourceHistoryKey(String siteKey, String vodId) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return "";
        return siteKey + AppDatabase.SYMBOL + vodId;
    }
}
