package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.PluginManagerActivity;
import com.fongmi.android.tv.utils.Util;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class PluginManagerMobileUiDeviceTest {

    @Test
    public void importActionsRemainReadableAtNarrowWidthsAndLargeFonts() {
        Assume.assumeTrue(Util.isMobile());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Locale locale : new Locale[]{Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH}) {
                for (float scale : new float[]{1f, 1.3f, 2f}) {
                    for (int width : new int[]{320, 360, 640}) {
                        Context context = context(locale, scale);
                        View root = LayoutInflater.from(context).inflate(R.layout.activity_plugin_manager, null);
                        root.findViewById(R.id.emptyHint).setVisibility(View.VISIBLE);
                        layout(root, context, width, width == 640 ? 320 : 720);
                        for (int id : new int[]{R.id.importPlugin, R.id.remoteImportPlugin, R.id.reloadPlugins}) {
                            TextView button = root.findViewById(id);
                            assertReadable(button, context, locale + " font=" + scale + " width=" + width);
                            assertTrue("action stays inside screen", button.getRight() <= ((View) button.getParent()).getWidth());
                        }
                    }
                }
            }
        });
    }

    @Test
    public void entryActionsHaveTouchTargetsWithoutSqueezingMetadata() {
        Assume.assumeTrue(Util.isMobile());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context(Locale.SIMPLIFIED_CHINESE, 1.3f);
            View row = LayoutInflater.from(context).inflate(R.layout.adapter_plugin_entry, null);
            TextView name = row.findViewById(R.id.entryName);
            TextView meta = row.findViewById(R.id.entryMeta);
            name.setText("用于展示移动端插件名称与阅读体验的测试插件");
            meta.setText("版本 2026.11.09 · 运行时 QuickJS · 启用");
            layout(row, context, 288, 0);
            assertTrue("metadata has more than half the row width", meta.getWidth() > row.getWidth() / 2);
            for (int id : new int[]{R.id.entryParams, R.id.entryDelete, R.id.entryEnabled}) {
                View action = row.findViewById(id);
                assertTrue("touch width >= 48dp", action.getWidth() >= dp(context, 48));
                assertTrue("touch height >= 48dp", action.getHeight() >= dp(context, 48));
            }
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void busyStateDisablesTheEmptyImportEntryAndRestoresIt() {
        Assume.assumeTrue(Util.isMobile());
        try (ActivityScenario<PluginManagerActivity> scenario = ActivityScenario.launch(PluginManagerActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Method bind = PluginManagerActivity.class.getDeclaredMethod("bindEntries", java.util.List.class);
                    bind.setAccessible(true);
                    bind.invoke(activity, java.util.Collections.singletonList(new PluginSourceConfig.Entry(
                            "mobile.ui.test", "移动端测试插件", "1.0", "js", "", true, 0)));
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                View root = (View) activity.findViewById(R.id.toolbar).getParent();
                androidx.core.view.WindowInsetsCompat actual = androidx.core.view.ViewCompat.getRootWindowInsets(root);
                assertTrue("real window insets are available", actual != null);
                androidx.core.graphics.Insets actualBars = actual.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                        | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, actual);
                assertTrue("bottom gutter includes real navigation clearance",
                        root.getPaddingBottom() == dp(activity, 16) + actualBars.bottom);
                androidx.core.view.WindowInsetsCompat insets = new androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                                androidx.core.graphics.Insets.of(10, 20, 30, 40)).build();
                for (int pass = 0; pass < 2; pass++) {
                    androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets);
                    assertTrue("left gutter survives system insets", root.getPaddingLeft() == dp(activity, 16) + 10);
                    assertTrue("top clearance survives system insets", root.getPaddingTop() == dp(activity, 8) + 20);
                    assertTrue("right gutter survives system insets", root.getPaddingRight() == dp(activity, 16) + 30);
                    // Pre-Android 11 dispatch clamps synthetic navigation insets to the real stable bounds.
                    assertTrue("bottom gutter is preserved", root.getPaddingBottom() >= dp(activity, 16));
                }
                androidx.core.view.ViewCompat.requestApplyInsets(root);
                androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.pluginList);
                androidx.recyclerview.widget.RecyclerView.Adapter adapter = list.getAdapter();
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = adapter.createViewHolder(list, 0);
                invoke(activity, "beginBusy");
                adapter.bindViewHolder(holder, 0);
                assertTrue("progress is visible while busy", activity.findViewById(android.R.id.content)
                        .findViewWithTag("pluginManagerProgress").getVisibility() == View.VISIBLE);
                for (int id : new int[]{R.id.entryEnabled, R.id.entryParams, R.id.entryDelete}) {
                    assertFalse("entry actions disabled while busy", holder.itemView.findViewById(id).isEnabled());
                }
                for (int id : new int[]{R.id.importPlugin, R.id.emptyImport, R.id.remoteImportPlugin, R.id.reloadPlugins}) {
                    assertFalse("all import entries disabled while busy", activity.findViewById(id).isEnabled());
                }
                invoke(activity, "endBusy");
                adapter.bindViewHolder(holder, 0);
                assertTrue("progress is hidden when idle", activity.findViewById(android.R.id.content)
                        .findViewWithTag("pluginManagerProgress").getVisibility() == View.GONE);
                for (int id : new int[]{R.id.entryEnabled, R.id.entryParams, R.id.entryDelete}) {
                    assertTrue("entry actions restored", holder.itemView.findViewById(id).isEnabled());
                    assertTrue("entry control identifies its plugin", holder.itemView.findViewById(id)
                            .getContentDescription().toString().contains("移动端测试插件"));
                }
                for (int id : new int[]{R.id.importPlugin, R.id.emptyImport, R.id.remoteImportPlugin, R.id.reloadPlugins}) {
                    assertTrue("all import entries restored", activity.findViewById(id).isEnabled());
                }
            });
            capture("plugin-manager-portrait.png");
            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertTrue("rotated activity is usable",
                    activity.findViewById(R.id.remoteImportPlugin).isEnabled()));
            capture("plugin-manager-landscape.png");
        }
    }

    private static void capture(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertTrue("device screenshot is available", image != null);
        java.io.File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalCacheDir();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name))) {
            assertTrue("device screenshot saved", image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        } finally {
            image.recycle();
        }
    }

    private static Context context(Locale locale, float fontScale) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration configuration = new Configuration(target.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.fontScale = fontScale;
        return new ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_WebHTV);
    }

    private static void layout(View root, Context context, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(dp(context, width), View.MeasureSpec.EXACTLY),
                height == 0 ? View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        : View.MeasureSpec.makeMeasureSpec(dp(context, height), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private static void assertReadable(TextView text, Context context, String scenario) {
        assertTrue(scenario + " touch height", text.getHeight() >= dp(context, 48));
        assertTrue(scenario + " text layout exists", text.getLayout() != null);
        assertTrue(scenario + " no vertical character stacking", text.getLineCount() <= 2);
        for (int line = 0; line < text.getLineCount(); line++) {
            assertTrue(scenario + " no clipped label", text.getLayout().getEllipsisCount(line) == 0);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
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
}
