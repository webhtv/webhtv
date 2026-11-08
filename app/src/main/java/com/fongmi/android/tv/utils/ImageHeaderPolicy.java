package com.fongmi.android.tv.utils;

import com.google.common.net.HttpHeaders;

import java.util.Locale;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class ImageHeaderPolicy {

    static final String DOUBAN_IMAGE_REFERER = "https://movie.douban.com";

    // 预编译：String.matches() 每调用一次都会重新编译同一个正则。详情页/播放页的图片列表
    // 会成百次地走 doubanImageReferer()，编译开销全落在主线程上；Pattern 是线程安全的，
    // 复用一个实例即可，语义与旧写法 String.matches(...) 完全相同。
    private static final Pattern DOUBAN_IMAGE_HOST = Pattern.compile("img\\d+\\.doubanio\\.com");

    private ImageHeaderPolicy() {
    }

    public static String doubanImageReferer(String url, boolean hasReferer) {
        if (hasReferer) return "";
        return isDoubanImageHost(host(url)) ? DOUBAN_IMAGE_REFERER : "";
    }

    public static boolean isReferer(String name) {
        return name != null && HttpHeaders.REFERER.equalsIgnoreCase(name.trim());
    }

    private static boolean isDoubanImageHost(String host) {
        return host != null && DOUBAN_IMAGE_HOST.matcher(host).matches();
    }

    private static String host(String url) {
        String value = stripDirectives(url);
        if (value.isEmpty()) return "";
        HttpUrl parsed = HttpUrl.parse(value);
        if (parsed == null && value.startsWith("//")) parsed = HttpUrl.parse("https:" + value);
        return parsed == null ? "" : parsed.host().toLowerCase(Locale.ROOT);
    }

    private static String stripDirectives(String url) {
        if (url == null) return "";
        String value = url.trim();
        int marker = firstDirective(value);
        return marker < 0 ? value : value.substring(0, marker);
    }

    private static int firstDirective(String value) {
        int marker = -1;
        marker = min(marker, value.indexOf("@Headers="));
        marker = min(marker, value.indexOf("@Cookie="));
        marker = min(marker, value.indexOf("@Referer="));
        marker = min(marker, value.indexOf("@User-Agent="));
        return marker;
    }

    private static int min(int current, int candidate) {
        if (candidate < 0) return current;
        return current < 0 ? candidate : Math.min(current, candidate);
    }
}
