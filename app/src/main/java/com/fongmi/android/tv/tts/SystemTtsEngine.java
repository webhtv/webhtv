package com.fongmi.android.tv.tts;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 系统朗读引擎（Android TextToSpeech）。
 *
 * 对齐 legado TTSReadAloudService：
 * - 首句 SPEC：QUEUE_FLUSH，其余 QUEUE_ADD；
 * - 整段文本交给系统引擎（系统引擎自己按标点断句，比应用层切碎更自然）；
 * - {@code onDone} 推进游标；{@code onError} 跳过当前段继续（单段失败不该中断整章朗读）。
 *
 * 与本设备相关的失败路径：设备可能没有安装任何 TTS 引擎（本机测试设备即是如此），
 * 此时 {@code onInit} 返回 ERROR，引擎上报 fatal 错误，由上层给出可操作提示。
 */
final class SystemTtsEngine implements TtsEngine {

    private static final String TAG = "TV-tts";
    private static final String UTTERANCE_PREFIX = "webhtv-tts-";

    private final Context context;
    private final List<TtsVoice> voices = new ArrayList<>();
    private final AtomicBoolean speaking = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private TextToSpeech textToSpeech;
    private TtsEngine.Listener listener;
    private List<TtsTextSplitter.Chunk> chunks = Collections.emptyList();
    private int current = -1;
    private boolean released;
    private boolean ready;
    private float rate = 1f;
    private float pitch = 1f;
    private String voiceId = "";
    private boolean lastUtteranceQueued;
    /** 队列里已经全部入队，只等最后一段 onDone 触发队列完成。 */
    private boolean queueExhausted;

    SystemTtsEngine(Context context) {
        this.context = context;
    }

    @Override
    public String id() {
        return TtsEngines.SOURCE_SYSTEM;
    }

    @Override
    public boolean available() {
        return TtsEngines.systemAvailable(context);
    }

    @Override
    public String detail() {
        return available() ? "设备系统语音" : "设备未安装系统 TTS 引擎";
    }

    @Override
    public List<TtsVoice> voices() {
        return new ArrayList<>(voices);
    }

    @Override
    public void prepare(TtsOptions options, Listener listener) {
        this.listener = listener;
        if (released) return;
        if (!available()) {
            listener.onTtsError(-1, "设备未安装系统 TTS 引擎，请在系统设置中安装语音包或改用在线音源", true);
            return;
        }
        applyOptions(options);
        // 引擎初始化是异步的；重复 prepare（换音色/改语速）时复用已建好的实例
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status != TextToSpeech.SUCCESS) {
                    SpiderDebug.log(TAG, "system tts init failed status=%d", status);
                    if (listener != null) listener.onTtsError(-1, "系统 TTS 初始化失败", true);
                    return;
                }
                ready = true;
                configure();
                collectVoices();
                if (listener != null) listener.onTtsVoices(voices());
            });
        } else if (ready) {
            configure();
        }
    }

    private void applyOptions(TtsOptions options) {
        if (options == null) return;
        rate = options.rate;
        pitch = options.pitch;
        voiceId = options.voice;
    }

    private void configure() {
        TextToSpeech tts = textToSpeech;
        if (tts == null) return;
        try {
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);
            if (voiceId != null && !voiceId.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Voice target = findVoice(voiceId);
                if (target != null) tts.setVoice(target);
            } else {
                tts.setLanguage(Locale.CHINA);
            }
            tts.setOnUtteranceProgressListener(new Progress());
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "system tts configure failed %s", e.getMessage());
        }
    }

    private Voice findVoice(String id) {
        TextToSpeech tts = textToSpeech;
        if (tts == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null;
        try {
            Set<Voice> all = tts.getVoices();
            if (all == null) return null;
            for (Voice v : all) if (id.equals(v.getName())) return v;
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "findVoice failed %s", e.getMessage());
        }
        return null;
    }

    private void collectVoices() {
        voices.clear();
        TextToSpeech tts = textToSpeech;
        if (tts == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Set<Voice> all = tts.getVoices();
                if (all != null) {
                    for (Voice v : all) {
                        String lang = v.getLocale() == null ? "" : v.getLocale().getLanguage();
                        if (!lang.startsWith("zh")) continue;
                        if (v.isNetworkConnectionRequired() && v.getFeatures() != null
                                && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                            continue;
                        }
                        voices.add(new TtsVoice(v.getName(), v.getName(), v.getLocale().toString()));
                    }
                }
            }
            if (voices.isEmpty()) {
                for (android.speech.tts.TextToSpeech.EngineInfo info : tts.getEngines()) {
                    voices.add(new TtsVoice(info.name, info.label, ""));
                }
            }
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "collectVoices failed %s", e.getMessage());
        }
        voices.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    @Override
    public void speak(List<TtsTextSplitter.Chunk> list, int fromIndex) {
        if (textToSpeech == null || !ready) {
            // 初始化尚未完成：记下请求，初始化回调里不会自动重放，因此明确报错让 UI 提示重试
            if (listener != null) {
                listener.onTtsError(fromIndex, available() ? "系统语音正在初始化，请稍候重试" : "设备未安装系统 TTS 引擎", !available());
            }
            return;
        }
        chunks = list == null ? Collections.emptyList() : list;
        queueExhausted = false;
        lastUtteranceQueued = false;
        paused.set(false);
        speaking.set(true);
        current = Math.max(0, Math.min(fromIndex, chunks.size() - 1));
        try {
            textToSpeech.stop();
        } catch (Throwable ignore) {
        }
        boolean first = true;
        for (int i = current; i < chunks.size(); i++) {
            String text = chunks.get(i).text;
            int mode = first ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            int result = speakChunk(i, text, mode);
            if (result == TextToSpeech.ERROR) {
                SpiderDebug.log(TAG, "system tts speak error index=%d", i);
                if (first && listener != null) {
                    listener.onTtsError(i, "系统语音播报失败", false);
                }
                continue;
            }
            first = false;
        }
        if (first) {
            // 一个片段都没入队
            speaking.set(false);
            if (listener != null) listener.onTtsQueueDone();
            return;
        }
        queueExhausted = true;
        // 立即上报起始进度，UI 不必等引擎回调
        if (listener != null) listener.onTtsStart(current, chunks.get(current).text);
    }

    private int speakChunk(int index, String text, int mode) {
        TextToSpeech tts = textToSpeech;
        if (tts == null) return TextToSpeech.ERROR;
        Bundle params = new Bundle();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return tts.speak(text, mode, params, UTTERANCE_PREFIX + index);
            }
            java.util.HashMap<String, String> legacy = new java.util.HashMap<>();
            legacy.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_PREFIX + index);
            return tts.speak(text, mode, legacy);
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "speakChunk failed index=%d %s", index, e.getMessage());
            return TextToSpeech.ERROR;
        }
    }

    @Override
    public void pause() {
        if (textToSpeech == null || !speaking.get()) return;
        paused.set(true);
        try {
            textToSpeech.stop();
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void resume() {
        if (textToSpeech == null || !speaking.get()) return;
        paused.set(false);
        // 系统引擎的 stop() 会清空队列，恢复时从当前片段重新入队（复读当前段，语义与 legado 一致）
        speak(chunks, Math.max(0, current));
    }

    @Override
    public void stop() {
        speaking.set(false);
        paused.set(false);
        queueExhausted = false;
        try {
            if (textToSpeech != null) textToSpeech.stop();
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void setRate(float value) {
        rate = value;
        try {
            if (textToSpeech != null && ready) textToSpeech.setSpeechRate(value);
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void setPitch(float value) {
        pitch = value;
        try {
            if (textToSpeech != null && ready) textToSpeech.setPitch(value);
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void setVoice(String voice) {
        voiceId = voice == null ? "" : voice;
        configure();
    }

    @Override
    public void release() {
        released = true;
        speaking.set(false);
        try {
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.shutdown();
            }
        } catch (Throwable ignore) {
        }
        textToSpeech = null;
        ready = false;
    }

    private final class Progress extends UtteranceProgressListener {

        @Override
        public void onStart(String utteranceId) {
            int index = indexOf(utteranceId);
            if (index < 0 || index >= chunks.size()) return;
            current = index;
            if (listener != null) listener.onTtsStart(index, chunks.get(index).text);
        }

        @Override
        public void onDone(String utteranceId) {
            int index = indexOf(utteranceId);
            if (index < 0) return;
            if (listener != null) listener.onTtsChunkDone(index);
            if (queueExhausted && index >= chunks.size() - 1) {
                speaking.set(false);
                if (listener != null) listener.onTtsQueueDone();
            }
        }

        @Override
        public void onError(String utteranceId, int errorCode) {
            handleError(utteranceId, "errorCode=" + errorCode);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onError(String utteranceId) {
            handleError(utteranceId, "engine error");
        }

        private void handleError(String utteranceId, String message) {
            int index = indexOf(utteranceId);
            // 单段失败只跳过该段，不中断整章（对齐 legado 的 nextParagraph 行为）
            if (listener != null) listener.onTtsError(index, "系统语音播报失败（" + message + "）", false);
            if (queueExhausted && index >= 0 && index >= chunks.size() - 1) {
                speaking.set(false);
                if (listener != null) listener.onTtsQueueDone();
            }
        }

        private int indexOf(String utteranceId) {
            if (utteranceId == null || !utteranceId.startsWith(UTTERANCE_PREFIX)) return -1;
            try {
                return Integer.parseInt(utteranceId.substring(UTTERANCE_PREFIX.length()));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }
}
