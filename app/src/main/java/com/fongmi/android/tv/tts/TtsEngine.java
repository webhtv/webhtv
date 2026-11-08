package com.fongmi.android.tv.tts;

import java.util.List;

/**
 * 朗读引擎抽象。
 *
 * 引擎自己持有「朗读片段队列 + 游标」，逐片段回调进度（对齐 legado 让朗读服务
 * 持有 contentList/nowSpeak 的做法）：系统朗读靠 TTS 引擎的 QUEUE_ADD 排队，
 * 在线朗读靠落盘缓存 + 预取排队，上层只关心片段下标。
 */
public interface TtsEngine {

    /** 引擎事件回调（可能来自主线程或工作线程）。 */
    interface Listener {
        /** 开始朗读某个片段。 */
        void onTtsStart(int index, String text);

        /** 某个片段朗读完成。 */
        void onTtsChunkDone(int index);

        /** 整个队列读完（章节读完）。 */
        void onTtsQueueDone();

        /** 出错；fatal=true 表示引擎已不可用，需要上层停止朗读。 */
        void onTtsError(int index, String message, boolean fatal);

        /** 音色列表异步就绪（系统引擎初始化完成后才有）。 */
        void onTtsVoices(List<TtsVoice> voices);
    }

    String id();

    /** 引擎在当前设备/配置下是否可用（不含网络可达性，网络失败在朗读时上报）。 */
    boolean available();

    /** 不可用的原因或补充说明，用于 UI 提示。 */
    String detail();

    /** 可用音色；可能为空。 */
    List<TtsVoice> voices();

    /** 初始化并准备朗读（系统引擎是异步初始化）。 */
    void prepare(TtsOptions options, Listener listener);

    /** 从 fromIndex 开始朗读整个队列。 */
    void speak(List<TtsTextSplitter.Chunk> chunks, int fromIndex);

    void pause();

    void resume();

    void stop();

    void setRate(float rate);

    void setPitch(float pitch);

    void setVoice(String voiceId);

    /** 释放资源，之后不可再用。 */
    void release();
}
