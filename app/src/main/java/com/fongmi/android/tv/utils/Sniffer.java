package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;
 
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sniffer {

    public static final Pattern CLICKER = Pattern.compile("\\[a=cr:(\\{.*?\\})\\/](.*?)\\[\\/a]");
    public static final Pattern AI_PUSH = Pattern.compile("(https?|thunder|magnet|ed2k|video):\\S+");
    public static final Pattern SNIFFER = Pattern.compile("https?://[^\\s]{12,}\\.(?:m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)(?:\\?.*)?|https?://.*?video/tos[^\\s]*|rtmp:[^\\s]+");

    public static String getUrl(String text) {
        // 携带请求头的推送串：不能走 AI_PUSH 正则，否则 json 里的空格会把地址截断
        if (text.contains(HEADERS_TAG)) return text;
        if (Json.isObj(text) || text.contains("$")) return text;
        Matcher m = AI_PUSH.matcher(text);
        if (m.find()) return m.group(0);
        return text;
    }

    /**
     * 推送串携带请求头的约定，与图片地址（ImgUtil）保持一致：
     * <code>url@Headers=&lt;encode(json)&gt;</code>，另外兼容 @Cookie= / @Referer= / @User-Agent=。
     * json 用 Uri.encode 编码，避免空格、#、$、& 等字符干扰后续的正则/线路/快捷键解析。
     */
    public static final String HEADERS_TAG = "@Headers=";
    private static final String[] HEADER_TAGS = {"@Headers=", "@Cookie=", "@Referer=", "@User-Agent="};
 
    /** 取推送串里的真实地址；不含请求头段时原样返回。 */
    public static String pushUrl(String text) {
        if (TextUtils.isEmpty(text)) return "";
        for (String tag : HEADER_TAGS) {
            int idx = text.indexOf(tag);
            if (idx > 0) return text.substring(0, idx);
        }
        return text;
    }
 
    /** 取推送串里的请求头（含 @Headers=<json> 与单独的头段）。 */
    public static Map<String, String> pushHeaders(String text) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (TextUtils.isEmpty(text)) return headers;
        String raw = segment(text, HEADERS_TAG);
        if (!TextUtils.isEmpty(raw)) {
            try {
                headers.putAll(Json.toMap(Json.parse(Uri.decode(raw))));
            } catch (Throwable ignored) {
            }
        }
        addPushHeader(headers, text, "@Cookie=", HttpHeaders.COOKIE);
        addPushHeader(headers, text, "@Referer=", HttpHeaders.REFERER);
        addPushHeader(headers, text, "@User-Agent=", HttpHeaders.USER_AGENT);
        return headers;
    }
 
    /** 生成携带请求头的推送串。 */
    public static String pushPayload(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url) || headers == null || headers.isEmpty()) return url;
        return url + HEADERS_TAG + Uri.encode(App.gson().toJson(headers));
    }
 
    private static String segment(String text, String tag) {
        int idx = text.indexOf(tag);
        if (idx < 0) return "";
        String raw = text.substring(idx + tag.length());
        int end = raw.indexOf('@');
        return end < 0 ? raw : raw.substring(0, end);
    }
 
    private static void addPushHeader(Map<String, String> headers, String text, String tag, String name) {
        String raw = segment(text, tag);
        if (!TextUtils.isEmpty(raw)) headers.put(UrlUtil.fixHeader(name), Uri.decode(raw));
    }
 
    public static boolean isVideoFormat(String url) {
        // 资源网关地址形如 http://127.0.0.1:9978/webResource?url=<encoded>&headers=...
        // 真实地址藏在被编码的 url 参数里，直接匹配正则必然失败，
        // 会导致播放页把默认线路排成"嗅探"。
        String inner = gatewayTarget(url);
        if (!TextUtils.isEmpty(inner)) url = inner;
        // 推送串带 @Headers= 时同样要剥掉，否则 .m3u8@Headers= 匹配不上
        url = pushUrl(url);
        Rule rule = getRule(UrlUtil.uri(url));
        for (String exclude : rule.getExclude()) if (url.contains(exclude)) return false;
        for (String exclude : rule.getExclude()) if (Pattern.compile(exclude).matcher(url).find()) return false;
        for (String regex : rule.getRegex()) if (url.contains(regex)) return true;
        for (String regex : rule.getRegex()) if (Pattern.compile(regex).matcher(url).find()) return true;
        if (url.contains("url=http") || url.contains("v=http") || url.contains(".html")) return false;
        return SNIFFER.matcher(url).find();
    }

    /** 取资源网关地址里被编码的真实地址；非网关地址返回空。 */
    public static String gatewayTarget(String url) {
        if (TextUtils.isEmpty(url) || !url.contains("/webResource")) return "";
        try {
            int idx = url.indexOf('?');
            if (idx < 0) return "";
            String query = url.substring(idx + 1);
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                if (!"url".equals(pair.substring(0, eq))) continue;
                String value = pair.substring(eq + 1);
                try {
                    value = java.net.URLDecoder.decode(value, "UTF-8");
                } catch (Throwable ignored) {
                }
                return value.startsWith("http") ? value : "";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static List<String> getScript(Uri uri) {
        return getRule(uri).getScript();
    }

    private static Rule getRule(Uri uri) {
        if (uri.getHost() == null) return Rule.empty();
        String hosts = TextUtils.join(",", Arrays.asList(UrlUtil.host(uri), UrlUtil.host(uri.getQueryParameter("url"))));
        for (Rule rule : RuleConfig.get().getRules()) for (String host : rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule;
        return Rule.empty();
    }

    public static SpannableStringBuilder buildClickable(String text, Function<Result, ClickableSpan> factory) {
        SpannableStringBuilder span = new SpannableStringBuilder();
        Matcher matcher = CLICKER.matcher(text);
        int last = 0;
        while (matcher.find()) {
            span.append(text, last, matcher.start());
            int start = span.length();
            span.append(Trans.s2t(matcher.group(2).trim()));
            span.setSpan(factory.apply(Result.type(matcher.group(1))), start, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            last = matcher.end();
        }
        span.append(text, last, text.length());
        return span;
    }
}
