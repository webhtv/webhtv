package com.fongmi.android.tv.ui.web;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小说朗读的 JS 桥契约测试。
 *
 * 朗读功能横跨三处：reader.html（面板与段落收集）、WebReaderActivity（JS 桥）、
 * ReaderTtsService（后台服务与通知）。三者靠字符串名字相连，任何一处改名都不会
 * 被编译期发现，只会在真机上表现为「点了没反应」，因此这里用源码契约把它们钉住。
 */
public class ReaderTtsBridgeSourceTest {

    private static final Pattern BRIDGE_CALL = Pattern.compile("AndroidReader\\.([A-Za-z0-9_]+)");
    private static final Pattern JAVA_BRIDGE = Pattern.compile("@JavascriptInterface\\s+public\\s+[\\w<>\\[\\].]+\\s+(\\w+)\\s*\\(");
    private static final Pattern JS_CALLBACK_DEF = Pattern.compile("window\\.(__onTts\\w+)\\s*=");
    private static final Pattern JAVA_CALLBACK_CALL = Pattern.compile("window\\.(__onTts\\w+)\\s*&&\\s*window\\.\\1");

    @Test
    public void everyReaderBridgeCallHasANativeImplementation() throws Exception {
        String html = read("app/src/main/assets/reader.html");
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        Set<String> called = new LinkedHashSet<>();
        Matcher calls = BRIDGE_CALL.matcher(html);
        while (calls.find()) called.add(calls.group(1));

        Set<String> implemented = new LinkedHashSet<>();
        Matcher methods = JAVA_BRIDGE.matcher(activity);
        while (methods.find()) implemented.add(methods.group(1));

        assertTrue("reader.html 必须调用朗读桥", called.contains("ttsStart"));
        for (String name : called) {
            assertTrue("JS 调用了 AndroidReader." + name + "，但原生没有 @JavascriptInterface 实现",
                    implemented.contains(name));
        }
    }

    @Test
    public void everyNativeCallbackIsHandledByTheReader() throws Exception {
        String html = read("app/src/main/assets/reader.html");
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        Set<String> defined = new LinkedHashSet<>();
        Matcher defs = JS_CALLBACK_DEF.matcher(html);
        while (defs.find()) defined.add(defs.group(1));

        Set<String> dispatched = new LinkedHashSet<>();
        Matcher calls = JAVA_CALLBACK_CALL.matcher(activity);
        while (calls.find()) dispatched.add(calls.group(1));

        assertTrue("原生必须回调朗读状态", dispatched.contains("__onTtsState"));
        assertTrue("原生必须回调朗读进度", dispatched.contains("__onTtsProgress"));
        assertTrue("原生必须回调章末事件", dispatched.contains("__onTtsChapterEnd"));
        for (String name : dispatched) {
            assertTrue("原生回调 window." + name + "，但 reader.html 没有定义", defined.contains(name));
        }
    }

    @Test
    public void readerHtmlNoLongerTreatsWebSpeechAsThePrimaryPath() throws Exception {
        String html = read("app/src/main/assets/reader.html");

        int nativeGate = html.indexOf("function ttsNative()");
        int webSpeak = html.indexOf("function ttsWebSpeak(");
        int firstSpeakCall = html.indexOf("speechSynthesis.speak(");

        assertTrue("reader.html 必须有原生朗读开关", nativeGate >= 0);
        assertTrue("必须有浏览器兜底实现", webSpeak >= 0);
        assertTrue("speechSynthesis 只能出现在兜底实现里",
                firstSpeakCall > webSpeak);
    }

    @Test
    public void sourceIdsMatchTheNativeRegistry() throws Exception {
        String html = read("app/src/main/assets/reader.html");
        String engines = read("app/src/main/java/com/fongmi/android/tv/tts/TtsEngines.java");

        for (String id : new String[]{"system", "edge", "baidu", "custom"}) {
            assertTrue("reader.html 缺少音源入口: " + id, html.contains("data-tts-source=\"" + id + "\""));
            assertTrue("原生缺少音源常量: " + id, engines.contains("SOURCE_" + id.toUpperCase() + " = \"" + id + "\""));
        }
    }

    @Test
    public void readAloudServiceIsDeclaredForBackgroundPlayback() throws Exception {
        String manifest = read("app/src/main/AndroidManifest.xml");
        String service = read("app/src/main/java/com/fongmi/android/tv/service/ReaderTtsService.java");

        int declared = manifest.indexOf("android:name=\".service.ReaderTtsService\"");
        assertTrue("必须在清单里声明朗读前台服务", declared >= 0);
        int block = manifest.indexOf("/>", declared);
        String declaration = manifest.substring(declared, block);
        assertTrue("朗读服务必须是 mediaPlayback 前台服务: " + declaration,
                declaration.contains("foregroundServiceType=\"mediaPlayback\""));

        assertTrue("朗读服务必须上报前台通知", service.contains("startForeground("));
        assertTrue("朗读服务必须处理音频焦点", service.contains("requestAudioFocus"));
        assertTrue("朗读服务必须注册拔耳机广播并在路由丢失时暂停",
                service.contains("ACTION_AUDIO_BECOMING_NOISY") && service.contains("onAudioBecomingNoisy()"));
    }

    @Test
    public void controllerOwnsParagraphCursorAndChapterEnd() throws Exception {
        String controller = read("app/src/main/java/com/fongmi/android/tv/tts/ReaderTtsController.java");

        assertTrue("控制器必须持有朗读片段队列", controller.contains("TtsTextSplitter.split("));
        assertTrue("控制器必须支持上一段", controller.contains("public void previous()"));
        assertTrue("控制器必须支持下一段", controller.contains("public void next()"));
        assertTrue("控制器必须支持定时停止", controller.contains("timerTick"));
        assertTrue("章末必须通知上层续读", controller.contains("listener.onTtsChapterEnd()"));
    }

    @Test
    public void readAloudControlBridgesDispatchToTheMainThread() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");
        String[] names = {"ttsStart", "ttsPause", "ttsResume", "ttsToggle", "ttsPrev", "ttsNext",
                "ttsSeek", "ttsSetRate", "ttsSetPitch", "ttsSetVoice", "ttsSetTimer", "ttsStop"};
        for (String name : names) {
            int start = activity.indexOf("public void " + name + "(");
            assertTrue("朗读桥缺少方法: " + name, start >= 0);
            int end = activity.indexOf("@JavascriptInterface", start);
            String body = activity.substring(start, end > start ? end : activity.length());
            // @JavascriptInterface 方法跑在 WebView 的 JavaBridge 线程，而控制器/前台服务
            // 要求主线程语义（引擎回调、前台通知、MediaPlayer 都在主线程），必须显式切回主线程。
            assertTrue("朗读桥 " + name + " 没有切回主线程（控制器状态会与主线程引擎回调并发）",
                    body.contains("postToService(") || body.contains("runOnUiThread("));
        }
    }

    private static String read(String path) throws Exception {
        // 单测工作目录随 Gradle 版本落在仓库根或 app/ 模块目录，两种都兼容
        Path direct = Path.of(path);
        if (Files.exists(direct)) return new String(Files.readAllBytes(direct), StandardCharsets.UTF_8);
        Path moduleRelative = Path.of(path.startsWith("app/") ? path.substring("app/".length()) : path);
        assertTrue("缺少文件: " + path, Files.exists(moduleRelative));
        return new String(Files.readAllBytes(moduleRelative), StandardCharsets.UTF_8);
    }
}
