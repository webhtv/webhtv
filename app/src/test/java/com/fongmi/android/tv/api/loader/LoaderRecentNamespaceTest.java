package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.github.catvod.crawler.Spider;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ?do=js} / {@code ?do=py} 子路由的 **真实行为** 覆盖。
 *
 * <p>这两个子路由的请求由站源脚本自己生成（quickjs 的 {@code ?do=js}、chaquo 的 {@code ?do=py}
 * 都不带 siteKey/configKey），消费端 {@code BaseLoader.proxy} 只能靠 loader 的 {@code recent}
 * 定位 spider。{@code spiders} 表按 {@link BaseLoader#slot(String, String)} 建键，所以 recent
 * 也必须是槽位键；否则桥接配置（非激活配置）的脚本请求要么查不到自己（proxy 返回 null →
 * HTTP INVALID_RESPONSE），要么命中激活配置里同名站源的实例（B2 同类串源）。
 *
 * <p>这里断言的是真实代码路径，不是源码字符串：预置的 fake Spider 放进真实 {@code spiders} 表，
 * 调用真实的 {@code setRecent(namespace, key)} 与真实的 {@code proxy(params)}，断言取回的正是
 * 该 fake。{@code JsLoader}/{@code PyLoader} 的构造器会初始化 QuickJS / 启动 Python，纯 JVM 单测
 * 不能执行它，所以实例用 {@code sun.misc.Unsafe.allocateInstance} 分配（不跑构造器），
 * 只有 {@code spiders} 表与 {@code recent} 两个字段是测试写入的，被测代码本身没有任何替身。
 */
public class LoaderRecentNamespaceTest {

    @Test
    public void jsProxyResolvesRecentToTheNamespacedSlot() throws Exception {
        JsLoader loader = allocate(JsLoader.class);
        ConcurrentHashMap<String, Spider> spiders = seedSpiders(loader, JsLoader.class);

        spiders.put(BaseLoader.slot("", "same"), fake("active"));
        spiders.put(BaseLoader.slot("cfgAlpha", "same"), fake("alpha"));
        spiders.put(BaseLoader.slot("cfgBeta", "same"), fake("beta"));

        // 桥接配置 cfgAlpha 的 ?do=js：必须命中自己的槽位。
        loader.setRecent("cfgAlpha", "same");
        assertEquals("桥接配置的 ?do=js 必须命中自己的槽位，不能命中激活配置的同名站源", "alpha", proxy(loader));
        // 桥接配置 cfgBeta 的 ?do=js：必须命中自己的槽位。
        loader.setRecent("cfgBeta", "same");
        assertEquals("不同命名空间下的同名 siteKey 必须互不干扰", "beta", proxy(loader));
        // 激活配置（空命名空间）走单参 setRecent：与改动前逐字节等价。
        loader.setRecent("same");
        assertEquals("空命名空间的 recent 必须仍是裸 siteKey（激活配置路径逐字节不变）", "active", proxy(loader));
        loader.setRecent(null, "same");
        assertEquals("null 命名空间等价于空命名空间", "active", proxy(loader));
    }

    @Test
    public void pyProxyResolvesRecentToTheNamespacedSlot() throws Exception {
        PyLoader loader = allocate(PyLoader.class);
        ConcurrentHashMap<String, Spider> spiders = seedSpiders(loader, PyLoader.class);

        spiders.put(BaseLoader.slot("", "same"), fake("active"));
        spiders.put(BaseLoader.slot("cfgAlpha", "same"), fake("alpha"));
        spiders.put(BaseLoader.slot("cfgBeta", "same"), fake("beta"));

        loader.setRecent("cfgAlpha", "same");
        assertEquals("桥接配置的 ?do=py 必须命中自己的槽位，不能命中激活配置的同名站源", "alpha", proxy(loader));
        loader.setRecent("cfgBeta", "same");
        assertEquals("不同命名空间下的同名 siteKey 必须互不干扰", "beta", proxy(loader));
        loader.setRecent("same");
        assertEquals("空命名空间的 recent 必须仍是裸 siteKey（激活配置路径逐字节不变）", "active", proxy(loader));
        loader.setRecent(null, "same");
        assertEquals("null 命名空间等价于空命名空间", "active", proxy(loader));
    }

    /**
     * 反证用例：recent 若退回裸 siteKey，本用例必然变红。
     *
     * <p>只预置激活配置（裸键）的 fake，然后把 recent 指向另一条配置的同名站源：
     * 槽位键正确时 {@code spiders.get("cfgBeta@@@same")} 落空 → proxy 返回 null；
     * 一旦最近记录退回裸键 {@code "same"}，就会命中激活配置的 fake 并返回非 null，
     * 即「用激活配置的 spider 服务非激活配置的请求」这一串源现场。
     */
    @Test
    public void jsProxyReturnsNullWhenRecentBelongsToAnotherNamespace() throws Exception {
        JsLoader loader = allocate(JsLoader.class);
        ConcurrentHashMap<String, Spider> spiders = seedSpiders(loader, JsLoader.class);

        spiders.put(BaseLoader.slot("", "same"), fake("active"));
        loader.setRecent("cfgBeta", "same");

        assertNull("recent 属于另一条配置时必须落空；命中激活配置的实例即为串源", loader.proxy(params()));
        loader.setRecent("same");
        assertNotNull("同一张表的裸键必须仍然可取（对照，证明上面的 null 不是表本身空）", loader.proxy(params()));
    }

    @Test
    public void pyProxyReturnsNullWhenRecentBelongsToAnotherNamespace() throws Exception {
        PyLoader loader = allocate(PyLoader.class);
        ConcurrentHashMap<String, Spider> spiders = seedSpiders(loader, PyLoader.class);

        spiders.put(BaseLoader.slot("", "same"), fake("active"));
        loader.setRecent("cfgBeta", "same");

        assertNull("recent 属于另一条配置时必须落空；命中激活配置的实例即为串源", loader.proxy(params()));
        loader.setRecent("same");
        assertNotNull("同一张表的裸键必须仍然可取（对照，证明上面的 null 不是表本身空）", loader.proxy(params()));
    }

    private static Map<String, String> params() {
        Map<String, String> params = new HashMap<>();
        params.put("do", "js");
        params.put("url", "https://example.invalid/v");
        return params;
    }

    private static Spider fake(String tag) {
        return new Spider() {
            @Override
            public Object[] proxy(Map<String, String> params) {
                return new Object[]{tag, params.get("url")};
            }
        };
    }

    private static Object proxy(Object loader) throws Exception {
        Map<String, String> params = params();
        if (loader instanceof JsLoader) return ((JsLoader) loader).proxy(params)[0];
        return ((PyLoader) loader).proxy(params)[0];
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Spider> seedSpiders(Object loader, Class<?> type) throws Exception {
        ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
        set(type, loader, "spiders", spiders);
        return spiders;
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 分配实例但不执行构造器：{@code JsLoader}/{@code PyLoader} 的构造器会
     * {@code QuickJSLoader.init()} / {@code Python.start(...)}，在纯 JVM 单测里必然失败。
     * 用反射按名取 {@code sun.misc.Unsafe}，避免编译期依赖 JDK 内部类型。
     */
    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return (T) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }
}
