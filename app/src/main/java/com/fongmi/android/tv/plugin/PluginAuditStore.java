package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.utils.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 插件审计日志的落盘层：把 {@link PluginAuditLog} 的内存环形缓冲写进 cache 目录。
 *
 * <p><b>为什么要落盘。</b>D2 裁定 {@code net.request} 不做域名白名单，审计日志因此是唯一
 * 的安全观测面。只留在内存里的话，崩溃、被系统杀进程或用户重启之后，那条"某个插件外发了
 * 不该发的请求"的证据就一起消失了——而那恰恰是最该留痕的时刻。</p>
 *
 * <p><b>为什么读取要分两段。</b>{@link #FILE_NAME}.1 是轮转出的较旧一段，主文件是较新一段；
 * 合并时旧在前、新在后，这样"按行取尾部"得到的就是真正最新的记录。</p>
 *
 * <p><b>为什么写盘失败一律静默。</b>调用链是 {@code PluginNetBridge.request -> recordRequest
 * -> 本类}，网络桥那边只 catch {@code IOException|RuntimeException}。审计是观测面，不是功能
 * 面：磁盘满、权限异常都不能让一次网络请求失败，所以每个 IO 动作都自己吞掉异常。</p>
 */
public final class PluginAuditStore {

    /** 审计文件名。前缀 {@code webhtv-plugin-audit} 同时被缓存模块登记（见 CacheModuleRegistry）。 */
    public static final String FILE_NAME = "webhtv-plugin-audit.log";

    /**
     * 单个文件的字节上限：512 KiB。
     *
     * <p>500 条记录按每条最坏 512 B 估算约 256 KiB，取 2 倍余量后定在 512 KiB——正常运行时
     * 这个上限永远碰不到（环形缓冲先丢最旧的），它只是防止畸形数据把文件写到磁盘爆炸的兜底。</p>
     */
    static final int MAX_FILE_BYTES = 512 * 1024;

    /**
     * 轮转之后每个文件保留的最大行数：环形容量的 2 倍。
     *
     * <p>取 2 倍是为了让"轮转 + 读合并"之后仍能回放出完整的 {@link PluginAuditLog#MAX_ENTRIES}
     * 条历史，而不是每轮转一次就白白丢掉一半可用记录。</p>
     */
    static final int MAX_FILE_LINES = PluginAuditLog.MAX_ENTRIES * 2;

    /** 存储后端；测试可替换成内存实现（JVM 单测没有 {@code Init.context()}）。 */
    public interface Sink {
        String read();

        void write(String text);
    }

    private static final Sink FILE_SINK = new Sink() {
        @Override
        public String read() {
            try {
                String older = Path.read(rotatedFile());
                String newer = Path.read(mainFile());
                String merged;
                if (older.isEmpty()) {
                    merged = newer;
                } else if (newer.isEmpty()) {
                    merged = older;
                } else {
                    merged = older + (older.endsWith("\n") ? "" : "\n") + newer;
                }
                return tail(merged, MAX_FILE_BYTES * 2L, PluginAuditLog.MAX_ENTRIES);
            } catch (Throwable error) {
                return "";
            }
        }

        @Override
        public void write(String text) {
            try {
                String value = text == null ? "" : text;
                File main = mainFile();
                File rotated = rotatedFile();
                if (value.isEmpty()) {
                    delete(main);
                    delete(rotated);
                    return;
                }
                if (value.getBytes(StandardCharsets.UTF_8).length <= MAX_FILE_BYTES) {
                    // 内容还在上限内就只留主文件：残留的 .1 会在读取时把已经裁掉的旧记录复活。
                    delete(rotated);
                    writeFile(main, value);
                    return;
                }
                writeFile(rotated, tail(Path.read(main), MAX_FILE_BYTES, MAX_FILE_LINES));
                writeFile(main, tail(value, MAX_FILE_BYTES, MAX_FILE_LINES));
            } catch (Throwable ignored) {
                // 落盘失败只是丢掉持久化能力，不能让调用方看到异常。
            }
        }
    };

    private final Sink sink;
    private static volatile PluginAuditStore instance;

    private PluginAuditStore(Sink sink) {
        this.sink = sink;
    }

    public static PluginAuditStore get() {
        if (instance != null) return instance;
        synchronized (PluginAuditStore.class) {
            if (instance == null) instance = new PluginAuditStore(FILE_SINK);
            return instance;
        }
    }

    /** 仅测试使用：替换后端并重建单例。传 {@code null} 回到默认实现。 */
    static PluginAuditStore useSinkForTest(@Nullable Sink sink) {
        synchronized (PluginAuditStore.class) {
            instance = new PluginAuditStore(sink == null ? FILE_SINK : sink);
            return instance;
        }
    }

    /** 读出落盘的全部记录行；没有文件、读失败或后端不支持都返回空串。 */
    public String read() {
        try {
            String text = sink.read();
            return text == null ? "" : text;
        } catch (Throwable error) {
            return "";
        }
    }

    /** 用给定内容整体覆盖落盘；空串表示清空。任何失败都静默。 */
    public void write(@Nullable String text) {
        try {
            sink.write(text == null ? "" : text);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 只保留尾部若干行，同时受字节上限与行数上限约束，行序不变。
     *
     * <p><b>为什么按整行裁而不是按字节裁。</b>一行就是一条审计记录，从中间截断会让这段 JSON
     * 变成坏行，重启后这条记录直接被丢弃——等于在最需要证据的时候把证据裁掉了一半。</p>
     */
    static String tail(@Nullable String text, long maxBytes, int maxLines) {
        String value = text == null ? "" : text;
        if (value.isEmpty() || maxLines <= 0 || maxBytes <= 0) return "";
        String[] lines = value.split("\n", -1);
        if (lines.length <= maxLines && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        int first = lines.length;
        long bytes = 0;
        while (first > 0 && lines.length - first < maxLines) {
            long size = lines[first - 1].getBytes(StandardCharsets.UTF_8).length + 1L;
            if (bytes + size > maxBytes) break;
            bytes += size;
            first--;
        }
        if (first == lines.length) return "";
        StringBuilder builder = new StringBuilder();
        for (int index = first; index < lines.length; index++) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(lines[index]);
        }
        return builder.toString();
    }

    /**
     * 把一行文本解析成审计记录对象；结构不符或不是本程序写的行返回 {@code null}。
     *
     * <p>坏行必须跳过而不是整份丢弃：日志被截断或半行落盘是真实会发生的情况。</p>
     */
    @Nullable
    static JsonObject object(String line) {
        try {
            JsonElement element = JsonParser.parseString(line);
            if (!element.isJsonObject()) return null;
            return element.getAsJsonObject();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static File mainFile() {
        return Path.cache(FILE_NAME);
    }

    private static File rotatedFile() {
        return Path.cache(FILE_NAME + ".1");
    }

    private static void writeFile(File file, String text) {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static void delete(File file) {
        try {
            if (file.isFile()) file.delete();
        } catch (Throwable ignored) {
        }
    }
}
