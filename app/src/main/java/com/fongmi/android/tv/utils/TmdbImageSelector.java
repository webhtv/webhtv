package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TmdbImageSelector {

    private enum Orientation {
        LANDSCAPE,
        PORTRAIT,
        UNKNOWN
    }

    public static List<String> backgrounds(JsonObject detail, String imageBase, String backdropBase, boolean preferLandscape, int limit) {
        List<String> preferred = preferLandscape
                ? backdrops(detail, backdropBase, limit)
                : posters(detail, imageBase, limit);
        if (!preferred.isEmpty()) return preferred;
        return preferLandscape
                ? posters(detail, imageBase, limit)
                : backdrops(detail, backdropBase, limit);
    }


    public static List<String> backdrops(JsonObject detail, String backdropBase, int limit) {
        List<Candidate> candidates = new ArrayList<>();
        addImages(candidates, detail, "backdrops", backdropBase, Orientation.LANDSCAPE);
        addRootImage(candidates, detail, "backdrop_path", backdropBase, Orientation.LANDSCAPE);
        return urls(candidates, limit);
    }

    public static List<String> posters(JsonObject detail, String imageBase, int limit) {
        List<Candidate> candidates = new ArrayList<>();
        addImages(candidates, detail, "posters", imageBase, Orientation.PORTRAIT);
        addRootImage(candidates, detail, "poster_path", imageBase, Orientation.PORTRAIT);
        return urls(candidates, limit);
    }

    private static List<String> urls(List<Candidate> candidates, int limit) {
        candidates.sort(Comparator
                .comparingInt((Candidate item) -> item.sourceRank)
                .thenComparing(Comparator.comparingLong((Candidate item) -> item.pixels).reversed())
                .thenComparing(Comparator.comparingDouble((Candidate item) -> item.voteAverage).reversed())
                .thenComparing(Comparator.comparingInt((Candidate item) -> item.voteCount).reversed()));
        List<String> urls = new ArrayList<>();
        int max = limit <= 0 ? Integer.MAX_VALUE : limit;
        for (Candidate candidate : candidates) {
            if (urls.contains(candidate.url)) continue;
            urls.add(candidate.url);
            if (urls.size() >= max) break;
        }
        return urls;
    }


    public static String poster(JsonObject detail, String imageBase, String fallback) {
        List<Candidate> candidates = new ArrayList<>();
        addImages(candidates, detail, "posters", imageBase, Orientation.PORTRAIT);
        addRootImage(candidates, detail, "poster_path", imageBase, Orientation.PORTRAIT);
        candidates.sort(Comparator
                .comparingInt((Candidate item) -> item.sourceRank)
                .thenComparing(Comparator.comparingLong((Candidate item) -> item.pixels).reversed())
                .thenComparing(Comparator.comparingDouble((Candidate item) -> item.voteAverage).reversed())
                .thenComparing(Comparator.comparingInt((Candidate item) -> item.voteCount).reversed()));
        for (Candidate candidate : candidates) {
            if (!isEmpty(candidate.url)) return candidate.url;
        }
        return originalUrl(fallback);
    }

    public static String cardImage(TmdbItem item, boolean landscape) {
        if (item == null) return "";
        return landscape ? item.getBackdropUrl() : item.getPosterUrl();
    }

    public static String originalUrl(String url) {
        if (isEmpty(url)) return "";
        int marker = url.indexOf("/t/p/");
        if (marker >= 0) {
            int sizeStart = marker + "/t/p/".length();
            int sizeEnd = url.indexOf('/', sizeStart);
            if (sizeEnd > sizeStart) return url.substring(0, sizeStart) + "original" + url.substring(sizeEnd);
        }
        return url.replaceFirst("/(w\\d+|h\\d+|original)/", "/original/");
    }

    /**
     * 把 TMDB 图片地址换成官方档位（如 {@code w500}）；非 TMDB {@code /t/p/} 路径原样返回。
     * <p>
     * 只使用官方 OAS 声明过的档位：poster {@code w92/w154/w185/w342/w500/w780/original}、
     * backdrop {@code w300/w780/w1280/original}、still {@code w92/w185/w300/original}。
     * 代理包装线路（wsrv/itv666）把原始地址放在 query 里，包含 {@code /t/p/} 时同样可替换；
     * 编码过或无法识别时返回原地址，宁可不优化也不破图。
     */
    public static String tierUrl(String url, String size) {
        if (isEmpty(url) || isEmpty(size)) return isEmpty(url) ? "" : url;
        int marker = url.indexOf("/t/p/");
        if (marker < 0) return url;
        int sizeStart = marker + "/t/p/".length();
        int sizeEnd = url.indexOf('/', sizeStart);
        if (sizeEnd <= sizeStart) return url;
        return url.substring(0, sizeStart) + size.trim() + url.substring(sizeEnd);
    }

    /** 详情页 rail 的海报缩略图档位（实测原图 1,187,694B → w500 85,680B）。 */
    public static String posterTier(String url) {
        return tierUrl(url, "w500");
    }

    /** 详情页 rail 的图集/剧照缩略图档位（backdrop 官方档位 w780）。 */
    public static String photoTier(String url) {
        return tierUrl(url, "w780");
    }

    /**
     * 详情页 hero 海报档位（poster 官方档位 {@code w780}）。
     * <p>
     * hero 海报显示尺寸远小于原图，实测 original 1,187,694B / w780 201,063B（−83%），
     * 解码后按显示尺寸缩放，画质无可见差异。
     */
    public static String heroPosterTier(String url) {
        return tierUrl(url, "w780");
    }

    private static void addImages(List<Candidate> candidates, JsonObject detail, String key, String base, Orientation fallback) {
        JsonArray images = array(detail, "images", key);
        for (JsonElement element : images) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String path = string(object, "file_path");
            String url = image(base, path);
            if (isEmpty(url)) continue;
            int width = integer(object, "width");
            int height = integer(object, "height");
            candidates.add(new Candidate(url, orientation(width, height, fallback), pixels(width, height), number(object, "vote_average"), integer(object, "vote_count"), 0));
        }
    }

    private static void addRootImage(List<Candidate> candidates, JsonObject detail, String key, String base, Orientation orientation) {
        String url = image(base, string(detail, key));
        if (!isEmpty(url)) candidates.add(new Candidate(url, orientation, 0, 0, 0, 1));
    }

    private static List<Candidate> filter(List<Candidate> candidates, Orientation orientation) {
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) if (candidate.orientation == orientation) result.add(candidate);
        return result;
    }

    private static String image(String base, String path) {
        if (isEmpty(path)) return "";
        String normalizedBase = originalBase(base);
        return TmdbProxy.imageUrl(normalizedBase, path);
    }

    private static String originalBase(String base) {
        if (isEmpty(base)) return "";
        String value = base.trim();
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        int marker = value.indexOf("/t/p/");
        if (marker >= 0) return value.substring(0, marker + "/t/p/".length()) + "original";
        return value;
    }

    private static Orientation orientation(int width, int height, Orientation fallback) {
        if (width > 0 && height > 0) return width >= height ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
        return fallback == null ? Orientation.UNKNOWN : fallback;
    }

    private static long pixels(int width, int height) {
        return Math.max(0, width) * (long) Math.max(0, height);
    }

    private static JsonArray array(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return new JsonArray();
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
            current = currentObject.get(key);
        }
        return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
            return object.get(key).getAsString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static int integer(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
            return object.get(key).getAsInt();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static double number(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
            return object.get(key).getAsDouble();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record Candidate(String url, Orientation orientation, long pixels, double voteAverage, int voteCount, int sourceRank) {
    }
}
