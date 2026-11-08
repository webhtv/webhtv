package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps the ad-rule ranking user-facing by resolving stored rule IDs to display names. */
public class SiteHealthReportDialogSourceTest {

    @Test
    public void ruleRankingUsesResolvedDisplayNames() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fongmi/android/tv/ui/dialog/SiteHealthReportDialog.java"));

        assertTrue(source.contains("ruleDimensionSummary(report.adBlockedByRule)"));
        assertTrue(source.contains("AdBlockStatsStore.getRuleDisplayName(entry.getKey())"));
        assertFalse(source.contains("dimensionSummary(report.adBlockedByRule)"));
    }

    /**
     * The report body is built in code, so it cannot inherit a theme attribute the way the dialog
     * XML does. A hard-coded translucent black looked fine on the light panel but made every
     * generated row unreadable on the dark one (the XML title, which uses {@code ?attr/colorOnSurface},
     * stayed legible) - so generated text must use resolved semantic roles instead.
     */
    @Test
    public void generatedRowsUseResolvedThemeRolesInsteadOfFixedBlack() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fongmi/android/tv/ui/dialog/SiteHealthReportDialog.java"));

        assertFalse("generated report text must not use a fixed translucent black",
                source.contains("R.color.black"));
        assertTrue("generated text must resolve its colour from the active theme",
                source.contains("ThemeController.current().colorOnSurface"));
        // Status accents are tuned for a light panel; on a dark panel they must be
        // contrast-corrected against the actual panel fill rather than used raw.
        // Match the call site, not the declaration: "readableOnPanel(" also occurs in
        // the method signature, so that spelling stayed true after the call was removed.
        assertTrue("status accents must be contrast-corrected against the panel",
                source.contains("readableOnPanel(color)"));
        assertTrue(source.contains("ThemeController.current().colorSurfaceContainerHigh()"));
    }

    /**
     * The footer cancel button was painted under the navigation bar on phones: the window was
     * sized from the raw display instead of the system-bar-safe area, so the centred floating
     * window's last row fell outside the bar-safe area. The dialog now delegates that resolution
     * to {@link DialogSafeArea} (whose own contract is covered by {@code DialogSafeAreaTest}) and
     * only supplies its gutter.
     */
    @Test
    public void dialogSizesItsWindowFromTheSystemBarSafeArea() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fongmi/android/tv/ui/dialog/SiteHealthReportDialog.java"));

        assertTrue("must derive its window from the shared bar-safe area",
                source.contains("DialogSafeArea.area(requireActivity(), window)"));
        assertTrue("must re-size the window when the real insets arrive",
                source.contains("DialogSafeArea.follow("));
        assertTrue("must size the width from the shared helper",
                source.contains("ThemeDialogLayout.width("));
        assertTrue("must size the height from the shared helper",
                source.contains("ThemeDialogLayout.height("));
        assertTrue("must centre the window inside the bar-safe area",
                source.contains("params.gravity = Gravity.CENTER"));
        assertTrue("must apply the size to the window", source.contains("window.setLayout("));
        assertFalse("must not offset the window; Gravity.CENTER already centres it",
                source.contains("params.x = ") || source.contains("params.y = "));
        assertFalse("must not size the window from the raw display height",
                source.contains("ResUtil.getScreenHeight(requireContext()) - margin * 2"));
        assertFalse("must not size the window from the raw display width",
                source.contains("ResUtil.getScreenWidth(requireContext()) - margin * 2"));
    }
}
