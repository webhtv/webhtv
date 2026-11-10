package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ImageViewCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeMode;
import com.fongmi.android.tv.theme.ThemeProfile;
import com.fongmi.android.tv.theme.ThemeResolver;
import com.fongmi.android.tv.theme.ThemeSeed;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.activity.PluginManagerActivity;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class PluginManagerLeanbackUiDeviceTest {

    private static final String ID = "tv.ui.focus.probe";
    private static final PluginSourceConfig.Entry ENTRY = new PluginSourceConfig.Entry(
            ID, "遥控测试插件", "1.0", "js", "", true, 0);

    @Before
    public void setUp() {
        Assume.assumeTrue(Util.isLeanback());
        AccessibilityServiceInfo info = automation().getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation().setServiceInfo(info);
    }

    @Test
    public void parameterHintPreservesJsonQuotesInAllTranslations() {
        android.content.Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (java.util.Locale locale : new java.util.Locale[]{java.util.Locale.ENGLISH,
                java.util.Locale.SIMPLIFIED_CHINESE, java.util.Locale.TRADITIONAL_CHINESE}) {
            android.content.res.Configuration configuration = new android.content.res.Configuration(
                    target.getResources().getConfiguration());
            configuration.setLocale(locale);
            String hint = target.createConfigurationContext(configuration).getString(R.string.plugin_manager_params_hint);
            String example = hint.substring(hint.indexOf('{'));
            assertTrue(locale + " JSON key quotes are visible", example.contains("\"DOUBAN_TOKEN\""));
            assertTrue(locale + " JSON string quotes are visible", example.contains("\"abc\""));
            assertEquals("abc", com.google.gson.JsonParser.parseString(example).getAsJsonObject()
                    .get("DOUBAN_TOKEN").getAsString());
        }
    }

    @Test
    public void emptyParameterEditorReachesCancelConfirmAndReturnsToEditing() {
        try (ActivityScenario<PluginManagerActivity> scenario = ActivityScenario.launch(PluginManagerActivity.class)) {
            openParams(scenario);
            setText("");
            focusEditor();
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertFocused("android:id/button2");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFocused("android:id/button1");
            capture("plugin-manager-tv-params.png");
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            assertFocused("android:id/button2");
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertTrue("up returns to the JSON editor", editor().isFocused());
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            assertDialogClosed(scenario);
        }
    }

    @Test
    public void multilineCaretNavigationAndJsonValidationRemainFunctional() {
        String previous = PluginEnvironmentStore.get().get(ID);
        try (ActivityScenario<PluginManagerActivity> scenario = ActivityScenario.launch(PluginManagerActivity.class)) {
            openParams(scenario);
            String json = "{\n  \"probe\": true\n}";
            setText(json);
            select(0);
            focusEditor();
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue("down inside the document still moves the caret", editor().isFocused());
            assertTrue("caret advances to the following line", editor().getTextSelectionStart() > 0);
            select(json.length());
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertFocused("android:id/button2");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertTrue("confirm can return to editing", editor().isFocused());

            setText("/");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            assertNotNull("invalid JSON does not close the dialog", editor());
            assertEquals("确认无效 JSON 后必须保留用户输入", "/", editor().getText().toString());
            String error = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getString(R.string.plugin_manager_params_invalid);
            assertFalse("validation error remains visible", root().findAccessibilityNodeInfosByText(error).isEmpty());
            assertEquals("invalid input is not saved", previous, PluginEnvironmentStore.get().get(ID));
            capture("plugin-manager-tv-params-error.png");

            focusEditor();
            setText("{\"probe\":true}");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            assertDialogClosed(scenario);
            assertEquals("confirm persists valid JSON", "{\"probe\":true}", PluginEnvironmentStore.get().get(ID));
        } finally {
            if (previous.isEmpty()) PluginEnvironmentStore.get().remove(ID);
            else PluginEnvironmentStore.get().put(ID, previous);
        }
    }

    @Test
    public void parameterButtonsPreserveCustomThemeColorsAndTransparentDefaultStroke() {
        try (ActivityScenario<PluginManagerActivity> scenario = ActivityScenario.launch(PluginManagerActivity.class)) {
            scenario.onActivity(activity -> {
                ThemeTokens previous = ThemeController.current();
                ThemeProfile profile = ThemeProfile.defaultProfile();
                profile.dark.focus = "#00B0FF";
                ThemeTokens tokens = ThemeResolver.resolve(ThemeMode.DARK, ThemeSeed.EXPLICIT,
                        0xFF8E24AA, 0, profile, null, true);
                assertFalse("测试主题必须不同于编译色板", tokens.colorPrimaryContainer()
                        == activity.getColor(R.color.webhtv_color_primary_container));
                assertFalse("配对描边色必须与独立焦点槽区分", tokens.colorOnPrimaryContainer() == tokens.colorFocus());
                TextInputEditText input = new TextInputEditText(activity);
                AlertDialog dialog = new WebHtvAlertDialogBuilder(activity, R.style.Theme_WebHTV_Dialog)
                        .setView(input)
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.dialog_confirm, null)
                        .create();
                try {
                    ThemeController.apply(activity, tokens);
                    dialog.show();
                    LightDialog.apply(dialog);
                    Method method = PluginManagerActivity.class.getDeclaredMethod("wireParameterDialogFocus",
                            AlertDialog.class, TextInputEditText.class);
                    method.setAccessible(true);
                    method.invoke(null, dialog, input);
                    int[][] states = {{android.R.attr.state_enabled},
                            {android.R.attr.state_enabled, android.R.attr.state_focused},
                            {android.R.attr.state_enabled, android.R.attr.state_pressed}, {},
                            {android.R.attr.state_focused}};
                    int[] backgrounds = {Color.TRANSPARENT, tokens.colorPrimaryContainer(),
                            tokens.colorPrimaryContainer(), tokens.colorSurfaceContainerHighest(),
                            tokens.colorSurfaceContainerHighest()};
                    int[] texts = {tokens.colorPrimary(), tokens.colorPrimary(), tokens.colorPrimary(),
                            tokens.colorOnSurfaceVariant(), tokens.colorOnSurfaceVariant()};
                    int[] strokes = {Color.TRANSPARENT, tokens.colorOnPrimaryContainer(),
                            tokens.colorOnPrimaryContainer(), Color.TRANSPARENT, tokens.colorOnPrimaryContainer()};
                    for (int id : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
                        assertTrue("参数操作必须使用 Material 按钮", dialog.getButton(id) instanceof MaterialButton);
                        MaterialButton button = (MaterialButton) dialog.getButton(id);
                        for (int index = 0; index < states.length; index++) {
                            assertEquals("按钮背景必须跟随活动主题，状态 " + index, backgrounds[index],
                                    button.getBackgroundTintList().getColorForState(states[index], 0));
                            assertEquals("按钮文字必须跟随活动主题，状态 " + index, texts[index],
                                    button.getTextColors().getColorForState(states[index], 0));
                            assertEquals("按钮描边必须保留配对焦点色与默认透明，状态 " + index, strokes[index],
                                    button.getStrokeColor().getColorForState(states[index], 0));
                        }
                        assertEquals("参数按钮描边宽度保持不变",
                                Math.round(input.getResources().getDisplayMetrics().density * 2), button.getStrokeWidth());
                    }
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                } finally {
                    dialog.dismiss();
                    ThemeController.apply(activity, previous);
                }
            });
        }
    }

    @Test
    public void topActionsUseConsistentColorsAndBusyFeedback() {
        try (ActivityScenario<PluginManagerActivity> scenario = ActivityScenario.launch(PluginManagerActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                MaterialButton local = activity.findViewById(R.id.importPlugin);
                MaterialButton remote = activity.findViewById(R.id.remoteImportPlugin);
                MaterialButton reload = activity.findViewById(R.id.reloadPlugins);
                for (int[] state : new int[][]{{android.R.attr.state_enabled},
                        {android.R.attr.state_enabled, android.R.attr.state_focused}, {}}) {
                    int expected = local.getBackgroundTintList().getColorForState(state, 0);
                    assertEquals("remote import shares the action palette", expected,
                            remote.getBackgroundTintList().getColorForState(state, 0));
                    assertEquals("reload shares the action palette", expected,
                            reload.getBackgroundTintList().getColorForState(state, 0));
                }
                local.requestFocus();
            });
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFocused("com.silent.android.webhtv:id/remoteImportPlugin");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFocused("com.silent.android.webhtv:id/reloadPlugins");
            capture("plugin-manager-tv-page.png");
            scenario.onActivity(activity -> {
                invoke(activity, "beginBusy");
                for (int id : new int[]{R.id.importPlugin, R.id.remoteImportPlugin, R.id.reloadPlugins, R.id.emptyImport}) {
                    assertFalse("all entries disabled while busy", activity.findViewById(id).isEnabled());
                }
                View progress = activity.findViewById(android.R.id.content).findViewWithTag("pluginManagerProgress");
                assertNotNull("TV busy progress exists", progress);
                assertEquals(View.VISIBLE, progress.getVisibility());
            });
            capture("plugin-manager-tv-busy.png");
            scenario.onActivity(activity -> {
                invoke(activity, "endBusy");
                assertTrue(activity.findViewById(R.id.reloadPlugins).isEnabled());
                View progress = activity.findViewById(android.R.id.content).findViewWithTag("pluginManagerProgress");
                assertEquals(View.GONE, progress.getVisibility());
                View row = android.view.LayoutInflater.from(activity).inflate(R.layout.adapter_plugin_entry, null);
                ThemeController.bindTheme(row);
                android.widget.ImageView action = row.findViewById(R.id.entryDelete);
                assertEquals("unfocused icon follows the surface text role", ThemeController.current().colorOnSurface(),
                        ImageViewCompat.getImageTintList(action).getDefaultColor());
            });
        }
    }

    private static void openParams(ActivityScenario<PluginManagerActivity> scenario) {
        scenario.onActivity(activity -> {
            try {
                Method method = PluginManagerActivity.class.getDeclaredMethod("editParams", PluginSourceConfig.Entry.class);
                method.setAccessible(true);
                method.invoke(activity, ENTRY);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
        idle();
        assertNotNull(editor());
    }

    private static void invoke(PluginManagerActivity activity, String name) {
        try {
            Method method = PluginManagerActivity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(activity);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void assertDialogClosed(ActivityScenario<PluginManagerActivity> scenario) {
        int applicationWindows = 0;
        for (android.view.accessibility.AccessibilityWindowInfo window : automation().getWindows()) {
            if (window.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            applicationWindows++;
            AccessibilityNodeInfo content = window.getRoot();
            if (content != null) assertTrue("parameter dialog is absent", findEditable(content) == null);
        }
        assertEquals("only the manager window remains", 1, applicationWindows);
        scenario.onActivity(activity -> assertTrue("manager page remains usable",
                activity.findViewById(R.id.importPlugin).isShown()));
    }

    private static UiAutomation automation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static void idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try {
            automation().waitForIdle(50, 3000);
        } catch (TimeoutException error) {
            throw new AssertionError("UI did not settle", error);
        }
    }

    private static AccessibilityNodeInfo root() {
        AccessibilityNodeInfo root = automation().getRootInActiveWindow();
        assertNotNull("dialog or activity accessibility root", root);
        return root;
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node.isEditable()) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            AccessibilityNodeInfo found = findEditable(child);
            if (found != null) return found;
        }
        return null;
    }

    private static AccessibilityNodeInfo editor() {
        AccessibilityNodeInfo editor = findEditable(root());
        assertNotNull("JSON input is present", editor);
        return editor;
    }

    private static void focusEditor() {
        AccessibilityNodeInfo input = editor();
        if (!input.isFocused()) input.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        idle();
        assertTrue("JSON input receives focus", editor().isFocused());
    }

    private static void setText(String text) {
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        assertTrue(editor().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments));
        idle();
    }

    private static void select(int position) {
        Bundle arguments = new Bundle();
        arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, position);
        arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, position);
        assertTrue(editor().performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments));
        idle();
    }

    private static void key(int code) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code);
        idle();
    }

    private static void assertFocused(String id) {
        List<AccessibilityNodeInfo> nodes = root().findAccessibilityNodeInfosByViewId(id);
        assertFalse("focus target exists: " + id, nodes.isEmpty());
        assertTrue("remote focus reaches: " + id, nodes.get(0).isFocused());
    }

    private static void capture(String name) {
        idle();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Bitmap image = automation().takeScreenshot();
        assertNotNull(image);
        File directory = instrumentation.getTargetContext().getExternalCacheDir();
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (IOException error) {
            throw new AssertionError(error);
        } finally {
            image.recycle();
        }
    }
}
