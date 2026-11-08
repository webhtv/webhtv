package com.fongmi.android.tv.tts;

import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 在线朗读音频缓存（对齐 legado HttpReadAloudService 的「按段落 MD5 落盘」思路）。
 *
 * 与 legado 的差异：不引入 media3 的 {@code SimpleCache} / {@code DownloadRequest}，
 * 因为这里每次只需复用一小段 mp3，直接用应用缓存目录 + 按最后修改时间淘汰的
 * 容量上限即可，避免为朗读再加一个数据库与索引。
 */
public final class TtsAudioCache {

    private static final String TAG = "TV-tts";
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final long MIN_KEEP_BYTES = 4L * 1024 * 1024;
    private static final String EXTENSION = ".mp3";

    private final File dir;

    public TtsAudioCache(File root, String engineId) {
        this.dir = new File(root, sanitize(engineId));
        if (!dir.exists()) dir.mkdirs();
    }

    public File dir() {
        return dir;
    }

    /** 缓存文件（不保证存在）。 */
    public File file(String engineId, String text) {
        return new File(dir, key(engineId, text) + EXTENSION);
    }

    public boolean has(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }

    /** 把输入流写入缓存文件（先写临时文件再原子改名，避免半截文件被当成命中）。 */
    public boolean store(File target, InputStream in) {
        if (target == null || in == null) return false;
        File tmp = new File(target.getAbsolutePath() + ".part");
        try (InputStream stream = in; OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = stream.read(buf)) > 0) {
                total += n;
                out.write(buf, 0, n);
                // 单段音频超过 8MB 视为异常响应，避免坏引擎把缓存撑爆
                if (total > 8L * 1024 * 1024) throw new IllegalStateException("audio too large");
            }
            out.flush();
            if (total <= 0) throw new IllegalStateException("empty audio");
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "store failed %s: %s", target.getName(), e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        evictIfNeeded();
        return true;
    }

    private void evictIfNeeded() {
        File[] files = dir.listFiles();
        if (files == null || files.length < 8) return;
        long total = 0;
        List<File> audio = new ArrayList<>(files.length);
        for (File f : files) {
            if (!f.isFile()) continue;
            if (!f.getName().endsWith(EXTENSION)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                continue;
            }
            total += f.length();
            audio.add(f);
        }
        if (total <= MAX_BYTES) return;
        audio.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long target = Math.max(MIN_KEEP_BYTES, MAX_BYTES / 2);
        for (File f : audio) {
            if (total <= target) break;
            long len = f.length();
            if (f.delete()) total -= len;
        }
        SpiderDebug.log(TAG, "evict cache remaining=%d bytes", total);
    }

    /** 清空缓存（用户切引擎/清理时调用）。 */
    public void clear() {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 缓存键：引擎 id + 文本（文本包含语速/音色造成的差异，因此由调用方拼进 text）。 */
    public static String key(String engineId, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(((engineId == null ? "" : engineId) + "\u0000" + (text == null ? "" : text)).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Throwable e) {
            return Integer.toHexString((engineId + text).hashCode());
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) return "default";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }
        return sb.toString();
    }
}
