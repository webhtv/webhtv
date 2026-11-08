package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.fongmi.android.tv.App;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;

/**
 * Contract for the TV category long press: holding DPAD_CENTER on a category chip must
 * fire the long-press callback exactly once and must not additionally deliver the click
 * that the same key release would normally produce.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvFilterResetLongPressTest {

    private ActivityController<Activity> controller;
    private CustomTypeView view;
    private int longPressCount;
    private int clickCount;

    @Before
    public void setUp() {
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        // CustomTypeView defers its cooldown reset through App.post, so the test needs the
        // application singleton the production code expects.
        ReflectionHelpers.setStaticField(App.class, "instance", new App());
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        view = new CustomTypeView(activity);
        view.setLongPressListener(() -> longPressCount++);
        view.setOnClickListener(v -> clickCount++);
        activity.setContentView(view, new ViewGroup.LayoutParams(240, 120));
        controller.visible();
        view.requestFocus();
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
        ReflectionHelpers.setStaticField(App.class, "instance", null);
    }

    @Test
    public void holdingCenterFiresLongPressOnceAndSuppressesTheClick() {
        key(KeyEvent.ACTION_DOWN, 0);
        mainLooper().idleFor(Duration.ofMillis(CustomTypeView.LONG_PRESS_MS - 100));
        assertEquals(0, longPressCount);

        mainLooper().idleFor(Duration.ofMillis(200));
        assertEquals(1, longPressCount);

        key(KeyEvent.ACTION_UP, 0);
        mainLooper().idleFor(Duration.ofMillis(200));
        assertEquals(1, longPressCount);
        assertEquals(0, clickCount);
    }

    @Test
    public void releasingBeforeTheThresholdStillClicks() {
        key(KeyEvent.ACTION_DOWN, 0);
        mainLooper().idleFor(Duration.ofMillis(100));
        key(KeyEvent.ACTION_UP, 0);
        mainLooper().idleFor(Duration.ofMillis(CustomTypeView.LONG_PRESS_MS + 200));
        assertEquals(0, longPressCount);
        assertEquals(1, clickCount);
    }

    @Test
    public void repeatedKeyDownEventsDoNotRestartTheTimer() {
        key(KeyEvent.ACTION_DOWN, 0);
        mainLooper().idleFor(Duration.ofMillis(300));
        key(KeyEvent.ACTION_DOWN, 1);
        mainLooper().idleFor(Duration.ofMillis(300));
        assertEquals(1, longPressCount);
        key(KeyEvent.ACTION_UP, 0);
        mainLooper().idleFor(Duration.ofMillis(200));
        assertEquals(1, longPressCount);
        assertEquals(0, clickCount);
    }

    @Test
    public void aLostReleaseDoesNotSwallowTheNextClick() {
        key(KeyEvent.ACTION_DOWN, 0);
        mainLooper().idleFor(Duration.ofMillis(CustomTypeView.LONG_PRESS_MS + 200));
        assertEquals(1, longPressCount);
        // The matching ACTION_UP never reached this view (pause, sleep, detach). The next fresh
        // press must still behave as an ordinary short press instead of inheriting a stale latch
        // that would skip the timer and then swallow the release.
        key(KeyEvent.ACTION_DOWN, 0);
        mainLooper().idleFor(Duration.ofMillis(100));
        key(KeyEvent.ACTION_UP, 0);
        mainLooper().idleFor(Duration.ofMillis(200));
        assertEquals(1, longPressCount);
        assertEquals(1, clickCount);
    }

    private void key(int action, int repeatCount) {
        long eventTime = android.os.SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(eventTime, eventTime, action, KeyEvent.KEYCODE_DPAD_CENTER, repeatCount);
        view.dispatchKeyEvent(event);
    }

    private ShadowLooper mainLooper() {
        return Shadow.extract(Looper.getMainLooper());
    }
}
