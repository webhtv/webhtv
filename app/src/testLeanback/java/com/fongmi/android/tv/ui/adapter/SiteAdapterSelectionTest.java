package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteAdapterSelectionTest {

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve(".git"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root not found");
        return path;
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void focusedSiteCardUsesReadableFillInsteadOfThemePrimaryWhite() throws Exception {
        Path root = repoRoot();
        String focused = read(root.resolve("app/src/leanback/res/drawable/shape_site_item_focused.xml"));
        String selected = read(root.resolve("app/src/leanback/res/drawable/shape_site_item_selected.xml"));

        assertTrue("focused site card must use the active primary role",
                focused.contains("<solid android:color=\"?attr/colorPrimary\" />"));
        assertTrue("focused site card must pair its border with onPrimary",
                focused.contains("?attr/colorOnPrimary"));
        assertTrue("selected site card must use the active primary container role",
                selected.contains("<solid android:color=\"?attr/colorPrimaryContainer\" />"));
        assertTrue("selected site card must use the active primary outline",
                selected.contains("?attr/colorPrimary"));
        assertFalse("fixed purple must not survive in the TV site card",
                focused.contains("#381E72") || selected.contains("#381E72"));
    }
}
