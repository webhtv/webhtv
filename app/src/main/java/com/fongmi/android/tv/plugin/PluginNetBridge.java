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
 * {@link #MAX_RESPONSE_BYTES}，否则一个视频流 URL 就能把插件进程撑爆；超时会先被
 * {@link PluginConfig#timeoutOf} 按 {@link #MAX_TIMEOUT_SECONDS} 钳一次，本类再用同一个常量
 * 兜一次底——两道闸共用一份范围定义，不会各自漂移成两个数字。
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
     * @see #request(String, String, String, String, String, String, int, int) 需要审计记到插件版本时用这个
     */
    public static String request(@Nullable String pluginId, @Nullable String method, @Nullable String url,
            @Nullable String headers, @Nullable String body, int timeoutSeconds) {
        return request(pluginId, null, method, url, headers, body, timeoutSeconds, 0);
    }

    /**
     * 执行一次请求，并允许复用一份尚未过期的响应。
     *
     * @param ttlSeconds  这次响应可以复用多少秒；{@code 0} 或负数表示完全不碰缓存，行为与
     *                    {@link #request(String, String, String, String, String, int)} 逐字节相同
     * @return 结果 JSON 文本；命中缓存时状态码恒为 {@link PluginHttpCache#CACHE_STATUS}（缓存里只有 200）
     * @see PluginHttpCache 为什么只缓存 GET/HEAD、为什么键里必须有 pluginId、为什么文件名不带后缀
     */
    public static String request(@Nullable String pluginId, @Nullable String method, @Nullable String url,
            @Nullable String headers, @Nullable String body, int timeoutSeconds, int ttlSeconds) {
        return request(pluginId, null, method, url, headers, body, timeoutSeconds, ttlSeconds);
    }

    /**
     * 带插件版本的请求入口：审计因此能记到"哪个插件的哪个版本"。
     *
     * <p><b>为什么版本要单独传而不是让本类去查。</b>网络桥是纯静态工具，拿不到装载器；
     * 让它反向依赖 {@code PluginHost} 才能拿到版本，会把一次 HTTP 调用绑死在插件生命周期上。
     * 版本沿信封 -> 运行时 -> 本类一路透传，是本类保持无状态的最小代价。
     *
     * @param pluginVersion 插件自身版本，仅用于审计；{@code null} 表示调用方不知道
     */
    public static String request(@Nullable String pluginId, @Nullable String pluginVersion,
            @Nullable String method, @Nullable String url, @Nullable String headers, @Nullable String body,
            int timeoutSeconds, int ttlSeconds) {
        return request(pluginId, pluginVersion, null, method, url, headers, body, timeoutSeconds, ttlSeconds);
    }

    /**
     * 带「触发事件」的请求入口：审计因此能回答"插件在响应什么的时候把请求发了出去"。
     *
     * <p>docs §8.1:330 把触发事件列为审计字段。网络桥自己是静态工具，不知道当前在跑哪个事件或
     * 动作，所以这个值沿运行时 -> 信封 -> 本类一路透传；{@code null} 表示装载期之外没有明确触发者
     * （例如脚本顶层发起的请求由运行时标为 {@code runtime.init}）。
     *
     * @param event 触发这次请求的事件名或动作名，仅用于审计
     */
    public static String request(@Nullable String pluginId, @Nullable String pluginVersion,
            @Nullable String event, @Nullable String method, @Nullable String url, @Nullable String headers,
            @Nullable String body, int timeoutSeconds, int ttlSeconds) {
        String verb = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (verb.isEmpty()) verb = "GET";
        if (!ALLOWED_METHODS.contains(verb)) {
            return blocked(pluginId, pluginVersion, event, verb, url, "unsupported method: " + verb);
        }
        String target = url == null ? "" : url.trim();
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return blocked(pluginId, pluginVersion, event, verb, target, "unsupported url scheme");
        }
        // 兜底而非唯一钳制点：信封侧的钳制在 PluginConfig.timeoutOf，但本方法是公共入口，
        // 单测与其它调用方会直接传秒数进来，这条路必须自己守住。范围共用同一个常量。
        int timeout = timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        int ttl = ttlSeconds < 1 ? 0 : Math.min(ttlSeconds, PluginHttpCache.MAX_TTL_SECONDS);

        if (ttl > 0) {
            String cached = PluginHttpCache.read(pluginId, verb, target, headers, body, ttl);
            if (cached != null) {
                // 缓存命中没有发出真实请求：字节数取不到，传 0 而不是猜一个 body 长度。
                PluginAuditLog.get().recordRequest(pluginId, pluginVersion, event, verb, target,
                        PluginHttpCache.CACHE_STATUS, 0, "cache", 0);
                return result(PluginHttpCache.CACHE_STATUS, cached);
            }
        }

        long started = System.currentTimeMillis();
        try {
            Request request = build(verb, target, headers, body);
            try (Response response = OkHttp.client(TimeUnit.SECONDS.toMillis(timeout)).newCall(request).execute()) {
                int status = response.code();
                String text = readCapped(response.body());
                long elapsed = System.currentTimeMillis() - started;
                PluginAuditLog.get().recordRequest(pluginId, pluginVersion, event, verb, target, status, elapsed,
                        response.isSuccessful() ? "ok" : "error", requestBytes(verb, body));
                if (ttl > 0 && status == PluginHttpCache.CACHE_STATUS) {
                    PluginHttpCache.write(pluginId, verb, target, headers, body, ttl, text);
                }
                return result(status, text);
            }
        } catch (IOException | RuntimeException error) {
            long elapsed = System.currentTimeMillis() - started;
            // 请求体可能参与了这次失败：仍按实际载荷记账，但异常分支不再重算 method/body 语义。
            PluginAuditLog.get().recordRequest(pluginId, pluginVersion, event, verb, target, 0, elapsed, "error",
                    requestBytes(verb, body));
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
        return blocked(pluginId, null, method, url, reason);
    }

    /** 带插件版本的拒绝入口：被拒的那一次同样要能追到具体版本。 */
    public static String blocked(@Nullable String pluginId, @Nullable String pluginVersion, @Nullable String method,
            @Nullable String url, String reason) {
        return blocked(pluginId, pluginVersion, null, method, url, reason);
    }

    /** 带版本与触发事件的拒绝入口：被拒的那一次同样要能归因到具体事件。 */
    public static String blocked(@Nullable String pluginId, @Nullable String pluginVersion, @Nullable String event,
            @Nullable String method, @Nullable String url, String reason) {
        try {
            PluginAuditLog.get().recordRequest(pluginId, pluginVersion, event, method, url, 0, 0, "blocked", 0);
        } catch (Throwable ignored) {
        }
        return error(reason);
    }

    /**
     * 这次请求实际带上的请求体字节数，仅用于审计。
     *
     * <p>GET/HEAD 在 {@link #build} 里被显式置为无 body，审计必须与真实发出的请求一致，
     * 不能因为脚本多传了一个 {@code body} 参数就记成一个并不存在的载荷。
     */
    private static long requestBytes(String verb, @Nullable String body) {
        if ("GET".equals(verb) || "HEAD".equals(verb)) return 0;
        return body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
    }

    /** 成功响应的统一形状：缓存命中与真实响应必须长得一模一样，脚本才不用分支。 */
    private static String result(int status, @Nullable String text) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("body", text == null ? "" : text);
        return result.toString();
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
