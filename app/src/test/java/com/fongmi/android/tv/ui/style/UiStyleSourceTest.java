package com.fongmi.android.tv.ui.style;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UiStyleSourceTest {

    @Test
    public void sharedComponentStylesExistAndOnlyConsumeTokens() throws Exception {
        String styles = withoutComments(
                Files.readString(Path.of("src/main/res/values/webhtv_styles.xml"), StandardCharsets.UTF_8));
        String[] required = {
                "Theme.WebHTV", "Theme.WebHTV.Dialog", "ThemeOverlay.WebHTV.Dialog", "Widget.WebHTV.Button.Filled",
                "Widget.WebHTV.Button.Tonal", "Widget.WebHTV.Button.Outlined", "Widget.WebHTV.Button.Text",
                "Widget.WebHTV.Input", "Widget.WebHTV.ListItem", "Widget.WebHTV.Card",
                "Widget.WebHTV.Dialog", "Widget.WebHTV.BottomSheet", "Widget.WebHTV.PlayerControl"
        };
        for (String name : required) assertTrue(name, styles.contains("name=\"" + name + "\""));
        assertFalse(styles.matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
    }

    /**
     * The comment strip must stay load-bearing, in both directions.
     *
     * <p>A guard that reads raw text flags any table whose audit comment quotes a measured hex
     * (here the scrim colour it wired up, {@code #52000000}). Deleting that evidence would be the
     * wrong fix, so the strip is what keeps the check honest - and it is only honest while it also
     * keeps real declarations. Both directions are asserted on synthetic input, so the guard binds
     * the strip behaviour instead of pinning today's comment text; the real file is then checked
     * for the actual invariant (nothing renders a raw hex) without depending on its prose.
     */
    @Test
    public void commentStripIgnoresCommentHexAndKeepsRealOnes() throws Exception {
        assertFalse("注释里的 hex 必须被剔除（否则审查注释会被当成硬编码色）",
                withoutComments("<style><!-- 实测 #52000000 / #1A73E8 --></style>")
                        .matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
        assertTrue("真属性的 hex 必须保留（剔除不得过度）",
                withoutComments("<item name=\"a\">#FF123456</item>")
                        .matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
        assertTrue("剔除不得吃掉声明本身",
                withoutComments("<!--x--><style name=\"Theme.WebHTV\"/>").contains("Theme.WebHTV"));

        String styles = withoutComments(
                Files.readString(Path.of("src/main/res/values/webhtv_styles.xml"), StandardCharsets.UTF_8));
        assertFalse("这份表剔除注释后不得残留 hex", styles.matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
    }

    @Test
    public void selectorsCoverInteractionStates() throws Exception {
        Path directory = Path.of("src/main/res/color");
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(value -> value.getFileName().toString().startsWith("webhtv_selector_")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                assertTrue(path + " missing disabled", source.contains("state_enabled=\"false\""));
                assertTrue(path + " missing pressed", source.contains("state_pressed=\"true\""));
                assertTrue(path + " missing focused", source.contains("state_focused=\"true\""));
                assertTrue(path + " missing selected/activated", source.contains("state_selected=\"true\"") || source.contains("state_activated=\"true\""));
            }
        }
    }

    @Test
    public void legacyDialogNamesRemainAliasesOnly() throws Exception {
        String styles = Files.readString(Path.of("src/main/res/values/styles.xml"), StandardCharsets.UTF_8);
        assertTrue(styles.contains("<style name=\"Theme.WebHTV.LightDialog\" parent=\"Theme.WebHTV.Dialog\" />"));
        assertTrue(styles.contains("<style name=\"ThemeOverlay.WebHTV.LightDialog\" parent=\"ThemeOverlay.WebHTV.Dialog\" />"));
        for (Path path : javaSources()) {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertFalse(path + " still uses a legacy dialog theme", source.contains("R.style.Theme_WebHTV_LightDialog"));
            assertFalse(path + " still uses a legacy dialog overlay", source.contains("R.style.ThemeOverlay_WebHTV_LightDialog"));
        }
    }

    @Test
    public void dialogAndSettingLayoutsUseSemanticColors() throws Exception {
        for (Path path : dialogAndSettingLayouts()) {
            String source = withoutComments(Files.readString(path, StandardCharsets.UTF_8));
            assertFalse(path + " contains a raw color", source.matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
            assertFalse(path + " contains a fixed business color", source.matches("(?s).*@color/(white|black|white_[0-9]+|black_[0-9]+|grey_[0-9]+|text|yellow)\\b.*"));
            assertFalse(path + " still uses a legacy widget style", source.contains("@style/Widget.WebHTV.LightDialog"));
        }
    }

    /**
     * XML comments are documentation, not rendered values.
     *
     * <p>An audit comment that quotes a measured hex (the palette it replaced, the contrast it
     * scored) is not a hard-coded colour, so scanning the raw file text reports a false positive
     * for any file whose only hex sits in a comment - and the fix an author is pressured into is
     * deleting the evidence. Stripping comments first keeps the guard aimed at what actually
     * renders, and matches how the other source guards in this repository read their inputs
     * ({@code TvFocusScaleContractTest}, {@code LabSettingsDialogThemeTest}).
     */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
    }

    private static Path[] javaSources() throws Exception {
        try (Stream<Path> paths = Stream.of(Path.of("src/main/java"), Path.of("src/mobile/java"), Path.of("src/leanback/java"))) {
            return paths.flatMap(UiStyleSourceTest::walk)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toArray(Path[]::new);
        }
    }

    private static Path[] dialogAndSettingLayouts() throws Exception {
        return Stream.of(Path.of("src/main/res/layout"), Path.of("src/mobile/res/layout"), Path.of("src/leanback/res/layout"))
                .flatMap(UiStyleSourceTest::walk)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("dialog_") || name.startsWith("fragment_setting") || name.startsWith("activity_setting");
                })
                .toArray(Path[]::new);
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    public void playerControlsStayIsolatedFromGeneralFocusRoles() throws Exception {
        String light = Files.readString(Path.of("src/main/res/values/webhtv_tokens.xml"), StandardCharsets.UTF_8);
        String dark = Files.readString(Path.of("src/main/res/values-night/webhtv_tokens.xml"), StandardCharsets.UTF_8);
        for (String tokens : new String[]{light, dark}) {
            String focus = value(tokens, "webhtv_color_focus");
            String primary = value(tokens, "webhtv_color_primary");
            String active = value(tokens, "webhtv_color_player_control_active");
            assertFalse("player active must not reuse the general focus color", active.equals(focus));
            assertFalse("player active must not reuse the primary color", active.equals(primary));
        }

        String control = Files.readString(Path.of("src/main/res/color/player_control_text.xml"), StandardCharsets.UTF_8);
        assertTrue(control.contains("@color/webhtv_color_player_control_active"));
        assertTrue(control.contains("@color/webhtv_color_player_control"));
        assertFalse("player controls must not reintroduce raw yellow", control.contains("#FFD700"));
    }

    /**
     * Standalone inputs must draw their own box and must not inherit a container style.
     *
     * <p>Every caller applies {@code Widget.WebHTV.Input} directly to a bare
     * {@code TextInputEditText} - there is no {@code TextInputLayout} anywhere in those
     * trees. When the style inherited {@code Widget.Material3.TextInputLayout.OutlinedBox}
     * (a container style) the outline was never drawn, because the container owns it, and
     * the floating-label metrics squeezed the text inside the fixed 44dp heights. Reported
     * as "the input boxes are cut off and no longer look like inputs".
     */
    @Test
    public void standaloneInputStyleProvidesItsOwnBox() throws Exception {
        String styles = Files.readString(Path.of("src/main/res/values/webhtv_styles.xml"), StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<style name=\"Widget.WebHTV.Input\"([^>]*)>(.*?)</style>",
                        java.util.regex.Pattern.DOTALL)
                .matcher(styles);
        assertTrue("Widget.WebHTV.Input must exist", matcher.find());
        assertFalse("a TextInputLayout container style cannot be applied to a bare edit field",
                matcher.group(1).contains("TextInputLayout"));
        assertTrue("a standalone field must draw its own box",
                matcher.group(2).contains("@drawable/selector_dialog_input"));
        assertTrue("the standalone field must set its own text colours",
                matcher.group(2).contains("android:textColor"));
    }

    private static String value(String source, String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<color name=\\\"" + name + "\\\">(#[0-9A-Fa-f]{6,8})</color>")
                .matcher(source);
        assertTrue("missing token " + name, matcher.find());
        return matcher.group(1);
    }
}
