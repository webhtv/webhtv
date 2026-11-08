package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.VodSiteCatalog;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.bean.Doh;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okio.ByteString;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * 当前已加载站源的 T4 网关，复用 NanoHTTPD 端口（默认 9978）。
 * 配置：/vod/api?ac=config；站点：/vod/api?key=... 或 /vod/api/<key>。
 * T3 直接调用 Spider；远端站源仅复用 SiteApi.call 的 HTTP 层，不进入本机播放器。
 * 协议参考用户提供的实验室 T4Proxy.js，并以 SiteApi 的实际 T4 请求为互操作契约。
 */
public class VodApi implements Process {

    static final String PATH = "/vod/api";
    static final String WARNING = "资源来自网络，仅供学习使用";
    /** 与 T4Proxy.js / vod.php 一致：站源没给总页数时按 9999 处理。 */
    static final int DEFAULT_PAGE_COUNT = 9999;
    static final int DEFAULT_LIMIT = 20;
    private static final String[] FIELDS = {"area", "year", "type", "class", "lang"};
    /** 与 {@code App.gson()} 等价（那里就是 new Gson()），独立持有以便单元测试。 */
    private static final Gson GSON = new Gson();
    private final VodConfig sources;
    private final VodSiteCatalog.Source catalogs;

    public VodApi() {
        this(VodConfig.get(), VodSiteCatalog.database());
    }

    VodApi(VodConfig sources) {
        this(sources, VodSiteCatalog.database());
    }

    VodApi(VodConfig sources, VodSiteCatalog.Source catalogs) {
        this.sources = sources;
        this.catalogs = catalogs;
    }

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return PATH.equals(url) || (url != null && url.startsWith(PATH + "/"));
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() == NanoHTTPD.Method.OPTIONS) {
            return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, "application/json", ""));
        }
        if (session.getMethod() != NanoHTTPD.Method.GET && session.getMethod() != NanoHTTPD.Method.POST
                && session.getMethod() != NanoHTTPD.Method.HEAD) {
            return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json", error("不支持的 HTTP 方法").toString()));
        }
        try {
            JsonObject input = input(session, files);
            String origin = origin(session);
            String key = siteKeyFromPath(url);
            if (key.isEmpty()) key = text(input, "key");
            String configKey = text(input, "configKey");
            if (isConfig(input) || (key.isEmpty() && input.isEmpty())) {
                return json(configJson(origin, configKey));
            }
            SpiderDebug.log("vod-api", "route=%s configKey=%s", route(input), configKey);
            VodSiteCatalog.Catalog catalog = catalogOf(configKey);
            JsonObject result = handle(input, key, origin, catalog);
            Site site = findSite(catalog, key);
            String proxyKey = site != null && !site.isEmpty() && site.getType() == 3 && supportsDirectProxy(site.spider()) ? key : "";
            externalize(result, origin, proxyKey, configKey, com.github.catvod.Proxy.getPort());
            return json(result);
        } catch (Exception e) {
            SpiderDebug.log("vod-api", e);
            return json(error(e instanceof IllegalArgumentException ? e.getMessage() : "站源请求失败，请检查站源或查看应用日志"));
        }
    }

    /**
     * 配置清单。带 {@code configKey} 时返回该配置的站源，解析走 {@link VodSiteCatalog}，
     * 不加载、不保存、不切换当前激活配置；不带时保持原语义，返回当前已加载站源。
     */
    private JsonObject configJson(String origin, String configKey) throws Exception {
        VodSiteCatalog.Catalog catalog = catalogOf(configKey);
        if (catalog == null) return configJson(sources.getSites(), origin + PATH, sources.getDoh(), sources.getRules());
        return configJson(catalog.getSites(), origin + PATH, catalog.getDoh(), catalog.getRules(), configKey);
    }

    /** 只在显式传入 configKey 时才解析目标配置；空值一律走当前激活源。 */
    private VodSiteCatalog.Catalog catalogOf(String configKey) throws Exception {
        if (configKey == null || configKey.isEmpty()) return null;
        return catalogs.load(configKey);
    }

    private Site findSite(VodSiteCatalog.Catalog catalog, String key) {
        return catalog != null ? catalog.find(key) : sources.getSite(key);
    }

    private List<String> flagsOf(VodSiteCatalog.Catalog catalog) {
        return catalog != null ? catalog.getFlags() : sources.getFlags();
    }

    private JsonObject handle(JsonObject input, String key, String origin, VodSiteCatalog.Catalog catalog) throws Exception {
        if (key.isEmpty()) return error("缺少站点 key 参数");
        Site site = findSite(catalog, key);
        if (site == null || site.isEmpty()) return error("site not found: " + key);
        if (isSelfGateway(site.getApi(), origin, com.github.catvod.Proxy.getPort())) return error("不能把本网关的配置作为本机源再次导入");
        int page = Math.max(1, parseInt(text(input, "pg"), 1));
        String route = route(input);
        String flag = text(input, "flag");
        String id = text(input, "id").isEmpty() ? text(input, "play") : text(input, "id");
        List<String> ids = ids(input);
        if ("play".equals(route) && (flag.isEmpty() || id.isEmpty())) return error("缺少 flag 或 id 参数");
        if ("detail".equals(route) && ids.isEmpty()) return error("缺少 ids 参数");
        String tid = text(input, "t");
        boolean filter = !input.has("f") || truthy(text(input, "f"));
        if (input.has("filter")) filter = truthy(text(input, "filter"));
        JsonObject raw;
        if (site.getType() == 3) {
            // 不切换全局 recent loader；返回 /proxy 时以 siteKey 明确路由。
            Spider spider = site.spider();
            String content;
            switch (route) {
                case "search":
                    boolean quick = truthy(text(input, "quick"));
                    content = page == 1 ? spider.searchContent(text(input, "wd"), quick)
                            : spider.searchContent(text(input, "wd"), quick, String.valueOf(page));
                    break;
                case "detail": content = spider.detailContent(ids); break;
                case "play": content = spider.playerContent(flag, id, flagsOf(catalog)); break;
                default:
                    content = tid.isEmpty() ? spider.homeContent(filter)
                            : spider.categoryContent(tid, String.valueOf(page), filter, extendFrom(input));
            }
            raw = sourceObject(content);
            if ("list".equals(route) && tid.isEmpty()) {
                // 推荐内容是可选的；保留已经成功拿到的首页分类和筛选。
                try {
                    JsonObject video = sourceObject(spider.homeVideoContent());
                    if (video.has("list") && video.get("list").isJsonArray() && !video.getAsJsonArray("list").isEmpty()) raw.add("list", video.get("list"));
                } catch (Exception e) {
                    SpiderDebug.log("vod-api", "homeVideoContent unavailable: %s", e.getClass().getSimpleName());
                }
            }
        } else {
            if ("play".equals(route) && site.getType() != 4) {
                Result result = new Result();
                result.setUrl(id);
                result.setFlag(flag);
                result.setHeader(site.getHeader());
                result.setPlayUrl(site.getPlayUrl());
                result.setParse(Sniffer.isVideoFormat(id) && site.getPlayUrl().isEmpty() ? 0 : 1);
                return play(result, flag);
            }
            ArrayMap<String, String> params = new ArrayMap<>();
            switch (route) {
                case "search":
                    params.put("wd", text(input, "wd"));
                    params.put("pg", String.valueOf(page));
                    params.put("quick", String.valueOf(truthy(text(input, "quick"))));
                    break;
                case "detail":
                    params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                    params.put("ids", String.join(",", ids));
                    break;
                case "play": params.put("play", id); params.put("flag", flag); break;
                default:
                    if (tid.isEmpty()) {
                        params.put("filter", String.valueOf(filter));
                        if (site.getType() == 4) site.fetchExt();
                    } else {
                        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                        params.put("t", tid);
                        params.put("pg", String.valueOf(page));
                        String extend = GSON.toJson(extendFrom(input));
                        if (site.getType() == 4) params.put("ext", ByteString.encodeUtf8(extend).base64Url());
                        if (site.getType() == 1) params.put("f", extend);
                    }
            }
            String content = SiteApi.call(site, params);
            raw = site.getType() == 0 ? GSON.toJsonTree(Result.fromXml(content)).getAsJsonObject() : sourceObject(content);
        }
        if (raw.has("error") || "-1".equals(text(raw, "code"))) return error("站源返回错误");
        if ("play".equals(route)) return playJson(raw, flag, site);
        return formatContent(raw, page);
    }

    private static JsonObject sourceObject(String raw) {
        if (raw == null || raw.isEmpty()) return new JsonObject();
        JsonObject object = jsonObject(raw);
        if (object == null) throw new IllegalArgumentException("站源返回的内容不是 JSON 对象");
        return object;
    }

    private static List<String> ids(JsonObject input) {
        List<String> ids = new ArrayList<>();
        JsonElement raw = input.get("ids");
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement id : raw.getAsJsonArray()) if (id.isJsonPrimitive() && !id.getAsString().trim().isEmpty()) ids.add(id.getAsString().trim());
        } else {
            for (String id : csv(input, "ids").split(",")) if (!id.trim().isEmpty()) ids.add(id.trim());
        }
        if (ids.size() > 100) throw new IllegalArgumentException("一次最多查询 100 个视频");
        return ids;
    }

    /** ac=detail 既用于分类也用于详情；具体参数优先于 ac。 */
    static String route(JsonObject input) {
        String ac = text(input, "ac");
        if ("config".equals(ac) || "site".equals(ac)) return "config";
        if (!text(input, "wd").isEmpty()) return "search";
        if (input.has("ids")) return "detail";
        if ("play".equals(ac) || !text(input, "play").isEmpty()
                || (!text(input, "flag").isEmpty() && !text(input, "id").isEmpty())) return "play";
        return "list";
    }

    private static boolean isConfig(JsonObject input) {
        return "config".equals(route(input));
    }

    /** NanoHTTPD 已解码 URI；不要再次解码或将 key 中的 '?'、'/' 当分隔符。 */
    static String siteKeyFromPath(String url) {
        return url != null && url.startsWith(PATH + "/") ? url.substring(PATH.length() + 1) : "";
    }

    /** 保留原始分页/筛选元数据，同时用公共字段白名单隔离宿主内部状态。 */
    static JsonObject formatContent(JsonObject raw, int page) {
        Result result = new Result();
        List<Vod> vods = new ArrayList<>();
        List<JsonObject> originals = new ArrayList<>();
        JsonElement rawList = raw.get("list");
        if (rawList != null && rawList.isJsonArray()) {
            for (JsonElement element : rawList.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject().deepCopy();
                item.remove("site");
                item.remove("tmdb");
                Vod vod = GSON.fromJson(item, Vod.class);
                if (item.has("flags") && item.get("flags").isJsonArray()) vod.setFlags(Arrays.asList(GSON.fromJson(item.get("flags"), Flag[].class)));
                vods.add(vod);
                originals.add(item);
            }
        }
        result.setList(vods);
        List<Class> types = new ArrayList<>();
        JsonElement rawTypes = raw.get("class");
        if (rawTypes != null && rawTypes.isJsonArray()) {
            for (JsonElement type : rawTypes.getAsJsonArray()) if (type.isJsonObject()) types.add(GSON.fromJson(type, Class.class));
        }
        result.setTypes(types);
        JsonObject out = macCms(result, page);
        int actualPage = Math.max(1, parseInt(text(raw, "page"), page));
        int pageCount = Math.max(1, parseInt(text(raw, "pagecount"), vods.isEmpty() ? actualPage : DEFAULT_PAGE_COUNT));
        int limit = Math.max(1, parseInt(text(raw, "limit"), vods.isEmpty() ? DEFAULT_LIMIT : vods.size()));
        out.addProperty("page", actualPage);
        out.addProperty("pagecount", pageCount);
        out.addProperty("limit", limit);
        long total = (long) pageCount * limit;
        try { total = Math.max(0L, Long.parseLong(text(raw, "total"))); } catch (NumberFormatException ignored) { }
        out.addProperty("total", total);
        for (int i = 0; i < originals.size(); i++) {
            JsonObject item = out.getAsJsonArray("list").get(i).getAsJsonObject();
            for (String name : new String[]{"type_id", "vod_tag"}) {
                if (originals.get(i).has(name) && originals.get(i).get(name).isJsonPrimitive()) item.add(name, originals.get(i).get(name));
            }
        }
        if (raw.has("filters") && raw.get("filters").isJsonObject()) out.add("filters", raw.get("filters").deepCopy());
        if (!text(raw, "msg").isEmpty()) out.addProperty("msg", text(raw, "msg"));
        return out;
    }

    /** 保留 Spider 的解析提示/URL 数组，不假定服务端已经完成嗅探或提取。 */
    static JsonObject playJson(JsonObject raw, String flag, Site site) {
        JsonObject out = new JsonObject();
        for (String field : new String[]{"url", "urls", "playUrl", "flag", "parse", "jx", "format", "header", "drm", "subs", "click", "jxFrom", "msg", "danmaku"}) {
            if (raw.has(field) && !raw.get(field).isJsonNull()) out.add(field, raw.get(field).deepCopy());
        }
        if (!out.has("url")) out.addProperty("url", "");
        if (text(out, "flag").isEmpty()) prop(out, "flag", flag);
        if (!out.has("parse")) out.addProperty("parse", 0);
        if (!out.has("jx")) out.addProperty("jx", 0);
        if (!out.has("header") || (out.get("header").isJsonObject() && out.getAsJsonObject("header").isEmpty())) out.add("header", GSON.toJsonTree(site.getHeader()));
        return out;
    }

    /** 只改实际 URL 字段；请求头、视频 ID 和签名 query 内嵌字符串不能做全局替换。 */
    static void externalize(JsonObject out, String origin, String key, int port) {
        externalize(out, origin, key, "", port);
    }

    static void externalize(JsonObject out, String origin, String key, String configKey, int port) {
        for (String field : new String[]{"url", "urls", "playUrl", "subs", "drm"}) {
            if (out.has(field)) out.add(field, mediaUrls(out.get(field), origin, key, configKey, port));
        }
        if (out.has("list") && out.get("list").isJsonArray()) {
            for (JsonElement element : out.getAsJsonArray("list")) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (item.has("vod_pic")) item.add("vod_pic", mediaUrls(item.get("vod_pic"), origin, key, configKey, port));
            }
        }
    }

    private static JsonElement mediaUrls(JsonElement value, String origin, String key, String configKey, int port) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new com.google.gson.JsonPrimitive(remoteUrl(value.getAsString(), origin, key, configKey, port));
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) array.add(mediaUrls(item, origin, key, configKey, port));
            return array;
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String field : new String[]{"url", "v", "values", "server"}) {
                if (object.has(field)) object.add(field, mediaUrls(object.get(field), origin, key, configKey, port));
            }
        }
        return value;
    }

    static String remoteUrl(String url, String origin, String key, int port) {
        return remoteUrl(url, origin, key, "", port);
    }

    /** 本网关代理地址回填：/proxy 同时带上 siteKey 与 configKey，非激活配置的代理也能路由回原站点。 */
    static String remoteUrl(String url, String origin, String key, String configKey, int port) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (port <= 0 || uri.getPort() != port || host == null || uri.getRawUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) return url;
            if (!loopback(host) && !host.equalsIgnoreCase(URI.create(origin).getHost())) return url;
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            if ("/proxy".equals(path) || path.startsWith("/proxy/")) {
                if (!key.isEmpty() && (query == null || !(query.startsWith("siteKey=") || query.contains("&siteKey=")))) {
                    query = (query == null || query.isEmpty() ? "" : query + "&") + "siteKey=" + encode(key);
                }
                if (configKey != null && !configKey.isEmpty() && (query == null || !query.contains("configKey="))) {
                    query = (query == null || query.isEmpty() ? "" : query + "&") + "configKey=" + encode(configKey);
                }
            }
            return origin + path + (query == null ? "" : "?" + query) + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (IllegalArgumentException ignored) {
            return url;
        }
    }

    /** 老式 JAR 的代理在集中 Proxy 类上；强加 siteKey 会绕过 JarLoader 的兼容分派。 */
    static boolean supportsDirectProxy(Spider spider) {
        try {
            return spider.getClass().getMethod("proxy", Map.class).getDeclaringClass() != Spider.class;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean loopback(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "[::1]".equals(host) || "::1".equals(host);
    }

    static boolean isSelfGateway(String api, String origin, int port) {
        try {
            URI uri = URI.create(api);
            String path = uri.getPath();
            if (uri.getHost() == null || path == null || !(PATH.equals(path) || path.startsWith(PATH + "/"))) return false;
            return (loopback(uri.getHost()) && uri.getPort() == port) || uri.getRawAuthority().equalsIgnoreCase(URI.create(origin).getRawAuthority());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** 苹果CMS 列表信封。 */
    static JsonObject macCms(Result result, int page) {
        Result safe = result == null ? Result.empty() : result;
        JsonArray list = new JsonArray();
        for (Vod vod : safe.getList()) list.add(vodJson(vod));
        int limit = safe.getList().isEmpty() ? DEFAULT_LIMIT : safe.getList().size();
        int pageCount = safe.getPageCount() > 0 ? safe.getPageCount() : safe.getList().isEmpty() ? Math.max(1, page) : DEFAULT_PAGE_COUNT;
        JsonObject out = new JsonObject();
        out.addProperty("page", page);
        out.addProperty("pagecount", pageCount);
        out.addProperty("limit", limit);
        out.addProperty("total", (long) pageCount * limit);
        out.add("list", list);
        JsonArray types = new JsonArray();
        for (Class type : safe.getTypes()) types.add(typeJson(type));
        if (!types.isEmpty()) out.add("class", types);
        JsonElement filters = GSON.toJsonTree(safe.getFilters());
        if (filters.isJsonObject() && !filters.getAsJsonObject().isEmpty()) out.add("filters", filters);
        return out;
    }

    /** 苹果CMS 播放信封：给 T4 客户端一条已解析好的可播放地址。 */
    static JsonObject play(Result result, String flag) {
        Result safe = result == null ? Result.empty() : result;
        JsonObject out = new JsonObject();
        prop(out, "url", safe.getRealUrl());
        prop(out, "flag", safe.getFlag().isEmpty() ? flag : safe.getFlag());
        out.addProperty("parse", safe.getParse());
        out.addProperty("jx", safe.getJx());
        String format = safe.getFormat();
        if (!TextUtils.isEmpty(format)) prop(out, "format", format);
        JsonElement header = GSON.toJsonTree(safe.getHeader());
        if (header.isJsonObject() && !header.getAsJsonObject().isEmpty()) out.add("header", header);
        if (safe.getDrm() != null) out.add("drm", GSON.toJsonTree(safe.getDrm()));
        if (!safe.getSubs().isEmpty()) out.add("subs", GSON.toJsonTree(safe.getSubs()));
        if (safe.getUrl().isMulti()) {
            JsonArray urls = new JsonArray();
            for (Value value : safe.getUrl().getValues()) urls.add(value.getV());
            out.add("urls", urls);
        }
        if (safe.hasMsg()) prop(out, "msg", safe.getMsg());
        return out;
    }

    /** 单个视频条目，只输出公开的苹果CMS 字段，不泄漏站点配置等内部状态。 */
    static JsonObject vodJson(Vod vod) {
        JsonObject item = new JsonObject();
        prop(item, "vod_id", vod.getId());
        prop(item, "vod_name", vod.getName());
        prop(item, "type_id", "");
        prop(item, "type_name", vod.getTypeName());
        prop(item, "vod_pic", vod.getPic());
        prop(item, "vod_remarks", vod.getRemarks());
        prop(item, "vod_year", vod.getYear());
        prop(item, "vod_area", vod.getArea());
        prop(item, "vod_director", vod.getDirector());
        prop(item, "vod_actor", vod.getActor());
        prop(item, "vod_content", vod.getContent());
        prop(item, "vod_play_from", playFrom(vod));
        prop(item, "vod_play_url", playUrl(vod));
        String action = vod.getAction();
        if (!TextUtils.isEmpty(action)) prop(item, "action", action);
        return item;
    }

    static JsonObject typeJson(Class type) {
        JsonObject item = new JsonObject();
        prop(item, "type_id", type.getTypeId());
        prop(item, "type_name", type.getTypeName());
        if (!type.getTypeFlag().isEmpty()) prop(item, "type_flag", type.getTypeFlag());
        return item;
    }

    /** 字段可能来自爬虫的原始 JSON，缺失时是 null；统一转成空串，避免写响应时 NPE。 */
    private static void prop(JsonObject target, String key, String value) {
        target.addProperty(key, value == null ? "" : value);
    }

    /**
     * 线路名。T3 爬虫常只回 {@code flags} 数组而不回 {@code vod_play_from}，
     * 直接透传会让 T4 客户端看到空线路，所以这里按苹果CMS 的 {@code $$$} 规则回填。
     */
    static String playFrom(Vod vod) {
        String raw = vod.getPlayFrom();
        if (!raw.isEmpty()) return raw;
        List<String> flags = new ArrayList<>();
        for (Flag flag : vod.getFlags()) if (flag != null) flags.add(flag.getFlag());
        return String.join("$$$", flags);
    }

    /** 播放地址，回填规则同 {@link #playFrom(Vod)}，集与集之间用 {@code #}。 */
    static String playUrl(Vod vod) {
        String raw = vod.getPlayUrl();
        if (!raw.isEmpty()) return raw;
        List<String> groups = new ArrayList<>();
        for (Flag flag : vod.getFlags()) {
            if (flag == null) continue;
            if (flag.getEpisodes() == null || flag.getEpisodes().isEmpty()) {
                groups.add(flag.getUrls());
                continue;
            }
            List<String> episodes = new ArrayList<>();
            for (Episode episode : flag.getEpisodes()) if (episode != null) episodes.add(episode.getName() + "$" + episode.getUrl());
            groups.add(String.join("#", episodes));
        }
        return String.join("$$$", groups);
    }

    /** T4 配置：站点 {@code api} 指回本网关，因此整条链路不再依赖外部脚本。 */
    static JsonObject configJson(List<Site> sites, String baseUrl, List<Doh> doh, List<Rule> rules) {
        return configJson(sites, baseUrl, doh, rules, "");
    }

    /**
     * 同上，但把配置标识写进每个站点 api：网关据此服务非激活配置，
     * 客户端后续的 /proxy 请求也能带着它回到同一条配置。
     */
    static JsonObject configJson(List<Site> sites, String baseUrl, List<Doh> doh, List<Rule> rules, String configKey) {
        String suffix = configKey == null || configKey.isEmpty() ? "" : "&configKey=" + encode(configKey);
        JsonObject config = new JsonObject();
        config.addProperty("spider", "");
        config.addProperty("wallpaper", "");
        config.addProperty("warningText", WARNING);
        JsonArray array = new JsonArray();
        if (sites != null) {
            for (Site site : sites) {
                if (site == null || site.getKey().isEmpty()) continue;
                JsonObject item = new JsonObject();
                item.addProperty("key", site.getKey());
                item.addProperty("name", site.getName());
                item.addProperty("api", baseUrl + "?key=" + encode(site.getKey()) + suffix);
                item.addProperty("type", "4");
                item.addProperty("searchable", site.isSearchable() ? 1 : 0);
                item.addProperty("quickSearch", site.isQuickSearch() ? 1 : 0);
                item.addProperty("filterable", 1);
                array.add(item);
            }
        }
        config.add("sites", array);
        config.add("doh", doh == null ? new JsonArray() : GSON.toJsonTree(doh));
        config.add("rules", rules == null ? new JsonArray() : GSON.toJsonTree(rules));
        config.add("lives", new JsonArray());
        return config;
    }

    /** 筛选参数：base64 {@code ext} → JSON {@code extend} → 显式字段，后者覆盖前者。 */
    static HashMap<String, String> extendFrom(JsonObject input) {
        HashMap<String, String> extend = new HashMap<>();
        putAll(extend, decodeExtend(text(input, "ext")));
        JsonElement raw = input.get("extend");
        putAll(extend, raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : jsonObject(text(input, "extend")));
        for (String field : FIELDS) {
            String value = text(input, field);
            if (!value.isEmpty()) extend.put(field, value);
        }
        return extend;
    }

    private static void putAll(HashMap<String, String> target, JsonObject source) {
        if (source == null) return;
        for (String key : source.keySet()) {
            JsonElement value = source.get(key);
            if (value != null && value.isJsonPrimitive()) target.put(key, value.getAsString());
        }
    }

    /** TVBox 的 T4 约定：URL-safe base64 的 JSON。 */
    static JsonObject decodeExtend(String ext) {
        if (TextUtils.isEmpty(ext)) return null;
        try {
            ByteString decoded = ByteString.decodeBase64(ext);
            return decoded == null ? null : jsonObject(decoded.utf8());
        } catch (Throwable e) {
            return null;
        }
    }

    static JsonObject jsonObject(String json) {
        if (TextUtils.isEmpty(json)) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static JsonObject input(IHTTPSession session, Map<String, String> files) {
        JsonObject input = new JsonObject();
        for (Map.Entry<String, String> entry : session.getParms().entrySet()) input.addProperty(entry.getKey(), entry.getValue());
        String post = files == null ? null : files.get("postData");
        JsonObject body = jsonObject(post);
        if (post != null && !post.isEmpty() && body == null) throw new IllegalArgumentException("POST body 必须是 JSON 对象");
        if (body != null) for (String key : body.keySet()) input.add(key, body.get(key));
        return input;
    }

    private static String origin(IHTTPSession session) {
        String host = session.getHeaders().get("host");
        if (TextUtils.isEmpty(host)) host = "127.0.0.1:" + com.github.catvod.Proxy.getPort();
        URI uri = URI.create("http://" + host);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null || uri.getRawFragment() != null) throw new IllegalArgumentException("无效的 Host");
        return "http://" + host;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String text(JsonObject input, String key) {
        if (input == null || !input.has(key)) return "";
        JsonElement element = input.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        return element.getAsString();
    }

    /** {@code ids} 允许逗号分隔或 JSON 数组（POST body）。 */
    static String csv(JsonObject input, String key) {
        if (input == null || !input.has(key)) return "";
        JsonElement element = input.get(key);
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonElement item : element.getAsJsonArray()) {
                if (item == null || item.isJsonNull()) continue;
                if (builder.length() > 0) builder.append(",");
                builder.append(item.getAsString());
            }
            return builder.toString();
        }
        return element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static boolean truthy(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    static JsonObject error(String msg) {
        JsonObject result = new JsonObject();
        result.addProperty("code", -1);
        result.addProperty("msg", msg == null ? "" : msg);
        return result;
    }

    private static Response json(JsonObject object) {
        return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", object.toString()));
    }

    private static Response cors(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return response;
    }
}
