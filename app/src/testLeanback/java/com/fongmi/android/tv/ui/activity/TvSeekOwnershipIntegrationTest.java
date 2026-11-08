package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.ActivityCastBinding;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.custom.CustomKeyDownLive;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest.TestApp;
import com.fongmi.android.tv.utils.Clock;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = TestApp.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvSeekOwnershipIntegrationTest {
    private final List<PlayerManager> managers = new ArrayList<>();

    @After public void releasePlayers() { for (PlayerManager manager : managers) manager.release(); }

    @Test
    public void castPreviewBackCancelsActualInputWithoutLeavingPage() {
        TestCast host = cast();
        ActivityCastBinding binding = ReflectionHelpers.getField(host, "mBinding");
        burst(host, 10);
        assertEquals(View.VISIBLE, binding.widget.center.getVisibility());
        host.back();
        drain();
        assertEquals("Preview Back must consume the key burst, not seek later", 0, host.seeks);
        assertFalse(host.isFinishing());
    }

    @Test
    public void castBackDownClearsTimerButKeepsPreviewBackBehavior() {
        TestCast host = cast();
        burst(host, 10);
        host.dispatchKeyEvent(new KeyEvent(20, 20, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0));
        drain();
        assertEquals(0, host.seeks);
        host.back();
        assertFalse(host.isFinishing());
    }

    @Test
    public void castControlsCancelAndCurrentPlaybackStillAcceptsSeek() {
        TestCast host = cast();
        burst(host, 10);
        ReflectionHelpers.callInstanceMethod(host, "showControl");
        drain();
        assertEquals(0, host.seeks);
        ReflectionHelpers.callInstanceMethod(host, "hideControl");
        burst(host, 20);
        drain();
        assertEquals(1, host.seeks);
    }

    @Test
    public void videoCurrentInputAppliesAndSourceRestartRejectsQueuedInput() {
        TestVideo host = Robolectric.buildActivity(TestVideo.class).get();
        PlayerManager manager = configure(host);
        burst(host, 10);
        drain();
        assertEquals(1, host.seeks);
        burst(host, 20);
        trace(manager).begin();
        trace(manager).begin();
        drain();
        assertEquals("same key/URL cannot represent A-B-A ownership", 1, host.seeks);
    }

    @Test
    public void videoControlsTakeOverBeforeQueuedCommit() {
        TestVideo host = Robolectric.buildActivity(TestVideo.class).get();
        configure(host);
        ActivityVideoBinding binding = ReflectionHelpers.getField(host, "mBinding");
        burst(host, 10);
        binding.control.getRoot().setVisibility(View.VISIBLE);
        host.dispatchKeyEvent(new KeyEvent(20, 20, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        drain();
        assertEquals(0, host.seeks);
    }



    private TestCast cast() {
        TestCast host = Robolectric.buildActivity(TestCast.class).get();
        configure(host);
        return host;
    }

    private PlayerManager configure(PlaybackActivity host) {
        PlayerSetting.putPlayer(PlayerSetting.EXO);
        PlayerManager manager = new PlayerManager(null);
        managers.add(manager);
        host.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        ViewBinding binding;
        if (host instanceof CastActivity cast) {
            binding = cast.getBinding();
            ((ActivityCastBinding) binding).control.getRoot().setVisibility(View.GONE);
        } else if (host instanceof LiveActivity live) {
            binding = live.getBinding();
            ((ActivityLiveBinding) binding).control.getRoot().setVisibility(View.GONE);
            ((ActivityLiveBinding) binding).recycler.setVisibility(View.GONE);
        } else {
            binding = ((VideoActivity) host).getBinding();
            ((ActivityVideoBinding) binding).control.getRoot().setVisibility(View.GONE);
            ((ActivityVideoBinding) binding).lutQuick.setVisibility(View.GONE);
            ReflectionHelpers.setField(host, "fullscreen", true);
        }
        host.setContentView(binding.getRoot());
        PlaybackService service = new PlaybackService();
        ReflectionHelpers.setField(service, "player", manager);
        ReflectionHelpers.setField(host, "mService", service);
        if (!(host instanceof TestVideo)) ReflectionHelpers.setField(host, "mPlaybackKey", "same-url");
        ReflectionHelpers.setField(host, "mClock", Clock.create());
        ReflectionHelpers.setField(host, "mR1", (Runnable) () -> { });
        ReflectionHelpers.setField(manager, "spec", PlaySpec.from("same-url", "https://example.test/a.mp4", Collections.emptyMap(), MediaMetadata.EMPTY));
        manager.getPlayer().setMediaItem(MediaItem.fromUri("https://example.test/a.mp4"));
        trace(manager).begin();
        if (host instanceof LiveActivity) ReflectionHelpers.setField(host, "mKeyDown", CustomKeyDownLive.create(host));
        else ReflectionHelpers.setField(host, "mKeyDown", CustomKeyDownVod.create(host));
        return manager;
    }

    private static PlaybackTrace trace(PlayerManager manager) { return ReflectionHelpers.getField(manager, "playbackTrace"); }
    private static void burst(PlaybackActivity host, long time) {
        host.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        host.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
    }
    private static void drain() { ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(Duration.ofMillis(250)); }

    public static class TestCast extends CastActivity {
        int seeks;
        @Override protected boolean seekTo(long deltaMs) { seeks++; return false; }
        void back() { onBackInvoked(); }
        void stopped() { onStop(); }
    }
    public static class TestVideo extends VideoActivity {
        int seeks;
        @Override protected String getPlaybackKey() { return "same-url"; }
        @Override protected boolean seekTo(long deltaMs) { seeks++; return false; }
        void stopped() { onStop(); }
    }
    public static class TestLive extends LiveActivity {
        int seeks;
        @Override protected boolean seekTo(long deltaMs) { seeks++; return false; }
        void stopped() { onStop(); }
    }
}
