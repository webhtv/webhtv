package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Site;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Util;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 锁定桥接解析的缓存隔离：桥接出来的 Site 必须带着 configKey 作为命名空间进 loader 槽位，
 * 否则两条配置里的同名 siteKey 会共用同一个 spider 实例（搜索出别人的结果、播放地址错源）。
 *
 * <p>这里走的是真实的 loader 槽位解析（{@link JarLoader#getSpider(String, String, String, String, String)}），
 * 不是源码文本断言：JarLoader 的构造器不碰 native，能在纯 JVM 单测里直接构造。
 * {@code BaseLoader.get()} 仍然构造不了（会 new PyLoader/JsLoader 拉起 Python 与 QuickJS），
 * 所以「Site 的命名空间确实被送进 loader」这一段由源码契约固定在下面第三个用例里。
 */
public class LoaderNamespaceSlotTest {

    @Test
    public void sameSiteKeyUnderDifferentConfigKeysHitsIndependentLoaderSlots() throws Exception {
        JarLoader loader = new JarLoader();
        ConcurrentHashMap<String, Spider> spiders = map(loader, "spiders");
        String jar = "";

        // 三条配置里的同名 siteKey：命名空间取自真实 Site（桥接时由 VodSiteCatalog 打上 configKey）。
        Site activeSite = site("same", "");
        Site alphaSite = site("same", "cfgAlpha");
        Site betaSite = site("same", "cfgBeta");
        Spider active = new Spider() {};
        Spider alpha = new Spider() {};
        Spider beta = new Spider() {};

        // 三个实例分别占住「激活配置（空命名空间）」与两条桥接配置的槽位。
        spiders.put(Util.md5(jar) + BaseLoader.slot(activeSite.getNamespace(), activeSite.getKey()), active);
        spiders.put(Util.md5(jar) + BaseLoader.slot(alphaSite.getNamespace(), alphaSite.getKey()), alpha);
        spiders.put(Util.md5(jar) + BaseLoader.slot(betaSite.getNamespace(), betaSite.getKey()), beta);

        assertSame("空命名空间必须仍然命中原来的键（激活配置路径逐字节不变）",
                active, loader.getSpider(activeSite.getNamespace(), activeSite.getKey(), "csp_Demo", "", jar));
        assertSame("cfgAlpha 必须命中自己的槽位",
                alpha, loader.getSpider(alphaSite.getNamespace(), alphaSite.getKey(), "csp_Demo", "", jar));
        assertSame("cfgBeta 必须命中自己的槽位",
                beta, loader.getSpider(betaSite.getNamespace(), betaSite.getKey(), "csp_Demo", "", jar));
        assertNotSame("两条配置的同名 siteKey 绝不能拿到同一个实例",
                loader.getSpider(alphaSite.getNamespace(), alphaSite.getKey(), "csp_Demo", "", jar),
                loader.getSpider(betaSite.getNamespace(), betaSite.getKey(), "csp_Demo", "", jar));
        assertNotSame("桥接配置不能与激活配置共用实例",
                loader.getSpider(alphaSite.getNamespace(), alphaSite.getKey(), "csp_Demo", "", jar),
                loader.getSpider(activeSite.getNamespace(), activeSite.getKey(), "csp_Demo", "", jar));
    }

    @Test
    public void siteNamespaceDrivesTheLoaderSlotWhileActiveSiteStaysUnchanged() throws Exception {
        Site active = site("same", "");
        Site alpha = site("same", "cfgAlpha");
        Site beta = site("same", "cfgBeta");

        assertEquals("原生（非桥接）Site 的命名空间必须为空", "", active.getNamespace());
        assertEquals("空命名空间必须落到原键", "same", BaseLoader.slot(active.getNamespace(), active.getKey()));
        assertEquals("桥接 Site 必须落到带 configKey 的键", "cfgAlpha@@@same",
                BaseLoader.slot(alpha.getNamespace(), alpha.getKey()));
        assertNotEquals("两条配置的槽键必须不同",
                BaseLoader.slot(alpha.getNamespace(), alpha.getKey()),
                BaseLoader.slot(beta.getNamespace(), beta.getKey()));
        assertEquals("命名空间只做 trim，不改变 siteKey 语义", "cfgAlpha", site("same", " cfgAlpha ").getNamespace());
        assertEquals("空串命名空间等价于空", "", site("same", "   ").getNamespace());
    }

    @Test
    public void bridgePlumbingPassesTheNamespaceAllTheWayToTheLoader() {
        String site = read("com/fongmi/android/tv/bean/Site.java");
        assertTrue("Site.spider() 必须把命名空间透传给五参 getSpider",
                site.contains("getSpider(getNamespace(), getKey(), getApi(), getExt(), getJar())"));
        assertTrue("命名空间必须是纯运行时标记，不进 Room/Gson/Parcel",
                site.contains("private transient String namespace;"));

        String catalog = read("com/fongmi/android/tv/api/config/VodSiteCatalog.java");
        assertTrue("按 configKey 解析出来的 Catalog 必须以该 configKey 作命名空间",
                catalog.contains("catalogOf(findConfig(id), id)"));
        assertTrue("桥接清单里的每个站源都要打上命名空间（含合并进来的自定义 CSP 站源）",
                catalog.contains("site.setNamespace(namespace)"));
        assertTrue("空命名空间（激活配置）不得打标",
                catalog.contains("if (!isBlank(namespace))"));

        // /vod/api 的两条主路径（proxyKey 判定与 type==3 请求分发）都用 site.spider()，
        // 因此由 Catalog 打的命名空间自动生效，VodApi 不需要额外参数。
        String vodApi = read("com/fongmi/android/tv/server/process/VodApi.java");
        assertFalse("VodApi 不得再自己拼空命名空间调用", vodApi.contains("getSpider(site.getKey()"));
        assertTrue("proxyKey 判定必须走命名空间感知的 spider()", vodApi.contains("supportsDirectProxy(site.spider())"));
        assertTrue("type==3 主路径必须走命名空间感知的 spider()", vodApi.contains("Spider spider = site.spider();"));
    }

    private static Site site(String key, String namespace) {
        Site site = new Site();
        site.setKey(key);
        site.setNamespace(namespace);
        return site;
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Spider> map(JarLoader loader, String name) throws Exception {
        Field field = JarLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Spider>) field.get(loader);
    }

    private static String read(String relative) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(Paths.get("app", "src", "main", "java")).resolve(relative.replace('/', java.io.File.separatorChar));
            if (Files.isRegularFile(candidate)) {
                try {
                    return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new AssertionError("无法读取 " + candidate, e);
                }
            }
        }
        throw new IllegalStateException("app/src/main/java 下找不到 " + relative);
    }
}
