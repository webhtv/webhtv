package com.fongmi.android.tv.playback.origin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.util.HashMap;
import java.util.Map;

/**
 * 跨配置历史的原源会话内核：正常播放页只负责显示，详情／线路／集数／取流全部来自原配置 worker。
 *
 * <p>身份固定为原历史的 cid/key：这里只缓存 worker 返回的原源 Vod 与已解析 Result，
 * 进度写回落回原记录（{@link OriginProgressWriter}），不碰当前配置的历史与站点。
 */
public final class OriginHistoryPlayback {

    public static final String EXTRA_CID = "origin_history_cid";
    public static final String EXTRA_KEY = "origin_history_key";
    public static final String EXTRA_PAYLOAD = "origin_history_payload";

    public interface Listener {
        void onOriginDetail(Vod vod);

        void onOriginResult(Result result);

        void onOriginFallback(History history, String reason);

        void onOriginError(String message);
    }

    private final History history;
    private final Listener listener;
    private final Map<String, Result> resolved = new HashMap<>();
    /** 缓存里 Result 所属的 relay 端口：端口变了（换租约）就必须丢弃缓存，否则回环地址已注销。 */
    private int resolvedPort;
    private OriginSessionClient client;
    private Vod vod;
    private String siteName = "";
    private String pendingEpisodeUrl = "";
    private boolean detailDelivered, resultDelivered, released;
    private android.content.Context ownerContext;
    private History targetHistory;
    private int attempt;
    /** 旧租约退出与新租约建立之间需要一个短暂窗口：worker 拒绝对已持有租约再开。 */
    private static final int MAX_ATTEMPTS = 4;

    private final OriginSessionClient.Callback callback = new OriginSessionClient.Callback() {
        @Override public void onReady(String lease, Bundle envelope) {
            if (released) return;
            client.play(history.getEpisodeUrl(), history.getVodRemarks(), String.valueOf(PlayerSetting.getActivePlayer()));
        }

        @Override public void onPlayReady(String lease, OriginSessionClient.Playback playback) {
            if (released) return;
            consume(playback);
        }

        @Override public void onUnavailable(String lease, String reason) {
            handleFailure(OriginSessionContract.UNAVAILABLE, reason, "");
        }

        @Override public void onBlocked(String lease, String reason, String detail) {
            handleFailure(OriginSessionContract.BLOCKED, reason, detail);
        }
    };

    private OriginHistoryPlayback(History history, Listener listener) {
        this.history = history;
        this.listener = listener;
    }

    public static void putExtras(Intent intent, History history) {
        if (intent == null || history == null) return;
        intent.putExtra(EXTRA_CID, history.getCid());
        intent.putExtra(EXTRA_KEY, history.getKey());
        intent.putExtra(EXTRA_PAYLOAD, HistoryResumePayload.encode(history));
    }

    public static boolean hasPayload(Intent intent) {
        return intent != null && !TextUtils.isEmpty(intent.getStringExtra(EXTRA_PAYLOAD));
    }

    public static History restore(Intent intent) {
        if (!hasPayload(intent)) return null;
        return HistoryResumePayload.restore(intent.getIntExtra(EXTRA_CID, -1), intent.getStringExtra(EXTRA_PAYLOAD));
    }

    public static OriginHistoryPlayback open(Context owner, Intent intent, Listener listener) {
        History restored = restore(intent);
        if (restored == null) return null;
        OriginHistoryPlayback entry = new OriginHistoryPlayback(restored.copy(), listener);
        OriginSessionClient held = PlaybackService.originLease();
        if (held != null && !held.isClosed() && held.matchesHistory(restored)) {
            entry.client = held;
            held.setCallback(owner, entry.callback);
            entry.resultDelivered = true;
            if (held.playback() != null) com.fongmi.android.tv.App.post(() -> entry.consume(held.playback()));
            return entry;
        }
        if (held != null && !held.isClosed()) {
            // 旧租约属于另一份原历史：worker 一次只允许一个租约，直接开新的会被拒绝
            // （表现为详情页拿不到原源线路）。释放旧租约后按有限次数重试，避免依赖一次性回调。
            held.setCallback(owner, null);
            PlaybackService.releaseOriginLease();
        }
        entry.ownerContext = owner;
        entry.targetHistory = restored.copy();
        entry.connect(owner, restored);
        return entry;
    }

    private void connect(Context owner, History target) {
        if (released) return;
        client = OriginSessionClient.open(owner, target, callback);
    }

    public boolean isActive() {
        return !released && client != null && !client.isClosed();
    }

    public String historyKey() {
        return history.getKey();
    }

    public History history() {
        return history.copy();
    }

    public Vod vod() {
        return vod;
    }

    public Site site() {
        String name = TextUtils.isEmpty(siteName) ? history.getSiteName() : siteName;
        return Site.get(history.getSiteKey(), name);
    }

    public String playbackKey() {
        return client != null && !client.isClosed() ? client.playbackKey() : "origin:" + history.getKey();
    }

    public void requestDetail() {
        if (vod != null) listener.onOriginDetail(vod);
    }

    /**
     * 用户点了一集：先看 worker 是否已经解析过同一集（进入页面时那次原源播放），否则重新向原配置取流。
     */
    public boolean requestPlay(Flag flag, Episode episode) {
        if (episode == null || !isActive()) return false;
        Result cached = resolved.get(episode.getUrl());
        if (cached != null && resolvedPort == client.proxyPort() && cached.getRealUrl().contains(":" + resolvedPort + "/")) {
            deliverResult(cached);
            return true;
        }
        if (cached != null) {
            // 端口已换：旧 Result 指向的回环地址不再登记，重取而不是拿旧地址去播。
            resolved.clear();
            resolvedPort = 0;
        }
        String stableFlagKey = flag == null ? "" : Flag.stableKey(flag, vod == null ? 0 : vod.getFlags().indexOf(flag));
        pendingEpisodeUrl = episode.getUrl();
        client.play(history.getSiteKey(), history.getVodId(), stableFlagKey, episode.getUrl(), episode.getName(), PlayerSetting.getActivePlayer());
        return true;
    }

    public void writeProgress(History target, long positionMs, long durationMs) {
        if (target == null || positionMs < 0) return;
        OriginProgressWriter.write(target, positionMs, durationMs);
        if (client != null && !client.isClosed()) client.progressCommitted();
    }

    public void release() {
        if (released) return;
        released = true;
        OriginSessionClient value = client;
        client = null;
        resolved.clear();
        if (value == null) return;
        if (PlaybackService.originLease() == value) PlaybackService.releaseOriginLease();
        else value.close();
    }

    private void consume(OriginSessionClient.Playback playback) {
        if (released) return;
        try {
            Result result = Result.fromJson(playback.result);
            if (result.hasMsg() || result.needParse() || TextUtils.isEmpty(result.getRealUrl())) {
                handleFailure(OriginSessionContract.BLOCKED, OriginSessionContract.PLAYER_FAILED,
                        result.hasMsg() ? result.getMsg() : "原源取流结果不可直接播放");
                return;
            }
            apply(playback);
            if (vod == null || vod.getFlags().isEmpty()) {
                handleFailure(OriginSessionContract.BLOCKED, OriginSessionContract.SNAPSHOT_INVALID, "原源详情或选集数据无效");
                return;
            }
            resolvedPort = client == null ? 0 : client.proxyPort();
            resolved.put(resolvedEpisodeUrl(playback), result);
            if (!detailDelivered) {
                detailDelivered = true;
                listener.onOriginDetail(vod);
            }
            if (!pendingEpisodeUrl.isEmpty() && pendingEpisodeUrl.equals(resolvedEpisodeUrl(playback))) {
                pendingEpisodeUrl = "";
                deliverResult(result);
            }
        } catch (RuntimeException failure) {
            handleFailure(OriginSessionContract.BLOCKED, OriginSessionContract.SNAPSHOT_INVALID, failure.toString());
        }
    }

    private void deliverResult(Result result) {
        if (!isActive()) return;
        resultDelivered = true;
        client.markPlaybackStarted();
        PlaybackService.holdOriginLease(client, history.copy());
        listener.onOriginResult(result);
    }

    private void handleFailure(String status, String reason, String detail) {
        if (released) return;
        // 只有"旧租约还占着 worker"才值得重试：那是一个几十毫秒级的时序窗口。
        // 真正的隔离不足/代理异常必须立刻走用户可见的失败处理，绝不能被重试延迟成"点了没反应"。
        if (!resultDelivered && leaseBusy(detail) && attempt < MAX_ATTEMPTS) {
            attempt++;
            OriginSessionClient previous = client;
            client = null;
            if (previous != null) previous.close();
            com.fongmi.android.tv.App.post(() -> {
                if (!released) connect(ownerContext, targetHistory);
            }, 400L * attempt);
            return;
        }
        boolean fallback = OriginSessionContract.shouldResumeGlobally(resultDelivered, status, reason);
        String message = (TextUtils.isEmpty(reason) ? OriginSessionContract.ISOLATION_ERROR : reason)
                + (TextUtils.isEmpty(detail) ? "" : ": " + detail);
        release();
        if (fallback) listener.onOriginFallback(history.copy(), message);
        else listener.onOriginError(message);
    }

    /** worker 一次只允许一个租约，被拒时会带这两句 detail；其它 detail 都按不可重试的失败处理。 */
    private static boolean leaseBusy(String detail) {
        return detail != null && (detail.contains("worker lease already held") || detail.contains("old worker still alive"));
    }

    private void apply(OriginSessionClient.Playback playback) {
        Vod parsed = Vod.objectFrom(playback.vod);
        if (parsed == null || parsed.getFlags().isEmpty()) return;
        vod = parsed;
        siteName = playback.siteName;
        Episode selected = TextUtils.isEmpty(playback.selectedEpisode) ? null : com.fongmi.android.tv.App.gson().fromJson(playback.selectedEpisode, Episode.class);
        history.setVodFlag(playback.flag);
        history.setSourceBindingKey(playback.selectedFlagKey);
        if (selected != null) {
            history.setEpisodeUrl(selected.getUrl());
            history.setVodRemarks(selected.getName());
            history.setTmdbEpisodePosition(selected);
        }
        if (playback.positionMs > 0) history.setPosition(playback.positionMs);
        if (playback.durationMs > 0) history.setDuration(playback.durationMs);

    }

    private String resolvedEpisodeUrl(OriginSessionClient.Playback playback) {
        if (!TextUtils.isEmpty(playback.selectedEpisode)) {
            Episode selected = com.fongmi.android.tv.App.gson().fromJson(playback.selectedEpisode, Episode.class);
            if (selected != null && !TextUtils.isEmpty(selected.getUrl())) return selected.getUrl();
        }
        return history.getEpisodeUrl();
    }
}
