package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 插件脚本读取：把清单里的 `runtime.entry` 解析成脚本文本。
 *
 * <p>支持的入口形态与既有字幕源/站源一致（`assets://` / `file://` / 绝对路径），
 * 但不接受任意网络 URL —— 远程加载属于 P6 的分发切片，且必须先有签名与来源校验。
 * 现在放开远程等于把"插件能执行什么代码"交给网络，与 §5 的安全边界冲突。
 */
public final class PluginScriptSource {

    /** 单脚本体积上限，防止超大文件把 JS 引擎内存打爆。 */
    static final int MAX_SCRIPT_BYTES = 512 * 1024;

    private static final String ASSETS_PREFIX = "assets://";
    private static final String FILE_PREFIX = "file://";

    private PluginScriptSource() {
    }

    /**
     * 读取脚本。
     *
     * @return 脚本文本；路径不合法、不存在或超限时返回 {@code null}
     */
    @Nullable
    public static String read(@Nullable String entry) {
        if (entry == null) return null;
        String path = entry.trim();
        if (path.isEmpty()) return null;
        try {
            String text = readPath(path);
            // 必须同时拒绝 null 与空串：{@code Path.read(File)} 在文件不存在或读取失败时
            // 返回 ""，不是 null（见 catvod/utils/Path.java:135-141）。只判 null 会让
            // "文件不存在"被当成"脚本为空"继续往下走，最终在 init 阶段才报错，
            // 错误归因会指向插件脚本而不是入口路径。
            if (text == null || text.isEmpty()) return null;
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_SCRIPT_BYTES) return null;
            return text;
        } catch (Throwable error) {
            return null;
        }
    }

    @Nullable
    private static String readPath(String path) {
        if (path.startsWith(ASSETS_PREFIX)) return Asset.read(path.substring(ASSETS_PREFIX.length()));
        if (path.startsWith(FILE_PREFIX)) return Path.read(new File(path.substring(FILE_PREFIX.length())));
        // 裸路径只认绝对路径，避免相对路径逃逸到不可预期的位置。
        if (path.startsWith("/")) return Path.read(new File(path));
        return null;
    }
}
