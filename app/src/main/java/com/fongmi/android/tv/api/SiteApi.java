package com.fongmi.android.tv.api;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.utils.PushParser;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.VodDetailCache;
import com.fongmi.android.tv.web.WebHomeInlineVodStore;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Response;

public class SiteApi {

    public static final String PUSH = "push_agent";

    public static String call(@NonNull Site site, @NonNull ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        Call call = site.getExt().length() <= 1000 ? OkHttp.newCall(site.getApi(), site.getHeader(), params) : OkHttp.newCall(site.getApi(), site.getHeader(), OkHttp.toBody(params));
        try (Response response = call.execute()) {
            return response.body().string();
        }
    }

    private static boolean isSpider(@NonNull Site site) {
        return site.getType() == 3;
    }

    private static String ac(int type) {
        return type == 0 ? "videolist" : "detail";
    }

    @NonNull
    public static Result homeContent(@NonNull Site site) throws Exception {
        long start = System.currentTimeMillis();
        try {
            Result result = homeContentRaw(site);
            SiteHealthStore.recordHome(site, true, result.getList().size(), System.currentTimeMillis() - start, "");
            return result;
        } catch (Exception e) {
            SiteHealthStore.recordHome(site, false, 0, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    @NonNull
    private static Result homeContentRaw(@NonNull Site site) throws Exception {
        if (isSpider(site)) {
            Spider spider = site.recent().spider();
            boolean crash = Prefers.getBoolean("crash");
            String home = crash ? "" : spider.homeContent(true);
            String video = crash ? "" : spider.homeVideoContent();
            Prefers.put("crash", false);
            SpiderDebug.log("home", home);
            SpiderDebug.log("homeVideo", video);
            Result result = Result.fromJson(home);
            List<Vod> list = Result.fromJson(video).getList();
            if (!list.isEmpty()) result.setList(list);
            setTypes(site, result);
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("filter", "true");
            String homeContent = call(site.fetchExt(), params);
            SpiderDebug.log("home", homeContent);
            Result result = Result.fromJson(homeContent);
            setTypes(site, result);
            return result;
        } else {
            try (Response response = OkHttp.newCall(site.getApi(), site.getHeader()).execute()) {
                String homeContent = response.body().string();
                SpiderDebug.log("home", homeContent);
                Result result = Result.fromType(site.getType(), homeContent);
                fetchPic(site, result);
                setTypes(site, result);
                return result;
            }
        }
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) throws Exception {
        long start = System.currentTimeMillis();
        try {
            Result result = categoryContentRaw(key, tid, page, filter, extend);
            SiteHealthStore.recordCategory(key, true, result.getList().size(), System.currentTimeMillis() - start, "");
            return result;
        } catch (Exception e) {
            SiteHealthStore.recordCategory(key, false, 0, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    @NonNull
    private static Result categoryContentRaw(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) throws Exception {
        SpiderDebug.log("category", "key=%s,tid=%s,page=%s,filter=%s,extend=%s", key, tid, page, filter, extend);
        Site site = VodConfig.get().getSite(key);
        if (isSpider(site)) {
            String categoryContent = site.recent().spider().categoryContent(tid, page, filter, extend);
            SpiderDebug.log("category", categoryContent);
            return Result.fromJson(categoryContent);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
            if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
            params.put("ac", ac(site.getType()));
            params.put("t", tid);
            params.put("pg", page);
            String categoryContent = call(site, params);
            SpiderDebug.log("category", categoryContent);
            return Result.fromType(site.getType(), categoryContent);
        }
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id) throws Exception {
        return detailContent(key, id, false);
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id, boolean refresh) throws Exception {
        SpiderDebug.log("detail", "key=%s,id=%s,refresh=%s", key, id, refresh);
        if (WebHomeInlineVodStore.KEY.equals(key)) return WebHomeInlineVodStore.detail(id);
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty() && PUSH.equals(key)) {
            Vod vod = buildPushVod(id);
            Source.get().parse(vod.setFlags());
            return Result.vod(vod);
        } else if (isSpider(site)) {
            String detailContent = site.recent().spider().detailContent(Arrays.asList(id));
            SpiderDebug.log("detail", detailContent);
            Result result = Result.fromJson(detailContent);
            Source.get().parse(result.getVod().setFlags());
            return result;
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("ac", ac(site.getType()));
            params.put("ids", requestId);
            String detailContent = call(site, params);
            TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(detailContent);
            SpiderDebug.log("detail", ingress.getSanitizedJson());
            result = Result.fromType(site.getType(), ingress.getSanitizedJson());
        }
        Source.get().parse(result.getVod().setFlags());
        result = applyPushTitle(push, result);
        cacheDetail(key, sourceKey, id, result, beforeSpider);
        return result;
    }


    /**
     * 把详情结果写进缓存，除非这条不该缓存。
     *
     * <p>设置类入口的实际工作发生在 {@code detailContent} <b>里面</b>——弹输入框、写配置、扫码、
     * 请求宿主开网页。缓存命中时压根不调 spider，那些副作用就再也不发生：第二次点击弹窗不出、
     * 网页不开，宿主还会拿着占位结果直奔播放页。这正是「设置类入口只有第一次点击生效，
     * 第二次进播放页；过 5 分钟又能点一次」的由来。
     *
     * <p>三道挡板，各挡一种形态：
     * <ul>
     *   <li>{@link CatAction#blank}：什么都没有的条目（猫源老式动作项），缓存它也没意义
     *   <li>{@link #hasMetadata}：只有占位线路、没有元数据的条目（设置类站点的签名）
     *   <li>{@link CatWebEvent#requestedAfter}：这次调用顺带开过网页，那是副作用
     * </ul>
     * 前两道看结果形状，第三道看副作用——形状千变万化，副作用是确凿的，两类都要。
     *
     * @param beforeSpider 调 spider 之前的时刻。判定用 {@code >=}，所以同一毫秒内的陈旧开页
     *                     记录会让这条详情也躲过缓存——那个方向是安全的，代价只是下次点击
     *                     多走一次 spider。并发的另一次开页同理。
     */
    private static void cacheDetail(String key, String sourceKey, String id, Result result, long beforeSpider) {
        if (result.getList().isEmpty() || CatAction.blank(result.getVod())) return;
        if (!hasMetadata(result.getVod())) {
            SpiderDebug.log("detail-cache", "skip key=%s,id=%s reason=noMetadata", key, id);
            return;
        }
        if (result.getVod().getFlags().isEmpty()) {
            SpiderDebug.log("detail-cache", "skip key=%s,id=%s reason=noPlayableContent", key, id);
            return;
        }
        if (CatWebEvent.requestedAfter(beforeSpider)) {
            SpiderDebug.log("detail-cache", "skip key=%s,id=%s reason=webOpened", key, id);
            return;
        }
        String content = result.toString();
        VodDetailCache.putContent(sourceKey, id, content);
        SpiderDebug.log("detail-cache", "store key=%s,id=%s,size=%d", key, id, content.length());
    }

    /**
     * 这份详情带没带元数据——名字、封面、简介，有一项就算。
     *
     * <p>「只有线路、没有元数据」是<b>设置类站点</b>的签名。那类站点把每个设置项做成一个条目，
     * 真正的工作发生在 {@code detailContent} 里——弹输入框、写配置、扫码；返回值只是个占位的
     * 假线路，好让宿主别报错。实测某网盘配置源返回的就是：
     * <pre>{"list":[{"vod_play_from":"Config","vod_play_url":"Config$Config"}],"parse":0,"jx":0}</pre>
     *
     * <p>缓存住这种条目就等于把那个动作阉掉：命中缓存时 {@code detailContent} 压根不被调用，
     * 弹窗再也不出现，宿主还会拿着那个叫 {@code Config} 的假地址直奔播放页——这正是
     * 「设置类入口只有第一次点击生效，第二次进播放页」的由来。
     *
     * <p>{@link CatAction#blank} 挡不住它：{@code vod_play_from}/{@code vod_play_url} 会生成一条
     * Flag，{@code getFlags()} 非空，{@code blank} 就不成立。所以这里<b>刻意不看线路</b>——线路是
     * 占位结果也能轻易凑出来的东西，而元数据不是。
     *
     * <p>代价：真把元数据全放在列表项、detail 只回线路的源，每次进详情都要重新调一次 spider。
     * 那是缓存引入之前的原有行为，不是退步；而反过来把动作缓存掉是功能损坏。
     */
    private static boolean hasMetadata(Vod vod) {
        if (vod == null) return false;
        return !vod.getName().isEmpty()
                || !TextUtils.isEmpty(vod.getPic())
                || !TextUtils.isEmpty(vod.getContent());
    }

    private static String detailCacheSourceKey(String key, Site site) {
        if (site == null) return key;
        int signature = Objects.hash(site.getType(), site.getApi(), site.getExt(), site.getHeader());
        return key + "#" + Integer.toHexString(signature);
    }

    private static Result applyPushTitle(PushParser.Parsed push, Result result) {
        if (push == null || TextUtils.isEmpty(push.getTitle()) || result.getList().isEmpty()) return result;
        result.getVod().setName(push.getTitle());
        return result;
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id) throws Exception {
        return playerContent(key, flag, id, PlayerSetting.getActivePlayer());
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id, int playerType) throws Exception {
        return playerContent(key, flag, id, playerType, Source.get(), true);
    }

    @NonNull
    public static Result playerContentIsolated(@NonNull String key, @NonNull String flag, @NonNull String id) throws Exception {
        return playerContentIsolated(key, flag, id, PlayerSetting.getActivePlayer());
    }

    @NonNull
    public static Result playerContentIsolated(@NonNull String key, @NonNull String flag, @NonNull String id, int playerType) throws Exception {
        return playerContent(key, flag, id, playerType, new Source(), false);
    }

    @NonNull
    private static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id, int playerType, @NonNull Source source, boolean stopSource) throws Exception {
        SpiderDebug.log("player", "key=%s,flag=%s,id=%s", key, flag, id);
        if (stopSource) source.stop();
        if (WebHomeInlineVodStore.KEY.equals(key)) return WebHomeInlineVodStore.player(flag, id);
        Site site = VodConfig.get().getSite(key);
        String requestId = PUSH.equals(key) ? resolvePushPlayerUrl(id) : id;
        if (PUSH.equals(key) && (site.isEmpty() || isLocalFileUrl(requestId))) return pushPlayer(flag, requestId, playerType, source);
        if (site.getType() == 3) {
            String fallbackReason;
            try {
                String playerContent = site.recent().spider().playerContent(flag, requestId, VodConfig.get().getFlags());
                SpiderDebug.log("player", playerContent);
                Result result = Result.fromJson(playerContent);
                if (shouldFallbackPushSiteResult(key, result)) {
                    fallbackReason = result == null ? "" : result.getMsg();
                } else {
                    if (result.getFlag().isEmpty()) result.setFlag(flag);
                    result.setUrl(source.fetch(result, playerType));
                    result.setHeader(site.getHeader());
                    result.setKey(key);
                    return result;
                }
            } catch (Exception e) {
                if (PUSH.equals(key)) fallbackReason = e.getMessage();
                else throw e;
            }
            return fallbackPushPlayer(flag, requestId, playerType, fallbackReason, source);
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("play", id);
            params.put("flag", flag);
            String playerContent = call(site, params);
            SpiderDebug.log("player", playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result, playerType));
            result.setHeader(site.getHeader());
            return result;
        } else if (site.isEmpty() && PUSH.equals(key)) {
            Result result = buildPushPlayer(flag, id);
            result.setUrl(Source.get().fetch(result, playerType));
            SpiderDebug.log("player", result.toString());
            return result;
        } else {
            Result result = new Result();
            result.setUrl(requestId);
            result.setFlag(flag);
            result.setHeader(site.getHeader());
            result.setPlayUrl(site.getPlayUrl());
            result.setParse(Sniffer.isVideoFormat(requestId) && result.getPlayUrl().isEmpty() ? 0 : 1);
            result.setUrl(source.fetch(result, playerType));
            SpiderDebug.log("player", result.toString());
            return result;
        }
    }

    static String resolvePushPlayerUrl(String id) {
        return PushParser.fromId(id).getUrl();
    }

    static boolean isLocalFileUrl(String url) {
        return url.regionMatches(true, 0, "file:", 0, 5);
    }

    static boolean shouldSniffPushUrl(String url) {
        if (!shouldSniffPushUrl(url, false)) return false;
        return !Sniffer.isVideoFormat(url);
    }

    static boolean shouldSniffPushUrl(String url, boolean videoFormat) {
        if (url == null || url.isEmpty() || isLocalFileUrl(url)) return false;
        boolean webUrl = url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8);
        return webUrl && !videoFormat;
    }

    static boolean shouldFallbackPushSiteResult(String key, Result result) {
        return PUSH.equals(key) && (result == null || result.hasMsg() || result.getUrl().isEmpty());
    }

    private static Result fallbackPushPlayer(String flag, String url, int playerType, String reason, Source source) throws Exception {
        SpiderDebug.log("player", "push site fallback reason=%s", TextUtils.isEmpty(reason) ? "empty result" : reason);
        return pushPlayer(flag, url, playerType, source);
    }

    private static Result pushPlayer(String flag, String url, int playerType, Source source) throws Exception {
        Result result = new Result();
        result.setUrl(url);
        result.setParse(shouldSniffPushUrl(url) ? 1 : 0);
        result.setFlag(flag);
        result.setUrl(source.fetch(result, playerType));
        SpiderDebug.log("player", result.toString());
        return result;
    }

    private static Result pushDetail(@NonNull String id, PushParser.Parsed push) throws Exception {
        Vod vod = new Vod();
        vod.setId(id);
        vod.setName(push.getName());
        vod.setPlayUrl(push.getUrl());
        vod.setPlayFrom(ResUtil.getString(R.string.push));
        vod.setPic(ResUtil.getString(R.string.push_image));
        Source.get().parse(vod.setFlags());
        return Result.vod(vod);
    }

    @NonNull
    public static Result searchContent(@NonNull Site site, @NonNull String keyword, boolean quick, @NonNull String page) throws Exception {
        SpiderDebug.log("search", "site=%s,keyword=%s,quick=%s,page=%s", site.getName(), keyword, quick, page);
        boolean hasPage = !page.equals("1");
        if (isSpider(site)) {
            String searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
            SpiderDebug.log("search", searchContent);
            Result result = Result.fromJson(searchContent);
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", keyword);
            params.put("quick", String.valueOf(quick));
            params.put("extend", "");
            if (hasPage) params.put("pg", page);
            String searchContent = call(site, params);
            SpiderDebug.log("search", searchContent);
            Result result = fetchPic(site, Result.fromType(site.getType(), searchContent));
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        }
    }

    @NonNull
    public static Result action(@NonNull String key, @NonNull String action) throws Exception {
        Site site = VodConfig.get().getSite(key);
        SpiderDebug.log("action", "key=%s,action=%s", key, action);
        if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
        if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
        return Result.empty();
    }

    @NonNull
    public static Result fetchPic(@NonNull Site site, @NonNull Result result) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || !result.getVod().getPic().isEmpty()) return result;
        ArrayList<String> ids = new ArrayList<>();
        boolean empty = site.getCategories().isEmpty();
        for (Vod item : result.getList()) if (empty || site.getCategories().contains(item.getTypeName())) ids.add(item.getId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", ac(site.getType()));
        params.put("ids", TextUtils.join(",", ids));
        try (Response response = OkHttp.newCall(site.getApi(), site.getHeader(), params).execute()) {
            result.setList(Result.fromType(site.getType(), response.body().string()).getList());
            return result;
        }
    }

    private static Vod buildPushVod(String id) {
        // 推送串可能带请求头：url@Headers=<json>。真实地址与请求头在这里拆开，
        // 请求头缓存起来（详情先于播放执行），播放时由 buildPushPlayer 注入 Result.header。
        String base = Sniffer.pushUrl(id);
        Map<String, String> headers = Sniffer.pushHeaders(id);
        if (!headers.isEmpty()) rememberPushHeaders(base, headers);
        Vod vod = new Vod();
        vod.setId(id);
        vod.setPic(ResUtil.getString(R.string.push_image));
        vod.setName(base.startsWith("file://") ? new java.io.File(base).getName() : "");
        String url = base.startsWith("file://") ? toLanHttpUrl(base) : (base.contains("://") && base.contains("#") && !base.contains("$") ? base.replace("#", "***") : base);
        if (isThunderUrl(url)) {
            vod.setPlayUrl(url);
            vod.setPlayFrom("迅雷");
        } else if (url.contains("youtube.com")) {
            vod.setPlayUrl(url);
            vod.setPlayFrom("YouTube");
        } else if (url.contains("$")) {
            // 剧集包：集名$地址，多集用 # 或换行分隔。
            // 地址往往是大纲页/播放页而不是直链，线路名必须给"嗅探"，
            // 否则 buildPushPlayer 走 default 强制 parse=0，远端会把 HTML 当视频播。
            String playUrl = url.contains("\n") ? TextUtils.join("#", url.split("\n")) : url;
            vod.setPlayFrom(Sniffer.isVideoFormat(firstEpisodeUrl(playUrl)) ? "直链" : "嗅探");
            vod.setPlayUrl(playUrl);
        } else {
            vod.setPlayUrl(TextUtils.join("$$$", Arrays.asList(url, url, url)));
            if (Sniffer.isVideoFormat(url)) vod.setPlayFrom(TextUtils.join("$$$", Arrays.asList("直链", "嗅探", "解析")));
            else vod.setPlayFrom(TextUtils.join("$$$", Arrays.asList("嗅探", "直链", "解析")));
        }
        return vod;
    }
 
    /** 取剧集包推送串里第一集的地址：集名$地址#集名$地址 -> 地址。 */
    private static String firstEpisodeUrl(String playUrl) {
        if (TextUtils.isEmpty(playUrl)) return "";
        String first = playUrl.split("#")[0];
        int index = first.indexOf('$');
        return index >= 0 ? first.substring(index + 1).trim() : first.trim();
    }
 
    private static String toLanHttpUrl(String url) {
        if (TextUtils.isEmpty(url) || !url.startsWith("file://")) return url;
        String filePath = url.substring(7);
        String relative = stripStorageRoot(filePath);
        StringBuilder encodedPath = new StringBuilder();
        for (String segment : relative.split("/")) {
            if (segment.isEmpty()) {
                encodedPath.append("/");
            } else {
                encodedPath.append("/").append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        String lan = Server.get().getAddress(false);
        if (lan.contains("http://:")) lan = Server.get().getAddress(true);
        return lan + "/file" + encodedPath;
    }

    private static String stripStorageRoot(String path) {
        String extRoot = com.github.catvod.utils.Path.rootPath();
        if (path.startsWith(extRoot + "/")) return path.substring(extRoot.length() + 1);
        if (path.startsWith("/sdcard/")) return path.substring(8);
        if (path.startsWith(extRoot)) return path.substring(extRoot.length());
        if (path.startsWith("/sdcard")) return path.substring(7);
        return path;
    }
 
    private static Result buildPushPlayer(String flag, String id) {
        Result result = new Result();
        if (id.contains("://") && id.contains("***")) id = id.replace("***", "#");
        result.setFlag(flag);
        result.setUrl(id);
        Map<String, String> headers = recallPushHeaders(id);
        if (!headers.isEmpty()) result.setHeader(headers);
        switch (flag) {
            case "直链" -> result.setParse(0);
            case "解析" -> result.setParse(1);
            case "嗅探" -> result.setParse(1);
            default -> result.setParse(0);
        }
        return result;
    }
 
    /**
     * 投屏（/action?do=cast）只会把 History 发给远端，而 webhome_inline 的 VOD
     * 存在发送端进程内，远端必然 "WebHome inline VOD not found"。
     * 这里把本机直连的历史改写成自包含的 push_agent 推送串（真实地址 + 请求头），
     * 远端用同一套 PUSH 链路即可直接播放。
     */
    public static History castHistory(History history, String url, Map<String, String> headers) {
        if (history == null || !WebHomeInlineVodStore.KEY.equals(history.getSiteKey())) return history;
        String base = Sniffer.gatewayTarget(url);
        if (TextUtils.isEmpty(base)) base = url;
        if (TextUtils.isEmpty(base)) return history;
        History copy = History.objectFrom(history.toString());
        if (copy == null) return history;
        copy.setKey(PUSH + AppDatabase.SYMBOL + Sniffer.pushPayload(base, headers) + AppDatabase.SYMBOL + history.getCid());
        return copy;
    }
 
    private static final Map<String, Map<String, String>> PUSH_HEADERS = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) {
            return size() > 64;
        }
    });
 
    private static void rememberPushHeaders(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url) || headers == null || headers.isEmpty()) return;
        PUSH_HEADERS.put(url, new LinkedHashMap<>(headers));
    }
 
    private static Map<String, String> recallPushHeaders(String url) {
        Map<String, String> headers = PUSH_HEADERS.get(url);
        return headers == null ? Collections.emptyMap() : headers;
    }
 
    private static boolean isThunderUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return url.startsWith("magnet") || url.startsWith("thunder") || url.startsWith("ed2k")
                || (!url.startsWith("magnet") && url.split(";")[0].endsWith(".torrent"));
    }
 
    private static void setTypes(@NonNull Site site, @NonNull Result result) {
        result.getTypes().stream().filter(type -> result.getFilters().containsKey(type.getTypeId())).forEach(type -> type.setFilters(result.getFilters().get(type.getTypeId())));
        if (site.getCategories().isEmpty()) return;
        Map<String, Class> typeByName = new HashMap<>();
        result.getTypes().forEach(type -> typeByName.put(type.getTypeName(), type));
        List<Class> types = site.getCategories().stream().map(typeByName::get).filter(Objects::nonNull).toList();
        if (!types.isEmpty()) result.setTypes(types);
    }
}
