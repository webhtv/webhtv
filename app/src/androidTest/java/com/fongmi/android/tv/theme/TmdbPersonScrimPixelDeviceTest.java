package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.TmdbPersonDialog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Device-only proof that scrimOpacity changes the shipped dialog scrim, not the opaque panel. */
@RunWith(AndroidJUnit4.class)
public class TmdbPersonScrimPixelDeviceTest {

    private ActivityScenario<TmdbScrimHostActivity> scenario;
    private ThemeProfile originalProfile;

    @Before
    public void setUp() {
        originalProfile = ThemeProfileStore.load();
        scenario = ActivityScenario.launch(TmdbScrimHostActivity.class);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @After
    public void tearDown() {
        if (originalProfile != null) ThemeProfileStore.apply(originalProfile);
        if (scenario != null) scenario.close();
    }

    @Test
    public void scrimOpacityMovesOnlyTheDialogScrimPixels() {
        Bitmap unset = captureWithOpacity(null);
        Bitmap opaque = captureWithOpacity(ThemeProfileValidator.MAX_SCRIM_OPACITY);
        assertEquals("probe screenshots must share the device resolution", unset.getWidth(), opaque.getWidth());
        assertEquals("probe screenshots must share the device resolution", unset.getHeight(), opaque.getHeight());

        Rect scrim = new Rect(0, 0, unset.getWidth(), unset.getHeight() / 3);
        dumpSample("unset", unset, scrim);
        dumpSample("opaque", opaque, scrim);
        int changedInPanel = changedPixelsAroundPanelCenter(unset, opaque);
        int changedInScrim = changedPixels(unset, opaque, scrim);

        assertTrue("scrimOpacity=max must change visible pixels in the scrim area, changed=" + changedInScrim,
                changedInScrim > 0);
        assertEquals("opaque dialog panel centre must not change with scrimOpacity, changed=" + changedInPanel,
                0, changedInPanel);
    }

    private Bitmap captureWithOpacity(Float opacity) {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.scrimOpacity = opacity;
        profile.dark.scrimOpacity = opacity;
        ThemeProfileStore.apply(profile);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> scenario.onActivity(activity -> {
            Setting.putTmdbDetailTheme(Setting.DETAIL_STYLE_NATIVE);
            ThemeController.applyFromPreferences(activity);
            TmdbPerson person = new TmdbPerson(1, "Pixel Probe", "Actor", "", "Acting", "biography");
            TmdbPersonDialog.show(activity, person, null);
        }));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(500);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("screenshot unavailable", bitmap);
        scenario.recreate();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return bitmap;
    }

    private static int changedPixels(Bitmap before, Bitmap after, Rect area) {
        int changed = 0;
        for (int y = area.top; y < area.bottom; y += 2) {
            for (int x = area.left; x < area.right; x += 2) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changed++;
            }
        }
        return changed;
    }

    private static int changedPixelsAroundPanelCenter(Bitmap before, Bitmap after) {
        int changed = 0;
        int cx = before.getWidth() / 2;
        int cy = before.getHeight() / 2;
        for (int y = cy - 20; y <= cy + 20; y += 2) {
            for (int x = cx - 20; x <= cx + 20; x += 2) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changed++;
            }
        }
        return changed;
    }

    private static void dumpSample(String tag, Bitmap bitmap, Rect area) {
        int first = bitmap.getPixel(area.left + 4, area.top + 4);
        int mid = bitmap.getPixel(area.centerX(), area.centerY());
        android.util.Log.i("TmdbScrimPixel", tag + " first=#" + Integer.toHexString(first)
                + " mid=#" + Integer.toHexString(mid) + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
    }
}
