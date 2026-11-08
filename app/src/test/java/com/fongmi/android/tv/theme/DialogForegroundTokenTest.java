package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java-built dialogs must not pin their foreground to a fixed colour.
 *
 * <p>The dialogs below draw onto {@code shape_shell_proxy_dialog}, whose fill is
 * {@code ?attr/colorSurfaceContainerHigh} - light in the day table
 * ({@code #E7E8EF}) and dark in the night table ({@code #2A2F34}). A hard-coded
 * foreground can therefore only be right in one of the two modes.
 *
 * <p>This is the Java-side twin of the layout guard in
 * {@link ThemeBaseWiringTest#darkGlassSheetsUseAConstantLightForeground}. Layouts pin
 * their colour in XML and can be checked by reading the file; these dialogs build their
 * views in code and assign the colour at runtime, so the check is on the assignment
 * source instead. The two guards cover the two ways a fixed colour gets in.
 *
 * <p>Measured on the day/night panel pair, the pinned literals these classes used were:
 * {@code #5F6368} 4.95:1 day but 2.23:1 night, {@code #202124} 13.18:1 day but 1.19:1
 * night. The semantic replacements resolve to 5.23:1 or better in <em>both</em> tables.
 */
public class DialogForegroundTokenTest {

    /** The dialogs this guard was originally opened for. */
    private static final String[] BASE_DIALOG_SOURCES = {
            "DebugLogDialog.java",
            "MpvConfigDialog.java",
            "PlaybackPerformanceDialog.java",
    };

    /**
     * The viewing-record sync family: the top-level dialog and its two second-level managers.
     *
     * <p>They build their rows in code, so they are covered by the same foreground contract as
     * the dialogs above, plus the per-method row/badge surface contract below.
     */
    private static final String[] VIEWING_RECORD_SYNC_SOURCES = {
            "ViewingRecordSyncDialog.java",
            "PlaybackRemoteSyncDialog.java",
            "PlaybackWebhookDialog.java",
    };

    /** Java-built dialogs that paint onto the palette-following dialog panel. */
    private static final String[] DIALOG_SOURCES = concat(BASE_DIALOG_SOURCES, VIEWING_RECORD_SYNC_SOURCES);

    /**
     * Fixed foregrounds that must not appear: the legacy Material greys and blues these
     * dialogs used to pin, plus the plain white/black literals that would equally break
     * the other mode.
     */
    private static final String[] FORBIDDEN_LITERALS = {
            "#5F6368", "#202124", "#3C4043", "#174EA6", "#1A73E8",
            "#8AB4F8", "#E8F0FE", "#C4C7C5", "#C5221F",
    };

    /**
     * The only colour resources a guarded dialog may name directly.
     *
     * <p>All five are selector resources under {@code res/color/} that resolve to palette roles -
     * four items per selector, naming {@code webhtv_color_*} roles plus a final mode-independent
     * {@code @color/transparent} ({@code #00000000}) where the shape supplies the fill - so they
     * already follow the palette and naming them is not a fixed colour. Anything else, such as a
     * {@code res/values} literal like the legacy greys, would pin one mode and is rejected.
     */
    private static final String[] ALLOWED_COLOR_RESOURCES = {
            "dialog_tonal_button_bg",
            "dialog_tonal_button_text",
            "dialog_outlined_button_bg",
            "dialog_outlined_button_text",
            "dialog_outlined_button_stroke",
    };

    /**
     * Every row/text surface the viewing-record sync family builds must take its colour from the
     * token that belongs to that specific call site.
     *
     * <p>These are per-method contracts, not whole-file presence checks, because the defect this
     * guard exists for is a single wrong assignment: {file, declaration, then one or more
     * {@code ThemeController.current()} token suffixes the body must contain}. A whole-file check
     * would stay green when one of several call sites in the same file silently went back to a
     * literal, which is exactly how the original bug shipped.
     */
    private static final String[][] REQUIRED_SITES = {
            // ViewingRecordSyncDialog: the top-level list.
            {"ViewingRecordSyncDialog.java", "private View sectionTitle(int text) {",
                    "colorOnSurfaceVariant()"},
            {"ViewingRecordSyncDialog.java",
                    "private View switchRow(String title, String detail, boolean enabled",
                    "colorOnSurface()"},
            {"ViewingRecordSyncDialog.java",
                    "private View entryRow(String title, String detail, int actionText",
                    "colorOnSurface()"},
            {"ViewingRecordSyncDialog.java", "private void addDetail(LinearLayoutCompat root, String value) {",
                    "colorOnSurfaceVariant()"},
            {"ViewingRecordSyncDialog.java", "private GradientDrawable rowBackground() {",
                    "colorSurfaceContainerLow()", "colorOutlineVariant()"},
            {"ViewingRecordSyncDialog.java",
                    "private MaterialButton actionButton(int text, boolean tonal, boolean danger) {",
                    "colorError()"},

            // PlaybackRemoteSyncDialog: the remote-sync managers, each rendered as a card row.
            {"PlaybackRemoteSyncDialog.java", "private View row(RemoteSyncConfig config) {",
                    "colorOnSurface()", "colorError()"},
            {"PlaybackRemoteSyncDialog.java", "private void addDetail(LinearLayoutCompat root, String value) {",
                    "colorOnSurfaceVariant()"},
            {"PlaybackRemoteSyncDialog.java", "private int statusColor(RemoteSyncConfig config) {",
                    "colorSuccess()", "colorOnSurfaceVariant()"},
            {"PlaybackRemoteSyncDialog.java",
                    "private MaterialButton actionButton(int text, boolean tonal, boolean danger) {",
                    "colorError()"},
            {"PlaybackRemoteSyncDialog.java", "private MaterialTextView badge(String value, int color) {",
                    "colorSurfaceContainerLowest()"},
            {"PlaybackRemoteSyncDialog.java", "private GradientDrawable rowBackground() {",
                    "colorSurfaceContainerLow()", "colorOutlineVariant()"},

            // PlaybackWebhookDialog: adds the suspended row, which keeps an error fill and stroke.
            {"PlaybackWebhookDialog.java", "private View row(WebhookConfig config) {",
                    "colorOnSurface()", "colorError()"},
            {"PlaybackWebhookDialog.java", "private void addDetail(LinearLayoutCompat root, String value) {",
                    "colorOnSurfaceVariant()"},
            {"PlaybackWebhookDialog.java", "private int statusColor(WebhookConfig config) {",
                    "colorError()", "colorSuccess()", "colorOnSurfaceVariant()"},
            {"PlaybackWebhookDialog.java",
                    "private MaterialButton actionButton(int text, boolean tonal, boolean danger) {",
                    "colorError()"},
            {"PlaybackWebhookDialog.java", "private MaterialTextView badge(String value, int color) {",
                    "colorSurfaceContainerLowest()"},
            {"PlaybackWebhookDialog.java", "private GradientDrawable rowBackground(WebhookConfig config) {",
                    "colorErrorContainer()", "colorError()",
                    "colorSurfaceContainerLow()", "colorOutlineVariant()"},
    };

    @Test
    public void javaBuiltDialogsDoNotPinAFixedForeground() throws Exception {
        for (String name : DIALOG_SOURCES) {
            String source = stripComments(read(dialogPath(name)));
            for (String literal : FORBIDDEN_LITERALS) {
                assertFalse(name + " pins the fixed foreground " + literal
                                + ", which cannot be legible in both the day and night palettes",
                        source.contains("\"" + literal + "\""));
            }
            assertFalse(name + " must not pin a foreground through Color.parseColor",
                    source.contains("Color.parseColor(\"#"));
            assertNoBareColorConstant(name, source);
            assertNoColorConstructor(name, source);
            assertNoIntegerHexLiteral(name, source);
            assertNoFixedColorResource(name, source);
        }
    }

    /**
     * The dialogs must take their foreground from the active semantic snapshot.
     *
     * <p>{@code Color.TRANSPARENT} is deliberately still allowed: it is not a foreground
     * and carries no palette assumption (window backgrounds, tab ripples).
     */
    @Test
    public void javaBuiltDialogsReadTheActiveTokens() throws Exception {
        for (String name : DIALOG_SOURCES) {
            String source = stripComments(read(dialogPath(name)));
            assertTrue(name + " must resolve its foreground from ThemeController.current()",
                    source.contains("ThemeController.current()"));
            assertTrue(name + " must import ThemeController",
                    source.contains("import com.fongmi.android.tv.theme.ThemeController;"));
        }
    }

    /**
     * The MPV action popup must keep its fill and its foreground in the same palette.
     *
     * <p>{@code MpvConfigDialog.actionItem} now paints its label with
     * {@code colorOnSurface} / {@code colorError}. The popup background it sits on was a
     * fixed white shape, so it had to migrate in the same change - otherwise night mode
     * would render a near-black label on a still-white popup. Both halves are pinned so a
     * later edit cannot move one without the other.
     */
    @Test
    public void mpvActionPopupFollowsTheSamePaletteAsItsForeground() throws Exception {
        String popup = stripComments(read("src/main/res/drawable/shape_mpv_action_menu.xml"));
        assertTrue("the popup fill must follow the palette",
                popup.contains("?attr/colorSurfaceContainerLowest"));
        assertTrue("the popup stroke must follow the palette",
                popup.contains("?attr/colorOutlineVariant"));
        assertFalse("the popup must not keep a fixed fill", popup.contains("#FFFFFF"));
        assertFalse("the popup must not keep a fixed stroke", popup.contains("#E1E5EA"));
    }

    /**
     * Every row, badge and status surface of the viewing-record sync family follows the palette.
     *
     * <p>These rows are built in code and were filled with a fixed light card
     * ({@code #F5F6F7}) on a fixed light hairline ({@code #DADCE0} / {@code #E2E5E8}), with the
     * row title pinned to {@code Color.BLACK} and the badge to {@code #FFFFFF}. None of those
     * values equals a {@code webhtv_color_*} role, so {@code ThemeBinder}'s exact-match rewrite
     * skipped them by design: under the night table the dialog panel went dark while every row
     * stayed a near-white card carrying black text, and the section labels/details stayed the
     * fixed {@code #5F6368} grey that measures 2.23:1 on that panel.
     *
     * <p>Measured after the change against the real token values, every pair clears WCAG AA in
     * <em>both</em> tables: row title 15.43:1 day / 13.29:1 night, detail text 8.39:1 / 10.06:1,
     * badge text on its chip 6.53:1 / 11.78:1, and the suspended row's error text on its error
     * container 5.14:1 / 5.51:1.
     */
    @Test
    public void viewingRecordSyncRowsUseTheActivePalette() throws Exception {
        for (String[] site : REQUIRED_SITES) {
            String name = site[0];
            String method = site[1];
            String body = methodBody(name, stripComments(read(dialogPath(name))), method);
            for (int i = 2; i < site.length; i++) {
                assertTrue(name + " " + method + " must take its colour from "
                                + "ThemeController.current()." + site[i]
                                + ", not a literal that can only be right in one palette",
                        body.contains("ThemeController.current()." + site[i]));
            }
        }
    }

    /**
     * The badge chip and the suspended row keep their own semantic surfaces.
     *
     * <p>The badge was a fixed {@code #FFFFFF} fill, which under the night table drew a white
     * chip carrying the night success/error text. The suspended row was a fixed {@code #FFF7F7}
     * pink that only read as "attention" in the day table, and its fill alone is a hue cue - the
     * day/night error container differs from the normal card by only 1.15:1 / 1.83:1 - so it also
     * keeps the paired {@code colorError} stroke. Both halves are pinned so a later edit cannot
     * move one without the other.
     */
    @Test
    public void viewingRecordSyncBadgeAndSuspendedRowFollowThePalette() throws Exception {
        String remote = stripComments(
                read(dialogPath("PlaybackRemoteSyncDialog.java")));
        String remoteBadge = methodBody("PlaybackRemoteSyncDialog.java", remote,
                "private MaterialTextView badge(String value, int color) {");
        assertTrue("the remote-sync badge fill must follow the palette",
                remoteBadge.contains("ThemeController.current().colorSurfaceContainerLowest()"));

        String webhook = stripComments(
                read(dialogPath("PlaybackWebhookDialog.java")));
        String webhookBadge = methodBody("PlaybackWebhookDialog.java", webhook,
                "private MaterialTextView badge(String value, int color) {");
        assertTrue("the webhook badge fill must follow the palette",
                webhookBadge.contains("ThemeController.current().colorSurfaceContainerLowest()"));

        String suspendedRow = methodBody("PlaybackWebhookDialog.java", webhook,
                "private GradientDrawable rowBackground(WebhookConfig config) {");
        assertTrue("the suspended webhook row must use the semantic error container",
                suspendedRow.contains("ThemeController.current().colorErrorContainer()"));
        assertTrue("the suspended webhook row must keep an error stroke, so the cue is not hue-only",
                suspendedRow.contains("ThemeController.current().colorError()"));
    }

    /**
     * Rejects {@code Color.BLACK}, {@code Color.WHITE}, {@code Color.GRAY} and friends.
     *
     * <p>The literal scan above only catches {@code "#RRGGBB"} strings, so a bare
     * {@link android.graphics.Color} constant was a second way back to a pinned foreground.
     * {@code Color.TRANSPARENT} is exempt: it is not a foreground.
     */
    private static void assertNoBareColorConstant(String name, String source) {
        Matcher matcher = Pattern.compile("Color\\.([A-Z][A-Z0-9_]*)").matcher(source);
        while (matcher.find()) {
            String constant = matcher.group(1);
            if (!"TRANSPARENT".equals(constant)) {
                fail(name + " must not pin a foreground through Color." + constant
                        + "; use ThemeController.current(), or Color.TRANSPARENT if it is not a colour");
            }
        }
    }

    /**
     * Rejects {@code Color.rgb(32, 33, 36)} and friends - the fourth way to pin a literal.
     *
     * <p>This is not hypothetical: {@code MpvCustomButtonDialog} and
     * {@code PlaybackPerformanceModal} pin exactly this way, and {@code Color.rgb(32, 33, 36)}
     * is literally the forbidden {@code #202124}. Only the constructors that take numeric
     * channel literals are rejected; the channel readers and {@code Color.valueOf} are
     * conversions of an existing colour, not a way to introduce one.
     */
    private static void assertNoColorConstructor(String name, String source) {
        Matcher matcher = Pattern.compile("Color\\.(rgb|argb|HSVToColor)\\s*\\(").matcher(source);
        if (matcher.find()) {
            fail(name + " must not build a colour through Color." + matcher.group(1)
                    + "(...); use ThemeController.current() so both palettes stay legible");
        }
    }

    /**
     * Rejects {@code 0xFF5F6368}-style integer colours, a third way to pin a literal.
     *
     * <p>An 8-digit {@code 0x} literal is either an {@code AARRGGBB} colour or an Android
     * resource id ({@code 0x7f......}); only the former is a pinned colour, so the package-id
     * prefix is excluded to keep the rule from rejecting an unrelated packed constant.
     */
    private static void assertNoIntegerHexLiteral(String name, String source) {
        Matcher matcher = Pattern.compile("0x(?!7[fF])[0-9A-Fa-f]{8}").matcher(source);
        if (matcher.find()) {
            fail(name + " must not pin a foreground through the integer colour " + matcher.group()
                    + "; use ThemeController.current()");
        }
    }

    /** Rejects {@code R.color.<literal>} except the palette-following selector resources. */
    private static void assertNoFixedColorResource(String name, String source) {
        Matcher matcher = Pattern.compile("R\\.color\\.([A-Za-z0-9_]+)").matcher(source);
        while (matcher.find()) {
            String resource = matcher.group(1);
            if (!List.of(ALLOWED_COLOR_RESOURCES).contains(resource)) {
                fail(name + " must not name the colour resource R.color." + resource
                        + "; it is not one of the palette-following selectors "
                        + List.of(ALLOWED_COLOR_RESOURCES));
            }
        }
    }

    /**
     * The body of the single method whose declaration contains {@code signature}.
     *
     * <p>Braces are balanced from the declaration's opening brace, so the returned text is only
     * that method - which is what makes a per-call-site assertion possible. A missing or
     * duplicated declaration fails loudly instead of silently matching the wrong body.
     *
     * <p><b>Assumption:</b> this counts raw braces, so it is only correct while the guarded
     * dialogs contain no {@code '{'} / {@code '}'} inside a string or character literal. A
     * literal brace would mis-balance the scan and could silently return a truncated body,
     * weakening every contract in {@link #REQUIRED_SITES}. {@link #assertNoBraceInLiteral}
     * therefore rejects that shape outright rather than letting it corrupt the result.
     */
    private static String methodBody(String name, String source, String signature) {
        assertNoBraceInLiteral(name, source);
        int start = source.indexOf(signature);
        if (start < 0) {
            fail(name + " no longer declares \"" + signature
                    + "\"; update this contract if the method was renamed or removed");
        }
        if (source.indexOf(signature, start + 1) >= 0) {
            fail(name + " declares \"" + signature + "\" more than once; the contract needs a"
                    + " signature that identifies one method");
        }
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError(name + " declares \"" + signature + "\" without a balanced body");
    }

    /**
     * Rejects a brace inside a string or character literal, which {@link #methodBody} cannot
     * count correctly.
     *
     * <p>This turns a silent weakening into a loud failure: without it, adding
     * {@code text("{")} to a guarded dialog would mis-balance the scan, and the contracts below
     * would start asserting against a truncated body while still reporting green.
     */
    private static void assertNoBraceInLiteral(String name, String source) {
        Matcher matcher = Pattern.compile("\"[^\"\\n]*[{}][^\"\\n]*\"|'[{}]'").matcher(source);
        if (matcher.find()) {
            fail(name + " contains a brace inside a string or character literal ("
                    + matcher.group() + "), which methodBody cannot brace-balance; move the brace"
                    + " out of the literal or replace the scanner with a real parser");
        }
    }

    private static String[] concat(String[] first, String[] second) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(List.of(second));
        return all.toArray(new String[0]);
    }

    private static String dialogPath(String name) {
        return "src/main/java/com/fongmi/android/tv/ui/dialog/" + name;
    }

    private static String stripComments(String source) {
        String withoutXml = source.replaceAll("(?s)<!--.*?-->", "");
        return withoutXml
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
