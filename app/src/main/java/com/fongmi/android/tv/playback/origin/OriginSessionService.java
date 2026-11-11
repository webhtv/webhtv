package com.fongmi.android.tv.playback.origin;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.node.NodeRuntime;
import com.fongmi.android.tv.player.ParseJob;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.crawler.SpiderDebug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class OriginSessionService extends Service {
    private final ExecutorService serial = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final IBinder nodeOwner = new Binder();
    private volatile Future<?> operation;
    private volatile ParseJob parser;
    private volatile String leaseId;
    private volatile long generation;
    private Bundle opened;
    private OriginStorageContext storage;
    private History history;
    private Config config;
    private IBinder owner;
    private Vod detail;
    private int proxyPort;
    private boolean played;

    private final IBinder.DeathRecipient death = this::requestShutdown;
    private final OriginSessionContract.OriginSessionPort port = new OriginSessionContract.OriginSessionPort.Stub() {
        @Override public void open(Bundle request, ParcelFileDescriptor fd, IBinder owner, OriginSessionContract.OriginReply reply) {
            synchronized (OriginSessionService.this) {
                if (leaseId != null || closing.get()) {
                    OriginSessionContract.closeFd(fd);
                    serial.execute(() -> send(reply, request, OriginSessionContract.BLOCKED, OriginSessionContract.ISOLATION_ERROR, "worker lease already held", null));
                    return;
                }
                try { OriginSessionContract.validate(request); }
                catch (Exception e) {
                    OriginSessionContract.closeFd(fd);
                    serial.execute(() -> send(reply, request, OriginSessionContract.BLOCKED, OriginSessionContract.SNAPSHOT_INVALID, e.toString(), null));
                    return;
                }
                leaseId = request.getString("leaseId");
                generation = request.getLong("generation");
                opened = new Bundle(request);
                operation = serial.submit(() -> openLease(request, fd, owner, reply));
            }
        }
        @Override public void play(Bundle request, OriginSessionContract.OriginReply reply) {
            if (closing.get()) { serial.execute(() -> send(reply, request, OriginSessionContract.CANCELLED, "", "", null)); return; }
            operation = serial.submit(() -> playLease(request, reply));
        }
        @Override public void cancel(long value) {
            if (value == generation) requestShutdown();
        }
        @Override public void close(String lease, long value) {
            if (java.util.Objects.equals(leaseId, lease) && generation == value) requestShutdown();
        }
    };
    @Override public IBinder onBind(Intent intent) { return port.asBinder(); }
    @Override public void onDestroy() { requestShutdown(); super.onDestroy(); }

    private void openLease(Bundle request, ParcelFileDescriptor fd, IBinder ownerBinder, OriginSessionContract.OriginReply reply) {
        try {
            if (ownerBinder == null || !ownerBinder.isBinderAlive()) throw new IllegalStateException("owner absent");
            owner = ownerBinder;
            owner.linkToDeath(death, 0);
            storage = OriginSessionBootstrap.install(getBaseContext(), leaseId);
            OriginSessionSnapshot snapshot = OriginSessionSnapshot.read(fd);
            fd = null;
            snapshot.seed(storage, request.getInt("originCid"), request.getString("sourceHistoryKey"));
            history = snapshot.history;
            config = snapshot.config;
            if (!request.getString("expectedResumeVersion", "").equals(OriginSessionSnapshot.resumeVersion(history))) throw new IllegalArgumentException("snapshot resume version");
            if (config == null) {
                send(reply, request, OriginSessionContract.UNAVAILABLE, OriginSessionContract.CONFIG_MISSING, "original config missing", null);
                return;
            }
            com.fongmi.android.tv.setting.ProxySetting.apply();
            OriginMediaRelay.Endpoint endpoint = OriginMediaRelay.begin(leaseId);
            proxyPort = endpoint.port;
            Proxy.set(proxyPort);
            NodeRuntime.openOrigin(storage, leaseId, generation, proxyPort, nodeOwner);
            try { VodConfig.get().loadOriginConfig(config); }
            catch (Exception e) {
                String reason = OriginHistoryResolver.infrastructure(e) ? OriginSessionContract.ISOLATION_ERROR
                        : OriginHistoryResolver.auth(e) ? OriginSessionContract.ORIGIN_AUTH_REQUIRED : OriginSessionContract.CONFIG_LOAD_FAILED;
                send(reply, request, OriginSessionContract.shouldFallback(reason) ? OriginSessionContract.UNAVAILABLE : OriginSessionContract.BLOCKED, reason, e.toString(), null);
                return;
            }
            if (closing.get()) return;
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("pid", android.os.Process.myPid());
            info.put("root", storage.root().getPath());
            info.put("files", storage.getFilesDir().getPath());
            info.put("cache", storage.getCacheDir().getPath());
            info.put("codeCache", storage.getCodeCacheDir().getPath());
            info.put("database", storage.getDatabasePath("tv").getPath());
            info.put("cid", VodConfig.getCid());
            info.put("sites", VodConfig.get().getSites().size());
            send(reply, request, OriginSessionContract.READY, "", "", info);
        } catch (IllegalArgumentException e) {
            send(reply, request, OriginSessionContract.BLOCKED, OriginSessionContract.SNAPSHOT_INVALID, e.toString(), null);
        } catch (Throwable e) {
            if (!closing.get()) send(reply, request, OriginSessionContract.BLOCKED, OriginSessionContract.ISOLATION_ERROR, e.toString(), null);
        } finally { OriginSessionContract.closeFd(fd); }
    }

    private void playLease(Bundle request, OriginSessionContract.OriginReply reply) {
        try {
            OriginSessionContract.validate(request);
            if (!leaseId.equals(request.getString("leaseId")) || generation != request.getLong("generation")) {
                send(reply, request, OriginSessionContract.STALE, "", "", null);
                return;
            }
            if (history == null || config == null) throw new IllegalStateException("lease not loaded");
            int playerType = request.getInt("playerType", history.getPlayer());
            if (!history.getSiteKey().equals(request.getString("siteKey", history.getSiteKey())) || !history.getVodId().equals(request.getString("vodId", history.getVodId()))) throw new IllegalArgumentException("foreign episode request");
            OriginHistoryResolver.Resolved resolved = detail == null ? OriginHistoryResolver.resolve(history, playerType)
                    : OriginHistoryResolver.select(history.getSiteKey(), detail, request.getString("stableFlagKey", ""), request.getString("episodeUrl", ""), playerType);
            detail = resolved.vod;
            OriginMediaRelay.bindProxy(leaseId, params -> {
                VodConfig.get().getSite(history.getSiteKey()).recent();
                return com.fongmi.android.tv.api.loader.BaseLoader.get().proxy(params);
            });
            Result result = resolved.result;
            if (result.needParse()) {
                // 低版本没有独立的 WebView 数据目录：不能在这个 worker 里创建 WebView，
                // 必须明确报隔离不足，而不是伪造成原源不可用去匹配当前源。
                if (!OriginSessionBootstrap.canIsolateWebView())
                    throw OriginHistoryResolver.failure(OriginSessionContract.ISOLATION_ERROR,
                            "origin source needs WebView sniffing; a private WebView directory needs Android 9+");
                result = parse(result);
            }
            if (closing.get()) return;
            result = relayResult(result);
            String flagKey = Flag.stableKey(resolved.flag, detail.getFlags().indexOf(resolved.flag));
            Map<String, Object> payload = new LinkedHashMap<>();
            com.google.gson.JsonObject displayVod = App.gson().toJsonTree(detail).getAsJsonObject();
            displayVod.remove("site");
            payload.put("vod", App.gson().toJson(displayVod));
            payload.put("site", VodConfig.get().getSite(history.getSiteKey()).getDisplayName());
            payload.put("flags", App.gson().toJson(detail.getFlags()));
            payload.put("flag", resolved.flag.getFlag());
            payload.put("selectedFlagKey", flagKey);
            payload.put("episode", resolved.episode.getName());
            payload.put("selectedEpisode", App.gson().toJson(resolved.episode));
            com.google.gson.JsonObject mediaResult = App.gson().toJsonTree(result).getAsJsonObject();
            mediaResult.remove("header");
            mediaResult.addProperty("jx", 0);
            mediaResult.remove("list");
            payload.put("result", App.gson().toJson(mediaResult));
            payload.put("resumePositionMs", played ? 0L : history.getPosition());
            payload.put("resumeDurationMs", played ? 0L : history.getDuration());
            played = true;
            send(reply, request, OriginSessionContract.PLAY_READY, "", "", payload);
        } catch (OriginHistoryResolver.Failure e) {
            android.util.Log.w("OriginSession", "play classified failure lease=" + leaseId + " reason=" + e.reason + " detail=" + e.getMessage());
            if (!closing.get()) send(reply, request, !played && OriginSessionContract.shouldFallback(e.reason) ? OriginSessionContract.UNAVAILABLE : OriginSessionContract.BLOCKED, e.reason, e.toString(), null);
        } catch (Exception e) {
            android.util.Log.w("OriginSession", "play failed lease=" + leaseId + " generation=" + generation, e);
            if (!closing.get()) send(reply, request, OriginSessionContract.BLOCKED, OriginSessionContract.ISOLATION_ERROR, e.toString(), null);
        }
    }

    /** 相对媒体地址只能相对原历史里那条绝对地址解析；解析不出来就原样返回，让后面的判定给出可见失败。 */
    private String resolveMediaUrl(String url) {
        if (url == null || url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:")) return url;
        String base = history == null ? "" : history.getEpisodeUrl();
        if (base == null || !(base.startsWith("http:") || base.startsWith("https:") || base.startsWith("file:"))) return url;
        try {
            return OriginMediaRelay.resolve(base, url);
        } catch (Exception e) {
            return url;
        }
    }

    private Result relayResult(Result result) throws OriginHistoryResolver.Failure {
        // 相对媒体地址必须先按原历史里那条绝对地址解析：资源站返回 "/vod/xxx.m3u8" 很常见，
        // 不解析就会以"unmapped original media scheme"拒绝一个本来可播的原源，用户看到的是播放失败。
        String url = resolveMediaUrl(result.getRealUrl());
        result.setUrl(url);
        String originalMediaUrl = url;
        int contentType = androidx.media3.common.util.Util.inferContentTypeForUriAndMimeType(android.net.Uri.parse(url), result.getFormat());
        if (contentType == androidx.media3.common.C.CONTENT_TYPE_HLS) result.setFormat(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
        else if (contentType == androidx.media3.common.C.CONTENT_TYPE_DASH) result.setFormat(androidx.media3.common.MimeTypes.APPLICATION_MPD);
        else if (contentType == androidx.media3.common.C.CONTENT_TYPE_SS)
            throw OriginHistoryResolver.failure(OriginSessionContract.ISOLATION_ERROR, "origin SmoothStreaming manifest is not isolated yet");
        Map<String, String> headers = new LinkedHashMap<>(result.getHeader());
        if (url.startsWith("edl://")) {
            try {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("file=%([0-9]+)%").matcher(url);
                StringBuilder mapped = new StringBuilder(); int offset = 0;
                while (matcher.find(offset)) {
                    mapped.append(url, offset, matcher.start());
                    int byteLength = Integer.parseInt(matcher.group(1));
                    byte[] remaining = url.substring(matcher.end()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if (byteLength <= 0 || byteLength > remaining.length) throw new IllegalArgumentException("invalid EDL length");
                    String source = new String(remaining, 0, byteLength, java.nio.charset.StandardCharsets.UTF_8);
                    if (!(source.startsWith("http:") || source.startsWith("https:") || source.startsWith("file:"))) throw new IllegalArgumentException("unmapped EDL media");
                    String local = OriginMediaRelay.localUrl(leaseId, OriginMediaRelay.register(leaseId, source, headers));
                    mapped.append("file=%").append(local.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).append('%').append(local);
                    offset = matcher.end() + source.length();
                }
                if (offset == 0) throw new IllegalArgumentException("unrecognized EDL");
                mapped.append(url.substring(offset)); url = mapped.toString();
            } catch (Exception e) { throw OriginHistoryResolver.failure(OriginSessionContract.ISOLATION_ERROR, e.toString()); }
        } else {
            if (!(url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:"))) throw OriginHistoryResolver.failure(OriginSessionContract.ISOLATION_ERROR, "unmapped original media scheme");
            url = OriginMediaRelay.localUrl(leaseId, OriginMediaRelay.register(leaseId, url, headers));
        }
        result.setUrl(url); result.setPlayUrl(""); result.setParse(0); result.getHeader().clear();
        for (com.fongmi.android.tv.bean.Value value : result.getUrl().getValues()) {
            if (value.getV().startsWith("http://127.0.0.1:" + proxyPort + "/origin/") || value.getV().startsWith("edl://")) continue;
            if (!(value.getV().startsWith("http") || value.getV().startsWith("file:"))) throw OriginHistoryResolver.failure(OriginSessionContract.ISOLATION_ERROR, "unmapped alternate media URL");
            value.setV(OriginMediaRelay.localUrl(leaseId, OriginMediaRelay.register(leaseId, value.getV(), headers)));
        }
        if (result.getDrm() != null) {
            com.fongmi.android.tv.bean.Drm drm = result.getDrm();
            if (drm.getKey().startsWith("http")) {
                Map<String, String> licenseHeaders = new LinkedHashMap<>(headers); licenseHeaders.putAll(drm.getHeader());
                result.setDrm(com.fongmi.android.tv.bean.Drm.create(OriginMediaRelay.localUrl(leaseId, OriginMediaRelay.register(leaseId, drm.getKey(), licenseHeaders)), drm.getType(), Map.of(), drm.isForceKey()));
            }
        }
        if (!result.getSubs().isEmpty()) {
            com.google.gson.JsonObject safeResult = App.gson().toJsonTree(result).getAsJsonObject();
            com.google.gson.JsonArray mappedSubs = new com.google.gson.JsonArray();
            for (com.fongmi.android.tv.bean.Sub sub : result.getSubs()) {
                String source = OriginMediaRelay.resolve(originalMediaUrl, sub.getUrl());
                if (!(source.startsWith("http:") || source.startsWith("https:") || source.startsWith("file:")))
                    throw OriginHistoryResolver.failure(OriginSessionContract.ISOLATION_ERROR, "unmapped original subtitle scheme");
                com.google.gson.JsonObject mapped = App.gson().toJsonTree(sub).getAsJsonObject();
                mapped.addProperty("url", OriginMediaRelay.localUrl(leaseId, OriginMediaRelay.register(leaseId, source, headers)));
                mappedSubs.add(mapped);
            }
            safeResult.add("subs", mappedSubs);
            result = Result.fromJson(safeResult.toString());
        }
        return result;
    }

    private Result parse(Result result) throws OriginHistoryResolver.Failure {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result> out = new AtomicReference<>();
        Map<String, String> originalHeaders = new LinkedHashMap<>(result.getHeader());
        parser = ParseJob.create(new com.fongmi.android.tv.impl.ParseCallback() {
            @Override public void onParseSuccess(Map<String, String> headers, String url, String from) {
                Map<String, String> merged = new LinkedHashMap<>(originalHeaders);
                if (headers != null) merged.putAll(headers);
                result.setUrl(url);
                result.getHeader().clear();
                result.setHeader(merged);
                result.setPlayUrl("");
                result.setParse(0);
                out.set(result);
                latch.countDown();
            }
            @Override public void onParseError() { latch.countDown(); }
        }).start(result, false);
        try {
            if (!latch.await(45, TimeUnit.SECONDS) || out.get() == null) throw OriginHistoryResolver.failure(OriginSessionContract.PARSE_FAILED, "origin parse unresolved");
            return out.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OriginHistoryResolver.failure(OriginSessionContract.PARSE_FAILED, "origin parse cancelled");
        } finally { if (parser != null) parser.stop(); parser = null; }
    }

    private void send(OriginSessionContract.OriginReply reply, Bundle request, String status, String reason, String detail, Object payload) {
        if (reply == null) return;
        Bundle envelope = new Bundle();
        envelope.putInt("version", OriginSessionContract.VERSION);
        envelope.putString("leaseId", request == null ? leaseId : request.getString("leaseId"));
        envelope.putLong("generation", request == null ? generation : request.getLong("generation"));
        envelope.putString("status", status);
        envelope.putString("reason", reason);
        envelope.putString("detail", detail);
        envelope.putInt("originCid", request == null ? -1 : request.getInt("originCid"));
        envelope.putString("sourceHistoryKey", request == null ? "" : request.getString("sourceHistoryKey"));
        envelope.putString("configRevision", request == null ? "" : request.getString("configRevision"));
        envelope.putInt("proxyPort", proxyPort);
        envelope.putIntArray("nodePorts", NodeRuntime.originPorts(leaseId, generation));
        envelope.putInt("pid", android.os.Process.myPid());
        ParcelFileDescriptor output = null;
        try {
            if (payload != null) output = OriginSessionSnapshot.writeJson(storage.root(), payload);
            reply.complete(envelope, output);
            android.util.Log.i("OriginSession", "status=" + status + " reason=" + reason + " pid=" + android.os.Process.myPid() + " proxy=" + proxyPort);
        } catch (Exception e) { requestShutdown(); }
        finally { OriginSessionContract.closeFd(output); }
    }

    private void requestShutdown() {
        if (!closing.compareAndSet(false, true)) return;
        Future<?> running = operation;
        if (running != null) running.cancel(true);
        if (parser != null) parser.stop();
        OkHttp.cancelAll();
        serial.shutdownNow();
        Task.execute(() -> {
            String lease = leaseId;
            try { NodeRuntime.closeOrigin(lease, generation); }
            catch (Exception e) { android.util.Log.e("OriginSession", "Node close failed", e); }
            OriginMediaRelay.close(lease);
            if (owner != null) owner.unlinkToDeath(death, 0);
            android.util.Log.i("OriginSession", "closed lease=" + lease + " pid=" + android.os.Process.myPid());
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
}
