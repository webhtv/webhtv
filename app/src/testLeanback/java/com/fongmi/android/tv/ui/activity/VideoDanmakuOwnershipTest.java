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
                VideoDanmakuOwnershipTest.PrepareIsolation.class, VideoDanmakuOwnershipTest.ParseIsolation.class})
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
        @Implementation protected void rebuildPlayer(boolean resetVideoSurface) { }
        @Implementation protected void setMediaItem(long timeout) { }
        @Implementation protected void prepareMpvOutputForNewItem() { }
    }
}
