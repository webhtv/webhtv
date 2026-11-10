package com.fongmi.android.tv.download;

import android.os.SystemClock;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.Setting;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 全局下载管理器：单例，队列管理，4 线程并发，整合直链多线程分片 + HLS 分片下载。
 *  支持 暂停 / 继续 / 取消 / 失败重试（重试优先续传）。 */
public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final int DEFAULT_PARALLEL = 4;
    /** 连续失败多少次就熔断整轮：URL 失效/被限流时，避免几百个分片挨个建 0B 文件跑几小时 */
    private static final int MAX_CONSECUTIVE_FAIL = 5;
    /** 任务快照落盘路径：<filesDir>/download/tasks.json */
    private static final String STORE_DIR = "download";
    private static final String STORE_FILE = "tasks.json";
    /** 落盘节流间隔：分片完成会频繁触发保存，避免高频写盘 */
    private static final long SAVE_MIN_INTERVAL = 1500;

    private static class Holder { static final DownloadManager INSTANCE = new DownloadManager(); }
    public static DownloadManager get() {
        DownloadManager m = Holder.INSTANCE;
        m.restoreIfNeeded();   // 进程重启后恢复历史任务，保证下载管理页不为空
        return m;
    }

    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    /** 每轮调度的代号：暂停/取消后旧一轮还在收尾的线程不再影响新一轮结果 */
    private final Map<String, AtomicInteger> runGenerations = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
    /** 分片线程池按任务隔离：任务 A 的残留线程不会占着任务 B 的线程，
     *  暂停/取消时也可以整体 shutdownNow() 掉，把排队中的分片一次性清空。 */
    private final Map<String, ExecutorService> segmentPools = new ConcurrentHashMap<>();
    private volatile Listener listener;
    /** 落盘相关状态：只恢复一次，保存走节流 + 单线程 */
    private volatile boolean restored;
    private volatile long lastSaveTime;
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    public interface Listener {
        void onTaskStarted(DownloadTask task);
        void onTaskProgress(DownloadTask task, long bytes, long total);
        void onTaskCompleted(DownloadTask task, File output);
        void onTaskFailed(DownloadTask task, String msg);
        void onTaskPaused(DownloadTask task);
        void onTaskResumed(DownloadTask task);
        void onTaskCancelled(DownloadTask task);
    }

    public void setListener(Listener l) { this.listener = l; }

    /** 创建任务，outputDir 可传 null（此时使用 Setting 中配置的下载目录）。
     *  输出目录在创建时固化到任务上，之后修改全局设置不会再影响该任务。 */
    public DownloadTask addTask(String name, String url, Map<String, String> headers, File outputDir) {
        File dir = outputDir != null ? outputDir : new File(Setting.getDownloadDir());
        dir.mkdirs();
        DownloadTask task = new DownloadTask();
        task.id = UUID.randomUUID().toString();
        task.name = sanitizeFilename(name);
        task.url = url;
        task.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        task.type = looksLikeHls(url) ? DownloadTask.Type.HLS : DownloadTask.Type.DIRECT;
        task.ext = task.type == DownloadTask.Type.HLS ? "mp4" : guessExt(url);
        task.outputDir = dir.getAbsolutePath();
        tasks.put(task.id, task);
        cancelFlags.put(task.id, new AtomicBoolean(false));
        saveAsync();   // 新任务立刻落盘，避免刚退出进程就丢记录

        // 异步初始化（解析 m3u8 / 获取直链大小 / 分片规划）
        taskExecutor.submit(() -> initAndStart(task));
        return task;
    }

    private void initAndStart(DownloadTask task) {
        try {
            new File(task.outputDir).mkdirs();
            task.partDirPath = partDir(task).getAbsolutePath();
            if (task.type == DownloadTask.Type.HLS) {
                initHls(task);
            } else {
                initDirect(task);
            }
            // 解析 m3u8 / 探测大小可能耗时较久，期间用户可能已经点了暂停或取消；
            // 这里必须尊重最新状态，否则会把 PAUSED 强行改回 RUNNING，导致暂停彻底失效
            if (task.status == DownloadTask.Status.PAUSED || task.status == DownloadTask.Status.CANCELLED) return;
            task.status = DownloadTask.Status.RUNNING;
            if (listener != null) listener.onTaskStarted(task);
            saveAsync();   // 分片规划结果（totalSegments/总大小）落盘
            runSegments(task);
        } catch (Exception e) {
            Log.e(TAG, "init failed", e);
            fail(task, e.getMessage());
        }
    }

    private void initHls(DownloadTask task) throws Exception {
        HlsParser.Result result = HlsParser.parse(task.url, task.headers);
        List<DownloadTask.FileSegment> segs = new ArrayList<>();
        // fMP4（EXT-X-MAP）：初始化分片必须排在所有媒体分片之前，否则合并结果无法播放
        if (result.initUrl != null) {
            DownloadTask.FileSegment init = new DownloadTask.FileSegment();
            init.index = -1;
            init.url = result.initUrl;
            init.start = 0;
            init.end = -1;
            init.partFile = new File(partDir(task), "seg_init.part").getAbsolutePath();
            segs.add(init);
        }
        Map<String, byte[]> keyCache = new HashMap<>();
        for (int i = 0; i < result.segmentUrls.size(); i++) {
            DownloadTask.FileSegment s = new DownloadTask.FileSegment();
            s.index = i;
            s.url = result.segmentUrls.get(i);
            s.start = 0;
            s.end = -1;
            s.partFile = new File(partDir(task), "seg_" + i + ".part").getAbsolutePath();
            String keyUrl = result.segmentKeyUrls.get(i);
            if (keyUrl != null) {
                byte[] key = keyCache.get(keyUrl);
                if (key == null) {
                    key = HlsParser.fetchBytes(keyUrl, task.headers);
                    keyCache.put(keyUrl, key);
                }
                if (key != null && key.length > 0) {
                    s.keyBytes = key;
                    // 未显式给 IV 时，按 RFC 8216 用媒体序号推导 IV
                    byte[] iv = result.segmentKeyIvs.get(i);
                    s.keyIv = iv != null && iv.length == 16 ? iv : sequenceIv(result.mediaSequence + i);
                }
            }
            segs.add(s);
        }
        task.segments = segs.toArray(new DownloadTask.FileSegment[0]);
        task.totalSegments = segs.size();
        task.totalSize = -1;   // hls 总大小未知，靠分片实际下载量累加
        task.ext = "mp4";      // 分片合并结果统一使用 .mp4 扩展名
    }

    /** 由媒体序号推导 AES-128 IV（16 字节大端）。 */
    private static byte[] sequenceIv(long seq) {
        byte[] iv = new byte[16];
        for (int i = 15; i >= 0 && seq != 0; i--) {
            iv[i] = (byte) (seq & 0xFF);
            seq >>>= 8;
        }
        return iv;
    }

    private void initDirect(DownloadTask task) throws Exception {
        Probe p = probe(task.url, task.headers);
        task.totalSize = p.length;  // -1 表示大小未知
        // 仅当服务器支持 Range 且总大小已知时才多线程分片；否则单线程整文件下载
        boolean canSplit = p.range && p.length > 0;
        if (!canSplit) {
            task.segments = new DownloadTask.FileSegment[]{newSegment(task, 0, 0, -1)};
            task.totalSegments = 1;
        } else {
            int n = DEFAULT_PARALLEL;
            long perSeg = (p.length + n - 1) / n;
            DownloadTask.FileSegment[] arr = new DownloadTask.FileSegment[n];
            for (int i = 0; i < n; i++) {
                long start = i * perSeg;
                long end = Math.min(p.length - 1, (i + 1) * perSeg - 1);
                arr[i] = newSegment(task, i, start, end);
            }
            task.segments = arr;
            task.totalSegments = n;
        }
    }

    private DownloadTask.FileSegment newSegment(DownloadTask task, int index, long start, long end) {
        DownloadTask.FileSegment s = new DownloadTask.FileSegment();
        s.index = index;
        s.url = task.url;
        s.start = start;
        s.end = end;
        s.partFile = new File(partDir(task), "seg_" + index + ".part").getAbsolutePath();
        return s;
    }

    /** 调度一轮分片下载：已完成分片直接跳过，未完成的按已有 .part 续传。
     *
     *  关键改动：不再把全部分片一次性 submit 进无界队列。
     *  旧实现会把几百个分片一起排进线程池队列，暂停/取消根本撤不下来：
     *  排队的任务照样被执行 → 先 new FileOutputStream 建出 0B 的 .part，再读到中断标记退出，
     *  于是「暂停后不断冒出 0B 分片、点继续毫无反应、取消后还在生成」。
     *  现在改成固定 4 个 worker 用 cursor 主动取号，队列里永远只有 4 个 runnable，
     *  暂停/取消在最坏情况下也只多写一个分片就停。 */
    private void runSegments(DownloadTask task) {
        final int gen = nextGeneration(task.id);
        // 新一轮必须用全新的中断标记（复用旧的会被上一轮的残留线程读到 false）
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(task.id, cancelled);

        final AtomicInteger finished = new AtomicInteger(0);
        final AtomicInteger succeeded = new AtomicInteger(0);
        final AtomicInteger cursor = new AtomicInteger(0);
        final AtomicInteger consecutiveFail = new AtomicInteger(0);

        for (DownloadTask.FileSegment seg : task.segments) {
            if (seg.done) {
                finished.incrementAndGet();
                succeeded.incrementAndGet();
            }
        }
        task.completedSegments = succeeded.get();
        if (finished.get() >= task.totalSegments) {
            mergeSegments(task, gen);
            return;
        }

        final ExecutorService pool = poolFor(task);
        for (int i = 0; i < DEFAULT_PARALLEL; i++) {
            try {
                pool.submit(() -> runWorker(task, gen, cursor, finished, succeeded, consecutiveFail, cancelled));
            } catch (RejectedExecutionException e) {
                break;   // 本轮已被作废（线程池已关闭）
            }
        }
        // 提交期间可能刚好被暂停/取消：立刻停掉本轮，别让 worker 继续取号
        if (cancelled.get() || !isCurrent(task.id, gen)) stopRound(task.id);
    }

    /** 单个 worker：循环取号下载，取完或本轮作废就退出。一次只持有一个分片。 */
    private void runWorker(DownloadTask task, int gen, AtomicInteger cursor,
                           AtomicInteger finished, AtomicInteger succeeded,
                           AtomicInteger consecutiveFail, AtomicBoolean cancelled) {
        while (true) {
            if (cancelled.get() || !isCurrent(task.id, gen)) return;
            int idx = cursor.getAndIncrement();
            if (idx < 0 || idx >= task.segments.length) return;
            final DownloadTask.FileSegment seg = task.segments[idx];
            if (seg.done) continue;
            SegmentDownloader.download(seg, task.headers, cancelled,
                    new SegmentDownloader.Listener() {
                        @Override public void onProgress(DownloadTask.FileSegment s, long bytesDownloaded) {
                            if (!isCurrent(task.id, gen)) return;
                            long total = sumSegmentBytes(task);
                            task.downloadedBytes = total;
                            if (listener != null) listener.onTaskProgress(task, total, task.totalSize);
                        }
                        @Override public void onComplete(DownloadTask.FileSegment s) {
                            if (!isCurrent(task.id, gen)) return;
                            consecutiveFail.set(0);
                            task.completedSegments = succeeded.incrementAndGet();
                            // hls 场景: 记录实际分片大小
                            if (task.type == DownloadTask.Type.HLS) {
                                try { s.end = new File(s.partFile).length(); } catch (Exception ignored) {}
                            }
                            saveAsync();   // 分片完成即落盘：续传进度不至于丢太多
                            if (finished.incrementAndGet() >= task.totalSegments) mergeSegments(task, gen);
                        }
                        @Override public void onError(DownloadTask.FileSegment s, String msg) {
                            if (!isCurrent(task.id, gen)) return;
                            // 连续多个分片失败（URL 失效 / 被限流 / 空响应）：熔断，
                            // 否则几百个分片会挨个建 0B 文件跑上小时
                            if (consecutiveFail.incrementAndGet() >= MAX_CONSECUTIVE_FAIL) {
                                stopRound(task.id);
                                fail(task, "Aborted after " + MAX_CONSECUTIVE_FAIL + " consecutive failures: " + msg);
                                return;
                            }
                            // 单分片失败不立即放弃；等所有分片结束再统一判定
                            if (finished.incrementAndGet() >= task.totalSegments) mergeSegments(task, gen);
                        }
                    });
            if (cancelled.get() || !isCurrent(task.id, gen)) return;
        }
    }

    /** 取（或新建）该任务专属的分片线程池。 */
    private ExecutorService poolFor(DownloadTask task) {
        ExecutorService pool = segmentPools.get(task.id);
        if (pool == null || pool.isShutdown()) {
            pool = Executors.newFixedThreadPool(DEFAULT_PARALLEL);
            segmentPools.put(task.id, pool);
        }
        return pool;
    }

    /** 停掉本轮：置中断标记 + 关掉专属线程池（排队中的 runnable 一并丢弃）。
     *  不动 generation，这样最后的收尾回调仍能正常判定成败。 */
    private void stopRound(String taskId) {
        AtomicBoolean cancelled = cancelFlags.get(taskId);
        if (cancelled != null) cancelled.set(true);
        ExecutorService pool = segmentPools.remove(taskId);
        if (pool != null) pool.shutdownNow();
    }

    /** 作废当前轮次：升代（丢弃旧回调）+ 停掉本轮。 */
    private void abortRound(String taskId) {
        nextGeneration(taskId);
        stopRound(taskId);
    }

    /** 任务结束（完成/失败）后回收线程池。放到 taskExecutor 上异步执行，
     *  避免在分片线程里 shutdownNow 把自己也中断掉。 */
    private void releasePool(String taskId) {
        try {
            taskExecutor.submit(() -> {
                ExecutorService pool = segmentPools.remove(taskId);
                if (pool != null) pool.shutdownNow();
            });
        } catch (RejectedExecutionException ignored) {
            // 管理器已 shutdown，线程池随 shutdown() 一起清理
        }
    }

    private void mergeSegments(DownloadTask task, int gen) {
        if (!isCurrent(task.id, gen)) return;   // 旧一轮的收尾回调，忽略
        if (task.status == DownloadTask.Status.CANCELLED || task.status == DownloadTask.Status.PAUSED) return;
        boolean allDone = true;
        for (DownloadTask.FileSegment s : task.segments) if (!s.done) { allDone = false; break; }
        if (!allDone) {
            // 失败：保留 .part 文件与任务记录，便于「重试」优先续传
            String firstError = null;
            for (DownloadTask.FileSegment s : task.segments) {
                if (!s.done && s.errorMsg != null) { firstError = s.errorMsg; break; }
            }
            fail(task, firstError != null ? "Segment failed: " + firstError : "Some segments failed");
            return;
        }
        File output = new File(new File(task.outputDir), task.name + "." + task.ext);
        try {
            writeMerged(task, output);
            deleteDir(partDir(task));
            // 全部分片都是 0 字节（典型：URL 失效/403/被限速），合并出来的是空文件 —— 判定失败，不要报完成
            if (task.totalSegments > 0 && output.length() == 0) {
                output.delete();
                fail(task, "Empty output: all segments are 0 bytes");
                return;
            }
            task.status = DownloadTask.Status.COMPLETED;
            task.downloadedBytes = output.length();
            releasePool(task.id);
            saveAsync();
            if (listener != null) listener.onTaskCompleted(task, output);
        } catch (IOException e) {
            fail(task, e.getMessage());
        }
    }

    private void writeMerged(DownloadTask task, File output) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output), 65536)) {
            for (DownloadTask.FileSegment seg : task.segments) {
                File part = new File(seg.partFile);
                if (!part.exists()) throw new IOException("Missing segment: " + seg.partFile);
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(part), 65536)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                }
                part.delete();
            }
        }
    }

    /** 暂停：中断在途分片读取，保留已下载的 .part 文件，任务留在列表中。 */
    public void pauseTask(String taskId) {
        DownloadTask t = tasks.get(taskId);
        if (t == null) return;
        // 初始化阶段（PENDING，正在解析 m3u8 / 探测大小）也要能暂停，否则暂停会被静默忽略
        if (t.status != DownloadTask.Status.RUNNING && t.status != DownloadTask.Status.PENDING) return;
        t.status = DownloadTask.Status.PAUSED;
        abortRound(taskId);
        saveAsync();
        if (listener != null) listener.onTaskPaused(t);
    }

    /** 继续：从已有 .part 断点续传未完成的分片。 */
    public void resumeTask(String taskId) {
        DownloadTask t = tasks.get(taskId);
        if (t == null || t.status != DownloadTask.Status.PAUSED) return;
        t.status = DownloadTask.Status.RUNNING;
        clearSegmentErrors(t);
        saveAsync();
        ensureService();   // 进程重启后服务未必在，补启一个用于显示进度通知
        if (listener != null) listener.onTaskResumed(t);
        taskExecutor.submit(() -> restart(t));
    }

    /** 失败重试：优先续传（保留 .part），仅对未完成分片重新请求。 */
    public void retryTask(String taskId) {
        DownloadTask t = tasks.get(taskId);
        if (t == null || t.status != DownloadTask.Status.FAILED) return;
        t.errorMsg = null;
        t.status = DownloadTask.Status.RUNNING;
        clearSegmentErrors(t);
        saveAsync();
        ensureService();
        if (listener != null) listener.onTaskResumed(t);
        taskExecutor.submit(() -> restart(t));
    }

    /** 进程重启后 DownloadService 不一定还活着，这里只把它拉起来做通知宿主，
     *  不携带 name/url，因此不会重复创建任务。 */
    private void ensureService() {
        try {
            if (listener != null) return;   // 服务已在，通知正常
            DownloadService.attach(App.get());
        } catch (Throwable ignored) {
            // 服务起不来不影响下载本身
        }
    }

    private void restart(DownloadTask task) {
        try {
            if (task.segments == null || task.segments.length == 0) initAndStart(task);
            else {
                // 清掉旧版本留下的 0B .part：它们会让续传误以为"已下过"，也会让合并出一个空文件
                cleanEmptyParts(task);
                // 续传时先把已落地的字节数回填，避免恢复后 UI 在首个进度回调到来前显示旧值
                task.downloadedBytes = sumSegmentBytes(task);
                runSegments(task);
            }
        } catch (Exception e) {
            Log.e(TAG, "resume failed", e);
            fail(task, e.getMessage());
        }
    }

    /** 删除未完成分片里长度为 0 的 .part 文件，并复位其下载计数。 */
    private void cleanEmptyParts(DownloadTask task) {
        if (task.segments == null) return;
        for (DownloadTask.FileSegment s : task.segments) {
            if (s.done) continue;
            File part = new File(s.partFile);
            if (!part.exists()) continue;
            if (part.length() == 0) {
                part.delete();
                s.downloaded = 0;
            } else if (s.downloaded == 0) {
                // 有数据但计数丢了（进程被杀过）：按实际文件大小回填，保证续传起点正确
                s.downloaded = part.length();
            }
        }
    }

    /** 累加各分片已下载字节（含断点续传已有的部分）。 */
    private long sumSegmentBytes(DownloadTask task) {
        if (task.segments == null) return 0;
        long total = 0;
        for (DownloadTask.FileSegment x : task.segments) total += x.downloaded;
        return total;
    }

    private void clearSegmentErrors(DownloadTask task) {
        if (task.segments != null) for (DownloadTask.FileSegment s : task.segments) s.errorMsg = null;
    }

    private void fail(DownloadTask task, String msg) {
        task.status = DownloadTask.Status.FAILED;
        task.errorMsg = msg;
        releasePool(task.id);   // 失败后不留残余线程继续写文件
        saveAsync();
        if (listener != null) listener.onTaskFailed(task, msg);
    }

    public void cancelTask(String taskId) {
        DownloadTask t = tasks.remove(taskId);
        if (t == null) return;
        t.status = DownloadTask.Status.CANCELLED;
        // 先作废本轮（撤销排队任务 + 置中断标记 + 关掉线程池），最后才删目录。
        // 顺序反了的话，排队中的分片会在目录被删之后才被执行，把 .part 重新建出来
        abortRound(taskId);
        cancelFlags.remove(taskId);
        runGenerations.remove(taskId);
        deleteDir(partDir(t));   // 使用任务自己的分片目录，与全局设置无关
        saveAsync();             // 取消 = 记录消失，立刻落盘
        if (listener != null) listener.onTaskCancelled(t);
    }

    public DownloadTask getTask(String id) { return tasks.get(id); }
    public List<DownloadTask> getAllTasks() { return new ArrayList<>(tasks.values()); }

    /** 从列表移除任务：清理分片临时目录 + 已产出的成品文件。 */
    public void removeTask(String taskId) {
        DownloadTask t = tasks.remove(taskId);
        if (t == null) return;
        abortRound(taskId);
        cancelFlags.remove(taskId);
        runGenerations.remove(taskId);
        deleteDir(partDir(t));
        // 成品文件的扩展名必须用任务自己的 ext（HLS 现为 mp4），否则会删不到 / 删错文件
        File output = new File(new File(t.outputDir), t.name + "." + t.ext);
        if (output.exists()) output.delete();
        saveAsync();
    }

    /** 正在下载（RUNNING）的任务数。 */
    public int getActiveCount() {
        int count = 0;
        for (DownloadTask t : tasks.values()) if (t.status == DownloadTask.Status.RUNNING) count++;
        return count;
    }

    /** 是否还有未结束的任务（RUNNING/PAUSED/PENDING）；服务停不停自用它判断。 */
    public boolean hasTaskWork() {
        for (DownloadTask t : tasks.values()) {
            if (t.status == DownloadTask.Status.RUNNING || t.status == DownloadTask.Status.PAUSED || t.status == DownloadTask.Status.PENDING) return true;
        }
        return false;
    }

    public void shutdown() {
        for (ExecutorService pool : segmentPools.values()) pool.shutdownNow();
        segmentPools.clear();
        cancelFlags.clear();
        saveNow();               // 收尾前先落盘，再关掉保存用的单线程
        taskExecutor.shutdown();
    }

    // ---------- 持久化：进程重启后仍能管理历史下载 ----------

    /** 首次取实例时恢复一次历史任务（下载管理页因此不会在重进 App 后变空）。 */
    private void restoreIfNeeded() {
        if (restored) return;
        synchronized (this) {
            if (restored) return;
            restored = true;
            restoreNow();
        }
    }

    private void restoreNow() {
        try {
            File file = storeFile();
            if (!file.exists()) return;
            String json = readAll(file);
            if (json == null || json.isEmpty()) return;
            DownloadTask[] arr = App.gson().fromJson(json, DownloadTask[].class);
            if (arr == null) return;
            for (DownloadTask t : arr) {
                if (t == null || t.id == null || t.id.isEmpty()) continue;
                // 进程重启后不自动跑流量：在途任务一律落到暂停态，由用户手动继续
                if (t.status == DownloadTask.Status.RUNNING || t.status == DownloadTask.Status.PENDING) {
                    t.status = DownloadTask.Status.PAUSED;
                }
                if (t.segments == null) t.segments = new DownloadTask.FileSegment[0];
                if (t.headers == null) t.headers = new HashMap<>();
                reconcile(t);
                tasks.put(t.id, t);
                // 暂停态：任何迟到的旧回调都应被丢弃
                cancelFlags.put(t.id, new AtomicBoolean(true));
            }
            Log.i(TAG, "restored " + tasks.size() + " task(s)");
        } catch (Exception e) {
            Log.e(TAG, "restore failed", e);
        }
    }

    /** 用磁盘真实情况校正恢复出来的任务，避免出现"显示已完成但文件没了"或进度虚高。 */
    private void reconcile(DownloadTask t) {
        if (t.status == DownloadTask.Status.COMPLETED) {
            File out = new File(new File(t.outputDir), t.name + "." + t.ext);
            if (out.exists()) {
                t.downloadedBytes = out.length();
                t.completedSegments = t.totalSegments;
            } else {
                // 成品被外部删掉了：退回失败态，让用户可以选择重试
                t.status = DownloadTask.Status.FAILED;
                t.errorMsg = "Output file missing";
            }
            return;
        }
        long sum = 0;
        int done = 0;
        for (DownloadTask.FileSegment s : t.segments) {
            if (s.done) { done++; sum += s.downloaded; continue; }
            File part = new File(s.partFile);
            if (!part.exists()) {
                s.downloaded = 0;             // 分片文件没了，续传只能从头开始
            } else if (s.downloaded <= 0) {
                s.downloaded = part.length(); // 计数丢了就用实际文件大小回填
            }
            sum += s.downloaded;
        }
        t.completedSegments = done;
        t.downloadedBytes = sum;
    }

    /** 节流落盘：分片完成会高频触发，这里合并成最少间隔 SAVE_MIN_INTERVAL 一次。 */
    private void saveAsync() {
        if (!restored) return;
        if (!savePending.compareAndSet(false, true)) return;
        try {
            taskExecutor.submit(this::flushPending);
        } catch (RejectedExecutionException ignored) {
            savePending.set(false);
        }
    }

    private void flushPending() {
        try {
            long wait = SAVE_MIN_INTERVAL - (SystemClock.elapsedRealtime() - lastSaveTime);
            if (wait > 0) Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        savePending.set(false);
        saveNow();
    }

    private void saveNow() {
        try {
            List<DownloadTask> list = new ArrayList<>();
            for (DownloadTask t : tasks.values()) list.add(snapshot(t));
            String json = App.gson().toJson(list);
            File file = storeFile();
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            File tmp = new File(dir, file.getName() + ".tmp");
            writeAll(tmp, json);
            if (!tmp.renameTo(file)) {
                writeAll(file, json);   // rename 失败（跨分区等）时直接覆盖写
            }
            lastSaveTime = SystemClock.elapsedRealtime();
        } catch (Exception e) {
            Log.e(TAG, "save failed", e);
        }
    }

    /** 落盘快照：已完成的任务不存分片明细（不再需要续传，省掉上千条记录）。 */
    private DownloadTask snapshot(DownloadTask t) {
        DownloadTask c = new DownloadTask();
        c.id = t.id;
        c.name = t.name;
        c.type = t.type;
        c.url = t.url;
        c.headers = t.headers != null ? t.headers : new HashMap<>();
        c.ext = t.ext;
        c.totalSize = t.totalSize;
        c.downloadedBytes = t.downloadedBytes;
        c.completedSegments = t.completedSegments;
        c.totalSegments = t.totalSegments;
        c.status = t.status;
        c.errorMsg = t.errorMsg;
        c.partDirPath = t.partDirPath;
        c.outputDir = t.outputDir;
        if (t.segments == null || t.status == DownloadTask.Status.COMPLETED) {
            c.segments = new DownloadTask.FileSegment[0];
            return c;
        }
        DownloadTask.FileSegment[] arr = new DownloadTask.FileSegment[t.segments.length];
        for (int i = 0; i < arr.length; i++) {
            DownloadTask.FileSegment s = t.segments[i];
            DownloadTask.FileSegment d = new DownloadTask.FileSegment();
            d.index = s.index;
            d.url = s.url;
            d.start = s.start;
            d.end = s.end;
            d.downloaded = s.downloaded;
            d.partFile = s.partFile;
            d.done = s.done;
            d.errorMsg = s.errorMsg;
            d.keyBytes = s.keyBytes;
            d.keyIv = s.keyIv;
            arr[i] = d;
        }
        c.segments = arr;
        return c;
    }

    private File storeFile() {
        return new File(new File(App.get().getFilesDir(), STORE_DIR), STORE_FILE);
    }

    private static String readAll(File file) throws IOException {
        long len = file.length();
        if (len <= 0) return "";
        byte[] buf = new byte[(int) len];
        try (FileInputStream in = new FileInputStream(file)) {
            int off = 0, r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        }
    }

    private static void writeAll(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
    }

    // ---------- 工具 ----------
    private int nextGeneration(String taskId) {
        return runGenerations.computeIfAbsent(taskId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private boolean isCurrent(String taskId, int gen) {
        AtomicInteger g = runGenerations.get(taskId);
        return g != null && g.get() == gen;
    }

    private File partDir(DownloadTask task) {
        File d = new File(new File(task.outputDir), ".parts_" + task.id);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f != null) f.delete();
        dir.delete();
    }

    private boolean looksLikeHls(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".m3u8") || lower.contains(".m3u8?") || lower.contains("m3u8");
    }

    private String guessExt(String url) {
        int q = url.indexOf('?');
        String base = q > 0 ? url.substring(0, q) : url;
        int dot = base.lastIndexOf('.');
        if (dot > 0 && dot < base.length() - 5) return base.substring(dot + 1).toLowerCase();
        return "mp4";
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isEmpty()) return "video_" + System.currentTimeMillis();
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** 探测文件总大小 + 是否支持 HTTP Range（用 GET + Range: bytes=0-0，比 HEAD 兼容性好）。 */
    private Probe probe(String url, Map<String, String> headers) {
        Probe p = new Probe();
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) conn.setRequestProperty(h.getKey(), h.getValue());
            }
            conn.setRequestProperty("Range", "bytes=0-0");
            int code = conn.getResponseCode();
            if (code == 206) {
                p.range = true;
                String cr = conn.getHeaderField("Content-Range");
                if (cr != null) {
                    // 形如 bytes 0-0/1234567
                    int slash = cr.indexOf('/');
                    if (slash > 0) try { p.length = Long.parseLong(cr.substring(slash + 1).trim()); } catch (Exception ignored) {}
                }
            } else if (code == 200) {
                String cl = conn.getHeaderField("Content-Length");
                if (cl != null) try { p.length = Long.parseLong(cl.trim()); } catch (Exception ignored) {}
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return p;
    }

    private static final class Probe {
        long length = -1;  // 文件总大小，-1 未知
        boolean range;     // 服务器是否支持 HTTP Range（返回 206）
    }
}
