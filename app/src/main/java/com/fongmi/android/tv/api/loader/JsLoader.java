package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final Loader loader;
    private volatile String recent;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        Module.get().clear();
        spiders.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public java.util.Set<String> activeKeys() {
        return java.util.Set.copyOf(spiders.keySet());
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return SpiderInit.load(spiders, key,
                () -> loader.spider(api, BaseLoader.get().dex(jar)), spider -> {
                    spider.siteKey = key;
                    spider.init(App.get(), ext);
                }, e -> {
                    SpiderDebug.log("JsLoader", "JS spider init failed: key=%s api=%s", key, api);
                    SpiderDebug.log("JsLoader", e);
                    e.printStackTrace();
                });
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
