package com.fongmi.android.tv.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;

/**
 * 朗读引擎注册表：构造内置引擎、保存/读取用户配置的自定义在线引擎。
 *
 * 内置音源（均不需要申请 Key）：
 * - {@code system}：设备自带 TTS 引擎（Android TextToSpeech），离线可用、质量取决于系统语音包；
 * - {@code edge}：微软 Edge 大声朗读协议（Neural 音色，质量最好，依赖网络）；
 * - {@code baidu}：百度翻译发音接口（轻量、低延迟，依赖网络）。
 *
 * 另有用户自定义在线引擎（legado 规则子集），配置持久化在 SharedPreferences。
 */
public final class TtsEngines {

    private static final String TAG = "TV-tts";
    public static final String SOURCE_SYSTEM = "system";
    public static final String SOURCE_EDGE = "edge";
    public static final String SOURCE_BAIDU = "baidu";
    public static final String SOURCE_CUSTOM = "custom";

    private static final String PREF = "reader_tts";
    private static final String KEY_CUSTOM = "custom_engine";

    private TtsEngines() {
    }

    public static File cacheRoot() {
        File dir = new File(App.get().getCacheDir(), "tts");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static TtsEngine create(Context context, String source, TtsEngineConfig custom) {
        String id = source == null ? SOURCE_SYSTEM : source.trim();
        switch (id) {
            case SOURCE_EDGE:
                return new EdgeTtsEngine();
            case SOURCE_BAIDU:
                return new BaiduTtsEngine();
            case SOURCE_CUSTOM:
                return new HttpTtsEngine(custom);
            case SOURCE_SYSTEM:
            default:
                return new SystemTtsEngine(context);
        }
    }

    /** 是否有系统 TTS 引擎可用（同步探测，供 UI 提前给出提示）。 */
    public static boolean systemAvailable(Context context) {
        try {
            return !context.getPackageManager()
                    .queryIntentServices(new android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                    .isEmpty();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "systemAvailable failed %s", e.getMessage());
            return false;
        }
    }

    /** 内置音源的静态音色表（系统与百度音色需运行时探测，返回空表）。 */
    public static java.util.List<TtsVoice> staticVoices(String source) {
        if (SOURCE_EDGE.equals(source)) {
            java.util.List<TtsVoice> list = new java.util.ArrayList<>();
            for (String[] voice : EdgeTtsEngine.VOICES) {
                list.add(new TtsVoice(voice[0], voice[1], "zh-CN"));
            }
            return list;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 探测系统引擎的音色列表（异步）。
     *
     * TextToSpeech 的音色必须在初始化完成后才能读取，因此这里建一个临时实例，
     * 拿到音色后立即释放。仅在设备没有系统引擎时同步回调空表。
     *
     * 注意：临时实例与正在朗读的实例并存时，部分设备会互相干扰，
     * 因此调用方（朗读面板）只在用户切到系统音源时探测一次。
     */
    public static void probeSystemVoices(Context context, java.util.function.Consumer<java.util.List<TtsVoice>> callback) {
        if (callback == null) return;
        if (!systemAvailable(context)) {
            callback.accept(java.util.Collections.emptyList());
            return;
        }
        final SystemTtsEngine engine = new SystemTtsEngine(context.getApplicationContext());
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable timeout = () -> finishProbe(done, engine, callback, java.util.Collections.emptyList());
        engine.prepare(new TtsOptions(1f, 1f, ""), new TtsEngine.Listener() {
            @Override
            public void onTtsStart(int index, String text) {
            }

            @Override
            public void onTtsChunkDone(int index) {
            }

            @Override
            public void onTtsQueueDone() {
            }

            @Override
            public void onTtsError(int index, String message, boolean fatal) {
                SpiderDebug.log(TAG, "probeSystemVoices error %s", message);
                handler.removeCallbacks(timeout);
                finishProbe(done, engine, callback, java.util.Collections.emptyList());
            }

            @Override
            public void onTtsVoices(java.util.List<TtsVoice> voices) {
                handler.removeCallbacks(timeout);
                finishProbe(done, engine, callback, voices);
            }
        });
        handler.postDelayed(timeout, 8000L);
    }

    private static void finishProbe(java.util.concurrent.atomic.AtomicBoolean done, SystemTtsEngine engine,
                                    java.util.function.Consumer<java.util.List<TtsVoice>> callback,
                                    java.util.List<TtsVoice> voices) {
        if (!done.compareAndSet(false, true)) return;
        try {
            callback.accept(voices == null ? java.util.Collections.emptyList() : voices);
        } finally {
            try {
                engine.release();
            } catch (Throwable ignore) {
            }
        }
    }

    public static TtsEngineConfig loadCustomConfig() {
        String json = App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_CUSTOM, "");
        if (json == null || json.isEmpty()) return null;
        return TtsEngineConfig.fromJson(json);
    }

    public static void saveCustomConfig(TtsEngineConfig config) {
        App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM, config == null ? "" : config.toJson()).apply();
    }
}
