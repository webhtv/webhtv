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
@Config(sdk = 28, application = TestApp.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DanmakuApiOwnershipTest {
    private MockWebServer server;
    private OkHttpClient client;
    @Before public void setup() throws Exception {
        server = new MockWebServer(); server.start();
        client = new OkHttpClient.Builder().build();
        ReflectionHelpers.setField(OkHttp.get(), "client", client);
        DanmakuSetting.putApiUrl(server.url("/danmaku").toString());
    }
    @After public void cleanup() throws Exception {
        client.dispatcher().cancelAll(); server.shutdown();
        client.connectionPool().evictAll(); client.dispatcher().executorService().shutdownNow();
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
    private static void drain() { ((ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle(); }
}
