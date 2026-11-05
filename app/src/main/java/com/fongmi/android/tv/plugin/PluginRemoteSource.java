package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 远程插件源：把用户粘进来的一条地址，翻译成"实际可下载的 zip 地址"，并把它下载到临时文件。
 *
 * <p><b>为什么需要翻译。</b>用户手里通常是浏览器地址栏里那个 {@code github.com/owner/repo}，
 * 而那不是 zip：直接下载会得到 HTML 页面。真正的 zip 在 {@code codeload.github.com} 上，
 * 且需要知道分支名。分支名只有 GitHub API 知道，而那个 API 有速率限制、还可能被墙——
 * 为了"装个插件"去赌一次 API 调用不划算，所以这里退化成"main 再 master"两个候选顺序试。
 * 用户明确给了 {@code /tree/<branch>} 时就不必猜，只给一个候选。
 *
 * <p><b>只收 https。</b>{@code http} 会被中间人替换成任意脚本，而这些脚本随后以插件身份
 * 在宿主里跑——所以 http 是刻意拒绝的，不是漏写。{@code file://} / {@code content://} 同理：
 * 它们绕过"远程包"这一前提（本地目录导入另有入口）。
 *
 * <p><b>带账号密码的地址一律拒绝。</b>这类 URL 一旦进入日志、界面对话框或错误信息，
 * 口令就等于公开了。因此不是"错别字"，而是直接拒收。
 *
 * <p>{@link #resolve(String)} 是纯字符串处理，可被普通 JVM 单测完整覆盖；{@link #download}
 * 依赖项目共享的 OkHttp 客户端，只能真机验证。
 */
public final class PluginRemoteSource {

    /** 插件包体积上限：脚本插件不该有 16MB，超过它基本可以断定"这不是插件包"。 */
    public static final long MAX_BUNDLE_BYTES = 16L * 1024L * 1024L;

    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_WWW_HOST = "www.github.com";
    private static final String CODELOAD_PREFIX = "https://codeload.github.com/";

    /** 猜不到分支时的尝试顺序：新仓库默认 main，老仓库默认 master。 */
    private static final List<String> FALLBACK_BRANCHES = List.of("main", "master");

    /** owner / repo / 分支名的保守字符集：GitHub 实际允许的更宽，但更宽的字符在这里只会带来歧义。 */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    /** 路径穿越段：它们能通过 {@link #NAME_PATTERN}，所以在名字校验里单独排除。 */
    private static final String DOT = ".";
    private static final String DOT_DOT = "..";

    private static final byte[] ZIP_LOCAL_HEADER = {'P', 'K', 3, 4};
    private static final byte[] ZIP_EMPTY_HEADER = {'P', 'K', 5, 6};

    /**
     * 解析结果。
     *
     * @param urls  按优先级排列的下载地址；失败时为空
     * @param error 面向用户的原因；成功时为 {@code null}
     */
    public record Resolution(List<String> urls, @Nullable String error) {

        public boolean isSuccess() {
            return error == null && urls != null && !urls.isEmpty();
        }
    }

    private PluginRemoteSource() {
    }

    /**
     * 把用户输入的地址翻译成有序的下载候选。
     *
     * <p>只接受三种形态：GitHub 仓库根、GitHub {@code /tree/<分支>[/<子目录>]}、
     * 任意主机上的 {@code https} 直链 {@code .zip}。其余一律给出可读的中文原因。
     */
    public static Resolution resolve(@Nullable String rawUrl) {
        String raw = rawUrl == null ? "" : rawUrl.trim();
        if (raw.isEmpty()) return fail("请输入插件地址");

        // 解析前先拒 userinfo：URI 解析失败时拿不到 authority，届时就无法判断该不该回显这段文本。
        if (raw.indexOf('@') >= 0) return fail("地址包含账号或密码，出于安全已拒绝该链接");

        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException error) {
            return fail("地址格式不合法，请检查是否含有空格或非法字符");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.isEmpty()) return fail("地址必须以 https:// 开头");
        if ("http".equals(scheme)) return fail("只接受 https 地址：http 已被刻意拒绝，明文下载的插件可被中间人替换");
        if ("file".equals(scheme) || "content".equals(scheme)) {
            return fail("不支持 " + scheme + ":// 地址，远程安装只接受 https 链接");
        }
        if (!"https".equals(scheme)) return fail("只接受 https 地址，当前是 " + scheme + "://");

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return fail("地址缺少主机名");

        List<String> segments = segmentsOf(uri.getRawPath());
        if (GITHUB_HOST.equals(host) || GITHUB_WWW_HOST.equals(host)) return resolveGitHub(segments);
        return resolveDirectZip(uri, raw);
    }

    /**
     * 下载到 {@code target}：按顺序尝试每个地址，第一个成功即返回。
     *
     * <p>边下边计数上限，不把整个响应读进内存；任何失败路径都删掉目标文件——半截 zip
     * 比没有更糟，它会让下一步的索引报一个与真实原因无关的"压缩包损坏"。
     *
     * @throws IOException 全部候选都失败时抛出，消息里带尝试次数与最后一次失败原因
     */
    public static File download(@Nullable List<String> urls, @Nullable File target, int timeoutSeconds)
            throws IOException {
        if (urls == null || urls.isEmpty()) throw new IOException("没有可用的下载地址");
        if (target == null) throw new IOException("未指定下载目标文件");
        int timeout = timeoutSeconds < 1 ? PluginNetBridge.DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;

        IOException last = null;
        int tried = 0;
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            tried++;
            try {
                fetch(url, target, timeout);
                return target;
            } catch (IOException error) {
                last = error;
                delete(target);
            }
        }
        if (last == null) throw new IOException("没有可用的下载地址");
        throw new IOException("下载失败（已尝试 " + tried + " 个地址）：" + describe(last), last);
    }

    /** 单个地址的一次下载。成功返回后 {@code target} 必定是一个以 zip 头开头的文件。 */
    private static void fetch(String url, File target, int timeout) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建下载目录：" + parent.getAbsolutePath());
        }
        Request request = new Request.Builder().url(url).build();
        try (Response response = OkHttp.client(TimeUnit.SECONDS.toMillis(timeout)).newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + "：" + url);
            ResponseBody body = response.body();
            if (body == null) throw new IOException("响应为空：" + url);
            // 服务器给了长度就先拦一次：省得白下 100MB 再报超限。
            if (body.contentLength() > MAX_BUNDLE_BYTES) throw new IOException("插件包超过 " + MAX_BUNDLE_BYTES + " 字节上限");
            write(body, target);
        } catch (IOException error) {
            delete(target);
            throw error;
        } catch (RuntimeException error) {
            // OkHttp 在极端输入（空 URL、被中断的调用）下会抛运行时异常；对调用方统一成 IO 失败。
            delete(target);
            throw new IOException("下载异常：" + describe(error), error);
        }
        if (!hasZipHeader(target)) {
            delete(target);
            throw new IOException("下载到的内容不是 zip");
        }
    }

    private static void write(ResponseBody body, File target) throws IOException {
        long total = 0;
        try (InputStream input = body.byteStream(); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BUNDLE_BYTES) throw new IOException("插件包超过 " + MAX_BUNDLE_BYTES + " 字节上限");
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }

    /** 空压缩包的正常头是 {@code PK\x05\x06}（无条目）；其余非 zip 内容一律拒绝。 */
    private static boolean hasZipHeader(File file) {
        if (!file.isFile() || file.length() < 4) return false;
        byte[] header = new byte[4];
        try (InputStream input = new java.io.FileInputStream(file)) {
            int read = 0;
            while (read < 4) {
                int step = input.read(header, read, 4 - read);
                if (step == -1) return false;
                read += step;
            }
        } catch (IOException error) {
            return false;
        }
        return matches(header, ZIP_LOCAL_HEADER) || matches(header, ZIP_EMPTY_HEADER);
    }

    private static boolean matches(byte[] header, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (header[index] != expected[index]) return false;
        }
        return true;
    }

    private static Resolution resolveGitHub(List<String> segments) {
        if (segments.size() < 2) return fail("GitHub 地址需要 owner/repo 两段路径");
        String owner = nameOf(segments.get(0));
        String repo = nameOf(stripGitSuffix(segments.get(1)));
        if (owner == null || repo == null) return fail("GitHub 的 owner/repo 只能包含字母、数字、点、下划线、连字符");

        if (segments.size() == 2) {
            List<String> urls = new ArrayList<>(FALLBACK_BRANCHES.size());
            for (String branch : FALLBACK_BRANCHES) {
                urls.add(CODELOAD_PREFIX + owner + "/" + repo + "/zip/refs/heads/" + branch);
            }
            return new Resolution(List.copyOf(urls), null);
        }
        if (!"tree".equalsIgnoreCase(segments.get(2))) {
            return fail("GitHub 只支持仓库根地址或 /tree/<分支> 地址，不支持 " + segments.get(2) + " 页面");
        }
        if (segments.size() < 4) return fail("GitHub /tree/ 地址缺少分支名");
        String branch = nameOf(segments.get(3));
        if (branch == null) return fail("GitHub 分支名只能包含字母、数字、点、下划线、连字符");
        return new Resolution(List.of(CODELOAD_PREFIX + owner + "/" + repo + "/zip/refs/heads/" + branch), null);
    }

    /** 非 GitHub 主机：只收直链 {@code .zip}（大小写不敏感，忽略查询串与片段）。 */
    private static Resolution resolveDirectZip(URI uri, String raw) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!path.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return fail("只支持 GitHub 仓库地址或 https 直链 .zip 文件");
        }
        // 回显原始输入而不是重组 URI：直链可能带签名参数（{@code ?token=...}），重组会把它丢掉。
        return new Resolution(List.of(raw), null);
    }

    private static List<String> segmentsOf(@Nullable String rawPath) {
        List<String> segments = new ArrayList<>();
        if (rawPath == null || rawPath.isEmpty()) return segments;
        for (String segment : rawPath.split("/")) {
            if (!segment.isEmpty()) segments.add(segment);
        }
        return segments;
    }

    /**
     * 解码并校验一段名字。
     *
     * <p>先解码再校验：{@code %2F} 解出来是路径分隔符，它不可能是一段合法名字，必须拒；
     * 反过来先校验原文则会放过 {@code %2F}（它的每个字符都"看起来合法"）。
     */
    @Nullable
    private static String nameOf(@Nullable String segment) {
        if (segment == null) return null;
        String decoded;
        try {
            decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8);
        } catch (RuntimeException error) {
            return null;
        }
        if (DOT.equals(decoded) || DOT_DOT.equals(decoded)) return null;
        return NAME_PATTERN.matcher(decoded).matches() ? decoded : null;
    }

    /** GitHub 的 clone 地址以 {@code .git} 结尾；用户常直接粘它。 */
    private static String stripGitSuffix(String repo) {
        if (repo.length() > 4 && repo.toLowerCase(Locale.ROOT).endsWith(".git")) {
            return repo.substring(0, repo.length() - 4);
        }
        return repo;
    }

    private static Resolution fail(String message) {
        return new Resolution(List.of(), message);
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        // 删不掉无法补救（占用/权限），只能尽力：调用方随后会因为文件不是 zip 而拒绝它。
        file.delete();
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }
}
