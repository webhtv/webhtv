package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityOriginPlaybackBinding;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.playback.origin.OriginProgressWriter;
import com.fongmi.android.tv.playback.origin.OriginSessionClient;
import com.fongmi.android.tv.playback.origin.OriginSessionContract;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.custom.CustomSeekView;

import java.util.List;

public class OriginPlaybackActivity extends PlaybackActivity {
    public static final String EXTRA_CID = "originCid", EXTRA_KEY = "originHistoryKey", EXTRA_PAYLOAD = "originResumePayload";
    public static void start(Activity activity, History history) {
        if (activity == null || history == null) return;
        Intent intent = new Intent(activity, OriginPlaybackActivity.class)
                .putExtra(EXTRA_CID, history.getCid()).putExtra(EXTRA_KEY, history.getKey())
                .putExtra(EXTRA_PAYLOAD, HistoryResumePayload.encode(history));
        activity.startActivity(intent);
    }

    private ActivityOriginPlaybackBinding binding;
    private OriginSessionClient client;
    private History history;
    private Vod originalVod;
    private Flag selectedFlag;
    private Episode selectedEpisode;
    private boolean started, terminal, fallbackRequested, serviceReady;
    private int currentCid;

    private final PlaybackService.NavigationCallback navigation = new PlaybackService.NavigationCallback() {
        @Override public void onStop() { exit(); }
        @Override public void onNext() { next(); }
        @Override public void onPrev() { move(-1); }
        @Override public void onReplay() { if (selectedEpisode != null) request(selectedFlag, selectedEpisode); }
    };
    private final OriginSessionClient.Callback callback = new OriginSessionClient.Callback() {
        @Override public void onReady(String lease, Bundle envelope) {
            if (!alive()) return;
            client.play(history.getEpisodeUrl(), history.getVodRemarks(), String.valueOf(PlayerSetting.getActivePlayer()));
        }
        @Override public void onPlayReady(String lease, OriginSessionClient.Playback playback) { if (alive()) consume(playback); }
        @Override public void onUnavailable(String lease, String reason) {
            if (!alive() || started || !OriginSessionContract.shouldFallback(reason)) return;
            releaseLease();
            if (currentCid == com.fongmi.android.tv.api.config.VodConfig.getCid()) HistoryResumeCoordinator.fallbackToCurrent(OriginPlaybackActivity.this, history);
            fallbackRequested = true;
            setRedirect(true);
        }
        @Override public void onBlocked(String lease, String reason, String detail) {
            if (!alive()) return;
            binding.message.setText(reason + ": " + detail);
            releaseLease();
        }
    };

    @Override protected ViewBinding getBinding() { return binding = ActivityOriginPlaybackBinding.inflate(getLayoutInflater()); }
    @Override protected boolean customWall() { return false; }
    @Override protected void initView(Bundle saved) {
        int cid = getIntent().getIntExtra(EXTRA_CID, -1);
        String payload = getIntent().getStringExtra(EXTRA_PAYLOAD);
        if (payload == null) payload = getIntent().getStringExtra(EXTRA_KEY);
        history = HistoryResumePayload.restore(cid, payload);
        currentCid = com.fongmi.android.tv.api.config.VodConfig.getCid();
        // 基类 initView 负责绑定播放服务（shouldBindPlaybackService/defer 分支），
        // 必须先有 history 再交给它，否则服务连上时 openOrigin 还没东西可发。
        super.initView(saved);
        if (history == null) { binding.message.setText(R.string.history_record_missing); finish(); return; }
        binding.title.setText(history.getVodName());
        binding.message.setText(R.string.history_source_searching);
        binding.play.setOnClickListener(view -> { if (controller() != null) controller().play(); });
        binding.pause.setOnClickListener(view -> { if (controller() != null) { controller().pause(); writeProgress(); } });
        binding.next.setOnClickListener(view -> next());
        binding.lines.setOnClickListener(view -> chooseLine());
        binding.close.setOnClickListener(view -> exit());
        // 播放服务可能先于本页 initView 连上；那时还没有 history，openOrigin 会直接返回，
        // 所以拿到 history 之后要再触发一次，避免页面停在“正在搜索”而从不发送租约。
        if (serviceReady) openOrigin();
    }
    @Override protected void onServiceConnected() {
        serviceReady = true;
        openOrigin();
    }
    private void openOrigin() {
        if (history == null || !alive()) return;
        History selected = HistoryResumePayload.restore(history.getCid(), HistoryResumePayload.encode(history));
        if (selected == null) { binding.message.setText(R.string.history_record_missing); finish(); return; }
        history = selected;
        OriginSessionClient held = PlaybackService.originLease();
        if (held != null && !held.isClosed()) {
            client = held;
            client.setCallback(this, callback);
            if (held.matchesHistory(history)) {
                if (client.playback() != null) {
                    started = true;
                    restoreSelection(client.playback());
                    binding.message.setText(client.playback().siteName + " · " + client.playback().episode);
                }
                updateNavigationKey();
                return;
            }
            held.closeAfterDeath(() -> {
                if (!alive() || com.fongmi.android.tv.api.config.VodConfig.getCid() != currentCid) return;
                PlaybackService.releaseOriginLease();
                client = OriginSessionClient.open(this, history, callback);
            });
            return;
        }
        if (client == null) client = OriginSessionClient.open(this, history, callback);
    }
    private boolean alive() { return !terminal && !isFinishing() && !isDestroyed(); }
    private void restoreSelection(OriginSessionClient.Playback playback) {
        originalVod = Vod.objectFrom(playback.vod);
        selectedFlag = null;
        for (int i = 0; i < originalVod.getFlags().size(); i++) if (playback.selectedFlagKey.equals(Flag.stableKey(originalVod.getFlags().get(i), i))) selectedFlag = originalVod.getFlags().get(i);
        selectedEpisode = playback.selectedEpisode.isEmpty() ? null : App.gson().fromJson(playback.selectedEpisode, Episode.class);
    }
    private void consume(OriginSessionClient.Playback playback) {
        try {
            Result result = Result.fromJson(playback.result);
            if (result.needParse() || !(result.getRealUrl().startsWith("http://127.0.0.1:" + client.proxyPort() + "/origin/") || result.getRealUrl().startsWith("edl://file=%"))) throw new IllegalStateException("unmapped origin media");
            boolean first = !started;
            restoreSelection(playback);
            if (selectedEpisode != null) {
                history.setEpisodeUrl(selectedEpisode.getUrl()); history.setVodRemarks(selectedEpisode.getName());
                history.setVodFlag(playback.flag); history.setSourceBindingKey(playback.selectedFlagKey);
                history.setTmdbEpisodePosition(selectedEpisode);
                if (!first) { history.setPosition(0); history.setDuration(0); }
            }
            started = true;
            client.markPlaybackStarted();
            PlaybackService.holdOriginLease(client, history);
            binding.message.setText(playback.siteName + " · " + playback.episode);
            MediaMetadata metadata = new MediaMetadata.Builder().setTitle(history.getVodName() + " · " + playback.episode).build();
            long position = first && playback.positionMs > 0 ? playback.positionMs : first && history.getPosition() > 0 ? history.getPosition() : C.TIME_UNSET;
            startPlayer(getPlaybackKey(), result, false, Constant.TIMEOUT_PLAY, metadata, position);
            updateNavigationKey();
        } catch (Exception e) { onError(OriginSessionContract.PLAYER_FAILED + ": " + e); }
    }
    private void chooseLine() {
        if (originalVod == null) return;
        List<Flag> flags = originalVod.getFlags();
        String[] names = new String[flags.size()]; for (int i = 0; i < flags.size(); i++) names[i] = flags.get(i).getShow();
        new WebHtvAlertDialogBuilder(this).setItems(names, (dialog, index) -> {
            Flag flag = flags.get(index); Episode target = null;
            for (Episode episode : flag.getEpisodes()) if (selectedEpisode != null && (episode.getUrl().equals(selectedEpisode.getUrl()) || episode.matchesNumber(selectedEpisode))) { if (target != null) { onError("目标集不唯一"); return; } target = episode; }
            if (target == null) { onError("目标线路缺少当前集"); return; }
            request(flag, target);
        }).show();
    }
    private void next() { move(1); }
    private void move(int delta) {
        if (selectedFlag == null || selectedEpisode == null) return;
        int index = -1;
        for (int i = 0; i < selectedFlag.getEpisodes().size(); i++) if (selectedEpisode.getUrl().equals(selectedFlag.getEpisodes().get(i).getUrl())) { index = i; break; }
        int next = index + delta;
        if (index < 0 || next < 0 || next >= selectedFlag.getEpisodes().size()) return;
        request(selectedFlag, selectedFlag.getEpisodes().get(next));
    }
    private void request(Flag flag, Episode episode) {
        if (client == null || originalVod == null || !alive()) return;
        writeProgress();
        if (player() != null) player().pause();
        client.play(history.getSiteKey(), history.getVodId(), Flag.stableKey(flag, originalVod.getFlags().indexOf(flag)), episode.getUrl(), episode.getName(), PlayerSetting.getActivePlayer());
    }
    @Override protected PlaybackService.NavigationCallback getNavigationCallback() { return navigation; }
    @Override protected CustomSeekView getSeekView() { return binding.seek; }
    @Override protected PlayerView getExoView() { return binding.exo; }
    @Override protected String getPlaybackKey() { return client == null ? "origin" : client.playbackKey(); }
    @Override protected long getDurationFallbackMs() { return history == null ? C.TIME_UNSET : history.getDuration(); }
    @Override protected boolean shouldAutoPlay() { return true; }
    @Override protected void onBackInvoked() { exit(); }
    @Override protected void onError(String message) { binding.message.setText(message); android.util.Log.e("OriginPlayback", message); }
    @Override public void onRenderedFirstFrame() {
        super.onRenderedFirstFrame();
        android.util.Log.i("OriginPlayback", "FIRST_FRAME cid=" + history.getCid() + " episode=" + history.getVodRemarks() + " position=" + (player() == null ? -1 : player().getPlayer().getCurrentPosition()));
    }
    @Override protected void onPlayerPositionDiscontinuity(Player.PositionInfo previous, Player.PositionInfo next, int reason) { writeProgress(); }
    @Override protected void onPause() { writeProgress(); super.onPause(); }
    @Override protected void onStop() { super.onStop(); if (fallbackRequested) finish(); }
    @Override protected void onDestroy() {
        writeProgress();
        if (isChangingConfigurations() || !isFinishing() && started && !terminal) { if (client != null) client.setCallback(null, null); }
        else releaseLease();
        super.onDestroy();
    }
    private void writeProgress() {
        if (!started || history == null || player() == null || !isOwner()) return;
        Player active = player().getPlayer();
        OriginProgressWriter.write(history, active.getCurrentPosition(), active.getDuration());
        if (client != null) client.progressCommitted();
    }
    private void exit() {
        if (terminal) return;
        writeProgress(); terminal = true; markPlaybackExiting();
        stopPlayback(); releaseLease(); finish();
    }
    private void releaseLease() {
        if (client != null) {
            if (PlaybackService.originLease() == client) PlaybackService.releaseOriginLease();
            else client.close();
            client = null;
        }
    }
}
