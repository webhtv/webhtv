package com.fongmi.android.tv.ui.dialog;
 
import android.text.TextUtils;
 
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
 
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
 
import java.util.ArrayList;
import java.util.List;
 
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
 
/**
 * 异步加载接口（Depot.url）对应的 config.json 内容并解析出 sites。
 */
final class RepoApiLoader {
 
    private static final String TAG = RepoApiLoader.class.getSimpleName();

    private RepoApiLoader() {
    }
 
    static void loadSites(@NonNull Depot depot, @NonNull Callback callback) {
        Task.submit(() -> {
            String url = depot.getUrl();
            if (TextUtils.isEmpty(url)) {
                App.post(() -> callback.onError("接口链接为空"));
                return;
            }
            try {
                String json = Decoder.getJson(url, TAG);
                if (json != null && json.length()>0) {
                    if (!json.startsWith("{") && !json.startsWith("[") && !json.startsWith("/")) {
                        SpiderDebug.log("多仓", "接口加载失败：\n"+json);
                        App.post(() -> callback.onError("加载失败："+json));
                        return;
                    }
                    com.google.gson.JsonObject object = Json.parse(json).getAsJsonObject();
                    List<Site> sites = new ArrayList<>();
                    // 先 spider
                    String spider = Json.safeString(object, "spider");
                    sites.addAll(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).toList());
                    if (sites.isEmpty()) {
                        App.post(() -> callback.onError("接口站点列表为空"));
                        return;
                    }
                    App.post(() -> callback.onSuccess(sites));
                }
            } catch (Throwable e) {
                SpiderDebug.log("多仓", "接口加载异常：\n"+e.getMessage());
                App.post(() -> callback.onError("加载异常: " + e.getMessage()));
            }
        });
    }
 
    public interface Callback {
        void onSuccess(@NonNull List<Site> sites);
        void onError(@NonNull String msg);
    }
}
