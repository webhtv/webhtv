package com.fongmi.android.tv.tts;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 「按片段合成音频 → 顺序播放」的在线朗读基类。
 *
 * 对齐 legado HttpReadAloudService 的关键行为，但按 WebHTV 的规模做了收敛：
 * - 每个片段一次请求，结果按 MD5 落盘缓存（{@link TtsAudioCache}），命中即直接播放；
 * - 顺序预取后面 {@link #PREFETCH} 个片段，避免「读完一句等下一句」；
 * - 播放用 {@link MediaPlayer}（片段是几秒到几十秒的 mp3，不需要 media3 的缓存/下载体系）；
 * - 单片段失败重试 {@link #RETRY} 次后跳过该片段；连续 {@link #MAX_CONSECUTIVE_ERRORS}
 *   个片段失败则判定引擎不可用（fatal），避免坏引擎无限空转。
 */
abstract class AudioFileTtsEngine implements TtsEngine {

    private static final String TAG = "TV-tts";
    private static final int PREFETCH = 3;
    private static final int RETRY = 2;
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    /** 同一片段最多容忍的播放失败次数（缓存损坏重下后仍失败就跳过）。 */
    private static final int MAX_PLAY_ERRORS = 2;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService fetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "reader-tts-fetch");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private final java.util.Map<Integer, Integer> playErrorCounts = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 缓存目录必须懒建：{@code id()} 由子类实现，而子类字段（如自定义引擎的 id）在
     * 父类构造期间还没赋值，在构造函数里取 id() 会拿到 null 并把所有引擎的缓存
     * 混进同一个 default 目录。
     */
    private TtsAudioCache cache;

    protected Listener listener;
    protected TtsOptions options = new TtsOptions(1f, 1f, "");

    /**
     * 最近一次合成失败的原因（HTTP 状态码/Content-Type/错误体片段/异常信息）。
     * 在线朗读最容易被吐槽的就是「点了没声音却不知道哪里错」，因此把原因带进 UI 提示。
     */
    protected volatile String lastFetchError = "";

    private List<TtsTextSplitter.Chunk> chunks = Collections.emptyList();
    private MediaPlayer player;
    private int current = -1;
    private int consecutiveErrors;
    private boolean playing;
    private boolean paused;
    private boolean released;
    private String preparedPath = "";

    AudioFileTtsEngine() {
    }

    /** 合成一个片段到目标文件；返回 false 表示失败。实现必须阻塞直到完成或失败。 */
    protected abstract boolean fetchChunk(String text, File target);

    /** 缓存键文本：引擎参数（音色/语速）必须参与，否则换音色会播到旧音频。 */
    protected abstract String cacheKey(String text);

    protected TtsAudioCache cache() {
        TtsAudioCache local = cache;
        if (local == null) {
            synchronized (playErrorCounts) {
                if (cache == null) cache = new TtsAudioCache(TtsEngines.cacheRoot(), id());
                local = cache;
            }
        }
        return local;
    }

    protected Handler mainHandler() {
        return main;
    }

    @Override
    public List<TtsVoice> voices() {
        return Collections.emptyList();
    }

    @Override
    public void prepare(TtsOptions opts, Listener l) {
        this.listener = l;
        if (opts != null) this.options = opts;
    }

    @Override
    public void speak(List<TtsTextSplitter.Chunk> list, int fromIndex) {
        if (released) return;
        chunks = list == null ? Collections.emptyList() : list;
        if (chunks.isEmpty()) {
            if (listener != null) listener.onTtsQueueDone();
            return;
        }
        releasePlayer();
        playing = true;
        paused = false;
        consecutiveErrors = 0;
        playErrorCounts.clear();
        generation.incrementAndGet();
        int start = Math.max(0, Math.min(fromIndex, chunks.size() - 1));
        playFrom(start);
    }

    @Override
    public void pause() {
        if (!playing || paused) return;
        paused = true;
        try {
            if (player != null && player.isPlaying()) player.pause();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "pause failed %s", e.getMessage());
        }
    }

    @Override
    public void resume() {
        if (!playing || !paused) return;
        paused = false;
        MediaPlayer mp = player;
        if (mp != null) {
            try {
                mp.start();
                // 暂停期间改过语速的话在此补上：平台文档说明已 prepared 的播放器
                // 用非零速度调 setPlaybackParams 等价于 start()，因此不会重置进度
                applyRate(mp);
                return;
            } catch (Throwable e) {
                SpiderDebug.log(TAG, "resume failed, replay current %s", e.getMessage());
            }
        }
        playFrom(Math.max(0, current));
    }

    @Override
    public void stop() {
        playing = false;
        paused = false;
        generation.incrementAndGet();
        releasePlayer();
    }

    @Override
    public void setRate(float rate) {
        options = options.withRate(rate);
        MediaPlayer mp = player;
        // 暂停中不能直接改播放器速率：平台文档明确「已 prepared 的播放器用非零速度
        // 调 setPlaybackParams 等价于 start()」，会让界面显示「已暂停」时突然出声。
        // 暂停期间只更新 options，恢复播放时由 resume() 补上（后续片段也会走新语速）。
        if (mp != null && !paused) applyRate(mp);
    }

    /** 把当前语速应用到已 prepared 的播放器。 */
    private void applyRate(MediaPlayer mp) {
        try {
            android.media.PlaybackParams params = mp.getPlaybackParams();
            params.setSpeed(options.rate);
            mp.setPlaybackParams(params);
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "setRate failed %s", e.getMessage());
        }
    }

    @Override
    public void setPitch(float pitch) {
        options = options.withPitch(pitch);
    }

    @Override
    public void setVoice(String voiceId) {
        options = options.withVoice(voiceId);
    }

    @Override
    public void release() {
        released = true;
        stop();
        fetchExecutor.shutdownNow();
    }

    protected boolean isReleased() {
        return released;
    }

    private void playFrom(int index) {
        if (!playing || released) return;
        if (index >= chunks.size()) {
            playing = false;
            if (listener != null) listener.onTtsQueueDone();
            return;
        }
        current = index;
        final int gen = generation.get();
        String text = chunks.get(index).text;
        fetch(index, text, gen, file -> {
            if (!playing || generation.get() != gen) return;
            if (file == null) {
                // 合成失败：记为失败并跳过，连续失败则判定引擎不可用
                consecutiveErrors++;
                if (listener != null) {
                    listener.onTtsError(index, "在线语音合成失败：" + lastFetchError + "，已跳过该段",
                            consecutiveErrors >= MAX_CONSECUTIVE_ERRORS);
                }
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    playing = false;
                    return;
                }
                main.post(() -> playFrom(index + 1));
                return;
            }
            consecutiveErrors = 0;
            startPlayer(file, index, gen);
            prefetch(index + 1, gen);
        });
    }

    /** 取缓存或合成；回调一定在主线程执行。 */
    private void fetch(int index, String text, int gen, FetchResult callback) {
        File file = cache().file(id(), cacheKey(text));
        if (cache().has(file)) {
            main.post(() -> {
                if (generation.get() == gen) callback.onResult(file);
            });
            return;
        }
        fetchExecutor.execute(() -> {
            if (released || generation.get() != gen) return;
            boolean ok = false;
            File target = cache().file(id(), cacheKey(text));
            for (int attempt = 0; attempt <= RETRY && !ok; attempt++) {
                if (released || generation.get() != gen) return;
                try {
                    ok = fetchChunk(text, target) && cache().has(target);
                } catch (Throwable e) {
                    SpiderDebug.log(TAG, "fetch failed engine=%s attempt=%d %s", id(), attempt, e.getMessage());
                    ok = false;
                }
                if (!ok && attempt < RETRY) {
                    try {
                        Thread.sleep(400L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            final boolean success = ok;
            main.post(() -> {
                if (generation.get() == gen) callback.onResult(success ? target : null);
            });
        });
    }

    /** 顺序预取后续片段，填满缓存窗口。 */
    private void prefetch(int fromIndex, int gen) {
        if (!playing || released) return;
        for (int i = fromIndex; i < Math.min(chunks.size(), fromIndex + PREFETCH); i++) {
            final int index = i;
            String text = chunks.get(index).text;
            File file = cache().file(id(), cacheKey(text));
            if (cache().has(file)) continue;
            fetchExecutor.execute(() -> {
                if (released || generation.get() != gen || !playing) return;
                File target = cache().file(id(), cacheKey(text));
                try {
                    if (!cache().has(target)) fetchChunk(text, target);
                } catch (Throwable e) {
                    SpiderDebug.log(TAG, "prefetch failed index=%d %s", index, e.getMessage());
                }
            });
        }
    }

    private void startPlayer(File file, int index, int gen) {
        releasePlayer();
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            preparedPath = file.getAbsolutePath();
            mp.setDataSource(preparedPath);
            mp.setOnPreparedListener(prepared -> {
                if (!playing || generation.get() != gen) {
                    releasePlayer();
                    return;
                }
                try {
                    android.media.PlaybackParams params = prepared.getPlaybackParams();
                    params.setSpeed(options.rate);
                    prepared.setPlaybackParams(params);
                } catch (Throwable e) {
                    SpiderDebug.log(TAG, "apply rate failed %s", e.getMessage());
                }
                if (paused) return;
                prepared.start();
                if (listener != null) listener.onTtsStart(index, chunks.get(index).text);
            });
            mp.setOnCompletionListener(completed -> {
                if (generation.get() != gen) return;
                if (listener != null) listener.onTtsChunkDone(index);
                main.post(() -> playFrom(index + 1));
            });
            mp.setOnErrorListener((errored, what, extra) -> {
                SpiderDebug.log(TAG, "player error index=%d what=%d extra=%d", index, what, extra);
                // 缓存文件可能损坏：删掉缓存重下；同一片段最多重试两次，
                // 否则「下到坏音频 → 播放失败 → 再下」会无限循环。
                File cached = new File(preparedPath);
                //noinspection ResultOfMethodCallIgnored
                cached.delete();
                if (generation.get() != gen) return true;
                int attempts = playErrorCounts.merge(index, 1, Integer::sum);
                main.post(() -> {
                    if (attempts <= MAX_PLAY_ERRORS) {
                        playFrom(index);
                    } else {
                        playErrorCounts.remove(index);
                        if (listener != null) {
                            listener.onTtsError(index, "音频播放失败，已跳过该段", false);
                        }
                        playFrom(index + 1);
                    }
                });
                return true;
            });
            mp.prepareAsync();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "startPlayer failed index=%d %s", index, e.getMessage());
            if (listener != null) listener.onTtsError(index, "音频播放失败：" + e.getMessage(), false);
            main.post(() -> playFrom(index + 1));
        }
    }

    private void releasePlayer() {
        MediaPlayer mp = player;
        player = null;
        preparedPath = "";
        if (mp == null) return;
        try {
            mp.setOnPreparedListener(null);
            mp.setOnCompletionListener(null);
            mp.setOnErrorListener(null);
            mp.reset();
        } catch (Throwable ignore) {
        }
        try {
            mp.release();
        } catch (Throwable ignore) {
        }
    }

    private interface FetchResult {
        void onResult(File file);
    }

    /** 便捷方法：把输入流写入指定文件（供实现类复用）。 */
    protected static boolean storeStream(File target, java.io.InputStream in) {
        try (java.io.InputStream stream = in;
             java.io.OutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = stream.read(buf)) > 0) {
                total += n;
                if (total > 8L * 1024 * 1024) throw new IllegalStateException("audio too large");
                out.write(buf, 0, n);
            }
            out.flush();
            return total > 0;
        } catch (Throwable e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return false;
        }
    }
}
