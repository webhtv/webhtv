package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private static final String PY_TAG = "python-spider";

    private final ConcurrentHashMap<String, Spider> spiders;
    private final Loader loader;
    private volatile String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        try {
            SpiderCleanup.destroy("py-loader", spiders);
        } finally {
            spiders.clear();
            recent = null;
        }
    }

    public void setRecent(String recent) {
        setRecent("", recent);
    }

    /**
     * 与 {@link JsLoader#setRecent(String, String)} 同构：spiders 按
     * {@link BaseLoader#slot(String, String)} 建键，proxy() 又用 recent 查同一张表，
     * 所以 recent 也必须是槽位键，否则桥接配置的 ?do=py 查不到自己或命中激活配置的同名站源。
     */
    public void setRecent(String namespace, String key) {
        this.recent = BaseLoader.slot(namespace, key);
    }

    /** 缓存键可能带命名空间前缀；对外的"活动插件"仍按脚本自己的 key 报告。 */
    public java.util.Set<String> activeKeys() {
        return com.fongmi.android.tv.api.loader.BaseLoader.baseKeys(spiders.keySet());
    }

    public Spider getSpider(String key, String api, String ext) {
        return getSpider("", key, api, ext);
    }

    /**
     * 按 (命名空间, siteKey) 取 spider：桥接解析非激活配置时不能写进激活配置的槽位，
     * 否则 A、B 两个配置的同名站点会互相串源。空命名空间等于原来的行为。
     */
    public Spider getSpider(String namespace, String key, String api, String ext) {
        return spiders.computeIfAbsent(BaseLoader.slot(namespace, key), k -> {
            try {
                Spider spider = loader.spider(api);
                spider.siteKey = key;
                spider.init(App.get(), normalizeExt(ext));
                return spider;
            } catch (Throwable e) {
                // A failed bridge init is otherwise hard to attribute: SpiderNull then
                // returns "" for every method, so home/category/search just render empty.
                // Record it under the python-spider tag (already routed to the NETWORK
                // category) and name the callable that failed, because the stack alone
                // does not tell whether it was init(), getDependence() or the import.
                SpiderDebug.log(PY_TAG, "init failed key=%s api=%s error=%s:%s", key, api, e.getClass().getName(), e.getMessage());
                SpiderDebug.log(PY_TAG, e);
                // SpiderDebug.log() is gated on DebugLogStore.acceptsTag(), which is false
                // whenever 调试日志 is off (the default).  Relying on it alone would make a
                // failed init invisible in Logcat even though this catch is the only place
                // that explains an all-empty site.  Keep the unconditional channel too, as
                // the sibling JsLoader does for its own init failure.
                e.printStackTrace();
                return new SpiderNull();
            }
        });
    }

    private String normalizeExt(String ext) {
        String value = TextUtils.isEmpty(ext) ? "" : ext.trim();
        // Many live Python spiders treat ext as an option object and call .get().
        // TV-style configs commonly express an empty extension as [], which would
        // otherwise deserialize to a list and crash those spiders during init.
        return "[]".equals(value) ? "{}" : ext;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
