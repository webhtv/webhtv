package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteDialogLayoutTest {

    @Test
    public void siteDialogUsesCenteredPanelInsteadOfFullScreen() throws Exception {
        String source = readLeanbackJava("SiteDialog.java");

        assertTrue("Site dialog window must be a centered panel, not a full-screen sheet",
                source.contains("params.gravity = Gravity.CENTER"));
        assertTrue("Site dialog window height must wrap its content",
                source.contains("params.height = WindowManager.LayoutParams.WRAP_CONTENT"));
        assertTrue("Site dialog window size must be applied through setLayout on TV devices",
                source.contains("window.setLayout(params.width, params.height);"));
        assertFalse("Site dialog must not fill the screen width",
                source.contains("params.width = WindowManager.LayoutParams.MATCH_PARENT"));
        assertFalse("Site dialog must not fill the screen height",
                source.contains("params.height = WindowManager.LayoutParams.MATCH_PARENT"));
    }

    @Test
    public void siteDialogLeavesSafeMarginsAndCapsItsWidth() throws Exception {
        String source = readLeanbackJava("SiteDialog.java");

        assertTrue("panel width must be a fraction of the screen so it never spans the full width",
                source.contains("screen * PANEL_WIDTH_RATIO"));
        assertTrue("panel must follow the repo's large-dialog 90% convention",
                source.contains("PANEL_WIDTH_RATIO = 0.9f"));
        assertFalse("a dp width cap would shrink the panel on density-1.0 TVs and clip the group row",
                source.contains("PANEL_MAX_WIDTH_DP"));
        assertTrue("panel height must be capped as a fraction of the available height",
                source.contains("PANEL_MAX_HEIGHT_RATIO"));
    }

    @Test
    public void siteGridHeightSnapsToWholeRowsSoTheLastRowIsNotClipped() throws Exception {
        String source = readLeanbackJava("SiteDialog.java");

        assertTrue("grid height must be derived from whole rows",
                source.contains("private int gridHeight(int count)"));
        assertTrue("grid height must be clamped to the panel's height budget",
                source.contains("gridBudgetHeight()"));
        assertFalse("the grid must no longer be stretched to fill the panel",
                source.contains("params.height = ViewGroup.LayoutParams.MATCH_PARENT;\n        binding.recycler.setLayoutParams(params);"));
    }

    @Test
    public void siteCardsDeclareHorizontalPaddingOnTheViewNotOnlyOnTheBackground() throws Exception {
        // SiteDialogTheme.applySiteItem() 用程序化 GradientDrawable 整体替换 background，
        // 那个 drawable 没有 padding，selector_site_item 里 shape 声明的内边距会一起丢失。
        // 内边距必须显式写在 View 上，否则健康点会直接压在卡片边框上。
        for (String layout : new String[]{"adapter_site.xml", "adapter_site_switch.xml"}) {
            String source = read(findLeanbackLayoutPath().resolve(layout));
            assertTrue(layout + " must declare start padding on the card view itself",
                    source.contains("android:paddingStart=\"14dp\""));
            assertTrue(layout + " must declare end padding on the card view itself",
                    source.contains("android:paddingEnd=\"14dp\""));
        }
    }

    private static String readLeanbackJava(String fileName) throws Exception {
        return read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", fileName)));
    }

    /**
     * 读取源文件并把行尾统一成 LF。
     *
     * <p>仓库开了 core.autocrlf，Windows 工作区里这些文件是 CRLF，而 Java 字符串字面量里的
     * {@code "\n"} 是单个 LF。不归一化的话，任何跨行的 {@code contains()} 断言都永远为假——
     * 断言会变成恒真，锁不住它本该锁的回归。同包的
     * {@code SiteDialogSelectionScrollSourceTest} 用的是同一套写法。
     */
    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackLayoutPath() {
        Path moduleRelative = Path.of("src", "leanback", "res", "layout");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res", "layout");
    }
}
