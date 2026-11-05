package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.ui.activity.AppBrandingActivity;
import com.github.catvod.utils.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 面板槽位与剧集上下文注入的**真机**测试：面板真的弹出来、WebView 真的渲染出插件返回的 html、
 * 动作 payload 里真的带上了 {@code context}，以及缺 {@code plugin.data.episode} 权限时真的不带。
 *
 * <p><b>为什么必须在设备上跑。</b>面板链路后半段全是 Android 运行时：
 * {@code PluginController.showPanel} 要投递到主线程 Handler、{@code PluginPanelDialog} 要经
 * {@code FragmentManager} 提交事务、{@code SideSheetDialog} 要真正的窗口、WebView 要有渲染进程。
 * JVM 单测只能覆盖到"payload 里有没有 context"这一层，覆盖不到"用户是否真的看见评论"。
 *
 * <p><b>断言为什么读 WebView 的 DOM 而不是读返回值。</b>插件动作的返回值在
 * {@code PluginController} 内部就被路由掉了，测试拿不到。而 DOM 里的文本正是用户看到的文本——
 * 只要 {@code document.getElementById(...).textContent} 等于期望值，就证明
 * 「payload → 脚本 → html → WebView」整条链路都通，而不是仅仅"某一环没抛异常"。
 *
 * <p>宿主复用已注册的 {@link AppBrandingActivity}：新增 Manifest 条目会扩大测试对生产配置的
 * 侵入面，而面板与上下文注入对宿主页面类型并无要求（只要它是 {@code FragmentActivity}）。
 */
@RunWith(AndroidJUnit4.class)
public class PluginPanelAndContextDeviceTest {

    private static final String ID = "panel.probe";
    private static final String PLUGIN_VERSION = "panel-1";
    private static final String ACTION = "probe.panel";
    private static final String SLOT_LABEL = "ProbePanel";
    private static final String PANEL_TAG = "plugin-panel";
    private static final String TITLE = "PROBE42";
    private static final String YEAR = "2019";
    private static final String NO_CONTEXT = "NO_CONTEXT";
    private static final long WAIT_MS = 3_000L;
    /** WebView 首次创建要起渲染进程，冷启动那一次比后续几次慢得多，单给 DOM 一个更宽的窗口。 */
    private static final long DOM_WAIT_MS = 20_000L;
    /** 等 {@code document.readyState} 变成 complete 的上限：加载本身可能比读一次 DOM 慢得多。 */
    private static final long READY_WAIT_MS = 20_000L;
    /**
     * 读 DOM 的轮询间隔，**只给 {@code awaitWebText} 用**。
     *
     * <p>不能沿用 {@link #sleep()} 的 50ms：那个间隔是查「视图出现没有」，代价近乎零；
     * 而这里每一轮都要往 WebView 里发一次 {@code evaluateJavascript}，50ms 一轮意味着
     * 20 秒窗口能堆四百次 eval，页面还没就绪时足以把渲染进程压住。
     */
    private static final long TEXT_POLL_MS = 250L;

    private static final String MANIFEST = """
            {
              "protocol": "webhtv-plugin",
              "version": 1,
              "id": "%s",
              "name": "Panel Probe",
              "pluginVersion": "%s",
              "sdk": 1,
              "runtime": { "api": "js", "entry": "main.js", "timeout": 15 },
              "events": ["ui.contentShown"],
              "permissions": %s,
              "ui": [ { "slot": "panel", "label": "%s", "action": "%s" } ]
            }
            """;

    /** 只证明"面板里显示的就是插件给的那段 html"。 */
    private static final String SCRIPT_MARK = """
            webhtv.action('probe.panel', function (payload) {
              return '<html><body><div id="probe-title">PANELMARK</div></body></html>';
            });
            """;

    /**
     * 把 payload 里的 activity 与 context 字段**逐字搬进 DOM**，让断言变成精确值比对。
     *
     * <p>{@code payload} 是 JSON **字符串**而不是对象：{@code QuickJsPluginRuntime.dispatch} 直接把
     * 宿主拼好的串交给 JS，解析由插件自己做（README §5.1 的同一条约定，example/hello.js 也这么写）。
     * 这里必须自己 {@code JSON.parse}，否则 {@code payload.context} 恒为 undefined。
     */
    private static final String SCRIPT_CONTEXT = """
            webhtv.action('probe.panel', function (raw) {
              var payload = {};
              try { payload = JSON.parse(raw || '{}'); } catch (e) {}
              var ctx = (payload && payload.context) || {};
              var act = (payload && payload.activity) || 'NO_ACTIVITY';
              var t = ctx.title || 'NO_CONTEXT';
              var y = ctx.year || 'NO_CONTEXT';
              return '<html><body>'
                + '<div id="probe-title">' + t + '</div>'
                + '<div id="probe-year">' + y + '</div>'
                + '<div id="probe-activity">' + act + '</div>'
                + '</body></html>';
            });
            """;

    private ActivityScenario<AppBrandingActivity> scenario;
    private AppBrandingActivity activity;
    private String activityName;
    private File sourceDir;

    @Before
    public void setUp() {
        PluginController.reset();
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        sourceDir = new File(Path.cache(), "panel-plugin-src");
        deleteQuietly(sourceDir);
        assertTrue(sourceDir.mkdirs());

        scenario = ActivityScenario.launch(AppBrandingActivity.class);
        scenario.onActivity(host -> {
            activity = host;
            activityName = host.getClass().getSimpleName();
        });
        // 宿主 Activity 简名是上下文的键：插件在别的页面上拿到的必须是 null，所以这条先钉死。
        assertEquals("AppBrandingActivity", activityName);
    }

    @After
    public void tearDown() {
        dismissPanelQuietly();
        PluginEpisodeContext.clear(activityName);
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        reloadOnMain();
        PluginController.reset();
        if (scenario != null) scenario.close();
        deleteQuietly(sourceDir);
    }

    /** 面板渲染：插件返回的 html 真的进了 WebView，且标题来自槽位声明而不是宿主兜底文案。 */
    @Test
    public void panelSlotOpensSheetWithPluginHtml() {
        install("[\"plugin.events\",\"ui.panel\"]", SCRIPT_MARK);
        resumeHost();

        PluginController.get().dispatchSlotAction(ID, ACTION);

        Fragment panel = awaitPanel();
        assertNotNull("panel slot must open a PluginPanelDialog", panel);
        assertEquals(SLOT_LABEL, awaitViewText(R.id.pluginPanelTitle));

        WebView web = awaitWebView();
        assertNotNull("panel must host a WebView", web);
        assertEquals("plugin html must be rendered by the panel WebView", "PANELMARK", awaitWebText(web, "probe-title"));
    }

    /** 有 plugin.data.episode 权限：剧集字段真的进了 payload，并被脚本读出来渲染到面板上。 */
    @Test
    public void actionPayloadCarriesEpisodeContextWhenPermissionGranted() {
        install("[\"plugin.events\",\"ui.panel\",\"plugin.data.episode\"]", SCRIPT_CONTEXT);
        resumeHost();
        publishContext();

        PluginController.get().dispatchSlotAction(ID, ACTION);

        WebView web = awaitWebView();
        assertNotNull("panel must host a WebView", web);
        assertEquals("context.title must reach the script", TITLE, awaitWebText(web, "probe-title"));
        assertEquals("context.year must reach the script", YEAR, awaitWebText(web, "probe-year"));
        // 这一条同时证明 payload 本身存在：没有 payload 就取不到 activity，只会渲染 NO_ACTIVITY。
        assertEquals("payload.activity must name the host page", activityName, awaitWebText(web, "probe-activity"));
    }

    /** 未授予 plugin.data.episode：payload 照旧送达（activity 在），但 context 必须缺席而不是错位数据。 */
    @Test
    public void actionPayloadOmitsEpisodeContextWithoutPermission() {
        install("[\"plugin.events\",\"ui.panel\"]", SCRIPT_CONTEXT);
        resumeHost();
        publishContext();

        PluginController.get().dispatchSlotAction(ID, ACTION);

        WebView web = awaitWebView();
        assertNotNull("panel must host a WebView", web);
        // 锚点先立：payload 确实到达了脚本，否则下面的"没有 context"毫无诊断力。
        assertEquals("payload.activity must still be delivered", activityName, awaitWebText(web, "probe-activity"));
        assertEquals("context must be absent without plugin.data.episode", NO_CONTEXT, awaitWebText(web, "probe-title"));
        assertEquals("context must be absent without plugin.data.episode", NO_CONTEXT, awaitWebText(web, "probe-year"));
    }

    private void install(String permissions, String script) {
        write(new File(sourceDir, "manifest.json"),
                String.format(Locale.US, MANIFEST, ID, PLUGIN_VERSION, permissions, SLOT_LABEL, ACTION));
        write(new File(sourceDir, "main.js"), script);
        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(sourceDir);
        assertTrue("import failed: " + result.error(), result.isSuccess());
        PluginBootstrap.install();
        assertTrue("reload should load the probe plugin", reloadOnMain());
        PluginHost host = PluginController.get().host();
        assertTrue("plugin must be loaded", host.isLoaded(ID));
        assertNotNull("panel slot must survive manifest parsing", host.slotOfAction(ID, ACTION));
    }

    /**
     * 装载必须在主线程发起：{@code PluginController.reload} 会顺手把槽位控件挂到当前页的 decorView 上，
     * 而 View 只能在建树线程上改动。在测试线程直接调用会被 reload 内部的 catch 吞掉异常，
     * 表现为"插件已 ready 但 reload 返回空列表"——这正是本测试首版失败的真正原因。
     */
    private static boolean reloadOnMain() {
        AtomicBoolean loaded = new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> loaded.set(PluginBootstrap.reloadAll()));
        return loaded.get();
    }

    /** 宿主页面必须是控制器眼里的"当前页"，否则 showPanel 会按防串台规则直接放弃。 */
    private void resumeHost() {
        scenario.onActivity(host -> PluginController.safeOnResumed(host));
    }

    private void publishContext() {
        TmdbItem item = new TmdbItem(4242, "tv", TITLE, "", "", "", "");
        Vod vod = new Vod();
        vod.setId("probe-vod");
        vod.setName(TITLE);
        vod.setYear(YEAR);
        PluginEpisodeContext.publish(activityName, PluginEpisodePayload.of(item, vod, null));
    }

    private Fragment awaitPanel() {
        return poll(() -> {
            AppBrandingActivity host = activity;
            if (host == null || host.isFinishing()) return null;
            return host.getSupportFragmentManager().findFragmentByTag(PANEL_TAG);
        }, WAIT_MS);
    }

    private WebView awaitWebView() {
        return poll(() -> {
            Fragment panel = awaitPanelNow();
            if (panel == null) return null;
            View root = panel.getView();
            if (root == null) return null;
            View found = root.findViewById(R.id.pluginPanelWeb);
            return found instanceof WebView ? (WebView) found : null;
        }, WAIT_MS);
    }

    private String awaitViewText(int viewId) {
        String text = poll(() -> {
            Fragment panel = awaitPanelNow();
            if (panel == null) return null;
            View root = panel.getView();
            if (root == null) return null;
            View found = root.findViewById(viewId);
            return found instanceof TextView ? ((TextView) found).getText().toString() : null;
        }, WAIT_MS);
        assertNotNull("view " + viewId + " must exist in the panel", text);
        return text;
    }

    private Fragment awaitPanelNow() {
        AppBrandingActivity host = activity;
        if (host == null || host.isFinishing()) return null;
        return host.getSupportFragmentManager().findFragmentByTag(PANEL_TAG);
    }

    /**
     * 读 DOM 文本；一次读不到就重开面板再读一次，仍读不到才返回诊断串。
     *
     * <p><b>为什么要重开一次。</b>这条链路偶发过一次性失败：{@code evaluateJavascript} 的回调
     * 再也不回来（{@code latch.await} 直接超时，诊断串里 html=null），随后单独重跑同一批用例又全过。
     * 那是 WebView 渲染进程被压住，而不是插件或 payload 出错——此时断言失败会把「渲染进程卡了一次」
     * 说成「context 没送到脚本」，指向完全错误的方向。用户遇到同样情形会关掉面板再点一次，
     * 重开一次正是更高保真的复现路径。
     */
    private String awaitWebText(WebView web, String elementId) {
        String value = readWebText(liveWeb(web), elementId);
        if (value != null) return value;
        WebView reopened = retryDispatch();
        if (reopened != null) {
            web = reopened;
            value = readWebText(web, elementId);
            if (value != null) return value;
        }
        // 超时不能只报 null：那既可能是"页面没加载"，也可能是"元素 id 不对"，
        // 两种原因的修法完全不同，所以把页面真实内容一起带进断言消息。
        return "TIMEOUT html=" + evaluate(web, "document.documentElement.outerHTML")
                + " audit=" + PluginAuditLog.get().entriesFor(ID)
                + " url=" + evaluate(web, "document.URL");
    }

    /**
     * 取当前面板里**活着的**那个 WebView。
     *
     * <p>重开面板后调用方手里那个 {@code web} 已经随旧面板销毁，再往上发
     * {@code evaluateJavascript} 只会一直等到超时——那正是本次要消除的慢路径。
     * 所以每一轮都先按面板当前实例重新取一次，取不到才退回入参。
     */
    private WebView liveWeb(WebView fallback) {
        WebView current = awaitWebView();
        return current != null ? current : fallback;
    }

    /** 等到页面 {@code readyState=complete} 之后再逐次读文本；没就绪就返回 null，交给上层重开。 */
    private String readWebText(WebView web, String elementId) {
        if (!awaitDocumentReady(web)) return null;
        String expression = "document.getElementById('" + elementId + "').textContent";
        long deadline = SystemClock.uptimeMillis() + DOM_WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            String value = evaluate(web, expression);
            if (value != null) return value;
            sleepTextPoll();
        }
        return null;
    }

    /**
     * 等 {@code document.readyState} 变成 complete。
     *
     * <p>加载中就往 WebView 里灌 {@code evaluateJavascript} 会挤住渲染进程——这一条是本次加固的
     * 核心：先确认页面就绪，再把轮询压到 {@link #TEXT_POLL_MS} 一次。
     */
    private static boolean awaitDocumentReady(WebView web) {
        long deadline = SystemClock.uptimeMillis() + READY_WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if ("complete".equals(evaluate(web, "document.readyState"))) return true;
            sleepTextPoll();
        }
        return false;
    }

    /**
     * 关掉卡住的面板再重新触发一次点击，返回新面板的 WebView。
     *
     * <p>必须先 dismiss 再重开：{@code PluginPanelDialog.show} 用固定 TAG 提交事务，旧实例还在时
     * 再 add 同一个 tag 会撞在 FragmentManager 上。等旧 fragment 真的消失，新面板才拿得到 tag。
     */
    private WebView retryDispatch() {
        dismissPanelQuietly();
        poll(() -> awaitPanelNow() == null ? Boolean.TRUE : null, WAIT_MS);
        PluginController.get().dispatchSlotAction(ID, ACTION);
        return awaitWebView();
    }

    private static String evaluate(WebView web, String expression) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> box = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                web.evaluateJavascript(expression, value -> {
                    box.set(value);
                    latch.countDown();
                });
            } catch (Throwable error) {
                latch.countDown();
            }
        });
        try {
            latch.await(WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        return decode(box.get());
    }

    /** evaluateJavascript 回传的是 JSON：字符串带引号，元素不存在时是字面量 null。 */
    private static String decode(String raw) {
        if (raw == null || "null".equals(raw)) return null;
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static <T> T poll(Callable<T> probe, long timeoutMs) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final AtomicReference<T> box = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            box.set(null);
            instrumentation.runOnMainSync(() -> {
                try {
                    box.set(probe.call());
                } catch (Throwable ignored) {
                }
            });
            T value = box.get();
            if (value != null) return value;
            sleep();
        }
        return null;
    }

    /** 查视图是否出现的轮询间隔：不碰 WebView，50ms 就够。 */
    private static void sleep() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    /** 读 DOM 的轮询间隔：每一轮都要发一次 JS eval，必须比 {@link #sleep()} 更宽。 */
    private static void sleepTextPoll() {
        try {
            Thread.sleep(TEXT_POLL_MS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private void dismissPanelQuietly() {
        AppBrandingActivity host = activity;
        if (host == null) return;
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Fragment panel = host.getSupportFragmentManager().findFragmentByTag(PANEL_TAG);
                if (panel instanceof DialogFragment dialog) dialog.dismissAllowingStateLoss();
            });
        } catch (Throwable ignored) {
        }
    }

    private static void write(File file, String text) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            try {
                out.write(text.getBytes(java.nio.charset.Charset.forName("UTF-8")));
            } finally {
                out.close();
            }
        } catch (Exception error) {
            throw new AssertionError("cannot write fixture " + file, error);
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteQuietly(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
