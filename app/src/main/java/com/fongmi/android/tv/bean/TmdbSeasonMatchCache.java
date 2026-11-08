package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Stores a user's explicit source-to-TMDB-season decision separately from media identity. */
public class TmdbSeasonMatchCache {

    private static final int VERSION = 1;
    private static final int MAX_ROUTE_BINDINGS = 512;

    private Map<String, Entry> items;
    private Map<String, RouteBinding> routeBindings;

    public enum Mode {
        MANUAL_SEASON,
        MANUAL_FLAT,
        MANUAL_MULTI_SLICE
    }

    public TmdbSeasonMatchCache() {
        this.items = new HashMap<>();
    }

    public static TmdbSeasonMatchCache objectFrom(String str) {
        try {
            TmdbSeasonMatchCache cache = App.gson().fromJson(str, TmdbSeasonMatchCache.class);
            return cache == null ? new TmdbSeasonMatchCache() : cache;
        } catch (Exception e) {
            return new TmdbSeasonMatchCache();
        }
    }

    public Entry find(String siteKey, String vodId, String sourceTitle, int tmdbId) {
        return find(siteKey, vodId, sourceTitle, "", tmdbId);
    }

    public Entry find(String siteKey, String vodId, String sourceTitle, String flagKey, int tmdbId) {
        return find(siteKey, vodId, sourceTitle, flagKey, tmdbId, false);
    }

    /**
     * Reads a legacy Vod-level binding only when the caller has proved that the
     * detail has a single distinguishable playback line.  A multi-line Vod must
     * not copy one season binding to every Flag.
     */
    public Entry find(String siteKey, String vodId, String sourceTitle, String flagKey, int tmdbId,
                      boolean allowLegacyFallback) {
        if (!hasScope(siteKey, vodId, sourceTitle) || tmdbId <= 0) return null;
        Entry entry = getItems().get(key(siteKey, vodId, sourceTitle, flagKey));
        if (entry == null) entry = getLegacyEntry(siteKey, vodId, sourceTitle, flagKey);
        if (entry == null && allowLegacyFallback && !TextUtils.isEmpty(flagKey)) {
            entry = getLegacyEntry(siteKey, vodId, sourceTitle, "");
        }
        return entry != null && entry.matches(tmdbId) ? entry : null;
    }

    public void put(
            String siteKey,
            String vodId,
            String sourceTitle,
            int tmdbId,
            String mediaType,
            Integer seasonNumber,
            Mode mode,
            String sourceFingerprint,
            int sourceEpisodeCount,
            int tmdbSeasonEpisodeCount) {
        put(siteKey, vodId, sourceTitle, "", tmdbId, mediaType, seasonNumber, mode,
                sourceFingerprint, sourceEpisodeCount, tmdbSeasonEpisodeCount, List.of());
    }

    public void put(
            String siteKey,
            String vodId,
            String sourceTitle,
            String flagKey,
            int tmdbId,
            String mediaType,
            Integer seasonNumber,
            Mode mode,
            String sourceFingerprint,
            int sourceEpisodeCount,
            int tmdbSeasonEpisodeCount) {
        put(siteKey, vodId, sourceTitle, flagKey, tmdbId, mediaType, seasonNumber, mode,
                sourceFingerprint, sourceEpisodeCount, tmdbSeasonEpisodeCount, List.of());
    }

    public void put(
            String siteKey,
            String vodId,
            String sourceTitle,
            String flagKey,
            int tmdbId,
            String mediaType,
            Integer seasonNumber,
            Mode mode,
            String sourceFingerprint,
            int sourceEpisodeCount,
            int tmdbSeasonEpisodeCount,
            List<TmdbSeasonSegment> segments) {
        if (!hasScope(siteKey, vodId, sourceTitle) || tmdbId <= 0 || !"tv".equalsIgnoreCase(mediaType)) return;
        if (mode == Mode.MANUAL_SEASON && (seasonNumber == null || seasonNumber < 0)) return;
        if ((mode == Mode.MANUAL_FLAT || mode == Mode.MANUAL_MULTI_SLICE) && seasonNumber != null) return;
        if (mode == null) return;
        Entry entry = Entry.create(tmdbId, mediaType, seasonNumber, mode, sourceFingerprint, sourceEpisodeCount,
                tmdbSeasonEpisodeCount, segments);
        getItems().put(key(siteKey, vodId, sourceTitle, flagKey), entry);
    }

    public void remove(String siteKey, String vodId, String sourceTitle) {
        remove(siteKey, vodId, sourceTitle, "");
    }

    public void remove(String siteKey, String vodId, String sourceTitle, String flagKey) {
        if (!hasScope(siteKey, vodId, sourceTitle)) return;
        getItems().remove(key(siteKey, vodId, sourceTitle, flagKey));
    }

    public boolean removeIfMediaChanged(String siteKey, String vodId, String sourceTitle, int tmdbId, String mediaType) {
        return removeIfMediaChanged(siteKey, vodId, sourceTitle, "", tmdbId, mediaType);
    }

    public boolean removeIfMediaChanged(String siteKey, String vodId, String sourceTitle, String flagKey, int tmdbId, String mediaType) {
        return removeIfMediaChanged(siteKey, vodId, sourceTitle, flagKey, tmdbId, mediaType, false);
    }

    public boolean removeIfMediaChanged(String siteKey, String vodId, String sourceTitle, String flagKey,
                                        int tmdbId, String mediaType, boolean allowLegacyFallback) {
        if (!hasScope(siteKey, vodId, sourceTitle) || tmdbId <= 0) return false;
        String key = key(siteKey, vodId, sourceTitle, flagKey);
        Entry entry = getItems().get(key);
        if (entry == null) {
            String legacy = legacyKeyMatch(siteKey, vodId, sourceTitle, flagKey);
            if (legacy != null) {
                key = legacy;
                entry = getItems().get(key);
            }
        }
        if (entry == null && allowLegacyFallback && !TextUtils.isEmpty(flagKey)) {
            String legacy = legacyKeyMatch(siteKey, vodId, sourceTitle, "");
            if (legacy != null) {
                key = legacy;
                entry = getItems().get(key);
            }
        }
        if (entry == null || entry.matches(tmdbId) && "tv".equalsIgnoreCase(mediaType)) return false;
        getItems().remove(key);
        return true;
    }

    public Map<String, Entry> getItems() {
        if (items == null) items = new HashMap<>();
        return items;
    }

    public boolean recordRouteBinding(String siteKey, String vodId, String flagKey, String sourceFlag,
                                      int tmdbId, String mediaType, TmdbSeasonScope scope) {
        return recordRouteBinding(siteKey, vodId, flagKey, sourceFlag, "", tmdbId, mediaType, scope);
    }

    public boolean recordRouteBinding(String siteKey, String vodId, String flagKey, String sourceFlag,
                                      String sourceFingerprint, int tmdbId, String mediaType,
                                      TmdbSeasonScope scope) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || TextUtils.isEmpty(flagKey)) return false;
        String key = routeKey(siteKey, vodId, flagKey);
        if (scope == null || !scope.isKnown() || tmdbId <= 0 || !"tv".equalsIgnoreCase(mediaType)) {
            return getRouteBindings().remove(key) != null;
        }
        RouteBinding previous = getRouteBindings().get(key);
        RouteBinding next = RouteBinding.create(
                siteKey, vodId, flagKey, sourceFlag, sourceFingerprint, tmdbId, mediaType, scope);
        if (next.sameAs(previous)) return false;
        getRouteBindings().put(key, next);
        trimRouteBindings();
        return true;
    }

    public boolean pruneRouteBindings(String siteKey, String vodId, Map<String, String> currentFingerprints) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return false;
        boolean changed = false;
        java.util.Iterator<Map.Entry<String, RouteBinding>> iterator = getRouteBindings().entrySet().iterator();
        while (iterator.hasNext()) {
            RouteBinding binding = iterator.next().getValue();
            if (binding == null || !TextUtils.equals(siteKey, binding.siteKey)
                    || !TextUtils.equals(vodId, binding.vodId)) continue;
            String current = currentFingerprints == null ? null : currentFingerprints.get(binding.getFlagKey());
            if (current == null || !TextUtils.equals(current, binding.getSourceFingerprint())) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    public List<RouteBinding> findRouteBindings(String siteKey, String vodId, int tmdbId,
                                                String mediaType, int seasonNumber) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || tmdbId <= 0 || seasonNumber < 0) {
            return List.of();
        }
        return indexRouteBindings(tmdbId, mediaType, seasonNumber)
                .getOrDefault(routeIdentity(siteKey, vodId), List.of());
    }

    public Map<String, List<RouteBinding>> indexRouteBindings(
            int tmdbId, String mediaType, int seasonNumber) {
        Map<String, List<RouteBinding>> result = new HashMap<>();
        if (tmdbId <= 0 || seasonNumber < 0) return result;
        for (RouteBinding binding : getRouteBindings().values()) {
            if (binding == null || !binding.matches(tmdbId, mediaType, seasonNumber)) continue;
            result.computeIfAbsent(routeIdentity(binding.getSiteKey(), binding.getVodId()), ignored -> new ArrayList<>())
                    .add(binding);
        }
        for (List<RouteBinding> bindings : result.values()) {
            bindings.sort(Comparator.comparingLong(RouteBinding::getUpdatedAt).reversed());
        }
        return result;
    }

    public static String routeIdentity(String siteKey, String vodId) {
        return (siteKey == null ? "" : siteKey) + AppDatabase.SYMBOL + (vodId == null ? "" : vodId);
    }

    private void trimRouteBindings() {
        while (getRouteBindings().size() > MAX_ROUTE_BINDINGS) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, RouteBinding> entry : getRouteBindings().entrySet()) {
                long updatedAt = entry.getValue() == null ? Long.MIN_VALUE : entry.getValue().getUpdatedAt();
                if (oldestKey == null || updatedAt < oldestTime) {
                    oldestKey = entry.getKey();
                    oldestTime = updatedAt;
                }
            }
            if (oldestKey == null) return;
            getRouteBindings().remove(oldestKey);
        }
    }

    private Map<String, RouteBinding> getRouteBindings() {
        if (routeBindings == null) routeBindings = new HashMap<>();
        return routeBindings;
    }

    private boolean hasScope(String siteKey, String vodId, String sourceTitle) {
        return !TextUtils.isEmpty(siteKey) && !TextUtils.isEmpty(vodId) && !TextUtils.isEmpty(sourceTitle);
    }

    private String key(String siteKey, String vodId, String sourceTitle, String flagKey) {
        String key = siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL + sourceKey(sourceTitle);
        return TextUtils.isEmpty(flagKey) ? key : key + AppDatabase.SYMBOL + sourceKey(flagKey);
    }

    private String routeKey(String siteKey, String vodId, String flagKey) {
        return siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL + sourceKey(flagKey);
    }

    private String sourceKey(String sourceTitle) {
        return normalize(sourceTitle).replace(AppDatabase.SYMBOL, " ");
    }

    /**
     * 按「内容等价」而不是「键字面相等」找回旧条目。
     *
     * <p>旧键用的是写入当时的字形，无法从当前字形反推：{@code T2S} 方向在 10 组
     * 「多简对一繁」的字上是多对一坍缩（{@code 餘 <= 余,馀}），拿 {@code t2s} 去反推会得到
     * {@code 庆馀年} 这类不存在的键。所以这里改为遍历缓存，把两侧都做同一次单向
     * {@code s2t} 归一（{@code s2t} 无坍缩）后按内容比较。分季缓存条目数是几十条量级，
     * 遍历成本可忽略；命中仍需通过 tmdbId/mediaType 校验。
     */
    private String legacyKeyMatch(String siteKey, String vodId, String sourceTitle, String flagKey) {
        String prefix = siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL;
        String title = segmentKey(sourceTitle);
        String flag = TextUtils.isEmpty(flagKey) ? "" : segmentKey(flagKey);
        for (String candidate : getItems().keySet()) {
            if (candidate == null || !candidate.startsWith(prefix)) continue;
            String[] parts = candidate.substring(prefix.length()).split(AppDatabase.SYMBOL, -1);
            boolean matched = parts.length == (TextUtils.isEmpty(flag) ? 1 : 2)
                    && segmentKey(parts[0]).equals(title)
                    && (TextUtils.isEmpty(flag) || segmentKey(parts[1]).equals(flag));
            if (matched) return candidate;
        }
        return null;
    }

    private Entry getLegacyEntry(String siteKey, String vodId, String sourceTitle, String flagKey) {
        String key = legacyKeyMatch(siteKey, vodId, sourceTitle, flagKey);
        return key == null ? null : getItems().get(key);
    }

    /** 与 {@link #sourceKey} 相同的归一，用于按内容比较键段（含简繁归一）。 */
    private String segmentKey(String text) {
        return normalize(text).replace(AppDatabase.SYMBOL, " ");
    }

    /** 旧版本的键归一（不做简繁归一），保留给需要「原始字形」的调用点使用。 */
    private String legacyNormalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 分季缓存键必须与界面语言无关：站源标题在繁体界面会被 {@code Vod.trans()} 转成繁体，
     * 键若不归一，简体界面保存的「分季结论」切到繁体界面就读不回来。
     *
     * <p>归一方向只用单向 {@code Trans.s2t}（简→繁）：反向 {@code t2s} 在「多简对一繁」的
     * 10 组字上会坍缩，把「余罪/强势/线上」这类常用简体词改写成异体字。必须显式传
     * {@code pass = false}，否则 {@code Trans.s2t(String)} 在简体态会短路返回原文而假绿。
     */
    private String normalize(String text) {
        return legacyNormalize(Trans.s2t(false, Objects.toString(text, "")));
    }

    public static class Entry {

        private int version;
        private int tmdbId;
        private String mediaType;
        private Integer seasonNumber;
        private Mode mode;
        private String sourceFingerprint;
        private int sourceEpisodeCount;
        private int tmdbSeasonEpisodeCount;
        private List<TmdbSeasonSegment> segments;
        private long updatedAt;

        public static Entry create(
                int tmdbId,
                String mediaType,
                Integer seasonNumber,
                Mode mode,
                String sourceFingerprint,
                int sourceEpisodeCount,
                int tmdbSeasonEpisodeCount) {
            return create(tmdbId, mediaType, seasonNumber, mode, sourceFingerprint, sourceEpisodeCount,
                    tmdbSeasonEpisodeCount, List.of());
        }

        public static Entry create(
                int tmdbId,
                String mediaType,
                Integer seasonNumber,
                Mode mode,
                String sourceFingerprint,
                int sourceEpisodeCount,
                int tmdbSeasonEpisodeCount,
                List<TmdbSeasonSegment> segments) {
            Entry entry = new Entry();
            entry.version = VERSION;
            entry.tmdbId = tmdbId;
            entry.mediaType = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
            entry.seasonNumber = seasonNumber;
            entry.mode = mode;
            entry.sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
            entry.sourceEpisodeCount = Math.max(0, sourceEpisodeCount);
            entry.tmdbSeasonEpisodeCount = Math.max(0, tmdbSeasonEpisodeCount);
            entry.segments = segments == null ? List.of() : List.copyOf(segments);
            entry.updatedAt = System.currentTimeMillis();
            return entry;
        }

        private boolean matches(int expectedTmdbId) {
            return version == VERSION
                    && tmdbId == expectedTmdbId
                    && "tv".equalsIgnoreCase(mediaType)
                    && mode != null
                    && (mode != Mode.MANUAL_SEASON || seasonNumber != null && seasonNumber >= 0)
                    && ((mode != Mode.MANUAL_FLAT && mode != Mode.MANUAL_MULTI_SLICE) || seasonNumber == null);
        }

        public int getVersion() {
            return version;
        }

        public int getTmdbId() {
            return tmdbId;
        }

        public String getMediaType() {
            return mediaType == null ? "" : mediaType;
        }

        public Integer getSeasonNumber() {
            return seasonNumber;
        }

        public Mode getMode() {
            return mode;
        }

        public String getSourceFingerprint() {
            return sourceFingerprint == null ? "" : sourceFingerprint;
        }

        public int getSourceEpisodeCount() {
            return sourceEpisodeCount;
        }

        public int getTmdbSeasonEpisodeCount() {
            return tmdbSeasonEpisodeCount;
        }

        public List<TmdbSeasonSegment> getSegments() {
            return segments == null ? List.of() : List.copyOf(segments);
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public boolean isFresh(String fingerprint, int currentSourceEpisodeCount,
                               int currentTmdbSeasonEpisodeCount) {
            return TextUtils.equals(getSourceFingerprint(), fingerprint)
                    && sourceEpisodeCount == Math.max(0, currentSourceEpisodeCount)
                    && tmdbSeasonEpisodeCount == Math.max(0, currentTmdbSeasonEpisodeCount);
        }
    }

    public static class RouteBinding {

        private int version;
        private String siteKey;
        private String vodId;
        private String flagKey;
        private String sourceFlag;
        private String sourceFingerprint;
        private int tmdbId;
        private String mediaType;
        private List<Integer> seasons;
        private List<TmdbSeasonSegment> segments;
        private long updatedAt;

        static RouteBinding create(String siteKey, String vodId, String flagKey, String sourceFlag,
                                   String sourceFingerprint, int tmdbId, String mediaType,
                                   TmdbSeasonScope scope) {
            RouteBinding binding = new RouteBinding();
            binding.version = VERSION;
            binding.siteKey = siteKey;
            binding.vodId = vodId;
            binding.flagKey = flagKey;
            binding.sourceFlag = sourceFlag == null ? "" : sourceFlag;
            binding.sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
            binding.tmdbId = tmdbId;
            binding.mediaType = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
            binding.seasons = List.copyOf(scope.getSeasons());
            binding.segments = List.copyOf(scope.getSegments());
            binding.updatedAt = System.currentTimeMillis();
            return binding;
        }

        boolean sameAs(RouteBinding other) {
            return other != null && other.version == VERSION && tmdbId == other.tmdbId
                    && TextUtils.equals(siteKey, other.siteKey) && TextUtils.equals(vodId, other.vodId)
                    && TextUtils.equals(flagKey, other.flagKey) && TextUtils.equals(sourceFlag, other.sourceFlag)
                    && TextUtils.equals(sourceFingerprint, other.sourceFingerprint)
                    && TextUtils.equals(mediaType, other.mediaType)
                    && getSeasons().equals(other.getSeasons()) && getSegments().equals(other.getSegments());
        }

        boolean matches(String expectedSiteKey, String expectedVodId, int expectedTmdbId,
                        String expectedMediaType, int seasonNumber) {
            return matches(expectedTmdbId, expectedMediaType, seasonNumber)
                    && TextUtils.equals(siteKey, expectedSiteKey)
                    && TextUtils.equals(vodId, expectedVodId);
        }

        boolean matches(int expectedTmdbId, String expectedMediaType, int seasonNumber) {
            return version == VERSION && tmdbId == expectedTmdbId
                    && TextUtils.equals(mediaType, expectedMediaType == null ? "" : expectedMediaType.toLowerCase(Locale.ROOT))
                    && getSeasons().contains(seasonNumber);
        }

        public String getSiteKey() {
            return siteKey == null ? "" : siteKey;
        }

        public String getVodId() {
            return vodId == null ? "" : vodId;
        }

        public String getFlagKey() {
            return flagKey == null ? "" : flagKey;
        }

        public String getSourceFlag() {
            return sourceFlag == null ? "" : sourceFlag;
        }

        public String getSourceFingerprint() {
            return sourceFingerprint == null ? "" : sourceFingerprint;
        }

        public List<Integer> getSeasons() {
            return seasons == null ? List.of() : List.copyOf(seasons);
        }

        public List<TmdbSeasonSegment> getSegments() {
            return segments == null ? List.of() : List.copyOf(segments);
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }
}
