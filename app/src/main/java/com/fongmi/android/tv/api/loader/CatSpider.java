package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.node.NodeRuntime;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 猫源爬虫：把 {@link Spider} 的调用转成对本机 bundle 的 HTTP 请求。
 *
 * <p>CatPawOpen 的 {@code /config} 给出的站点 {@code type} 是 3（在本项目里意味着"本地
 * JS 爬虫"），但它的 {@code api} 指向 bundle 上的 HTTP 路由，方法名接在后面，
 * 统一用 POST + JSON：{@code POST <api>/home}、{@code /category}、{@code /detail}、
 * {@code /play}、{@code /search}。字段沿用它的约定：{@code id}/{@code page}/{@code wd}/
 * {@code filters}/{@code flag}。
 */
public class CatSpider extends Spider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 本机 bundle 的 api 形如 {@code http://127.0.0.1:<port>/spider/<site>/3[/...]}；测试断言形状用。 */
    static final Pattern BUNDLE_BASE = Pattern.compile("^((http|https)://[^/]+)?(/spider/[^/]+/[^/]+(?:/[^/]+)*)$");

    private final String api;

    public CatSpider(String api) {
        this.api = api.endsWith("/") ? api.substring(0, api.length() - 1) : api;
    }

    /** 测试专用：尾斜杠归一后的 api。 */
    String apiForTest() {
        return api;
    }

    /** api 是绝对地址且落在 bundle 的爬虫路由上。 */
    public static boolean matches(String api) {
        if (TextUtils.isEmpty(api)) return false;
        return api.startsWith("http") && api.contains("/spider/");
    }

    /**
     * 每次都取 Node 运行时的当前端口拼请求地址，而不是用固化在 api 里的端口。
     *
     * <p>站点 api 在配置加载时 rebase 成本机 http://127.0.0.1:<旧端口>/spider/... 并驻留内存。
     * 一旦 :node 子进程崩溃被 {@link NodeRuntime} 检测并自动重启，新进程可能占用不同端口
     * （首选 9988 被释放后大概率仍取 9988，但并发占用/端口扫描变化时可能变）。
     * 若请求仍打到旧端口，会得到一个无人监听的连接、白白等完整超时，用户看到
     * 的就是「搜索不出来」，即使 node 已重启也一直失败。所以这里对<b>本机回环 api</b>每次
     * 都用当前端口重建；远端（非本机）api 原样使用，不得被改写到本机。
     */

    @Override
    public void init(Context context, String extend) {
        post("/init", new JsonObject());
    }

    @Override
    public String homeContent(boolean filter) {
        return post("/home", new JsonObject());
    }

    @Override
    public String homeVideoContent() {
        // home 的响应里已经带 list，再单独取一次没有意义
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", tid);
        body.addProperty("page", page(pg));
        if (extend != null && !extend.isEmpty()) {
            JsonObject filters = new JsonObject();
            for (Map.Entry<String, String> entry : extend.entrySet()) filters.addProperty(entry.getKey(), entry.getValue());
            body.add("filters", filters);
        }
        return post("/category", body);
    }

    @Override
    public String detailContent(List<String> ids) {
        JsonObject body = new JsonObject();
        body.addProperty("id", ids == null || ids.isEmpty() ? "" : ids.get(0));
        return post("/detail", body);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        JsonObject body = new JsonObject();
        body.addProperty("wd", key);
        body.addProperty("page", page(pg));
        return post("/search", body);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JsonObject body = new JsonObject();
        body.addProperty("flag", flag);
        body.addProperty("id", id);
        return post("/play", body);
    }

    private int page(String pg) {
        try {
            return TextUtils.isEmpty(pg) ? 1 : Math.max(1, Integer.parseInt(pg.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String post(String path, JsonObject body) {
        String url = resolve(path);
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
            try (Response response = OkHttp.client().newCall(request).execute()) {
                if (response.body() == null) return "";
                String text = response.body().string();
                if (response.code() != 200) {
                    SpiderDebug.log("cat-spider", "%s -> HTTP %s", path, response.code());
                    return "";
                }
                return unwrap(text);
            }
        } catch (Exception e) {
            // 本机 bundle 连接类失败（连接被拒/对端中断）说明 :node 子进程很可能已死：
            // 这没有回调会把 NodeRuntime.running 置假，不处理的话这里会一直白等超时。
            // 交给 NodeRuntime 用原 bundle 异步重启，本次返回空，下次搜索自动走新端口。
            if (isConnectionFailure(e)) NodeRuntime.restartIfDead(App.get());
            SpiderDebug.log("cat-spider", e);
            return "";
        }
    }

    private static boolean isConnectionFailure(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.net.ConnectException) return true;
            if (cause instanceof java.net.SocketTimeoutException) return true;
            if (cause instanceof java.io.IOException && cause.getMessage() != null
                    && (cause.getMessage().contains("unexpected end of stream") || cause.getMessage().contains("Connection refused") || cause.getMessage().contains("Failed to connect"))) return true;
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 把相对路由接到 Node 运行时当前的地址上。
     *
     * <p>api 里固化的端口在 node 崩溃重启后可能失效，这里每次都从 {@link NodeRuntime}
     * 当前端口重建 host,再把相对路由接回去。远端（非本机）api 原样使用。
     */
    private String resolve(String path) {
        Matcher matcher = BUNDLE_BASE.matcher(api);
        if (!matcher.matches()) return api + path;
        String base = matcher.group(1);
        // 远端 T4 猫源（https://…/spider/…）必须原样请求：本地 node 在跑（port>0）时若跟着
        // 本机端口重建，远端站点会被错误改写到 127.0.0.1，表现为远端源全部失效。只有本机
        // bundle（api 在配置加载时被 rebase 成 http://127.0.0.1:<port>）才跟随当前端口。
        if (base != null && !isLoopbackBase(base)) return api + path;
        int port = NodeRuntime.port();
        if (port > 0 && (base == null || !base.endsWith(":" + port))) return "http://127.0.0.1:" + port + matcher.group(3) + path;
        return api + path;
    }

    /** base 是否指向本机回环地址——本机 bundle 的 api 固定是 {@code http://127.0.0.1:<port>}。 */
    static boolean isLoopbackBase(String base) {
        int mark = base.indexOf("://");
        String authority = mark >= 0 ? base.substring(mark + 3) : base;
        int at = authority.lastIndexOf('@');
        String host = at >= 0 ? authority.substring(at + 1) : authority;
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    /** 部分路由把结果包在 {@code {code, data}} 里，取出 data 才是标准结果体。 */
    private String unwrap(String text) {
        if (TextUtils.isEmpty(text)) return "";
        try {
            JsonObject object = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            if (object.has("data") && object.get("data").isJsonObject()) return object.getAsJsonObject("data").toString();
            if (object.has("data") && object.get("data").isJsonArray()) {
                JsonArray array = object.getAsJsonArray("data");
                JsonObject wrapper = new JsonObject();
                wrapper.add("list", array);
                return wrapper.toString();
            }
            return text;
        } catch (Exception ignored) {
            return text;
        }
    }
}
