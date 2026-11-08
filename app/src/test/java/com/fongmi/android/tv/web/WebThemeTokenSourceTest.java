package com.fongmi.android.tv.web;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebThemeTokenSourceTest {

    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{3}(?:[0-9A-Fa-f]{3})?\\b");

    @Test
    public void sharedStylesExposeCanonicalTokensAndLegacyAliases() throws Exception {
        String css = read("app/src/main/assets/css/ui.css");
        for (String token : new String[]{
                "--webhtv-primary", "--webhtv-on-primary", "--webhtv-surface",
                "--webhtv-surface-container", "--webhtv-surface-container-high",
                "--webhtv-on-surface", "--webhtv-on-surface-variant", "--webhtv-outline",
                "--webhtv-outline-variant", "--webhtv-error", "--webhtv-success",
                "--webhtv-warning", "--webhtv-focus", "--webhtv-shape-small",
                "--webhtv-space-1"
        }) {
            assertTrue(token + " must be defined", css.contains(token + ":"));
        }
        assertTrue("legacy --md-primary must remain an alias", css.contains("--md-primary: var(--webhtv-primary);"));
        assertTrue("explicit light mode hook missing", css.contains(":root[data-theme=\"light\"]"));
        assertTrue("explicit dark mode hook missing", css.contains(":root[data-theme=\"dark\"]"));
    }

    @Test
    public void assetStylesDoNotUseRawHexOutsideControlledDeclarations() throws Exception {
        for (String file : new String[]{
                "app/src/main/assets/css/ui.css", "app/src/main/assets/index.html",
                "app/src/main/assets/manage.html", "app/src/main/assets/parse.html",
                "app/src/main/assets/webhome/eclipse-detail.html"
        }) {
            for (String line : read(file).split("\\R")) {
                if (!HEX.matcher(line).find()) continue;
                String stripped = line.trim();
                assertTrue(file + " raw hex outside token declaration: " + stripped,
                        stripped.startsWith("--") || stripped.contains("<meta name=\"theme-color\""));
            }
        }
    }

    @Test
    public void webHomePagesConsumeNativeTokensAndReadOnlyThemeInfo() throws Exception {
        assertTrue(read("app/src/main/assets/webhome/eclipse.html").contains("applyNativeTokens(info || {})"));
        assertTrue(read("app/src/main/assets/webhome/eclipse-detail.html").contains("applyNativeTokens(info || {})"));
        String controller = read("app/src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        assertTrue(controller.contains("root.add(\"tokens\""));
        assertTrue(controller.contains("ThemeWebBridge.snapshotJson"));
        assertFalse(controller.contains("ThemeController.apply("));
    }

    @Test
    public void webHomeTokenDeclarationsStayInsideRootScope() throws Exception {
        String home = read("app/src/main/assets/webhome/eclipse.html");
        assertTrue(home.contains(":root {\n      --eclipse-color-1: #a8c7fa;"));
        assertTrue(home.contains("      --eclipse-color-56: #000;\n    }"));

        String detail = read("app/src/main/assets/webhome/eclipse-detail.html");
        assertTrue(detail.contains(":root {\n      --eclipse-detail-color-1: #a8c7fa;"));
        assertTrue(detail.contains("      --eclipse-detail-color-105: #705cff;\n    }"));
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        String relative = path.startsWith("app/") ? path.substring("app/".length()) : path;
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }
}
