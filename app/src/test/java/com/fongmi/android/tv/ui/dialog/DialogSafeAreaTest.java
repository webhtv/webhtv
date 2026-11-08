package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keeps every screen-sized dialog on the shared system-bar-safe contract.
 *
 * <p>{@code ResUtil.getScreenWidth/Height} return the whole display, while a floating dialog window
 * is laid out inside the bar-safe area. A window (or an inner scroll/list cap) sized from the raw
 * display therefore overflows the bottom of that area on a phone with a navigation/gesture bar, and
 * the dialog's last row - usually a fixed-height action button - is painted under the bar. That is
 * what clipped the playback health report's cancel button; {@link DialogSafeArea} is the single
 * resolution path, and the theme editor keeps its own instrumented copy in {@code ThemeDialog}.
 */
public class DialogSafeAreaTest {

    private static final String DIALOGS = "src/main/java/com/fongmi/android/tv/ui/dialog/";

    @Test
    public void helperResolvesTheBarSafeAreaFromEveryKnownSource() throws Exception {
        String source = read(DIALOGS + "DialogSafeArea.java");

        assertTrue("must read the system bars and the display cutout",
                source.contains("WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()"));
        assertTrue("OEM gesture navigation can under-report systemBars().bottom",
                source.contains("WindowInsetsCompat.Type.systemGestures()"));
        assertTrue(source.contains("Math.max(bars.bottom, gestures.bottom)"));
        assertTrue("must derive the size from the shared, unit-tested math",
                source.contains("ThemeDialogLayout.safeArea("));
        assertTrue("a floating dialog window on API 28 reports all-zero insets while a bar is on screen",
                source.contains("insets.isEmpty()"));
        assertTrue(source.contains("ViewCompat.getRootWindowInsets("));
        assertTrue("must fall back to the space the host content view already gave up",
                source.contains("ThemeDialogLayout.Insets.ofContent("));
        assertTrue("must re-apply the size when the insets change",
                source.contains("ViewCompat.setOnApplyWindowInsetsListener(")
                        && source.contains("ViewCompat.requestApplyInsets("));
        assertFalse("window sizing must not depend on configuration screen dp",
                source.contains("screenHeightDp"));
    }

    @Test
    public void everyScreenSizedDialogAsksTheSharedHelper() throws Exception {
        String[] dialogs = {
                "AiRecommendationInfoDialog", "CacheManagementDialog", "CodecCapabilityDialog",
                "GitCloudDialog", "GroupRuleDialog", "LoginStateLearnDialog", "LoginStatePathDialog",
                "ManagePageDialog", "MpvConfigDialog", "MpvConfigEditorDialog", "MpvCustomButtonDialog",
                "MultiThreadProxyDialog", "PanNetworkDiagnosticDialog", "PlaybackPerformanceDialog",
                "PlaybackPerformanceModal", "PlaybackRemoteSyncDialog", "PlaybackWebhookDialog",
                "PlayerButtonConfigDialog", "RemoteTrustDialog", "ShellProxyDialog", "SiteHealthDialog",
                "SiteHealthReportDialog", "UpdateSettingsDialog", "ViewingRecordSyncDialog",
                "WebHomeExtensionDebugDialog", "WebHomeExtensionDialog",
        };
        for (String dialog : dialogs) {
            String source = read(DIALOGS + dialog + ".java");
            assertTrue(dialog + " must size from the bar-safe area, not the raw display",
                    source.contains("DialogSafeArea."));
            // Any raw-display height in these files is the defect this contract exists to
            // prevent: a window or an inner scroll/list cap taller than the bar-safe area puts
            // the dialog's last row under the navigation bar. Width stays out of the assertion
            // because the navigation bar sits at the bottom; the horizontal-cutout audit is a
            // separate task. Matching the whole call also catches the inner-cap variants that
            // the old exact-string check (`params.height = (int) (...)`) did not.
            assertFalse(dialog + " must not derive any height from the raw display",
                    source.contains("ResUtil.getScreenHeight("));
        }

        for (String flavor : new String[]{"mobile", "leanback"}) {
            String source = read("src/" + flavor + "/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java");
            assertTrue(flavor + " ad-block statistics dialog must size from the bar-safe area",
                    source.contains("DialogSafeArea.width(activity, window)")
                            && source.contains("DialogSafeArea.height(activity, window)"));
            assertFalse(flavor + " ad-block statistics dialog must not derive its height from the raw display",
                    source.contains("ResUtil.getScreenHeight(") || source.contains("heightPixels"));
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
