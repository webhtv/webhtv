package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fongmi.android.tv.utils.ResUtil;

import java.util.WeakHashMap;

/**
 * The screen area a floating dialog may occupy without colliding with the system bars.
 *
 * <p>{@code ResUtil.getScreenWidth/Height} return the whole display, not the area the window
 * manager actually offers a floating dialog: this app draws edge to edge, so on phones the value
 * still includes the status bar and the navigation/gesture bar. A dialog window sized (or capped)
 * from that value is taller than the bar-safe area, and its bottom row - a fixed-height action
 * button - ends up painted under the navigation bar. The theme colour editor and the playback
 * health report were both clipped that way; every dialog that sizes its window or an inner
 * scroll/list cap from the screen now goes through this class instead.
 *
 * <p>The bar-safe area is the display minus the dialog window's own insets, falling back to the
 * host activity's root insets and then to the space the host content view already gave up. An
 * all-zero inset report is treated as unknown rather than as "no bars", because a floating dialog
 * window on API 28 keeps reporting zeros while the navigation bar is on screen. The math itself
 * stays in {@link ThemeDialogLayout} so every dialog shares one implementation.
 *
 * <p>On a television, or in any fullscreen/immersive host, the resolved insets are zero and this
 * class returns exactly the old display-based numbers, so no television geometry changes.
 */
final class DialogSafeArea {

    private static final WeakHashMap<View, ThemeDialogLayout.Insets> OBSERVED = new WeakHashMap<>();

    private DialogSafeArea() {
    }

    /** Normalises one platform inset report into the four edges a dialog must keep clear of. */
    static ThemeDialogLayout.Insets of(WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
        // Only the bottom edge takes the gesture area into account: some OEM gesture-navigation
        // builds under-report systemBars().bottom while the handle still covers the window, but
        // letting the left/right gesture regions into the max would needlessly narrow dialogs on
        // three-button-navigation phones.
        return new ThemeDialogLayout.Insets(bars.left, bars.top, bars.right, Math.max(bars.bottom, gestures.bottom));
    }

    /** The bar-safe area for a dialog window, or for its host when the window is unknown. */
    static ThemeDialogLayout.Area area(Activity activity, Window window) {
        ThemeDialogLayout.Insets insets = window == null ? null : OBSERVED.get(window.getDecorView());
        if (insets == null || insets.isEmpty()) insets = hostInsets(activity);
        int displayWidth = ResUtil.getScreenWidth(activity);
        int displayHeight = ResUtil.getScreenHeight(activity);
        if (insets.isEmpty()) {
            insets = ThemeDialogLayout.Insets.ofContent(displayWidth, displayHeight, contentWidth(activity), contentHeight(activity));
        }
        return ThemeDialogLayout.safeArea(displayWidth, displayHeight, insets);
    }

    static ThemeDialogLayout.Area area(Activity activity) {
        return area(activity, null);
    }

    /** Bar-safe width for the given dialog window (never below one pixel). */
    static int width(Activity activity, Window window) {
        return Math.max(1, area(activity, window).width());
    }

    /** Bar-safe height for the given dialog window (never below one pixel). */
    static int height(Activity activity, Window window) {
        return Math.max(1, area(activity, window).height());
    }

    /** Bar-safe width for a context that may not be an Activity; falls back to the display. */
    static int width(Context context) {
        Activity activity = activityOf(context);
        return activity == null ? ResUtil.getScreenWidth(context) : width(activity, null);
    }

    /** Bar-safe height for a context that may not be an Activity; falls back to the display. */
    static int height(Context context) {
        Activity activity = activityOf(context);
        return activity == null ? ResUtil.getScreenHeight(context) : height(activity, null);
    }

    /**
     * Re-runs {@code resize} whenever the system-bar insets change, so a window sized before the
     * platform reported its insets - or before a rotation - converges on the bar-safe area instead
     * of staying clipped. The listener only fires when the reported insets really change, so it
     * cannot loop with the resize that {@code window.setLayout} triggers.
     */
    static void follow(Activity activity, Window window, Runnable resize) {
        if (activity == null || window == null) return;
        View decorView = window.getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (view, insets) -> {
            ThemeDialogLayout.Insets next = of(insets);
            ThemeDialogLayout.Insets cached = OBSERVED.get(view);
            if (!next.equals(cached)) {
                OBSERVED.put(view, next);
                resize.run();
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(decorView);
    }

    private static ThemeDialogLayout.Insets hostInsets(Activity activity) {
        if (activity == null) return ThemeDialogLayout.Insets.none();
        try {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
            return insets == null ? ThemeDialogLayout.Insets.none() : of(insets);
        } catch (RuntimeException error) {
            return ThemeDialogLayout.Insets.none();
        }
    }

    private static int contentWidth(Activity activity) {
        View content = activity == null ? null : activity.findViewById(android.R.id.content);
        return content == null ? 0 : content.getWidth();
    }

    private static int contentHeight(Activity activity) {
        View content = activity == null ? null : activity.findViewById(android.R.id.content);
        return content == null ? 0 : content.getHeight();
    }

    private static Activity activityOf(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity activity) return activity;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity activity ? activity : null;
    }
}
