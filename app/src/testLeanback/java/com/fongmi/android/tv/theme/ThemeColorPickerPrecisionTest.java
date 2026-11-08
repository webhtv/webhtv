package com.fongmi.android.tv.theme;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class ThemeColorPickerPrecisionTest {

    @Test
    public void openingAndConfirmingDoesNotRoundAnExactHexThroughHsv() {
        ActivityController<AppCompatActivity> controller = activity();
        String[] picked = {null};
        AlertDialog dialog = ThemeColorPickerDialog.create(controller.get(), "Primary", "#DAB9FF", ThemeTokens.dark(), hex -> picked[0] = hex);
        dialog.show();
        ShadowLooper.idleMainLooper();
        EditText input = findInput(dialog.getWindow().getDecorView());
        assertEquals("#DAB9FF", input.getText().toString());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals("#DAB9FF", picked[0]);
        assertFalse(dialog.isShowing());
        controller.pause().stop().destroy();
    }

    @Test
    public void useHexIsAlsoLosslessAndInvalidConfirmationKeepsThePickerOpen() {
        ActivityController<AppCompatActivity> controller = activity();
        String[] picked = {null};
        AlertDialog dialog = ThemeColorPickerDialog.create(controller.get(), "Primary", "#123456", ThemeTokens.light(), hex -> picked[0] = hex);
        dialog.show();
        ShadowLooper.idleMainLooper();
        View root = dialog.getWindow().getDecorView();
        EditText input = findInput(root);
        input.setText("#DAB9FF");
        Button use = findButton(root, controller.get().getString(R.string.theme_editor_hex_apply));
        assertNotNull(use);
        use.performClick();
        assertEquals("#DAB9FF", input.getText().toString());
        input.setText("#ZZZZZZ");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertNull(picked[0]);
        input.setText("#a5c9f0");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals("#A5C9F0", picked[0]);
        controller.pause().stop().destroy();
    }

    private static ActivityController<AppCompatActivity> activity() {
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(R.style.Theme_App);
        return controller.setup();
    }

    private static EditText findInput(View view) {
        if (view instanceof EditText input) return input;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findInput(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button findButton(View view, String label) {
        if (view instanceof Button button && label.contentEquals(button.getText())) return button;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
}
