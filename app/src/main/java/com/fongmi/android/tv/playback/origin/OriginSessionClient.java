package com.fongmi.android.tv.playback.origin;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.player.PlaybackRouteRegistry;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.JsonObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OriginSessionClient implements AutoCloseable {
    public interface Callback {
        void onReady(String leaseId, Bundle envelope);
        void onPlayReady(String leaseId, Playback playback);
        void onUnavailable(String leaseId, String reason);
        void onBlocked(String leaseId, String reason, String detail);
    }

    public static final class Playback {
        public final String siteName, flag, episode, vod, flags, result, selectedFlagKey, selectedEpisode;
        public final long positionMs, durationMs;
        Playback(JsonObject json) {
            siteName = string(json, "site");
            flag = string(json, "flag");
            episode = string(json, "episode");
            vod = string(json, "vod");
            flags = string(json, "flags");
            result = string(json, "result");
            selectedFlagKey = string(json, "selectedFlagKey");
            selectedEpisode = string(json, "selectedEpisode");
            positionMs = json.has("resumePositionMs") ? json.get("resumePositionMs").getAsLong() : 0;
            durationMs = json.has("resumeDurationMs") ? json.get("resumeDurationMs").getAsLong() : 0;
        }
        private static String string(JsonObject json, String key) {
            if (!json.has(key) || json.get(key).isJsonNull()) return "";
            return json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : App.gson().toJson(json.get(key));
        }
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final IBinder owner = new Binder();
    private final String leaseId = UUID.randomUUID().toString();
    private final long generation = System.nanoTime();
    private final History snapshotHistory;
    private final int originCid, currentCidAtClick;
    private final String historyKey, resumePayload, configRevision;
    private final List<PlaybackRouteRegistry.Registration> routes = new ArrayList<>();
    private volatile Callback callback;
    private volatile WeakReference<Activity> activity;
    private volatile String expectedResumeVersion;
    private volatile OriginSessionContract.OriginSessionPort port;
    private volatile IBinder serviceBinder;
    private volatile boolean bound, playbackStarted;
    private volatile int proxyPort;
    private volatile Playback playback;

    private final IBinder.DeathRecipient death = () -> handler.post(() -> fail(OriginSessionContract.WORKER_DIED, "worker binder died"));
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed.get()) return;
            serviceBinder = binder;
            port = OriginSessionContract.OriginSessionPort.Stub.asInterface(binder);
            try { binder.linkToDeath(death, 0); }
            catch (Exception e) { fail(OriginSessionContract.WORKER_DIED, "worker already dead"); return; }
            Task.execute(() -> {
                ParcelFileDescriptor fd = null;
                try {
                    if (closed.get() || !current()) { close(); return; }
                    OriginSessionSnapshot snapshot = OriginSessionSnapshot.capture(snapshotHistory);
                    fd = snapshot.descriptor(new java.io.File(context.getCacheDir(), "origin-ipc"));
                    port.open(request(), fd, owner, reply());
                } catch (Exception e) {
                    handler.post(() -> fail(OriginSessionContract.ISOLATION_ERROR, String.valueOf(e.getMessage())));
                } finally { OriginSessionContract.closeFd(fd); }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { fail(OriginSessionContract.WORKER_DIED, "worker disconnected"); }
        @Override public void onBindingDied(ComponentName name) { fail(OriginSessionContract.WORKER_DIED, "binding died"); }
        @Override public void onNullBinding(ComponentName name) { fail(OriginSessionContract.ISOLATION_ERROR, "null binding"); }
    };

    private OriginSessionClient(Context context, History history, Callback callback) {
        this.context = context.getApplicationContext();
        snapshotHistory = history.copy();
        originCid = history.getCid();
        historyKey = history.getKey();
        resumePayload = HistoryResumePayload.encode(history);
        expectedResumeVersion = OriginSessionSnapshot.resumeVersion(history);
        currentCidAtClick = VodConfig.getCid();
        configRevision = OriginSessionSnapshot.revision(Config.find(originCid));
        setCallback(context, callback);
    }

    public static OriginSessionClient open(Context context, History history, Callback callback) {
        OriginSessionClient client = new OriginSessionClient(context, history, callback);
        try {
            client.bound = client.context.bindService(new Intent(client.context, OriginSessionService.class), client.connection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
            if (!client.bound) client.handler.post(() -> client.fail(OriginSessionContract.ISOLATION_ERROR, "bind refused"));
        } catch (Exception e) { client.handler.post(() -> client.fail(OriginSessionContract.ISOLATION_ERROR, e.toString())); }
        return client;
    }

    public void setCallback(Context ownerContext, Callback value) {
        callback = value;
        activity = ownerContext instanceof Activity ? new WeakReference<>((Activity) ownerContext) : null;
    }
    public Playback playback() { return playback; }
    public void markPlaybackStarted() { playbackStarted = true; }
    public void progressCommitted() { if (!closed.get()) expectedResumeVersion = OriginSessionSnapshot.resumeVersion(HistoryResumePayload.restore(originCid, resumePayload)); }
    public String leaseId() { return leaseId; }
    public int proxyPort() { return proxyPort; }
    public History history() { return snapshotHistory.copy(); }
    public String playbackKey() { return "origin:" + leaseId + ":" + snapshotHistory.getKey(); }
    public boolean matchesHistory(History selected) {
        return selected != null && selected.getCid() == originCid
                && java.util.Objects.equals(selected.getKey(), snapshotHistory.getKey())
                && resumePayload.equals(HistoryResumePayload.encode(selected))
                && currentCidAtClick == com.fongmi.android.tv.api.config.VodConfig.getCid()
                && configRevision.equals(OriginSessionSnapshot.revision(com.fongmi.android.tv.bean.Config.find(originCid)))
                && expectedResumeVersion.equals(OriginSessionSnapshot.resumeVersion(selected));
    }
    public boolean isClosed() { return closed.get(); }

    private Bundle request() {
        Bundle request = new Bundle();
        request.putInt("version", OriginSessionContract.VERSION);
        request.putString("leaseId", leaseId);
        request.putLong("generation", generation);
        request.putInt("originCid", originCid);
        request.putString("sourceHistoryKey", historyKey);
        request.putString("resumePayload", resumePayload);
        request.putString("expectedResumeVersion", expectedResumeVersion);
        request.putInt("currentCidAtClick", currentCidAtClick);
        request.putString("configRevision", configRevision);
        return request;
    }

    public void play(String episodeUrl, String episodeName, String playerType) {
        int type;
        try { type = Integer.parseInt(playerType); } catch (Exception e) { type = 0; }
        play(snapshotHistory.getSiteKey(), snapshotHistory.getVodId(), snapshotHistory.getSourceBindingKey(), episodeUrl, episodeName, type);
    }
    public void play(String siteKey, String vodId, String stableFlagKey, String episodeUrl, String episodeName, int playerType) {
        if (closed.get()) return;
        if (playbackStarted) expectedResumeVersion = OriginSessionSnapshot.resumeVersion(HistoryResumePayload.restore(originCid, resumePayload));
        if (!current()) { close(); return; }
        Bundle request = request();
        request.putString("siteKey", siteKey);
        request.putString("vodId", vodId);
        request.putString("stableFlagKey", stableFlagKey);
        request.putString("episodeUrl", episodeUrl);
        request.putString("episodeName", episodeName);
        request.putInt("playerType", playerType);
        Task.execute(() -> {
            try {
                OriginSessionContract.OriginSessionPort value = port;
                if (value == null) throw new IllegalStateException("no worker port");
                value.play(request, reply());
            } catch (Exception e) { handler.post(() -> fail(OriginSessionContract.WORKER_DIED, e.toString())); }
        });
    }

    private OriginSessionContract.OriginReply reply() {
        return new OriginSessionContract.OriginReply.Stub() {
            @Override public void complete(Bundle envelope, ParcelFileDescriptor fd) {
                if (closed.get() || !envelopeMatches(envelope)) { OriginSessionContract.closeFd(fd); return; }
                Task.execute(() -> {
                    String json = null;
                    try { if (fd != null) json = OriginSessionSnapshot.readJson(fd); }
                    catch (Exception e) { handler.post(() -> fail(OriginSessionContract.SNAPSHOT_INVALID, e.toString())); return; }
                    finally { OriginSessionContract.closeFd(fd); }
                    dispatch(envelope, json);
                });
            }
        };
    }

    private boolean envelopeMatches(Bundle envelope) {
        try { OriginSessionContract.validate(envelope); }
        catch (Exception e) { return false; }
        return leaseId.equals(envelope.getString("leaseId")) && generation == envelope.getLong("generation")
                && originCid == envelope.getInt("originCid", -1) && historyKey.equals(envelope.getString("sourceHistoryKey"))
                && configRevision.equals(envelope.getString("configRevision"));
    }

    private boolean current() {
        return currentCidAtClick == VodConfig.getCid()
                && configRevision.equals(OriginSessionSnapshot.revision(Config.find(originCid)))
                && expectedResumeVersion.equals(OriginSessionSnapshot.resumeVersion(HistoryResumePayload.restore(originCid, resumePayload)));
    }
    private boolean ownerAlive() {
        WeakReference<Activity> reference = activity;
        Activity value = reference == null ? null : reference.get();
        return reference == null || value != null && !value.isFinishing() && !value.isDestroyed();
    }

    private void dispatch(Bundle envelope, String json) {
        handler.post(() -> {
            if (closed.get()) return;
            if (!current() || !ownerAlive()) { close(); return; }
            Callback value = callback;
            if (value == null) return;
            String status = envelope.getString("status", "");
            String reason = envelope.getString("reason", "");
            if (OriginSessionContract.CANCELLED.equals(status) || OriginSessionContract.STALE.equals(status)) { close(); return; }
            if (OriginSessionContract.READY.equals(status)) {
                proxyPort = envelope.getInt("proxyPort", 0);
                if (proxyPort <= 0) { fail(OriginSessionContract.ISOLATION_ERROR, "no relay port"); return; }
                synchronized (routes) { routes.add(PlaybackRouteRegistry.registerOriginSession(proxyPort)); }
                value.onReady(leaseId, envelope);
            } else if (OriginSessionContract.PLAY_READY.equals(status)) {
                try { playback = new Playback(App.gson().fromJson(json, JsonObject.class)); }
                catch (Exception e) { fail(OriginSessionContract.SNAPSHOT_INVALID, e.toString()); return; }
                value.onPlayReady(leaseId, playback);
            } else if (!playbackStarted && OriginSessionContract.UNAVAILABLE.equals(status) && OriginSessionContract.shouldFallback(reason)) {
                close();
                value.onUnavailable(leaseId, reason);
            } else { fail(reason.isEmpty() ? OriginSessionContract.ISOLATION_ERROR : reason, envelope.getString("detail", "")); }
        });
    }

    private void fail(String reason, String detail) {
        if (closed.get()) return;
        Callback value = callback;
        boolean notify = ownerAlive();
        close();
        if (notify && value != null) value.onBlocked(leaseId, reason, detail);
    }
    public void cancel() {
        OriginSessionContract.OriginSessionPort value = port;
        if (value != null) try { value.cancel(generation); } catch (Exception ignored) { }
        close();
    }
    public void closeAfterDeath(Runnable then) {
        IBinder binder = serviceBinder;
        Callback ownerCallback = callback;
        close();
        Task.execute(() -> {
            java.util.concurrent.CountDownLatch stopped = new java.util.concurrent.CountDownLatch(1);
            IBinder.DeathRecipient receipt = stopped::countDown;
            try {
                if (binder != null && binder.isBinderAlive()) binder.linkToDeath(receipt, 0);
                else stopped.countDown();
                if (!stopped.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    handler.post(() -> { if (ownerCallback != null) ownerCallback.onBlocked(leaseId, OriginSessionContract.ISOLATION_ERROR, "old worker still alive"); });
                    return;
                }
                handler.post(then);
            } catch (Exception e) {
                if (binder == null || !binder.isBinderAlive()) handler.post(then);
                else handler.post(() -> { if (ownerCallback != null) ownerCallback.onBlocked(leaseId, OriginSessionContract.ISOLATION_ERROR, e.toString()); });
            } finally { if (binder != null) try { binder.unlinkToDeath(receipt, 0); } catch (Exception ignored) { } }
        });
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        OriginSessionContract.OriginSessionPort value = port;
        port = null;
        if (value != null) try { value.close(leaseId, generation); } catch (Exception ignored) { }
        IBinder binder = serviceBinder;
        serviceBinder = null;
        if (binder != null) binder.unlinkToDeath(death, 0);
        if (bound) {
            bound = false;
            try { context.unbindService(connection); } catch (Exception ignored) { }
        }
        synchronized (routes) { for (PlaybackRouteRegistry.Registration route : routes) route.close(); routes.clear(); }
        callback = null;
        activity = null;
        proxyPort = 0;
    }
}
