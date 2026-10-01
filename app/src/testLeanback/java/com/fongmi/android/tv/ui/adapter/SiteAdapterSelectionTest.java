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

        assertFalse("focused site card must not be filled with theme colorPrimary, which is white on TV",
                focused.contains("<solid android:color=\"?attr/colorPrimary\" />"));
        assertTrue("focused site card must use an explicit dark surface fill for white text",
                focused.contains("<solid android:color=\"#381E72\" />"));
        assertTrue("selected site card must remain visually distinct but readable",
                selected.contains("<solid android:color=\"#381E72\" />"));
    }
}
