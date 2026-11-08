package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.cache.CacheAvailability;
import com.fongmi.android.tv.cache.CacheMeasurement;
import com.fongmi.android.tv.cache.CacheModuleId;
import com.fongmi.android.tv.cache.CacheSnapshot;
import com.fongmi.android.tv.databinding.DialogCacheManagementBinding;
import com.fongmi.android.tv.utils.Util;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Measures the real dialog views without starting scans, changing policies, or deleting cache. */
public class CacheManagementDialogLayoutTest {

    @Test
    public void testNarrowChinesePortrait() {
        verifyLayout(320, 640, 1f, Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    public void testLargeFontChinesePortrait() {
        verifyLayout(360, 800, 1.3f, Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    public void testLargestFontNarrowPortrait() {
        verifyLayout(320, 640, 2f, Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    public void testEnglishLandscape() {
        verifyLayout(640, 360, 1f, Locale.ENGLISH);
    }

    private void verifyLayout(int screenWidthDp, int screenHeightDp, float fontScale, Locale locale) {
        assumeTrue("Mobile-only layout regression", Util.isMobile());
        runOnMainThread(() -> {
            Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Configuration configuration = new Configuration(target.getResources().getConfiguration());
            configuration.screenWidthDp = screenWidthDp;
            configuration.screenHeightDp = screenHeightDp;
            configuration.smallestScreenWidthDp = Math.min(screenWidthDp, screenHeightDp);
            configuration.orientation = screenWidthDp > screenHeightDp
                    ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
            configuration.fontScale = fontScale;
            configuration.setLocale(locale);
            Context context = new ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_App);
            DialogCacheManagementBinding binding = DialogCacheManagementBinding.inflate(LayoutInflater.from(context));
            CacheManagementDialog dialog = new CacheManagementDialog() {
                @Override
                public Context getContext() {
                    return context;
                }
            };
            try {
                Field field = CacheManagementDialog.class.getDeclaredField("binding");
                field.setAccessible(true);
                field.set(dialog, binding);
                List<CacheMeasurement> modules = new ArrayList<>();
                for (CacheModuleId id : CacheModuleId.values()) {
                    modules.add(new CacheMeasurement(id, 37L * 1024 * 1024, 203,
                            1791198720000L, 1791198720000L, CacheAvailability.AVAILABLE, List.of()));
                }
                Method render = CacheManagementDialog.class.getDeclaredMethod("renderModules", CacheSnapshot.class);
                render.setAccessible(true);
                render.invoke(dialog, new CacheSnapshot(0, 87L * 1024 * 1024,
                        881L * 1024 * 1024, 0, 0, modules, List.of()));
                dialog.initEvent();
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
            binding.summary.setText(context.getString(R.string.cache_management_summary_with_quota, "87.6 MB", "881.9 MB"));
            binding.status.setText(context.getString(R.string.cache_cleanup_done, "163.7 MB", 1));
            // Include the third footer action: it must remain readable while cleanup is running.
            binding.cancel.setVisibility(View.VISIBLE);
            float density = context.getResources().getDisplayMetrics().density;
            int width = Math.round(screenWidthDp * 0.9f * density);
            int height = Math.round(screenHeightDp * 0.9f * density);
            View root = binding.getRoot();
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);

            assertEquals(CacheModuleId.values().length, binding.modules.getChildCount());
            for (int index = 0; index < binding.modules.getChildCount(); index++) {
                ViewGroup row = (ViewGroup) binding.modules.getChildAt(index);
                View detail = row.getChildAt(0);
                assertEquals("Module details must get the full row width, not leftover button space",
                        row.getWidth() - row.getPaddingLeft() - row.getPaddingRight(), detail.getWidth());
            }
            assertVisibleTextFits(root);
            for (View action : new View[]{binding.autoCleanup, binding.retention, binding.totalLimit,
                    binding.cleanupLight, binding.cleanupStandard, binding.cleanupDeep,
                    binding.cancel, binding.refresh, binding.close}) {
                assertTrue("Action needs a readable touch target", action.getWidth() >= 48 * density - 1);
                assertTrue("Action needs a readable touch target", action.getHeight() >= 48 * density - 1);
                assertTrue("Existing action must stay clickable", action.hasOnClickListeners());
            }
        });
    }

    private void runOnMainThread(Runnable check) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                check.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private void assertVisibleTextFits(View view) {
        if (view.getVisibility() == View.GONE) return;
        ViewGroup parent = view.getParent() instanceof ViewGroup group ? group : null;
        if (parent != null) {
            assertTrue("Child extends beyond the left edge", view.getLeft() >= parent.getPaddingLeft());
            assertTrue("Child extends beyond the right edge", view.getRight() <= parent.getWidth() - parent.getPaddingRight());
        }
        if (view instanceof TextView text && text.length() > 0) {
            String message = "Clipped label: " + text.getText();
            Layout layout = text.getLayout();
            assertNotNull(message, layout);
            assertTrue(message, text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom()
                    >= layout.getHeight());
            assertEquals(message, text.length(), layout.getLineEnd(layout.getLineCount() - 1));
            for (int line = 0; line < layout.getLineCount(); line++) {
                assertEquals(message, 0, layout.getEllipsisCount(line));
                assertTrue(message, layout.getLineMax(line)
                        <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
            }
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                assertVisibleTextFits(group.getChildAt(index));
            }
        }
    }
}
