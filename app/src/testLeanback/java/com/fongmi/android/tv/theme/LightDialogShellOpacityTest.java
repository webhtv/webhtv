package com.fongmi.android.tv.theme;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.github.catvod.utils.Prefers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The dialog shell has exactly one owner for each half of its contract.
 *
 * <p>{@code LightDialog.apply} is the shell owner for its whole call-site family. It
 * builds that shell from the active palette and then hands the very same drawable to
 * {@link ThemeController#bindWindowBackground}, which applies {@code dialogOpacity}
 * itself. Baking the opacity into the shape as well scaled it twice - a profile that
 * set {@code 0.70} produced {@code 0.49} - and, because a translucent fill no longer
 * matches its baseline role in {@link ThemeColorIndex}, it also stopped the shell from
 * following a user {@code surfaceContainerHigh} override.
 *
 * <p>Both halves are asserted on the real drawable the production path passes to the
 * binder, so the guard cannot pass by reading the source instead of the behaviour.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class LightDialogShellOpacityTest {

    public static class Host extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle state) {
            setTheme(R.style.Theme_App);
            super.onCreate(state);
        }
    }

    @Before
    public void reset() {
        Prefers.getPrefers().edit().clear().commit();
    }

    @Test
    public void shellStartsOpaqueAndOnTheSemanticSurfaceRole() {
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            Drawable shell = LightDialog.shell(host.get());
            assertEquals("the shell must ship opaque so dialogOpacity has a single owner",
                    0xFF, fill(shell) >>> 24);
            assertEquals("the shell fill must be the semantic surfaceContainerHigh value",
                    ThemeController.current().colorSurfaceContainerHigh(), fill(shell));
        }
    }

    @Test
    public void dialogOpacityReachesTheShellExactlyOnce() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        // 两份表都设：TV 现在产品默认深色（与上游一致），而 mobile/显式浅色会解析 light 槽。
        // 本测试验证的是「opacity 只到达 shell 一次」这个机制，与哪张表生效无关。
        profile.light.dialogOpacity = 0.70f;
        profile.dark.dialogOpacity = 0.70f;
        assertTrue("the fixture profile must validate", ThemeProfileStore.apply(profile).success());

        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            assertEquals("the fixture must actually activate the profile opacity",
                    0.70f, ThemeController.current().dialogOpacity(), 0.0001f);

            Drawable shell = LightDialog.shell(host.get());
            assertEquals("the shell must ship opaque, so only the binder scales it",
                    0xFF, fill(shell) >>> 24);
            ThemeController.bindWindowBackground(shell);

            // 0.70 * 255 = 178.5, and Math.round takes it up to 179. Baking the opacity into
            // the shape as well used to produce 125 here (0.70 * 0.70).
            assertEquals("dialogOpacity must reach the shell exactly once", 179, fill(shell) >>> 24);
        }
    }

    private static int fill(Drawable drawable) {
        assertTrue("unexpected shell drawable: " + drawable, drawable instanceof GradientDrawable);
        GradientDrawable shape = (GradientDrawable) drawable;
        assertNotNull("the shell fill must exist", shape.getColor());
        return shape.getColor().getDefaultColor();
    }
}
