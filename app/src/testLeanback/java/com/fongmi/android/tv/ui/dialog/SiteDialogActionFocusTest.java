package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.View;

import com.fongmi.android.tv.databinding.DialogSiteBinding;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SiteDialogActionFocusTest {

    private ActivityController<Activity> controller;
    private DialogSiteBinding binding;
    private SiteDialog dialog;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        binding = DialogSiteBinding.inflate(activity.getLayoutInflater());
        binding.searchBar.setVisibility(View.GONE);
        binding.action.setVisibility(View.VISIBLE);
        binding.mode.setVisibility(View.GONE);
        activity.setContentView(binding.getRoot());
        controller.visible();
        binding.getRoot().measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        binding.getRoot().layout(0, 0, 1920, 1080);
        dialog = SiteDialog.create();
        ReflectionHelpers.setField(dialog, "binding", binding);
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void switchModeKeepsBothBulkButtonsInTheRemoteFocusChain() {
        setLoadedMode(0);

        assertTrue(binding.select.isEnabled());
        assertTrue(binding.cancel.isEnabled());
        assertTrue(binding.change.requestFocus());
        assertSame(binding.select, binding.change.focusSearch(View.FOCUS_DOWN));
        assertTrue(binding.select.requestFocus());
        assertSame(binding.cancel, binding.select.focusSearch(View.FOCUS_DOWN));
        assertTrue(binding.cancel.requestFocus());
        assertSame(binding.select, binding.cancel.focusSearch(View.FOCUS_UP));
        assertSame(binding.change, binding.select.focusSearch(View.FOCUS_UP));
    }

    @Test
    public void bulkChangesStillRequireSearchOrChangeMode() {
        for (int type : new int[]{0, 1, 2, 0}) {
            setLoadedMode(type);
            assertTrue(binding.select.isEnabled());
            assertTrue(binding.cancel.isEnabled());
            if (type == 0) {
                assertFalse(binding.select.isClickable());
                assertFalse(binding.cancel.isClickable());
            } else {
                assertTrue(binding.select.isClickable());
                assertTrue(binding.cancel.isClickable());
            }
        }
    }

    @Test
    public void loadingStillDisablesAllSiteEditingActions() {
        for (int type : new int[]{0, 1, 2}) {
            setLoadedMode(type);
            setActionsEnabled(false);
            assertFalse(binding.search.isEnabled());
            assertFalse(binding.change.isEnabled());
            assertFalse(binding.select.isEnabled());
            assertFalse(binding.cancel.isEnabled());
        }
    }

    private void setLoadedMode(int type) {
        ReflectionHelpers.callInstanceMethod(dialog, "setType", ClassParameter.from(int.class, type));
        // Isolate the action state from site loading, databases and network requests.
        setActionsEnabled(true);
    }

    private void setActionsEnabled(boolean enabled) {
        ReflectionHelpers.callInstanceMethod(dialog, "setActionEnabled", ClassParameter.from(boolean.class, enabled));
    }
}
