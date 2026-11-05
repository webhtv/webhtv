package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 插件包索引：把一个 zip（GitHub 源码包、自打包、任意来源）里有几个插件、分别叫什么、
 * 声明了什么权限，在"导入"之前就先读出来给用户挑。
 *
 * <p><b>为什么必须先索引再导入。</b>一个 GitHub 仓库常带多个插件，用户点一次"从链接安装"
 * 不该被硬塞进整包内容；而"装哪一个"这个决定只能由用户做。索引就是让这个决定变得可做的
 * 那一步：它给出候选清单与每个候选的元数据，导入侧再按用户勾选逐个跑既有导入流程。
 *
 * <p><b>本类纯文件检查：无网络、无 Android API。</b>因此它可以被普通 JVM 单测完整覆盖，
 * 也保证"列清单"这一步没有任何机会偷偷下载或触碰设备能力。真正的下载在
 * {@link PluginRemoteSource}，真正的落盘在 {@link PluginPackageImporter}。
 *
 * <p><b>索引不承担安全职责。</b>这里只做"发现"：跳过明显越界的条目名、跳过超限清单，
 * 是为了不给用户一个注定导入失败的候选；真正的写入安全由
 * {@link PluginPackageImporter#importBundle(File, List)} 在解压时再判一次——
 * 索引阶段放过的条目，解压阶段仍会因越界被拒。
 *
 * <p><b>不剥掉公共顶层前缀。</b>GitHub 的 zip 总是包一层 {@code repo-main/}，看起来"多余"，
 * 但它对正确性毫无影响：安装目录名来自清单 {@code id}，与 zip 内路径无关。剥前缀需要猜
 * "哪一层才是公共前缀"，猜错就是把所有候选都指向错误目录；记录完整路径则永远指向真实位置。
 */
public final class PluginBundleIndex {

    /** 单次索引最多返回多少个候选：足够覆盖仓库合集，又不至于让选择界面变成一堵墙。 */
    public static final int MAX_CANDIDATES = 64;

    /** 包内清单位置：与 {@link PluginSourceConfig} 共用同一文件名。 */
    private static final String MANIFEST_NAME = PluginSourceConfig.MANIFEST_NAME;

    /** macOS 打包残留目录，里面的清单是另一份拷贝，不能算第二个插件。 */
    private static final String MACOSX_DIR = "__macosx";

    /** Windows 盘符形态的绝对路径（{@code C:/a}）：在所有平台上都按越界处理。 */
    private static final Pattern DRIVE_PATTERN = Pattern.compile("^[A-Za-z]:");

    /**
     * 索引阶段认可的运行时：与 {@link PluginPackageImporter#importFrom(File)} 的拒绝口径一致。
     *
     * <p>清单解析已由 {@link PluginManifest#parse} 按注册表判过一次"是否已知引擎"，这里再判一次
     * 是本类自己的契约：索引的产物是"可以导入"的候选，而导入只支持这三种。注册表将来新增
     * 一个引擎时，这里会显式地继续只列出可导入的那些，而不是把用户导向一次必然失败的导入。
     */
    private static final Set<String> SUPPORTED_APIS = Set.of("js", "py", "html");

    /**
     * 一个已发现的插件。
     *
     * @param entryPath   zip 内目录（根目录为 {@code ""}），例如 {@code repo-main/douban}；不含结尾斜杠
     * @param id          清单 id（导入后的安装目录名）
     * @param name        清单显示名
     * @param version     清单插件版本
     * @param runtimeApi  {@code js} / {@code py} / {@code html}
     * @param permissions 清单声明的（已按能力注册表过滤的）权限
     */
    public record Candidate(String entryPath, String id, String name, String version,
            String runtimeApi, Set<String> permissions) {
    }

    /**
     * 索引结果。
     *
     * @param candidates 已按 {@code entryPath} 排序、按 id 去重、最多 {@link #MAX_CANDIDATES} 个
     * @param error      {@code null} 表示压缩包读成功；非空时 {@code candidates} 为空
     */
    public record Result(List<Candidate> candidates, @Nullable String error) {

        public boolean isSuccess() {
            return error == null;
        }
    }

    private PluginBundleIndex() {
    }

    /**
     * 扫描一个 zip，列出其中所有可导入的插件。
     *
     * <p>本方法不抛异常：不是 zip、文件不存在、IO 失败、条目名越界都只是在结果里少一个候选
     * 或多一条 {@link Result#error()}。调用方是设置页的点击回调，异常会直接终止界面。
     */
    public static Result inspect(@Nullable File zip) {
        if (zip == null) return new Result(List.of(), "未选择插件包");
        if (!zip.exists()) return new Result(List.of(), "插件包不存在：" + zip.getAbsolutePath());
        if (!zip.isFile()) return new Result(List.of(), "插件包不是文件：" + zip.getAbsolutePath());

        List<Candidate> found = new ArrayList<>();
        try (ZipFile archive = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = safeEntryName(entry.getName());
                if (name == null) continue;
                if (!isManifestEntry(name)) continue;
                Candidate candidate = candidateOf(archive, entry, name);
                if (candidate != null) found.add(candidate);
            }
        } catch (IOException | RuntimeException error) {
            // 打不开 = 不是 zip 或已被删除，两者对用户是一件事：这个文件不能当插件包用。
            return new Result(List.of(), "无法读取压缩包：" + describe(error));
        }
        return new Result(select(found), null);
    }

    /**
     * 排序、按 id 去重、截断。
     *
     * <p>先去重后截断：两个目录声明同一个 id 时只留排序在前的那个（顺序稳定，用户看到的
     * 永远是同一个），否则选择界面会出现两个"可导入"的同一插件，装第二个等于覆盖第一个。
     */
    private static List<Candidate> select(List<Candidate> found) {
        found.sort(Comparator.comparing(Candidate::entryPath));
        Set<String> seen = new HashSet<>();
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : found) {
            if (selected.size() >= MAX_CANDIDATES) break;
            if (!seen.add(candidate.id())) continue;
            selected.add(candidate);
        }
        return List.copyOf(selected);
    }

    /**
     * 读一份清单并转成候选；任何一项不合规都返回 {@code null}（跳过这一个，不影响其他候选）。
     */
    @Nullable
    private static Candidate candidateOf(ZipFile archive, ZipEntry entry, String name) {
        // 先看声明大小再看内容：ZipEntry 的大小来自中央目录，恶意包可以谎报，所以读取时还有一次计数上限。
        if (entry.getSize() > PluginManifest.MAX_MANIFEST_BYTES) return null;
        String text = readText(archive, entry);
        if (text == null || text.isEmpty()) return null;
        PluginManifest manifest = PluginManifest.parse(text);
        if (manifest == null) return null;
        String api = manifest.getRuntimeApi() == null ? "" : manifest.getRuntimeApi().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_APIS.contains(api)) return null;
        if (!PluginSourceConfig.isSafeId(manifest.getId())) return null;
        return new Candidate(parentPathOf(name), manifest.getId(), manifest.getName(), manifest.getVersion(),
                api, manifest.getPermissions());
    }

    /**
     * 条目名归一化：返回 {@code null} 表示这个条目一律忽略。
     *
     * <p>判据与导入侧一致，理由也一致：绝对路径、{@code ..} 段、反斜杠、盘符都是"想写到压缩包
     * 之外"的写法；以 {@code .} 开头的段（{@code .git/}、{@code .hidden/}）则不是插件内容。
     */
    @Nullable
    private static String safeEntryName(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.indexOf('\\') >= 0) return null;
        if (raw.startsWith("/")) return null;
        if (DRIVE_PATTERN.matcher(raw).lookingAt()) return null;

        String[] segments = raw.split("/", -1);
        StringBuilder normalized = new StringBuilder(raw.length());
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.isEmpty()) {
                // 末尾斜杠是目录条目的正常写法；中间的 "a//b" 不是。
                if (index != segments.length - 1) return null;
                continue;
            }
            if (segment.startsWith(".")) return null;
            if (MACOSX_DIR.equalsIgnoreCase(segment)) return null;
            if (normalized.length() > 0) normalized.append('/');
            normalized.append(segment);
        }
        return normalized.length() == 0 ? null : normalized.toString();
    }

    /** 是否为清单文件：文件名必须正好是 {@code manifest.json}，{@code mymanifest.json} 不算。 */
    private static boolean isManifestEntry(String normalizedName) {
        int slash = normalizedName.lastIndexOf('/');
        String fileName = slash < 0 ? normalizedName : normalizedName.substring(slash + 1);
        return MANIFEST_NAME.equals(fileName);
    }

    /** zip 内目录：根目录返回空串（不是 "/"，也不是 "."），避免调用方各自猜写法。 */
    private static String parentPathOf(String normalizedName) {
        int slash = normalizedName.lastIndexOf('/');
        return slash < 0 ? "" : normalizedName.substring(0, slash);
    }

    /**
     * 读清单文本，超过 {@link PluginManifest#MAX_MANIFEST_BYTES} 即放弃。
     *
     * <p>手写计数循环：{@code InputStream.readAllBytes()} 要到 Android API 33，
     * 真机 minSdk 24 上会直接 {@code NoSuchMethodError}。
     */
    @Nullable
    private static String readText(ZipFile archive, ZipEntry entry) {
        try (InputStream input = archive.getInputStream(entry)) {
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

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }
}
