package com.fongmi.android.tv.theme;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.ThemeDialog;
import com.github.catvod.utils.Prefers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class ThemeDialogModeTest {
    public static class Host extends AppCompatActivity {
        @Override protected void onCreate(android.os.Bundle state) {
            setTheme(R.style.Theme_App);
            super.onCreate(state);
        }
    }

    @Before public void reset() {
        Prefers.getPrefers().edit().clear().commit();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Test public void saveDarkThenLightPersistsBothDirectionsAndReopensSelected() {
        saveAndReopen(true);
        saveAndReopen(false);
    }

    private void saveAndReopen(boolean dark) {
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeDialog dialog = open(host.get());
            click(dialog, dark ? R.string.theme_editor_dark : R.string.theme_editor_light);
            assertEquals(dark ? -1 : 1, Setting.getThemeMode());
            click(dialog, R.string.theme_editor_apply);
            assertEquals(dark ? 1 : 0, Setting.getThemeMode());
            assertEquals(dark ? "dark" : "light", ThemeProfileStore.load().mode);
            ThemeController.applyFromPreferences(null);
            assertEquals(dark ? ThemeTokens.dark().colorSurface() : ThemeTokens.light().colorSurface(),
                    ThemeController.current().colorSurface());
            assertEquals(dark ? ThemeTokens.dark().colorPrimary() : ThemeTokens.light().colorPrimary(),
                    ThemeController.current().colorPrimary());
            ShadowLooper.idleMainLooper();
        }
        var host = Robolectric.buildActivity(Host.class).setup();
        ThemeDialog reopened = open(host.get());
        assertTrue(find(reopened.getDialog().getWindow().getDecorView(),
                host.get().getString(dark ? R.string.theme_editor_dark : R.string.theme_editor_light)).isSelected());
        reopened.dismissNow();
        host.pause().stop().destroy();
    }

    @Test public void cancelDoesNotPublishModeOrProfile() {
        String original = ThemeProfileCodec.encode(ThemeProfileStore.load());
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeDialog dialog = open(host.get());
            click(dialog, R.string.theme_editor_dark);
            click(dialog, R.string.theme_editor_cancel);
            assertEquals(-1, Setting.getThemeMode());
            assertEquals(original, ThemeProfileCodec.encode(ThemeProfileStore.load()));
        }
    }

    @Test public void savingColorsWithoutModeSelectionPreservesFollowSystem() {
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeDialog dialog = open(host.get());
            click(dialog, R.string.theme_editor_apply);
            assertEquals(-1, Setting.getThemeMode());
        }
    }

    private static ThemeDialog open(Host host) {
        ThemeDialog dialog = new ThemeDialog();
        dialog.showNow(host.getSupportFragmentManager(), "theme");
        return dialog;
    }

    private static void click(ThemeDialog dialog, int label) {
        View view = find(dialog.getDialog().getWindow().getDecorView(), dialog.getString(label));
        assertNotNull(view);
        view.performClick();
    }

    private static View find(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
}
