package com.fongmi.android.tv.player.extractor;

import android.util.Base64;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MpdSanitizer {

    private static final String MIME_TYPE = "data:application/dash+xml";
    private static final Pattern FRAME_RATE = Pattern.compile("\\s+frameRate\\s*=\\s*([\\\"'])(.*?)\\1");
    private static final Pattern VALUE = Pattern.compile("([0-9]+)(?:/([0-9]+))?");
    private static final Pattern ADAPTATION_SET_ID = Pattern.compile("(<AdaptationSet\\b[^>]*?)\\s+id\\s*=\\s*([\\\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADAPTATION_SET = Pattern.compile("<AdaptationSet\\b[\\s\\S]*?</AdaptationSet>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_URL = Pattern.compile("<BaseURL\\b[^>]*>[\\s\\S]*?</BaseURL>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCODED_URL = Pattern.compile("[A-Za-z0-9_-]{200,}={0,2}");
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");

    private MpdSanitizer() {
    }

    public static boolean isYoutube(String url) {
        if (url == null || !url.startsWith("data:application/dash+xml;base64,")) return false;
        int comma = url.indexOf(',');
        try {
            String xml = new String(Base64.decode(url.substring(comma + 1), Base64.DEFAULT), StandardCharsets.UTF_8);
            return xml.contains("googlevideo.com") || xml.contains("127.0.0.1%253A10172");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean hasLimitedYoutubeAudio(String url) {
        if (url == null || !url.startsWith("data:application/dash+xml;base64,")) return false;
        try {
            String xml = new String(Base64.decode(url.substring(url.indexOf(',') + 1), Base64.DEFAULT), StandardCharsets.UTF_8);
            Matcher matcher = ADAPTATION_SET.matcher(xml);
            while (matcher.find()) {
                String set = matcher.group();
                if (!set.contains("contentType='audio'") && !set.contains("contentType=\"audio\"")) continue;
                if (containsClient(set, "ANDROID_VR")) return false;
                if (containsClient(set, "ANDROID")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String sanitize(String url) {
        if (!isBase64Mpd(url)) return url;
        int comma = url.indexOf(',');
        try {
            String xml = new String(Base64.decode(url.substring(comma + 1), Base64.DEFAULT), StandardCharsets.UTF_8);
            if (SpiderDebug.isEnabled()) save(xml);
            int[] removed = new int[4];
            xml = removeInvalidFrameRates(xml, removed);
            xml = removeInvalidAdaptationSetIds(xml, removed);
            xml = removeTvHtml5VideoAdaptationSets(xml, removed);
            xml = keepAndroidVrAudioTrack(xml, removed);
            if (removed[0] == 0 && removed[1] == 0 && removed[2] == 0 && removed[3] == 0) return url;
            SpiderDebug.log("mpd-sanitizer", "removed invalid frameRate=%d adaptationSetId=%d tvHtml5VideoSets=%d limitedAudioSets=%d", removed[0], removed[1], removed[2], removed[3]);
            return url.substring(0, comma + 1) + Base64.encodeToString(xml.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        } catch (Exception e) {
            SpiderDebug.log("mpd-sanitizer", "skip malformed MPD data URI: %s", e.getClass().getSimpleName());
            return url;
        }
    }

    private static boolean isBase64Mpd(String url) {
        if (url == null || !url.regionMatches(true, 0, MIME_TYPE, 0, MIME_TYPE.length())) return false;
        int comma = url.indexOf(',');
        if (comma < 0) return false;
        return url.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64");
    }

    private static boolean isValid(String value) {
        Matcher matcher = VALUE.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) return false;
        try {
            if (Long.parseLong(matcher.group(1)) <= 0) return false;
            return matcher.group(2) == null || Long.parseLong(matcher.group(2)) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Matcher#appendReplacement/appendTail 的 StringBuilder 重载需要 API 34；
    // StringBuffer 重载自 API 1 就有，语义一致（仅在单线程局部使用）。
    private static String removeInvalidFrameRates(String xml, int[] removed) {
        Matcher matcher = FRAME_RATE.matcher(xml);
        StringBuffer output = new StringBuffer(xml.length());
        while (matcher.find()) {
            if (isValid(matcher.group(2))) continue;
            matcher.appendReplacement(output, "");
            removed[0]++;
        }
        if (removed[0] == 0) return xml;
        matcher.appendTail(output);
        return output.toString();
    }

    private static String removeInvalidAdaptationSetIds(String xml, int[] removed) {
        Matcher matcher = ADAPTATION_SET_ID.matcher(xml);
        StringBuffer output = new StringBuffer(xml.length());
        while (matcher.find()) {
            if (isValidInteger(matcher.group(3))) continue;
            matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(1)));
            removed[1]++;
        }
        if (removed[1] == 0) return xml;
        matcher.appendTail(output);
        return output.toString();
    }

    private static boolean isValidInteger(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!INTEGER.matcher(trimmed).matches()) return false;
        try {
            Long.parseLong(trimmed);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void save(String xml) {
        try (FileOutputStream output = new FileOutputStream(new File(App.get().getCacheDir(), "youtube-mpd.xml"))) {
            output.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            SpiderDebug.log("mpd-sanitizer", "save failed: %s", e.getClass().getSimpleName());
        }
    }

    private static String removeTvHtml5VideoAdaptationSets(String xml, int[] removed) {
        Matcher matcher = ADAPTATION_SET.matcher(xml);
        StringBuffer output = new StringBuffer(xml.length());
        while (matcher.find()) {
            String set = matcher.group();
            if (!set.contains("contentType='video'") && !set.contains("contentType=\"video\"")) continue;
            if (!containsClient(set, "TVHTML5")) continue;
            matcher.appendReplacement(output, "");
            removed[2]++;
        }
        if (removed[2] == 0) return xml;
        matcher.appendTail(output);
        return output.toString();
    }

    private static boolean containsClient(String text, String client) {
        Matcher matcher = ENCODED_URL.matcher(text);
        while (matcher.find()) {
            try {
                String decoded = new String(Base64.decode(matcher.group(), Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8);
                if (decoded.contains("&c=" + client) || decoded.contains("?c=" + client)) return true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    private static String keepAndroidVrAudioTrack(String xml, int[] removed) {
        Matcher matcher = ADAPTATION_SET.matcher(xml);
        StringBuffer output = new StringBuffer(xml.length());
        while (matcher.find()) {
            String set = matcher.group();
            if (!set.contains("contentType='audio'") && !set.contains("contentType=\"audio\"")) continue;
            if (!containsClient(set, "ANDROID_VR")) continue;
            Matcher urls = BASE_URL.matcher(set);
            StringBuffer filtered = new StringBuffer(set.length());
            int count = 0;
            while (urls.find()) {
                if (containsClient(urls.group(), "ANDROID_VR")) continue;
                urls.appendReplacement(filtered, "");
                count++;
            }
            if (count == 0) continue;
            urls.appendTail(filtered);
            matcher.appendReplacement(output, Matcher.quoteReplacement(filtered.toString()));
            removed[3] += count;
        }
        if (removed[3] == 0) return xml;
        matcher.appendTail(output);
        return output.toString();
    }

}
