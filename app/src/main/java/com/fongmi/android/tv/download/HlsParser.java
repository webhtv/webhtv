package com.fongmi.android.tv.download;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 最小化 m3u8 解析器：提取分片 URL、EXTINF 时长、EXT-X-MAP 初始化分片、EXT-X-KEY(AES-128) 加密信息。
 *  master playlist 自动选最高码率 variant。 */
public class HlsParser {

    /** 解析入口：fetch manifest；若为 master playlist 则选最高码率 variant 后递归解析。 */
    public static Result parse(String manifestUrl, Map<String, String> headers) throws Exception {
        String content = fetchText(manifestUrl, headers);
        if (content == null || !content.contains("#EXTM3U")) {
            throw new IllegalArgumentException("Not a valid m3u8 manifest");
        }
        if (content.contains("#EXT-X-STREAM-INF")) {
            String variant = pickHighestBitrateVariant(content);
            if (variant != null) return parse(toAbsolute(variant, manifestUrl), headers);
        }
        return parseMediaPlaylist(content, manifestUrl);
    }

    private static Result parseMediaPlaylist(String content, String baseUrl) {
        List<String> urls = new ArrayList<>();
        List<Double> durations = new ArrayList<>();
        List<String> keyUrls = new ArrayList<>();
        List<byte[]> keyIvs = new ArrayList<>();
        String initUrl = null;
        String keyUrl = null;
        byte[] keyIv = null;
        long mediaSequence = 0;
        double lastDuration = 0;
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF")) {
                    try {
                        Matcher m = Pattern.compile("EXTINF:([\\d.]+)").matcher(line);
                        if (m.find()) lastDuration = Double.parseDouble(m.group(1));
                    } catch (Exception ignored) {}
                } else if (line.startsWith("#EXT-X-MAP")) {
                    String uri = attr(line, "URI");
                    if (uri != null) initUrl = toAbsolute(uri, baseUrl);
                } else if (line.startsWith("#EXT-X-KEY")) {
                    String method = attr(line, "METHOD");
                    if ("AES-128".equalsIgnoreCase(method)) {
                        String uri = attr(line, "URI");
                        keyUrl = uri != null ? toAbsolute(uri, baseUrl) : null;
                        keyIv = parseIv(attr(line, "IV"));
                    } else {
                        keyUrl = null;
                        keyIv = null;
                    }
                } else if (line.startsWith("#EXT-X-MEDIA-SEQUENCE")) {
                    String v = line.substring(line.indexOf(':') + 1).split(",")[0].trim();
                    try { mediaSequence = Long.parseLong(v); } catch (Exception ignored) {}
                }
                continue;
            }
            // 这一行就是分片 URL
            urls.add(toAbsolute(line, baseUrl));
            durations.add(lastDuration);
            keyUrls.add(keyUrl);
            keyIvs.add(keyIv);
        }
        if (urls.isEmpty()) throw new IllegalStateException("No segments found in m3u8");
        return new Result(urls, durations, initUrl, keyUrls, keyIvs, mediaSequence);
    }

    /** 下载原始字节（用于取 AES-128 密钥，通常 16 字节）。 */
    public static byte[] fetchBytes(String url, Map<String, String> headers) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) conn.setRequestProperty(e.getKey(), e.getValue());
        }
        try (InputStream is = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) out.write(buf, 0, r);
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    /** 取标签属性值，支持 URI="..." 与 METHOD=AES-128 两种写法。 */
    private static String attr(String line, String name) {
        Matcher m = Pattern.compile(name + "=(\"([^\"]*)\"|[^,]*)").matcher(line);
        if (!m.find()) return null;
        String v = m.group(2) != null ? m.group(2) : m.group(1);
        return v != null ? v.trim() : null;
    }

    private static byte[] parseIv(String hex) {
        if (hex == null) return null;
        String s = (hex.startsWith("0x") || hex.startsWith("0X")) ? hex.substring(2) : hex;
        if (s.isEmpty() || s.length() > 32) return null;
        if (s.length() % 2 != 0) s = "0" + s;
        try {
            byte[] out = new byte[s.length() / 2];
            for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String pickHighestBitrateVariant(String content) {
        Pattern streamInf = Pattern.compile("#EXT-X-STREAM-INF:[^\\n]*BANDWIDTH=(\\d+)[^\\n]*\\n(\\S+)");
        Matcher m = streamInf.matcher(content);
        long bestBw = -1;
        String bestUrl = null;
        while (m.find()) {
            long bw = Long.parseLong(m.group(1));
            if (bw > bestBw) { bestBw = bw; bestUrl = m.group(2); }
        }
        return bestUrl != null ? bestUrl : content;
    }

    private static String toAbsolute(String relOrAbs, String baseUrl) {
        try {
            URL url = new URL(baseUrl);
            URI base = URI.create(url.getProtocol() + "://" + url.getHost() + (url.getPort() > 0 ? ":" + url.getPort() : "") + url.getPath());
            return base.resolve(relOrAbs).toString();
        } catch (Exception e) {
            return relOrAbs;
        }
    }

    private static String fetchText(String url, Map<String, String> headers) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) conn.setRequestProperty(e.getKey(), e.getValue());
        }
        try (InputStream is = conn.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    public static class Result {
        public final List<String> segmentUrls;
        public final List<Double> durations;
        public final String initUrl;              // EXT-X-MAP 初始化分片（fMP4），无则 null
        public final List<String> segmentKeyUrls; // 每个分片的 AES-128 密钥 URL（无加密则元素为 null）
        public final List<byte[]> segmentKeyIvs;  // 每个分片的显式 IV（无则 null，用媒体序号推导）
        public final long mediaSequence;          // EXT-X-MEDIA-SEQUENCE 起始序号

        public Result(List<String> segmentUrls, List<Double> durations, String initUrl,
                      List<String> segmentKeyUrls, List<byte[]> segmentKeyIvs, long mediaSequence) {
            this.segmentUrls = segmentUrls;
            this.durations = durations;
            this.initUrl = initUrl;
            this.segmentKeyUrls = segmentKeyUrls;
            this.segmentKeyIvs = segmentKeyIvs;
            this.mediaSequence = mediaSequence;
        }
    }
}
