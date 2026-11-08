package com.fongmi.android.tv.ui.dialog;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

/**
 * Window sizing for the theme colour editor.
 *
 * <p>The editor previously inherited Material's default alert width, so the 13 colour
 * slots, the 3 opacity sliders and the live preview were squeezed into a narrow column
 * and the dialog covered only about 58% x 70% of a 1920x1080 screen - measured on
 * device at [405,160][1515,920]. It now follows the same contract as the ad-block
 * statistics dialog: fill the screen minus a constant safety margin.
 *
 * <p>The margin is a fixed dp value rather than a screen percentage. A percentage
 * silently changes the usable area with the panel resolution, while the editor's rows
 * have a fixed dp height and the preview needs a predictable amount of room; a fixed
 * margin keeps the same physical gutter on every device. It also keeps the dialog from
 * ever reaching the very edge of an overscanning TV panel.
 *
 * <p>The gutter is subtracted from the system-bar-safe area, not from the raw display
 * bounds. {@code ResUtil.getScreenHeight} returns the full display on API 30+, so sizing
 * the window from it pushed the footer's cancel/reset/save row under the navigation bar
 * and the buttons were cut off. {@link #safeArea} subtracts the window's status-bar,
 * navigation-bar and display-cutout insets instead, so the result stays inside every
 * system bar and any TV overscan.
 *
 * <p>The same contract also sizes the playback-link health report dialog
 * ({@code SiteHealthReportDialog}), whose footer cancel button was clipped for exactly the
 * same reason. The arithmetic lives here so the two dialogs cannot drift apart.
 */
final class ThemeDialogLayout {

    /** Matches the ad-block statistics dialog's mobile gutter. */
    static final int MOBILE_MARGIN_DP = 16;

    /** Matches the ad-block statistics dialog's television gutter. */
    static final int LEANBACK_MARGIN_DP = 24;

    private ThemeDialogLayout() {
    }

    /**
     * The system-bar-safe area the editor may occupy.
     *
     * @param width  usable width, already excluding the status/navigation bars and cutouts
     * @param height usable height, already excluding the status/navigation bars and cutouts
     */
    record Area(int width, int height) {
    }

    /**
     * The four system-bar insets that have to be kept clear, in pixels.
     */
    record Insets(int left, int top, int right, int bottom) {

        static Insets none() {
            return new Insets(0, 0, 0, 0);
        }

        /**
         * True when every edge is zero, i.e. the source carried no usable information.
         *
         * <p>A floating dialog window on API 28 reports {@code systemBars()} as all zeros even
         * while a navigation bar is on screen (measured: the window's own insets are
         * {@code [0,0][0,0]} while the display's stable area is {@code [0,42][1080,1830]}).
         * Treating that as "no bars" is what let the editor grow past the navigation bar, so an
         * all-zero report must stay indistinguishable from "unknown" and leave the host-content
         * fallback in charge.</p>
         */
        boolean isEmpty() {
            return left <= 0 && top <= 0 && right <= 0 && bottom <= 0;
        }

        /**
         * Insets implied by the display bounds minus the host content view.
         *
         * <p>The host already gave up that space to the status bar, the navigation bar, a
         * display cutout and TV overscan, which makes this a reliable second source when the
         * dialog window cannot report its own insets. A zero-sized content view means
         * "unknown" and reports no insets.
         */
        static Insets ofContent(int screenWidth, int screenHeight, int contentWidth, int contentHeight) {
            if (contentWidth <= 0 || contentHeight <= 0) return none();
            return new Insets(0, 0, Math.max(0, screenWidth - contentWidth),
                    Math.max(0, screenHeight - contentHeight));
        }

        /** Never negative, never wider or taller than the display itself. */
        Insets clamped(int screenWidth, int screenHeight) {
            int l = Math.max(0, left);
            int t = Math.max(0, top);
            int r = Math.max(0, Math.min(right, Math.max(0, screenWidth - l)));
            int b = Math.max(0, Math.min(bottom, Math.max(0, screenHeight - t)));
            return new Insets(l, t, r, b);
        }
    }

    /** Screen edge gutter in dp for the running flavour. */
    static int marginDp(boolean leanback) {
        return leanback ? LEANBACK_MARGIN_DP : MOBILE_MARGIN_DP;
    }

    /**
     * The display minus the system-bar insets, or the whole display when that leaves no room.
     *
     * <p>On a 1080x1920 phone with a 42px status bar and a 90px navigation bar this is
     * 1080x1788, and the window manager centres the resulting 1732px-tall window in the
     * bar-safe area, so it spans y=[70,1802] and clears the navigation bar at y=1830.
     * Sizing from the raw display made the window 1864px tall instead, which is what put
     * the footer's save button under that bar.
     */
    static Area safeArea(int screenWidth, int screenHeight, Insets insets) {
        int screenW = Math.max(1, screenWidth);
        int screenH = Math.max(1, screenHeight);
        Insets bars = (insets == null ? Insets.none() : insets).clamped(screenW, screenH);
        int width = screenW - bars.left() - bars.right();
        int height = screenH - bars.top() - bars.bottom();
        if (width <= 0 || height <= 0) return fullScreen(screenW, screenH);
        return new Area(width, height);
    }

    /** The whole display; used only when the system bars would leave no usable area. */
    static Area fullScreen(int screenWidth, int screenHeight) {
        return new Area(Math.max(1, screenWidth), Math.max(1, screenHeight));
    }

    /** Window width that keeps {@code margin} on both sides, never below one pixel. */
    static int width(int screenWidth, int margin) {
        return Math.max(1, Math.max(1, screenWidth) - Math.max(0, margin) * 2);
    }

    /** Window height that keeps {@code margin} above and below, never below one pixel. */
    static int height(int screenHeight, int margin) {
        return Math.max(1, Math.max(1, screenHeight) - Math.max(0, margin) * 2);
    }

    /**
     * The panel drawable the editor must keep as its window background.
     *
     * <p>{@code WebHtvAlertDialogBuilder} paints this editor's panel through the window
     * background rather than through a view: the title row, the colour slots, the weighted
     * scroll area and the action buttons carry no background of their own. Material wraps
     * that panel in an {@link InsetDrawable} gutter, which is what kept the panel narrower
     * than the window, so the wrapper is dropped now that the window carries the margin
     * itself.
     *
     * <p>The panel itself has to be kept. Replacing the window background with a transparent
     * fill is only safe for a dialog whose layout supplies its own card - the ad-block
     * statistics dialog does - while here it erased the panel and left the settings page
     * visible through the whole editor.
     *
     * @return the inset-free panel, or {@code null} when the window carries no background.
     */
    static Drawable panelBackground(Drawable windowBackground) {
        Drawable panel = windowBackground;
        while (panel instanceof InsetDrawable inset) {
            panel = inset.getDrawable();
        }
        return panel;
    }
}
