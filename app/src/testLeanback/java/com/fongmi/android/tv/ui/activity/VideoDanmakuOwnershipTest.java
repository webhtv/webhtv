package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.*;

import android.os.Looper;
import android.os.Message;
import androidx.media3.common.MediaMetadata;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.*;
import com.fongmi.android.tv.utils.Clock;
import com.github.catvod.Init;
import com.github.catvod.net.OkHttp;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.concurrent.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = VideoDanmakuOwnershipTest.TestApp.class,
        shadows = {VideoDanmakuOwnershipTest.UiIsolation.class, VideoDanmakuOwnershipTest.SurfaceIsolation.class,
                VideoDanmakuOwnershipTest.PrepareIsolation.class, VideoDanmakuOwnershipTest.ParseIsolation.class,
                VideoDanmakuOwnershipTest.MobileSiteIsolation.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class VideoDanmakuOwnershipTest {
    private MockWebServer server;
    private OkHttpClient client;
    private CountDownLatch completed;
    private final List<RecordingManager> managers = new ArrayList<>();

    @Before public void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder().build();
        ReflectionHelpers.setField(OkHttp.get(), "client", client);
        DanmakuSetting.putLoad(true);
        DanmakuSetting.putAuto(true);
        DanmakuSetting.putApiUrl(server.url("/danmaku").toString());
        PlayerSetting.putPlayer(PlayerSetting.EXO);
    }

    @After public void cleanup() throws Exception {
        client.dispatcher().cancelAll();
        server.shutdown();
        for (RecordingManager manager : managers) manager.release();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdownNow();
    }

    @Test public void postedOldResultCannotApplyDuringActualGetPlayerPendingWindow() throws Exception {
        TestVideo host = host();
        response("old.xml");
        accepted(host, result());
        awaitPosted();
        String before = host.manager.getPlaybackTraceId();
        request(host);
        assertEquals("pending must still have the old playback trace", before, host.manager.getPlaybackTraceId());
        assertEquals(1, host.model.requests);
        drain();
        assertEquals("HTTP already posted; replacement pending must reject old danmaku", 0, host.manager.applied.size());
    }

    @Test public void currentAcceptedStartAppliesExactlyOnce() throws Exception {
        TestVideo host = host();
        response("current.xml");
        accepted(host, result());
        awaitPosted();
        drain();
        assertEquals(Collections.singletonList(server.url("/current.xml").toString()), host.manager.applied);
    }

    @Test public void sameUrlAndReusedResultStillHaveDistinctPlaybackSessions() throws Exception {
        TestVideo host = host();
        Result shared = result();
        response("a1.xml");
        accepted(host, shared);
        awaitPosted();
        String a1 = host.manager.getPlaybackTraceId();
        host.onItemClick(shared);
        response("a2.xml");
        accepted(host, shared);
        awaitPosted();
        assertNotEquals(a1, host.manager.getPlaybackTraceId());
        drain();
        assertEquals(Collections.singletonList(server.url("/a2.xml").toString()), host.manager.applied);
    }

    @Test public void mutableResultCannotChangeCapturedSource() throws Exception {
        TestVideo host = host();
        Result shared = result();
        response("old.xml");
        accepted(host, shared);
        awaitPosted();
        shared.setUrl("https://example.test/changed.mp4");
        drain();
        assertTrue(host.manager.applied.isEmpty());
    }

    @Test public void rejectedStartMustNotSearchForOldTrace() throws Exception {
        TestVideo host = host();
        response("old.xml");
        accepted(host, result());
        awaitPosted();
        String old = host.manager.getPlaybackTraceId();
        accepted(host, Result.error("rejected"));
        accepted(host, Result.empty());
        assertEquals(old, host.manager.getPlaybackTraceId());
        drain();
        assertTrue(host.manager.applied.isEmpty());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void qualityReplacementInvalidatesAlreadyPostedResult() throws Exception {
        TestVideo host = host();
        response("old.xml");
        accepted(host, result());
        awaitPosted();
        host.onItemClick(result());
        drain();
        assertTrue(host.manager.applied.isEmpty());
    }

    @Test public void disconnectAndRedirectRejectQueuedResult() throws Exception {
        TestVideo host = host();
        response("old.xml");
        accepted(host, result());
        awaitPosted();
        host.onServiceDisconnected(null);
        drain();
        assertTrue(host.manager.applied.isEmpty());
        TestVideo redirected = host();
        response("redirect.xml");
        accepted(redirected, result());
        awaitPosted();
        redirected.setRedirect(true);
        drain();
        assertTrue(redirected.manager.applied.isEmpty());
    }

    @Test public void pendingServiceResultIsDiscardedBeforeNewRequest() throws Exception {
        TestVideo host = host();
        ReflectionHelpers.setField(host, "mService", null);
        accepted(host, result());
        request(host);
        assertNull(ReflectionHelpers.getField(host, "mPendingPlayer"));
        assertEquals(0, server.getRequestCount());
    }

    @Test public void currentParseCanApplyButNextParseCannotReuseOldPost() throws Exception {
        TestVideo host = host();
        Result parsed = result();
        parsed.setParse(1);
        response("parse.xml");
        accepted(host, parsed);
        awaitPosted();
        assertNotEquals(PlaybackTrace.NONE, host.manager.getPlaybackTraceId());
        drain();
        assertEquals(1, host.manager.applied.size());
        response("old-parse.xml");
        accepted(host, parsed);
        awaitPosted();
        host.onItemClick(parsed);
        drain();
        assertEquals(1, host.manager.applied.size());
    }

    @Test public void oldActivityDestroyDoesNotCancelNewOwnerAndItsPostedResult() throws Exception {
        TestVideo old = host();
        response("old-owner.xml");
        accepted(old, result());
        awaitPosted();
        TestVideo current = host();
        response("new-owner.xml");
        accepted(current, result());
        awaitPosted();
        old.destroyHost();
        drain();
        assertTrue(old.manager.applied.isEmpty());
        assertEquals(Collections.singletonList(server.url("/new-owner.xml").toString()), current.manager.applied);
    }

    @Test public void decoderTraceReplacementRenewsOnceButDuplicateReadyAndSeekDoNot() throws Exception {
        TestVideo host = host();
        response("old-session.xml");
        accepted(host, result());
        awaitPosted();
        host.manager.toggleDecode();
        response("renewed.xml");
        host.ready();
        awaitPosted();
        drain();
        host.ready();
        host.manager.getPlayer().seekTo(1000);
        host.ready();
        assertEquals(Collections.singletonList(server.url("/renewed.xml").toString()), host.manager.applied);
        assertEquals("same trace READY and seek must not requery", 2, server.getRequestCount());
    }

    @Test public void stoppedSameSessionStillAcceptsResult() throws Exception {
        TestVideo host = host();
        response("background.xml");
        accepted(host, result());
        awaitPosted();
        host.stoppedHost();
        drain();
        assertEquals(1, host.manager.applied.size());
    }

    @Test public void newerPlaybackFinishesBeforeCanceledOldHttpAndOnlyNewResultApplies() throws Exception {
        TestVideo host = host();
        CountDownLatch newReceived = new CountDownLatch(1), releaseNew = new CountDownLatch(1);
        CountDownLatch oldReceived = new CountDownLatch(1), releaseOld = new CountDownLatch(1), idle = new CountDownLatch(1);
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) throws InterruptedException {
                if (request.getBody().readUtf8().replace("+", "%20").contains("episode=Episode%20A")) {
                    oldReceived.countDown();
                    releaseOld.await(5, TimeUnit.SECONDS);
                    return new MockResponse().setBody("[{\"url\":\"/old.xml\"}]");
                }
                newReceived.countDown();
                releaseNew.await(5, TimeUnit.SECONDS);
                return new MockResponse().setBody("[{\"url\":\"" + server.url("/new.xml") + "\"}]");
            }
        });
        accepted(host, result());
        assertTrue(oldReceived.await(5, TimeUnit.SECONDS));
        EpisodeAdapter episodes = ReflectionHelpers.getField(host, "mEpisodeAdapter");
        episodes.addAll(Collections.singletonList(Episode.create("Episode B", "https://example.test/same.mp4")));
        request(host);
        accepted(host, result());
        assertTrue(newReceived.await(5, TimeUnit.SECONDS));
        client.dispatcher().setIdleCallback(idle::countDown);
        releaseNew.countDown();
        releaseOld.countDown();
        assertTrue(idle.await(5, TimeUnit.SECONDS));
        drain();
        assertEquals(Collections.singletonList(server.url("/new.xml").toString()), host.manager.applied);
    }

    @Test public void mobileDecodeRefreshRejectsAlreadyPostedResultBeforeTraceChanges() throws Exception {
        TestVideo host = host();
        Object mobile = mobileSeam(host);
        response("mobile-old.xml");
        acceptedMobile(host, mobile, result());
        awaitPosted();
        String oldTrace = host.manager.getPlaybackTraceId();
        MobileSiteIsolation.pending();
        ReflectionHelpers.callInstanceMethod(mobile, "refreshAndSwitchDecode");
        assertTrue(MobileSiteIsolation.entered.await(5, TimeUnit.SECONDS));
        assertEquals(oldTrace, host.manager.getPlaybackTraceId());
        drain();
        assertEquals("Mobile decode HTTP is pending: posted old auto result must not apply", 0, host.manager.applied.size());
    }

    @Test public void mobileFreshDecodeUsesFreshSpiderFirstResultRatherThanOldEmptySiteList() throws Exception {
        TestVideo host = host(); Object mobile = mobileSeam(host);
        DanmakuSetting.putSpiderFirst(true);
        response("old.xml"); acceptedMobile(host, mobile, result()); awaitPosted();
        MobileSiteIsolation.pending();
        ReflectionHelpers.callInstanceMethod(mobile, "refreshAndSwitchDecode");
        assertTrue(MobileSiteIsolation.entered.await(5, TimeUnit.SECONDS));
        MobileSiteIsolation.result = Result.objectFrom("{\"url\":\"https://example.test/fresh.mp4\",\"parse\":0,\"danmaku\":[{\"url\":\"https://example.test/site.xml\"}]}");
        MobileSiteIsolation.release.countDown(); awaitMobilePosted(); drain();
        ReflectionHelpers.callInstanceMethod(mobile, "renewAutoDanmaku");
        assertTrue(host.manager.applied.isEmpty());
        assertEquals("fresh site danmaku must prevent any new auto HTTP", 1, server.getRequestCount());
    }

    @Test public void mobileAcceptedFreshDecodeQueriesAndAppliesItsNewSessionOnce() throws Exception {
        TestVideo host = host(); Object mobile = mobileSeam(host);
        DanmakuSetting.putSpiderFirst(false);
        response("old.xml"); acceptedMobile(host, mobile, result()); awaitPosted();
        String old = host.manager.getPlaybackTraceId();
        MobileSiteIsolation.pending();
        ReflectionHelpers.callInstanceMethod(mobile, "refreshAndSwitchDecode");
        assertTrue(MobileSiteIsolation.entered.await(5, TimeUnit.SECONDS));
        MobileSiteIsolation.result = result();
        response("fresh.xml"); MobileSiteIsolation.release.countDown(); awaitMobilePosted(); drain();
        pollDanmakuPosted(); drain();
        assertNotEquals(old, host.manager.getPlaybackTraceId());
        assertEquals(Collections.singletonList(server.url("/fresh.xml").toString()), host.manager.applied);
        ReflectionHelpers.callInstanceMethod(mobile, "renewAutoDanmaku");
        assertEquals(2, server.getRequestCount());
    }

    @Test public void mobileFailedDecodeFallbackCanRestoreSameMediaAutoQueryOnlyOnce() throws Exception {
        TestVideo host = host(); Object mobile = mobileSeam(host);
        DanmakuSetting.putSpiderFirst(false);
        response("old.xml"); acceptedMobile(host, mobile, result()); awaitPosted();
        MobileSiteIsolation.pending();
        ReflectionHelpers.callInstanceMethod(mobile, "refreshAndSwitchDecode");
        assertTrue(MobileSiteIsolation.entered.await(5, TimeUnit.SECONDS));
        MobileSiteIsolation.release.countDown(); awaitMobilePosted(); drain();
        assertEquals("fallback keeps toggleDecode and issues no auto query by itself", 1, server.getRequestCount());
        assertTrue(host.manager.applied.isEmpty());
        response("fallback.xml");
        ReflectionHelpers.callInstanceMethod(mobile, "renewAutoDanmaku");
        pollDanmakuPosted(); drain();
        assertEquals(Collections.singletonList(server.url("/fallback.xml").toString()), host.manager.applied);
        ReflectionHelpers.callInstanceMethod(mobile, "renewAutoDanmaku");
        assertEquals("same trace after fallback recovery must not requery", 2, server.getRequestCount());
    }

    @Test public void mobileLateDecodeFallbackCannotReviveTokenAfterAnotherReplacement() throws Exception {
        TestVideo host = host(); Object mobile = mobileSeam(host);
        response("old.xml"); acceptedMobile(host, mobile, result()); awaitPosted();
        MobileSiteIsolation.pending();
        ReflectionHelpers.callInstanceMethod(mobile, "refreshAndSwitchDecode");
        assertTrue(MobileSiteIsolation.entered.await(5, TimeUnit.SECONDS));
        request(host);
        MobileSiteIsolation.release.countDown(); awaitMobilePosted(); drain();
        assertNull("newer replacement must discard the suspended decode token",
                ReflectionHelpers.getField(mobile, "mDecodeRestoreToken"));
        assertEquals(0, host.manager.applied.size());
    }

    private static void pollDanmakuPosted() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Message message = ReflectionHelpers.getField(Looper.getMainLooper().getQueue(), "mMessages");
            while (message != null) {
                if (message.getCallback() != null && message.getCallback().getClass().getName().contains("DanmakuApi")) return;
                message = ReflectionHelpers.getField(message, "next");
            }
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        fail("automatic danmaku callback must be queued on App main queue");
    }

    private static void awaitMobilePosted() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Message message = ReflectionHelpers.getField(Looper.getMainLooper().getQueue(), "mMessages");
            while (message != null) {
                if (message.getCallback() != null && message.getCallback().getClass().getName().contains("CompiledMobileDanmaku")) return;
                message = ReflectionHelpers.getField(message, "next");
            }
            Thread.yield();
        }
        fail("actual Mobile refresh result must be posted on App main queue");
    }

    private static void acceptedMobile(TestVideo host, Object mobile, Result result) {
        String before = host.manager.getPlaybackTraceId();
        DanmakuSetting.putAuto(false);
        accepted(host, result);
        DanmakuSetting.putAuto(true);
        ReflectionHelpers.callInstanceMethod(mobile, "searchAutoDanmaku",
                ReflectionHelpers.ClassParameter.from(Result.class, result),
                ReflectionHelpers.ClassParameter.from(PlayerManager.class, host.manager),
                ReflectionHelpers.ClassParameter.from(String.class, before));
    }

    // Compile the actual Mobile source seam, not a transcription of its ownership/replacement rules.
    private static Object mobileSeam(TestVideo host) throws Exception {
        ReflectionHelpers.setField(host.manager, "callback", java.lang.reflect.Proxy.newProxyInstance(
                host.getClass().getClassLoader(), new java.lang.Class<?>[]{PlayerManager.Callback.class}, (proxy, method, args) -> null));
        java.nio.file.Path source = java.nio.file.Path.of("src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        if (!java.nio.file.Files.exists(source)) source = java.nio.file.Path.of("app").resolve(source);
        String mobile = java.nio.file.Files.readString(source);
        int begin = mobile.indexOf("    private final String mDanmakuOwnerTag");
        int end = mobile.indexOf("    @Override\n    protected void startPlayer", begin);
        if (end < 0) end = mobile.indexOf("    @Override\r\n    protected void startPlayer", begin);
        String body = mobile.substring(begin, end);
        String code = "package com.fongmi.android.tv.ui.activity;\n"
                + "import android.text.TextUtils; import androidx.media3.common.MediaMetadata;\n"
                + "import com.fongmi.android.tv.bean.*; import com.fongmi.android.tv.api.*;\n"
                + "import com.fongmi.android.tv.player.*; import com.fongmi.android.tv.service.PlaybackService;\n"
                + "import com.fongmi.android.tv.setting.*; import com.fongmi.android.tv.utils.Notify;\n"
                + "import com.fongmi.android.tv.App; import com.github.catvod.crawler.SpiderDebug;\n"
                + "import com.fongmi.android.tv.utils.Task;\n"
                + "public class CompiledMobileDanmaku extends VideoDanmakuOwnershipTest.MobileBridge {\n"
                + "private History mHistory; private boolean decodeSwitchRefreshing;\n"
                + "public CompiledMobileDanmaku(VideoDanmakuOwnershipTest.TestVideo host) { super(host); mHistory=history(); }\n"
                + body + sourceMethod(mobile, "refreshAndSwitchDecode") + sourceMethod(mobile, "switchDecodeWithResult") + "\n}";
        Set<String> paths = new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for (ClassLoader loader = host.getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader urls) for (java.net.URL url : urls.getURLs()) {
                if ("file".equals(url.getProtocol())) paths.add(java.nio.file.Path.of(url.toURI()).toString());
            }
        }
        java.nio.file.Path output = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build"), "C-TV-ASYNC-RESULT-OWNERSHIP-MOBILE-");
        java.nio.file.Path input = output.resolve("CompiledMobileDanmaku.java");
        java.nio.file.Files.writeString(input, code);
        Object compiler = java.lang.Class.forName("javax.tools.ToolProvider").getMethod("getSystemJavaCompiler").invoke(null);
        assertNotNull("existing JDK compiler", compiler);
        java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();
        String[] args = {"-proc:none", "-classpath", String.join(java.io.File.pathSeparator, paths), "-d", output.toString(), input.toString()};
        int exit = (Integer) java.lang.Class.forName("javax.tools.Tool").getMethod("run", java.io.InputStream.class, java.io.OutputStream.class,
                java.io.OutputStream.class, String[].class).invoke(compiler, null, errors, errors, args);
        assertEquals("Mobile source seam compiler: " + errors, 0, exit);
        ClassLoader loader = new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, host.getClass().getClassLoader());
        return loader.loadClass("com.fongmi.android.tv.ui.activity.CompiledMobileDanmaku").getConstructor(TestVideo.class).newInstance(host);
    }

    private static String sourceMethod(String source, String name) {
        java.util.regex.Matcher method = java.util.regex.Pattern.compile("    private [^\\r\\n]+ " + name + "\\(").matcher(source);
        if (!method.find()) throw new IllegalArgumentException("Missing Mobile source method " + name);
        int begin = method.start();
        int open = source.indexOf('{', method.end()), depth = 1, end = open + 1;
        while (depth > 0) { char c = source.charAt(end++); if (c == '{') depth++; else if (c == '}') depth--; }
        return source.substring(begin, end) + "\n";
    }

    public static class MobileBridge {
        final TestVideo host;
        protected com.fongmi.android.tv.utils.Clock mClock;
        public MobileBridge(TestVideo host) { this.host = host; mClock = ReflectionHelpers.getField(host, "mClock"); }
        public History history() { return ReflectionHelpers.getField(host, "mHistory"); }
        public PlaybackService service() { return host.service(); }
        public PlayerManager player() { return host.player(); }
        public boolean isOwner() { return host.isOwner(); }
        public boolean isUseParse() { return host.isUseParse(); }
        public boolean isRedirect() { return host.isRedirect(); }
        public boolean isPlaybackExiting() { return host.isPlaybackExiting(); }
        public boolean isFinishing() { return host.isFinishing(); }
        public boolean isDestroyed() { return host.isDestroyed(); }
        public String getHistoryKey() { return ReflectionHelpers.callInstanceMethod(host, "getHistoryKey"); }
        public String getKey() { return ReflectionHelpers.callInstanceMethod(host, "getKey"); }
        public Flag getFlag() { return ReflectionHelpers.callInstanceMethod(host, "getFlag"); }
        public Episode getEpisode() { return ReflectionHelpers.callInstanceMethod(host, "getEpisode"); }
        public String getEpisodePlayFlag(Flag flag, Episode episode) {
            return ReflectionHelpers.callInstanceMethod(host, "getEpisodePlayFlag", ReflectionHelpers.ClassParameter.from(Flag.class, flag), ReflectionHelpers.ClassParameter.from(Episode.class, episode));
        }
        public MediaMetadata buildMetadata() { return ReflectionHelpers.callInstanceMethod(host, "buildMetadata"); }
        public void setNextDecodeText() { }
        public void setDecodeSwitchPending(boolean pending) { }
        public void setR1Callback() { }
        public void setDecode() { }
    }

    @Implements(com.fongmi.android.tv.api.SiteApi.class)
    public static class MobileSiteIsolation {
        static CountDownLatch entered, release;
        static Result result;
        static void pending() { entered = new CountDownLatch(1); release = new CountDownLatch(1); result = Result.error("fixture"); }
        @Implementation protected static Result playerContent(String key, String flag, String episode) throws InterruptedException {
            entered.countDown(); release.await(5, TimeUnit.SECONDS); return result;
        }
    }

    private TestVideo host() {
        org.robolectric.android.controller.ActivityController<TestVideo> controller = Robolectric.buildActivity(TestVideo.class);
        TestVideo host = controller.get();
        host.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        controller.create();
        ActivityVideoBinding binding = (ActivityVideoBinding) host.getBinding();
        host.setContentView(binding.getRoot());
        host.manager = new RecordingManager();
        managers.add(host.manager);
        PlaybackService service = new PlaybackService();
        ReflectionHelpers.setField(service, "player", host.manager);
        ReflectionHelpers.setField(host, "mService", service);
        host.model = new PendingModel();
        ReflectionHelpers.setField(host, "mViewModel", host.model);
        ReflectionHelpers.setField(host, "mQualityAdapter", new QualityAdapter(host));
        FlagAdapter flags = new FlagAdapter(host);
        Flag flag = Flag.create("line");
        flags.addAll(Collections.singletonList(flag));
        ReflectionHelpers.setField(host, "mFlagAdapter", flags);
        EpisodeAdapter episodes = new EpisodeAdapter(host);
        Episode episode = Episode.create("Episode A", "https://example.test/same.mp4");
        episodes.addAll(Collections.singletonList(episode));
        ReflectionHelpers.setField(host, "mEpisodeAdapter", episodes);
        History history = new History();
        history.setVodName("Test video");
        history.setVodFlag("line");
        history.setVodRemarks("Episode A");
        history.setEpisodeUrl(episode.getUrl());
        ReflectionHelpers.setField(host, "mHistory", history);
        ReflectionHelpers.setField(host, "mClock", Clock.create());
        return host;
    }

    private static Result result() { return Result.objectFrom("{\"url\":\"https://example.test/same.mp4\",\"parse\":0}"); }
    private static void accepted(TestVideo host, Result result) {
        ReflectionHelpers.callInstanceMethod(host, "setPlayer", ReflectionHelpers.ClassParameter.from(Result.class, result));
    }
    private static void request(TestVideo host) {
        FlagAdapter flags = ReflectionHelpers.getField(host, "mFlagAdapter");
        EpisodeAdapter episodes = ReflectionHelpers.getField(host, "mEpisodeAdapter");
        ReflectionHelpers.callInstanceMethod(host, "getPlayer", ReflectionHelpers.ClassParameter.from(Flag.class, flags.getActivated()),
                ReflectionHelpers.ClassParameter.from(Episode.class, episodes.getActivated()));
    }
    private void response(String url) {
        completed = new CountDownLatch(1);
        client.dispatcher().setIdleCallback(completed::countDown);
        server.enqueue(new MockResponse().setBody("[{\"url\":\"" + server.url("/" + url) + "\"}]"));
    }
    private void awaitPosted() throws Exception {
        assertNotNull("real HTTP request", server.takeRequest(5, TimeUnit.SECONDS));
        assertTrue("onResponse including App.post finished", completed.await(5, TimeUnit.SECONDS));
        Message message = ReflectionHelpers.getField(Looper.getMainLooper().getQueue(), "mMessages");
        boolean found = false;
        while (message != null) {
            if (message.getCallback() != null && message.getCallback().getClass().getName().contains("DanmakuApi")) found = true;
            message = ReflectionHelpers.getField(message, "next");
        }
        assertTrue("DanmakuApi callback really queued on main before replacement", found);
    }
    private static void drain() { ((ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle(); }

    public static class TestApp extends App { @Override public void onCreate() { Init.set(this); } }
    public static class TestVideo extends VideoActivity {
        RecordingManager manager;
        PendingModel model;
        @Override protected void initView(android.os.Bundle state) { }
        @Override protected void initEvent() { }
        @Override protected boolean customWall() { return false; }
        @Override protected String getPlaybackKey() { return manager == null ? "" : manager.getKey(); }
        @Override protected void onError(String msg) { }
        void ready() { onStateChanged(androidx.media3.common.Player.STATE_READY); }
        void destroyHost() { onDestroy(); }
        void stoppedHost() { onStop(); }
    }
    public static class PendingModel extends SiteViewModel {
        int requests;
        @Override public void playerContent(String key, String flag, String id) { requests++; }
    }
    public static class RecordingManager extends PlayerManager {
        final List<String> applied = new ArrayList<>();
        RecordingManager() { super(null); }
        @Override public void setDanmaku(Danmaku item) { if (!item.isEmpty()) applied.add(item.getUrl()); }
    }

    // Isolate unrelated artwork/history persistence; actual getPlayer/setPlayer and ownership remain real.
    @Implements(VideoActivity.class)
    public static class UiIsolation extends SurfaceIsolation {
        @Implementation protected void hideProgress() { }
        @Implementation protected void refreshLyrics() { }
        @Implementation protected void setQualityVisible(boolean visible) { }
        @Implementation protected void showProgress() { }
        @Implementation protected void applyPlaybackArtwork(Episode episode) { }
        @Implementation protected void applyAudioQueueMetadata(Episode episode) { }
        @Implementation protected void updateHistory(Episode episode) { }
        @Implementation protected void saveHistory(boolean forced) { }
    }
    @Implements(PlaybackActivity.class)
    public static class SurfaceIsolation extends org.robolectric.shadows.ShadowActivity { @Implementation protected void attachSurface() { } }
    @Implements(com.fongmi.android.tv.player.ParseJob.class)
    public static class ParseIsolation { @Implementation protected void execute(Result result) { } }

    @Implements(PlayerManager.class)
    public static class PrepareIsolation {
        @org.robolectric.annotation.RealObject PlayerManager manager;

        @Implementation protected void rebuildPlayer(boolean resetVideoSurface) { }
        @Implementation protected void setMediaItem(long timeout) { }
        @Implementation protected void prepareMpvOutputForNewItem() { }
        // Test-side stand-in for the native decode rebuild; the real playback trace contract still runs.
        @Implementation protected void toggleDecode() { beginFixtureTrace("fixture-toggle-decode"); }
        @Implementation protected void switchDecode(Result result, String key, MediaMetadata metadata, boolean useParse,
                long position, float speed, boolean repeat) { beginFixtureTrace("fixture-switch-decode-result"); }

        private void beginFixtureTrace(String reason) {
            ReflectionHelpers.callInstanceMethod(PlayerManager.class, manager, "beginPlaybackTrace",
                    ReflectionHelpers.ClassParameter.from(String.class, reason),
                    ReflectionHelpers.ClassParameter.from(boolean.class, false));
        }
    }
}
