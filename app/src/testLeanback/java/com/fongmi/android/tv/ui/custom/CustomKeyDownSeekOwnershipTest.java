package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.KeyEvent;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlaybackTrace;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = CustomKeyDownSeekOwnershipTest.TestApp.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CustomKeyDownSeekOwnershipTest {
    @Test
    public void burstFromPreviousPlaybackDoesNotSeekReplacement() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(100);
        burst(host, 110, KeyEvent.KEYCODE_DPAD_RIGHT);
        host.trace.begin();
        drain(250);
        assertTrue("A queued TV burst must not seek replacement playback", host.seeks.isEmpty());
    }

    @Test
    public void rapidReleasesCommitOnceAt250msAfterLastRelease() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(100);
        burst(host, 110, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(249);
        assertTrue(host.seeks.isEmpty());
        drain(1);
        assertEquals(Arrays.asList(20_000L), host.seeks);
        drain(250);
        assertEquals(1, host.seeks.size());
    }

    @Test
    public void newHeldDownRemovesEarlierReleaseTimer() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(100);
        host.input.onKeyDown(key(110, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        drain(300);
        assertTrue(host.seeks.isEmpty());
        host.input.onKeyDown(key(110, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        drain(250);
        assertEquals(Arrays.asList(20_000L), host.seeks);
    }

    @Test
    public void ownedRepeatsAccumulateUntilRelease() {
        VodHost host = vod();
        for (int repeat = 0; repeat < 3; repeat++) host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, repeat, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0, 1, 0));
        drain(250);
        assertEquals(Arrays.asList(-30_000L), host.seeks);
    }

    @Test
    public void unownedRepeatAndReleaseCannotCreateBurst() {
        VodHost host = vod();
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        drain(250);
        assertTrue(host.seeks.isEmpty());
    }

    @Test
    public void wrongDeviceKeyAndDownTimeCannotReleaseOwnedKey() {
        VodHost host = vod();
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1, 2, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 2, 0));
        host.input.onKeyDown(key(11, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0, 1, 0));
        drain(250);
        assertTrue(host.seeks.isEmpty());
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        drain(250);
        assertEquals(Arrays.asList(10_000L), host.seeks);
    }

    @Test
    public void canceledReleaseAndLateRepeatsNeverResurrectSeek() {
        VodHost host = vod();
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, KeyEvent.FLAG_CANCELED));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        drain(250);
        assertTrue(host.seeks.isEmpty());
        assertEquals(1, host.cancels);
    }

    @Test
    public void canceledRepeatClearsTheOwnedBurst() {
        VodHost host = vod();
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1, 1, KeyEvent.FLAG_CANCELED));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        drain(250);
        assertTrue(host.seeks.isEmpty());
    }

    @Test
    public void opposingDirectionsCancelWithoutSeekOrResume() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        burst(host, 20, KeyEvent.KEYCODE_DPAD_LEFT);
        drain(250);
        assertTrue(host.seeks.isEmpty());
        assertEquals(1, host.cancels);
        burst(host, 30, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(250);
        assertEquals(Arrays.asList(10_000L), host.seeks);
    }

    @Test
    public void lostCapabilityAndNoneSessionNeverCommit() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        host.seekable = false;
        drain(250);
        assertTrue(host.seeks.isEmpty());
        host.seekable = true;
        host.trace.clear();
        burst(host, 20, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(250);
        assertTrue(host.seeks.isEmpty());
    }

    @Test
    public void sameSourceRestartStillRejectsOldSessionAndAllowsCurrent() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        host.trace.begin();
        host.trace.begin();
        drain(250);
        assertTrue(host.seeks.isEmpty());
        burst(host, 20, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(250);
        assertEquals(Arrays.asList(10_000L), host.seeks);
    }

    @Test
    public void cancellationIsIdempotentAndFullscreenExitClearsTimer() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        host.input.setFull(false);
        host.input.cancelSeek();
        host.input.reset();
        drain(250);
        assertTrue(host.seeks.isEmpty());
        assertEquals(1, host.cancels);
    }

    @Test
    public void upKeySpeedAndDownNavigationRemainUnchanged() {
        VodHost host = vod();
        burst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        host.input.onKeyDown(key(20, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 1, 1, KeyEvent.FLAG_LONG_PRESS));
        host.input.onKeyDown(key(20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP, 0, 1, 0));
        host.input.onKeyDown(key(30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN, 0, 1, 0));
        drain(250);
        assertTrue(host.seeks.isEmpty());
        assertEquals(1, host.speedUps);
        assertEquals(1, host.speedEnds);
        assertEquals(1, host.downs);
    }

    @Test
    public void seekableLiveUsesSameCoalescingAndControlCancellation() {
        LiveHost host = live();
        liveBurst(host, 10, KeyEvent.KEYCODE_DPAD_RIGHT);
        liveBurst(host, 20, KeyEvent.KEYCODE_DPAD_RIGHT);
        drain(250);
        assertEquals(Arrays.asList(20_000L), host.seeks);
        liveBurst(host, 30, KeyEvent.KEYCODE_DPAD_RIGHT);
        host.allowed = false;
        host.input.onKeyDown(key(40, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0, 1, 0));
        drain(250);
        assertEquals(1, host.seeks.size());
    }

    @Test
    public void ordinaryLiveKeepsImmediateLinesNavigationAnd2000msDigits() {
        LiveHost host = live();
        host.seekable = false;
        liveBurst(host, 10, KeyEvent.KEYCODE_DPAD_LEFT);
        liveBurst(host, 20, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, host.lefts);
        assertEquals(1, host.rights);
        host.input.onKeyDown(key(30, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0, 1, 0));
        host.input.onKeyDown(key(40, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0, 1, 0));
        assertEquals(1, host.ups);
        assertEquals(1, host.downs);
        host.input.onKeyDown(key(50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_1, 0, 1, 0));
        drain(1999);
        assertTrue(host.found.isEmpty());
        drain(1);
        assertEquals(Arrays.asList("1"), host.found);
        assertTrue(host.seeks.isEmpty());
    }

    @Test
    public void canceledLiveReleaseCannotSwitchLine() {
        LiveHost host = live();
        host.seekable = false;
        host.input.onKeyDown(key(10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, KeyEvent.FLAG_CANCELED));
        drain(250);
        assertEquals(0, host.rights);
        assertTrue(host.seeks.isEmpty());
    }

    @Test
    public void unpairedLiveReleaseStillSwitchesLineWithoutSeek() {
        LiveHost host = live();
        host.seekable = false;
        host.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0, 1, 0));
        host.input.onKeyDown(key(20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        assertEquals(1, host.lefts);
        assertEquals(1, host.rights);
        assertTrue(host.seeks.isEmpty());
        VodHost vodHost = vod();
        vodHost.input.onKeyDown(key(10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0, 1, 0));
        vodHost.input.onKeyDown(key(20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 1, 0));
        drain(250);
        assertTrue(vodHost.seeks.isEmpty());
    }

    private static VodHost vod() {
        VodHost host = Robolectric.buildActivity(VodHost.class).setup().get();
        host.trace.begin();
        host.input = CustomKeyDownVod.create(host);
        return host;
    }
    private static LiveHost live() {
        LiveHost host = Robolectric.buildActivity(LiveHost.class).setup().get();
        host.trace.begin();
        host.input = CustomKeyDownLive.create(host);
        return host;
    }
    private static void burst(VodHost host, long time, int code) {
        host.input.onKeyDown(key(time, KeyEvent.ACTION_DOWN, code, 0, 1, 0));
        host.input.onKeyDown(key(time, KeyEvent.ACTION_UP, code, 0, 1, 0));
    }
    private static void liveBurst(LiveHost host, long time, int code) {
        host.input.onKeyDown(key(time, KeyEvent.ACTION_DOWN, code, 0, 1, 0));
        host.input.onKeyDown(key(time, KeyEvent.ACTION_UP, code, 0, 1, 0));
    }
    private static void drain(long ms) { ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(Duration.ofMillis(ms)); }
    private static KeyEvent key(long downTime, int action, int code, int repeat, int device, int flags) {
        return new KeyEvent(downTime, downTime + repeat, action, code, repeat, 0, device, 0, flags);
    }

    public static class TestApp extends App { @Override public void onCreate() { } }
    public static class VodHost extends Activity implements CustomKeyDownVod.Listener {
        final PlaybackTrace trace = new PlaybackTrace();
        final List<Long> seeks = new ArrayList<>();
        CustomKeyDownVod input;
        boolean seekable = true;
        int cancels, speedUps, speedEnds, downs;
        @Override public boolean canSeek() { return seekable; }
        @Override public String getSeekSession() { return trace.current(); }
        @Override public void onSeekCancel() { cancels++; }
        @Override public void onSeeking(long time) { }
        @Override public void onSeekEnd(long time) { seeks.add(time); input.reset(); }
        @Override public void onSpeedUp() { speedUps++; }
        @Override public void onSpeedEnd() { speedEnds++; }
        @Override public void onKeyUp() { }
        @Override public void onKeyDown() { downs++; }
        @Override public void onKeyCenter() { }
        @Override public void onSingleTap() { }
        @Override public void onDoubleTap() { }
    }
    public static class LiveHost extends Activity implements CustomKeyDownLive.Listener {
        final PlaybackTrace trace = new PlaybackTrace();
        final List<Long> seeks = new ArrayList<>();
        final List<String> found = new ArrayList<>();
        CustomKeyDownLive input;
        boolean seekable = true, allowed = true;
        int lefts, rights, ups, downs;
        @Override public boolean canSeek() { return seekable; }
        @Override public String getSeekSession() { return trace.current(); }
        @Override public boolean dispatch(boolean check) { return allowed; }
        @Override public void onSeeking(long time) { }
        @Override public void onSeekEnd(long time) { seeks.add(time); input.reset(); }
        @Override public void onSeekCancel() { }
        @Override public void onKeyLeft(long time) { lefts++; }
        @Override public void onKeyRight(long time) { rights++; }
        @Override public void onShow(String number) { }
        @Override public void onFind(String number) { found.add(number); }
        @Override public void onKeyUp() { ups++; }
        @Override public void onKeyDown() { downs++; }
        @Override public void onKeyCenter() { }
        @Override public void onMenu() { }
        @Override public void onSingleTap() { }
        @Override public void onDoubleTap() { }
    }
}
