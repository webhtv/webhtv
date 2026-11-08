package com.fongmi.android.tv.tts;

import androidx.annotation.Nullable;

import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 通用在线朗读引擎：按 legado 兼容的 URL 规则请求音频流。
 *
 * 同时用于内置的百度在线语音（{@link BaiduTtsEngine}）与用户自定义引擎
 * （规则来自 {@link TtsEngineConfig}，见 {@link TtsHttpRule} 支持的模板子集）。
 */
class HttpTtsEngine extends AudioFileTtsEngine {

    private static final String TAG = "TV-tts";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final TtsEngineConfig config;
    private final TtsHttpRule rule;
    private final String engineId;

    HttpTtsEngine(TtsEngineConfig config) {
        this(config, TtsEngines.SOURCE_CUSTOM);
    }

    HttpTtsEngine(TtsEngineConfig config, String engineId) {
        this.config = config;
        this.engineId = engineId;
        this.rule = config == null ? null : TtsHttpRule.parse(config.url, config.contentType);
    }

    @Override
    public String id() {
        return engineId;
    }

    @Override
    public boolean available() {
        return rule != null && rule.isUsable();
    }

    @Override
    public String detail() {
        if (config == null) return "未配置在线朗读引擎";
        if (rule == null || !rule.isUsable()) {
            return rule == null ? "引擎规则无法解析" : rule.unsupportedReason();
        }
        return config.displayName();
    }

    @Override
    protected String cacheKey(String text) {
        // 参数（音色/语速）参与键，避免改设置后播到旧音频
        return engineId + "|" + options.rate + "|" + options.voice + "|" + text;
    }

    @Override
    protected boolean fetchChunk(String text, File target) {
        if (!available()) {
            lastFetchError = detail();
            return false;
        }
        int speed = speakSpeed(options.rate);
        String url = rule.url(text, speed);
        String method = rule.method();
        String body = rule.body(text, speed);
        try {
            Request.Builder builder = new Request.Builder().url(url);
            if (config != null && config.header != null && !config.header.trim().isEmpty()) {
                applyHeader(builder, config.header);
            }
            if (TtsHttpRule.METHOD_POST.equals(method)) {
                RequestBody requestBody = RequestBody.create(
                        body == null ? "" : body,
                        MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"));
                builder.post(requestBody);
            } else {
                builder.get();
            }
            try (Response response = CLIENT.newCall(builder.build()).execute()) {
                ResponseBody responseBody = response.body();
                if (!response.isSuccessful() || responseBody == null) {
                    lastFetchError = "HTTP " + response.code();
                    SpiderDebug.log(TAG, "http tts bad response code=%d", response.code());
                    return false;
                }
                String contentType = response.header("Content-Type", "");
                if (!looksLikeAudio(contentType)) {
                    // 引擎改成返回错误文本时，读一小段便于用户诊断（对齐 legado 的错误体提示）
                    String preview = peek(responseBody);
                    lastFetchError = "返回 " + contentType + "：" + preview;
                    SpiderDebug.log(TAG, "http tts non-audio contentType=%s body=%s", contentType, preview);
                    return false;
                }
                try (InputStream in = responseBody.byteStream()) {
                    boolean stored = storeStream(target, in);
                    if (!stored) lastFetchError = "音频写入缓存失败";
                    return stored;
                }
            }
        } catch (Throwable e) {
            lastFetchError = e.getClass().getSimpleName() + " " + (e.getMessage() == null ? "" : e.getMessage());
            SpiderDebug.log(TAG, "http tts failed %s", e.getMessage());
            return false;
        }
    }

    private static boolean looksLikeAudio(String contentType) {
        if (contentType == null) return false;
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("audio") || ct.startsWith("application/octet-stream")
                || ct.startsWith("application/mpeg") || ct.startsWith("binary/octet-stream");
    }

    private static String peek(ResponseBody body) {
        try {
            InputStream in = body.byteStream();
            byte[] buf = new byte[256];
            int n = in.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, "UTF-8");
        } catch (Throwable e) {
            return "";
        }
    }

    private static void applyHeader(Request.Builder builder, String header) {
        // legado 的 header 字段允许是 JSON 或直接粘贴的 "Key: Value" 多行文本
        String text = header.trim();
        com.google.gson.JsonObject json = TtsHttpRule.parseObject(text);
        if (json != null) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : json.entrySet()) {
                if (e.getValue() == null || e.getValue().isJsonNull()) continue;
                builder.header(e.getKey(), e.getValue().getAsString());
            }
            return;
        }
        for (String line : text.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            builder.header(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
    }

    /** 语速映射：朗读面板的 0.5x–2.0x → 引擎自己的速度刻度。 */
    protected int speakSpeed(float rate) {
        return Math.max(1, Math.min(7, Math.round(rate * 5f)));
    }

    @Nullable
    static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Throwable e) {
            return null;
        }
    }
}
