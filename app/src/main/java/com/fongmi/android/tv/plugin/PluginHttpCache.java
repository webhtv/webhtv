package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.utils.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code webhtv.request} 的宿主侧 HTTP 缓存。
 *
 * <p>插件只声明「这次响应可以复用多久」，其余全部由宿主决定：缓存键、存放位置、失效判据。这样插件既拿不到
 * 宿主的文件系统，也无法用一个错误的键把别人的响应读成自己的。
 *
 * <p>只缓存 GET/HEAD：这两种方法在 HTTP 语义上是安全的，重放不会改变服务端状态；POST/PUT/PATCH/DELETE
 * 即使带了 {@code ttlSeconds} 也必须真的发出去，否则脚本会拿到一个「看起来成功、但服务端从未收到」的结果。
 *
 * <p>缓存键里必须含 {@code pluginId}：多个插件会请求同一个豆瓣 URL，键里若只有 URL，插件 A 的响应就会被插件
 * B 当成自己的命中，等于绕过了缓存目录之外的其它一切隔离。
 *
 * <p>缓存文件名是裸的 SHA-256，不带任何后缀：缓存模块注册表把 {@code .apk/.zip/.tmp/.log} 当成可随时丢弃的
 * 临时文件，给缓存文件接上其中任何一种后缀，它就会被临时文件清理顺手删掉，而不是由本模块自己管。
 *
 * <p>所有方法都不上抛异常：缓存只是这条调用路径上的可选加速，命不中、读不动、写不进都只该退化成一次普通请求。
 */
public final class PluginHttpCache {

    /** 缓存目录名；缓存模块注册表按这个名字把它登记成独立模块。 */
    public static final String DIRECTORY = "plugin-http-cache";

    /** 复用期的硬上限（30 天）：再长的 TTL 会让「清了缓存还是不生效」变得无法解释。 */
    public static final int MAX_TTL_SECONDS = 30 * 24 * 60 * 60;

    /** 只有 200 会被复用，所以命中时能如实回报状态码，而不必额外存一份元数据文件。 */
    static final int CACHE_STATUS = 200;

    private static final Set<String> CACHEABLE_METHODS = Set.of("GET", "HEAD");
    private static final String NO_PLUGIN = "_";
    private static final String TEMP_SUFFIX = ".part";
    private static final int MAX_DIRECTORY_NAME = 64;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** JVM 单测里 {@code Init.context()} 不可用，缓存根目录必须可以注入。 */
    private static volatile File rootForTest;

    private PluginHttpCache() {
    }

    static void overrideRootForTest(@Nullable File root) {
        rootForTest = root;
    }

    /**
     * 解析脚本传来的 {@code ttlSeconds}：缺省、非数字、非正数一律表示「这次不要缓存」。
     *
     * @return 0 表示不缓存；否则是钳到 {@link #MAX_TTL_SECONDS} 之内的秒数
     */
    static int ttlSeconds(@Nullable String raw) {
        if (raw == null) return 0;
        String text = raw.trim();
        if (text.isEmpty()) return 0;
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException error) {
            return 0;
        }
        if (!(value > 0)) return 0;
        if (value >= MAX_TTL_SECONDS) return MAX_TTL_SECONDS;
        int seconds = (int) value;
        return seconds < 1 ? 1 : seconds;
    }

    static boolean cacheable(@Nullable String verb) {
        return verb != null && CACHEABLE_METHODS.contains(verb);
    }

    /** 缓存键：插件 id、方法、URL、请求体、请求头各占一位，任何一位不同都不会互相命中。 */
    static String key(@Nullable String pluginId, String verb, String url, @Nullable String headers,
            @Nullable String body) {
        String material = safe(pluginId) + '\n' + verb + '\n' + url + '\n'
                + sha256(body == null ? "" : body) + '\n' + sha256(canonicalHeaders(headers));
        return sha256(material);
    }

    @Nullable
    static String read(@Nullable String pluginId, @Nullable String verb, String url, @Nullable String headers,
            @Nullable String body, int ttlSeconds) {
        if (ttlSeconds < 1 || !cacheable(verb)) return null;
        try {
            File file = file(pluginId, key(pluginId, verb, url, headers, body));
            if (!file.isFile()) return null;
            long length = file.length();
            // 0 字节只可能是崩溃留下的半成品；超过单次响应上限的文件也不该被当成一次合法命中。
            if (length <= 0 || length > PluginNetBridge.MAX_RESPONSE_BYTES) return null;
            long age = System.currentTimeMillis() - file.lastModified();
            if (age < 0 || age > ttlSeconds * 1000L) return null;
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return text.isEmpty() ? null : text;
        } catch (Throwable error) {
            return null;
        }
    }

    static void write(@Nullable String pluginId, @Nullable String verb, String url, @Nullable String headers,
            @Nullable String body, int ttlSeconds, @Nullable String text) {
        if (ttlSeconds < 1 || !cacheable(verb)) return;
        // 空响应体写成 0 字节文件，下次读只能判成损坏，干脆不写。
        if (text == null || text.isEmpty()) return;
        try {
            File file = file(pluginId, key(pluginId, verb, url, headers, body));
            File directory = file.getParentFile();
            if (directory == null) return;
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) return;
            File temp = new File(directory, file.getName() + TEMP_SUFFIX);
            Files.write(temp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            move(temp, file);
        } catch (Throwable error) {
            // 写缓存失败只丢一次加速机会，绝不让已经成功的请求变成失败。
        }
    }

    static File root() {
        File override = rootForTest;
        return override != null ? override : Path.cache(DIRECTORY);
    }

    static File file(@Nullable String pluginId, String key) {
        return new File(new File(root(), directoryName(pluginId)), key);
    }

    private static void move(File temp, File target) {
        try {
            Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException atomicUnsupported) {
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException error) {
                temp.delete();
            }
        }
    }

    /**
     * 目录名只保留文件系统安全字符：{@code pluginId} 来自清单，不能直接当成路径用。
     * 截断后可能撞名，但缓存键里带着完整的 id，所以撞名只影响可读性，不会串数据。
     */
    private static String directoryName(@Nullable String pluginId) {
        String text = pluginId == null ? "" : pluginId.trim();
        if (text.isEmpty()) return NO_PLUGIN;
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length() && builder.length() < MAX_DIRECTORY_NAME; i++) {
            char value = text.charAt(i);
            boolean safe = (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9') || value == '.' || value == '_' || value == '-';
            builder.append(safe ? value : '_');
        }
        String name = builder.toString();
        return name.isEmpty() || ".".equals(name) || "..".equals(name) ? NO_PLUGIN : name;
    }

    /**
     * 请求头先按小写名排序再拼接，所以键只看「头集合」而不看书写顺序：
     * 同一组头换个顺序不应该变成两次网络请求。
     */
    private static String canonicalHeaders(@Nullable String headers) {
        if (headers == null || headers.trim().isEmpty()) return "";
        try {
            JsonElement parsed = JsonParser.parseString(headers);
            if (!parsed.isJsonObject()) return headers.trim();
            JsonObject object = parsed.getAsJsonObject();
            TreeMap<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (entry.getKey() == null || value == null || !value.isJsonPrimitive()) continue;
                sorted.put(entry.getKey().toLowerCase(Locale.ROOT), value.getAsString());
            }
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                builder.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
            }
            return builder.toString();
        } catch (RuntimeException error) {
            return headers.trim();
        }
    }

    private static String safe(@Nullable String pluginId) {
        return pluginId == null ? NO_PLUGIN : pluginId;
    }

    private static String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(HEX[(value >> 4) & 0xF]).append(HEX[value & 0xF]);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
