package com.fongmi.android.tv.web;
 
import android.app.Activity;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.WebView;
 
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;
 
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
 
/**
 * 无界面的 WebHome 解析实例。
 *
 * 进程重启后，网页里的 __fmWebHomeInlineResolver 随进程消失，之前只能标记 needsParse
 * 交给系统嗅探兜底。其实站点脚本、扩展都还在，缺的只是一个「加载网页的 WebView」：
 * 这里在播放器 Activity 上挂一个 1x1 的隐藏 WebView，复用 HomeWebController 的完整解析链路
 * （A/A2 HTML 抓取 -> 真实加载 -> 停播），拿到的直链和网页里点剧集是同一套结果。
 */
public final class WebHomeHiddenResolver {
 
    private static final Object LOCK = new Object();
    // 只在冷启动链路用，同一时间只允许一个隐藏实例，避免多个 WebView 抢音频焦点
    private static volatile boolean running;
 
    private static final HomeWebController.Listener STUB = new HomeWebController.Listener() {
        @Override
        public void onWebLoading() {
        }
 
        @Override
        public void onWebReady() {
        }
 
        @Override
        public void onWebError() {
        }
    };
 
    /**
     * 同步解析一个剧集：成功返回 {url, headers, format}，失败返回 null（调用方退回系统嗅探）。
     * 必须在后台线程调用。
     */
    public static JsonObject resolve(JsonObject payload) {
        synchronized (LOCK) {
            if (running) return null;
            running = true;
        }
        long start = System.currentTimeMillis();
        HomeWebController controller = null;
        try {
            Activity activity = App.activity();
            String siteKey = Json.safeString(payload, "siteKey");
            // 有站点 key 就用它，没有就退回当前首页源（WebHome 站点）
            Site selected = TextUtils.isEmpty(siteKey) ? VodConfig.get().getHome() : VodConfig.get().getSite(siteKey);
            if (selected == null) selected = VodConfig.get().getHome();
            final Site site = selected;
            String pageUrl = first(Json.safeString(payload, "pageUrl"), Json.safeString(payload, "href"), Json.safeString(payload, "url"));
            if (activity == null || site == null || !site.hasHomePage() || pageUrl.isEmpty()) {
                SpiderDebug.log("webhome-inline", "hidden resolve skip activity=%s site=%s page=%s", activity != null, site == null ? "" : site.getKey(), pageUrl);
                return null;
            }
            CompletableFuture<HomeWebController> setup = new CompletableFuture<>();
            App.post(() -> {
                try {
                    WebView webView = new WebView(activity);
                    HomeWebController created = new HomeWebController(activity, webView, STUB);
                    // 1x1 挂在播放器 Activity 上：太小看不见，但能正常加载和执行脚本；
                    // 关掉焦点能力，避免抢走播放器遥控器焦点
                    webView.setFocusable(false);
                    webView.setFocusableInTouchMode(false);
                    activity.addContentView(webView, new ViewGroup.LayoutParams(1, 1));
                    created.loadHidden(site, pageUrl);
                    setup.complete(created);
                } catch (Throwable e) {
                    setup.completeExceptionally(e);
                }
            });
            controller = setup.get(10, TimeUnit.SECONDS);
            SpiderDebug.log("webhome-inline", "hidden resolve start site=%s page=%s", site.getKey(), pageUrl);
            JsonObject object = controller.resolveEpisode(payload == null ? new JsonObject() : payload.deepCopy());
            SpiderDebug.log("webhome-inline", "hidden resolve ok cost=%sms url=%s", System.currentTimeMillis() - start, Json.safeString(object, "url"));
            return object;
        } catch (Throwable e) {
            SpiderDebug.log("webhome-inline", "hidden resolve failed cost=%sms error=%s", System.currentTimeMillis() - start, e.getMessage());
            return null;
        } finally {
            if (controller != null) {
                HomeWebController target = controller;
                App.post(target::destroyHidden);
            }
            running = false;
        }
    }
 
    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }
}
