package com.fongmi.android.tv.following;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.following.FollowingSourceProbe.FollowingSourceSnapshot;

import java.io.IOException;
import java.util.List;

public class FollowingUpdateCoordinator {

    /** TMDB 降级文案：设备层断言逐字锁定，同时它是「来源没问题」的唯一证据，故必须可复用于判据。 */
    static final String TMDB_DEGRADED = "TMDB不可用，已回退原站";

    /** 来源检查失败文案：对外逐字可见且测试已逐字锁定，因此集中一处供复用，取值不得改动。 */
    static final String SOURCE_FAILED = "来源检查失败";

    private final FollowingMetadataClient metadataClient;
    private final FollowingSourceProbe sourceProbe;

    public FollowingUpdateCoordinator() {
        this(new FollowingMetadataClient(), new FollowingSourceProbe());
    }

    FollowingUpdateCoordinator(FollowingMetadataClient metadataClient, FollowingSourceProbe sourceProbe) {
        this.metadataClient = metadataClient;
        this.sourceProbe = sourceProbe;
    }

    public int checkDue(long now) throws IOException {
        if (!FollowingSettings.isEnabled()) return 0;
        boolean foreground = App.isForeground();
        List<Following> due = FollowingStore.database().getFollowingDao().findDue(now);
        int checked = 0;
        for (Following item : due) {
            if (check(item, false, foreground)) checked++;
        }
        return checked;
    }

    public boolean checkNow(String identityKey, boolean manual) {
        Following item = FollowingStore.find(identityKey);
        return item != null && check(item, manual);
    }

    boolean check(Following item, boolean manual) {
        return check(item, manual, App.isForeground());
    }

    boolean check(Following item, boolean manual, boolean foreground) {
        long now = System.currentTimeMillis();
        try {
            FollowingSource source = FollowingStore.preferredSource(item.identityKey);
            FollowingMetadataSnapshot metadata = null;
            boolean metadataChanged = false;
            boolean sourceAlreadyProbed = false;
            boolean probeFailed = false;
            Throwable metadataError = null;
            if (item.tmdbId > 0) {
                try {
                    metadata = metadataClient.fetch(item, manual);
                } catch (Throwable error) {
                    // TMDB is the preferred official provider, but an unavailable/rate-limited
                    // provider must not make the whole record fail when the bound source is
                    // still usable. Preserve the last official snapshot and probe the source.
                    metadataError = error;
                }
            }
            if (metadata != null) {
                metadataChanged = FollowingUpdatePolicy.applyMetadata(item, metadata, now);
            } else {
                ProbeOutcome initial = probeBestEffort(item, source, manual, false);
                FollowingSourceSnapshot sourceSnapshot = initial.snapshot;
                sourceAlreadyProbed = sourceSnapshot != null;
                if (sourceSnapshot == null || sourceSnapshot.source == null || sourceSnapshot.playableEpisode <= 0) {
                    Throwable failure = metadataError != null
                            ? metadataError : new IllegalStateException(SOURCE_FAILED);
                    FollowingUpdatePolicy.markFailure(item, failure, now);
                    FollowingStore.update(item);
                    // 顺序不可颠倒：先让 markFailure 的 lastError/failureCount/退避时间落库，再由 reconcile 从历史重建进度。
                    // 反过来 update(item) 会用内存里旧的 watchedEpisode 覆盖掉刚重建的结果。
                    // 失败记录同样需要重建进度，否则「探针探不到集」的源进度会永久停更（见 FOLLOW-1 设计文档 610 行）。
                    FollowingStore.reconcile(item);
                    FollowingScheduler.scheduleNext(App.get(), item);
                    return false;
                }
                source = sourceSnapshot.source;
            }
            FollowingStore.reconcile(item);
            FollowingUpdatePolicy.refreshDerived(item, now);
            if (shouldProbeAgain(sourceAlreadyProbed, FollowingSettings.shouldProbe(manual, metadataChanged, source))) {
                ProbeOutcome probed = probeBestEffort(item, source, manual, false);
                if (probed.snapshot != null) source = probed.snapshot.source;
                // 探针确实跑过却拿不到可用来源才算失败：既包含没跑出快照，也包含跑出快照但零可播集数
                //（线路解析不出集号），仅凭 snapshot==null 会把后者的静默零值错判成检查成功。
                probeFailed = probed.attempted && probeYieldedNoPlayable(probed.snapshot);
            }
            item.lastCheckedAt = now;
            item.failureCount = 0;
            item.lastError = composeLastError(metadataError, probeFailed, source);
            item.nextCheckAt = FollowingSchedulePolicy.nextCheckAt(now, item.officialStatus, item.nextAirAt, foreground);
            item.updatedAt = now;
            FollowingStore.update(item);
            if (source != null) FollowingStore.updateSource(source);
            if (FollowingNotifier.notifyUpdate(item, source)) FollowingStore.update(item);
            FollowingScheduler.scheduleNext(App.get(), item);
            return true;
        } catch (Throwable error) {
            FollowingUpdatePolicy.markFailure(item, error, now);
            FollowingStore.update(item);
            FollowingScheduler.scheduleNext(App.get(), item);
            return false;
        }
    }

    /** 探针结果 + 是否真的调用过探针：只有 attempted && snapshot==null 才是「检查过但失败」。 */
    private static final class ProbeOutcome {
        static final ProbeOutcome NOT_RUN = new ProbeOutcome(false, null);

        final boolean attempted;
        final FollowingSourceSnapshot snapshot;

        ProbeOutcome(boolean attempted, FollowingSourceSnapshot snapshot) {
            this.attempted = attempted;
            this.snapshot = snapshot;
        }
    }

    /**
     * item.lastError 合成优先级：TMDB 降级文案（既有断言逐字锁定）> 源侧检查失败原因 > 空。
     * 源侧失败必须落库原因，否则 UI 只能显示「尚未检查」，用户看不到失败。
     */
    static String composeLastError(Throwable metadataError, boolean probeFailed, FollowingSource source) {
        if (metadataError != null) return TMDB_DEGRADED;
        return probeFailed ? probeFailureReason(source) : "";
    }

    /** 本次 check 内探针至多跑一次：第一次已经跑出快照时不再重复探测同一来源。 */
    static boolean shouldProbeAgain(boolean sourceAlreadyProbed, boolean due) {
        return !sourceAlreadyProbed && due;
    }

    /** 探针没跑出快照、或跑出快照但没有任何可播集数，都等于拿不到可用来源。 */
    static boolean probeYieldedNoPlayable(FollowingSourceSnapshot snapshot) {
        return snapshot == null || snapshot.playableEpisode <= 0;
    }

    /**
     * 筛选与卡片共用的失败判据：TMDB 降级只说明元数据缺失，来源本身仍可用，不算来源检查失败。
     * UI 在 com.fongmi.android.tv.ui.* 包内，故必须 public 才能与筛选同口径。
     */
    public static boolean sourceCheckFailed(String lastError) {
        return lastError != null && !lastError.isBlank() && !TMDB_DEGRADED.equals(lastError);
    }

    static String probeFailureReason(FollowingSource source) {
        if (source == null || source.lastError == null || source.lastError.isBlank()) return SOURCE_FAILED;
        return source.lastError;
    }

    private ProbeOutcome probeBestEffort(Following item, FollowingSource source, boolean manual, boolean required) {
        if (source == null) source = fallbackSource(item);
        if (source == null || !FollowingSettings.shouldProbe(manual, false, source)) return ProbeOutcome.NOT_RUN;
        try {
            FollowingSourceSnapshot result = sourceProbe.probe(item, source);
            FollowingStore.updateSource(source);
            return new ProbeOutcome(true, result);
        } catch (Throwable error) {
            source.lastError = error.getMessage() == null ? SOURCE_FAILED : error.getMessage();
            source.lastProbeAt = System.currentTimeMillis();
            FollowingStore.updateSource(source);
            if (required) throw error instanceof RuntimeException ? (RuntimeException) error : new RuntimeException(error);
            return new ProbeOutcome(true, null);
        }
    }

    private static FollowingSource fallbackSource(Following item) {
        if (item == null || item.siteKey == null || item.siteKey.isBlank() || item.vodId == null || item.vodId.isBlank()) return null;
        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.cid = item.cid;
        source.siteKey = item.siteKey;
        source.vodId = item.vodId;
        source.vodName = item.vodName;
        source.vodPic = item.vodPic;
        source.preferred = true;
        return source;
    }
}
