package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The version cards in the “版本更新” dialog must be readable on every shipped palette
 * and under a user theme profile.
 *
 * <p>Regression under test (user screenshot, 1080x2340, dark wallpaper, “主题色彩 =
 * 壁纸取色”): the beta card rendered as a solid {@code #0F1D26} block carrying
 * {@code #49454F} text - <b>1.5:1</b> - while the stable card beside it stayed legible.
 * The two cards took their fill from the static
 * {@code selector_update_version_item} drawable and their text from three separate
 * {@code ColorStateList}s. {@link ThemeBinder} rewrites those text lists but leaves the
 * card's {@code StateListDrawable} untouched, so on a derived palette the text and the
 * fill drift apart. Most users never see it because the default profile resolves
 * {@code ThemeSeed.NONE} and {@code ThemeBinder} is then a no-op.
 *
 * <p>These assertions are pure arithmetic on the shipped token tables plus source
 * contracts, so they fail on the old wiring and pass on the fix without a device.
 */
public class UpdateVersionRowThemeTest {

    @Test
    public void everyCardTextClearsWcagAaOnBothCardFillsForEveryShippedPalette() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.dark(), ThemeTokens.light()}) {
            for (boolean expanded : new boolean[]{false, true}) {
                int fill = UpdateVersionRowTheme.fill(tokens, expanded);
                String where = (tokens == ThemeTokens.dark() ? "dark" : "light") + (expanded ? "/expanded" : "/resting");
                assertTrue(where + " name", ThemeContrast.ratio(UpdateVersionRowTheme.nameColor(tokens, expanded), fill) >= 4.5);
                assertTrue(where + " version", ThemeContrast.ratio(UpdateVersionRowTheme.versionColor(tokens, expanded), fill) >= 4.5);
                assertTrue(where + " description", ThemeContrast.ratio(UpdateVersionRowTheme.descColor(tokens, expanded), fill) >= 4.5);
                assertTrue(where + " expand affordance", ThemeContrast.ratio(UpdateVersionRowTheme.expandColor(tokens, expanded), fill) >= 4.5);
            }
        }
    }

    /**
     * The card keeps its text colours while the user toggles it open, so a colour that
     * only clears AA on one of the two fills would go unreadable on the other. Pin that
     * directly instead of trusting the two per-state assertions above.
     */
    @Test
    public void cardTextSurvivesTheRestingToExpandedFillChange() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.dark(), ThemeTokens.light()}) {
            int resting = UpdateVersionRowTheme.fill(tokens, false);
            int expanded = UpdateVersionRowTheme.fill(tokens, true);
            assertNotEquals("the two states must be distinguishable", resting, expanded);
            for (int color : new int[]{
                    UpdateVersionRowTheme.nameColor(tokens, false),
                    UpdateVersionRowTheme.versionColor(tokens, false),
                    UpdateVersionRowTheme.descColor(tokens, false),
                    UpdateVersionRowTheme.expandColor(tokens, false)}) {
                assertTrue(ThemeContrast.ratio(color, resting) >= 4.5);
                assertTrue(ThemeContrast.ratio(color, expanded) >= 4.5);
            }
        }
    }

    @Test
    public void thePillAndItsLabelStayReadable() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.dark(), ThemeTokens.light()}) {
            int fill = UpdateVersionRowTheme.pillFill(tokens);
            assertTrue(ThemeContrast.ratio(UpdateVersionRowTheme.pillTextColor(tokens), fill) >= 4.5);
            // The pill has to be visible on both card fills, otherwise it reads as part of
            // the card instead of as a badge.
            assertTrue(ThemeContrast.ratio(fill, UpdateVersionRowTheme.fill(tokens, false)) >= 1.2);
        }
    }

    @Test
    public void selectedAndFocusedRingsStayVisibleAgainstTheirOwnFill() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.dark(), ThemeTokens.light()}) {
            assertTrue("resting ring", UpdateVersionRowTheme.ringIsVisible(tokens, false));
            assertTrue("expanded ring", UpdateVersionRowTheme.ringIsVisible(tokens, true));
        }
    }

    /**
     * The static selector separated "focused" from "selected" by swapping the fill and ring
     * roles ({@code colorPrimary} vs {@code colorPrimaryContainer}). The runtime renderer has
     * to keep that: the TV dialog always opens with {@code stableItem.requestFocus()}, so a
     * focus fill of {@code colorPrimaryContainer} would make the focused card look like the
     * expanded one and collapse the two clues into one.
     */
    @Test
    public void theFocusedCardStaysDistinguishableFromTheSelectedCard() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.dark(), ThemeTokens.light()}) {
            String where = tokens == ThemeTokens.dark() ? "dark" : "light";
            int focused = UpdateVersionRowTheme.focusFill(tokens);
            int selected = UpdateVersionRowTheme.fill(tokens, true);
            int resting = UpdateVersionRowTheme.fill(tokens, false);
            assertNotEquals(where + ": focus must not paint the selected fill", focused, selected);
            assertTrue(where + ": the focus fill has to clear the non-text floor against the selected fill",
                    ThemeContrast.ratio(focused, selected) >= 3.0);
            assertTrue(where + ": the focus fill has to differ from the resting fill", ThemeContrast.ratio(focused, resting) >= 3.0);
            assertTrue(where + ": focus ink on the focus fill",
                    ThemeContrast.ratio(UpdateVersionRowTheme.focusInk(tokens), focused) >= 4.5);
            // The selected card keeps the resting ink while it is not focused, so the
            // selected fill still has to carry it.
            assertTrue(where + ": resting ink on the selected fill",
                    ThemeContrast.ratio(UpdateVersionRowTheme.nameColor(tokens, true), selected) >= 4.5);
        }
    }

    /**
     * The contract test in the leanback source set reads
     * {@code selector_update_version_item.xml}, which this dialog no longer renders: the
     * helper overrides {@code row.setBackground(...)}. Pin the runtime side here so focus and
     * selected cannot silently collapse again while that test stays green.
     */
    @Test
    public void theCardFaceKeepsItsStatesAndTheSharedRingWidth() throws Exception {
        String source = codeOnly(read("src/main/java/com/fongmi/android/tv/theme/UpdateVersionRowTheme.java"));
        for (String state : new String[]{"android.R.attr.state_focused", "android.R.attr.state_pressed", "android.R.attr.state_selected"}) {
            assertTrue("the card face must declare " + state, source.contains(state));
        }
        assertTrue("the focused face must paint the primary fill", source.contains("faced(context, safe.colorPrimary(),"));
        assertTrue("the selected face must paint the primary container fill", source.contains("faced(context, safe.colorPrimaryContainer(),"));
        assertTrue("the ring width must come from the shared dimens token", source.contains("R.dimen.webhtv_focus_ring_width"));
        assertTrue("the card text must flip on focus", source.contains("setTextColor(textColors("));
        assertTrue("focused and pressed must be matched before the default entry",
                source.contains("new int[][]{new int[]{android.R.attr.state_focused}, new int[]{android.R.attr.state_pressed}, new int[]{}}"));
        // The state list only reaches the text because the card nodes propagate the row
        // state; without these declarations the focused branch would never be selected.
        for (String flavour : new String[]{"mobile", "leanback"}) {
            String layout = read("src/" + flavour + "/res/layout/dialog_update.xml");
            int declarations = layout.split("duplicateParentState", -1).length - 1;
            assertTrue(flavour + ": every card text node must propagate the row focus state", declarations >= 8);
        }
        assertFalse("the ring width must not be a per-widget literal", source.contains(", 2, CORNER_DP)"));
    }

    /**
     * Pins the reported defect numerically. The user screenshot measured a
     * {@code #0F1D26} card carrying {@code #49454F} text; a fix that still allowed that
     * pair would pass every other assertion here.
     */
    @Test
    public void theReportedScreenshotPairIsExactlyTheUnreadableOne() {
        int reportedFill = 0xFF0F1D26;
        int reportedText = 0xFF49454F;
        assertTrue("the reported pair must be the unreadable one",
                ThemeContrast.ratio(reportedText, reportedFill) < 2.0);
        assertTrue("the fix must not reproduce it",
                ThemeContrast.ratio(UpdateVersionRowTheme.versionColor(ThemeTokens.dark(), false),
                        UpdateVersionRowTheme.fill(ThemeTokens.dark(), false)) >= 4.5);
    }

    /**
     * An arbitrary user palette can make {@code onSurfaceVariant} collide with the card
     * fill - {@link ThemeContrast#require(ThemeTokens)} never checks that pair. The card
     * has to fall back to a colour that does clear AA instead of painting an unreadable
     * one.
     */
    @Test
    public void aCollidingProfileFallsBackToAReadableForeground() {
        int fill = 0xFF0F1D26;
        int colliding = 0xFF11202A;
        assertEquals("white is the only fallback that clears AA on a dark fill", 0xFFFFFFFF, UpdateVersionRowTheme.readableOn(colliding, fill));
        assertTrue(ThemeContrast.ratio(UpdateVersionRowTheme.readableOn(colliding, fill), fill) >= 4.5);
        int paleFill = 0xFFE1E2E9;
        assertEquals("black is the only fallback that clears AA on a pale fill", 0xFF000000, UpdateVersionRowTheme.readableOn(0xFFDDDEE6, paleFill));
        // A colour that already clears AA on every fill must be kept, not replaced.
        assertEquals(0xFF000000, UpdateVersionRowTheme.readableOn(0xFF000000, paleFill));
        assertEquals(0xFFFFFFFF, UpdateVersionRowTheme.readableOn(0xFFFFFFFF, fill));
    }

    @Test
    public void bothFlavoursRouteTheCardsThroughTheSharedPalette() throws Exception {
        for (String flavour : new String[]{"mobile", "leanback"}) {
            String dialog = codeOnly(read("src/" + flavour + "/java/com/fongmi/android/tv/ui/dialog/UpdateDialog.java"));
            assertTrue(flavour + " must paint the card from the active palette",
                    dialog.contains("UpdateVersionRowTheme.apply(binding.stableItem, binding.stableName, binding.stableVersion, binding.stableStatus, binding.stableExpand, binding.stableDesc, expanded, tokens);"));
            assertTrue(flavour + " must paint the beta card too",
                    dialog.contains("UpdateVersionRowTheme.apply(binding.betaItem, binding.betaName, binding.betaVersion, binding.betaStatus, binding.betaExpand, binding.betaDesc, expanded, tokens);"));
            assertTrue(flavour + " must read the active palette", dialog.contains("ThemeTokens tokens = ThemeController.current();"));
            assertTrue(flavour + " must apply the theme while rendering", dialog.contains("applyCardTheme(channel, expanded);"));
            // The old wiring let the card fill and the card text come from different graphs.
            assertFalse(flavour + " must not colour card text inline from a fixed resource",
                    dialog.contains("setTextColor(R.color.update_version_item"));
        }
    }

    /**
     * The helper must own the whole card, including the {@code webhtv:ignore} tag: the
     * dialog still runs {@link ThemeController#bindDialog}, and without the tag the binder
     * would re-colour the text from a different role mapping and undo the guarantee above.
     */
    @Test
    public void theHelperOwnsTheCardSubtreeAndUsesOnlySemanticRoles() throws Exception {
        String source = codeOnly(read("src/main/java/com/fongmi/android/tv/theme/UpdateVersionRowTheme.java"));
        assertTrue(source.contains("row.setTag(IGNORE_TAG)"));
        assertTrue(source.contains("safe.colorSurfaceContainerHighest()"));
        assertTrue(source.contains("safe.colorPrimaryContainer()"));
        assertTrue(source.contains("safe.colorOnSurface()"));
        assertTrue(source.contains("safe.colorOnSurfaceVariant()"));
        assertTrue(source.contains("safe.colorPrimary()"));
        assertTrue(source.contains("safe.colorOutlineVariant()"));
        assertTrue(source.contains("colorSurface()"));
        assertFalse("the card renderer must not hard-code a fill", source.contains("#0F1D26"));
        assertFalse(source.contains("#E1E2E9"));
        assertFalse(source.contains("#34393E"));
    }

    /** Strips comments so a source contract matches code, not the prose naming the defect. */
    private static String codeOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (current == '/' && next == '/') {
                while (index < source.length() && source.charAt(index) != '\n') index++;
            } else if (current == '/' && next == '*') {
                index += 2;
                while (index + 1 < source.length() && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) index++;
                index = Math.min(source.length(), index + 2);
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
