package com.fongmi.android.tv.node;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.playback.origin.OriginSessionBootstrap;
import com.fongmi.android.tv.playback.origin.OriginStorageContext;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class OriginNodeService extends Service {
    static final int OPEN = 100, CLOSE = 101;
    static final String LEASE = "leaseId", GENERATION = "generation", PROXY_PORT = "workerProxyPort";
    private static final int NOTIFICATION_ID = 9533;
    private static final String CHANNEL = "origin_node";
    private static final AtomicBoolean PROCESS_LAUNCHED = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Messenger endpoint = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message message) {
            if (message.sendingUid != android.os.Process.myUid()) return;
            Bundle data = message.getData();
            if (message.what == OPEN) open(data, message.replyTo);
            else if (message.what == CLOSE && matches(data)) shutdown();
        }
    });
    private String leaseId;
    private long generation;
    private int workerProxyPort;
    private OriginStorageContext storage;
    private Messenger reply;
    private IBinder owner;
    private Thread preparation;
    private OkHttpClient probeClient;
    private final IBinder.DeathRecipient ownerDeath = () -> handler.post(this::shutdown);

    @Override public void onCreate() {
        super.onCreate();
        if (!isOwnProcess()) throw new IllegalStateException("ISOLATION_ERROR: wrong Node process");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "原源 Node", NotificationManager.IMPORTANCE_LOW));
        }
        foreground("原源 Node 准备中");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        foreground("原源 Node 准备中");
        if (intent == null || !intent.hasExtra(LEASE)) shutdown();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return endpoint.getBinder(); }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }

    private boolean isOwnProcess() {
        // 低版本没有 Application.getProcessName()：统一走 /proc 兜底，否则 Node 进程不会自毁。
        return (getPackageName() + ":origin_node").equals(com.fongmi.android.tv.playback.origin.OriginSessionBootstrap.processName());
    }

    private boolean matches(Bundle data) {
        return data != null && leaseId != null && leaseId.equals(data.getString(LEASE))
                && generation == data.getLong(GENERATION);
    }

    private void open(Bundle data, Messenger response) {
        if (closed.get()) return;
        if (leaseId != null) {
            if (!matches(data)) fail("ISOLATION_ERROR: live Node lease cannot be replaced");
            return;
        }
        reply = response;
        try {
            leaseId = data.getString(LEASE);
            UUID.fromString(leaseId);
            generation = data.getLong(GENERATION);
            workerProxyPort = data.getInt(PROXY_PORT);
            owner = data.getBinder("owner");
            String url = data.getString("url");
            if (response == null || owner == null || !owner.isBinderAlive() || workerProxyPort <= 0
                    || workerProxyPort > 65535 || url == null || url.isEmpty()) {
                throw new IllegalStateException("ISOLATION_ERROR: invalid Node lease");
            }
            owner.linkToDeath(ownerDeath, 0);
            if (closed.get() || !owner.isBinderAlive()) throw new IllegalStateException("WORKER_DIED");
            storage = OriginSessionBootstrap.install(getBaseContext(), leaseId);
            NodeBundle.originDir(storage, leaseId);
            Proxy.set(workerProxyPort);
            if (!PROCESS_LAUNCHED.compareAndSet(false, true)) {
                throw new IllegalStateException("ISOLATION_ERROR: libnode already used in process");
            }
            preparation = new Thread(() -> startNode(url), "origin-node-runtime");
            preparation.start();
        } catch (Throwable e) { fail(error(e)); }
    }

    private void startNode(String url) {
        try {
            send(NodeService.MSG_PROGRESS, "原源 Node 运行时检查", 0, null);
            String error = NodeLib.ensure(storage, (done, total) -> send(NodeService.MSG_PROGRESS, "原源 Node " + done + "/" + total, 0, null));
            if (closed.get()) return;
            if (error != null) throw new IllegalStateException("ISOLATION_ERROR: " + error);
            error = NodeBundle.ensure(storage, url);
            if (closed.get()) return;
            if (error != null) throw new IllegalStateException("ISOLATION_ERROR: " + error);
            File directory = NodeBundle.originDir(storage, leaseId);
            File portFile = new File(directory, "port");
            if (portFile.exists() && !portFile.delete()) throw new IllegalStateException("ISOLATION_ERROR: stale Node port");
            int nodePort;
            try (ServerSocket socket = new ServerSocket()) {
                socket.bind(new InetSocketAddress("127.0.0.1", 0));
                nodePort = socket.getLocalPort();
            }
            File script = NodeBoot.write(storage, NodeBundle.file(storage), NodeBundle.config(storage), workerProxyPort, nodePort, leaseId, generation);
            if (closed.get()) return;
            new Thread(() -> {
                try {
                    int result = com.fongmi.android.tv.nodejs.NodeBridge.start(script, "--max-old-space-size=256");
                    if (!closed.get()) fail("WORKER_DIED: Node exited " + result);
                } catch (Throwable e) { if (!closed.get()) fail("ISOLATION_ERROR: " + error(e)); }
            }, "origin-node-main").start();
            int[] ports = waitReady(portFile, nodePort);
            if (closed.get()) return;
            if (ports == null) throw new IllegalStateException("PORT_OWNERSHIP_AMBIGUOUS: Node port not proven");
            send(NodeService.MSG_READY, "", nodePort, ports);
            handler.post(() -> { if (!closed.get()) foreground("原源 Node 端口 " + nodePort); });
        } catch (Throwable e) { if (!closed.get()) fail(error(e)); }
    }

    private int[] waitReady(File portFile, int nodePort) throws Exception {
        probeClient = new OkHttpClient.Builder()
                .connectTimeout(NodeRuntime.READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(NodeRuntime.READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(NodeRuntime.READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS).build();
        long deadline = SystemClock.elapsedRealtime() + NodeRuntime.READY_TIMEOUT_MS;
        while (!closed.get() && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(NodeRuntime.READY_POLL_MS);
            if (!portFile.isFile()) continue;
            int[] ports;
            try (FileInputStream input = new FileInputStream(portFile)) {
                byte[] bytes = new byte[4096];
                int length = input.read(bytes);
                if (length <= 0 || input.read() != -1) throw new IllegalStateException("PORT_OWNERSHIP_AMBIGUOUS: invalid port receipt");
                JSONObject receipt = new JSONObject(new String(bytes, 0, length, StandardCharsets.UTF_8));
                if (!leaseId.equals(receipt.getString(LEASE)) || !Long.toString(generation).equals(receipt.getString(GENERATION))
                        || receipt.getInt(PROXY_PORT) != workerProxyPort || receipt.getInt("nodePort") != nodePort
                        || receipt.getInt("pid") != android.os.Process.myPid()) {
                    throw new IllegalStateException("PORT_OWNERSHIP_AMBIGUOUS: stale port receipt");
                }
                JSONArray values = receipt.getJSONArray("ports");
                ports = new int[values.length()];
                boolean found = false;
                for (int i = 0; i < ports.length; i++) {
                    ports[i] = values.getInt(i);
                    if (ports[i] <= 0 || ports[i] > 65535 || ports[i] == workerProxyPort) {
                        throw new IllegalStateException("PORT_OWNERSHIP_AMBIGUOUS: invalid owned port");
                    }
                    found |= ports[i] == nodePort;
                }
                if (!found) continue;
            }
            try (Response response = probeClient.newCall(new Request.Builder().url("http://127.0.0.1:" + nodePort + "/config").build()).execute()) {
                if (!leaseId.equals(response.header("X-Origin-Node-Lease"))
                        || !Long.toString(generation).equals(response.header("X-Origin-Node-Generation"))) {
                    throw new IllegalStateException("PORT_OWNERSHIP_AMBIGUOUS: foreign Node listener");
                }
                if (response.isSuccessful() && response.body() != null && CatSource.isConfig(response.body().string())) return ports;
            } catch (java.io.IOException ignored) { }
        }
        return null;
    }

    private void send(int what, String text, int nodePort, int[] ports) {
        if (closed.get() || reply == null) return;
        Bundle data = new Bundle();
        data.putString(LEASE, leaseId);
        data.putLong(GENERATION, generation);
        data.putInt(PROXY_PORT, workerProxyPort);
        data.putString("text", text);
        if (ports != null) data.putIntArray("ports", ports);
        Message message = Message.obtain(null, what, nodePort, 0);
        message.setData(data);
        try { reply.send(message); }
        catch (RemoteException e) { handler.post(this::shutdown); }
    }

    private void fail(String text) {
        SpiderDebug.log("origin-node", "%s", text);
        send(NodeService.MSG_ERROR, text, 0, null);
        handler.post(this::shutdown);
    }

    private static String error(Throwable e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return text.startsWith("PORT_OWNERSHIP_AMBIGUOUS") || text.startsWith("WORKER_DIED") || text.startsWith("ISOLATION_ERROR")
                ? text : "ISOLATION_ERROR: " + text;
    }

    private void foreground(String text) {
        Notification notification = new androidx.core.app.NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("原源 Node")
                .setContentText(text).setOnlyAlertOnce(true).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION_ID, notification);
    }

    private void shutdown() {
        if (!closed.compareAndSet(false, true)) return;
        if (owner != null) try { owner.unlinkToDeath(ownerDeath, 0); } catch (RuntimeException ignored) { }
        if (preparation != null) preparation.interrupt();
        if (probeClient != null) probeClient.dispatcher().cancelAll();
        stopForeground(true);
        stopSelf();
        if (isOwnProcess()) android.os.Process.killProcess(android.os.Process.myPid());
    }
}
