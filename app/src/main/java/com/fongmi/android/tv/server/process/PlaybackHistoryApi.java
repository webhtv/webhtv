package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.playback.PlaybackApi;
import com.fongmi.android.tv.playback.PlaybackRecord;
import com.fongmi.android.tv.playback.ViewingRecordSyncStore;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * 按站点读取播放历史的只读端点。
 *
 * <p>为什么需要它：既有的 {@code /api/playback/current} 读的是
 * {@code PlaybackRuntime.currentHistory()}——一个只在**播放中**有值的内存字段，
 * 未播放或已息屏时恒为 {@code null} 并返回 404。对端（WebHTV PC 的实时同步）
 * 需要的是"这个站点有哪些历史"，而不是"现在正在播什么"，因此必须有一个
 * 从数据库读的端点。
 *
 * <p>设计约束：
 * <ul>
 *   <li><b>只读</b>：不接受 POST，不落库，不触发任何播放副作用；</li>
 *   <li><b>无记录返回空数组</b>（200），而不是 404——"没有历史"与"端点不存在"
 *       必须可区分，否则对端无法判断自己是否该降级；</li>
 *   <li><b>字段裁剪与 {@code /api/playback/current} 一致</b>
 *       （{@link com.fongmi.android.tv.playback.PlaybackFieldPolicy#apiSafe()}），
 *       不因为新增端点而多暴露字段；</li>
 *   <li><b>隐身模式返回空数组</b>：隐身是用户显式表达的"不要记录/不要外泄"意图；</li>
 *   <li><b>观影记录同步关闭时返回 403</b>：与写端点 {@code /api/playback/progress}
 *       同款开关（{@link com.fongmi.android.tv.playback.ViewingRecordSyncStore#isEnabled()}），
 *       关闭后本端点不再向局域网暴露任何历史；</li>
 *   <li><b>siteKey 用精确前缀匹配</b>（{@code substr}），不用 {@code LIKE}——
 *       siteKey 可能含 {@code %} / {@code _}，{@code LIKE} 会误命中。</li>
 * </ul>
 *
 * <p>响应形态（对端按此解析）：
 * <pre>
 * {
 *   "siteKey": "csp_xxx",
 *   "items": [ { ...apiSafe 字段集... } ],
 *   "total": 3
 * }
 * </pre>
 *
 * <p>{@code total} 是**本次响应 items 的长度**，不是该站点的历史总数：
 * 本端点没有分页参数，结果被 {@code limit} 截断（默认 20，上限 100）后一次返回。
 * 对端若看到 {@code total == limit}，不能据此推断"已取全"。
 */
public class PlaybackHistoryApi implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return "/api/playback/history".equals(url) || "/playback/history".equals(url);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() == NanoHTTPD.Method.OPTIONS) return cors(json(Response.Status.NO_CONTENT, ""), session);
        if (session.getMethod() != NanoHTTPD.Method.GET) return cors(error(Response.Status.METHOD_NOT_ALLOWED, 405, "只支持 GET"), session);
        // 与写端点 /api/playback/progress 同款门控：观影记录同步关闭时，本端点会一次
        // 暴露整站最多 100 条历史（vodName/episodeName/positionMs），暴露面远大于
        // /api/playback/current（1 条且要求正在播放），因此必须同样受开关约束。
        if (!ViewingRecordSyncStore.isEnabled()) return cors(error(Response.Status.FORBIDDEN, 403, "观影记录同步未开启"), session);
        Map<String, String> params = session.getParms();
        String siteKey = siteKey(params);
        if (TextUtils.isEmpty(siteKey)) return cors(error(Response.Status.BAD_REQUEST, 400, "siteKey不能为空"), session);
        try {
            List<PlaybackRecord> records = PlaybackApi.history(siteKey, limit(params));
            SpiderDebug.log("playback-api", "history site=%s total=%s", siteKey, records.size());
            return cors(json(Response.Status.OK, payload(siteKey, records).toString()), session);
        } catch (Throwable e) {
            // 同 PlaybackApi.history：SpiderDebug 受 DebugLogStore.enabled（默认 false）门控，
            // 默认发行版里不会落盘，必须补 printStackTrace 才能让 500 在 Logcat 里可查。
            SpiderDebug.log("playback-api", e);
            e.printStackTrace();
            // 不回显 e.getMessage()：本端点在局域网内无需鉴权即可访问，SQLite 异常文本
            // 会泄露表名/列名等结构信息。诊断走上面的 Logcat。
            return cors(error(Response.Status.INTERNAL_ERROR, 500, "读取播放历史失败"), session);
        }
    }

    private JsonObject payload(String siteKey, List<PlaybackRecord> records) {
        JsonObject object = new JsonObject();
        object.addProperty("siteKey", siteKey);
        JsonArray items = new JsonArray();
        // 字段裁剪已在 PlaybackApi.history 内完成；这里只做序列化。
        for (PlaybackRecord record : records) {
            if (record == null) continue;
            items.add(record.toJson(com.fongmi.android.tv.playback.PlaybackFieldPolicy.apiSafe()));
        }
        object.add("items", items);
        object.addProperty("total", items.size());
        return object;
    }

    /**
     * 从查询参数里取 siteKey。
     *
     * <p>包级可见而不是私有：参数归一化是纯逻辑，值得被单测直接锁定，
     * 不必为了测它去 mock 一整个 HTTP session。
     *
     * <p>{@code siteKey} 存在但只有空白时，**也要**回退到 {@code site} 别名：
     * {@code ?siteKey=%20&site=csp_x} 里调用方明确给了别名，若因为主参数是空白就
     * 直接返回空串，端点会报 400 而调用方拿不到任何解释。只有两者都空才返回空串。
     */
    static String siteKey(Map<String, String> params) {
        String value = params.get("siteKey");
        if (TextUtils.isEmpty(value) || TextUtils.isEmpty(value.trim())) value = params.get("site");
        return value == null ? "" : value.trim();
    }

    /**
     * 解析 {@code limit}。非法值（非数字、负数、零）一律回落默认，
     * 而不是报 400——对端传了一个坏参数不该让整次拉取失败。
     */
    static int limit(Map<String, String> params) {
        String value = params.get("limit");
        if (TextUtils.isEmpty(value)) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Response error(Response.Status status, int code, String message) {
        JsonObject object = new JsonObject();
        object.addProperty("code", code);
        object.addProperty("message", TextUtils.isEmpty(message) ? "请求失败" : message);
        return json(status, object.toString());
    }

    private Response json(Response.Status status, String text) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", text);
    }

    private Response cors(Response response, IHTTPSession session) {
        String origin = session.getHeaders().get("origin");
        response.addHeader("Access-Control-Allow-Origin", TextUtils.isEmpty(origin) ? "*" : origin);
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Methods", "GET,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "*");
        response.addHeader("Access-Control-Expose-Headers", "*");
        response.addHeader("Access-Control-Max-Age", "86400");
        response.addHeader("Cache-Control", "no-store");
        return response;
    }
}
