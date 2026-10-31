package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.net.OkHttp;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 插件网络桥：把脚本里的 {@code webhtv.request(...)} 落到宿主的 HTTP 客户端上。
 *
 * <p><b>为什么不放域名白名单。</b>这是已定裁定（设计文档 §8 D2）：插件是用户自己装的外部脚本，
 * 与站源、字幕源同级信任；给它列白名单只会让豆瓣、TMDB 这类需要多域名的场景装不上，
 * 而真正的风险（插件偷 token）白名单也挡不住——token 就是用户填给自己插件的。因此本层的
 * 安全边界是**可观测**而非**可阻止**：每一次请求都写 {@link PluginAuditLog}（丢弃 query 与
 * fragment，只留 host + path），出问题能查出插件在跟谁说话。
 *
 * <p><b>硬上限（不是策略，是防呆）。</b>方法限定在 {@link #ALLOWED_METHODS}；响应体截到
 * {@link #MAX_RESPONSE_BYTES}，否则一个视频流 URL 就能把插件进程撑爆；超时由清单
 * {@code runtime.timeout}（1..60 秒）决定，与 {@code PluginManifest} 的校验同源。
 *
 * <p><b>为什么用 {@code OkHttp.client(timeout)} 手工 execute，而不是 {@code OkHttp.string(url)}。</b>
 * 后者把所有异常吞成空串、也拿不到状态码，而审计需要 status 才能区分 403 与超时。
 */
public final class PluginNetBridge {

    /** 允许的方法；不在这里的一律拒绝，避免插件把 CONNECT/TRACE 之类透给客户端。 */
    static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    /** 响应体上限：512KB。插件要的是 JSON 评论/详情，不是视频本体。 */
    static final int MAX_RESPONSE_BYTES = 512 * 1024;
    /** 请求体上限：256KB。 */
    static final int MAX_REQUEST_BYTES = 256 * 1024;
    /** 单插件单次请求的超时上下界，秒。 */
    static final int DEFAULT_TIMEOUT_SECONDS = 15;
    static final int MAX_TIMEOUT_SECONDS = 60;
    static final int MAX_HEADERS = 32;
    static final int MAX_HEADER_LENGTH = 1024;

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.parse("text/plain; charset=utf-8");
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private PluginNetBridge() {
    }

    /**
     * 执行一次请求。
     *
     * @param pluginId 插件 id，仅用于审计归因
     * @param method   HTTP 方法；空则按 GET
     * @param url      完整 http(s) URL
     * @param headers  JSON 对象文本，值必须是字符串；可为 {@code null}
     * @param body     请求体；GET/HEAD 忽略
     * @param timeoutSeconds 清单里的超时，越界回落到 {@link #DEFAULT_TIMEOUT_SECONDS}
     * @return 结果 JSON 文本，形如 {@code {"status":200,"body":"..."}}；
     *         失败时为 {@code {"status":0,"error":"..."}}。**永远不抛异常**——脚本侧不该处理 Java 异常。
     */
    public static String request(@Nullable String pluginId, @Nullable String method, @Nullable String url,
            @Nullable String headers, @Nullable String body, int timeoutSeconds) {
        String verb = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (verb.isEmpty()) verb = "GET";
        if (!ALLOWED_METHODS.contains(verb)) {
            return blocked(pluginId, verb, url, "unsupported method: " + verb);
        }
        String target = url == null ? "" : url.trim();
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return blocked(pluginId, verb, target, "unsupported url scheme");
        }
        int timeout = timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;

        long started = System.currentTimeMillis();
        try {
            Request request = build(verb, target, headers, body);
            try (Response response = OkHttp.client(TimeUnit.SECONDS.toMillis(timeout)).newCall(request).execute()) {
                int status = response.code();
                String text = readCapped(response.body());
                long elapsed = System.currentTimeMillis() - started;
                PluginAuditLog.get().recordRequest(pluginId, null, null, verb, target, status, elapsed,
                        response.isSuccessful() ? "ok" : "error");
                JsonObject result = new JsonObject();
                result.addProperty("status", status);
                result.addProperty("body", text == null ? "" : text);
                return result.toString();
            }
        } catch (IOException | RuntimeException error) {
            long elapsed = System.currentTimeMillis() - started;
            PluginAuditLog.get().recordRequest(pluginId, null, null, verb, target, 0, elapsed, "error");
            return error(describe(error));
        }
    }

    private static Request build(String verb, String url, @Nullable String headers, @Nullable String body)
            throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        applyHeaders(builder, headers);
        if ("GET".equals(verb) || "HEAD".equals(verb)) {
            builder.method(verb, null);
        } else {
            String payload = body == null ? "" : body;
            if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
                throw new IOException("request body exceeds " + MAX_REQUEST_BYTES + " bytes");
            }
            builder.method(verb, RequestBody.create(payload, mediaType(headers)));
        }
        return builder.build();
    }

    /**
     * 内容类型：插件显式给了 Content-Type 就用它，给了 JSON 形状的 body 就按 JSON。
     *
     * <p>不自动嗅探 body 内容——把 {@code {"a":1}} 猜成 JSON 猜错时，对方服务端给的是
     * 400 而不是一个可诊断的错误。
     */
    private static MediaType mediaType(@Nullable String headers) {
        String type = headerValue(headers, "content-type");
        if (type == null || type.isEmpty()) return DEFAULT_MEDIA_TYPE;
        try {
            MediaType parsed = MediaType.parse(type);
            return parsed == null ? DEFAULT_MEDIA_TYPE : parsed;
        } catch (RuntimeException error) {
            return DEFAULT_MEDIA_TYPE;
        }
    }

    private static void applyHeaders(Request.Builder builder, @Nullable String headers) throws IOException {
        if (headers == null || headers.isBlank()) return;
        JsonObject object;
        try {
            JsonElement element = JsonParser.parseString(headers);
            if (!element.isJsonObject()) throw new IOException("headers must be a JSON object");
            object = element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException error) {
            throw new IOException("headers must be a JSON object");
        }
        Set<String> seen = new LinkedHashSet<>();
        int count = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (count++ >= MAX_HEADERS) break;
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            if (name.isEmpty()) continue;
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive()) continue;
            String text = value.getAsString();
            if (text.length() > MAX_HEADER_LENGTH) continue;
            // 重复键会覆盖同名头，统一交给 builder 处理；这里只是防止插件写出非法名字。
            if (!seen.add(name.toLowerCase(Locale.ROOT))) continue;
            try {
                builder.header(name, text);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Nullable
    private static String headerValue(@Nullable String headers, String name) {
        if (headers == null || headers.isBlank()) return null;
        try {
            JsonElement element = JsonParser.parseString(headers);
            if (!element.isJsonObject()) return null;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase(name)) continue;
                JsonElement value = entry.getValue();
                return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * 读响应体并截断。
     *
     * <p>用 {@code byteStream().read} 而不是 {@code string()}：后者会把整个响应先读进内存，
     * 对一个 200MB 的响应体来说，上限就成了摆设。
     */
    @Nullable
    private static String readCapped(@Nullable ResponseBody body) throws IOException {
        if (body == null) return "";
        byte[] buffer = new byte[8 * 1024];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int total = 0;
        int read;
        try (java.io.InputStream stream = body.byteStream()) {
            while ((read = stream.read(buffer)) != -1) {
                int remain = MAX_RESPONSE_BYTES - total;
                if (remain <= 0) break;
                int take = Math.min(read, remain);
                out.write(buffer, 0, take);
                total += take;
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * 明确拒绝一次请求并记账。
     *
     * <p>也不抛异常：脚本里的能力缺失必须以"这次调用返回错误"的形式出现，
     * 否则 {@link PluginHost} 会把整个插件判为故障并禁用——一处能力缺失不该让整个插件下线。
     */
    public static String blocked(@Nullable String pluginId, @Nullable String method, @Nullable String url,
            String reason) {
        try {
            PluginAuditLog.get().recordRequest(pluginId, null, null, method, url, 0, 0, "blocked");
        } catch (Throwable ignored) {
        }
        return error(reason);
    }

    private static String error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", 0);
        result.addProperty("error", message == null ? "request failed" : message);
        return result.toString();
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
