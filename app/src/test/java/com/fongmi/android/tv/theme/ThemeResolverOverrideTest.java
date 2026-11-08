package com.fongmi.android.tv.theme;

import org.junit.Test;

import com.google.android.material.color.utilities.Hct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ThemeResolverOverrideTest {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    public void absentOrEmptyProfileKeepsTheFrozenPalette() {
        assertEquals(ThemeTokens.light(), ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, null, null, false));
        assertEquals(ThemeTokens.dark(), ThemeResolver.resolve(
                ThemeMode.DARK, ThemeSeed.NONE, 0, 0, ThemeProfile.defaultProfile(), null, true));
        assertTrue(ThemeResolver.lastDiagnostic().startsWith("default"));
    }

    @Test
    public void eachColorSlotOverridesOnlyItsOwnToken() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        ThemeProfile.SlotSet slots = profile.light;
        slots.primary = "#000000";
        slots.primaryContainer = "#000000";
        slots.secondaryContainer = "#000000";
        slots.focus = "#000000";
        slots.surface = "#FFFFFF";
        slots.surfaceContainer = "#F7F7F7";
        slots.surfaceContainerHigh = "#EFEFEF";
        slots.onSurface = "#000000";
        slots.onSurfaceVariant = "#000000";
        slots.outline = "#000000";
        slots.error = "#000000";
        slots.success = "#000000";
        slots.warning = "#000000";

        ThemeTokens base = ThemeTokens.light();
        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        tokens.requireContrast();

        assertEquals(BLACK, tokens.colorPrimary());
        assertEquals(BLACK, tokens.colorPrimaryContainer());
        assertEquals(BLACK, tokens.colorSecondaryContainer());
        assertEquals(BLACK, tokens.colorFocus());
        assertEquals(WHITE, tokens.colorSurface());
        assertEquals(0xFFF7F7F7, tokens.colorSurfaceContainer());
        assertEquals(0xFFEFEFEF, tokens.colorSurfaceContainerHigh());
        assertEquals(BLACK, tokens.colorOnSurface());
        assertEquals(BLACK, tokens.colorOnSurfaceVariant());
        assertEquals(BLACK, tokens.colorOutline());
        assertEquals(BLACK, tokens.colorError());
        assertEquals(BLACK, tokens.colorSuccess());
        assertEquals(BLACK, tokens.colorWarning());

        // Slots the user did not touch keep the frozen palette.
        assertEquals(base.colorTertiary(), tokens.colorTertiary());
        assertEquals(base.colorSecondary(), tokens.colorSecondary());
        assertEquals(base.colorErrorContainer(), tokens.colorErrorContainer());
        assertEquals(base.colorOutlineVariant(), tokens.colorOutlineVariant());
        assertEquals(base.colorInverseSurface(), tokens.colorInverseSurface());
    }

    @Test
    public void playerAndHealthTokensAreNeverReachableFromAProfile() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        ThemeProfile.SlotSet slots = profile.light;
        slots.primary = "#FF00AA";
        slots.surface = "#FAFAFA";
        slots.error = "#AA0000";
        slots.dialogOpacity = 0.75f;
        slots.scrimOpacity = 0.80f;
        slots.overlayOpacity = 0.55f;

        ThemeTokens base = ThemeTokens.light();
        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        assertEquals(base.colorPlayerControl(), tokens.colorPlayerControl());
        assertEquals(base.colorPlayerControlMuted(), tokens.colorPlayerControlMuted());
        assertEquals(base.colorPlayerControlActive(), tokens.colorPlayerControlActive());
        assertEquals(base.colorPlayerScrim(), tokens.colorPlayerScrim());
        assertEquals(base.colorHealthGood(), tokens.colorHealthGood());
        assertEquals(base.colorHealthWarn(), tokens.colorHealthWarn());
        assertEquals(base.colorHealthBad(), tokens.colorHealthBad());
        assertEquals(base.colorOverlayDark(), tokens.colorOverlayDark());
        assertEquals(base.focusScale(), tokens.focusScale(), 0.0001f);
    }

    @Test
    public void derivedOnColorsFollowBlackOrWhiteReadability() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.primary = "#FFFFFF";
        ThemeTokens light = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        // Primary is also drawn as dialog action text, so a white primary on a near-white
        // surface is repaired first. The on* pair must then stay readable against whatever
        // primary the repair produced, and the accent contract must hold either way.
        assertNotEquals("white primary on a near-white surface must be repaired",
                0xFFFFFFFF, light.colorPrimary());
        assertTrue(ThemeContrast.ratio(light.colorOnPrimary(), light.colorPrimary()) >= 4.5);
        assertAccentLegible(light, "white primary request");

        ThemeProfile darkProfile = ThemeProfile.defaultProfile();
        darkProfile.dark.primary = "#000000";
        ThemeTokens dark = ThemeResolver.resolve(
                ThemeMode.DARK, ThemeSeed.NONE, 0, 0, darkProfile, null, true);
        assertNotEquals("black primary on a near-black surface must be repaired",
                0xFF000000, dark.colorPrimary());
        assertTrue(ThemeContrast.ratio(dark.colorOnPrimary(), dark.colorPrimary()) >= 4.5);
        assertAccentLegible(dark, "black primary request");
    }

    @Test
    public void unreadableUserTextIsCorrectedInsteadOfRejected() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.surface = "#FFFFFF";
        profile.light.onSurface = "#FEFEFE";
        profile.light.onSurfaceVariant = "#FDFDFD";
        profile.light.outline = "#FBFBFB";

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        tokens.requireContrast();
        assertTrue(ThemeContrast.ratio(tokens.colorOnSurface(), tokens.colorSurface()) >= 4.5);
        assertTrue(ThemeContrast.ratio(tokens.colorOnSurfaceVariant(), tokens.colorSurface()) >= 4.5);
        assertTrue(ThemeContrast.ratio(tokens.colorOutline(), tokens.colorSurface()) >= 3.0);
        assertTrue(ThemeResolver.lastDiagnostic().contains("profile"));
    }

    @Test
    public void identicalSurfaceLayersReceiveAMinimalDistinction() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.surface = "#F5F5F5";
        profile.light.surfaceContainer = "#F5F5F5";
        profile.light.surfaceContainerHigh = "#F5F5F5";

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        assertNotEquals(tokens.colorSurface(), tokens.colorSurfaceContainer());
        assertNotEquals(tokens.colorSurfaceContainer(), tokens.colorSurfaceContainerHigh());
        tokens.requireContrast();
    }

    @Test
    public void lightAndDarkSlotsResolveIndependently() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        // #155DFC lands at 4.06:1 on surfaceContainerHighest and would be repaired, which
        // would make this independence check assert repaired rather than chosen values.
        profile.light.primary = "#1039B8";
        profile.dark.primary = "#FFCC00";

        ThemeTokens light = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        ThemeTokens dark = ThemeResolver.resolve(
                ThemeMode.DARK, ThemeSeed.NONE, 0, 0, profile, null, true);
        assertEquals(0xFF1039B8, light.colorPrimary());
        assertEquals(0xFFFFCC00, dark.colorPrimary());
        assertNotEquals(light.colorPrimary(), dark.colorPrimary());
    }

    @Test
    public void opacitySlotsAreClampedToTheValidatorRange() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.scrimOpacity = 0.85f;
        profile.light.dialogOpacity = 0.80f;
        profile.light.overlayOpacity = 0.60f;

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        assertEquals(217, (tokens.colorScrim() >>> 24) & 0xFF);
        assertEquals(0.80f, tokens.dialogOpacity(), 0.0001f);
        assertEquals(153, (tokens.colorOverlayLight() >>> 24) & 0xFF);
    }

    @Test
    public void profileLayersOnTopOfTheSeedInsteadOfReplacingIt() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.focus = "#FF00AA";

        ThemeTokens seeded = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.EXPLICIT, 0xFF0B57D0, 0, profile, null, false);
        assertEquals(0xFFFF00AA, seeded.colorFocus());
        assertTrue(ThemeResolver.lastDiagnostic().contains("profile"));
        // The seed still owns every slot the profile did not override.
        ThemeTokens seedOnly = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.EXPLICIT, 0xFF0B57D0, 0, null, null, false);
        assertEquals(seedOnly.colorPrimary(), seeded.colorPrimary());
        assertEquals(seedOnly.colorSurface(), seeded.colorSurface());
    }

    @Test
    public void invalidProfileFallsBackToLastGoodThenToTheFrozenDefault() {
        ThemeProfile broken = ThemeProfile.defaultProfile();
        broken.light.dialogOpacity = 0.10f;

        ThemeProfile lastGood = ThemeProfile.defaultProfile();
        lastGood.light.primary = "#123456";

        ThemeTokens recovered = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, broken, lastGood, false);
        assertEquals(0xFF123456, recovered.colorPrimary());
        assertTrue(ThemeResolver.lastDiagnostic().startsWith("last-good"));

        ThemeTokens fallback = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, broken, null, false);
        assertEquals(ThemeTokens.light(), fallback);
        assertTrue(ThemeResolver.lastDiagnostic().startsWith("fallback"));
    }

    @Test
    public void invalidSeedStillShortCircuitsToTheDefaultPalette() {
        assertEquals(ThemeTokens.light(), ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.EXPLICIT, 0x00123456, 0, null, null, false));
        assertTrue(ThemeResolver.lastDiagnostic().startsWith("fallback:invalid-seed"));
    }

    /**
     * The B-safe contract promises a profile can never create an unreadable pair.
     * {@code colorPrimary} doubles as the AlertDialog action-button text colour, so a
     * user-chosen dark surface with the default primary rendered those actions at
     * 1.28:1 - measured on device. The resolver must restore Material's own guarantee
     * (its baseline scheme measures 4.97-10.91:1 on the surface roles).
     */
    @Test
    public void customSurfaceKeepsPrimaryLegibleOnEverySurfaceRole() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.surface = "#7B1FA2";
        profile.light.surfaceContainer = "#7B1FA2";
        profile.light.surfaceContainerHigh = "#7B1FA2";

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);

        assertAccentLegible(tokens, "purple surface");
        // Hue is preserved rather than snapped to black/white.
        double hueBefore = Hct.fromInt(ThemeTokens.light().colorPrimary()).getHue();
        double hueAfter = Hct.fromInt(tokens.colorPrimary()).getHue();
        assertEquals("primary must keep its hue", hueBefore, hueAfter, 12.0);
        assertNotEquals("primary must actually move", ThemeTokens.light().colorPrimary(), tokens.colorPrimary());
    }

    @Test
    public void lightSurfaceAlsoRepairsADarkCustomPrimary() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.surface = "#FFFFFF";
        profile.light.primary = "#F2F2F2";

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        assertAccentLegible(tokens, "white surface with near-white primary");
    }

    /**
     * 用户报告的实况：TV 主题编辑器「焦点色」输入 {@code #8C0000}、显示却是 {@code #FFFFFF}。
     * FOCUS 槽当时走的是把前景色直接夹到纯黑/纯白的 ensureContrast，深红在深色 surface
     * {@code #101418} 上只有 1.86:1，于是被整条改成白色。焦点色必须和 primary 一样只走明度。
     */
    @Test
    public void darkSurfaceFocusKeepsTheUsersHueInsteadOfSnappingToWhite() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.dark.focus = "#8C0000";

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.DARK, ThemeSeed.NONE, 0, 0, profile, null, true);

        assertNotEquals("深红焦点色不得被夹成纯白", WHITE, tokens.colorFocus());
        double ratio = ThemeContrast.ratio(tokens.colorFocus(), tokens.colorSurface());
        assertTrue("focus/surface = " + ratio + " must be >= 3.0", ratio + 0.0001 >= 3.0);
        assertEquals("焦点色必须保留用户选定的色相",
                Hct.fromInt(0xFF8C0000).getHue(), Hct.fromInt(tokens.colorFocus()).getHue(), 12.0);
    }

    @Test
    public void repairedPrimaryKeepsItsForegroundReadable() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.surface = "#7B1FA2";
        profile.light.surfaceContainerHigh = "#7B1FA2";

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        assertTrue("onPrimary must stay readable on the repaired primary",
                ThemeContrast.ratio(tokens.colorOnPrimary(), tokens.colorPrimary()) >= 4.5);
    }

    /** The frozen palettes already satisfy the contract, so no repair may fire there. */
    @Test
    public void shippedPalettesAlreadyClearTheAccentContract() {
        assertAccentLegible(ThemeTokens.light(), "frozen light");
        assertAccentLegible(ThemeTokens.dark(), "frozen dark");
    }

    /** An empty profile must stay byte-identical: the repair may not perturb it. */
    @Test
    public void emptyProfileStillResolvesToTheFrozenPalette() {
        assertEquals(ThemeTokens.light(), ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, ThemeProfile.defaultProfile(), null, false));
        assertEquals(ThemeTokens.dark(), ThemeResolver.resolve(
                ThemeMode.DARK, ThemeSeed.NONE, 0, 0, ThemeProfile.defaultProfile(), null, false));
    }

    /** Seed-derived palettes come from one tonal scheme, so no repair may fire. */
    @Test
    public void seedDerivedPalettesAreNotRepaired() {
        for (int seed : new int[]{0xFF0B57D0, 0xFF00897B, 0xFFFB8C00, 0xFF8E24AA}) {
            ThemeTokens tokens = ThemeResolver.resolve(
                    ThemeMode.LIGHT, ThemeSeed.EXPLICIT, seed, 0, null, null, false);
            assertAccentLegible(tokens, String.format("seed %08X", seed));
        }
    }

    /**
     * 空 profile 必须解析出编译期取值：遮罩 {@code #66000000}（{@code @color/black_40}），
     * 遮罩上的文字沿用该表的 colorOnPrimary。这条断言就是「默认字节不变」的机器化表述。
     */
    @Test
    public void itemOverlaySlotsDefaultToTheCompiledAppearance() {
        for (boolean dark : new boolean[]{false, true}) {
            ThemeTokens base = dark ? ThemeTokens.dark() : ThemeTokens.light();
            ThemeTokens resolved = ThemeResolver.resolve(dark ? ThemeMode.DARK : ThemeMode.LIGHT,
                    ThemeSeed.NONE, 0, 0, ThemeProfile.defaultProfile(), null, dark);
            assertEquals("空 profile 的遮罩必须仍是 @color/black_40", 0x66000000, resolved.colorItemOverlay());
            assertEquals(base.colorItemOverlay(), resolved.colorItemOverlay());
            assertEquals("遮罩文字默认沿用 onPrimary", base.colorOnPrimary(), resolved.colorOnItemOverlay());
            assertEquals("遮罩上的文字色必须与编译期 colorOnPrimary 完全一致",
                    base.colorOnPrimary(), base.colorOnItemOverlay());
            assertEquals("深色表同样的落点：black_40 + 该表 onPrimary",
                    dark ? 0xFF062E6F : 0xFFFFFFFF, base.colorOnItemOverlay());
            assertEquals("整张 token 表不得被新槽扰动", base, resolved);
            assertEquals("空 profile 的遮罩文字同样不得漂移", base.colorOnItemOverlay(), resolved.colorOnItemOverlay());
        }
    }

    /**
     * 用户改过遮罩或文字色之后，文字与该遮罩复合在 surface 上仍须 ≥4.5:1（WCAG 正文门槛）；
     * 夹取只走明度、保住色相，与 FOCUS 槽同一口径。遮罩是半透明的，宿主必须按复合后的实色算。
     */
    @Test
    public void userSelectedItemTextStaysReadableOnTheCompositedOverlay() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.itemOverlay = "#FFFFFF";
        profile.light.onItemOverlay = "#FFD54F";

        ThemeTokens tokens = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);

        assertEquals("遮罩底色必须照用户选择写入", 0xFFFFFFFF, tokens.colorItemOverlay());
        int backdrop = ThemeContrast.composite(tokens.colorItemOverlay(), tokens.colorSurface());
        double ratio = ThemeContrast.ratio(tokens.colorOnItemOverlay(), backdrop);
        assertTrue("onItemOverlay/遮罩复合底色 = " + ratio + " 必须 >= 4.5", ratio + 0.0001 >= 4.5);
        assertNotEquals("不可读的文字色必须被修正，而不是原样写入", 0xFFFFD54F, tokens.colorOnItemOverlay());
        assertEquals("夹取只走明度，色相必须保住",
                Hct.fromInt(0xFFFFD54F).getHue(), Hct.fromInt(tokens.colorOnItemOverlay()).getHue(), 12.0);

        // 深色表 + 只改强度：alpha 被强度槽缩放，文字仍在复合底色上达标。
        ThemeProfile darkProfile = ThemeProfile.defaultProfile();
        darkProfile.dark.itemOverlay = "#000000";
        darkProfile.dark.itemOverlayOpacity = 0.85f;
        darkProfile.dark.onItemOverlay = "#062E6F";
        ThemeTokens darkTokens = ThemeResolver.resolve(
                ThemeMode.DARK, ThemeSeed.NONE, 0, 0, darkProfile, null, true);
        assertEquals("强度槽只缩放 alpha", 217, (darkTokens.colorItemOverlay() >>> 24) & 0xFF);
        int darkBackdrop = ThemeContrast.composite(darkTokens.colorItemOverlay(), darkTokens.colorSurface());
        double darkRatio = ThemeContrast.ratio(darkTokens.colorOnItemOverlay(), darkBackdrop);
        assertTrue("深色表 onItemOverlay/复合底色 = " + darkRatio + " 必须 >= 4.5", darkRatio + 0.0001 >= 4.5);
    }

    /** 条目遮罩强度只有 0.05–1.0 这一个合法区间，越界与 NaN 都必须被拒，且只缩放 alpha。 */
    @Test
    public void itemOverlayOpacityIsRangeCheckedAndOnlyScalesAlpha() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertTrue(editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MIN_ITEM_OVERLAY_OPACITY).success());
        assertTrue(editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MAX_ITEM_OVERLAY_OPACITY).success());
        assertFalse("越上界必须被拒绝", editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MAX_ITEM_OVERLAY_OPACITY + 0.01f).success());
        assertFalse("越下界必须被拒绝", editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MIN_ITEM_OVERLAY_OPACITY - 0.01f).success());
        assertFalse("NaN 必须被拒绝", editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                Float.NaN).success());

        // 导入的 JSON 走同一个校验入口，直接塞进 profile 的越界值同样不行。
        ThemeProfile outOfRange = ThemeProfile.defaultProfile();
        outOfRange.light.itemOverlayOpacity = 1.01f;
        assertFalse(ThemeProfileValidator.validate(outOfRange).valid());
        assertTrue(ThemeProfileValidator.validate(outOfRange).message().contains("itemOverlayOpacity"));

        // 强度只缩放遮罩色的 alpha：色相（用户选的颜色）与不透明度互不干扰。
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.itemOverlay = "#123456";
        profile.light.itemOverlayOpacity = 0.2f;
        ThemeTokens tokens = ThemeResolver.resolve(ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, profile, null, false);
        assertEquals(51, (tokens.colorItemOverlay() >>> 24) & 0xFF);
        assertEquals(0x123456, tokens.colorItemOverlay() & 0x00FFFFFF);
    }

    private static void assertAccentLegible(ThemeTokens tokens, String label) {
        String[] roles = {"surface", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest"};
        int[] backgrounds = {tokens.colorSurface(), tokens.colorSurfaceContainer(),
                tokens.colorSurfaceContainerHigh(), tokens.colorSurfaceContainerHighest()};
        for (int index = 0; index < backgrounds.length; index++) {
            double ratio = ThemeContrast.ratio(tokens.colorPrimary(), backgrounds[index]);
            assertTrue(label + ": primary/" + roles[index] + " = " + ratio + " must be >= 4.5",
                    ratio + 0.0001 >= 4.5);
        }
    }
}
