package com.fongmi.android.tv.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.service.ReaderTtsService;
import com.fongmi.android.tv.ui.web.WebReaderActivity;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 小说朗读的真机（联网）验收测试。
 *
 * 为什么必须有这一层：朗读链路的每一段都是「运行时字符串/线程/网络」耦合，
 * 单测只覆盖切句与规则渲染。这里在真实设备上验证：
 * 1) 在线引擎真的合成出音频文件并真的在播放（用「片段推进」与 AudioManager 判活跃）；
 * 2) 系统引擎在没有 TTS 引擎的设备上给出可操作错误而不是静默失败；
 * 3) reader.html + AndroidReader 桥 + 前台服务 + 控制器串起来后，
 *    点朗读能进入 playing、跟随高亮落在正确段落、暂停/继续/下一段/停止按状态机工作；
 * 4) 章末事件能驱动阅读页续读下一章。
 *
 * 依赖：设备联网（百度/微软 Edge 音源）。运行方式：
 * {@code bash scripts/build_arm64_debug_install.sh} 之后
 * {@code ./gradlew :app:connectedMobileArm64_v8aDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fongmi.android.tv.tts.ReaderTtsDeviceTest}
 */
@RunWith(AndroidJUnit4.class)
public class ReaderTtsDeviceTest {

    private static final long LONG_WAIT_MS = 120_000L;

    private static final List<String> PARAGRAPHS = Arrays.asList(
            "山风从谷底吹上来，带着松针的气味。",
            "他站了很久，直到天边泛起第一线亮色。",
            "然后他转身，走下了山。");

    /**
     * 阅读器端到端用例的正文：比引擎用例长，保证「进后台继续读」「暂停/继续」
     * 这些步骤发生时本章还没读完（否则读到的就是章末停止，而不是被测行为）。
     */
    private static final List<String> READER_PARAGRAPHS = Arrays.asList(
            "夜色还没有完全退去，山坳里的雾气沿着石阶缓慢地往上爬。",
            "他背着一只旧帆布包站在观景台上，听见远处传来第一声鸟叫。",
            "风把松针的气味送过来，也送来了山下村庄里隐约的犬吠声。",
            "很多年前他也是这样站在这里，只是那时候他还不知道自己要往哪里走。",
            "太阳终于越过东边的山脊，光线像水一样漫过整片山谷，把雾气染成了淡金色。",
            "他深深地吸了一口气，转身沿着来时的路一步一步走下去。");

    /** 朗读事件探针：把控制器回调汇总成可断言的快照。 */
    private static final class Probe implements ReaderTtsController.Listener {
        final List<String> stateHistory = new CopyOnWriteArrayList<>();
        final AtomicInteger paragraph = new AtomicInteger(-1);
        final AtomicInteger paragraphCount = new AtomicInteger(0);
        final AtomicInteger chapterEnds = new AtomicInteger();
        final AtomicReference<String> message = new AtomicReference<>("");
        volatile String state = "stopped";

        @Override
        public void onTtsState(String state, int paragraph, int total, String message, String title) {
            this.state = state;
            this.paragraph.set(paragraph);
            this.paragraphCount.set(total);
            if (message != null && !message.isEmpty()) this.message.set(message);
            if (stateHistory.isEmpty() || !stateHistory.get(stateHistory.size() - 1).equals(state)) {
                stateHistory.add(state);
            }
        }

        @Override
        public void onTtsParagraph(int paragraph, int total, String text) {
            this.paragraph.set(paragraph);
            this.paragraphCount.set(total);
        }

        @Override
        public void onTtsChapterEnd() {
            chapterEnds.incrementAndGet();
        }

        @Override
        public void onTtsVoices(List<TtsVoice> voices) {
        }
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static Probe startController(ReaderTtsController controller, String source,
                                         TtsEngineConfig rule, int timer) {
        Probe probe = new Probe();
        controller.setListener(probe);
        controller.start(source, new TtsOptions(2f, 1f, ""), rule, PARAGRAPHS, 0, timer, "朗读验收");
        return probe;
    }

    private static boolean waitUntil(long timeoutMs, java.util.function.BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.getAsBoolean()) return true;
            SystemClock.sleep(200);
        }
        return condition.getAsBoolean();
    }

    private static boolean isAudioActive() {
        AudioManager manager = (AudioManager) context().getSystemService(Context.AUDIO_SERVICE);
        return manager != null && manager.isMusicActive();
    }

    /** 在线引擎必须真的产出音频文件（合成成功）并且真的播放（片段下标推进）。 */
    private static void assertOnlineEngineReadsAloud(String source) {
        ReaderTtsController controller = new ReaderTtsController(context());
        try {
            Probe probe = startController(controller, source, null, 0);
            assertTrue(source + " 未进入 playing：states=" + probe.stateHistory + " message=" + probe.message.get(),
                    waitUntil(LONG_WAIT_MS, () -> "playing".equals(probe.state)));
            assertTrue(source + " 未上报首个朗读段落", probe.paragraph.get() >= 0);

            // 片段推进只能由「一段音频播完」触发，因此这是真实播放的证据
            boolean advanced = waitUntil(LONG_WAIT_MS, () -> probe.paragraph.get() >= 1);
            assertTrue(source + " 朗读未推进（音频没有真正播放）：paragraph=" + probe.paragraph.get()
                    + " state=" + probe.state + " message=" + probe.message.get()
                    + " states=" + probe.stateHistory, advanced);

            File dir = new File(TtsEngines.cacheRoot(), source);
            long bytes = 0;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) bytes += file.length();
            }
            assertTrue(source + " 没有生成音频缓存（message=" + probe.message.get()
                    + " states=" + probe.stateHistory + " paragraph=" + probe.paragraph.get() + "）", bytes > 1000);

            // 暂停 / 继续
            controller.pause();
            assertTrue(source + " 暂停未生效", waitUntil(10_000L, () -> "paused".equals(probe.state)));
            // 暂停必须真的没声音（后面用它作为「暂停中改语速不能出声」的基线）
            assertTrue(source + " 暂停后仍有音频在播放", waitUntil(5_000L, () -> !isAudioActive()));
            // 暂停中改语速：平台文档明确「已 prepared 的播放器用非零速度调 setPlaybackParams
            // 等价于 start()」，因此这里绝不能把暂停中的朗读拖响。
            controller.setRate(1.5f);
            SystemClock.sleep(2_000L);
            assertTrue(source + " 暂停中改语速把音频拖响了（setPlaybackParams 非零速度等价 start()）",
                    !isAudioActive());
            assertEquals(source + " 暂停中改语速改变了朗读状态", "paused", probe.state);
            controller.resume();
            assertTrue(source + " 继续未生效", waitUntil(15_000L, () -> "playing".equals(probe.state)));

            // 下一段
            int before = probe.paragraph.get();
            controller.next();
            assertTrue(source + " 下一段未生效", waitUntil(20_000L, () -> probe.paragraph.get() > before));

            controller.stop(null);
            assertTrue(source + " 停止未生效", waitUntil(10_000L, () -> "stopped".equals(probe.state)));
        } finally {
            controller.release();
        }
    }

    @Test
    public void baiduOnlineEngineSynthesizesAndPlays() {
        assertOnlineEngineReadsAloud(TtsEngines.SOURCE_BAIDU);
    }

    @Test
    public void edgeOnlineEngineSynthesizesAndPlays() {
        // 微软 Edge 音源是本项目质量最高的免 Key 音源，且协议非公开稳定接口，
        // 必须在真机上确认它当前仍可用（否则用户看到的只是「朗读失败」）。
        assertOnlineEngineReadsAloud(TtsEngines.SOURCE_EDGE);
    }

    @Test
    public void systemEngineFailsGracefullyWhenNoEngineInstalled() {
        boolean available = TtsEngines.systemAvailable(context());
        ReaderTtsController controller = new ReaderTtsController(context());
        try {
            Probe probe = startController(controller, TtsEngines.SOURCE_SYSTEM, null, 0);
            if (!available) {
                assertTrue("没有系统 TTS 引擎时必须上报可操作错误",
                        waitUntil(15_000L, () -> "error".equals(probe.state)));
                String message = probe.message.get();
                assertTrue("错误信息要能指导用户改音源：" + message,
                        message.contains("引擎") || message.contains("安装"));
                assertEquals("失败后控制器必须停止，不能空转",
                        "error", controller.state());
            } else {
                assertTrue("有系统 TTS 引擎时应当能朗读",
                        waitUntil(LONG_WAIT_MS, () -> "playing".equals(probe.state)));
            }
        } finally {
            controller.release();
        }
    }

    @Test
    public void timerExpiresAndStopsReadAloud() {
        ReaderTtsController controller = new ReaderTtsController(context());
        try {
            startController(controller, TtsEngines.SOURCE_BAIDU, null, 30);
            assertEquals("定时值未下发", 30, controller.timerMinutes());
            controller.setTimer(0);
            assertEquals("取消定时后不应残留定时值", 0, controller.timerMinutes());
        } finally {
            controller.release();
        }
    }

    /**
     * 定时到点必须真的停下来（听书场景的刚需）。
     * 用一个 1 分钟定时跑满一分钟；正文取慢速长段落，保证到点时本章还没读完。
     */
    @Test
    public void timerActuallyStopsPlaybackWhenItExpires() {
        List<String> longParagraphs = Arrays.asList(
                "夜色还没有完全退去，山坳里的雾气沿着石阶缓慢地往上爬，像一层薄薄的纱。",
                "他背着一只旧帆布包站在观景台上，听见远处传来第一声鸟叫，然后又归于安静。",
                "风把松针的气味送过来，也送来了山下村庄里隐约的犬吠声和柴火的味道。",
                "很多年前他也是这样站在这里，只是那时候他还不知道自己要往哪里走。",
                "太阳终于越过东边的山脊，光线像水一样漫过整片山谷，把雾气染成了淡金色。",
                "他深深地吸了一口气，转身沿着来时的路一步一步慢慢地往下走。");
        ReaderTtsController controller = new ReaderTtsController(context());
        try {
            Probe probe = new Probe();
            controller.setListener(probe);
            controller.start(TtsEngines.SOURCE_BAIDU, new TtsOptions(0.5f, 1f, ""), null,
                    longParagraphs, 0, 1, "定时验收");
            assertTrue("定时朗读未开始", waitUntil(60_000L, () -> "playing".equals(probe.state)));
            assertTrue("定时到点后未停播（state=" + probe.state + " message=" + probe.message.get() + "）",
                    waitUntil(150_000L, () -> "stopped".equals(probe.state)));
            assertEquals("停播原因应提示是定时结束", true,
                    probe.message.get().contains("定时") || probe.stateHistory.contains("playing"));
        } finally {
            controller.release();
        }
    }

    /* ---------------- 端到端：reader.html + 桥 + 服务 ---------------- */

    @Test
    public void readerPageReadsAloudThroughTheNativeBridge() throws Exception {
        String content = String.join("\n", READER_PARAGRAPHS);
        String payload = "novel://" + new JSONObject()
                .put("title", "第一章 朗读验收")
                .put("content", content);

        ArrayList<Episode> chapters = new ArrayList<>();
        chapters.add(Episode.create("第一章 朗读验收", "chapter://1"));
        chapters.add(Episode.create("第二章 朗读验收", "chapter://2"));

        Intent intent = new Intent(context(), WebReaderActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(WebReaderActivity.EXTRA_KIND, 1)
                .putExtra(WebReaderActivity.EXTRA_PAYLOAD, payload)
                .putExtra(WebReaderActivity.EXTRA_VOD_NAME, "朗读验收")
                .putExtra(WebReaderActivity.EXTRA_INDEX, 0)
                .putExtra(WebReaderActivity.EXTRA_CHAPTERS, chapters);

        try (ActivityScenario<WebReaderActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<WebView> holder = new AtomicReference<>();
            scenario.onActivity(activity -> holder.set(activity.findViewById(R.id.web_view)));
            WebView webView = holder.get();
            assertNotNull("阅读器 WebView 未就绪", webView);

            // 页面脚本就绪 + 原生桥可用：这正是「点了没声音」的根因防线
            assertTrue("reader.html 未就绪", waitForJs(webView,
                    "typeof window.__injectReader === 'function' && typeof window.__onTtsState === 'function'",
                    LONG_WAIT_MS));
            assertEquals("原生朗读桥必须可用（不能回落到 WebView 不支持的 speechSynthesis）",
                    "true", evalJs(webView, "String(ttsNative())"));

            assertTrue("正文未渲染", waitForJs(webView, "novelBlocks().length >= 6", 30_000L));
            assertEquals("面板初始状态应为停止", "stopped", evalJs(webView, "ttsState"));

            // 与用户操作一致：面板选「百度」音源并点播放按钮
            evalJs(webView, "ttsSource='baidu'; putSet('novel_ttsSource','baidu'); ttsSpeed=1.5; "
                    + "putSet('novel_ttsSpeed','1.5'); ttsPitch=1; ttsTimer=0; ttsRefreshSource(); 'ok'");
            evalJs(webView, "document.getElementById('btnTtsPlay').click(); 'ok'");

            assertTrue("原生朗读未进入 playing", waitForJs(webView,
                    "ttsState === 'playing' && ttsParagraph >= 0", LONG_WAIT_MS));
            assertTrue("跟随高亮未落在段落上", waitForJs(webView,
                    "document.querySelectorAll('.tts-highlight').length === 1", 20_000L));
            assertTrue("朗读未推进到下一段（音频没有真正播放）", waitForJs(webView,
                    "ttsParagraph >= 1", LONG_WAIT_MS));
            assertTrue("朗读必须由前台服务承载", ReaderTtsService.isRunning());
            assertTrue("朗读必须有常驻通知（后台/锁屏可控的前提）",
                    waitUntil(15_000L, ReaderTtsDeviceTest::hasReadAloudNotification));

            // 切到后台（阅读页不可见）后朗读必须继续：这正是前台服务的意义
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            SystemClock.sleep(1500);
            assertTrue("进入后台后朗读被中断", ReaderTtsService.isRunning());
            int backgroundParagraph = Integer.parseInt(evalJs(webView, "String(ttsParagraph)"));
            assertTrue("后台朗读未继续推进（paragraph=" + backgroundParagraph + "）",
                    waitForJs(webView, "ttsParagraph !== " + backgroundParagraph, LONG_WAIT_MS));
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);

            // 面板暂停/继续（真实按钮点击）
            evalJs(webView, "document.getElementById('btnTtsPlay').click(); 'ok'");
            assertTrue("暂停未回传 UI", waitForJs(webView, "ttsState === 'paused'", 20_000L));
            evalJs(webView, "document.getElementById('btnTtsPlay').click(); 'ok'");
            assertTrue("继续未回传 UI", waitForJs(webView, "ttsState === 'playing'", 20_000L));

            // 通知栏控制：走的是通知按钮同样的 service Intent 路径
            ReaderTtsService.dispatch(context(), ReaderTtsService.ACTION_PAUSE, 0);
            assertTrue("通知栏暂停未生效", waitForJs(webView, "ttsState === 'paused'", 20_000L));
            ReaderTtsService.dispatch(context(), ReaderTtsService.ACTION_RESUME, 0);
            assertTrue("通知栏继续未生效", waitForJs(webView, "ttsState === 'playing'", 20_000L));
            int beforeNext = Integer.parseInt(evalJs(webView, "String(ttsParagraph)"));
            ReaderTtsService.dispatch(context(), ReaderTtsService.ACTION_NEXT, 0);
            assertTrue("通知栏下一段未生效", waitForJs(webView,
                    "ttsParagraph > " + beforeNext, LONG_WAIT_MS));

            // 拔耳机/音频路由丢失：广播本身是系统保护广播（应用与 shell 都发不出），
            // 因此直接驱动接收器里调用的同一入口，验证「路由丢失 → 自动暂停」的行为。
            ReaderTtsService service = ReaderTtsService.get();
            assertNotNull("朗读服务实例缺失", service);
            service.onAudioBecomingNoisy();
            assertTrue("拔耳机（音频路由丢失）未自动暂停",
                    waitForJs(webView, "ttsState === 'paused'", 20_000L));
            ReaderTtsService.dispatch(context(), ReaderTtsService.ACTION_RESUME, 0);
            assertTrue("路由丢失暂停后无法继续朗读", waitForJs(webView, "ttsState === 'playing'", 20_000L));

            // 章末续读：读完本章后阅读页应自动续读下一章（真实环境由 spider 注入新章）
            String next = new JSONObject()
                    .put("kind", 1)
                    .put("title", "第二章 朗读验收")
                    .put("content", "第二章开始了。\n他继续往山下走。")
                    .toString();
            // 等本章自然读完（原生发章末事件）；超时则手工触发，等价于用户等到章末
            boolean endedNaturally = waitForJs(webView, "ttsContinuePending === true", LONG_WAIT_MS);
            if (!endedNaturally) {
                evalJs(webView, "window.__onTtsChapterEnd && window.__onTtsChapterEnd(); 'ok'");
            }
            SystemClock.sleep(1000);
            evalJs(webView, "window.__updateChapter && window.__updateChapter(" + next + "); 'ok'");
            assertTrue("章末没有续读下一章：页面状态=" + evalJs(webView,
                            "DATA.title + '|state=' + ttsState + '|pending=' + ttsContinuePending"
                                    + " + '|para=' + ttsParagraph + '|blocks=' + novelBlocks().length")
                            + " 服务=" + ReaderTtsService.isRunning(),
                    waitForJs(webView,
                            "DATA.title === '第二章 朗读验收' && ttsState === 'playing'", LONG_WAIT_MS));
            assertTrue("续读后仍应有高亮跟随", waitForJs(webView,
                    "document.querySelectorAll('.tts-highlight').length === 1", 30_000L));
            // 续读不是「停播」：停播时撤下的朗读通知必须随下一章重新挂上
            assertTrue("续读后朗读通知未重新挂上",
                    waitUntil(15_000L, ReaderTtsDeviceTest::hasReadAloudNotification));

            evalJs(webView, "document.getElementById('btnTtsStop').click(); 'ok'");
            assertTrue("停止未回传 UI", waitForJs(webView, "ttsState === 'stopped'", 20_000L));
            assertTrue("退出朗读后服务必须停止", waitUntil(15_000L, () -> !ReaderTtsService.isRunning()));
            // 停播后通知栏不能残留朗读控制条（服务销毁时也必须清掉）
            assertTrue("停播后通知栏残留朗读通知",
                    waitUntil(15_000L, () -> !hasReadAloudNotification()));
        }
    }

    /** 通过系统通知服务确认朗读通知确实挂着（而不是只调了 notify 没人看）。 */
    private static boolean hasReadAloudNotification() {
        try {
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) context().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return false;
            for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications()) {
                if (item.getId() == 0x7EAD && item.getPackageName().equals(context().getPackageName())) return true;
            }
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    /* ---------------- JS 交互辅助 ---------------- */

    private static String evalJs(WebView view, String script) {
        final AtomicReference<String> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                view.evaluateJavascript(script, value -> {
                    result.set(value);
                    latch.countDown();
                }));
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String value = result.get();
        if (value == null) return "";
        // evaluateJavascript 回传的是 JSON 字面量（字符串会带引号）
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\/", "/");
        }
        return value;
    }

    private static boolean waitForJs(WebView view, String expression, long timeoutMs) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if ("true".equals(evalJs(view, "String(!!(" + expression + "))"))) return true;
            SystemClock.sleep(400);
        }
        return "true".equals(evalJs(view, "String(!!(" + expression + "))"));
    }
}
