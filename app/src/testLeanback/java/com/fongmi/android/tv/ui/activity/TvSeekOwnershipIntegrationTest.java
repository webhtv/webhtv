package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.model.LiveViewModel;
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
@Config(sdk = 27, application = TestApp.class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
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

    @Test
    public void liveUnknownDurationSwitchesBothLinesWithoutSeekPreview() {
        TestLive host = live();
        assertFalse(host.manager.isLive());
        assertFalse(host.manager.isEmpty());
        burst(host, 10);
        assertEquals(1, host.channel.getIndex());
        burst(host, 20, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, host.channel.getIndex());
        drain();
        assertEquals(0, host.seeks);
        assertEquals(View.GONE, liveBinding(host).widget.center.getVisibility());
    }

    @Test
    public void stoppedLiveStillSwitchesLineAndResetsExhaustedFallback() {
        TestLive host = live();
        host.manager.reset();
        host.manager.stop();
        ReflectionHelpers.setField(host, "mLineFallbackAnchor", 0);
        ReflectionHelpers.setField(host, "mLineFallbackExhausted", true);
        burst(host, 10);
        assertEquals(1, host.channel.getIndex());
        assertFalse(ReflectionHelpers.getField(host, "mLineFallbackExhausted"));
        drain();
        assertEquals(0, host.seeks);
        assertEquals(View.GONE, liveBinding(host).widget.center.getVisibility());
    }

    @Test
    public void loadingLiveWithoutPlaybackSpecStillSwitchesLine() {
        TestLive host = live();
        host.manager.clear();
        host.manager.stop();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(2, host.channel.getIndex());
        drain();
        assertEquals(0, host.seeks);
    }

    @Test
    public void ordinaryLiveWithVodLikeDurationStillSwitchesLine() {
        TestLive host = live();
        host.seekPlayer.duration = 120_000;
        host.seekPlayer.seekable = true;
        burst(host, 10);
        assertEquals(1, host.channel.getIndex());
        drain();
        assertEquals(0, host.seeks);
    }

    @Test
    public void catchupWithKnownDurationKeepsSeekWithoutSwitchingLine() {
        TestLive host = live();
        ReflectionHelpers.setField(host, "playbackCatchup", true);
        host.seekPlayer.duration = 120_000;
        host.seekPlayer.seekable = true;
        burst(host, 10);
        assertEquals(View.VISIBLE, liveBinding(host).widget.center.getVisibility());
        drain();
        assertEquals(1, host.seeks);
        assertEquals(0, host.channel.getIndex());
    }

    @Test
    public void catchupWithUnknownDurationDoesNotSeekOrSwitchLine() {
        TestLive host = live();
        ReflectionHelpers.setField(host, "playbackCatchup", true);
        host.seekPlayer.seekable = true;
        burst(host, 10);
        drain();
        assertEquals(0, host.seeks);
        assertEquals(0, host.channel.getIndex());
        assertEquals(View.GONE, liveBinding(host).widget.center.getVisibility());
    }

    @Test
    public void catchupWithoutSeekCommandDoesNotSeekOrSwitchLine() {
        TestLive host = live();
        ReflectionHelpers.setField(host, "playbackCatchup", true);
        host.seekPlayer.duration = 120_000;
        burst(host, 10);
        drain();
        assertEquals(0, host.seeks);
        assertEquals(0, host.channel.getIndex());
        assertEquals(View.GONE, liveBinding(host).widget.center.getVisibility());
    }

    @Test
    public void visibleLiveControlsKeepDirectionalKeysForFocus() {
        TestLive host = live();
        liveBinding(host).control.getRoot().setVisibility(View.VISIBLE);
        burst(host, 10);
        drain();
        assertEquals(0, host.seeks);
        assertEquals(0, host.channel.getIndex());
    }

    @Test
    public void singleLineLiveDoesNotSeekWhenDurationIsUnknown() {
        TestLive host = live();
        host.channel.setUrls(Collections.singletonList("https://example.test/a.ts"));
        burst(host, 10);
        drain();
        assertEquals(0, host.seeks);
        assertEquals(0, host.channel.getIndex());
        assertEquals(View.GONE, liveBinding(host).widget.center.getVisibility());
    }

    private TestLive live() {
        TestLive host = Robolectric.buildActivity(TestLive.class).get();
        host.manager = configure(host);
        host.seekPlayer = new TestSeekPlayer(host.manager.getPlayer());
        ReflectionHelpers.setField(host.manager, "player", host.seekPlayer);
        host.channel = Channel.create("Test live").group(new Group("Test group"));
        host.channel.setUrls(java.util.Arrays.asList("https://example.test/a.ts", "https://example.test/b.ts", "https://example.test/c.ts"));
        ReflectionHelpers.setField(host, "mChannel", host.channel);
        ReflectionHelpers.setField(host, "mViewModel", new LiveViewModel() {
            @Override public void getEpg(Channel channel) { }
            @Override public void getUrl(Channel channel) { }
        });
        for (String callback : java.util.Arrays.asList("mR2", "mR3", "mEndRetry", "mBufferingTimeout")) {
            ReflectionHelpers.setField(host, callback, (Runnable) () -> { });
        }
        liveBinding(host).widget.center.setVisibility(View.GONE);
        return host;
    }

    private static ActivityLiveBinding liveBinding(TestLive host) {
        return ReflectionHelpers.getField(host, "mBinding");
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
        burst(host, time, KeyEvent.KEYCODE_DPAD_RIGHT);
    }
    private static void burst(PlaybackActivity host, long time, int keyCode) {
        host.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0));
        host.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0));
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
    private static final class TestSeekPlayer extends ForwardingPlayer {
        long duration = C.TIME_UNSET;
        boolean seekable;

        TestSeekPlayer(Player player) { super(player); }
        @Override public long getDuration() { return duration; }
        @Override public boolean isCommandAvailable(int command) {
            return command == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM ? seekable : super.isCommandAvailable(command);
        }
    }

    public static class TestLive extends LiveActivity {
        int seeks;
        PlayerManager manager;
        Channel channel;
        TestSeekPlayer seekPlayer;
        @Override protected boolean seekTo(long deltaMs) { seeks++; return false; }
        void stopped() { onStop(); }
    }
}
