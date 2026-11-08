package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The theme colour editor used to inherit Material's default alert width and covered only
 * about 58% x 70% of a 1920x1080 panel (measured on device at [405,160][1515,920]), which
 * left the 13 colour slots, the 3 opacity sliders and the live preview squeezed into a
 * narrow column. It now follows the ad-block statistics dialog contract: fill the screen
 * minus a constant gutter.
 */
public class ThemeDialogLayoutTest {

    @Test
    public void guttersMatchTheAdBlockStatisticsReferenceDialog() {
        assertEquals("mobile gutter must match AdBlockStatsDialog", 16, ThemeDialogLayout.MOBILE_MARGIN_DP);
        assertEquals("television gutter must match AdBlockStatsDialog", 24, ThemeDialogLayout.LEANBACK_MARGIN_DP);
        assertEquals(16, ThemeDialogLayout.marginDp(false));
        assertEquals(24, ThemeDialogLayout.marginDp(true));
    }

    @Test
    public void dialogFillsTheScreenMinusTheGutter() {
        // 1920x1080 at 280dpi is the measured device; the gutter is already in pixels here.
        assertEquals(1920 - 32, ThemeDialogLayout.width(1920, 16));
        assertEquals(1080 - 32, ThemeDialogLayout.height(1080, 16));
        assertEquals(1920 - 48, ThemeDialogLayout.width(1920, 24));
        assertEquals(1080 - 48, ThemeDialogLayout.height(1080, 24));
    }

    /**
     * The editor used to size its window from the raw display bounds. On a portrait phone
     * with a navigation bar the window was taller than the visible area, so the footer's
     * cancel/reset/save row was pushed under the navigation bar and the save button was
     * clipped. The window must be derived from the system-bar-safe area instead.
     */
    @Test
    public void safeAreaExcludesTheSystemBars() {
        // 1080x1920 portrait, 42px status bar, 90px navigation bar.
        ThemeDialogLayout.Area area = ThemeDialogLayout.safeArea(1080, 1920,
                new ThemeDialogLayout.Insets(0, 42, 0, 90));
        assertEquals(1080, area.width());
        assertEquals(1788, area.height());
        // Window = 1788 - 2*28 = 1732 tall; centred in the bar-safe area [42,1830] it spans
        // [70,1802], clear of the navigation bar at y=1830.
        assertEquals(1732, ThemeDialogLayout.height(area.height(), 28));
        assertEquals(70, 42 + (1788 - 1732) / 2);
        assertEquals(1802, 42 + (1788 - 1732) / 2 + 1732);
    }

    @Test
    public void safeAreaAlsoHandlesLandscapeAndCutouts() {
        // 1920x1080 landscape with a 42px status bar and a 90px navigation bar.
        ThemeDialogLayout.Area landscape = ThemeDialogLayout.safeArea(1920, 1080,
                new ThemeDialogLayout.Insets(0, 42, 0, 90));
        assertEquals(1920, landscape.width());
        assertEquals(948, landscape.height());
        // A left display cutout narrows the area as well.
        ThemeDialogLayout.Area cutout = ThemeDialogLayout.safeArea(1080, 1920,
                new ThemeDialogLayout.Insets(60, 42, 0, 90));
        assertEquals(1020, cutout.width());
        assertEquals(1788, cutout.height());
    }

    @Test
    public void contentFallbackInsetsMatchTheHostLayout() {
        // The display minus the host content view is exactly the bar space the host gave up.
        ThemeDialogLayout.Insets fallback = ThemeDialogLayout.Insets.ofContent(1080, 1920, 1080, 1788);
        assertEquals(new ThemeDialogLayout.Insets(0, 0, 0, 132), fallback);
        // A host content view that has not been laid out means "unknown", not "no bars".
        assertEquals(ThemeDialogLayout.Insets.none(), ThemeDialogLayout.Insets.ofContent(1080, 1920, 0, 0));
        // A content view larger than the display must never produce negative insets.
        assertEquals(ThemeDialogLayout.Insets.none(), ThemeDialogLayout.Insets.ofContent(1080, 1920, 1200, 2000));
    }

    @Test
    public void unusableInsetsFallBackToTheWholeDisplay() {
        // No insets at all still yields the full display, not a collapsed window.
        assertEquals(ThemeDialogLayout.fullScreen(1080, 1920),
                ThemeDialogLayout.safeArea(1080, 1920, ThemeDialogLayout.Insets.none()));
        assertEquals(ThemeDialogLayout.fullScreen(1080, 1920), ThemeDialogLayout.safeArea(1080, 1920, null));
        // Insets covering the whole display would leave no room, so the display is used.
        assertEquals(ThemeDialogLayout.fullScreen(1080, 1920),
                ThemeDialogLayout.safeArea(1080, 1920, new ThemeDialogLayout.Insets(600, 1000, 600, 1000)));
        // Insets wider than the display are clamped instead of producing a negative area.
        ThemeDialogLayout.Area clamped = ThemeDialogLayout.safeArea(1080, 1920,
                new ThemeDialogLayout.Insets(5000, 5000, 5000, 5000));
        assertEquals(1080, clamped.width());
        assertEquals(1920, clamped.height());
    }

    /**
     * A floating dialog window on API 28 reports {@code systemBars()} as {@code [0,0,0,0]} even
     * while a navigation bar is on screen (measured: window insets {@code [0,0][0,0]} against a
     * display stable area of {@code [0,42][1080,1830]}). An all-zero report therefore means
     * "unknown", not "no bars", and must not override the host-content fallback - otherwise the
     * editor keeps growing past the navigation bar and the footer buttons are clipped.
     */
    @Test
    public void anAllZeroInsetReportIsTreatedAsUnknown() {
        assertTrue("an all-zero report must be reported as empty/unknown",
                ThemeDialogLayout.Insets.none().isEmpty());
        assertTrue(new ThemeDialogLayout.Insets(0, 0, 0, 0).isEmpty());
        assertFalse(new ThemeDialogLayout.Insets(0, 42, 0, 90).isEmpty());
        assertFalse(new ThemeDialogLayout.Insets(60, 0, 0, 0).isEmpty());
        // The value a real navigation bar produces must never be mistaken for "no bars".
        assertFalse(new ThemeDialogLayout.Insets(0, 0, 0, 132).isEmpty());
    }

    @Test
    public void safeAreaIsNeverSmallerThanOnePixel() {
        ThemeDialogLayout.Area area = ThemeDialogLayout.safeArea(0, 0, ThemeDialogLayout.Insets.none());
        assertEquals(1, area.width());
        assertEquals(1, area.height());
        assertTrue(area.width() > 0 && area.height() > 0);
    }

    @Test
    public void enlargedFootprintIsMateriallyLargerThanTheMeasuredBaseline() {
        int width = ThemeDialogLayout.width(1920, 16);
        int height = ThemeDialogLayout.height(1080, 16);
        // Measured before the change: [405,160][1515,920] => 1110 x 760.
        assertTrue("width must exceed the measured baseline of 1110px", width > 1110);
        assertTrue("height must exceed the measured baseline of 760px", height > 760);
        assertTrue("width must cover more than 90% of the panel", width > 1920 * 0.9);
        assertTrue("height must cover more than 90% of the panel", height > 1080 * 0.9);
    }

    @Test
    public void degenerateScreensNeverProduceANonPositiveWindow() {
        assertEquals(1, ThemeDialogLayout.width(0, 16));
        assertEquals(1, ThemeDialogLayout.height(0, 16));
        assertEquals(1, ThemeDialogLayout.width(10, 16));
        assertEquals(1, ThemeDialogLayout.height(10, 16));
        // A negative gutter is clamped to zero rather than widening past the panel.
        assertEquals(100, ThemeDialogLayout.width(100, -5));
        assertEquals(100, ThemeDialogLayout.height(100, -5));
        // A missing margin must not silently drop the whole measurement.
        assertNotEquals(0, ThemeDialogLayout.width(1080, 0));
        assertEquals(1080, ThemeDialogLayout.width(1080, 0));
    }

    @Test
    public void bothFlavoursApplyTheSharedSizingContract() throws Exception {
        for (String flavour : new String[]{"mobile", "leanback"}) {
            String dialog = read("src/" + flavour + "/java/com/fongmi/android/tv/ui/dialog/ThemeDialog.java");
            assertTrue(flavour + " must size the window from the shared helper",
                    dialog.contains("ThemeDialogLayout.marginDp("));
            // Regression guard: sizing from the raw display bounds pushed the footer's
            // save button under the navigation bar on portrait phones and clipped it.
            assertTrue(flavour + " must derive its window from the system-bar-safe area",
                    dialog.contains("ThemeDialogLayout.safeArea("));
            assertTrue(flavour + " must read the window's own system-bar insets",
                    dialog.contains("ViewCompat.setOnApplyWindowInsetsListener("));
            assertTrue(flavour + " must include the display cutout in those insets",
                    dialog.contains("WindowInsetsCompat.Type.displayCutout()"));
            assertTrue(flavour + " must re-size the window when the real insets arrive",
                    dialog.contains("ViewCompat.requestApplyInsets("));
            assertTrue(flavour + " must fall back to the host content view while insets are unknown",
                    dialog.contains("findViewById(android.R.id.content)"));
            // A floating dialog window on API 28 keeps reporting systemBars() = [0,0,0,0] while a
            // navigation bar is on screen; accepting that as "no bars" re-clips the footer.
            assertTrue(flavour + " must treat an all-zero inset report as unknown",
                    dialog.contains("!windowInsets.isEmpty()"));
            assertTrue(flavour + " must also read the host activity's root window insets",
                    dialog.contains("ViewCompat.getRootWindowInsets(requireActivity().getWindow().getDecorView())"));
            assertFalse(flavour + " must not offset the window; Gravity.CENTER inside the inset-safe area already centres it",
                    dialog.contains("params.x = ") || dialog.contains("params.y = "));
            assertTrue(flavour + " must keep the full-height scroll area inside the safe height",
                    dialog.contains("ViewGroup.LayoutParams.MATCH_PARENT, 0, 1"));
            assertTrue(flavour + " must size the width from the shared helper",
                    dialog.contains("ThemeDialogLayout.width("));
            assertTrue(flavour + " must size the height from the shared helper",
                    dialog.contains("ThemeDialogLayout.height("));
            assertTrue(flavour + " must apply the size to the window",
                    dialog.contains("window.setLayout(width, height)"));
            assertTrue(flavour + " must drop Material's background inset",
                    dialog.contains("decorView.setPadding(0, 0, 0, 0)"));
            assertTrue(flavour + " must let the scroll area absorb the freed height",
                    dialog.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
            assertTrue(flavour + " must configure the window when the dialog starts",
                    dialog.contains("configureWindow(dialog)"));
            // Regression guard: the panel is painted by the window background, so replacing
            // it with a transparent fill (safe only for a dialog whose layout has its own
            // card) erased the whole editor and showed the settings page through it.
            assertTrue(flavour + " must keep the themed panel as the window background",
                    dialog.contains("ThemeDialogLayout.panelBackground(decorView.getBackground())"));
            assertFalse(flavour + " must not replace the panel with a transparent fill",
                    dialog.contains("new ColorDrawable(Color.TRANSPARENT)"));
        }
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
