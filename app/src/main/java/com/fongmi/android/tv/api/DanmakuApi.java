package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.danmaku.DanmakuUrlPolicy;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import java.util.List;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && DanmakuSetting.hasValidApiUrl();
    }

    public static boolean canAutoSearch(List<Danmaku> siteDanmakus) {
        return canSearch() && (!DanmakuSetting.isSpiderFirst() || siteDanmakus == null || siteDanmakus.isEmpty());
    }

    public static Call newCall(String name, String episode) {
        String url = DanmakuSetting.getValidApiUrl();
        if (TextUtils.isEmpty(url)) return null;
        OkHttp.cancel(TAG);
        return newCall(name, episode, url, TAG);
    }

    private static Call newCall(String name, String episode, String url, String tag) {
        name = Trans.t2s(false, name);
        episode = Trans.t2s(false, episode);
        try {
            if (url.contains("{name}") || url.contains("{episode}")) {
                return OkHttp.newCall(url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), tag);
            } else {
                url = getSearchUrl(url);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search name=%s episode=%s %s", name, episode, DanmakuUrlPolicy.logSummary(url));
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("name", name);
                params.put("episode", episode);
                return OkHttp.newCall(url, OkHttp.toBody(params), tag);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getSearchUrl(String url) {
        Uri uri = Uri.parse(url);
        List<String> segments = uri.getPathSegments();
        if (!segments.isEmpty() && "danmaku".equalsIgnoreCase(segments.get(segments.size() - 1))) return url;
        if (segments.size() > 1) return url;
        return uri.buildUpon().appendPath("danmaku").build().toString();
    }

    public static List<Danmaku> arrayFrom(String body) {
        return normalize(Danmaku.arrayFrom(body));
    }

    private static List<Danmaku> normalize(List<Danmaku> items) {
        if (items.isEmpty()) return items;
        String api = getSearchUrl(DanmakuSetting.getValidApiUrl());
        for (Danmaku item : items) item.setUrl(normalizeUrl(api, item.getUrl()));
        return items;
    }

    private static String normalizeUrl(String api, String url) {
        String normalized = DanmakuUrlPolicy.normalize(api, url);
        if (SpiderDebug.isEnabled() && !TextUtils.equals(url, normalized)) SpiderDebug.log("danmaku", "normalize result %s", DanmakuUrlPolicy.logSummary(normalized));
        return normalized;
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        Call call = newCall(name, episode);
        enqueue(call, found);
    }

    public static Call searchOwned(String name, String episode, String ownerTag, Consumer<Danmaku> found) {
        String url = DanmakuSetting.getValidApiUrl();
        Call call = TextUtils.isEmpty(url) ? null : newCall(name, episode, url, ownerTag);
        enqueue(call, found);
        return call;
    }

    private static void enqueue(Call call, Consumer<Danmaku> found) {
        if (call == null) return;
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    arrayFrom(response.body().string()).stream().findFirst().ifPresent(item -> App.post(() -> found.accept(item)));
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
    }
}
