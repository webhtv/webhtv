package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * 远程插件源解析测试。
 *
 * <p>只覆盖 {@link PluginRemoteSource#resolve(String)}：它是纯字符串处理，能在普通 JVM 里
 * 完整验证；{@code download} 走项目共享的 OkHttp 客户端（依赖 {@code Init.context()}），
 * 只能真机验证，本测试没有替它造假。
 *
 * <p>断言的重点是两侧：三条接受的形态必须生成正确的 {@code codeload} 地址，其余一切
 * （http、file、content、GitHub 的非仓库页面、非 zip 直链、带口令的地址）必须被拒且给中文原因。
 */
public class PluginRemoteSourceTest {

    private static final String CANONICAL = "https://codeload.github.com/owner/repo/zip/refs/heads/";

    @Test
    public void repoRootYieldsMainThenMaster() {
        assertTwoBranchCandidates("https://github.com/owner/repo");
    }

    @Test
    public void repoRootToleratesTrailingSlash() {
        assertTwoBranchCandidates("https://github.com/owner/repo/");
    }

    @Test
    public void repoRootToleratesDotGitSuffix() {
        assertTwoBranchCandidates("https://github.com/owner/repo.git");
    }

    @Test
    public void repoRootIgnoresQueryAndFragment() {
        assertTwoBranchCandidates("https://github.com/owner/repo?tab=readme-ov-file");
        assertTwoBranchCandidates("https://github.com/owner/repo#install");
    }

    @Test
    public void treeUrlUsesTheGivenBranchOnly() {
        PluginRemoteSource.Resolution result = PluginRemoteSource.resolve("https://github.com/owner/repo/tree/dev");

        assertTrue(result.error(), result.isSuccess());
        assertEquals("用户已经说了分支，就不该再猜 main", 1, result.urls().size());
        assertEquals(CANONICAL + "dev", result.urls().get(0));
    }

    @Test
    public void treeUrlIgnoresSubdirectory() {
        PluginRemoteSource.Resolution result =
                PluginRemoteSource.resolve("https://github.com/owner/repo/tree/dev/plugins/douban");

        assertTrue(result.error(), result.isSuccess());
        assertEquals(1, result.urls().size());
        assertEquals("子目录不改变下载地址：整包下来之后再按目录挑插件", CANONICAL + "dev", result.urls().get(0));
    }

    @Test
    public void branchNameAllowsDotsAndDashes() {
        PluginRemoteSource.Resolution result =
                PluginRemoteSource.resolve("https://github.com/owner/repo/tree/release-1.2_x");

        assertTrue(result.error(), result.isSuccess());
        assertEquals(CANONICAL + "release-1.2_x", result.urls().get(0));
    }

    @Test
    public void encodedSlashBranchIsRejected() {
        // %2F 解出来是路径分隔符，不可能是一段合法分支名：拒掉而不是把它拼进 codeload 路径。
        PluginRemoteSource.Resolution result =
                PluginRemoteSource.resolve("https://github.com/owner/repo/tree/feature%2Fx");

        assertFalse(result.isSuccess());
        assertNotNull(result.error());
        assertTrue(result.error(), result.error().contains("分支名"));
        assertTrue(result.urls().isEmpty());
    }

    @Test
    public void directZipOnAnyHostIsUsedAsIs() {
        PluginRemoteSource.Resolution result = PluginRemoteSource.resolve("https://example.com/plugins/bundle.zip");

        assertTrue(result.error(), result.isSuccess());
        assertEquals(List.of("https://example.com/plugins/bundle.zip"), result.urls());
    }

    @Test
    public void directZipIsCaseInsensitiveAndKeepsQuery() {
        PluginRemoteSource.Resolution upper = PluginRemoteSource.resolve("https://example.com/BUNDLE.ZIP");
        assertTrue(upper.error(), upper.isSuccess());
        assertEquals(List.of("https://example.com/BUNDLE.ZIP"), upper.urls());

        PluginRemoteSource.Resolution signed = PluginRemoteSource.resolve("https://cdn.example.com/b.zip?token=abc");
        assertTrue(signed.error(), signed.isSuccess());
        assertEquals("直链必须原样返回，签名参数不能被重组丢掉",
                List.of("https://cdn.example.com/b.zip?token=abc"), signed.urls());
    }

    @Test
    public void rejectsPlainHttp() {
        PluginRemoteSource.Resolution result = PluginRemoteSource.resolve("http://github.com/owner/repo");

        assertFalse(result.isSuccess());
        assertNotNull(result.error());
        assertTrue("必须点明只接受 https：" + result.error(), result.error().contains("https"));
    }

    @Test
    public void rejectsNonHttpsSchemes() {
        assertRejected("file:///sdcard/plugin.zip");
        assertRejected("content://media/external/file/1234");
        assertRejected("ftp://example.com/bundle.zip");
    }

    @Test
    public void rejectsEmptyInput() {
        assertRejected("");
        assertRejected("   ");
        assertRejected(null);
    }

    @Test
    public void rejectsGitHubPagesThatAreNotRepoRootOrTree() {
        assertRejected("https://github.com/owner/repo/blob/main/x.zip");
        assertRejected("https://github.com/owner/repo/releases");
        assertRejected("https://github.com/owner/repo/issues");
        assertRejected("https://github.com/owner/repo/archive/refs/heads/main.zip");
        assertRejected("https://github.com/owner");
        assertRejected("https://github.com/owner/repo/tree");
    }

    @Test
    public void rejectsCredentialsInUrl() {
        PluginRemoteSource.Resolution result =
                PluginRemoteSource.resolve("https://user:secret@github.com/owner/repo");

        assertFalse(result.isSuccess());
        assertNotNull(result.error());
        assertFalse("错误信息绝不能回显口令：" + result.error(), result.error().contains("secret"));
        assertFalse(result.error().contains("user"));
    }

    @Test
    public void rejectsNonZipHttpsUrl() {
        assertRejected("https://example.com/plugins/index.html");
        assertRejected("https://example.com/plugins/");
    }

    @Test
    public void rejectsNamesOutsideConservativePattern() {
        assertRejected("https://github.com/ow ner/repo");
        assertRejected("https://github.com/ope%20ner/repo");
        assertRejected("https://github.com/../repo");
    }

    @Test
    public void wwwHostIsTreatedAsGithub() {
        PluginRemoteSource.Resolution result = PluginRemoteSource.resolve("https://www.github.com/owner/repo");

        assertTrue(result.error(), result.isSuccess());
        assertEquals(2, result.urls().size());
        assertEquals(CANONICAL + "main", result.urls().get(0));
    }

    @Test
    public void bundleSizeCapIsStatedInBytes() {
        assertEquals(16L * 1024L * 1024L, PluginRemoteSource.MAX_BUNDLE_BYTES);
    }

    // ---- helpers ----

    private static void assertTwoBranchCandidates(String url) {
        PluginRemoteSource.Resolution result = PluginRemoteSource.resolve(url);

        assertNull(result.error());
        assertTrue(url + " -> " + result.urls(), result.isSuccess());
        assertEquals("仓库根必须给出两个候选：" + result.urls(), 2, result.urls().size());
        assertEquals(CANONICAL + "main", result.urls().get(0));
        assertEquals(CANONICAL + "master", result.urls().get(1));
    }

    private static void assertRejected(String url) {
        PluginRemoteSource.Resolution result = PluginRemoteSource.resolve(url);

        assertFalse("should be rejected: " + url, result.isSuccess());
        assertNotNull("rejection must carry a reason: " + url, result.error());
        assertFalse(result.error().isEmpty());
        assertTrue("rejected input must not yield urls: " + url, result.urls().isEmpty());
    }
}
