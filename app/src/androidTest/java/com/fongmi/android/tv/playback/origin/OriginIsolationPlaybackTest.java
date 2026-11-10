package com.fongmi.android.tv.playback.origin;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.app.Instrumentation;
import androidx.test.platform.app.InstrumentationRegistry;
import android.view.KeyEvent;
import android.widget.TextView;

import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.node.NodeRuntime;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.TmdbDetailActivity;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Prefers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

import fi.iki.elonen.NanoHTTPD;

/** One installed mobileArm64_v8a debug APK; real workers and HTTP, never success-by-BLOCKED. */
public class OriginIsolationPlaybackTest extends junit.framework.TestCase {
    private Instrumentation getInstrumentation() { return InstrumentationRegistry.getInstrumentation(); }
    private static final String KEY = "origin_fixture";
    private static final String VOD = "origin-vod";
    private static final String FIXTURE_PREFS = "origin-fixture-private";
    private File fixturePreferenceFile;
    private boolean fixturePreferencesExisted;
    private byte[] originalFixturePreferenceBytes;
    private Map<String, Object> originalFixturePreferenceValues;
    private Context context;
    private File root;
    private FixtureServer upstream;
    private Config originalConfig;
    private List<Config> originalConfigs;
    private Map<String, Object> originalPreferences;
    private final List<Config> ownedConfigs = new ArrayList<>();
    private final List<History> ownedHistory = new ArrayList<>();
    private final List<OriginSessionClient> clients = new ArrayList<>();
    private final List<BindingContext> bindings = new ArrayList<>();
    private Activity activity;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        fixturePreferenceFile = new File(new File(context.getApplicationInfo().dataDir, "shared_prefs"), FIXTURE_PREFS + ".xml");
        fixturePreferencesExisted = fixturePreferenceFile.isFile();
        originalFixturePreferenceBytes = fixturePreferencesExisted ? Files.readAllBytes(fixturePreferenceFile.toPath()) : null;
        originalFixturePreferenceValues = copyPreferences(context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE));
        boolean unexpectedFixtureContent = originalFixturePreferenceValues.keySet().stream().anyMatch(key -> !key.equals("account"))
                || originalFixturePreferenceValues.containsKey("account") && !Arrays.asList("alice", "bob").contains(originalFixturePreferenceValues.get("account"));
        System.out.println("ORIGIN_FIXTURE_PREFS_BEFORE existed=" + fixturePreferencesExisted + " keys="
                + originalFixturePreferenceValues.keySet() + " unexpectedContent=" + unexpectedFixtureContent);
        originalConfig = Config.objectFrom(App.gson().toJson(VodConfig.get().getConfig()));
        originalConfigs = new ArrayList<>();
        for (Config c : AppDatabase.get().getConfigDao().findAll()) originalConfigs.add(Config.objectFrom(App.gson().toJson(c)));
        originalPreferences = copyPreferences(Prefers.getPrefers());
        root = new File(context.getNoBackupFilesDir(), "origin-isolation-test/" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        copyAssets("origin-isolation", root);
        File input = new File(context.getExternalFilesDir(null), "origin-isolation-input");
        File media = new File(input, "media-fixture");
        if (media.isDirectory()) copyDirectory(media, new File(root, "media-fixture"));
        File jar = new File(input, "origin-fixture.jar");
        if (jar.isFile()) Files.copy(jar.toPath(), new File(root, "runtime/origin-fixture.jar").toPath());
        upstream = new FixtureServer(root);
        upstream.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null) main(activity::finish);
            for (OriginSessionClient client : clients) client.close();
            for (BindingContext binding : bindings) binding.unbindRemaining();
            // 用户原配置可能是当前网络不可达的远程地址：这里只恢复数据库行与持久化选择，
            // 不在离线的验收环境里强制联网加载，否则会把环境不可达误报成测试失败。
            restorePreferences(Prefers.getPrefers(), originalPreferences);
            for (History h : ownedHistory) AppDatabase.get().getHistoryDao().delete(h.getCid(), h.getKey());
            for (Config c : ownedConfigs) AppDatabase.get().getConfigDao().delete(c.getUrl(), 0);
        } finally {
            try {
                restoreFixturePreferences();
            } finally {
                if (upstream != null) upstream.stop();
                if (root != null) deleteOwned(root, root.getCanonicalFile());
                super.tearDown();
            }
        }
    }

    private void restoreFixturePreferences() throws Exception {
        if (fixturePreferenceFile == null) return;
        File expected = new File(new File(context.getApplicationInfo().dataDir, "shared_prefs"), FIXTURE_PREFS + ".xml");
        assertEquals("restore may touch only this exact fixture preference file", expected.getCanonicalPath(), fixturePreferenceFile.getCanonicalPath());
        if (fixturePreferencesExisted) {
            restorePreferences(context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE), originalFixturePreferenceValues);
            Files.write(fixturePreferenceFile.toPath(), originalFixturePreferenceBytes);
            assertTrue("pre-existing fixture preference bytes must be restored exactly", Arrays.equals(originalFixturePreferenceBytes, Files.readAllBytes(fixturePreferenceFile.toPath())));
            assertEquals(originalFixturePreferenceValues, copyPreferences(context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE)));
        } else {
            context.deleteSharedPreferences(FIXTURE_PREFS);
            assertFalse("a previously absent fixture preference file must be removed", fixturePreferenceFile.exists());
            assertFalse("no new fixture preference backup may remain", new File(fixturePreferenceFile.getPath() + ".bak").exists());
        }
        System.out.println("ORIGIN_FIXTURE_PREFS_RESTORED existed=" + fixturePreferencesExisted);
    }

    public void testSingleMobileRuntimeSequenceRequiresRealOriginPlayback() throws Exception {
        assertTrue("prepare-fixtures command must supply the locally compiled Dex Jar", new File(root, "runtime/origin-fixture.jar").isFile());
        assertTrue("prepare-fixtures command must supply real AVC HLS, not the old WAV/map placeholder", new File(root, "media-fixture/preview.mp4").isFile());
        verifyRealVideo();
        List<String> runtimes = Arrays.asList("jar", "js", "py", "node");
        String selectedRuntime = InstrumentationRegistry.getArguments().getString("originRuntime");
        if (selectedRuntime != null) {
            assertTrue("requested runtime must be one of the four covered source types", runtimes.contains(selectedRuntime));
            runtimes = Collections.singletonList(selectedRuntime);
        }
        for (String runtime : runtimes) {
            Config a = config(runtime, "alice"), b = config(runtime, "bob");
            load(a);
            assertEquals(a.getId(), VodConfig.getCid());
            assertEquals("alice", SiteApi.detailContent(KEY, VOD).getVod().getName().split(":")[0]);
            String currentInstance = identity(runtime);
            History h = history(b, "/episode/2");
            MainState baseline = new MainState(h);
            BindingContext binding = new BindingContext(context);
            bindings.add(binding);
            Events events = new Events();
            OriginSessionClient client = OriginSessionClient.open(binding, h, events);
            clients.add(client);
            assertEquals(runtime + " open must be READY (BLOCKED/UNAVAILABLE is a failure): " + events.details(), "READY", events.take());
            assertEquals(b.getId(), events.envelope.getInt("originCid"));
            assertEquals(h.getKey(), events.envelope.getString("sourceHistoryKey"));
            assertTrue("actual private relay port is required", events.envelope.getInt("proxyPort") > 0);
            File privateRoot = new File(context.getNoBackupFilesDir(), "origin/" + client.leaseId());
            assertTrue("worker must have created a lease-private directory", privateRoot.isDirectory());
            assertFalse("private lease is not the main files dir", privateRoot.getCanonicalPath().equals(context.getFilesDir().getCanonicalPath()));
            assertTrue("a real separate worker must be alive", processId(context.getPackageName() + ":origin") > 0);
            client.play(h.getEpisodeUrl(), "第2集", "0");
            assertEquals(runtime + " original detail/player must be PLAY_READY: " + events.details(), "PLAY_READY", events.take());
            assertNotNull(events.playback);
            assertTrue("selected original second episode must survive", events.playback.episode.contains("2"));
            assertEquals(h.getPosition(), events.playback.positionMs);
            Result play = Result.fromJson(events.playback.result);
            assertTrue("the player must receive only the lease loopback URL", play.getRealUrl().startsWith("http://127.0.0.1:" + events.envelope.getInt("proxyPort") + "/origin/"));
            assertEquals("bob", new JSONObject(events.playback.vod).getString("vod_name").split(":")[0]);
            assertEquals("the current same-key runtime instance must survive", currentInstance, identity(runtime));
            baseline.assertUnchanged(h);
            assertHlsThroughRelay(play.getRealUrl());
            if (runtime.equals("node")) assertOriginNode(events.envelope, baseline.nodePid, client.leaseId());
            client.close();
            awaitWorkerGone();
            assertUnreachable(play.getRealUrl());
            exerciseActivity(h, baseline, runtime);
            upstream.assertNoAliceOnBobResources();
            assertEquals(currentInstance, identity(runtime));
            System.out.println("ORIGIN_RUNTIME_OK " + runtime + " cid=" + b.getId());
        }
    }

    public void testTmdbDetailModeRoutesCrossSourceHistoryIntoTmdbDetailOnOriginLines() throws Exception {
        Config a = config("jar", "alice"), b = config("jar", "bob");
        load(a);
        History h = history(b, "/episode/2");
        h.setTmdbId(603);
        h.setMediaType("tv");
        AppDatabase.get().getHistoryDao().insertOrUpdate(h);
        int originalMode = Setting.getDetailOpenMode();
        try {
            // 分流谓词必须按真实设置给出"进 TMDB 详情页"，并带上原历史的 TMDB 身份。
            Setting.putDetailOpenMode(Setting.DETAIL_OPEN_ENHANCED);
            // 基线必须在本用例自己的设置改动之后取：改详情页模式本身就是本用例的动作，不是产品污染。
            MainState baseline = new MainState(h);
            assertEquals("TMDB 详情模式下跨源历史必须走详情页",
                    OriginHistoryRoute.TMDB_DETAIL,
                    OriginHistoryRoute.describe(Setting.isTmdbDetailModeConfigured(),
                            Setting.isStandaloneTmdbDetailMode(Setting.getDetailOpenMode()), h.getTmdbId(), h.getMediaType()));
            assertEquals("非 TMDB 模式必须退化为直接播放",
                    OriginHistoryRoute.DIRECT_MODE,
                    OriginHistoryRoute.describe(Setting.isTmdbDetailModeConfigured(), false, h.getTmdbId(), h.getMediaType()));
            assertEquals("没有 TMDB 身份必须退化",
                    OriginHistoryRoute.DIRECT_IDENTITY,
                    OriginHistoryRoute.describe(true, true, 0, ""));

            upstream.failSegments = false;
            Intent intent = TmdbDetailActivity.originHistoryIntent(context, h).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = getInstrumentation().startActivitySync(intent);
            assertEquals("跨源历史在炫彩详情模式下必须进入 TMDB 详情页", TmdbDetailActivity.class.getName(), activity.getClass().getName());
            // 详情页的线路/集数来自原配置 worker：外壳必须显示 bob 的第 2 集，而不是当前配置 alice 的内容。
            AtomicReference<String> shell = new AtomicReference<>("");
            waitUntil(() -> { main(() -> shell.set(allText(activity))); return shell.get().contains("bob") && shell.get().contains("第2集"); },
                    30000, "TMDB detail must render the original source lines/episodes, got: " + shell.get());
            assertFalse("原源详情页不得混入当前配置的站源 chip，got: " + shell.get(), shell.get().contains("alice"));
            assertEquals(b.getId(), h.getCid());
            baseline.assertUnchanged(h);

            // 点播放：取流来自原配置 worker 的 relay，仍由同一个内联播放器真实起播。
            int playId = context.getResources().getIdentifier("play", "id", context.getPackageName());
            assertTrue("the TMDB detail page has a play control", playId != 0);
            main(() -> activity.findViewById(playId).performClick());
            AtomicReference<Player> player = new AtomicReference<>();
            waitUntil(() -> { main(() -> { PlayerView view = activity.findViewById(R.id.exo); if (view != null) player.set(view.getPlayer()); }); return player.get() != null; },
                    60000, "the inline player must attach after the original-source play request");
            waitUntil(() -> { AtomicBoolean good = new AtomicBoolean(); main(() -> good.set(player.get().getVideoSize().width > 0 && player.get().getCurrentPosition() > 0)); return good.get(); },
                    45000, "original-source TMDB playback must render a real first frame");
            AtomicReference<Bitmap> frame = new AtomicReference<>();
            waitUntil(() -> { Bitmap shot = getInstrumentation().getUiAutomation().takeScreenshot(); if (shot == null) return false; frame.set(shot); return coversVideoCenter(shot); }, 15000, "TMDB inline playback must show decoded pixels");
            if (frame.get() != null) frame.get().recycle();
            baseline.assertUnchanged(h);
            main(activity::finish);
            waitUntil(() -> processId(context.getPackageName() + ":origin") <= 0, 20000, "leaving the TMDB detail page must release the origin lease");
            History saved = History.find(b.getId(), h.getKey());
            assertNotNull(saved);
            assertEquals(b.getId(), saved.getCid());
            assertTrue("origin progress must be written back to the original cid", saved.getPosition() > 0);
            assertNull("no current-config record may be created", History.find(a.getId(), h.getKey()));
            activity = null;
            System.out.println("ORIGIN_TMDB_DETAIL_OK cid=" + b.getId() + " saved=" + saved.getPosition());
        } finally {
            Setting.putDetailOpenMode(originalMode);
        }
    }

    public void testPrePlayerFailureUsesGlobalAutoSearchAndOff() throws Exception {
        Config a = config("jar", "alice"), b = config("jar", "bob");
        load(a);
        History h = history(b, "/episode/2");
        String targetKey = KEY + AppDatabase.SYMBOL + VOD + AppDatabase.SYMBOL + a.getId();
        ownedHistory.add(h.forPlaybackKey(targetKey, a.getId()));
        upstream.blockBobPlayer = true;
        upstream.matchCurrent = true;
        for (int mode : new int[]{Setting.GLOBAL_HISTORY_AUTO, Setting.GLOBAL_HISTORY_SEARCH, Setting.GLOBAL_HISTORY_OFF}) {
            Setting.putGlobalHistoryMode(mode);
            upstream.searches.set(0);
            upstream.alicePlays.set(0);
            MainState before = new MainState(h);
            Activity originPage = getInstrumentation().startActivitySync(
                    VideoActivity.originHistoryIntent(context, h).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = originPage;
            String targetClass = mode == Setting.GLOBAL_HISTORY_SEARCH ? SearchActivity.class.getName() : VideoActivity.class.getName();
            Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(targetClass, null, false);
            getInstrumentation().addMonitor(monitor);
            try {
                AtomicReference<String> message = new AtomicReference<>("");
                waitUntil(() -> {
                    main(() -> message.set(allText(originPage)));
                    return message.get().contains("原源播放失败") && message.get().contains("ISOLATION_ERROR");
                }, 15000, "the pre-player worker failure must remain visibly explained on the normal shell");
                assertFalse("do not finish the origin page before global matching has launched", originPage.isFinishing());
                assertEquals("the explanation must appear before matching begins", 0, upstream.searches.get());
                before.assertUnchanged(h);
                if (mode == Setting.GLOBAL_HISTORY_OFF) {
                    long visibleSince = SystemClock.elapsedRealtime();
                    waitUntil(() -> SystemClock.elapsedRealtime() - visibleSince >= 2500, 4000, "observe the disabled-mode decision");
                    assertFalse("global resume OFF must stay on the explained origin failure", originPage.isFinishing());
                    assertEquals(0, upstream.searches.get());
                    main(() -> message.set(allText(originPage)));
                    assertTrue(message.get().contains("全局历史续播已关闭"));
                    before.assertUnchanged(h);
                } else {
                    Activity target = monitor.waitForActivityWithTimeout(30000);
                    assertNotNull("pre-player failure must launch the configured global-resume entry, mode=" + mode, target);
                    activity = target;
                    assertFalse("the target must no longer carry an origin-session intent", OriginHistoryPlayback.hasPayload(target.getIntent()));
                    assertEquals("the main configuration never switches", a.getId(), VodConfig.getCid());
                    waitUntil(() -> upstream.searches.get() > 0, 15000, "both global modes must actually search current sources");
                    if (mode == Setting.GLOBAL_HISTORY_AUTO) {
                        waitUntil(() -> upstream.alicePlays.get() > 0, 15000, "auto matching must reach the current-source player, not discard its async result");
                        assertEquals(b.getId(), target.getIntent().getIntExtra("resume_history_cid", -1));
                    } else {
                        assertEquals(b.getId(), target.getIntent().getIntExtra("historyResumeCid", -1));
                        assertEquals(a.getId(), target.getIntent().getIntExtra("historyResumeTargetCid", -1));
                        assertEquals(h.getKey(), target.getIntent().getStringExtra("historyResumeKey"));
                    }
                    main(target::finish);
                    waitUntil(originPage::isFinishing, 10000, "finish the failed origin shell only after a target is launched");
                }
                main(originPage::finish);
                getInstrumentation().waitForIdleSync();
                awaitWorkerGone();
                activity = null;
                System.out.println("ORIGIN_PREPLAYER_FALLBACK_OK mode=" + mode + " searches=" + upstream.searches.get());
            } finally {
                getInstrumentation().removeMonitor(monitor);
                main(originPage::finish);
            }
        }
    }

    public void testRealRelayResponsesPreserveBobHlsKeyMapRedirectAndRange206() throws Exception {
        String lease = UUID.randomUUID().toString(), foreign = UUID.randomUUID().toString();
        OriginMediaRelay.Endpoint endpoint = OriginMediaRelay.begin(lease);
        OriginMediaRelay.begin(foreign);
        try {
            String id = OriginMediaRelay.register(lease, upstream.base() + "/redirect", headers("bob"));
            String url = OriginMediaRelay.localUrl(lease, id);
            assertHlsThroughRelay(url);
            assertEquals(403, fetch("http://127.0.0.1:" + endpoint.port + OriginMediaRelay.localPath(foreign, id), Map.of()).status);
            assertEquals(404, fetch("http://127.0.0.1:" + endpoint.port + OriginMediaRelay.localPath(lease, "unregistered"), Map.of()).status);
            upstream.assertNoAliceOnBobResources();
            OriginMediaRelay.close(lease);
            assertFalse(OriginMediaRelay.isBound(lease));
            assertUnreachable(url);
            assertTrue(OriginMediaRelay.isBound(foreign));
        } finally { OriginMediaRelay.close(lease); OriginMediaRelay.close(foreign); }
    }

    public void testCancelledForeignAndLateClientRepliesCloseFdWithoutFallback() throws Exception {
        History h = history(config("js", "bob"), "/episode/2");
        InjectedBinding adapter = new InjectedBinding(context);
        Events events = new Events();
        OriginSessionClient client = OriginSessionClient.open(adapter, h, events);
        clients.add(client);
        waitUntil(() -> adapter.request != null, 15000, "the client must have sent its request through the public Binder port");
        for (String kind : new String[]{"foreign", "generation"}) {
            Bundle envelope = new Bundle(adapter.request);
            envelope.putString("status", OriginSessionContract.UNAVAILABLE);
            envelope.putString("reason", OriginSessionContract.CONFIG_MISSING);
            if (kind.equals("foreign")) envelope.putString("leaseId", UUID.randomUUID().toString());
            else envelope.putLong("generation", adapter.request.getLong("generation") - 1);
            ParcelFileDescriptor fd = descriptor("{\"vod\":\"late\"}");
            FileDescriptor observed = fd.getFileDescriptor();
            adapter.reply.complete(envelope, fd);
            waitUntil(() -> !observed.valid(), 10000, "rejected " + kind + " FD must close");
        }
        client.cancel();
        Bundle cancelled = new Bundle(adapter.request);
        cancelled.putString("status", OriginSessionContract.UNAVAILABLE);
        cancelled.putString("reason", OriginSessionContract.CONFIG_MISSING);
        ParcelFileDescriptor fd = descriptor("{\"vod\":\"cancelled\"}");
        FileDescriptor observed = fd.getFileDescriptor();
        adapter.reply.complete(cancelled, fd);
        assertFalse(observed.valid());
        getInstrumentation().waitForIdleSync();
        assertEquals("none of these discarded callbacks may invoke fallback", 0, events.unavailable.get());
        assertTrue("no success/block/error callback may be delivered either", events.queue.isEmpty());
    }

    public void testChangedResumeOrConfigRevisionDiscardsValidGenerationReply() throws Exception {
        for (boolean changeResume : new boolean[]{true, false}) {
            Config b = config(changeResume ? "js" : "py", "bob");
            History h = history(b, "/episode/2");
            InjectedBinding adapter = new InjectedBinding(context);
            Events events = new Events();
            OriginSessionClient client = OriginSessionClient.open(adapter, h, events);
            clients.add(client);
            waitUntil(() -> adapter.request != null, 15000, "the client must have sent its request through the public Binder port");
            if (changeResume) {
                h.setPosition(h.getPosition() + 1000);
                AppDatabase.get().getHistoryDao().insertOrUpdate(h);
            } else {
                b.setTime(b.getTime() + 1000);
                AppDatabase.get().getConfigDao().update(b);
            }
        Bundle stale = new Bundle(adapter.request);
            stale.putString("status", OriginSessionContract.UNAVAILABLE);
            stale.putString("reason", OriginSessionContract.CONFIG_MISSING);
            ParcelFileDescriptor fd = descriptor("{\"vod\":\"stale-revision\"}");
            FileDescriptor observed = fd.getFileDescriptor();
            adapter.reply.complete(stale, fd);
            waitUntil(() -> !observed.valid(), 10000, "obsolete snapshot FD must close");
            getInstrumentation().waitForIdleSync();
            assertEquals("changed origin config or resume snapshot is stale, not unavailable", 0, events.unavailable.get());
            assertTrue("stale valid-generation response must not launch playback", events.queue.isEmpty());
            client.close();
        }
    }

    public void testBusyLeaseIsBlockedAndCannotCloseTheFirstOwnersLease() throws Exception {
        Config a = config("js", "alice"), b = config("js", "bob"); load(a);
        History h = history(b, "/episode/2");
        BindingContext firstBinding = new BindingContext(context); bindings.add(firstBinding);
        Events first = new Events(); OriginSessionClient firstClient = OriginSessionClient.open(firstBinding, h, first); clients.add(firstClient);
        assertEquals("first actual lease must be READY", "READY", first.take());
        BindingContext secondBinding = new BindingContext(context); bindings.add(secondBinding);
        Events second = new Events(); OriginSessionClient secondClient = OriginSessionClient.open(secondBinding, h, second); clients.add(secondClient);
        assertEquals("held worker cannot change config/owner", "BLOCKED", second.take());
        assertEquals(OriginSessionContract.ISOLATION_ERROR, second.reason);
        assertEquals(0, second.unavailable.get());
        secondClient.close();
        firstClient.play(h.getEpisodeUrl(), "第2集", "0");
        assertEquals("a refused second owner cannot close the original owner", "PLAY_READY", first.take());
        firstClient.close();
        awaitWorkerGone();
    }

    public void testBinderSchemaSizeLargeFdAndPrimitiveGenerationsRoundTrip() throws Exception {
        Bundle envelope = new Bundle(); envelope.putInt("version", 1);
        OriginSessionContract.validate(envelope);
        for (boolean version : new boolean[]{true, false}) {
            Bundle bad = new Bundle(envelope);
            if (version) bad.putInt("version", 2); else bad.putString("padding", repeat('x', 65536));
            try { OriginSessionContract.validate(bad); fail("wrong version / >64KiB control must fail closed"); }
            catch (IllegalArgumentException expected) { assertEquals(OriginSessionContract.SNAPSHOT_INVALID, expected.getMessage()); }
        }
        String json = "{\"data\":\"" + repeat('字', 40000) + "\"}";
        AtomicReference<String> received = new AtomicReference<>();
        AtomicLong cancelled = new AtomicLong(), closed = new AtomicLong();
        OriginSessionContract.OriginSessionPort.Stub server = new OriginSessionContract.OriginSessionPort.Stub() {
            public void open(Bundle b, ParcelFileDescriptor fd, IBinder owner, OriginSessionContract.OriginReply reply) {
                assertNotNull(owner); received.set(readFd(fd));
            }
            public void play(Bundle b, OriginSessionContract.OriginReply reply) {}
            public void cancel(long generation) { cancelled.set(generation); }
            public void close(String lease, long generation) { assertEquals("lease", lease); closed.set(generation); }
        };
        OriginSessionContract.OriginSessionPort port = OriginSessionContract.OriginSessionPort.Stub.asInterface(bridge(server));
        ParcelFileDescriptor input = descriptor(json);
        try {
            port.open(envelope, input, new Binder(), new OriginSessionContract.OriginReply.Stub() { public void complete(Bundle b, ParcelFileDescriptor fd) { OriginSessionContract.closeFd(fd); } });
            port.cancel(9876543210L); port.close("lease", 9876543211L);
            assertEquals("large JSON goes through the FD, not the small control message", json, received.get());
            assertEquals(9876543210L, cancelled.get()); assertEquals(9876543211L, closed.get());
        } finally { input.close(); }
    }

    public void testLateCloseDoesNotKillCurrentLeaseAndDeathNeverFallsBack() throws Exception {
        Config a = config("js", "alice"), b = config("js", "bob"); load(a);
        History h = history(b, "/episode/2");
        BindingContext binding = new BindingContext(context); bindings.add(binding);
        Events events = new Events(); OriginSessionClient client = OriginSessionClient.open(binding, h, events); clients.add(client);
        assertEquals("READY", events.take());
        OriginSessionContract.OriginSessionPort port = OriginSessionContract.OriginSessionPort.Stub.asInterface(binding.binder);
        long generation = events.envelope.getLong("generation");
        port.close(client.leaseId(), generation - 1);
        client.play(h.getEpisodeUrl(), "第2集", "0");
        assertEquals("stale close must not kill an active newer generation", "PLAY_READY", events.take());
        int worker = processId(context.getPackageName() + ":origin");
        int node = processId(context.getPackageName() + ":node");
        assertTrue(worker > 0 && worker != android.os.Process.myPid());
        android.os.Process.killProcess(worker);
        awaitWorkerGone();
        assertEquals("worker death is infrastructure, never UNAVAILABLE", "BLOCKED", events.take());
        assertEquals(OriginSessionContract.WORKER_DIED, events.reason);
        assertEquals(0, events.unavailable.get());
        assertEquals("current Node must not be stopped by origin death", node, processId(context.getPackageName() + ":node"));
    }

    public void testOriginProgressKeepsCidAndCannotResurrectMissingHistory() throws Exception {
        Config a = config("js", "alice"), b = config("js", "bob"); load(a);
        History h = history(b, "/episode/2"); MainState before = new MainState(h);
        h.saveOriginProgress(5432, 32000);
        History saved = History.find(b.getId(), h.getKey());
        assertNotNull(saved); assertEquals(5432, saved.getPosition()); assertEquals(b.getId(), saved.getCid());
        before.assertUnchanged(h);
        AppDatabase.get().getHistoryDao().delete(b.getId(), h.getKey());
        h.saveOriginProgress(6000, 32000);
        assertNull("missing source record must not be recreated", History.find(b.getId(), h.getKey()));
        assertNull("progress must never create a current-cid copy", History.find(a.getId(), h.getKey()));
    }

    private void exerciseActivity(History h, MainState baseline, String runtime) throws Exception {
        upstream.failSegments = false;
        // 正常播放页入口：跨配置历史与当前源历史共用同一个外壳，只有数据来源是原配置 worker。
        Intent intent = com.fongmi.android.tv.ui.activity.VideoActivity.originHistoryIntent(context, h).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = getInstrumentation().startActivitySync(intent);
        AtomicReference<Player> player = new AtomicReference<>(); AtomicInteger frames = new AtomicInteger();
        waitUntil(() -> { main(() -> { PlayerView view = activity.findViewById(R.id.exo); if(view != null) player.set(view.getPlayer()); }); return player.get()!=null; }, 60000, "the actual playback page must attach its player");
        main(() -> player.get().addListener(new Player.Listener() { @Override public void onRenderedFirstFrame() { frames.incrementAndGet(); } }));
        waitUntil(() -> { AtomicBoolean good=new AtomicBoolean(); main(() -> good.set(player.get().getVideoSize().width>0 && player.get().getCurrentPosition()>=h.getPosition())); return good.get(); }, 45000, "real AVC video must render and resume, not merely return PLAY_READY");
        AtomicReference<Bitmap> frame = new AtomicReference<>();
        waitUntil(() -> { Bitmap shot = getInstrumentation().getUiAutomation().takeScreenshot(); if (shot == null) return false; frame.set(shot); return coversVideoCenter(shot); }, 15000, "video-center screenshot must contain decoded nonblack pixels, not just controls");
        if (frame.get() != null) frame.get().recycle();
        AtomicLong position = new AtomicLong(); main(() -> position.set(player.get().getCurrentPosition()));
        assertTrue("resume seek must be near original progress", Math.abs(position.get()-h.getPosition())<3500);
        // 正常播放页外壳显示原配置站点与那一集：证明详情／选集来自原源 worker，而不是当前配置 SiteApi。
        AtomicReference<String> shell = new AtomicReference<>("");
        main(() -> shell.set(allText(activity)));
        assertTrue("the normal playback shell must show the original site, got: " + shell.get(), shell.get().contains("bob"));
        assertTrue("the shell must show the resumed second episode, got: " + shell.get(), shell.get().contains("第2集"));
        int pid = processId(context.getPackageName()+":origin"); assertTrue(pid>0);
        main(() -> player.get().pause());
        AtomicBoolean paused = new AtomicBoolean(); main(() -> paused.set(!player.get().isPlaying())); assertTrue("pause must not release the origin lease", paused.get());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
        getInstrumentation().waitForIdleSync();
        assertEquals("background/page departure must retain the playing media lease", pid, processId(context.getPackageName()+":origin"));
        main(() -> activity.moveTaskToBack(false));
        main(() -> { player.get().seekTo(2000); player.get().play(); });
        upstream.failSegments = true;
        main(() -> player.get().seekTo(17000));
        waitUntil(() -> upstream.failedSegments.get()>0, 20000, "a real post-start segment 404 must reach the worker/player");
        assertEquals("segment 404 must not trigger current-source search", 0, upstream.searches.get());
        baseline.assertUnchanged(h);
        upstream.failSegments = false;
        main(() -> player.get().seekTo(2500));
        main(activity::finish);
        waitUntil(() -> processId(context.getPackageName()+":origin")<=0, 15000, "real exit must release the worker lease");
        History saved = History.find(h.getCid(), h.getKey()); assertNotNull(saved);
        assertEquals(h.getCid(), saved.getCid()); assertTrue("origin progress must be saved", saved.getPosition()>0);
        assertNull(History.find(VodConfig.getCid(), h.getKey()));
        assertEquals("main Node survives exit", baseline.nodePid, processId(context.getPackageName()+":node"));
        activity = null;
        System.out.println("ORIGIN_FIRST_FRAME " + runtime + " position=" + position.get() + " frameCallbacks=" + frames.get());
    }


    /** 收集当前窗口内所有文本：正常播放页外壳用它证明展示的是原配置站点与那一集。 */
    private static String allText(Activity activity) {
        StringBuilder text = new StringBuilder();
        collectText(activity.getWindow().getDecorView(), text);
        return text.toString();
    }

    private static void collectText(android.view.View view, StringBuilder text) {
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('|');
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), text);
        }
    }
    private void assertHlsThroughRelay(String masterUrl) throws Exception {
        HttpData master=fetch(masterUrl, Map.of()); assertEquals(200,master.status);
        String variant=firstMediaLine(master.text()); assertTrue("master must map its variant", variant.startsWith("http://127.0.0.1:"));
        HttpData media=fetch(variant,Map.of()); assertEquals(200,media.status);
        String text=media.text(); String key=tagUri(text,"#EXT-X-KEY"), map=tagUri(text,"#EXT-X-MAP"), segment=firstMediaLine(text);
        assertTrue(key.startsWith("http://127.0.0.1:")); assertTrue(map.startsWith("http://127.0.0.1:")); assertTrue(segment.startsWith("http://127.0.0.1:"));
        HttpData k=fetch(key,Map.of()); assertEquals(200,k.status); assertEquals(16,k.body.length);
        assertEquals("original key bytes, not an HTML/auth failure",0,k.body[0]); assertEquals(15,k.body[15]);
        HttpData init=fetch(map,Map.of("Range","bytes=4-35")); assertEquals(206,init.status); assertEquals(32,init.body.length);
        assertTrue(init.range.startsWith("bytes 4-35/"));
        HttpData chunk=fetch(segment,Map.of("Range","bytes=0-31")); assertEquals(206,chunk.status); assertEquals(32,chunk.body.length);
        upstream.assertBobRequest("/media-fixture/key.bin"); upstream.assertBobRequest("/media-fixture/init.mp4");
        upstream.assertNoAliceOnBobResources();
    }

    private void assertOriginNode(Bundle reply,int currentPid,String lease) throws Exception {
        int[] ports=reply.getIntArray("nodePorts"); assertNotNull(ports); assertTrue("Node READY must report a real owned port",ports.length>0);
        int worker=processId(context.getPackageName()+":origin_node"); assertTrue(worker>0 && worker!=currentPid);
        assertEquals(currentPid,processId(context.getPackageName()+":node"));
        JSONObject identity=new JSONObject(fetch("http://127.0.0.1:"+ports[0]+"/identity",Map.of()).text());
        assertEquals("bob",identity.getString("account")); assertEquals(reply.getInt("proxyPort"),identity.getInt("proxyPort"));
        assertTrue(identity.getString("root").contains("/origin/"+lease+"/"));
    }

    private Config config(String runtime,String account) throws Exception {
        JSONObject options=new JSONObject().put("account",account).put("base",upstream.base()).put("headers",new JSONObject(headers(account)));
        File directory=new File(root, runtime+"-"+account); assertTrue(directory.mkdirs());
        String url;
        if(runtime.equals("node")) {
            for(String file:new String[]{"index.js","index.js.md5"}) Files.copy(new File(root,"runtime/node/"+file).toPath(),new File(directory,file).toPath());
            Files.write(new File(directory,"index.config.js").toPath(),("module.exports="+options+";").getBytes(StandardCharsets.UTF_8));
            url=directory.getAbsolutePath();
        } else {
            JSONObject site=new JSONObject().put("key",KEY).put("name","origin-"+account).put("type",3)
                    .put("api",runtime.equals("jar")?"csp_OriginFixture":upstream.base()+"/runtime/"+(runtime.equals("js")?"js-spider.js":"py-spider.py"))
                    .put("ext",options.toString()).put("header",new JSONObject(headers(account))).put("searchable",1).put("quickSearch",1);
            JSONObject json=new JSONObject().put("sites",new JSONArray().put(site));
            if(runtime.equals("jar")) json.put("spider",upstream.base()+"/runtime/origin-fixture.jar");
            Files.write(new File(directory,"config.json").toPath(),json.toString().getBytes(StandardCharsets.UTF_8));
            url=upstream.base()+"/"+directory.getName()+"/config.json";
        }
        Config c=Config.create(0,url,"origin-isolation-"+runtime+"-"+account); ownedConfigs.add(c); return c;
    }

    private History history(Config config,String episode) {
        History h=new History(); h.setCid(config.getId()); h.setKey(KEY+"@@@"+VOD+"@@@"+config.getId()); h.setVodName("bob:fixture");
        h.setVodPic(""); h.setEpisodeUrl(episode); h.setSourceBindingKey("origin-line#1"); h.setVodRemarks("第2集");
        h.setPosition(1200); h.setDuration(32000); h.setCreateTime(System.currentTimeMillis());
        AppDatabase.get().getHistoryDao().insertOrUpdate(h); ownedHistory.add(h); return h;
    }

    private String identity(String runtime) throws Exception {
        if (runtime.equals("node")) return fetch(NodeRuntime.baseUrl() + "/identity", Map.of()).text();
        return VodConfig.get().getSite(KEY).spider().homeContent(false);
    }
    private void load(Config config) throws Exception {
        CountDownLatch ready=new CountDownLatch(1); AtomicReference<String> error=new AtomicReference<>();
        VodConfig.load(config,new Callback(){ public void success(){ready.countDown();} public void error(String message){error.set(message);ready.countDown();} });
        assertTrue("configuration load timeout",ready.await(65,TimeUnit.SECONDS)); assertNull("configuration must actually load: "+error.get(),error.get());
    }
    private void verifyRealVideo() throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        try { extractor.setDataSource(new File(root,"media-fixture/preview.mp4").getAbsolutePath()); boolean video=false;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat format=extractor.getTrackFormat(i); if(format.getString(MediaFormat.KEY_MIME).startsWith("video/")){ assertEquals(960,format.getInteger(MediaFormat.KEY_WIDTH));assertEquals(540,format.getInteger(MediaFormat.KEY_HEIGHT));video=true; }}
            assertTrue("existing MKV repack must contain a real AVC video track",video);
        } finally { extractor.release(); }
    }
    private static Map<String,String> headers(String account) {return Map.of("Authorization","Basic "+android.util.Base64.encodeToString((account+":secret").getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP),"Cookie","sid="+account,"Referer","http://origin.fixture/"+account);}
    private void main(Runnable action){getInstrumentation().runOnMainSync(action);}
    private interface Condition {boolean get() throws Exception;}
    private void waitUntil(Condition condition,long timeout,String failure) throws Exception {long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(condition.get())return;SystemClock.sleep(50);}fail(failure);}
    private int processId(String name){ ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);List<ActivityManager.RunningAppProcessInfo> ps=manager.getRunningAppProcesses();if(ps!=null)for(ActivityManager.RunningAppProcessInfo p:ps)if(name.equals(p.processName))return p.pid;return -1;}
    private static String repeat(char c,int count){char[] chars=new char[count];Arrays.fill(chars,c);return new String(chars);}
    private static String firstMediaLine(String text){for(String line:text.split("\n"))if(!line.isBlank()&&!line.startsWith("#"))return line.trim();throw new AssertionError("playlist has no media URL");}
    private static String tagUri(String text,String tag){for(String line:text.split("\n"))if(line.startsWith(tag)){Matcher m=Pattern.compile("URI=\"([^\"]+)\"").matcher(line);if(m.find())return m.group(1);}throw new AssertionError("valid "+tag+" URI attribute missing");}
    private static void assertRealFrame(Bitmap image){assertTrue("video-center screenshot must contain decoded nonblack pixels, not just controls",coversVideoCenter(image));}
    private static boolean coversVideoCenter(Bitmap image){int colored=0,samples=0;for(int y=image.getHeight()/3;y<image.getHeight()*2/3;y+=8)for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x+=8){int color=image.getPixel(x,y);int brightness=((color>>16)&255)+((color>>8)&255)+(color&255);if(brightness>40)colored++;samples++;}return colored>samples/3;}
    private static HttpData fetch(String url,Map<String,String> headers) throws IOException {HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(true);for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());try{int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();byte[] body=in==null?new byte[0]:readBytes(in);return new HttpData(status,body,c.getHeaderField("Content-Range"));}finally{c.disconnect();}}
    private static byte[] readBytes(InputStream in) throws IOException {try(in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private static void assertUnreachable(String url) throws Exception {try{fetch(url,Map.of());fail("released relay still accepts media requests");}catch(IOException expected){}}
    private ParcelFileDescriptor descriptor(String json) throws Exception {File f=File.createTempFile("fd-", ".json",root);Files.write(f.toPath(),json.getBytes(StandardCharsets.UTF_8));ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);assertTrue(f.delete());return fd;}
    private static String readFd(ParcelFileDescriptor fd){try{return new String(readBytes(new ParcelFileDescriptor.AutoCloseInputStream(fd)),StandardCharsets.UTF_8);}catch(IOException e){throw new AssertionError(e);}}
    private static Binder bridge(Binder target){return new Binder(){@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws android.os.RemoteException{return target.transact(code,data,reply,flags);}};}
    private void copyAssets(String name,File dest)throws Exception {String[] children=getInstrumentation().getContext().getAssets().list(name);if(children.length==0){File parent=dest.getParentFile();if(!parent.isDirectory())assertTrue(parent.mkdirs());try(InputStream in=getInstrumentation().getContext().getAssets().open(name)){Files.copy(in,dest.toPath());}}else{if(!dest.isDirectory())assertTrue(dest.mkdirs());for(String child:children)copyAssets(name+"/"+child,new File(dest,child));}}
    private static void copyDirectory(File from,File to)throws Exception {if(from.isDirectory()){assertTrue(to.isDirectory()||to.mkdirs());for(File child:Objects.requireNonNull(from.listFiles()))copyDirectory(child,new File(to,child.getName()));}else Files.copy(from.toPath(),to.toPath());}
    private static void deleteOwned(File file,File allowed)throws Exception {if(!file.getCanonicalPath().equals(allowed.getPath())&&!file.getCanonicalPath().startsWith(allowed.getPath()+File.separator))throw new IOException("cleanup escaped task directory");if(file.isDirectory())for(File child:Objects.requireNonNull(file.listFiles()))deleteOwned(child,allowed);assertTrue(file.delete());}
    private static Map<String,Object> copyPreferences(SharedPreferences preferences){Map<String,Object> result=new TreeMap<>();for(Map.Entry<String,?> entry:preferences.getAll().entrySet())result.put(entry.getKey(),entry.getValue() instanceof Set?new TreeSet<>((Set<String>)entry.getValue()):entry.getValue());return result;}
    /** 只排除主进程自己随播放写的广告统计；账号、配置、运行时偏好仍逐字比对。 */
    /**
     * 主进程自己随任何一次正常播放页播放都会写的运行时统计：广告统计、解码器学习缓存、
     * 原站协议健康度（SiteHealthStore.recordParse/recordPlay，按 site key 记）与音频歌单激活项
     * （AudioPlaylistStore.setActive）、后台缓存盘点耗时（CacheCenter.java:59 的 CacheScheduler 指标）。
     * 这几个键与"切不切配置"无关，正常当前源播放/详情页同样会写，
     * 因此只允许它们变化；账号、配置、解析器、插件等其它偏好必须逐字不变。
     */
    private static final Set<String> PLAYBACK_OWNED_KEYS = new TreeSet<>(Arrays.asList("ad_block_stats", "perf_exo_decoder_runtime_v1", "site_health", "audio_playlist_active", "cache_mgmt_inventory_duration_ms"));
    private static Map<String,Object> stablePreferences(android.content.SharedPreferences preferences){Map<String,Object> result=copyPreferences(preferences);result.keySet().removeAll(PLAYBACK_OWNED_KEYS);return result;}
    /** 偏好摘要：断言失败只暴露键名，绝不把账号/密钥值写进日志。 */
    private static String digest(Map<String,Object> values){try{java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");StringBuilder text=new StringBuilder();for(Map.Entry<String,Object> entry:new TreeMap<>(values).entrySet())text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');return Base64.getEncoder().encodeToString(md.digest(text.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError("digest unavailable");}}
    private static Set<String> changedKeys(Map<String,Object> before,Map<String,Object> after){Set<String> keys=new TreeSet<>(before.keySet());keys.addAll(after.keySet());keys.removeIf(key->java.util.Objects.equals(before.get(key),after.get(key)));return keys;}
    private static void restorePreferences(SharedPreferences prefs,Map<String,Object> before){SharedPreferences.Editor e=prefs.edit();for(String key:prefs.getAll().keySet())if(!before.containsKey(key))e.remove(key);for(Map.Entry<String,Object> value:before.entrySet()){Object v=value.getValue();String k=value.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}assertTrue(e.commit());}

    private final class MainState {
        final int cid,proxyPort,nodePid;final String configRows,siteRows,historyRows,preferencesDigest,fixturePreferencesDigest;final Map<String,Object> preferences,fixturePreferences,preferencesRaw;
        MainState(History origin){cid=VodConfig.getCid();proxyPort=Proxy.getPort();nodePid=processId(context.getPackageName()+":node");configRows=App.gson().toJson(AppDatabase.get().getConfigDao().findAll());siteRows=App.gson().toJson(AppDatabase.get().getSiteDao().findAll());historyRows=nonOriginHistory(origin);preferencesRaw=copyPreferences(Prefers.getPrefers());preferences=stablePreferences(Prefers.getPrefers());preferencesDigest=digest(preferences);fixturePreferences=copyPreferences(context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE));fixturePreferencesDigest=digest(fixturePreferences);}
        /** 断言只报告差异键名与摘要，绝不把含账号/密钥的偏好值写进日志。 */
        void assertUnchanged(History origin){assertEquals(cid,VodConfig.getCid());assertEquals(proxyPort,Proxy.getPort());assertEquals(nodePid,processId(context.getPackageName()+":node"));assertEquals("main config DB rows must stay byte-for-byte unchanged",configRows,App.gson().toJson(AppDatabase.get().getConfigDao().findAll()));assertEquals(siteRows,App.gson().toJson(AppDatabase.get().getSiteDao().findAll()));assertEquals("only the original history may progress",historyRows,nonOriginHistory(origin));Map<String,Object> prefs=stablePreferences(Prefers.getPrefers());Map<String,Object> prefsRaw=copyPreferences(Prefers.getPrefers());assertEquals("main default preferences changed outside the playback-owned keys, keys="+changedKeys(preferencesRaw,prefsRaw),preferencesDigest,digest(prefs));Map<String,Object> fixture=copyPreferences(context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE));assertEquals("main task fixture-private preferences changed, keys="+changedKeys(fixturePreferences,fixture),fixturePreferencesDigest,digest(fixture));}
    }
    private String nonOriginHistory(History h){List<History> rows=new ArrayList<>();for(History row:AppDatabase.get().getHistoryDao().findAll())if(row.getCid()!=h.getCid()||!row.getKey().equals(h.getKey()))rows.add(row);return App.gson().toJson(rows);}
    /**
     * 租约结束的可观测证据是 worker 进程真的消失（而不是一个可能因解绑而丢掉的
     * Binder 死亡通知），随后调用方还要断言回环地址不再可达。
     */
    private void awaitWorkerGone() {
        String name = context.getPackageName() + ":origin";
        long deadline = SystemClock.elapsedRealtime() + 20_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (processId(name) <= 0) return;
            SystemClock.sleep(100);
        }
        fail("close must end the real worker process " + name + ", pid=" + processId(name));
    }
    private final class BindingContext extends ContextWrapper {
        IBinder binder;final CountDownLatch death=new CountDownLatch(1);final Map<ServiceConnection,ServiceConnection> active=new ConcurrentHashMap<>();
        BindingContext(Context base){super(base);}
        @Override public Context getApplicationContext(){return this;}
        @Override public boolean bindService(Intent i,ServiceConnection connection,int flags){ServiceConnection tracked=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){binder=b;try{b.linkToDeath(death::countDown,0);}catch(Exception e){death.countDown();}connection.onServiceConnected(n,b);}public void onServiceDisconnected(ComponentName n){connection.onServiceDisconnected(n);}};active.put(connection,tracked);return super.bindService(i,tracked,flags);}
        @Override public void unbindService(ServiceConnection c){ServiceConnection tracked=active.remove(c);super.unbindService(tracked==null?c:tracked);}
        void unbindRemaining(){for(ServiceConnection c:new ArrayList<>(active.keySet()))try{unbindService(c);}catch(IllegalArgumentException ignored){}}
    }
    private final class InjectedBinding extends ContextWrapper {
        Bundle request;OriginSessionContract.OriginReply reply;
        InjectedBinding(Context base){super(base);}
        @Override public Context getApplicationContext(){return this;}
        @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags){connection.onServiceConnected(new ComponentName(context,OriginSessionService.class),new OriginSessionContract.OriginSessionPort.Stub(){public void open(Bundle r,ParcelFileDescriptor fd,IBinder owner,OriginSessionContract.OriginReply callback){request=r;reply=callback;OriginSessionContract.closeFd(fd);}public void play(Bundle r,OriginSessionContract.OriginReply callback){}public void cancel(long generation){}public void close(String lease,long generation){}});return true;}
        @Override public void unbindService(ServiceConnection c){}
    }
    private static final class Events implements OriginSessionClient.Callback {
        final BlockingQueue<String> queue=new LinkedBlockingQueue<>();final AtomicInteger unavailable=new AtomicInteger();Bundle envelope;OriginSessionClient.Playback playback;String reason="",detail="";
        public void onReady(String lease,Bundle reply){envelope=new Bundle(reply);queue.offer("READY");}
        public void onPlayReady(String lease,OriginSessionClient.Playback p){playback=p;queue.offer("PLAY_READY");}
        public void onUnavailable(String lease,String r){reason=r;unavailable.incrementAndGet();queue.offer("UNAVAILABLE");}
        public void onBlocked(String lease,String r,String d){reason=r;detail=d;queue.offer("BLOCKED");}
        String take()throws Exception {String event=queue.poll(70,TimeUnit.SECONDS);assertNotNull("worker callback timeout "+details(),event);return event;}
        String details(){return reason+" "+detail;}
    }
    private static final class HttpData {final int status;final byte[] body;final String range;HttpData(int s,byte[] b,String r){status=s;body=b;range=r==null?"":r;}String text(){return new String(body,StandardCharsets.UTF_8);}}
    private final class FixtureServer extends NanoHTTPD {
        final File files;final List<Map<String,String>> requests=new CopyOnWriteArrayList<>();final AtomicInteger searches=new AtomicInteger(),failedSegments=new AtomicInteger(),alicePlays=new AtomicInteger();volatile boolean failSegments,blockBobPlayer,matchCurrent;
        FixtureServer(File root){super("127.0.0.1",0);files=root;}
        String base(){return "http://127.0.0.1:"+getListeningPort();}
        @Override public Response serve(IHTTPSession request){try{
            String uri=request.getUri();Map<String,String> observed=new LinkedHashMap<>(request.getHeaders());observed.put("path",uri);requests.add(observed);
            if(uri.equals("/redirect")){Response r=newFixedLengthResponse(Response.Status.FOUND,"text/plain","");r.addHeader("Location",base()+"/media-fixture/master.m3u8");return r;}
            boolean protectedPath=uri.startsWith("/media-fixture/")||uri.equals("/detail")||uri.equals("/player")||uri.equals("/search");
            String account=request.getParms().getOrDefault("account","bob");
            if(protectedPath){Map<String,String> expected=headers(account);if(!expected.get("Authorization").equals(request.getHeaders().get("authorization"))||!expected.get("Cookie").equals(request.getHeaders().get("cookie"))||!expected.get("Referer").equals(request.getHeaders().get("referer")))return newFixedLengthResponse(Response.Status.UNAUTHORIZED,"text/plain","wrong account");}
            if(uri.equals("/search")){searches.incrementAndGet();return json(new JSONObject().put("list",matchCurrent?new JSONArray().put(new JSONObject().put("vod_id",VOD).put("vod_name","bob:fixture")):new JSONArray()));}
            if(uri.equals("/detail")){String runtime=request.getParms().getOrDefault("runtime","fixture");return json(new JSONObject().put("list",new JSONArray().put(new JSONObject().put("vod_id",VOD).put("vod_name",matchCurrent?"bob:fixture":account+":"+runtime).put("vod_play_from","origin-line").put("vod_play_url","第1集$/episode/1#第2集$/episode/2"))));}
            if(uri.equals("/player")){if(!"/episode/2".equals(request.getParms().get("episode")))return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","wrong episode");if(account.equals("alice"))alicePlays.incrementAndGet();return json(new JSONObject().put("parse",0).put("url",blockBobPlayer&&account.equals("bob")?"unsupported-origin://unplayable":base()+"/redirect").put("header",new JSONObject(headers(account))));}
            if(failSegments&&uri.contains("seg-")&&!uri.endsWith("seg-0.m4s")){failedSegments.incrementAndGet();return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","injected segment404 after start");}
            File f=new File(files,uri.substring(1));if(!f.getCanonicalPath().startsWith(files.getCanonicalPath()+File.separator)||!f.isFile())return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","missing fixture");
            byte[] data=Files.readAllBytes(f.toPath());String mime=uri.endsWith("m3u8")?"application/vnd.apple.mpegurl":uri.endsWith("json")?"application/json":"application/octet-stream";
            String range=request.getHeaders().get("range");if(range!=null){Matcher m=Pattern.compile("bytes=(\\d+)-(\\d*)").matcher(range);if(!m.matches())return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE,"text/plain","");int start=Integer.parseInt(m.group(1)),end=m.group(2).isEmpty()?data.length-1:Math.min(data.length-1,Integer.parseInt(m.group(2)));if(start>end)return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE,"text/plain","");byte[] part=Arrays.copyOfRange(data,start,end+1);Response r=newFixedLengthResponse(Response.Status.PARTIAL_CONTENT,mime,new ByteArrayInputStream(part),part.length);r.addHeader("Content-Range","bytes "+start+"-"+end+"/"+data.length);r.addHeader("Accept-Ranges","bytes");return r;}
            Response r=newFixedLengthResponse(Response.Status.OK,mime,new ByteArrayInputStream(data),data.length);r.addHeader("Accept-Ranges","bytes");return r;
        }catch(Exception e){return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain",String.valueOf(e));}}
        Response json(JSONObject value){return newFixedLengthResponse(Response.Status.OK,"application/json",value.toString());}
        void assertBobRequest(String path){assertTrue("a real authenticated request must reach "+path,requests.stream().anyMatch(r->path.equals(r.get("path"))&&headers("bob").get("Authorization").equals(r.get("authorization"))&&"sid=bob".equals(r.get("cookie"))));}
        void assertNoAliceOnBobResources(){for(Map<String,String> r:requests)if(r.get("path").startsWith("/media-fixture/")){assertEquals("original media/key/map must never use alice",headers("bob").get("Authorization"),r.get("authorization"));assertEquals("sid=bob",r.get("cookie"));assertEquals(headers("bob").get("Referer"),r.get("referer"));}}
    }
}
