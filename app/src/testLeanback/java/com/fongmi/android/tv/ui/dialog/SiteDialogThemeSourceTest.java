package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteDialogThemeSourceTest {

    private static Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve(".git"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root not found");
        return path;
    }

    private static String read(String relative) throws Exception {
        return Files.readString(root().resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    public void bothTvSiteDialogPathsBindTheIndependentWindow() throws Exception {
        String dialog = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java");
        assertTrue(dialog.contains("ThemeController.bindDialog(dialog)"));
        assertTrue(dialog.contains("ThemeController.bindDialog(directDialog)"));
        assertTrue(dialog.contains("SiteDialogTheme.applyShell"));
        assertTrue(dialog.contains("SiteDialogTheme.applyGroup(button)"));
    }

    @Test
    public void siteCardsUseRuntimeSemanticColorsInsteadOfFixedPurple() throws Exception {
        String adapter = read("app/src/leanback/java/com/fongmi/android/tv/ui/adapter/SiteAdapter.java");
        String helper = read("app/src/leanback/java/com/fongmi/android/tv/ui/helper/SiteDialogTheme.java");
        assertTrue(adapter.contains("SiteDialogTheme.applySiteItem"));
        assertTrue(helper.contains("tokens.colorPrimary()"));
        assertTrue(helper.contains("tokens.colorPrimaryContainer()"));
        assertTrue(helper.contains("tokens.colorOnPrimary()"));
        assertFalse(helper.contains("#381E72"));
        assertFalse(helper.contains("#6750A4"));
    }
}
