package com.fongmi.android.tv.api;

import static org.junit.Assert.*;
import android.os.Looper;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest.TestApp;
import com.github.catvod.net.OkHttp;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = TestApp.class, shadows = DanmakuApiOwnershipTest.TitleStages.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DanmakuApiOwnershipTest {
    private MockWebServer server;
    private OkHttpClient client;
    @Before public void setup() throws Exception {
        server = new MockWebServer(); server.start();
        client = new OkHttpClient.Builder().build();
        ReflectionHelpers.setField(OkHttp.get(), "client", client);
        com.fongmi.android.tv.setting.Setting.putDanmakuMatchCache(new com.fongmi.android.tv.bean.DanmakuMatchCache());
        TitleStages.aiQueries = 0;
        DanmakuSetting.putApiUrl(server.url("/danmaku").toString());
    }
    @After public void cleanup() throws Exception {
        DanmakuApi.cancel();
        client.dispatcher().cancelAll(); client.connectionPool().evictAll(); server.shutdown();
         client.dispatcher().executorService().shutdownNow();
    }

    @Test public void oldOwnerCallCancellationCannotCancelNewAutomaticOwner() throws Exception {
        CountDownLatch aReceived = new CountDownLatch(1), releaseA = new CountDownLatch(1), idle = new CountDownLatch(1);
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getBody().readUtf8().contains("name=A")) {
                    aReceived.countDown();
                    if (!releaseA.await(5, TimeUnit.SECONDS)) return new MockResponse().setResponseCode(500);
                }
                return new MockResponse().setBody("[{\"url\":\"" + server.url("/current.xml") + "\"}]");
            }
        });
        List<String> found = new ArrayList<>();
        Call old = DanmakuApi.searchOwned("A", "1", "owner-A", item -> found.add("old"));
        assertTrue(aReceived.await(5, TimeUnit.SECONDS));
        client.dispatcher().setIdleCallback(idle::countDown);
        Call current = DanmakuApi.searchOwned("B", "2", "owner-B", item -> found.add(item.getUrl()));
        old.cancel();
        releaseA.countDown();
        assertTrue(idle.await(5, TimeUnit.SECONDS));
        assertFalse(current.isCanceled());
        drain();
        assertEquals(Collections.singletonList(server.url("/current.xml").toString()), found);
    }

    @Test public void ownedQueryDoesNotCancelExistingManualCallButManualCleanupCancelsOwnedCall() throws Exception {
        CountDownLatch received = new CountDownLatch(1), release = new CountDownLatch(1), idle = new CountDownLatch(1);
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getBody().readUtf8().contains("name=manual")) {
                    received.countDown(); release.await(5, TimeUnit.SECONDS);
                }
                return new MockResponse().setBody("[{\"url\":\"" + server.url("/owned.xml") + "\"}]");
            }
        });
        Call manual = DanmakuApi.newCall("manual", "1");
        manual.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { }
            @Override public void onResponse(Call call, Response response) { response.close(); }
        });
        assertTrue(received.await(5, TimeUnit.SECONDS));
        List<String> found = new ArrayList<>();
        Call owned = DanmakuApi.searchOwned("owned", "2", "owner", item -> found.add(item.getUrl()));
        assertFalse("auto newCall must not call global cancel(TAG)", manual.isCanceled());
        client.dispatcher().setIdleCallback(idle::countDown);
        DanmakuApi.cancel();
        assertTrue(manual.isCanceled());
        assertTrue("manual cleanup must also cancel the in-flight automatic owned call", owned.isCanceled());
        release.countDown();
        assertTrue(idle.await(5, TimeUnit.SECONDS));
        drain();
        assertTrue("canceled owned call must not deliver a result", found.isEmpty());
    }
    @Test public void pageScopedCleanupCancelsOnlyItsOwnAutomaticCall() throws Exception {
        CountDownLatch aReceived = new CountDownLatch(1), releaseA = new CountDownLatch(1);
        CountDownLatch bReceived = new CountDownLatch(1), releaseB = new CountDownLatch(1), idle = new CountDownLatch(1);
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().readUtf8();
                if (body.contains("name=A")) {
                    aReceived.countDown(); releaseA.await(5, TimeUnit.SECONDS);
                } else if (body.contains("name=B")) {
                    bReceived.countDown(); releaseB.await(5, TimeUnit.SECONDS);
                }
                return new MockResponse().setBody("[{\"url\":\"" + server.url("/owner.xml") + "\"}]");
            }
        });
        List<String> found = new ArrayList<>();
        Call a = DanmakuApi.searchOwned("A", "1", "owner-A", item -> found.add("A"));
        assertTrue(aReceived.await(5, TimeUnit.SECONDS));
        Call b = DanmakuApi.searchOwned("B", "2", "owner-B", item -> found.add(item.getUrl()));
        assertTrue(bReceived.await(5, TimeUnit.SECONDS));
        client.dispatcher().setIdleCallback(idle::countDown);
        DanmakuApi.cancel("owner-A");
        assertTrue("page scoped cleanup must cancel its own automatic call", a.isCanceled());
        assertFalse("page scoped cleanup must not cancel another owner in-flight call", b.isCanceled());
        releaseA.countDown();
        releaseB.countDown();
        assertTrue(idle.await(5, TimeUnit.SECONDS));
        drain();
        assertEquals(Collections.singletonList(server.url("/owner.xml").toString()), found);
    }
    @Test public void pageCancellationAlsoRejectsAlreadyPostedSimpleOwnedResult() throws Exception {
        server.enqueue(new MockResponse().setBody("[{\"url\":\"" + server.url("/posted.xml") + "\"}]"));
        CountDownLatch idle = new CountDownLatch(1); client.dispatcher().setIdleCallback(idle::countDown);
        List<String> found = new ArrayList<>();
        Call call = DanmakuApi.searchOwned("posted", "1", "posted-owner", item -> found.add(item.getUrl()));
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)); assertTrue(idle.await(5, TimeUnit.SECONDS));
        DanmakuApi.cancel("posted-owner"); drain();
        assertTrue(call.isCanceled()); assertTrue(found.isEmpty());
    }

    private static com.fongmi.android.tv.title.MediaTitleRequest titleRequest() {
        return com.fongmi.android.tv.title.MediaTitleRequest.builder().siteKey("site").vodId("vod")
                .rawTitle("Original").episodeName("Episode 1").allowAi(true).build();
    }

    @Test public void fullOwnedTitleChainKeepsManualMemoryOriginalCleanAndAiOrder() throws Exception {
        com.fongmi.android.tv.bean.DanmakuMatchCache cache = new com.fongmi.android.tv.bean.DanmakuMatchCache();
        cache.putSeries("site", "vod", "manual", "Original", com.fongmi.android.tv.bean.Danmaku.from("https://example.test/manual.xml"));
        com.fongmi.android.tv.setting.Setting.putDanmakuMatchCache(cache);
        for (int i = 0; i < 4; i++) server.enqueue(new MockResponse().setBody("[]"));
        server.enqueue(new MockResponse().setBody("[{\"url\":\"" + server.url("/ai.xml") + "\"}]"));
        CountDownLatch idle = new CountDownLatch(1); client.dispatcher().setIdleCallback(idle::countDown);
        List<String> found = new ArrayList<>();
        DanmakuApi.searchOwned(titleRequest(), "chain", item -> found.add(item.getUrl()));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            HttpUrl form = HttpUrl.parse("https://fixture.test/?" + request.getBody().readUtf8());
            names.add(form.queryParameter("name"));
            assertEquals("Episode 1", form.queryParameter("episode"));
        }
        assertTrue(idle.await(5, TimeUnit.SECONDS)); drain();
        assertEquals(Arrays.asList("manual", "original1", "original2", "clean", "ai"), names);
        assertEquals(Collections.singletonList(server.url("/ai.xml").toString()), found);
        assertEquals(1, TitleStages.aiQueries);
    }

    @Test public void cancellationAtEveryTitleStageCannotLaunchFallbackAndNewOwnerStillWorks() throws Exception {
        for (String stage : Arrays.asList("original1", "original2", "clean", "ai")) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), idle = new CountDownLatch(1);
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    if (request.getBody().readUtf8().contains("name=" + stage)) {
                        entered.countDown(); release.await(5, TimeUnit.SECONDS);
                        return new MockResponse().setResponseCode(500).setBody("[]");
                    }
                    return new MockResponse().setBody("[]");
                }
            });
            List<String> found = new ArrayList<>();
            DanmakuApi.OwnedSearch query = DanmakuApi.searchOwned(titleRequest(), "cancel-chain", item -> found.add(item.getUrl()));
            assertTrue(stage, entered.await(5, TimeUnit.SECONDS));
            int count = server.getRequestCount();
            client.dispatcher().setIdleCallback(idle::countDown);
            query.cancel(); release.countDown(); assertTrue(idle.await(5, TimeUnit.SECONDS)); drain();
            assertEquals("no fallback may be resurrected after " + stage, count, server.getRequestCount());
            assertTrue(found.isEmpty());
        }
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setBody("[{\"url\":\"" + server.url("/new.xml") + "\"}]"); }
        });
        CountDownLatch idle = new CountDownLatch(1); client.dispatcher().setIdleCallback(idle::countDown);
        List<String> found = new ArrayList<>(); DanmakuApi.searchOwned(titleRequest(), "fresh", item -> found.add(item.getUrl()));
        assertTrue(idle.await(5, TimeUnit.SECONDS)); drain();
        assertEquals(Collections.singletonList(server.url("/new.xml").toString()), found);
    }

    @Test public void cachedPostedOwnedResultCanBeCanceledWithoutCancelingAnotherPage() {
        com.fongmi.android.tv.bean.DanmakuMatchCache cache = new com.fongmi.android.tv.bean.DanmakuMatchCache();
        cache.put("site", "vod", "Episode 1", "Original", "Original", com.fongmi.android.tv.bean.Danmaku.from("https://example.test/cached.xml"));
        com.fongmi.android.tv.setting.Setting.putDanmakuMatchCache(cache);
        List<String> found = new ArrayList<>();
        DanmakuApi.searchOwned(titleRequest(), "cache-A", item -> found.add("A"));
        DanmakuApi.searchOwned(titleRequest(), "cache-B", item -> found.add("B"));
        DanmakuApi.cancel("cache-A"); drain();
        assertEquals(Collections.singletonList("B"), found); assertEquals(0, server.getRequestCount());
    }

    @org.robolectric.annotation.Implements(com.fongmi.android.tv.title.MediaTitleResolver.class)
    public static class TitleStages {
        static int aiQueries;
        @org.robolectric.annotation.Implementation protected List<String> queryTitles(com.fongmi.android.tv.title.MediaTitleRequest request, int limit) { return new ArrayList<>(Arrays.asList("original1", "original2")); }
        @org.robolectric.annotation.Implementation protected List<String> queryCleanedTitles(com.fongmi.android.tv.title.MediaTitleRequest request, int limit) { return new ArrayList<>(Collections.singletonList("clean")); }
        @org.robolectric.annotation.Implementation protected List<String> queryAiFallbackTitles(com.fongmi.android.tv.title.MediaTitleRequest request, int limit) { aiQueries++; return new ArrayList<>(Collections.singletonList("ai")); }
    }

    private static void drain() { ((ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle(); }
}
