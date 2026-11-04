package com.fongmi.android.tv.node;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 猫源运行时：主进程侧的协调层。
 *
 * <p>真正的 Node 启动跑在 {@link NodeService}（{@code :node} 子进程）里。
 * 这里只负责：启动子进程 Service、接收子进程通过 Messenger 回报的进度/端口/错误、
 * 换源时杀旧子进程再起新子进程。
 *
 * <p>换源流程：如果已有子进程在跑且 bundle 不同，先 {@link NodeService#stop} 杀掉它
 * （Node/V8 随进程销毁干净），再 {@link NodeService#start} 起一个新的 :node 进程
 * 跑新 bundle。这绕过了 nodejs-mobile 的 {@code node::Start} 不可进程内重入的限制。
 */
public final class NodeRuntime {

    /** 首选端口，被占用时往后找；bundle 本身不做 EADDRINUSE 重试，所以由这边探。 */
    private static final int PREFERRED_PORT = 9988;
    private static final int PORT_SCAN = 20;

    /**
     * 猫源首次加载包含运行时检查、bundle 下载、Node 启动和 /config 探测，不能沿用普通接口的短预算。
     * 这些预算集中在运行时类中，保证主进程等待、子进程就绪和文件传输不会各自使用互相矛盾的时长。
     */
    public static final long START_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(12);
    static final long TRANSFER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);
    static final long LIB_TRANSFER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    static final long METADATA_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(8);
    static final long READY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);
    static final long READY_PROBE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(2);
    static final long READY_POLL_MS = 200L;

    /**
     * bundle 自带弹幕服务的探测预算。它的端口在猫源本体就绪之后才陆续写进 port 文件，
     * 所以不能只探一次；放后台轮询，界面全程只读缓存值。
     */
    private static final long DANMAKU_PROBE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);
    private static final long DANMAKU_PROBE_POLL_MS = 500L;
    /**
     * 在飞去重记的是<b>正在探测的世代号</b>，不是「有没有线程在探」。
     *
     * <p>MSG_READY 与 start 的复用捷径都可能触发探测。按「有无线程」去重时，快速换源会撞上
     * 上一代仍在跑的线程（最长 20s）：新一代唯一的触发点被挡回去，旧线程随后在世代核对处退出，
     * 新 bundle 的弹幕端口就永久探不到，整场没有弹幕。按世代去重后不同世代互不阻塞。
     */
    private static final AtomicLong DANMAKU_PROBING_GENERATION = new AtomicLong(-1);

    private static volatile int port;
    private static volatile int danmakuPort;
    /**
     * 最近一次探到的弹幕端口，换源清零 port/danmakuPort 时故意保留。
     *
     * <p>换源后正是靠它认出上一场本地包残留的弹幕地址：端口一旦忘了，那个回环地址就与陌生人
     * 的回环地址无从区分，只能永久留在屏幕上。
     */
    private static volatile int lastDanmakuPort;
    private static final AtomicBoolean STARTING = new AtomicBoolean(false);
    private static volatile boolean running;
    private static volatile String servingUrl = "";
    private static volatile String servingSourceKey = "";
    /** 每轮启动的世代号，用来丢弃旧子进程的延迟回报。 */
    private static final AtomicLong START_GENERATION = new AtomicLong();

    /** 主进程侧的回复 Messenger，接收子进程回报。写在后台线程、读在主线程，必须 volatile。 */
    private static volatile Messenger replyMessenger;
    private static volatile NodeRuntime.Callback pendingCallback;

    public interface Callback {
        void onProgress(String message);

        void onReady(String baseUrl);

        void onError(String message);
    }

    private NodeRuntime() {
    }

    public static boolean isRunning() {
        return running && serviceAlive();
    }

    /**
     * 如果 :node 子进程已死（崩溃/被系统杀掉）但还在 serving，立刻用原 bundle 重启它。
     *
     * <p>崩溃时 {@link #running} 没有回调可置假，只能靠进程探活发现。调用方（如爬虫发现
     * 本机 bundle 连不上）检测到后调这里，把运行时恢复到可用状态，后续请求会走新端口。
     * 返回 true 表示本次调用发起了重启（或本来就健康无需动作）。
     */
    public static boolean restartIfDead(Context context) {
        if (running && serviceAlive()) return true;
        if (TextUtils.isEmpty(servingUrl)) return false;
        SpiderDebug.log("node", "restartIfDead: node process dead while serving %s, restarting", servingUrl);
        // 直接复用 start：崩溃分支会清 running/port 并重新拉起同一个 bundle。
        start(context, servingUrl, null);
        return true;
    }

    /**
     * {@code :node} 子进程是否还活着。
     *
     * <p>{@link #running} 只在收到 READY 时置真、收到 ERROR/超时才置假：<b>子进程被系统或
     * 自身 OOM 杀掉时没有任何回调</b>，这个标志会一直停在「已就绪」。此后 {@link #start} 的
     * 复用捷径会一直把那个已经没人监听的死端口当成就绪服务返回，表现为「配置页加载不出来」
     * 且搜不出任何结果——用户只能杀掉应用重开。所以复用前必须实际核对进程是否还在。
     */
    private static boolean serviceAlive() {
        try {
            return isProcessRunning(App.get(), App.get().getPackageName() + ":node");
        } catch (Throwable e) {
            // 探测本身不可用时不能把功能一起判死：宁可退回旧行为（信任标志位）。
            return true;
        }
    }

    public static int port() {
        return port;
    }

    /** 本机回环地址的统一拼法：猫源本体与它自带的弹幕服务都在这一个前缀下。 */
    private static String loopbackUrl(int value, String path) {
        return "http://127.0.0.1:" + value + path;
    }

    public static String baseUrl() {
        return loopbackUrl(port, "");
    }

    public static String configUrl() {
        return loopbackUrl(port, "/config");
    }

    /**
     * bundle 自带的内置弹幕服务地址；未探测到或运行时未就绪时为空串。
     *
     * <p>本地包把弹幕服务开在猫源本体之外的另一个端口（实测 9321），只把它记在 port 文件的
     * 候选集合里。没有这个回退，用户必须自己手填 {@code http://127.0.0.1:端口/danmaku}
     * 才有弹幕，等于本地包开箱不生效。
     */
    public static String danmakuApiUrl() {
        int value = danmakuPort;
        // `:node` 被系统杀掉时没有任何回调会把 running/danmakuPort 置零，只认端口号会把
        // 一个没人监听的地址交给弹幕搜索。与 isServing 同一套判据，先无阻塞判标志再探进程。
        if (value <= 0 || !running || !serviceAlive()) return "";
        return loopbackUrl(value, "/danmaku");
    }

    /** 这个回环地址是不是本运行时自己开出来（现在或曾经）的端口：猫源本体或它自带的弹幕服务。 */
    public static boolean ownsLoopbackPort(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            int value = android.net.Uri.parse(url.trim()).getPort();
            return value > 0 && (value == port || value == danmakuPort || value == lastDanmakuPort);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 端口失效时必须两个一起清：留下旧的弹幕端口会把新源的弹幕指到旧进程的地址上。
     *
     * <p>{@link #lastDanmakuPort} 例外，这里不能把它一起清零——换源后正是靠它认出上一场
     * 本地包残留的弹幕地址，清掉就再也认不出来了。
     */
    private static void clearPort() {
        port = 0;
        danmakuPort = 0;
    }

    /**
     * 启动运行时。同一个 bundle 已在跑则直接复用；换 bundle 则杀旧子进程再起新的。
     *
     * @param url 用户填的猫源地址（{@code .../index.js.md5}）
     */
    public static synchronized void start(Context context, String url, Callback callback) {
        if (TextUtils.isEmpty(url)) {
            if (callback != null) callback.onError("未填写猫源地址");
            return;
        }
        // 复用要求「同一地址」且「来源身份仍与运行中的一致」：只比地址的话，服务端原地更新
        // bundle、或本地包被改写后都会继续跑旧 JS。本地包按内容指纹判定，内容没变就无需重启。
        // 还必须确认子进程仍在：它被杀掉时没有任何回调会把 running 置假。
        boolean alive = running && serviceAlive();
        if (alive && same(url) && NodeBundle.servesCurrentSource(url, servingSourceKey)) {
            // bundle 的弹幕服务可能晚于首次探测预算才落盘端口，复用路径不重探就整场没有弹幕。
            if (danmakuPort == 0) resolveDanmakuPort(port, START_GENERATION.get());
            if (callback != null) callback.onReady(baseUrl());
            return;
        }
        if (running && !alive) {
            SpiderDebug.log("node", "node process is gone while runtime still marked running, restarting");
            running = false;
            servingSourceKey = "";
            clearPort();
        }
        if (!STARTING.compareAndSet(false, true)) {
            if (callback != null) callback.onError("正在启动中");
            return;
        }
        long generation = START_GENERATION.incrementAndGet();
        pendingCallback = callback;
        replyMessenger = new Messenger(new ReplyHandler(generation));
        // 换源：先杀旧子进程（如果有），再起新的
        if (running || !TextUtils.isEmpty(servingUrl)) {
            SpiderDebug.log("node", "switching source: stopping old node service (was=%s)", servingUrl);
            notifyProgress(context, callback, context.getString(R.string.node_stopping));
            NodeService.stop(context);
            // stopService 是异步的，onDestroy 里的 killProcess 也需要时间生效。
            // 不等的话 startForegroundService 会投递到还没死掉的旧进程，node::Start 第二次调用会 SIGSEGV。
            waitForProcessDeath(context);
            running = false;
            servingSourceKey = "";
            clearPort();
            notifyProgress(context, callback, context.getString(R.string.node_switch_restart));
        }
        servingUrl = NodeBundle.bundleUrl(url);
        servingSourceKey = "";
        try {
            NodeService.start(context, url, replyMessenger);
            App.post(() -> timeout(context, generation), START_TIMEOUT_MS);
        } catch (RuntimeException e) {
            STARTING.set(false);
            running = false;
            servingUrl = "";
            servingSourceKey = "";
            clearPort();
            pendingCallback = null;
            replyMessenger = null;
            SpiderDebug.log("node", "failed to start node service: %s", e.getMessage());
            if (callback != null) callback.onError("猫源服务启动失败: " + e.getMessage());
        }
    }

    private static synchronized void timeout(Context context, long generation) {
        if (generation != START_GENERATION.get() || !STARTING.compareAndSet(true, false)) return;
        // 先让当前 reply handler 失效：stopService 是异步的，旧 :node 仍可能在退出前发 READY。
        // 同时保留 servingUrl，让下一次 start 先等待这个旧进程真的结束，不能把重试投给旧 Service。
        START_GENERATION.incrementAndGet();
        NodeService.stop(context);
        running = false;
        servingSourceKey = "";
        clearPort();
        NodeNotify.done(context, "猫源启动失败：服务未在预期时间内就绪");
        Notify.show("猫源启动失败：服务未在预期时间内就绪");
        Callback callback = pendingCallback;
        pendingCallback = null;
        replyMessenger = null;
        if (callback != null) callback.onError("服务未在预期时间内就绪");
    }

    /**
     * 只读判定：{@code url} 对应的 bundle 是否就是当前正在服务的那个。
     *
     * <p>桥接解析针对的是<b>非激活</b>配置，只能靠它决定能否复用运行时：不是同一个 bundle 时
     * {@link #start} 会先杀掉正在服务的子进程，激活配置的全部请求随后打到另一个源上。
     * 这里只读，不启动、不停止。
     *
     * <p>「在服务」必须是真实存活：只比对 {@code servingUrl} 会在两种已失效的状态上撒谎——
     * {@link #timeout} 只把 {@code running} 置 false、{@code port} 置 0，而 {@code :node} 子进程
     * 被系统杀掉时 {@code running} 甚至不会被改写。此时返回 true 会让调用方拿到
     * {@code http://127.0.0.1:0/config} 这种不可用地址。因此先做三次无阻塞的 volatile 判断
     * （running / port>0），再复用既有的 {@link #serviceAlive()} 探一次进程，语义与
     * {@link #isRunning()} 保持同一套判据。
     */
    public static boolean isServing(String url) {
        if (servingUrl.isEmpty() || !running || port <= 0) return false;
        return serviceAlive() && same(url);
    }

    /** 是否就是当前这个 bundle。 */
    private static boolean same(String url) {
        String requested = NodeBundle.bundleUrl(url);
        return !TextUtils.isEmpty(requested) && requested.equals(servingUrl);
    }

    /** 换源等主进程侧阶段进度：同时推通知栏和 callback。 */
    private static void notifyProgress(Context context, Callback callback, String text) {
        SpiderDebug.log("node", "%s", text);
        NodeNotify.progress(context, text, -1);
        App.post(() -> Notify.show(text));
        if (callback != null) callback.onProgress(text);
    }

    /**
     * 等 :node 子进程真正死掉，最多等 3 秒。
     * stopService / killProcess 都是异步的，不等就 startForegroundService 会复用旧进程导致 node::Start 重入崩溃。
     */
    private static void waitForProcessDeath(Context context) {
        String procName = context.getPackageName() + ":node";
        for (int i = 0; i < 30; i++) {
            if (!isProcessRunning(context, procName)) return;
            try { Thread.sleep(100); } catch (InterruptedException e) { return; }
        }
        SpiderDebug.log("node", "old :node process still alive after 3s, proceeding anyway");
    }

    private static boolean isProcessRunning(Context context, String processName) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (processName.equals(info.processName)) return true;
        }
        return false;
    }

    /**
     * 主进程侧处理子进程通过 Messenger 回报的消息。
     *
     * <p>每轮 start 都带自己的 generation：旧 :node 进程可能在 waitForProcessDeath 的 3 秒后
     * 还活着并继续回报，若不校验就会把旧端口写进新一轮的 port/servingSourceKey，
     * 表现为「选了 B 源却连到 A 源」，同时把新一轮的 callback 吃掉。
     */
    private static class ReplyHandler extends Handler {
        private final long generation;

        ReplyHandler(long generation) {
            super(Looper.getMainLooper());
            this.generation = generation;
        }

        @Override
        public void handleMessage(Message msg) {
            if (generation != START_GENERATION.get()) {
                SpiderDebug.log("node", "ignoring stale node reply what=%d gen=%d current=%d", msg.what, generation, START_GENERATION.get());
                return;
            }
            NodeRuntime.Callback callback = pendingCallback;
            switch (msg.what) {
                case NodeService.MSG_READY: {
                    port = msg.arg1;
                    danmakuPort = 0;
                    running = true;
                    servingSourceKey = NodeBundle.installedSourceKey(App.get());
                    resolveDanmakuPort(port, generation);
                    STARTING.set(false);
                    pendingCallback = null;
                    replyMessenger = null;
                    String ready = App.get().getString(R.string.node_ready);
                    NodeNotify.done(App.get(), ready + "，端口 " + port);
                    Notify.show(ready);
                    if (callback != null) callback.onReady(baseUrl());
                    break;
                }
                case NodeService.MSG_PROGRESS: {
                    String text = msg.getData() != null ? msg.getData().getString("text") : "";
                    NodeNotify.progress(App.get(), text, -1);
                    if (callback != null) callback.onProgress(text);
                    break;
                }
                case NodeService.MSG_ERROR: {
                    String text = msg.getData() != null ? msg.getData().getString("text") : "未知错误";
                    STARTING.set(false);
                    running = false;
                    servingSourceKey = "";
                    clearPort();
                    pendingCallback = null;
                    replyMessenger = null;
                    NodeNotify.done(App.get(), "猫源启动失败：" + text);
                    Notify.show("猫源启动失败：" + text);
                    if (callback != null) callback.onError(text);
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * 从首选端口往后找一个能绑上的。探测用的 socket 立刻关闭，与 Node 真正 bind 之间
     * 存在极小的竞争窗口——所以最终仍以落盘的实际端口为准，探测只是让端口尽量可预期。
     * 全部占用时返回 0，让 bundle 自己取随机端口。
     */
    static int freePort() {
        for (int candidate = PREFERRED_PORT; candidate < PREFERRED_PORT + PORT_SCAN; candidate++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress("127.0.0.1", candidate));
                return candidate;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /** 引导脚本落盘的候选端口，逗号分隔，猫源那个（我们指定的）在最前。兼容只有单个端口的旧格式。 */
    static List<Integer> readPorts(File file) {
        List<Integer> ports = new ArrayList<>();
        if (!file.exists()) return ports;
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[128];
            int len = in.read(buf);
            if (len <= 0) return ports;
            for (String part : new String(buf, 0, len).trim().split(",")) {
                try {
                    int value = Integer.parseInt(part.trim());
                    if (value > 0 && !ports.contains(value)) ports.add(value);
                } catch (NumberFormatException ignored) {
                }
            }
            return ports;
        } catch (Exception ignored) {
            return ports;
        }
    }

    /** 认领本世代的一次探测：同一世代已有线程在探才让路，新世代必须能立刻开工。 */
    private static boolean claimDanmakuProbe(long generation) {
        while (true) {
            long current = DANMAKU_PROBING_GENERATION.get();
            if (current == generation) return false;
            if (DANMAKU_PROBING_GENERATION.compareAndSet(current, generation)) return true;
        }
    }

    /** 探测收工：只释放自己认领的那一代，不误清后来者的认领。 */
    private static void releaseDanmakuProbe(long generation) {
        DANMAKU_PROBING_GENERATION.compareAndSet(generation, -1);
    }

    /**
     * 在猫源端口之外的候选端口里找 bundle 自带的内置弹幕服务。
     *
     * <p>只认 {@code GET /danmaku} 回 200 且 body 是 JSON 的那个：猫源本体的同名路径回 404，
     * 而弹幕服务的 /config 回 401，两者正反都区分得开。
     */
    private static void resolveDanmakuPort(int catPort, long generation) {
        // 复用捷径与 MSG_READY 都可能来探：同一世代只需一个线程，重复进来直接让路。
        if (!claimDanmakuProbe(generation)) return;
        try {
            Task.execute(() -> {
                // 复位必须写在任务里：Task.execute 只投递，写在投递处等于没去重。
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .readTimeout(READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .callTimeout(READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .build();
                    long deadline = System.currentTimeMillis() + DANMAKU_PROBE_TIMEOUT_MS;
                    // 换源会 incrementAndGet 出新世代：旧线程把上一代 bundle 的端口写回，
                    // 界面就会「选了 B 源却连到 A 源」，所以每一轮都要核对世代。
                    while (running && generation == START_GENERATION.get() && danmakuPort == 0 && System.currentTimeMillis() < deadline) {
                        for (int candidate : readPorts(new File(NodeBundle.dir(App.get()), "port"))) {
                            if (candidate == catPort) continue;
                            if (!isDanmakuService(client, candidate)) continue;
                            // 探一次要一秒左右，回来时可能已经换了源，写之前必须再核对一次。
                            if (generation != START_GENERATION.get() || !running) return;
                            danmakuPort = candidate;
                            lastDanmakuPort = candidate;
                            SpiderDebug.log("node", "bundle danmaku service on port %d", candidate);
                            return;
                        }
                        try {
                            Thread.sleep(DANMAKU_PROBE_POLL_MS);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                } finally {
                    releaseDanmakuProbe(generation);
                }
            });
        } catch (Throwable e) {
            // 线程池拒收时把这一代的认领让回去，否则同一世代再也探不出端口。
            releaseDanmakuProbe(generation);
        }
    }

    private static boolean isDanmakuService(okhttp3.OkHttpClient client, int candidate) {
        try (okhttp3.Response response = client.newCall(new okhttp3.Request.Builder()
                .url(loopbackUrl(candidate, "/danmaku"))
                .build()).execute()) {
            if (response.code() != 200 || response.body() == null) return false;
            String body = response.body().string().trim();
            return body.startsWith("[") || body.startsWith("{");
        } catch (Exception e) {
            return false;
        }
    }
}
