package com.fongmi.android.tv.tts;

import android.os.SystemClock;

import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * 微软 Edge「大声朗读」引擎（免 Key，Neural 音色）。
 *
 * 协议参考社区实现 {@code wangz-code/legado-edge-tts} 的 {@code EdgeSpeakFetch.kt}：
 * 1) 连接 {@code wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1}，
 *    查询参数带 {@code ConnectionId}、{@code Sec-MS-GEC}、{@code Sec-MS-GEC-Version} 与
 *    {@code TrustedClientToken}；
 * 2) {@code Sec-MS-GEC} = SHA-256(「Windows epoch 秒数按 300 秒取整 ×10^7」+ TrustedClientToken) 的大写十六进制；
 * 3) 先发 {@code speech.config} 文本帧，再发 {@code ssml} 文本帧；
 * 4) 服务端以二进制帧返回音频（前 2 字节大端为 header 长度，header 含 {@code X-RequestId}），
 *    以 {@code turn.end} 文本帧结束本轮。
 *
 * 与参考实现的差异：这里按 {@code X-RequestId} 匹配每个待合成片段（参考实现靠「最近一次连接」
 * 复用，句子并发时会串音），并复用同一条 WebSocket 顺序合成，避免每句重新握手带来的首句延迟。
 *
 * 风险：该协议非公开稳定接口，{@code Sec-MS-GEC-Version} 需与 Edge 客户端版本常量同步，
 * 上游可能调整。因此本引擎只作为可选音源，失败时由控制器回退/提示，不影响系统音源可用性。
 */
final class EdgeTtsEngine extends AudioFileTtsEngine {

    private static final String TAG = "TV-tts";

    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String BASE_URL = "wss://speech.platform.bing.com";
    private static final String WSS_PATH = "/consumer/speech/synthesize/readaloud/edge/v1";
    static final String DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";
    private static final String CHROMIUM_FULL_VERSION = "143.0.3650.75";
    private static final String SEC_MS_GEC_VERSION = "1-" + CHROMIUM_FULL_VERSION;
    private static final long WIN_EPOCH_SECONDS = 11644473600L;
    private static final String OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3";

    private static final long CONNECT_TIMEOUT_MS = 12000L;
    private static final long TURN_TIMEOUT_MS = 25000L;

    /** 内置中文音色（Neural），id 直接用于 SSML 的 voice name。 */
    static final String[][] VOICES = {
            {"zh-CN-XiaoxiaoNeural", "晓晓 · 女声（通用）"},
            {"zh-CN-XiaoyiNeural", "晓伊 · 女声（年轻）"},
            {"zh-CN-YunxiNeural", "云希 · 男声（通用）"},
            {"zh-CN-YunjianNeural", "云健 · 男声（浑厚）"},
            {"zh-CN-YunxiaNeural", "云夏 · 童声"},
            {"zh-CN-YunyangNeural", "云扬 · 男声（播音）"},
            {"zh-CN-liaoning-XiaobeiNeural", "晓北 · 东北口音"},
            {"zh-CN-shaanxi-XiaoniNeural", "晓妮 · 陕西方言"},
    };

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final Object lock = new Object();
    private final Map<String, Turn> turns = new LinkedHashMap<>();

    private WebSocket socket;
    private boolean configSent;
    private long lastFailureAt;

    private static final class Turn {
        final String requestId;
        final ByteArrayOutputStream audio = new ByteArrayOutputStream(16384);
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean success;

        Turn(String requestId) {
            this.requestId = requestId;
        }
    }

    @Override
    public String id() {
        return TtsEngines.SOURCE_EDGE;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String detail() {
        return "微软 Edge 语音（Neural 音色，依赖网络）";
    }

    @Override
    public List<TtsVoice> voices() {
        List<TtsVoice> list = new ArrayList<>(VOICES.length);
        for (String[] v : VOICES) list.add(new TtsVoice(v[0], v[1], "zh-CN"));
        return list;
    }

    @Override
    protected String cacheKey(String text) {
        return "edge|" + options.rate + "|" + TtsOptions.pitchOffsetHz(options.pitch) + "|" + voiceId() + "|" + text;
    }

    private String voiceId() {
        return options.voice == null || options.voice.isEmpty() ? DEFAULT_VOICE : options.voice;
    }

    @Override
    protected boolean fetchChunk(String text, File target) {
        String clean = cleanText(text);
        if (clean.isEmpty()) {
            lastFetchError = "该片段没有可朗读内容";
            return false;
        }
        long elapsed = SystemClock.elapsedRealtime();
        if (lastFailureAt > 0 && elapsed - lastFailureAt < 5000L) {
            // 刚失败过：等一小会儿再试，避免连接被拒时把重试全部打满
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        Turn turn = null;
        try {
            if (!ensureConnected()) {
                lastFetchError = "无法连接微软语音服务（网络或协议变更）";
                return false;
            }
            String requestId = connectId();
            turn = new Turn(requestId);
            synchronized (lock) {
                turns.put(requestId, turn);
            }
            if (!sendSsml(requestId, clean)) {
                lastFetchError = "微软语音连接已断开";
                markFailed();
                return false;
            }
            if (!turn.done.await(TURN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                lastFetchError = "微软语音响应超时";
                SpiderDebug.log(TAG, "edge tts turn timeout");
                markFailed();
                return false;
            }
            if (!turn.success || turn.audio.size() == 0) {
                lastFetchError = "微软语音没有返回音频";
                return false;
            }
            boolean stored = storeStream(target, new ByteArrayInputStream(turn.audio.toByteArray()));
            if (!stored) {
                lastFetchError = "音频写入缓存失败";
                SpiderDebug.log(TAG, "edge tts store failed bytes=%d", turn.audio.size());
            }
            return stored;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Throwable e) {
            lastFetchError = e.getClass().getSimpleName() + " " + (e.getMessage() == null ? "" : e.getMessage());
            SpiderDebug.log(TAG, "edge tts failed %s", e.getMessage());
            markFailed();
            return false;
        } finally {
            if (turn != null) {
                synchronized (lock) {
                    turns.remove(turn.requestId);
                }
            }
        }
    }

    private boolean ensureConnected() throws InterruptedException {
        WebSocket current;
        synchronized (lock) {
            current = socket;
        }
        if (current != null) return true;
        CountDownLatch opened = new CountDownLatch(1);
        String url = buildUrl();
        Request request = new Request.Builder().url(url)
                .header("User-Agent", userAgent())
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .build();
        WebSocket created = CLIENT.newWebSocket(request, new SocketListener(opened));
        if (!opened.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            SpiderDebug.log(TAG, "edge tts connect timeout");
            try {
                created.cancel();
            } catch (Throwable ignore) {
            }
            return false;
        }
        synchronized (lock) {
            return socket != null;
        }
    }

    private String buildUrl() {
        String connectionId = connectId();
        return String.format(Locale.US, "%s%s?ConnectionId=%s&Sec-MS-GEC=%s&Sec-MS-GEC-Version=%s&TrustedClientToken=%s",
                BASE_URL, WSS_PATH, connectionId, secMsGec(), SEC_MS_GEC_VERSION, TRUSTED_CLIENT_TOKEN);
    }

    private static String userAgent() {
        String major = CHROMIUM_FULL_VERSION.split("\\.")[0];
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + major + ".0.0.0 Safari/537.36 Edg/" + major + ".0.0.0";
    }

    /** Sec-MS-GEC：Windows epoch 秒数按 300 秒取整后转 100 纳秒刻度，拼 Token 求 SHA-256 大写十六进制。 */
    static String secMsGec() {
        long ticks = System.currentTimeMillis() / 1000L + WIN_EPOCH_SECONDS;
        ticks -= ticks % 300L;
        ticks *= 10_000_000L;
        return sha256(ticks + TRUSTED_CLIENT_TOKEN);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b));
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static String connectId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean sendSsml(String requestId, String text) {
        WebSocket current;
        synchronized (lock) {
            current = socket;
        }
        if (current == null) return false;
        SimpleDateFormat format = new SimpleDateFormat("EEE MMM d yyyy HH:mm:ss 'GMT+0000'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = format.format(new Date());
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>"
                + "<voice name='" + voiceId() + "'>"
                + "<prosody pitch='" + TtsOptions.pitchOffsetHz(options.pitch) + "' rate='" + prosodyRate() + "' volume='+0%'>"
                + escapeXml(text)
                + "</prosody></voice></speak>";
        String message = "X-RequestId:" + requestId
                + "\r\nContent-Type:application/ssml+xml"
                + "\r\nX-Timestamp:" + timestamp + "Z"
                + "\r\nPath:ssml\r\n\r\n" + ssml;
        return current.send(message);
    }

    /** 朗读面板语速（0.5x–2.0x）→ SSML 百分比：1.0x 对应 +0%。 */
    private String prosodyRate() {
        int percent = Math.round((options.rate - 1f) * 100f);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }

    private void sendSpeechConfig(WebSocket target) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String config = "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"" + OUTPUT_FORMAT + "\"}}}}";
        String message = "X-Timestamp:" + format.format(new Date())
                + "\r\nContent-Type:application/json; charset=utf-8"
                + "\r\nPath:speech.config\r\n\r\n" + config + "\r\n";
        target.send(message);
    }

    private void markFailed() {
        lastFailureAt = SystemClock.elapsedRealtime();
        WebSocket current;
        synchronized (lock) {
            current = socket;
            socket = null;
            configSent = false;
            for (Turn turn : turns.values()) turn.done.countDown();
        }
        if (current != null) {
            try {
                current.cancel();
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public void release() {
        markFailed();
        super.release();
    }

    private final class SocketListener extends WebSocketListener {

        private final CountDownLatch opened;

        SocketListener(CountDownLatch opened) {
            this.opened = opened;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            synchronized (lock) {
                socket = webSocket;
                configSent = false;
            }
            sendSpeechConfig(webSocket);
            synchronized (lock) {
                configSent = true;
            }
            opened.countDown();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (text == null || !text.contains("turn.end")) return;
            String requestId = headerValue(text, "X-RequestId");
            Turn turn = findTurn(requestId);
            if (turn == null) return;
            turn.success = turn.audio.size() > 0;
            turn.done.countDown();
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            byte[] data = bytes.toByteArray();
            if (data.length < 2) return;
            int headerLength = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            if (headerLength < 0 || headerLength + 2 > data.length) return;
            String header = new String(data, 2, headerLength, StandardCharsets.UTF_8);
            String path = headerValue(header, "Path");
            if (path != null && !"audio".equals(path)) return;
            Turn turn = findTurn(headerValue(header, "X-RequestId"));
            if (turn == null) return;
            int start = headerLength + 2;
            if (start >= data.length) return;
            turn.audio.write(data, start, data.length - start);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            SpiderDebug.log(TAG, "edge tts closed code=%d", code);
            markFailed();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            SpiderDebug.log(TAG, "edge tts failure %s", t == null ? "unknown" : t.getMessage());
            markFailed();
            opened.countDown();
        }
    }

    private Turn findTurn(String requestId) {
        if (requestId == null) return null;
        synchronized (lock) {
            return turns.get(requestId);
        }
    }

    /** 从协议的 header 段里取值（大小写不敏感，容忍首尾空白）。 */
    private static String headerValue(String raw, String name) {
        if (raw == null) return null;
        for (String line : raw.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            if (name.equalsIgnoreCase(line.substring(0, colon).trim())) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** 去掉朗读引擎不认识的装饰字符，保留中文与常用标点（对齐参考实现的清洗规则）。 */
    static String cleanText(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean keep = Character.isLetterOrDigit(c)
                    || Character.isWhitespace(c)
                    || "，。！？；：、（）《》【】“”‘’…—,.!?;:()[]'\"-".indexOf(c) >= 0;
            if (keep) sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&apos;").replace("\"", "&quot;");
    }
}
