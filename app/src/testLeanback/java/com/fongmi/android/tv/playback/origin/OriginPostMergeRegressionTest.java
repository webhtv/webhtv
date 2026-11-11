package com.fongmi.android.tv.playback.origin;

import android.app.Notification;
import android.os.Looper;

import androidx.media3.common.Player;
import androidx.room.Room;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.node.OriginNodeService;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.Init;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowService;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = OriginPostMergeRegressionTest.TestApp.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class OriginPostMergeRegressionTest {
    public static class TestApp extends App {
        @Override public void onCreate() { Init.set(this); }
    }

    private AppDatabase database;

    @Before public void setup() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries().build();
        ReflectionHelpers.setStaticField(AppDatabase.class, "instance", database);
        Setting.putIncognito(false);
    }

    @After public void cleanup() {
        PlaybackService.releaseOriginLease();
        Setting.putIncognito(false);
        database.close();
        ReflectionHelpers.setStaticField(AppDatabase.class, "instance", null);
    }

    @Test @Config(sdk = {24, 25})
    public void nodeForegroundNotificationWorksBeforeAndroid26WithoutLoadingNode() throws Exception {
        OriginNodeService service = Robolectric.buildService(OriginNodeService.class).get();
        Method foreground = OriginNodeService.class.getDeclaredMethod("foreground", String.class);
        foreground.setAccessible(true);
        foreground.invoke(service, "SDK fixture");
        ShadowService shadow = Shadow.extract(service);
        Notification notification = shadow.getLastForegroundNotification();
        assertNotNull("foreground must publish a real notification before any libnode load", notification);
        assertEquals(9533, shadow.getLastForegroundNotificationId());
        assertEquals("原源 Node", notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("SDK fixture", notification.extras.getString(Notification.EXTRA_TEXT));
        assertTrue((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
    }

    @Test
    @Config(sdk = 27, shadows = {com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest.SurfaceIsolation.class,
            RejectionArtworkIsolation.class})
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void tvReleasedOriginRejectionClearsWaitingFlagsAndShowsError() throws Exception {
        com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest factory = new com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest();
        com.fongmi.android.tv.ui.activity.VideoActivity host = ReflectionHelpers.callInstanceMethod(factory, "host");
        com.fongmi.android.tv.databinding.ActivityVideoBinding binding = ReflectionHelpers.getField(host, "mBinding");
        History history = ReflectionHelpers.getField(host, "mHistory");
        history.setCid(702);
        history.setKey("fixture@@@video@@@702");
        Constructor<OriginHistoryPlayback> constructor = OriginHistoryPlayback.class.getDeclaredConstructor(History.class, OriginHistoryPlayback.Listener.class);
        constructor.setAccessible(true);
        OriginHistoryPlayback released = constructor.newInstance(history, null);
        released.release();
        ReflectionHelpers.setField(host, "mOrigin", released);
        ReflectionHelpers.setField(host, "mPlaybackPlayerStarted", true);
        binding.progress.getRoot().setVisibility(android.view.View.VISIBLE);
        com.fongmi.android.tv.ui.adapter.FlagAdapter flags = ReflectionHelpers.getField(host, "mFlagAdapter");
        com.fongmi.android.tv.ui.adapter.EpisodeAdapter episodes = ReflectionHelpers.getField(host, "mEpisodeAdapter");
        try {
            ReflectionHelpers.callInstanceMethod(host, "getPlayer",
                    ReflectionHelpers.ClassParameter.from(com.fongmi.android.tv.bean.Flag.class, flags.getActivated()),
                    ReflectionHelpers.ClassParameter.from(com.fongmi.android.tv.bean.Episode.class, episodes.getActivated()));
            assertEquals("the real error view must be visible", android.view.View.VISIBLE, binding.widget.error.getVisibility());
            assertTrue(binding.widget.text.getText().toString().contains("原源会话已结束"));
            assertEquals("explicit rejection must end with the actual progress view hidden", android.view.View.GONE, binding.progress.getRoot().getVisibility());
            boolean active = ReflectionHelpers.getField(host, "mPlaybackRequestActive");
            boolean started = ReflectionHelpers.getField(host, "mPlaybackPlayerStarted");
            System.out.println("ORIGIN_TV_REJECTION_STATE active=" + active + " started=" + started
                    + " error=" + binding.widget.text.getText() + " progress=" + binding.progress.getRoot().getVisibility());
            assertFalse("TV rejected request must clear the real in-flight flag", active);
            assertFalse("TV rejected request must clear player-started state", started);
        } finally {
            PlayerManager manager = ReflectionHelpers.getField(host, "manager");
            manager.release();
        }
    }

    @org.robolectric.annotation.Implements(com.fongmi.android.tv.ui.activity.VideoActivity.class)
    public static class RejectionArtworkIsolation extends com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest.SurfaceIsolation {
        @org.robolectric.annotation.Implementation
        protected void applyPlaybackArtwork(com.fongmi.android.tv.bean.Episode episode) { }
    }

    @Test
    public void incognitoTickerDoesNotWriteOrCommitButNormalTickerWritesAndExitReleases() throws Exception {
        History history = new History();
        history.setCid(702);
        history.setKey("site@@@video@@@702");
        history.setPosition(123);
        history.setDuration(10000);
        history.setCreateTime(1000);
        database.getHistoryDao().insertOrUpdate(history);
        Constructor<OriginSessionClient> constructor = OriginSessionClient.class.getDeclaredConstructor(
                android.content.Context.class, History.class, OriginSessionClient.Callback.class);
        constructor.setAccessible(true);
        OriginSessionClient lease = constructor.newInstance(RuntimeEnvironment.getApplication(), history, null);
        String expected = ReflectionHelpers.getField(lease, "expectedResumeVersion");
        PlaybackService.holdOriginLease(lease);
        PlaybackService service = Robolectric.buildService(PlaybackService.class).get();
        PlayerManager manager = new PlayerManager(null);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            if ("getCurrentPosition".equals(method.getName())) return 5432L;
            if ("getDuration".equals(method.getName())) return 10000L;
            throw new AssertionError("unexpected player call: " + method.getName());
        });
        ReflectionHelpers.setField(manager, "player", player);
        ReflectionHelpers.setField(service, "player", manager);
        ReflectionHelpers.setField(service, "originHistory", history.copy());
        Runnable ticker = ReflectionHelpers.getField(service, "originProgressTicker");
        Setting.putIncognito(true);
        ticker.run();
        assertEquals("incognito must leave original progress untouched", 123, History.find(702, history.getKey()).getPosition());
        assertEquals("no progress commit may be invented", expected, ReflectionHelpers.getField(lease, "expectedResumeVersion"));
        assertFalse("incognito must not close the ongoing media lease", lease.isClosed());
        ShadowLooper looper = Shadow.extract(Looper.getMainLooper());
        assertNotNull("incognito still schedules lease/progress maintenance", looper.getNextScheduledTaskTime());
        App.removeCallbacks(ticker);
        Setting.putIncognito(false);
        ticker.run();
        assertEquals(5432, History.find(702, history.getKey()).getPosition());
        assertFalse(lease.isClosed());
        App.removeCallbacks(ticker);
        PlaybackService.releaseOriginLease();
        assertTrue("normal exit must release even after incognito playback", lease.isClosed());
        assertNull(PlaybackService.originLease());
    }
}
