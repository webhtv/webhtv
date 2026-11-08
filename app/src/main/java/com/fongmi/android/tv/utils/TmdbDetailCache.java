package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TmdbDetailCache {

    public static final String EXTRA_KEY = "tmdb_detail_cache_key";

    private static final int MAX_PENDING = 8;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();

    public static String put(TmdbItem item, JsonObject detail, List<TmdbPerson> cast, String language) {
        return put(item, detail, cast, language, null);
    }

    public static String put(TmdbItem item, JsonObject detail, List<TmdbPerson> cast) {
        return put(item, detail, cast, "");
    }

    /**
     * 详情页已经解析好的季集数据随详情快照一起交给播放页。
     *
     * <p>播放页的选集卡片模式由 {@code Episode.tmdbEpisode} 决定，而该字段只有播放页
     * 自己再取一次分季数据才会被写入。详情页在跳转前已经取过同一季（否则它自己也画不出
     * 卡片），让播放页复用这份数据可以消除第二次网络请求，避免请求失败时详情页有卡片、
     * 播放页却退化成纯文本按钮。
     */
    public static String put(TmdbItem item, JsonObject detail, List<TmdbPerson> cast, String language,
                             Map<Integer, List<TmdbEpisode>> seasonEpisodes) {
        if (item == null || item.getTmdbId() <= 0 || detail == null) return "";
        if (CACHE.size() >= MAX_PENDING) CACHE.clear();
        String key = "tmdb_detail_" + NEXT_ID.incrementAndGet();
        CACHE.put(key, new Entry(item, detail, cast, TmdbLanguagePolicy.normalize(language), seasonEpisodes));
        return key;
    }

    public static Entry take(String key, TmdbItem expected) {
        return take(key, expected, "");
    }

    public static Entry take(String key, TmdbItem expected, String expectedLanguage) {
        if (TextUtils.isEmpty(key)) return null;
        Entry entry = CACHE.remove(key);
        boolean rejected = entry != null && !entry.matches(expected, expectedLanguage);
        if (rejected) SpiderDebug.log("tmdb-language-cache", "snapshotLanguage=%s target=%s result=reject", entry.getLanguage(), expectedLanguage);
        if (entry == null || rejected) return null;
        return entry;
    }

    private TmdbDetailCache() {
    }

    public static class Entry {

        private final TmdbItem item;
        private final JsonObject detail;
        private final List<TmdbPerson> cast;
        private final String language;
        private final Map<Integer, List<TmdbEpisode>> seasonEpisodes;

        private Entry(TmdbItem item, JsonObject detail, List<TmdbPerson> cast, String language,
                      Map<Integer, List<TmdbEpisode>> seasonEpisodes) {
            this.item = item;
            this.detail = detail;
            this.cast = cast == null ? new ArrayList<>() : new ArrayList<>(cast);
            this.language = language == null ? "" : language;
            this.seasonEpisodes = copySeasonEpisodes(seasonEpisodes);
        }

        /**
         * 详情页已解析的季集数据（季号 -> 该季分集），没有时为 null。
         * 与 {@link #getCast()} 一致做防御性拷贝：快照一旦交给播放页就不应再受详情页改动影响。
         */
        public Map<Integer, List<TmdbEpisode>> getSeasonEpisodes() {
            return copySeasonEpisodes(seasonEpisodes);
        }

        private static Map<Integer, List<TmdbEpisode>> copySeasonEpisodes(Map<Integer, List<TmdbEpisode>> source) {
            if (source == null || source.isEmpty()) return null;
            Map<Integer, List<TmdbEpisode>> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<TmdbEpisode>> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null || entry.getValue().isEmpty()) continue;
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return copy.isEmpty() ? null : copy;
        }

        public String getLanguage() {
            return language;
        }

        public TmdbItem getItem() {
            return item;
        }

        public JsonObject getDetail() {
            return detail;
        }

        public List<TmdbPerson> getCast() {
            return new ArrayList<>(cast);
        }

        private boolean matches(TmdbItem expected, String expectedLanguage) {
            if (expected == null || item == null) return false;
            if (item.getTmdbId() != expected.getTmdbId() || !normalize(item.getMediaType()).equals(normalize(expected.getMediaType()))) return false;
            return TmdbLanguagePolicy.matchesTarget(expectedLanguage, language);
        }

        private String normalize(String value) {
            return TextUtils.isEmpty(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
