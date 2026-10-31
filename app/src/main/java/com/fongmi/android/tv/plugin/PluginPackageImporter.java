package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.utils.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 插件包导入：把用户选中的目录整包复制进 {@code files/plugins/<id>/}，并改写成可装载形态。
 *
 * <p><b>为什么是"复制"而不是"就地引用"。</b>用户选的目录可能是 SAF 临时授权、SD 卡或下载目录，
 * 换次启动就可能不可读。插件必须在无授权的内部存储里才谈得上"装过"。复制同时带来一个关键收益：
 * 落盘后 {@code runtime.entry} 可以直接改成复制后的绝对路径。
 *
 * <p><b>为什么导入期就改写 manifest，而不是装载期。</b>装载路径
 * （{@link PluginBootstrap#reloadAll()}）会在 App 启动时跑，还可能被后台线程重复触发；
 * 每次装载都解析并回写 JSON 既是多余的 IO，也让"盘上清单"与"内存里的清单"存在两个真相。
 * 导入是唯一一次写盘机会，改在这里之后装载只需读现成文件。
 *
 * <p><b>失败必须无残留。</b>半个包比没有包更糟：目录存在但脚本缺失，会让 {@code toManifests()}
 * 读到合法清单、装载期才在运行时炸掉，错误归因指向插件而不是导入。因此任何一步失败都删掉
 * 整个 {@code <id>} 目录——包括"先删旧版本再复制新版本"里旧版本已被删掉的情况（宁可回到
 * 未安装，也不要新旧混杂）。
 *
 * <p><b>只复制必需文件。</b>不递归复制用户选的整棵树：一个误选成"手机存储根目录"的操作
 * 不该把几万个文件搬进应用私有目录。白名单是 {@code manifest.json} 加上清单
 * {@code runtime.entry} 所在目录里的普通文件（插件常把辅助脚本放同级）。
 */
public final class PluginPackageImporter {

    /** 整包体积上限。插件是脚本，不是媒体资源；4MB 已经比单脚本上限宽 8 倍。 */
    public static final long MAX_PACKAGE_BYTES = 4L * 1024L * 1024L;

    /** 单次导入允许的文件数上限，防止"目录里塞上万个小文件"这种退化输入。 */
    public static final int MAX_FILES = 64;

    /** 包内清单位置：与 {@link PluginSourceConfig} 共用同一文件名。 */
    private static final String MANIFEST_NAME = PluginSourceConfig.MANIFEST_NAME;

    /** 插件根目录名，位于 {@code Path.files()} 之下。 */
    private static final String PLUGINS_DIR = "plugins";

    /**
     * 导入结果。
     *
     * @param entry 成功时的配置条目（已持久化）；失败时为 {@code null}
     * @param error 失败原因（面向用户与排查，需具体）；成功时为 {@code null}
     */
    public record Result(@Nullable PluginSourceConfig.Entry entry, @Nullable String error) {

        public boolean isSuccess() {
            return entry != null && error == null;
        }
    }

    /** 测试用根目录覆盖：JVM 单测没有 {@code Init.context()}，无法走 {@code Path.files()}。 */
    private static volatile File rootOverride;

    private PluginPackageImporter() {
    }

    /**
     * 插件包根目录：真机是 {@code files/plugins}，单测可临时覆盖到临时目录。
     *
     * <p>公开是给管理界面用的：根目录名只能有一处定义，界面自己拼字面量的话，
     * 改名后删除会恒判失败而没人发现。
     */
    public static File pluginsRoot() {
        File override = rootOverride;
        if (override != null) return override;
        return new File(Path.files(), PLUGINS_DIR);
    }

    /**
     * 判断一个路径是否严格位于 {@link #pluginsRoot()} 之下，供界面删除包目录前自检。
     *
     * <p>用 canonical 路径比较而不是字符串 startsWith：{@code files/plugins-x} 的字符串以
     * {@code files/plugins} 为前缀，但它是兄弟目录，删它等于删别人的数据。因此必须补上
     * {@link File#separator} 再比。任何异常（含符号链接逃逸）都返回 {@code false}——
     * 失败即拒绝，宁可删不掉，也不越界删。
     */
    public static boolean isOwnedDirectory(@Nullable String directory) {
        if (directory == null) return false;
        String path = directory.trim();
        if (path.isEmpty()) return false;
        try {
            String root = pluginsRoot().getCanonicalPath() + File.separator;
            String target = new File(path).getCanonicalPath();
            return target.length() > root.length() && target.startsWith(root);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 与 {@link PluginSourceConfig#useStoreForTest} 配套的测试接缝。传 {@code null} 恢复默认。 */
    static void overrideRootForTest(@Nullable File root) {
        rootOverride = root;
    }

    /**
     * 导入一个插件包目录。
     *
     * <p>本方法不抛异常：输入不可读、清单非法、超限、IO 失败都以 {@link Result#error()} 返回，
     * 因为调用方是设置页的点击回调，异常会直接终止界面。
     */
    public static Result importFrom(@Nullable File sourceDir) {
        if (sourceDir == null) return new Result(null, "未选择插件目录");
        if (!sourceDir.exists()) return new Result(null, "插件目录不存在：" + sourceDir.getAbsolutePath());
        if (!sourceDir.isDirectory()) return new Result(null, "插件路径不是目录");
        // 符号链接可以让"目录内文件"实际指向任意位置；导入必须只处理真实目录。
        if (isSymlink(sourceDir)) return new Result(null, "拒绝符号链接目录");

        File sourceManifest = new File(sourceDir, MANIFEST_NAME);
        if (!sourceManifest.exists()) return new Result(null, "缺少 " + MANIFEST_NAME);
        if (isSymlink(sourceManifest)) return new Result(null, "拒绝符号链接清单");
        if (!sourceManifest.isFile()) return new Result(null, MANIFEST_NAME + " 不是普通文件");
        if (sourceManifest.length() > PluginManifest.MAX_MANIFEST_BYTES) {
            return new Result(null, "清单超过 " + PluginManifest.MAX_MANIFEST_BYTES + " 字节上限");
        }

        String manifestText = readText(sourceManifest);
        if (manifestText == null || manifestText.isEmpty()) return new Result(null, "清单读取失败或为空");

        // 先单独判运行时：PluginManifest.parse 对"引擎不支持"与"清单非法"都只返回 null，
        // 用户看到同一句"清单不合法"时无法判断该改引擎还是改 JSON，所以这里先分出这一支。
        String runtimeApi = runtimeApiOf(manifestText);
        if (!isSupportedApi(runtimeApi)) {
            return new Result(null, "运行时不支持：" + (runtimeApi == null || runtimeApi.isEmpty() ? "<缺失>" : runtimeApi)
                    + "（仅支持 js / py / html）");
        }

        PluginManifest manifest = PluginManifest.parse(manifestText);
        if (manifest == null) return new Result(null, "清单不合法（协议、版本、sdk、权限或字段越界）");
        if (!PluginSourceConfig.isSafeId(manifest.getId())) return new Result(null, "清单 id 不能作为目录名：" + manifest.getId());

        File entryFile;
        try {
            entryFile = resolveEntry(sourceDir, manifest.getRuntimeEntry());
        } catch (IllegalArgumentException error) {
            return new Result(null, error.getMessage());
        }
        if (entryFile == null) return new Result(null, "入口脚本不存在：" + manifest.getRuntimeEntry());
        if (entryFile.length() > PluginScriptSource.MAX_SCRIPT_BYTES) {
            return new Result(null, "脚本超过 " + PluginScriptSource.MAX_SCRIPT_BYTES + " 字节上限：" + entryFile.getName());
        }

        List<File> planned;
        try {
            planned = planFiles(sourceDir, entryFile);
        } catch (IllegalArgumentException error) {
            return new Result(null, error.getMessage());
        }

        String id = manifest.getId();
        File target = new File(pluginsRoot(), id);
        try {
            copyInto(sourceDir, target, planned, entryFile);
        } catch (ImportFailure error) {
            rollback(target, id);
            return new Result(null, error.getMessage());
        } catch (Throwable error) {
            rollback(target, id);
            return new Result(null, "导入失败：" + describe(error));
        }

        PluginSourceConfig.Entry entry = new PluginSourceConfig.Entry(manifest.getId(), manifest.getName(),
                manifest.getVersion(), manifest.getRuntimeApi(), target.getAbsolutePath(), true,
                System.currentTimeMillis());
        // 导入成功即登记：让"盘上有包"与"配置里有条目"永不脱节。
        PluginSourceConfig config = PluginSourceConfig.get();
        config.put(entry);
        config.save();
        return new Result(entry, null);
    }

    /**
     * 回滚：删掉整个 {@code <id>} 目录，并清掉指向它的配置条目。
     *
     * <p>删目录是硬要求（半个包会让装载期才失败，错误归因跑偏）。同时必须删配置条目：
     * 目录既然没了，条目就只剩"界面上一个永远装不上的插件"这一个含义。重复导入失败时
     * 旧版本一起消失是有意的——宁可回到未安装，也不要"版本号说 A、盘上是 B"。
     */
    private static void rollback(File target, String id) {
        deleteRecursively(target);
        try {
            PluginSourceConfig config = PluginSourceConfig.get();
            if (config.find(id) == null) return;
            config.remove(id);
            config.save();
        } catch (Throwable ignored) {
            // 回滚的第二步失败没有可去之处：目录已经清干净，配置残留会在装载期被跳过。
        }
    }

    /**
     * 列举本包要复制的文件（{@code manifest.json} + 入口脚本所在目录的普通文件）。
     *
     * <p>上限在这里一次性判完，而不是边复制边判：中途判出超限虽然也能回滚，但会把已经
     * 写了一半的字节白写一遍，而且错误信息只能报告"第 N 个文件"，不如整包口径清楚。
     */
    private static List<File> planFiles(File sourceDir, File entryFile) {
        File entryDir = entryFile.getParentFile();
        List<File> files = new ArrayList<>();
        files.add(new File(sourceDir, MANIFEST_NAME));
        File[] siblings = entryDir == null ? null : entryDir.listFiles();
        if (siblings == null) throw new IllegalArgumentException("无法列出入口脚本目录：" + entryDir);
        // 排序只为报告稳定：同一目录在两次导入里给出同样的顺序。
        Arrays.sort(siblings, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : siblings) {
            if (!file.isFile()) continue;
            if (isSymlink(file)) throw new IllegalArgumentException("拒绝符号链接文件：" + file.getName());
            if (!files.contains(file)) files.add(file);
        }
        if (files.size() > MAX_FILES) throw new IllegalArgumentException("文件数超过上限 " + MAX_FILES + "：" + files.size());

        long total = 0;
        for (File file : files) {
            if (!file.isFile()) throw new IllegalArgumentException("不是普通文件：" + file.getName());
            long length = file.length();
            boolean isManifest = MANIFEST_NAME.equals(file.getName()) && sourceDir.equals(file.getParentFile());
            long cap = isManifest ? PluginManifest.MAX_MANIFEST_BYTES : PluginScriptSource.MAX_SCRIPT_BYTES;
            if (length > cap) throw new IllegalArgumentException("文件超过 " + cap + " 字节上限：" + file.getName());
            total += length;
            if (total > MAX_PACKAGE_BYTES) throw new IllegalArgumentException("整包超过 " + MAX_PACKAGE_BYTES + " 字节上限");
        }
        return files;
    }

    /**
     * 解析 {@code runtime.entry} 到包内真实文件。
     *
     * <p>只接受包内相对路径：{@code assets://}、{@code file://}、绝对路径与 URL 在导入语义下
     * 等于"引用包外的东西"，装进来也读不到，必须在导入期就拒掉，否则用户会得到一个
     * 永远加载失败的插件。
     */
    @Nullable
    private static File resolveEntry(File sourceDir, @Nullable String rawEntry) {
        if (rawEntry == null) return null;
        String entry = rawEntry.trim().replace('\\', '/');
        if (entry.isEmpty()) throw new IllegalArgumentException("清单 runtime.entry 为空");
        if (entry.contains("://")) throw new IllegalArgumentException("入口不支持协议前缀：" + rawEntry);
        if (entry.startsWith("/") || entry.matches("^[A-Za-z]:/.*")) throw new IllegalArgumentException("入口必须是包内相对路径：" + rawEntry);
        for (String segment : entry.split("/")) {
            // "." 与 ".." 都不是包内合法段：它们唯一的用途就是逃出源目录。
            if ("..".equals(segment) || ".".equals(segment)) throw new IllegalArgumentException("入口包含路径穿越：" + rawEntry);
        }
        File resolved = new File(sourceDir, entry);
        if (isSymlink(resolved)) throw new IllegalArgumentException("拒绝符号链接入口：" + rawEntry);
        if (!resolved.isFile()) return null;
        if (!contains(sourceDir, resolved)) throw new IllegalArgumentException("入口逃出插件目录：" + rawEntry);
        return resolved;
    }

    /**
     * 复制并改写清单。
     *
     * <p>先删旧目录再建：同 id 重导入必须只有一份内容，混进旧版脚本会让"版本"字段与
     * 实际代码不一致，而这种不一致事后无法从盘上还原。
     */
    private static void copyInto(File sourceDir, File target, List<File> planned, File entryFile) {
        deleteRecursively(target);
        if (!target.mkdirs() && !target.isDirectory()) throw new ImportFailure("无法创建插件目录：" + target.getAbsolutePath());

        File copiedEntry = null;
        File copiedManifest = null;
        for (File source : planned) {
            String relative = relativize(sourceDir, source);
            File destination = new File(target, relative);
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new ImportFailure("无法创建目录：" + parent.getAbsolutePath());
            }
            copyFile(source, destination);
            if (source.equals(entryFile)) copiedEntry = destination;
            if (MANIFEST_NAME.equals(relative)) copiedManifest = destination;
        }
        if (copiedEntry == null) throw new ImportFailure("入口脚本未被复制");
        if (copiedManifest == null) throw new ImportFailure("清单未被复制");
        // 空脚本能装进目录却永远跑不起来（{@code PluginScriptSource.read} 明确拒绝空文本），
        // 必须在导入期就拦住，否则用户要到启动日志里才发现插件"装了但不工作"。
        if (copiedEntry.length() == 0) throw new ImportFailure("入口脚本为空：" + entryFile.getName());
        rewriteEntry(copiedManifest, copiedEntry);
    }

    /**
     * 把复制后清单的 {@code runtime.entry} 改写为脚本绝对路径。
     *
     * <p>绝对路径（Android 上是 {@code /data/...}）正好命中 {@link PluginScriptSource}
     * 支持的第三种入口形态，装载期不需要再做任何 JSON 处理。
     */
    private static void rewriteEntry(File manifestFile, File entryFile) {
        String text = readText(manifestFile);
        if (text == null || text.isEmpty()) throw new ImportFailure("复制后的清单不可读");
        JsonElement root = null;
        try {
            root = JsonParser.parseString(text);
        } catch (RuntimeException ignored) {
            // 落到下面统一报错：这一步失败属于"复制了什么就是什么"的坏输入，不该静默放过。
        }
        if (root == null || !root.isJsonObject()) throw new ImportFailure("复制后的清单不是 JSON 对象");
        JsonObject object = root.getAsJsonObject();
        JsonElement runtime = object.get("runtime");
        if (runtime == null || !runtime.isJsonObject()) throw new ImportFailure("复制后的清单缺少 runtime 对象");
        runtime.getAsJsonObject().addProperty("entry", entryFile.getAbsolutePath());
        writeText(manifestFile, object.toString());
    }

    @Nullable
    private static String runtimeApiOf(String manifestText) {
        try {
            JsonElement root = JsonParser.parseString(manifestText);
            if (root == null || !root.isJsonObject()) return null;
            JsonElement runtime = root.getAsJsonObject().get("runtime");
            if (runtime == null || !runtime.isJsonObject()) return null;
            JsonElement api = runtime.getAsJsonObject().get("api");
            if (api == null || !api.isJsonPrimitive()) return null;
            return api.getAsString().trim().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isSupportedApi(@Nullable String api) {
        if (api == null) return false;
        return switch (api) {
            case "js", "py", "html" -> true;
            default -> false;
        };
    }

    private static String relativize(File base, File file) {
        String basePath = base.getAbsolutePath().replace('\\', '/');
        String filePath = file.getAbsolutePath().replace('\\', '/');
        if (!filePath.startsWith(basePath)) throw new ImportFailure("文件不在插件目录内：" + file.getName());
        String relative = filePath.substring(basePath.length());
        while (relative.startsWith("/")) relative = relative.substring(1);
        if (relative.isEmpty() || relative.contains("..")) throw new ImportFailure("非法相对路径：" + file.getName());
        return relative;
    }

    /** 复制并逐字节计数：长度在复制期间仍可能变化（源被同时写入），所以不能只信 {@code length()}。 */
    private static void copyFile(File source, File destination) {
        long written = 0;
        try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                written += read;
                if (written > PluginScriptSource.MAX_SCRIPT_BYTES) throw new ImportFailure("文件复制中超过体积上限：" + source.getName());
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (IOException error) {
            throw new ImportFailure("复制失败：" + source.getName() + " (" + describe(error) + ")");
        }
    }

    @Nullable
    private static String readText(File file) {
        try (InputStream input = new FileInputStream(file)) {
            // 手写计数循环而不用 InputStream 上那批 Java 9 便捷读法：Android 要到 API 33 才有，
            // core library desugaring 也不补 java.io，minSdk 24 的真机上会直接 NoSuchMethodError。
            // 超过上限返回 null 而不是留下已读部分：截断的清单只会变成一个难查的 JSON 解析错误。
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > PluginManifest.MAX_MANIFEST_BYTES) return null;
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return null;
        }
    }

    private static void writeText(File file, String text) {
        try (OutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException error) {
            throw new ImportFailure("清单改写失败：" + describe(error));
        }
    }

    private static boolean isSymlink(File file) {
        try {
            return Files.isSymbolicLink(file.toPath());
        } catch (Throwable error) {
            // 判不出来就不放行：这一层是安全边界，宁可导入失败。
            return true;
        }
    }

    /** 规范化后做前缀比较：仅靠字符串检查无法排除 {@code ./a/../../b} 这类写法。 */
    private static boolean contains(File base, File file) {
        try {
            String basePath = base.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            return filePath.equals(basePath) || filePath.startsWith(basePath + File.separator);
        } catch (IOException error) {
            return false;
        }
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        // 删除失败无法补救（目录权限/占用），只能尽力：残留的空目录不会让装载误判，
        // 因为装载读的是 manifest.json，读到空串就跳过。
        file.delete();
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }

    /** 内部失败：带一条面向用户的原因，由 {@link #importFrom(File)} 统一转成 {@link Result}。 */
    private static final class ImportFailure extends RuntimeException {

        ImportFailure(String message) {
            super(message);
        }
    }
}
