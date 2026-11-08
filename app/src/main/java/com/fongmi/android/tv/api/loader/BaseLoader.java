package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.VodSiteCatalog;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final JarLoader jarLoader;
    private final PyLoader pyLoader;
    private final JsLoader jsLoader;
    private final java.util.concurrent.ConcurrentHashMap<String, Spider> catSpiders;
    private final LoaderClearQueue clearQueue;

    private BaseLoader() {
        jarLoader = new JarLoader();
        pyLoader = new PyLoader();
        jsLoader = new JsLoader();
        catSpiders = new java.util.concurrent.ConcurrentHashMap<>();
        clearQueue = new LoaderClearQueue(Task.loaderExecutor());
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    /** 缓存槽键：空命名空间必须与原键逐字节相同——激活配置走的正是空命名空间。 */
    public static String slot(String namespace, String key) {
        return TextUtils.isEmpty(namespace) ? key : namespace + "@@@" + key;
    }

    /** 去掉缓存槽的命名空间前缀：插件脚本文件名只认脚本自己的 key。 */
    public static java.util.Set<String> baseKeys(java.util.Collection<String> slots) {
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        for (String slot : slots) {
            if (TextUtils.isEmpty(slot)) continue;
            int mark = slot.indexOf("@@@");
            keys.add(mark < 0 ? slot : slot.substring(mark + 3));
        }
        return java.util.Set.copyOf(keys);
    }

    private static boolean isJs(String api) {
        return api.contains(".js");
    }

    private static boolean isPy(String api) {
        return api.contains(".py");
    }

    private static boolean isCsp(String api) {
        return api.startsWith("csp_");
    }

    public Future<?> clear() {
        return clear("unknown");
    }

    public Future<?> clear(String reason) {
        String source = reason == null || reason.isEmpty() ? "unknown" : reason;
        SpiderDebug.log("base-loader", "clear requested reason=%s", source);
        LoaderClearQueue.Entry entry = clearQueue.submit(source, id -> clear(id, source));
        return entry.future();
    }

    private void clear(int id, String reason) {
        SpiderDebug.log("base-loader", "clear start id=%d reason=%s", id, reason);
        clear("jar", jarLoader::clear);
        clear("py", pyLoader::clear);
        clear("js", jsLoader::clear);
        clear("cat", catSpiders::clear);
        SpiderDebug.log("base-loader", "clear done id=%d reason=%s", id, reason);
    }

    public void awaitClear() throws InterruptedException {
        if (clearQueue.isRunning()) return;
        LoaderClearQueue.Entry entry = clearQueue.latest();
        if (entry == null) return;
        Future<?> future = entry.future();
        int id = entry.id();
        String reason = entry.reason();
        boolean waiting = !future.isDone();
        long start = System.nanoTime();
        if (waiting) SpiderDebug.log("base-loader", "clear wait start id=%d reason=%s", id, reason);
        try {
            future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            SpiderDebug.log("base-loader", "clear wait failed id=%d reason=%s error=%s:%s", id, reason, cause.getClass().getSimpleName(), cause.getMessage());
            SpiderDebug.log("base-loader", cause);
        } finally {
            if (waiting) SpiderDebug.log("base-loader", "clear wait done id=%d reason=%s elapsedMs=%d", id, reason, (System.nanoTime() - start) / 1_000_000L);
        }
    }

    private void clear(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            SpiderDebug.log("base-loader", "clear failed loader=%s error=%s:%s", name, e.getClass().getSimpleName(), e.getMessage());
            SpiderDebug.log("base-loader", e);
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return getSpider("", key, api, ext, jar);
    }

    /**
     * 同上，但把 spider 落进 {@code namespace} 的独立缓存槽。
     *
     * <p>四种 loader 的缓存原本只按 siteKey 建键：A、B 两个配置都有 siteKey=豆瓣 时，
     * 先解析 B 的豆瓣会把 B 的实例写进 A 的槽位，之后激活配置 A 的豆瓣一直被喂 B 的
     * spider，直到下一次 VodConfig.clear()。桥接路径因此按 (命名空间, siteKey) 建键，
     * 激活配置路径（空命名空间）的键保持原样。
     */
    public Spider getSpider(String namespace, String key, String api, String ext, String jar) {
        String slot = slot(namespace, key);
        // 猫源站点 type 也是 3，但 api 指向本机 bundle 的 HTTP 路由，得走协议适配而非本地引擎
        if (CatSpider.matches(api)) return catSpiders.computeIfAbsent(slot, k -> new CatSpider(api));
        if (isPy(api)) return pyLoader.getSpider(namespace, key, api, ext);
        else if (isJs(api)) return jsLoader.getSpider(namespace, key, api, ext, jar);
        else if (isCsp(api)) return jarLoader.getSpider(namespace, key, api, ext, jar);
        else return new SpiderNull();
    }

    public Spider getSpider(String key) {
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        return new SpiderNull();
    }

    /** 激活配置（不含命名空间）的 recent：与改动前的裸 siteKey / jar md5 完全一致。 */
    public void setRecent(String key, String api, String jar) {
        setRecent("", key, api, jar);
    }

    /**
     * 桥接配置的 recent 必须与槽位键同形，否则 {@code ?do=js} / {@code ?do=py} 子路由会串源。
     *
     * <p>js/py 的 {@code spiders} 表按 {@link #slot(String, String)} 建键，而
     * {@code JsLoader.proxy}/{@code PyLoader.proxy} 又拿 recent 去查同一张表；而这两条子路由的
     * 请求由站源脚本自己生成（quickjs 的 {@code ?do=js}、chaquo 的 {@code ?do=py} 都不带
     * siteKey/configKey），消费端只能靠 recent 定位。recent 若仍是裸 siteKey，桥接配置的请求
     * 要么查不到自己（proxy 返回 null → HTTP INVALID_RESPONSE），要么命中激活配置里同名站源的
     * 实例（与 B2 同类的串源）。jar 的 recent 是 jar md5，{@code loaders}/{@code methods} 也只按
     * md5 建键，本来就与 siteKey 无关，因此这里保持裸 md5。
     */
    public void setRecent(String namespace, String key, String api, String jar) {
        if (isJs(api)) jsLoader.setRecent(namespace, key);
        else if (isPy(api)) pyLoader.setRecent(namespace, key);
        else if (isCsp(api)) jarLoader.setRecent(Util.md5(jar));
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (params.containsKey("siteKey")) {
            // 显式带了 configKey 就必须解析到那条配置：解析不出来时抛错，绝不回落到激活配置，
            // 否则请求写的是 B、响应却来自 A。未带 configKey 时保持原有的激活配置语义。
            if (!TextUtils.isEmpty(params.get("configKey"))) return catalogSpider(params.get("configKey"), params.get("siteKey")).proxy(params);
            return getSpider(params.get("siteKey")).proxy(params);
        }
        if ("js".equals(params.get("do"))) return jsLoader.proxy(params);
        if ("py".equals(params.get("do"))) return pyLoader.proxy(params);
        return jarLoader.proxy(params);
    }

    /**
     * 非激活配置的代理站源。/proxy 带着 configKey 时用它定位站点。
     *
     * <p>解析失败一律向外抛：Proxy.doResponse 的 catch 会把它转成带原文的 500 响应，
     * 客户端因此能看到「未知的配置标识」这类可区分的信息，而不是一个静默的空结果。
     */
    private Spider catalogSpider(String configKey, String siteKey) throws Exception {
        if (TextUtils.isEmpty(siteKey)) throw new IllegalArgumentException("缺少站点 key");
        Site site = VodSiteCatalog.database().load(configKey).find(siteKey);
        if (site.isEmpty()) throw new IllegalArgumentException("配置 " + configKey + " 里没有站点: " + siteKey);
        SpiderDebug.log("base-loader", "catalog proxy configKey=%s siteKey=%s", configKey, siteKey);
        return getSpider(VodSiteCatalog.namespace(configKey), site.getKey(), site.getApi(), site.getExt(), site.getJar());
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        String key = Util.md5(jar);
        jarLoader.parseJar(key, jar);
        if (recent) jarLoader.setRecent(key);
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public java.util.Set<String> activePluginKeys() {
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        keys.addAll(jarLoader.activeKeys());
        keys.addAll(jsLoader.activeKeys());
        keys.addAll(pyLoader.activeKeys());
        return java.util.Set.copyOf(keys);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }
}
