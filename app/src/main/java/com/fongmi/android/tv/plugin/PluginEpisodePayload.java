package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;

/**
 * 把宿主当前的剧集信息压成插件可读的只读字段。
 *
 * <p><b>只挑选插件真的用得上的字段。</b>豆瓣评论插件要的是"这是哪部剧的第几集"，
 * 所以这里是标题、年份、类型、TMDB/豆瓣评分、语言、国家、海报与集名集号；
 * 演职员与简介属于另一个粒度的数据（要额外请求），不放进每次点击都要构造的载荷里。
 *
 * <p><b>零值一律省略而不是填默认值。</b>插件无法区分"评分 0"和"没有评分"，
 * 如果缺字段也写 0，插件就会渲染出"0 分"这种明显错误的信息。省略字段让插件
 * 用"键是否存在"判断，语义唯一。
 *
 * <p><b>不修改任何 bean。</b>这里只读取 getter；插件拿到的是副本，改坏了也不影响宿主。
 */
public final class PluginEpisodePayload {

    /** 载荷字节上限：超过就整份丢弃，避免把畸形数据当"成功但很大"地传下去。 */
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024;

    private PluginEpisodePayload() {
    }

    /**
     * 组装载荷。
     *
     * @return 三个入参全空（或所有字段都为空）时返回 {@code null}；超过 {@link #MAX_PAYLOAD_BYTES} 也返回 {@code null}
     */
    @Nullable
    public static JsonObject of(@Nullable TmdbItem item, @Nullable Vod vod, @Nullable Episode episode) {
        JsonObject payload = new JsonObject();
        putText(payload, "title", firstNonEmpty(item == null ? null : item.getTitle(), vod == null ? null : vod.getName()));
        putText(payload, "subtitle", item == null ? null : item.getSubtitle());
        putText(payload, "mediaType", mediaType(item));
        putText(payload, "year", vod == null ? null : vod.getYear());
        if (item != null && item.getTmdbId() > 0) payload.addProperty("tmdbId", item.getTmdbId());
        if (item != null && item.getTmdbRating() > 0) payload.addProperty("tmdbRating", item.getTmdbRating());
        if (item != null && item.getDoubanRating() > 0) payload.addProperty("doubanRating", item.getDoubanRating());
        putText(payload, "originalLanguage", item == null ? null : item.getOriginalLanguage());
        putText(payload, "originCountry", item == null ? null : item.getOriginCountry());
        putText(payload, "posterUrl", firstNonEmpty(item == null ? null : item.getPosterUrl(), vod == null ? null : vod.getPic()));
        putText(payload, "episodeName", episode == null ? null : episode.getName());
        if (episode != null && episode.getNumber() > 0) payload.addProperty("episodeNumber", episode.getNumber());
        if (payload.size() == 0) return null;
        if (payload.toString().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) return null;
        return payload;
    }

    /**
     * 媒体类型：优先用 TMDB 的判定。
     *
     * <p>兜底只可能是 {@code movie}：{@code TmdbItem.isTv()} 的定义就是
     * {@code "tv".equals(getMediaType())}，而 "tv" 在上一行已经按"已声明"返回了，
     * 所以这里再问一次 {@code isTv()} 永远为假。写死单值比留一个不可达分支诚实。
     */
    @Nullable
    private static String mediaType(@Nullable TmdbItem item) {
        if (item == null) return null;
        String declared = item.getMediaType();
        if (declared != null && !declared.isEmpty()) return declared;
        return "movie";
    }

    private static void putText(JsonObject payload, String key, @Nullable String value) {
        if (value == null || value.isEmpty()) return;
        payload.addProperty(key, value);
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isEmpty()) return first;
        return second == null || second.isEmpty() ? null : second;
    }
}
