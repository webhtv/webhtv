package com.fongmi.android.tv.ui.dialog;
 
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
 
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
 
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
 
/**
 * 射手字幕 API 客户端。
 *
 * 搜索策略：
 *   1. 优先按文件特征哈希（四段 4KB MD5）匹配——高置信度
 *   2. 如果哈希算不出来 / 射手返回空候选 → 自动降级为"空哈希 + pathinfo 标题"搜索
 *      （射手服务端会按文件名关键词模糊匹配，对 m3u8 拼接文件 / CDN 二次编码过的片源有效）
 *
 * 支持的输入类型：
 *   - 本地文件（file:// 或裸路径）：直接 RandomAccessFile
 *   - http(s):// URL：HEAD/Range 取 Content-Length → 四段 Range GET 算哈希
 *   - m3u8 等无固定文件哈希的 URL：跳过哈希直接降级标题搜索
 */
public final class ShooterSubtitleHelper {
 
    private static final String TAG = "ShooterSub";
    private static final String API = "https://www.shooter.cn/api/subapi.php";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36";
    private static final long MIN_FILE_SIZE = 12L * 1024; // 射手要求文件至少 12KB
    private static final long RANGE_CHUNK = 4L * 1024;
 
    /** 常见的在线视频索引/接口扩展名——哈希对它们无意义，直接跳过算哈希。 */
    private static final String[] INDEX_EXTENSIONS = {
            "m3u8", "m3u", "mpd", "mpd.tmp", "php", "asp", "jsp", "json",
            "xml", "txt", "html", "htm", "shtml", "do", "action", "cgi"
    };
 
    public static class Candidate {
        public final String displayName;   // 显示名，如 "filename | 射手 | srt"
        public final String language;      // zh / eng
        public final String format;        // srt / ass / ssa / vtt
        public final String downloadUrl;   // 射手返回的下载直链
 
        Candidate(String name, String lang, String fmt, String url) {
            this.displayName = name;
            this.language = lang;
            this.format = fmt;
            this.downloadUrl = url;
        }
 
        @Override public String toString() { return displayName; }
    }
 
    /** 用本地文件搜。先走哈希，空则降级标题搜索。 */
    public static List<Candidate> search(File file) throws Exception {
        List<Candidate> empty = new ArrayList<>();
        if (file == null || !file.isFile()) return empty;
        String hash = computeHash(file);
        String title = extractCleanTitle(file.getName());
        if (hash != null) {
            List<Candidate> r = postSearch(hash, file.getAbsolutePath(), title);
            if (!r.isEmpty()) return r;
            SpiderDebug.log(TAG, "hash search empty for " + file.getName() + ", fallback to pathinfo");
        } else {
            SpiderDebug.log(TAG, "hash unavailable for " + file.getName() + ", try pathinfo only");
        }
        // 降级：空哈希 + pathinfo 搜索
        return postSearchTitle(file.getAbsolutePath(), title);
    }
 
    /** 通用 URL 入口。先走哈希，空则降级标题搜索。 */
    public static List<Candidate> searchUrl(String url) throws Exception {
        List<Candidate> empty = new ArrayList<>();
        if (url == null || url.isEmpty()) return empty;
        String clean = url.trim();
        String scheme;
        String pathinfo;
        String fileName;
        URI uri;
        try {
            uri = URI.create(clean);
            scheme = uri.getScheme();
            pathinfo = clean;
            fileName = extractFileName(uri.getPath());
        } catch (Exception e) {
            return empty;
        }

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String title = extractCleanTitle(fileName);
            String ext = extension(fileName).toLowerCase(Locale.ROOT);
            // 索引文件 / 接口 URL 直接跳过哈希算——对它们算出来也撞不上射手
            boolean skipHash = isIndexExtension(ext) || ext.isEmpty();
            if (!skipHash) {
                String hash = computeHttpRangeHash(clean);
                if (hash != null) {
                    List<Candidate> r = postSearch(hash, clean, title);
                    if (!r.isEmpty()) return r;
                    SpiderDebug.log(TAG, "http hash search empty, fallback to pathinfo");
                } else {
                    SpiderDebug.log(TAG, "http hash unavailable (no Range / too small), try pathinfo");
                }
            } else {
                SpiderDebug.log(TAG, "skip hash for index/interface url (" + ext + "), pathinfo only");
            }
            return postSearchTitle(clean, title);
        }

        if ("file".equalsIgnoreCase(scheme)) {
            String p = uri.getPath();
            if (p != null && p.startsWith("//")) p = p.substring(1);
            File file = new File(p == null ? clean : p);
            return search(file);
        }

        // 兜底：直接按文件路径试
        File f = new File(clean);
        if (f.isFile()) return search(f);
        // 再兜底：当作裸 URL 走 pathinfo 降级
        String title = extractCleanTitle(fileName);
        return postSearchTitle(clean, title);
    }
 
    /** 射手四段哈希（本地文件版）。返回 null 表示不可用（文件太小等）。 */
    public static String computeHash(File file) {
        if (file == null || !file.isFile()) return null;
        long size = file.length();
        if (size < MIN_FILE_SIZE) return null;
        long[] offsets = new long[]{4 * 1024L, size / 3 * 2, size / 3, size - 8 * 1024L};
        offsets[3] = Math.max(offsets[3], 0L);
        byte[] buffer = new byte[4 * 1024];
        StringBuilder sb = new StringBuilder();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (long offset : offsets) {
                raf.seek(offset);
                int read = raf.read(buffer);
                if (read <= 0) continue;
                md.reset();
                md.update(buffer, 0, read);
                if (sb.length() > 0) sb.append(';');
                sb.append(hex(md.digest()));
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(TAG, "computeHash failed: " + e);
            return null;
        }
    }
 
    /** 对 HTTP(S) URL 发 Range 请求取四段 4KB 算射手哈希。 */
    public static String computeHttpRangeHash(String url) {
        try {
            long size = probeContentLength(url);
            if (size < MIN_FILE_SIZE) {
                SpiderDebug.log(TAG, "skip http: too small " + url + " size=" + size);
                return null;
            }
            long[] offsets = new long[]{4 * 1024L, size / 3 * 2, size / 3, Math.max(size - 8 * 1024L, 0L)};
            byte[] buffer = new byte[(int) RANGE_CHUNK];
            StringBuilder sb = new StringBuilder();
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (long offset : offsets) {
                Request req = new Request.Builder().url(url)
                        .header("User-Agent", UA)
                        .header("Range", "bytes=" + offset + "-" + (offset + RANGE_CHUNK - 1))
                        .get().build();
                try (Response resp = OkHttp.client().newCall(req).execute()) {
                    if (resp.code() != 206 || resp.body() == null) {
                        SpiderDebug.log(TAG, "range fail code=" + resp.code() + " offset=" + offset + " url=" + url);
                        return null;
                    }
                    InputStream in = resp.body().byteStream();
                    int read = 0;
                    int n;
                    while (read < buffer.length && (n = in.read(buffer, read, buffer.length - read)) > 0) {
                        read += n;
                    }
                    if (read <= 0) continue;
                    md.reset();
                    md.update(buffer, 0, read);
                    if (sb.length() > 0) sb.append(';');
                    sb.append(hex(md.digest()));
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(TAG, "computeHttpRangeHash fail " + url + ": " + e);
            return null;
        }
    }
 
    /** 对 URL 发 HEAD 或 Range: bytes=0-0 拿 Content-Length。 */
    private static long probeContentLength(String url) {
        try {
            Request head = new Request.Builder().url(url)
                    .header("User-Agent", UA).head().build();
            try (Response resp = OkHttp.client().newCall(head).execute()) {
                String cl = resp.header("Content-Length");
                if (cl != null) return Long.parseLong(cl);
            }
        } catch (Exception ignored) {}
        try {
            Request req = new Request.Builder().url(url)
                    .header("User-Agent", UA)
                    .header("Range", "bytes=0-0").get().build();
            try (Response resp = OkHttp.client().newCall(req).execute()) {
                String cr = resp.header("Content-Range");
                if (cr != null) {
                    int slash = cr.lastIndexOf('/');
                    if (slash >= 0) return Long.parseLong(cr.substring(slash + 1));
                }
                String cl = resp.header("Content-Length");
                if (cl != null) return Long.parseLong(cl);
            }
        } catch (Exception ignored) {}
        return 0;
    }
 
    /** 真正向射手 POST（filehash 有值）。 */
    private static List<Candidate> postSearch(String hash, String pathinfo, String displayName) throws Exception {
        FormBody body = new FormBody.Builder()
                .add("filehash", hash)
                .add("pathinfo", pathinfo)
                .add("format", "json")
                .add("lang", "chn")
                .build();
        return doPost(body, displayName == null || displayName.isEmpty() ? pathinfo : displayName);
    }
 
    /** 降级搜索：filehash 传空，射手按 pathinfo 里的标题关键词模糊匹配。 */
    private static List<Candidate> postSearchTitle(String pathinfo, String displayName) throws Exception {
        FormBody body = new FormBody.Builder()
                .add("filehash", "")
                .add("pathinfo", pathinfo)
                .add("format", "json")
                .add("lang", "chn")
                .build();
        return doPost(body, displayName == null || displayName.isEmpty() ? pathinfo : displayName);
    }
 
    private static List<Candidate> doPost(FormBody body, String displayName) throws Exception {
        Request request = new Request.Builder().url(API).header("User-Agent", UA).post(body).build();
        try (Response response = OkHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                SpiderDebug.log(TAG, "search http fail code=" + response.code());
                return new ArrayList<>();
            }
            String raw = response.body().string();
            if (raw == null || raw.isEmpty()) return new ArrayList<>();
            List<Candidate> r = parse(raw, displayName);
            SpiderDebug.log(TAG, (r.isEmpty() ? "no candidates" : r.size() + " candidates") + " for pathinfo=" + displayName);
            return r;
        }
    }
 
    private static List<Candidate> parse(String json, String displayName) {
        List<Candidate> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return out;
            for (JsonElement group : root.getAsJsonArray()) {
                if (!group.isJsonObject()) continue;
                JsonArray files = group.getAsJsonObject().has("Files")
                        && group.getAsJsonObject().get("Files").isJsonArray()
                        ? group.getAsJsonObject().getAsJsonArray("Files") : new JsonArray();
                for (JsonElement el : files) {
                    if (!el.isJsonObject()) continue;
                    JsonObject f = el.getAsJsonObject();
                    String link = f.has("Link") && !f.get("Link").isJsonNull()
                            ? f.get("Link").getAsString() : "";
                    if (link.isEmpty()) continue;
                    String ext = f.has("Ext") && !f.get("Ext").isJsonNull()
                            ? f.get("Ext").getAsString() : "";
                    String format = detectFormat(ext, link);
                    out.add(new Candidate(
                            displayName + " | 射手 | " + format,
                            "zh", format, link));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(TAG, "parse failed: " + e);
        }
        return out;
    }
 
    /** 把候选字幕下载到本地。返回最终 .srt/.ass/.vtt 文件。 */
    public static File download(Candidate candidate, File destDir) throws Exception {
        if (candidate == null || destDir == null) throw new IllegalArgumentException();
        if (!destDir.exists()) destDir.mkdirs();
        String safeName = "shooter_" + System.currentTimeMillis() + "." + candidate.format;
        File target = new File(destDir, safeName);
        Request request = new Request.Builder().url(candidate.downloadUrl)
                .header("User-Agent", UA).get().build();
        try (Response response = OkHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("download code=" + response.code());
            }
            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
        return target;
    }
 
    /** 从文件名/URL 提取"可读标题"——剥掉扩展名、查询串、目录层级。 */
    private static String extractCleanTitle(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) return "";
        String s = pathOrUrl;
        // URL 场景：去掉查询串和锚点
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        // 取末段文件名
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        // 剥掉最后一个扩展名（只剥最后一个，保留中间的 .2024.1080p. 这类信息）
        int dot = s.lastIndexOf('.');
        if (dot > 0 && dot < s.length() - 1) {
            String maybeExt = s.substring(dot + 1).toLowerCase(Locale.ROOT);
            // 扩展名小于 8 个字符、不含空格 → 视为扩展名剥掉
            if (maybeExt.length() <= 8 && !maybeExt.contains(" ")) {
                s = s.substring(0, dot);
            }
        }
        return s.trim();
    }
 
    private static boolean isIndexExtension(String ext) {
        if (ext == null || ext.isEmpty()) return true;
        for (String ie : INDEX_EXTENSIONS) if (ie.equals(ext)) return true;
        return false;
    }
 
    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1);
    }
 
    private static String detectFormat(String ext, String url) {
        String raw = (ext == null || ext.isEmpty()) ? extFrom(url) : ext;
        if (raw != null) raw = raw.toLowerCase(Locale.ROOT).replace(".", "");
        if ("ass".equals(raw) || "ssa".equals(raw) || "srt".equals(raw) || "vtt".equals(raw)) return raw;
        return "srt";
    }
 
    private static String extFrom(String url) {
        if (url == null) return "";
        int i = url.lastIndexOf('.');
        int q = url.indexOf('?', i);
        if (i < 0) return "";
        if (q < 0) return url.substring(i + 1);
        return url.substring(i + 1, q);
    }
 
    private static String extractFileName(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
 
    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }
}
