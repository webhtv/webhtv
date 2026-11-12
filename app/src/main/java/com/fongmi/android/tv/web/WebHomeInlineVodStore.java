package com.fongmi.android.tv.web;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.Source;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.common.net.HttpHeaders;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WebHomeInlineVodStore {

    public static final String KEY = "webhome_inline";
    private static final String HLS_FORMAT = "application/x-mpegURL";
    // 持久化：ITEMS 只是内存 Map，进程被回收后（隔久了从历史点开）必然
    // 报 "WebHome inline VOD not found"。把条目写进 Prefers 才能跨进程存活。
    private static final String PREF_KEY = "webhome_inline_vods";
    private static final int MAX_PERSIST = 40;
    // 剧集直链多数带签名/时效，进程重启后隔太久就不该再信这份缓存，宁可重新解析
    private static final long MEDIA_TTL_MS = 6 * 60 * 60 * 1000L;

    private static final Map<String, Entry> ITEMS = new ConcurrentHashMap<>();
    // Inline playback uses a synthetic route key; keep the real site for site-level policies.
    private static final Map<String, Site> ORIGIN_SITES = new ConcurrentHashMap<>();
    private static final Map<String, HeaderSpec> URL_HEADERS = new ConcurrentHashMap<>();
    private static final Map<String, EpisodeSpec> URL_EPISODES = new ConcurrentHashMap<>();
    // 剧集地址 -> VOD id：player() 只拿得到剧集地址，解析成功后要按 VOD id 落盘
    private static final Map<String, String> URL_VOD = new ConcurrentHashMap<>();

    public static String put(JsonObject payload) {
        return put(payload, null, null);
    }

    public static String put(JsonObject payload, Resolver resolver) {
        return put(payload, resolver, null);
    }

    static String put(JsonObject payload, Resolver resolver, Site originSite) {
        Vod vod = App.gson().fromJson(payload, Vod.class);
        if (vod == null) vod = new Vod();
        String id = first(vod.getId(), Json.safeString(payload, "vodId"), Json.safeString(payload, "id"), "webhome-" + UUID.randomUUID());
        Map<String, String> headers = HeaderPolicy.withDefaultUa(HeaderPolicy.parse(payload.get("headers")));
        HeaderSpec headerSpec = new HeaderSpec(headers, "include".equals(Json.safeString(payload, "credentials")));
        String playUrl = playUrl(payload.get("episodes"), headerSpec, resolver, id);
        vod.setId(id);
        if (vod.getName().isEmpty()) vod.setName(first(Json.safeString(payload, "vodName"), Json.safeString(payload, "title"), id));
        if (vod.getPic().isEmpty()) vod.setPic(first(Json.safeString(payload, "vodPic"), Json.safeString(payload, "pic")));
        if (vod.getPlayFrom().isEmpty()) vod.setPlayFrom(first(Json.safeString(payload, "vodPlayFrom"), Json.safeString(payload, "playFrom"), "WebHome"));
        if (vod.getPlayUrl().isEmpty()) vod.setPlayUrl(playUrl);
        ITEMS.put(id, new Entry(App.gson().toJson(vod), headerSpec, resolver));
        rememberOriginSite(id, originSite);
        persist(id, ITEMS.get(id));
        return id;
    }

    static void rememberOriginSite(String id, Site site) {
        if (TextUtils.isEmpty(id)) return;
        if (site == null || TextUtils.isEmpty(site.getKey())) {
            ORIGIN_SITES.remove(id);
            return;
        }
        ORIGIN_SITES.put(id, Site.get(site.getKey(), site.getName()));
    }

    public static Site getOriginSite(String id) {
        Site site = ORIGIN_SITES.get(id);
        return site == null ? new Site() : Site.get(site.getKey(), site.getName());
    }

    public static Result detail(String id) {
        Entry entry = ITEMS.get(id);
        if (entry == null) entry = loadPersisted(id);
        if (entry == null || TextUtils.isEmpty(entry.vod)) return Result.error("WebHome inline VOD not found");
        SpiderDebug.log("webhome-inline", "detail id=%s found=%s", id, true);
        return Result.vod(Vod.objectFrom(entry.vod));
    }

    public static boolean shouldRefreshYoutubeEpisode(String key, String episodeUrl, int statusCode, boolean attempted) {
        if (!KEY.equals(key) || attempted || (statusCode != 403 && statusCode != 410)) return false;
        try {
            String host = URI.create(episodeUrl).getHost();
            if (host != null) host = host.toLowerCase(Locale.ROOT);
            return host != null && (host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com"));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean invalidateResolvedEpisode(String id) {
        EpisodeSpec episodeSpec = URL_EPISODES.get(id);
        if (episodeSpec == null || TextUtils.isEmpty(episodeSpec.mediaUrl)) return false;
        URL_HEADERS.remove(episodeSpec.mediaUrl);
        URL_EPISODES.put(id, new EpisodeSpec(episodeSpec.payload.deepCopy(), episodeSpec.headerSpec, episodeSpec.resolver, true, "", episodeSpec.format, 0L));
        String vodId = URL_VOD.get(id);
        if (!TextUtils.isEmpty(vodId)) persist(vodId, ITEMS.get(vodId));
        SpiderDebug.log("webhome-inline", "invalidate resolved episode id=%s", id);
        return true;
    }

    public static Result player(String flag, String id) throws Exception {
        Entry entry = ITEMS.get(id);
        if (entry == null) entry = loadPersisted(id);
        HeaderSpec headerSpec = URL_HEADERS.get(id);
        EpisodeSpec episodeSpec = URL_EPISODES.get(id);
        String url = id;
        String format = "";
        long start = System.currentTimeMillis();
        SpiderDebug.log("webhome-inline", "player start flag=%s id=%s entry=%s episode=%s", flag, id, entry != null, episodeSpec != null);
        if (episodeSpec != null) {
            ResolveResult resolved = resolve(entry, id, episodeSpec);
            url = resolved.url;
            format = resolved.format;
            headerSpec = resolved.headerSpec;
            if (resolved.needsParse) {
                // 进程重启后脚本侧的解析器已经没了，此时这个地址是「播放页」而不是直链。
                // 不能直接丢给播放器（会拿 HTML 当视频播），标记为需要解析，交给 App 的嗅探流程。
                Result result = new Result();
                result.setParse(1);
                result.setUrl(url);
                result.setFlag(flag);
                result.setHeader(headers(url, headerSpec));
                if (!TextUtils.isEmpty(format)) result.setFormat(format);
                SpiderDebug.log("webhome-inline", "player needsParse id=%s url=%s", id, url);
                return result;
            }
        }
        if (headerSpec == null && entry != null) headerSpec = entry.headerSpec;
        if (headerSpec == null) headerSpec = new HeaderSpec(new HashMap<>(), false);
        Result result = new Result();
        result.setUrl(url);
        result.setParse(0);
        result.setFlag(flag);
        result.setHeader(headers(url, headerSpec));
        if (!TextUtils.isEmpty(format)) result.setFormat(format);
        else if (isHls(url)) result.setFormat(HLS_FORMAT);
        SpiderDebug.log("webhome-inline", "player resolved cost=%sms id=%s url=%s format=%s headers=%s", System.currentTimeMillis() - start, id, url, result.getFormat(), result.getHeader().keySet());
        result.setUrl(Source.get().fetch(result));
        SpiderDebug.log("webhome-inline", "player fetch ok cost=%sms id=%s url=%s", System.currentTimeMillis() - start, id, result.getUrl().v());
        return result;
    }

    /** 写进 Prefers。Resolver 是接口实例，无法序列化，用不到就留空。 */
    private static void persist(String id, Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.vod) || TextUtils.isEmpty(id)) return;
        try {
            JsonObject all = allPersisted();
            JsonObject one = new JsonObject();
            one.addProperty("vod", entry.vod);
            one.addProperty("headers", App.gson().toJson(entry.headerSpec.headers));
            one.addProperty("cookies", entry.headerSpec.includeCookies);
            one.addProperty("time", System.currentTimeMillis());
            Site site = ORIGIN_SITES.get(id);
            if (site != null && !TextUtils.isEmpty(site.getKey())) {
                one.addProperty("siteKey", site.getKey());
                one.addProperty("siteName", site.getName());
            }
            JsonObject episodes = persistEpisodes(entry.vod);
            if (episodes.size() > 0) one.add("episodes", episodes);
            all.add(id, one);
            trim(all);
            Prefers.put(PREF_KEY, all.toString());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 每个剧集的「要不要解析」标记必须跟 VOD 一起存下来：
     * URL_EPISODES 只是内存态，进程被回收后这些标记全部丢失，
     * 于是播放页地址被当成直链直接交给播放器（拿 HTML 当视频播，必然播不了）。
     */
    private static JsonObject persistEpisodes(String vodJson) {
        JsonObject result = new JsonObject();
        for (String url : episodeKeys(vodJson)) {
            EpisodeSpec spec = URL_EPISODES.get(url);
            if (spec == null) continue;
            JsonObject one = new JsonObject();
            one.add("payload", spec.payload);
            one.addProperty("headers", App.gson().toJson(spec.headerSpec.headers));
            one.addProperty("cookies", spec.headerSpec.includeCookies);
            one.addProperty("resolve", spec.resolve);
            if (!TextUtils.isEmpty(spec.mediaUrl)) {
                one.addProperty("mediaUrl", spec.mediaUrl);
                one.addProperty("format", spec.format);
                one.addProperty("mediaTime", spec.mediaTime > 0 ? spec.mediaTime : System.currentTimeMillis());
            }
            result.add(url, one);
        }
        return result;
    }

    /** vod_play_url 里的每个 $ 后面的串就是 URL_EPISODES 的 key。 */
    private static List<String> episodeKeys(String vodJson) {
        List<String> keys = new ArrayList<>();
        if (TextUtils.isEmpty(vodJson)) return keys;
        String playUrl;
        try {
            playUrl = Vod.objectFrom(vodJson).getPlayUrl();
        } catch (Throwable ignored) {
            return keys;
        }
        if (TextUtils.isEmpty(playUrl)) return keys;
        for (String seg : playUrl.split("\\$\\$\\$")) {
            for (String item : seg.split("#")) {
                int index = item.lastIndexOf('$');
                String url = (index >= 0 ? item.substring(index + 1) : item).trim();
                if (!TextUtils.isEmpty(url) && !keys.contains(url)) keys.add(url);
            }
        }
        return keys;
    }

    private static Entry loadPersisted(String id) {
        if (TextUtils.isEmpty(id)) return null;
        try {
            JsonObject all = allPersisted();
            JsonElement e = all.get(id);
            if (e == null || !e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            String vod = Json.safeString(o, "vod");
            if (TextUtils.isEmpty(vod)) return null;
            HeaderSpec headerSpec = new HeaderSpec(new HashMap<>(), o.has("cookies") && o.get("cookies").getAsBoolean());
            String raw = Json.safeString(o, "headers");
            if (!TextUtils.isEmpty(raw)) headerSpec = new HeaderSpec(parseHeaders(raw, headerSpec.headers), headerSpec.includeCookies);
            Entry entry = new Entry(vod, headerSpec, null);
            ITEMS.put(id, entry);
            String siteKey = Json.safeString(o, "siteKey");
            if (!TextUtils.isEmpty(siteKey)) ORIGIN_SITES.put(id, Site.get(siteKey, Json.safeString(o, "siteName")));
            JsonElement episodes = o.get("episodes");
            if (episodes != null && episodes.isJsonObject()) restoreEpisodes(id, episodes.getAsJsonObject());
            SpiderDebug.log("webhome-inline", "load persisted id=%s", id);
            return entry;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 把持久化的剧集标记装回内存表；解析器是脚本侧的实例，重启后必然拿不到，留空。 */
    private static void restoreEpisodes(String vodId, JsonObject episodes) {
        for (Map.Entry<String, JsonElement> item : episodes.entrySet()) {
            String url = item.getKey();
            JsonElement value = item.getValue();
            if (TextUtils.isEmpty(url) || value == null || !value.isJsonObject()) continue;
            JsonObject one = value.getAsJsonObject();
            Map<String, String> headers = parseHeaders(Json.safeString(one, "headers"), new HashMap<>());
            HeaderSpec headerSpec = new HeaderSpec(headers, one.has("cookies") && one.get("cookies").getAsBoolean());
            JsonObject payload = one.has("payload") && one.get("payload").isJsonObject() ? one.getAsJsonObject("payload") : new JsonObject();
            boolean resolve = !one.has("resolve") || one.get("resolve").getAsBoolean();
            long mediaTime = 0L;
            try {
                if (one.has("mediaTime")) mediaTime = one.get("mediaTime").getAsLong();
            } catch (Throwable ignored) {
            }
            String mediaUrl = Json.safeString(one, "mediaUrl");
            URL_HEADERS.put(url, headerSpec);
            URL_VOD.put(url, vodId);
            URL_EPISODES.put(url, new EpisodeSpec(payload, headerSpec, null, resolve, mediaUrl, Json.safeString(one, "format"), mediaTime));
            if (!TextUtils.isEmpty(mediaUrl)) URL_HEADERS.put(mediaUrl, headerSpec);
        }
    }

    private static Map<String, String> parseHeaders(String raw, Map<String, String> fallback) {
        Map<String, String> headers = new HashMap<>(fallback);
        if (TextUtils.isEmpty(raw)) return headers;
        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> parsed = App.gson().fromJson(raw, type);
            if (parsed != null) headers.putAll(parsed);
        } catch (Throwable ignored) {
        }
        return headers;
    }

    private static JsonObject allPersisted() {
        String raw = Prefers.getString(PREF_KEY, "");
        if (TextUtils.isEmpty(raw)) return new JsonObject();
        try {
            JsonObject o = Json.parse(raw).getAsJsonObject();
            return o == null ? new JsonObject() : o;
        } catch (Throwable ignored) {
            return new JsonObject();
        }
    }

    /** 只保留最近 MAX_PERSIST 条，避免 Prefers 无限增长。 */
    private static void trim(JsonObject all) {
        if (all.size() <= MAX_PERSIST) return;
        List<String> keys = new ArrayList<>();
        for (String k : all.keySet()) keys.add(k);
        keys.sort((a, b) -> Long.compare(time(all, a), time(all, b)));
        while (keys.size() > MAX_PERSIST) all.remove(keys.remove(0));
    }

    private static long time(JsonObject all, String key) {
        try {
            JsonElement e = all.get(key);
            if (e != null && e.isJsonObject() && e.getAsJsonObject().has("time")) return e.getAsJsonObject().get("time").getAsLong();
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    /**
     * 兜底解析用：按剧集地址取原始 payload（找不到返回 null）。
     */
    public static JsonObject episodePayload(String key) {
        String id = matchId(key);
        return id == null ? null : URL_EPISODES.get(id).payload.deepCopy();
    }

    /**
     * 兜底解析成功后回写缓存，同一集下次直接播直链，不再嗅探。
     */
    public static void cacheMedia(String key, JsonObject resolved) {
        String id = matchId(key);
        if (id == null || resolved == null) return;
        if (TextUtils.isEmpty(Json.safeString(resolved, "url"))) return;
        ResolveResult result = cacheResolved(id, URL_EPISODES.get(id), null, resolved.deepCopy());
        SpiderDebug.log("webhome-inline", "cache fallback media id=%s url=%s", id, result.url);
    }

    /** 剧集地址与播放页地址都可能出现在 Result 里，两种都认。 */
    private static String matchId(String key) {
        if (TextUtils.isEmpty(key)) return null;
        if (URL_EPISODES.containsKey(key)) return key;
        for (Map.Entry<String, EpisodeSpec> entry : URL_EPISODES.entrySet()) {
            if (key.equals(pageOf(entry.getValue(), entry.getKey()))) return entry.getKey();
        }
        return null;
    }

    private static Map<String, String> headers(String url, HeaderSpec headerSpec) {
        Map<String, String> result = HeaderPolicy.withDefaultUa(headerSpec.headers);
        boolean hasCookie = hasHeader(result, HttpHeaders.COOKIE);
        if (headerSpec.includeCookies && !hasCookie) {
            String cookie = CookieBridge.get(url);
            if (!TextUtils.isEmpty(cookie)) result.put("Cookie", cookie);
        }
        addBrowserCodeHeader(result);
        return result;
    }

    private static String playUrl(JsonElement element, HeaderSpec defaultSpec, Resolver resolver, String vodId) {
        if (element == null || !element.isJsonArray()) return "";
        StringBuilder builder = new StringBuilder();
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement child = array.get(i);
            JsonObject object = child != null && child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
            String mediaUrl = Json.safeString(object, "mediaUrl");
            String pageUrl = first(Json.safeString(object, "pageUrl"), Json.safeString(object, "href"));
            String rawUrl = child != null && child.isJsonPrimitive() ? child.getAsString() : Json.safeString(object, "url");
            String url = first(mediaUrl, pageUrl, rawUrl);
            if (TextUtils.isEmpty(url)) continue;
            URL_VOD.put(url, vodId);
            HeaderSpec episodeHeaders = episodeHeaders(object, defaultSpec);
            URL_HEADERS.put(url, episodeHeaders);
            if (child != null && child.isJsonObject()) {
                boolean resolve = bool(object, "resolve") || (!TextUtils.isEmpty(pageUrl) && TextUtils.isEmpty(mediaUrl));
                String format = first(Json.safeString(object, "format"), isHls(mediaUrl) ? HLS_FORMAT : "");
                URL_EPISODES.put(url, new EpisodeSpec(object.deepCopy(), episodeHeaders, resolver, resolve, mediaUrl, format, 0L));
                if (!TextUtils.isEmpty(mediaUrl)) URL_HEADERS.put(mediaUrl, episodeHeaders);
            }
            String name = first(Json.safeString(object, "name"), Json.safeString(object, "label"), Json.safeString(object, "title"), String.format("%02d", i + 1));
            if (builder.length() > 0) builder.append("#");
            builder.append(name.replace("$", " ").replace("#", " ")).append("$").append(url);
        }
        return builder.toString();
    }

    private static HeaderSpec episodeHeaders(JsonObject object, HeaderSpec defaultSpec) {
        Map<String, String> result = new HashMap<>(defaultSpec.headers);
        if (object != null && object.has("headers")) result.putAll(HeaderPolicy.parse(object.get("headers")));
        String referer = object == null ? "" : Json.safeString(object, "referer");
        if (!TextUtils.isEmpty(referer)) result.put(HttpHeaders.REFERER, referer);
        boolean includeCookies = defaultSpec.includeCookies || (object != null && "include".equals(Json.safeString(object, "credentials")));
        return new HeaderSpec(result, includeCookies);
    }

    private static ResolveResult resolve(Entry entry, String id, EpisodeSpec episodeSpec) throws Exception {
        if (fresh(episodeSpec)) {
            SpiderDebug.log("webhome-inline", "resolve cached id=%s url=%s", id, episodeSpec.mediaUrl);
            return new ResolveResult(episodeSpec.mediaUrl, episodeSpec.headerSpec, episodeSpec.format, false);
        }
        if (!episodeSpec.resolve) {
            String url = pageOf(episodeSpec, id);
            SpiderDebug.log("webhome-inline", "resolve direct id=%s url=%s", id, url);
            return new ResolveResult(url, episodeSpec.headerSpec, episodeSpec.format, false);
        }
        Resolver resolver = entry != null ? entry.resolver : episodeSpec.resolver;
        if (resolver == null) {
            // 进程重启后脚本侧的 __fmWebHomeInlineResolver 已经没了。
            // 这个地址是播放页，不是直链：标记 needsParse，先让系统嗅探（App 自己的 WebView + 原生嗅探）去拿，
            // 系统嗅探失败时由 ParseJob 回落到隐藏的 WebHome 实例（注入嗅探兜底）。
            SpiderDebug.log("webhome-inline", "resolve needsParse id=%s page=%s", id, pageOf(episodeSpec, id));
            return new ResolveResult(pageOf(episodeSpec, id), episodeSpec.headerSpec, episodeSpec.format, true);
        }
        long start = System.currentTimeMillis();
        SpiderDebug.log("webhome-inline", "resolve episode start id=%s page=%s", id, pageOf(episodeSpec, id));
        try {
            JsonObject resolved = resolver.resolve(episodeSpec.payload.deepCopy());
            if (TextUtils.isEmpty(Json.safeString(resolved, "url"))) throw new IllegalStateException("WebHome inline episode resolve failed");
            ResolveResult result = cacheResolved(id, episodeSpec, resolver, resolved);
            SpiderDebug.log("webhome-inline", "resolve episode ok cost=%sms id=%s url=%s", System.currentTimeMillis() - start, id, result.url);
            return result;
        } catch (Throwable e) {
            // 脚本侧 inline 解析失败：不要直接抛错，回退到 needsParse。
            // 这样 App 会像冷启动后从历史进入一样，自己开 WebView 真正加载播放页做原生嗅探，
            // 而不是在当前 WebView 里用 srcdoc 模拟，避免跨域 iframe 场景下扫不到真正的播放器。
            SpiderDebug.log("webhome-inline", "resolve episode failed cost=%sms id=%s error=%s, fallback needsParse", System.currentTimeMillis() - start, id, e.getMessage());
            return new ResolveResult(pageOf(episodeSpec, id), episodeSpec.headerSpec, episodeSpec.format, true);
        }
    }

    /** 解析成功后统一收口：更新内存缓存 + 落盘，供本会话与下次进程重启复用。 */
    private static ResolveResult cacheResolved(String id, EpisodeSpec episodeSpec, Resolver resolver, JsonObject resolved) {
        String url = Json.safeString(resolved, "url");
        HeaderSpec headerSpec = resolvedHeaders(resolved, episodeSpec.headerSpec);
        String format = first(Json.safeString(resolved, "format"), isHls(url) ? HLS_FORMAT : episodeSpec.format);
        URL_HEADERS.put(url, headerSpec);
        URL_EPISODES.put(id, new EpisodeSpec(episodeSpec.payload.deepCopy(), headerSpec, resolver, false, url, format, System.currentTimeMillis()));
        // 解析结果也落盘：下次进程重启后可以先直接用（MEDIA_TTL_MS 内有效）
        String vodId = URL_VOD.get(id);
        if (!TextUtils.isEmpty(vodId)) {
            URL_VOD.put(url, vodId);
            persist(vodId, ITEMS.get(vodId));
        }
        return new ResolveResult(url, headerSpec, format, false);
    }

    /** 直链缓存是否还能用：带时间戳的按 TTL 判，本次会话内解析的（无时间戳）直接用。 */
    private static boolean fresh(EpisodeSpec episodeSpec) {
        if (TextUtils.isEmpty(episodeSpec.mediaUrl)) return false;
        if (episodeSpec.mediaTime <= 0) return true;
        return System.currentTimeMillis() - episodeSpec.mediaTime < MEDIA_TTL_MS;
    }

    /** 播放页地址：脚本给的剧集对象里 pageUrl/href/url 都可能是它。 */
    private static String pageOf(EpisodeSpec episodeSpec, String id) {
        JsonObject payload = episodeSpec.payload;
        return first(Json.safeString(payload, "pageUrl"), Json.safeString(payload, "href"), Json.safeString(payload, "url"), id);
    }

    private static HeaderSpec resolvedHeaders(JsonObject resolved, HeaderSpec fallback) {
        Map<String, String> headers = new HashMap<>(fallback.headers);
        if (resolved != null && resolved.has("headers")) headers.putAll(HeaderPolicy.parse(resolved.get("headers")));
        String referer = resolved == null ? "" : Json.safeString(resolved, "referer");
        if (!TextUtils.isEmpty(referer)) headers.put(HttpHeaders.REFERER, referer);
        boolean includeCookies = fallback.includeCookies || (resolved != null && "include".equals(Json.safeString(resolved, "credentials")));
        return new HeaderSpec(headers, includeCookies);
    }

    private static void addBrowserCodeHeader(Map<String, String> headers) {
        if (hasHeader(headers, "browser-code")) return;
        String cookie = header(headers, HttpHeaders.COOKIE);
        if (TextUtils.isEmpty(cookie)) return;
        for (String part : cookie.split(";")) {
            String value = part.trim();
            if (!value.startsWith("browser-code=")) continue;
            headers.put("browser-code", value.substring("browser-code=".length()));
            return;
        }
    }

    private static boolean hasHeader(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        return "";
    }

    private static boolean isHls(String url) {
        String value = url == null ? "" : url.toLowerCase();
        return value.contains(".m3u8") || value.contains("/playlist/");
    }

    private static boolean bool(JsonObject object, String name) {
        try {
            return object != null && object.has(name) && object.get(name).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static String first(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value.trim();
        return "";
    }

    public interface Resolver {
        JsonObject resolve(JsonObject payload) throws Exception;
    }

    private record Entry(String vod, HeaderSpec headerSpec, Resolver resolver) {
    }

    private record HeaderSpec(Map<String, String> headers, boolean includeCookies) {
    }

    private record EpisodeSpec(JsonObject payload, HeaderSpec headerSpec, Resolver resolver, boolean resolve,
                               String mediaUrl, String format, long mediaTime) {
    }

    private record ResolveResult(String url, HeaderSpec headerSpec, String format, boolean needsParse) {
    }
}
