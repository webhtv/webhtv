package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.web.CookieBridge;
import com.fongmi.android.tv.web.HeaderPolicy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 资源网关：把带 headers / cookies 的请求代理成本机 HTTP 地址。
 *
 * v2 关键修复：播放列表内容重写。
 *
 * 背景：网关地址形如
 *   http://127.0.0.1:9978/webResource?url=https%3A%2F%2F...%2Findex.m3u8&headers=...
 * m3u8 里的分片常写成相对路径（如 b22ecaa4/001.ts），播放器会相对于
 * "http://127.0.0.1:9978/" 去解析，得到 http://127.0.0.1:9978/b22ecaa4/001.ts —— 404。
 * 这就是之前"媒体解析失败或没有有效音视频轨道"的真正原因。
 *
 * 修法：网关返回 m3u8 / mpd 时，把内容里的相对地址改写成绝对地址，
 * 并再包一层网关（继承同样的 headers / credentials），这样嵌套分片也带着请求头。
 *
 * 附带解决投屏：重写时用「请求方的 Host」构造网关地址。
 * 本机请求 Host=127.0.0.1:9978 → 分片地址也是 127.0.0.1（本机可访问）；
 * 远程设备请求 Host=192.168.1.5:9978 → 分片地址是局域网 IP（远程可访问）。
 * 无需额外配置，自动适配两端。
 */
public class WebResourceGateway implements Process {

    private static final int MAX_REWRITE_BYTES = 4 * 1024 * 1024;   // 超过 4MB 不重写，直接透传
    private static final int MAX_LINE = 4096;

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/webResource");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() == NanoHTTPD.Method.OPTIONS) return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, ""), session);
        okhttp3.Response response = null;
        try {
            Map<String, String> params = session.getParms();
            String target = params.get("url");
            if (!isAllowed(target)) return cors(Nano.error(Response.Status.BAD_REQUEST, "Unsupported url"), session);
            Map<String, String> headers = HeaderPolicy.withDefaultUa(HeaderPolicy.parse(params.get("headers")));
            copyRange(session, headers);
            Request.Builder builder = new Request.Builder().url(target).headers(HeaderPolicy.of(headers));
            CookieBridge.apply(builder.build().url(), builder, "include".equals(params.get("credentials")), HeaderPolicy.hasCookie(headers));
            builder.method(getMethod(session), getBody(session, builder.build().headers(), files));
            Request request = builder.build();
            long start = System.currentTimeMillis();
            SpiderDebug.log("web-resource", "%s %s", request.method(), request.url());
            response = OkHttp.client().newCall(request).execute();
            SpiderDebug.log("web-resource", "%s %s -> %s in %sms", request.method(), request.url(), response.code(), System.currentTimeMillis() - start);
            CookieBridge.set(target, response.headers());
            String type = getContentType(response);
            // 播放列表：改写内容里的相对地址，否则分片请求打到网关根路径必然 404
            if (isPlaylist(type, target)) {
                Response rewritten = rewritePlaylist(session, response, target, type);
                if (rewritten != null) return cors(rewritten, session);
            }
            return cors(toResponse(response, type), session);
        } catch (Throwable e) {
            if (response != null) response.close();
            SpiderDebug.log("web-resource", e);
            return cors(Nano.error(e.getMessage()), session);
        }
    }

    // ---------------- 播放列表判定 ----------------

    private boolean isPlaylist(String type, String target) {
        if (!TextUtils.isEmpty(type)) {
            String t = type.toLowerCase();
            if (t.contains("mpegurl") || t.contains("x-mpegurl") || t.contains("dash+xml")) return true;
        }
        String u = target == null ? "" : target.toLowerCase();
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        return u.endsWith(".m3u8") || u.endsWith(".mpd");
    }

    private boolean isDash(String type, String target) {
        if (!TextUtils.isEmpty(type) && type.toLowerCase().contains("dash+xml")) return true;
        String u = target == null ? "" : target.toLowerCase();
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        return u.endsWith(".mpd");
    }

    // ---------------- 内容重写 ----------------

    private Response rewritePlaylist(IHTTPSession session, okhttp3.Response response, String target, String type) {
        try {
            long len = response.body().contentLength();
            if (len > MAX_REWRITE_BYTES) return null;      // 太大，直接透传
            String body = response.body().string();
            if (TextUtils.isEmpty(body)) return null;
            String out = isDash(type, target) ? rewriteDash(session, body, target) : rewriteM3u8(session, body, target);
            if (TextUtils.isEmpty(out)) return null;
            Response res = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, type, out);
            res.addHeader("Access-Control-Allow-Origin", "*");
            return res;
        } catch (Throwable e) {
            SpiderDebug.log("web-resource", e);
            return null;
        }
    }

    /**
     * m3u8：逐行处理。
     *  - 空行、普通注释（#EXTINF 等）原样保留
     *  - 带 URI= 的标签（#EXT-X-KEY / #EXT-X-MAP / #EXT-X-PART 等）改写引号内的地址
     *  - 其它行视为分片地址，改写为绝对地址 + 包网关
     */
    private String rewriteM3u8(IHTTPSession session, String body, String target) {
        String[] lines = body.split("\n");
        StringBuilder out = new StringBuilder(body.length() + 1024);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                out.append('\n');
                continue;
            }
            if (line.startsWith("#")) {
                out.append(rewriteUriAttr(session, line, target)).append('\n');
                continue;
            }
            if (line.length() > MAX_LINE) {
                out.append(line).append('\n');
                continue;
            }
            out.append(wrap(session, resolve(target, line))).append('\n');
        }
        return out.toString();
    }

    /** 改写 #EXT-X-KEY:URI="..." / #EXT-X-MAP:URI="..." 这类标签里的地址。 */
    private String rewriteUriAttr(IHTTPSession session, String line, String target) {
        String lower = line.toLowerCase();
        int idx = lower.indexOf("uri=\"");
        if (idx < 0) return line;
        int start = idx + 5;
        int end = line.indexOf('"', start);
        if (end <= start) return line;
        String inner = line.substring(start, end);
        if (inner.isEmpty() || inner.startsWith("data:")) return line;             // 内嵌密钥，不改
        String wrapped = wrap(session, resolve(target, inner));
        return line.substring(0, start) + wrapped + line.substring(end);
    }

    /** DASH：改写 <BaseURL>...</BaseURL>，其余原样。 */
    private String rewriteDash(IHTTPSession session, String body, String target) {
        StringBuilder out = new StringBuilder(body.length() + 512);
        int i = 0;
        String lower = body.toLowerCase();
        while (i < body.length()) {
            int open = lower.indexOf("<baseurl", i);
            if (open < 0) {
                out.append(body.substring(i));
                break;
            }
            int gt = body.indexOf('>', open);
            int close = lower.indexOf("</baseurl>", open);
            if (gt < 0 || close < 0 || close < gt) {
                out.append(body.substring(i));
                break;
            }
            out.append(body, i, gt + 1);
            String inner = body.substring(gt + 1, close).trim();
            if (!inner.isEmpty() && !inner.startsWith("data:")) {
                out.append(wrap(session, resolve(target, inner)));
            }
            out.append("</BaseURL>");
            i = close + "</BaseURL>".length();
        }
        return out.toString();
    }

    /** 相对地址 -> 绝对地址。已是绝对地址的保持不变。 */
    private String resolve(String base, String ref) {
        if (TextUtils.isEmpty(ref)) return "";
        String r = ref.trim();
        if (r.startsWith("http://") || r.startsWith("https://")) return r;
        if (r.startsWith("//")) {
            try {
                java.net.URI b = java.net.URI.create(base);
                return b.getScheme() + ":" + r;
            } catch (Throwable e) {
                return r;
            }
        }
        try {
            java.net.URI resolved = java.net.URI.create(base).resolve(r);
            return resolved.toString();
        } catch (Throwable e) {
            // 兜底：手工拼接
            int cut = base.lastIndexOf('/');
            return cut > 8 ? base.substring(0, cut + 1) + r : r;
        }
    }

    /**
     * 把绝对地址包一层网关，继承本次请求的 headers / credentials。
     *
     * 用「请求方的 Host」而不是写死 127.0.0.1：
     * 本机请求 → 127.0.0.1；远程设备请求 → 局域网 IP。
     * 这样投屏出去的 m3u8 里分片地址也是局域网可达的。
     */
    private String wrap(IHTTPSession session, String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("data:") || url.startsWith("blob:")) return url;
        String host = session == null ? null : session.getHeaders().get("host");
        String prefix;
        if (!TextUtils.isEmpty(host)) prefix = "http://" + host + "/webResource?url=";
        else prefix = com.fongmi.android.tv.server.Server.get().getAddress("/webResource?url=");
        StringBuilder builder = new StringBuilder(prefix).append(encode(url));
        String headers = session == null ? null : session.getParms().get("headers");
        if (!TextUtils.isEmpty(headers)) builder.append("&headers=").append(encode(headers));
        String credentials = session == null ? null : session.getParms().get("credentials");
        if (!TextUtils.isEmpty(credentials)) builder.append("&credentials=").append(encode(credentials));
        return builder.toString();
    }

    private static String encode(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Throwable e) {
            return value;
        }
    }

    // ---------------- 原有逻辑 ----------------

    private boolean isAllowed(String url) {
        return !TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private void copyRange(IHTTPSession session, Map<String, String> headers) {
        String range = session.getHeaders().get("range");
        if (!TextUtils.isEmpty(range)) headers.put("Range", range);
    }

    private String getMethod(IHTTPSession session) {
        String method = session.getMethod().name();
        return "OPTIONS".equals(method) ? "GET" : method;
    }

    private RequestBody getBody(IHTTPSession session, Headers headers, Map<String, String> files) {
        String method = getMethod(session);
        if ("GET".equals(method) || "HEAD".equals(method)) return null;
        String body = session.getParms().get("body");
        if (TextUtils.isEmpty(body)) body = files.get("postData");
        String contentType = headers.get("Content-Type");
        MediaType type = MediaType.parse(contentType == null ? "text/plain; charset=utf-8" : contentType);
        return RequestBody.create(body == null ? "" : body, type);
    }

    private Response toResponse(okhttp3.Response response, String type) {
        InputStream stream = response.body().byteStream();
        Response.Status status = Response.Status.lookup(response.code());
        Response result = NanoHTTPD.newChunkedResponse(status == null ? Response.Status.OK : status, type, stream);
        for (String name : response.headers().names()) if (copyHeader(name)) for (String value : response.headers(name)) result.addHeader(name, value);
        return result;
    }

    private String getContentType(okhttp3.Response response) {
        MediaType type = response.body().contentType();
        return type == null ? NanoHTTPD.MIME_PLAINTEXT : type.toString();
    }

    private boolean copyHeader(String name) {
        return !"Connection".equalsIgnoreCase(name) && !"Transfer-Encoding".equalsIgnoreCase(name) && !"Keep-Alive".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name);
    }

    private Response cors(Response response, IHTTPSession session) {
        String origin = session.getHeaders().get("origin");
        response.addHeader("Access-Control-Allow-Origin", TextUtils.isEmpty(origin) ? "*" : origin);
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "*");
        response.addHeader("Access-Control-Expose-Headers", "*");
        response.addHeader("Access-Control-Max-Age", "86400");
        return response;
    }
}
