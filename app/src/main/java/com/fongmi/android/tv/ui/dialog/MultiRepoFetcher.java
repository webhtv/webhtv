package com.fongmi.android.tv.ui.dialog;
 
import android.text.TextUtils;
 
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
 
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.MultiRepo;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
 
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
 
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
 
/**
 * 多仓仓库网络请求工具：支持带缓存标识的增量更新，解析 json / text 格式。
 */
final class MultiRepoFetcher {
 
    private MultiRepoFetcher() {
    }
 
    static void fetchRepo(@NonNull MultiRepo repo, boolean force, @NonNull RepoCallback callback) {
        Task.submit(() -> {
            String url = repo.getUrl();
            if (TextUtils.isEmpty(url)) {
                postError(callback, "仓库链接为空");
                return;
            }
            if (!url.startsWith("http")) url = UrlUtil.convert(url);
            try {
                Request.Builder rb = new Request.Builder().url(url);
                if (!force) {
                    if (!TextUtils.isEmpty(repo.getEtag())) rb.header("If-None-Match", repo.getEtag());
                    if (!TextUtils.isEmpty(repo.getLastModified())) rb.header("If-Modified-Since", repo.getLastModified());
                }
                try (Response response = OkHttp.client().newCall(rb.build()).execute()) {
                    if (response.code() == 304) {
                        String cached = repo.getCache();
                        if (TextUtils.isEmpty(cached)) {
                            postError(callback, "服务器返回 304 但本地无缓存");
                        } else {
                            postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                        }
                        return;
                    }
                    if (!response.isSuccessful()) {
                        String cached = repo.getCache();
                        if (!TextUtils.isEmpty(cached)) {
                            postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                        } else {
                            postError(callback, "仓库请求失败: HTTP " + response.code());
                        }
                        return;
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        String cached = repo.getCache();
                        if (!TextUtils.isEmpty(cached)) {
                            postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                        } else {
                            postError(callback, "仓库响应体为空");
                        }
                        return;
                    }
                    String content = body.string();
                    String newEtag = firstNonEmpty(response.header("ETag"), response.header("etag"));
                    String newLastModified = firstNonEmpty(response.header("Last-Modified"), response.header("last-modified"));
                    postSuccess(callback, repo, content, newEtag, newLastModified, false);
                }
            } catch (Throwable e) {
                String cached = repo.getCache();
                if (!TextUtils.isEmpty(cached)) {
                    postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                } else {
                    postError(callback, "仓库请求异常: " + e.getMessage());
                }
            }
        });
    }
 
    private static String firstNonEmpty(String a, String b) {
        if (!TextUtils.isEmpty(a)) return a;
        if (!TextUtils.isEmpty(b)) return b;
        return "";
    }
 
    private static void postSuccess(RepoCallback callback, MultiRepo repo, String content, String etag, String lastModified, boolean fromCache) {
        App.post(() -> callback.onSuccess(new RepoResult(repo, content, etag, lastModified, fromCache)));
    }
 
    private static void postError(RepoCallback callback, String msg) {
        App.post(() -> callback.onError(msg));
    }
 
    /**
     * 解析仓库内容为接口 Depot 列表。支持 json 和 text 两种格式。
     */
    @NonNull
    static List<Depot> parseRepoContent(@Nullable String content) {
        List<Depot> result = new ArrayList<>();
        if (TextUtils.isEmpty(content)) return result;
        try {
            String trimmed = content.trim();
            // 清除BOM头
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1);
            }
            // 提取主体JSON，剔除前后HTML/无关文本
            boolean quickMatch = false;
            if (trimmed.length() > 2) {
                 int lastValidIdx = trimmed.length() - 1;
                 // 向前跳过末尾空白字符
                 while (lastValidIdx >= 0 && Character.isWhitespace(trimmed.charAt(lastValidIdx))) {
                     lastValidIdx--;
                 }
                 if (lastValidIdx > 0) {
                     char first = trimmed.charAt(0);
                     char last = trimmed.charAt(lastValidIdx);
                     if ((first == '{' && last == '}') || (first == '[' && last == ']')) {
                         quickMatch = true;
                     }
                 }
            }
            if (!quickMatch) {
                 int idxBrace = trimmed.indexOf('{');
                 int idxBracket = trimmed.indexOf('[');
                 if (idxBracket != -1 && (idxBrace == -1 || idxBracket < idxBrace)) {
                     trimmed = extractInnerJsonBlock(trimmed, '[', ']');
                 } else {
                     trimmed = extractInnerJsonBlock(trimmed, '{', '}');
                 }
            }
            Set<String> urlUniqueSet = new HashSet<>();
            if (trimmed.startsWith("{")) {
                // 判断是否存在多个对象拼接（} 后面空白字符，然后 {）
                if (hasConcatJsonObject(trimmed)) {
                    String fixedStr = fixConcatJsonObject(trimmed);
                    com.google.gson.JsonArray arr = Json.parse(fixedStr).getAsJsonArray();
                    for (com.google.gson.JsonElement item : arr) {
                        if (!item.isJsonObject()) continue;
                        com.google.gson.JsonObject obj = item.getAsJsonObject();
                        addDepotFromJsonObj(obj, result, urlUniqueSet);
                    }
                } else {
                    com.google.gson.JsonObject obj = Json.parse(trimmed).getAsJsonObject();
                    addDepotFromJsonObj(obj, result, urlUniqueSet);
                }
            } else if (trimmed.startsWith("[")) {
                List<Depot> tempList = Depot.arrayFrom(trimmed);
                for (Depot depot : tempList) {
                    addDepot(depot, result, urlUniqueSet);
                }
            } else {
                // 文本格式 name,url
                JsonArray arr = new JsonArray();
                String[] lines = trimmed.split("\\r?\\n");
                for (String line : lines) {
                    String t = line.trim();
                    if (TextUtils.isEmpty(t) || t.startsWith("#")) continue;
                    int firstComma = t.indexOf(',');
                    if (firstComma <= 0) continue;
                    String name = t.substring(0, firstComma).trim();
                    String url = t.substring(firstComma + 1).trim();
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)) continue;
                    JsonObject o = new JsonObject();
                    o.addProperty("name", name);
                    o.addProperty("url", url);
                    arr.add(o);
                }
                result.addAll(Depot.arrayFrom(arr.toString()));
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 提取文本中第一个成对括号包裹的JSON块，剔除前后无关垃圾字符
     * @param raw 原始文本
     * @param open 起始字符 '{' 或 '['
     * @param close 闭合字符 '}' 或 ']'
     * @return 截取后的完整json字符串；找不到返回原文本
     */
    private static String extractInnerJsonBlock(String raw, char open, char close) {
        int start = raw.indexOf(open);
        if (start == -1) return raw;
        int count = 0;
        int end = -1;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == open) {
                count++;
            } else if (c == close) {
                count--;
                if (count == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end == -1) return raw;
        return raw.substring(start, end + 1);
    }

    /**
     * 检查是否存在：} + 任意空白字符 + {，代表多个json对象拼接
     */
    private static boolean hasConcatJsonObject(String raw) {
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            if (raw.charAt(i) == '}') {
                int j = i + 1;
                // 跳过所有空白字符 空格 \n \r \t
                while (j < len && Character.isWhitespace(raw.charAt(j))) {
                    j++;
                }
                if (j < len && raw.charAt(j) == '{') {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 修复多对象拼接：}{中间有空白的情况，转为 },{
     */
    private static String fixConcatJsonObject(String raw) {
        StringBuilder sb = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            char c = raw.charAt(i);
            sb.append(c);
            if (c == '}') {
                int j = i + 1;
                while (j < len && Character.isWhitespace(raw.charAt(j))) {
                    j++;
                }
                if (j < len && raw.charAt(j) == '{') {
                    sb.append(',');
                }
            }
        }
        return "[" + sb + "]";
    }
    
    /**
     * 解析单个JsonObject，两种结构：{urls:[...]} 或者单条 {name,url}
     */
    private static void addDepotFromJsonObj(com.google.gson.JsonObject obj, List<Depot> result, Set<String> urlUniqueSet) {
        if (obj.has("urls")) {
            List<Depot> list = Depot.arrayFrom(obj.getAsJsonArray("urls").toString());
            for (Depot depot : list) {
                addDepot(depot, result, urlUniqueSet);
            }
        } else if (obj.has("name") && obj.has("url")) {
            Depot single = App.gson().fromJson(obj.toString(), Depot.class);
            addDepot(single, result, urlUniqueSet);
        }
    }
    
    /**
     * 添加Depot，URL去重，过滤空url
     */
    private static void addDepot(Depot depot, List<Depot> result, Set<String> urlUniqueSet) {
        if (depot == null || TextUtils.isEmpty(depot.getUrl())) return;
        String url = depot.getUrl().trim();
        if (!urlUniqueSet.contains(url)) {
            urlUniqueSet.add(url);
            result.add(depot);
        }
    }
 
    public static class RepoResult {
        public final MultiRepo repo;
        public final String content;
        public final String etag;
        public final String lastModified;
        public final boolean fromCache;
 
        RepoResult(MultiRepo repo, String content, String etag, String lastModified, boolean fromCache) {
            this.repo = repo;
            this.content = content;
            this.etag = etag;
            this.lastModified = lastModified;
            this.fromCache = fromCache;
        }
    }
 
    public interface RepoCallback {
        void onSuccess(RepoResult result);
        void onError(String msg);
    }
}
