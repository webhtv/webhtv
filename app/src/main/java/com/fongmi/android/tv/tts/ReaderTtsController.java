package com.fongmi.android.tv.tts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 小说朗读编排器（对齐 legado BaseReadAloudService 的职责，去掉与正文分页模型的耦合）。
 *
 * 负责：段落 → 朗读片段切分、引擎选择与生命周期、段落游标、上一段/下一段、
 * 定时停止、状态广播。真正的语音合成/播放交给 {@link TtsEngine} 实现。
 *
 * 之所以把编排放在原生侧（而不是留在 reader.html 的 JS 里）：
 * 切后台后 WebView 的 JS 计时器会被系统挂起，而朗读必须继续；
 * 同时通知栏的上一段/下一段也要能越过 JS 直接驱动队列。
 */
public final class ReaderTtsController implements TtsEngine.Listener {

    private static final String TAG = "TV-tts";

    public static final String STATE_PLAYING = "playing";
    public static final String STATE_PAUSED = "paused";
    public static final String STATE_STOPPED = "stopped";
    public static final String STATE_ERROR = "error";

    /** 面向 UI（Activity / 通知）的状态回调。 */
    public interface Listener {
        /** @param paragraph 当前段落下标（0 基），-1 表示无。 */
        void onTtsState(String state, int paragraph, int total, String message, String title);

        void onTtsParagraph(int paragraph, int total, String text);

        /** 本章朗读完毕，由 UI 决定是否续读下一章。 */
        void onTtsChapterEnd();

        void onTtsVoices(List<TtsVoice> voices);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (timerMinutes <= 0) return;
            if (STATE_PLAYING.equals(state)) {
                timerMinutes--;
                if (timerMinutes <= 0) {
                    timerMinutes = 0;
                    stop("定时朗读结束");
                    return;
                }
                notifyState("朗读定时剩余 " + timerMinutes + " 分钟");
            }
            handler.postDelayed(this, 60000L);
        }
    };

    private final Context context;
    private TtsEngine engine;
    private Listener listener;
    private List<TtsTextSplitter.Chunk> chunks = Collections.emptyList();
    private int paragraphCount;
    private int currentChunk = -1;
    private String state = STATE_STOPPED;
    private String title = "";
    private String message = "";
    private int timerMinutes;
    private String sourceId = TtsEngines.SOURCE_SYSTEM;
    private long startedAt;

    public ReaderTtsController(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public String state() {
        return state;
    }

    public int currentParagraph() {
        if (currentChunk < 0 || currentChunk >= chunks.size()) return -1;
        return chunks.get(currentChunk).paragraph;
    }

    public int paragraphCount() {
        return paragraphCount;
    }

    public boolean isPlaying() {
        return STATE_PLAYING.equals(state);
    }

    public boolean isActive() {
        return STATE_PLAYING.equals(state) || STATE_PAUSED.equals(state);
    }

    public int timerMinutes() {
        return timerMinutes;
    }

    public String sourceId() {
        return sourceId;
    }

    /** 已朗读时长（毫秒），用于通知/状态展示。 */
    public long elapsed() {
        return startedAt <= 0 ? 0 : SystemClock.elapsedRealtime() - startedAt;
    }

    /**
     * 开始朗读。
     *
     * @param source     音源 id：{@code system}/{@code edge}/{@code baidu}/{@code custom}
     * @param options    语速/音调/音色
     * @param customRule 自定义在线引擎（source=custom 时使用）
     * @param paragraphs 段落文本（阅读器正文，按 DOM 顺序）
     * @param startIndex 起始段落下标（0 基）
     * @param timer      定时分钟数，0 表示不定时
     * @param trackTitle 书名/章节名，用于通知
     */
    public void start(String source, TtsOptions options, TtsEngineConfig customRule,
                      List<String> paragraphs, int startIndex, int timer, String trackTitle) {
        stop(null);
        sourceId = source == null || source.isEmpty() ? TtsEngines.SOURCE_SYSTEM : source;
        title = trackTitle == null ? "" : trackTitle;
        paragraphCount = paragraphs == null ? 0 : paragraphs.size();
        if (paragraphCount == 0) {
            setState(STATE_ERROR, "没有可朗读的内容");
            return;
        }
        engine = TtsEngines.create(context, sourceId, customRule);
        if (engine instanceof HttpTtsEngine && (!engine.available())) {
            setState(STATE_ERROR, engine.detail());
            releaseEngine();
            return;
        }
        if (sourceId.equals(TtsEngines.SOURCE_SYSTEM) && !engine.available()) {
            setState(STATE_ERROR, engine.detail());
            releaseEngine();
            return;
        }
        // 系统引擎自己按标点断句更自然；在线引擎按句请求，首句更快出声且便于预取
        int maxChars = TtsEngines.SOURCE_SYSTEM.equals(sourceId) ? 0 : TtsTextSplitter.MAX_CHUNK;
        chunks = TtsTextSplitter.split(paragraphs, maxChars);
        if (chunks.isEmpty()) {
            setState(STATE_ERROR, "没有可朗读的内容");
            releaseEngine();
            return;
        }
        int start = Math.max(0, Math.min(startIndex, paragraphCount - 1));
        currentChunk = firstChunkOfParagraph(start);
        timerMinutes = Math.max(0, Math.min(timer, 600));
        startedAt = SystemClock.elapsedRealtime();
        SpiderDebug.log(TAG, "start source=%s paragraphs=%d chunks=%d start=%d timer=%d",
                sourceId, paragraphCount, chunks.size(), start, timerMinutes);
        engine.prepare(options, this);
        engine.speak(chunks, currentChunk);
        setState(STATE_PLAYING, null);
        scheduleTimer();
    }

    public void pause() {
        if (!STATE_PLAYING.equals(state)) return;
        if (engine != null) engine.pause();
        setState(STATE_PAUSED, null);
    }

    public void resume() {
        if (!STATE_PAUSED.equals(state)) return;
        if (engine != null) engine.resume();
        setState(STATE_PLAYING, null);
        scheduleTimer();
    }

    public void toggle() {
        if (STATE_PLAYING.equals(state)) pause();
        else if (STATE_PAUSED.equals(state)) resume();
    }

    /** 上一段：回到当前段起点；已在段首则退到上一段。 */
    public void previous() {
        if (!isActive() || chunks.isEmpty()) return;
        int paragraph = currentParagraph();
        int firstOfCurrent = firstChunkOfParagraph(paragraph);
        int target = currentChunk > firstOfCurrent ? firstOfCurrent : firstChunkOfParagraph(Math.max(0, paragraph - 1));
        seekChunk(target);
    }

    /** 下一段：跳到下一段起点；没有下一段则视为本章读完。 */
    public void next() {
        if (!isActive() || chunks.isEmpty()) return;
        int paragraph = currentParagraph();
        int target = firstChunkOfParagraph(paragraph + 1);
        if (target < 0 || target >= chunks.size() || chunks.get(target).paragraph <= paragraph) {
            handleQueueDone();
            return;
        }
        seekChunk(target);
    }

    /** 跳到指定段落。 */
    public void seekParagraph(int paragraph) {
        if (!isActive() || chunks.isEmpty()) return;
        if (paragraph < 0 || paragraph >= paragraphCount) return;
        seekChunk(firstChunkOfParagraph(paragraph));
    }

    public void setRate(float rate) {
        if (engine != null) engine.setRate(rate);
    }

    public void setPitch(float pitch) {
        if (engine != null) engine.setPitch(pitch);
    }

    public void setVoice(String voice) {
        if (engine == null) return;
        engine.setVoice(voice);
        // 换音色后从当前段重新合成/朗读
        if (isActive() && !chunks.isEmpty()) seekChunk(firstChunkOfParagraph(Math.max(0, currentParagraph())));
    }

    /** 设置定时的绝对分钟数（0 表示取消）。 */
    public void setTimer(int minutes) {
        timerMinutes = Math.max(0, Math.min(minutes, 600));
        notifyState(timerMinutes > 0 ? "朗读定时 " + timerMinutes + " 分钟" : null);
        scheduleTimer();
    }

    /** 在现有定时上追加（通知栏「+30 分钟」），上限 3 小时。 */
    public void addTimer(int delta) {
        setTimer(Math.min(180, timerMinutes + Math.max(0, delta)));
    }

    public void stop(String reason) {
        handler.removeCallbacks(timerTick);
        if (engine != null) {
            engine.stop();
        }
        releaseEngine();
        chunks = Collections.emptyList();
        currentChunk = -1;
        startedAt = 0;
        if (STATE_STOPPED.equals(state) && reason == null) return;
        setState(STATE_STOPPED, reason);
    }

    /** 用户退出阅读页：停播并释放全部资源。 */
    public void release() {
        stop(null);
        listener = null;
    }

    private void releaseEngine() {
        if (engine == null) return;
        try {
            engine.release();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "engine release failed %s", e.getMessage());
        }
        engine = null;
    }

    private void seekChunk(int index) {
        if (engine == null || chunks.isEmpty()) return;
        if (index < 0 || index >= chunks.size()) return;
        currentChunk = index;
        engine.speak(chunks, index);
        setState(STATE_PLAYING, null);
        scheduleTimer();
    }

    private int firstChunkOfParagraph(int paragraph) {
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.get(i).paragraph >= paragraph) return i;
        }
        return -1;
    }

    private void scheduleTimer() {
        handler.removeCallbacks(timerTick);
        if (timerMinutes > 0) handler.postDelayed(timerTick, 60000L);
    }

    private void setState(String newState, String info) {
        state = newState;
        message = info == null ? "" : info;
        if (STATE_STOPPED.equals(newState)) {
            startedAt = 0;
            timerMinutes = 0;
        }
        notifyState(null);
    }

    private void notifyState(String info) {
        if (listener == null) return;
        listener.onTtsState(state, currentParagraph(), paragraphCount,
                info != null ? info : message, title);
    }

    /**
     * 本章读完。
     *
     * 必须先发「章末」再落 STOPPED：阅读页靠这个事件决定是否续读下一章，
     * 而它判断「当前是否在读」依据的是 state；若先变 stopped，续读会被误判成
     * 「用户已停止」而丢弃，表现为读完一章就彻底停下。
     */
    private void handleQueueDone() {
        handler.removeCallbacks(timerTick);
        if (engine != null) engine.stop();
        currentChunk = -1;
        if (listener != null) listener.onTtsChapterEnd();
        setState(STATE_STOPPED, null);
    }

    /* ---------------- TtsEngine.Listener ---------------- */

    /**
     * 引擎回调统一切到主线程。
     *
     * 系统 TTS 的 UtteranceProgressListener、MediaPlayer 的完成回调虽然多在主线程，
     * 但 HTTP 合成失败/超时等路径可能从工作线程直接回调；而回调链路下游要做
     * startForeground/stopSelf、通知更新与 WebView evaluateJavascript，都要求主线程语义。
     */
    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else handler.post(action);
    }

    @Override
    public void onTtsStart(int index, String text) {
        onMain(() -> handleStart(index, text));
    }

    private void handleStart(int index, String text) {
        currentChunk = index;
        if (listener == null) return;
        int paragraph = index >= 0 && index < chunks.size() ? chunks.get(index).paragraph : -1;
        listener.onTtsParagraph(paragraph, paragraphCount, text);
        notifyState(null);
    }

    @Override
    public void onTtsChunkDone(int index) {
        // 进度推进以 onTtsStart 为准（引擎已在下一次 start 里给出新下标）
    }

    @Override
    public void onTtsQueueDone() {
        onMain(this::handleQueueDone);
    }

    @Override
    public void onTtsError(int index, String errorMessage, boolean fatal) {
        onMain(() -> handleError(index, errorMessage, fatal));
    }

    @Override
    public void onTtsVoices(List<TtsVoice> voices) {
        onMain(() -> {
            if (listener != null) listener.onTtsVoices(new ArrayList<>(voices));
        });
    }

    private void handleError(int index, String errorMessage, boolean fatal) {
        SpiderDebug.log(TAG, "engine error index=%d fatal=%b %s", index, fatal, errorMessage);
        if (fatal) {
            setState(STATE_ERROR, errorMessage);
            releaseEngine();
            chunks = Collections.emptyList();
            currentChunk = -1;
        } else {
            message = errorMessage == null ? "" : errorMessage;
            notifyState(null);
        }
    }

}
