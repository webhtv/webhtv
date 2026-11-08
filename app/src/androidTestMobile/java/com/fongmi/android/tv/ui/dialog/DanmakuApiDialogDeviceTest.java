package com.fongmi.android.tv.ui.dialog;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.theme.TmdbScrimHostActivity;
import com.fongmi.android.tv.ui.fragment.SettingDanmakuFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Exercises the real mobile dialog and its system text-action surfaces on a device. */
@RunWith(AndroidJUnit4.class)
public class DanmakuApiDialogDeviceTest {

    private static final String URL = "https://example.com/danmaku?key=clipboard";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private ActivityScenario<TmdbScrimHostActivity> scenario;
    private SettingDanmakuFragment settings;
    private DanmakuApiDialog fragment;
    private AlertDialog dialog;
    private EditText input;
    private ClipboardManager clipboard;
    private ClipData originalClip;
    private String originalApi;

    @Before
    public void setUp() {
        originalApi = DanmakuSetting.getApiUrl();
        scenario = ActivityScenario.launch(TmdbScrimHostActivity.class);
        scenario.onActivity(activity -> {
            clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            originalClip = clipboard.getPrimaryClip();
            settings = SettingDanmakuFragment.newInstance();
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, settings).commitNow();
            openDialog();
        });
        instrumentation.waitForIdleSync();
    }

    @After
    public void tearDown() {
        if (scenario != null) {
            scenario.onActivity(activity -> {
                if (fragment != null) fragment.dismissAllowingStateLoss();
                DanmakuSetting.putApiUrl(originalApi);
                if (originalClip != null) clipboard.setPrimaryClip(originalClip);
                else clipboard.clearPrimaryClip();
            });
            scenario.close();
        }
    }

    @Test
    public void floatingToolbarContextDoesNotInheritPanelTint() {
        scenario.onActivity(activity -> {
            // Android 14 LocalFloatingToolbarPopup applies a DeviceDefault theme on top
            // of the dialog context. AppCompat-only attributes survive that overlay.
            for (int theme : new int[]{android.R.style.Theme_DeviceDefault_Light,
                    android.R.style.Theme_DeviceDefault}) {
                Context floatingContext = new ContextThemeWrapper(dialog.getContext(), theme);
                TypedArray tint = floatingContext.obtainStyledAttributes(
                        new int[]{androidx.appcompat.R.attr.backgroundTint});
                try {
                    assertFalse("panel backgroundTint leaked into system text actions", tint.hasValue(0));
                } finally {
                    tint.recycle();
                }
            }
            TypedArray panel = dialog.getContext().obtainStyledAttributes(
                    new int[]{androidx.appcompat.R.attr.alertDialogStyle});
            try {
                assertEquals("rounded dialog appearance is still selected",
                        R.style.MaterialAlertDialog_WebHTV_Rounded, panel.getResourceId(0, 0));
            } finally {
                panel.recycle();
            }
        });
    }

    @Test
    public void typingFloatingToolbarCopyCutPasteAndPositiveSaveKeepWorking() {
        scenario.onActivity(activity -> {
            input.setText("");
            input.requestFocus();
        });
        instrumentation.waitForIdleSync();
        instrumentation.sendStringSync(URL);
        instrumentation.waitForIdleSync();
        scenario.onActivity(activity -> assertEquals(URL, input.getText().toString()));

        scenario.onActivity(activity -> {
            input.selectAll();
            assertTrue(input.onTextContextMenuItem(android.R.id.copy));
            assertEquals(URL, clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            assertTrue(input.onTextContextMenuItem(android.R.id.cut));
            assertEquals("", input.getText().toString());
            assertTrue(input.onTextContextMenuItem(android.R.id.paste));
            assertEquals(URL, input.getText().toString());
        });
        instrumentation.waitForIdleSync();

        scenario.onActivity(activity -> {
            input.selectAll();
            ActionMode mode = input.startActionMode(new OverflowActionModeCallback(), ActionMode.TYPE_FLOATING);
            assertNotNull("floating text toolbar must be creatable without crashing", mode);
            mode.finish();
            input.getText().insert(0, "  ");
            input.getText().append("  ");
            assertEquals("field must still hold the URL before saving",
                    "  " + URL + "  ", input.getText().toString());
            // AlertController dispatches button clicks through a Handler, so the
            // listener runs on a later looper pass; assert only after that drains.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        instrumentation.waitForIdleSync();
        scenario.onActivity(activity -> assertEquals(
                "save must retain the exact URL and trim surrounding whitespace",
                URL, DanmakuSetting.getApiUrl()));
        instrumentation.waitForIdleSync();
        scenario.onActivity(activity -> {
            openDialog();
            assertEquals("saved API must survive closing and reopening the dialog",
                    URL, input.getText().toString());
        });
    }

    @Test
    public void cancelDoesNotSaveAndImeDoneStillSaves() {
        scenario.onActivity(activity -> {
            input.setText(URL);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        instrumentation.waitForIdleSync();
        scenario.onActivity(activity -> assertEquals("cancel must not persist the draft URL",
                originalApi, DanmakuSetting.getApiUrl()));
        scenario.onActivity(activity -> {
            openDialog();
            input.setText(URL);
            input.onEditorAction(EditorInfo.IME_ACTION_DONE);
            assertEquals(URL, DanmakuSetting.getApiUrl());
        });
    }

    private static final class OverflowActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            for (int i = 0; i < 20; i++) menu.add("Action " + i);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }

    private void openDialog() {
        DanmakuApiDialog.show(settings);
        settings.getChildFragmentManager().executePendingTransactions();
        fragment = (DanmakuApiDialog) settings.getChildFragmentManager().getFragments().get(0);
        dialog = (AlertDialog) fragment.requireDialog();
        input = dialog.findViewById(R.id.text);
        assertNotNull(input);
    }
}
