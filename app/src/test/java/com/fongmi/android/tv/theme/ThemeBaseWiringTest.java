package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Layer 1 integration contract: both flavours inherit the WebHTV semantic theme and the
 * Activity theme maps every Material color role used by layouts onto a webhtv token, so no
 * screen silently falls back to the Material baseline palette.
 */
public class ThemeBaseWiringTest {

    private static final String[] ACTIVITY_ROLES = {
            "colorPrimary", "colorOnPrimary", "colorPrimaryContainer", "colorOnPrimaryContainer",
            "colorSecondary", "colorOnSecondary", "colorSecondaryContainer", "colorOnSecondaryContainer",
            "colorTertiary", "colorOnTertiary",
            "colorError", "colorOnError", "colorErrorContainer", "colorOnErrorContainer",
            "colorSurface", "colorSurfaceDim", "colorSurfaceBright",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow", "colorSurfaceContainer",
            "colorSurfaceContainerHigh", "colorSurfaceContainerHighest", "colorSurfaceVariant",
            "colorOnSurface", "colorOnSurfaceVariant", "colorOutline", "colorOutlineVariant",
            "colorSurfaceInverse", "colorOnSurfaceInverse", "colorPrimaryInverse",
            "colorControlNormal", "colorControlActivated",
    };

    private static final String[] DIALOG_ROLES = {
            "colorPrimary", "colorOnPrimary",
            "colorSecondaryContainer", "colorOnSecondaryContainer",
            "colorError", "colorOnError", "colorErrorContainer", "colorOnErrorContainer",
            "colorSurface", "colorSurfaceContainer", "colorSurfaceContainerHigh", "colorSurfaceContainerHighest",
            "colorOnSurface", "colorOnSurfaceVariant", "colorOutline", "colorOutlineVariant",
            "colorControlNormal", "colorControlActivated",
    };

    /** Layouts draw these over video, so they keep their own alpha instead of ?attr/colorOnSurface. */
    private static final String[] ON_SURFACE_ALPHA_ATTRS = {
            "colorOnSurface_20", "colorOnSurface_70", "colorOnSurface_80", "colorOnSurface_90",
    };

    /**
     * List/overlay layouts whose text is a flat colour over a fill that is dark in
     * every state.
     *
     * <p>Upstream painted these with a flat light colour ({@code @color/white},
     * {@code @color/white_60/80}); the unification to {@code ?attr/colorOnSurface}
     * resolved them to #1A1C1E in day mode and made them dark-on-dark over the
     * wallpaper, the video surface and the translucent white glass panels. Upstream
     * reused the very same item layouts inside {@code dialog_quick_search},
     * {@code dialog_episode_list} and {@code dialog_receive}, so a single always-light
     * role is correct in both the page and the dialog host.
     *
     * <p>The fill of each of these is {@code shape_live} (#47000000 / #5C000000),
     * {@code shape_vod_list} / {@code shape_vod_name} (#33000000),
     * {@code shape_video_episode_grid} (#99000000), {@code shape_quick_search_item} or a
     * {@code ?attr/colorSurfaceContainer*} panel - never a light one - so no state list
     * is needed. Their adapters were checked for {@code setSelected}: none of them call
     * it except through {@code shape_item}'s states, which this list excludes.
     */
    private static final String[] WALLPAPER_ITEMS = {
            "adapter_channel.xml", "adapter_epg_data.xml", "adapter_file.xml",
            "adapter_group.xml", "adapter_search.xml", "adapter_search_hot_word.xml",
            "adapter_search_record.xml", "adapter_search_word.xml", "adapter_vod.xml",
            "adapter_vod_list.xml", "adapter_vod_oval.xml", "view_empty.xml",
            "adapter_quick.xml", "view_progress.xml",
    };

    /**
     * Selectable items whose fill turns light when selected, so a flat colour cannot
     * serve both states.
     *
     * <p>{@code selector_item} is the shared fill of these items in the mobile flavour:
     * {@code state_selected} paints {@code colorSecondaryContainer} at 0.65 alpha - a
     * light grey in day mode (#DFE2E6 over the wallpaper measured #BAB9C5) - while the
     * default entry paints 15% black over the wallpaper, which is dark. Measured on the
     * home type chips: white text on the selected chip gave 1.94:1, dark text gave
     * 8.82:1; on an unselected chip white gave 6.32:1 and dark 2.71:1. Neither a flat
     * white nor a flat dark value can be right for both, which is why upstream shipped
     * these as state lists and why dev3 carried them until the unification replaced them.
     *
     * <p>This is the exact set dev3 used before that change; restoring it is the fix.
     */
    private static final String[][] WALLPAPER_STATE_ITEMS = {
            // chips over shape_item / shape_item_round -> selector_item
            {"adapter_type.xml", "@color/selector_text"},
            {"adapter_collect.xml", "@color/selector_text"},
            // episode / quality / flag rows over shape_video_item
            {"adapter_episode_grid.xml", "@color/selector_video_text"},
            {"adapter_episode_group.xml", "@color/selector_video_text"},
            {"adapter_episode_hori.xml", "@color/selector_video_text"},
            {"adapter_flag.xml", "@color/selector_video_text"},
            {"adapter_quality.xml", "@color/selector_video_text"},
    };

    /** Pages whose rows sit directly on the (dark) app wallpaper. */
    private static final String[] WALLPAPER_PAGES = {
            "fragment_setting.xml", "fragment_setting_ad.xml", "fragment_setting_ai.xml",
            "fragment_setting_danmaku.xml", "fragment_setting_enhance.xml",
            "fragment_setting_personal.xml", "fragment_setting_player.xml",
            "fragment_setting_subtitle.xml", "fragment_setting_tmdb.xml",
    };

    /**
     * Player bottom sheets whose own root background is a <em>dark</em> translucent
     * indigo glass panel, never a light one.
     *
     * <p>{@code shape_player_child_sheet_panel}, {@code shape_dialog_glass_panel},
     * {@code shape_dialog_control_glass_panel}, {@code shape_quick_search_panel},
     * {@code shape_danmaku_sheet_panel} and {@code shape_danmaku_setting_panel} are all
     * {@code #E62F315E -> #D6282955 -> #CC303463} (or {@code #E61F1F22}); composited over
     * any frame they stay dark, so their foreground must not follow the palette.
     *
     * <p>These are the mobile peers of the leanback sheets. Historically leanback used the
     * identical drawables with {@code ?attr/colorOnSurface}, which worked on TV only because
     * the leanback table was dark in every profile ({@code webhtv_color_on_surface} =
     * {@code #E2E2E9}). Now that TV resolves the same day/night tables as mobile, the TV
     * sheets carry the same palette-independent constants as these mobile ones, so both
     * flavours stay readable in both modes.
     *
     * <p>{@code 0af2340d4} replaced the flat light values ({@code @color/white},
     * {@code white_70}, {@code white_90}, {@code white_50/60}) in exactly these files, and
     * this list is that change's inverse. The fill is constant in every state, so a flat
     * light foreground is correct and no state list is needed.
     */
    private static final String[] DARK_GLASS_SHEETS = {
            "dialog_danmaku.xml", "dialog_danmaku_search.xml", "dialog_danmaku_setting.xml",
            "dialog_episode_list.xml", "dialog_live.xml", "dialog_live_epg.xml",
            "dialog_offset.xml", "dialog_quick_search.xml", "dialog_timer.xml",
            "dialog_title.xml", "dialog_track.xml", "dialog_video_content.xml",
    };

    /**
     * Palette-independent foregrounds that are correct on a dark surface in every table.
     *
     * <p>{@code webhtv_on_wallpaper} and {@code webhtv_color_player_control_muted} are
     * {@code #FFFFFF} / {@code #CCFFFFFF} in both token tables (the day and night files),
     * which is what makes them safe on a surface whose darkness does not
     * depend on the palette. {@code webhtv_color_overlay_light} is the matching constant
     * ripple fill. They are semantic roles, not raw values, so the sheets stay inside the
     * token system and need no allowlist exemption of their own.
     */
    private static final String[] CONSTANT_LIGHT_FOREGROUNDS = {
            "?attr/webhtvColorOnWallpaper",
            "@color/webhtv_color_player_control_muted",
            "@color/webhtv_color_overlay_light",
            "@color/selector_control_sheet_text",
    };

    /**
     * The sheet button state list must stay palette-independent too.
     *
     * <p>{@code dialog_danmaku_setting} and {@code dialog_timer} paint their option buttons
     * with {@code @color/selector_control_sheet_text} over
     * {@code selector_player_child_sheet_button}, a dark translucent fill. The list resolves
     * to {@code @color/white} / {@code #B3FFFFFF} plus the black selected/activated pair
     * that matches the light selected band, so it never follows the palette. Routing it
     * through {@code ?attr/colorOnSurface} would put near-black text on the dark sheet in
     * day mode, exactly like the layouts above.
     */
    @Test
    public void controlSheetButtonTextStaysPaletteIndependent() throws Exception {
        String selector = withoutComments(read("src/mobile/res/color/selector_control_sheet_text.xml"));
        assertFalse("the sheet button text must not follow the palette",
                selector.contains("?attr/"));
        assertTrue(selector.contains("@color/white"));
        assertTrue(selector.contains("#B3FFFFFF"));
        assertTrue("the selected state must stay dark on the light selected band",
                selector.contains("#FF000000"));
    }

    /** Attributes that paint a foreground onto the sheet's own dark panel. */
    private static final String[] SHEET_FOREGROUND_ATTRS = {
            "android:textColor", "android:textColorHint", "app:tint",
            "app:strokeColor", "app:rippleColor", "app:boxStrokeColor",
    };

    @Test
    public void flavourBaseThemesInheritTheWebhtvSemanticTheme() throws Exception {
        String mobile = read("src/mobile/res/values/styles.xml");
        assertTrue(mobile.contains("<style name=\"Theme.Base\" parent=\"Theme.WebHTV.Mobile\">"));
        assertFalse(mobile, mobile.contains("Theme.Material3.DynamicColors"));
        assertTrue(mobile.contains("<item name=\"materialAlertDialogTheme\">@style/ThemeOverlay.WebHTV.Dialog</item>"));
        assertTrue(mobile.contains("<item name=\"android:statusBarColor\">@color/transparent</item>"));

        String leanback = read("src/leanback/res/values/styles.xml");
        assertTrue(leanback.contains("<style name=\"Theme.Base\" parent=\"Theme.WebHTV.TV\">"));
        assertFalse(leanback, leanback.contains("<item name=\"colorPrimary\">@color/white</item>"));
    }

    @Test
    public void activityThemeMapsEveryMaterialColorRole() throws Exception {
        String theme = read("src/main/res/values/webhtv_styles.xml");
        assertFalse(theme, theme.contains("DynamicColors"));
        String body = styleBody(theme, "Theme.WebHTV");
        for (String role : ACTIVITY_ROLES) {
            assertTrue(role, body.contains("<item name=\"" + role + "\">@color/webhtv_"));
        }
        assertTrue(body.contains("<item name=\"android:colorBackground\">@color/webhtv_color_surface</item>"));
        assertTrue(body.contains("<item name=\"android:windowBackground\">?attr/colorSurface</item>"));
    }

    /**
     * Material points the framework text attributes at its own
     * {@code m3_sys_color_*} palette, so a widget built from the Activity context keeps
     * Material's colours and ThemeBinder cannot rewrite them (measured on device:
     * Material's {@code on_surface_variant} {@code #49454F} survived a custom theme).
     * The activity theme must therefore map the framework text roles onto our tokens.
     */
    @Test
    public void activityThemeMapsFrameworkTextRolesToWebhtvTokens() throws Exception {
        String theme = read("src/main/res/values/webhtv_styles.xml");
        String body = styleBody(theme, "Theme.WebHTV");
        for (String role : new String[]{
                "android:textColorPrimary", "android:textColorSecondary",
                "android:textColorTertiary", "android:textColorHint"}) {
            assertTrue(role + " must resolve through a webhtv selector",
                    body.contains("<item name=\"" + role + "\">@color/webhtv_text_"));
        }
        String primary = read("src/main/res/color/webhtv_text_primary.xml");
        String secondary = read("src/main/res/color/webhtv_text_secondary.xml");
        assertTrue(primary.contains("@color/webhtv_color_on_surface"));
        assertTrue(secondary.contains("@color/webhtv_color_on_surface_variant"));
        // Disabled emphasis must survive, otherwise disabled widgets no longer dim.
        assertTrue(primary.contains("android:state_enabled=\"false\""));
        assertTrue(secondary.contains("android:state_enabled=\"false\""));
    }

    @Test
    public void dialogsReuseTheSameSemanticRoles() throws Exception {
        String theme = read("src/main/res/values/webhtv_styles.xml");
        for (String style : new String[]{"Theme.WebHTV.Dialog", "ThemeOverlay.WebHTV.Dialog"}) {
            String body = styleBody(theme, style);
            for (String role : DIALOG_ROLES) {
                assertTrue(style + " is missing " + role, body.contains("<item name=\"" + role + "\">"));
            }
        }
    }

    @Test
    public void everyLayoutReferencedOnSurfaceAlphaAttrIsDeclaredAndThemed() throws Exception {
        StringBuilder layouts = new StringBuilder();
        for (String directory : new String[]{
                "src/main/res/layout", "src/mobile/res/layout", "src/leanback/res/layout"}) {
            Path root = Path.of(directory);
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    layouts.append(Files.readString(path, StandardCharsets.UTF_8));
                }
            }
        }
        String attrs = read("src/main/res/values/webhtv_attrs.xml");
        String theme = styleBody(read("src/main/res/values/webhtv_styles.xml"), "Theme.WebHTV");
        for (String attr : ON_SURFACE_ALPHA_ATTRS) {
            if (!layouts.toString().contains("?attr/" + attr)) continue;
            assertTrue(attr + " is used by a layout but never declared", attrs.contains("name=\"" + attr + "\" format=\"color\""));
            assertTrue(attr + " is declared but never assigned by Theme.WebHTV", theme.contains("<item name=\"" + attr + "\">"));
        }
    }

    @Test
    public void onSurfaceAlphaResourcesTrackTheOnSurfaceToken() throws Exception {
        String[][] palettes = {
                {"src/main/res/values/webhtv_tokens.xml", "webhtv_color_on_surface", "src/main/res/values-night/webhtv_tokens.xml"},
        };
        for (String[] palette : palettes) {
            assertAlphaVariants(palette[0], palette[2]);
        }
        // leanback 不再自带 token 表：day 走 main/values，night 走 main/values-night，
        // 所以这里不再有第三张需要单独校验的色板。
    }

    /**
     * A page that sits on the wallpaper must not use {@code ?attr/colorOnSurface}.
     *
     * <p>{@code BaseActivity} paints a full-screen {@code CustomWallView} under every
     * mobile activity and every built-in wall is dark, while dialogs draw their text on a
     * light panel. A single {@code ?attr/colorOnSurface} therefore cannot serve both: the
     * light table made the settings rows near-black on the wallpaper (measured 1.27:1),
     * the dark table turned dialog text light on a light card (measured 1.24:1). The two
     * consumers are separated by {@code ?attr/webhtvColorOnWallpaper}, which is always
     * light.
     *
     * <p>Guard: this pins both halves so a future migration cannot quietly put a page back
     * on the dialog role, and cannot make the wallpaper role follow the surface at all.
     */
    @Test
    public void wallpaperPagesUseTheWallpaperForegroundRole() throws Exception {
        String[] all = new String[WALLPAPER_PAGES.length + WALLPAPER_ITEMS.length];
        System.arraycopy(WALLPAPER_PAGES, 0, all, 0, WALLPAPER_PAGES.length);
        System.arraycopy(WALLPAPER_ITEMS, 0, all, WALLPAPER_PAGES.length, WALLPAPER_ITEMS.length);
        for (String name : all) {
            String source = read("src/mobile/res/layout/" + name);
            assertFalse(name + " still paints wallpaper rows with the dialog on-surface role",
                    source.contains("android:textColor=\"?attr/colorOnSurface\""));
            assertTrue(name + " must use ?attr/webhtvColorOnWallpaper for its rows",
                    source.contains("android:textColor=\"?attr/webhtvColorOnWallpaper\""));
        }
    }

    /**
     * A dark glass sheet must not use a palette-following role for its text or icons.
     *
     * <p>The inverse guard of {@link #wallpaperPagesUseTheWallpaperForegroundRole}: those
     * pages sit on the wallpaper, these on a dark translucent panel. Both need a light
     * foreground, and both are broken the same way - by routing through
     * {@code ?attr/colorOnSurface}, which is near-black in the mobile day table.
     *
     * <p>{@code ?attr/colorOutline} and {@code ?attr/colorOnSurfaceVariant} were rewritten
     * to those roles by the same commit on the sheet's ripple, stroke and secondary text;
     * they are pinned here too because the stroke and ripple sit directly on the dark
     * panel. The replacements are the palette-independent roles
     * {@code webhtvColorOnWallpaper}, {@code webhtv_color_player_control_muted} and
     * {@code webhtv_color_overlay_light}.
     *
     * <p>The check is per attribute, not per file: every foreground attribute in these
     * layouts must resolve to one of those constants, so a future edit cannot reintroduce
     * a palette-following role on one node while leaving another node correct.
     */
    @Test
    public void darkGlassSheetsUseAConstantLightForeground() throws Exception {
        for (String name : DARK_GLASS_SHEETS) {
            String source = withoutComments(read("src/mobile/res/layout/" + name));
            int checked = 0;
            for (String attr : SHEET_FOREGROUND_ATTRS) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile(java.util.regex.Pattern.quote(attr) + "=\"([^\"]+)\"")
                        .matcher(source);
                while (matcher.find()) {
                    String value = matcher.group(1);
                    checked++;
                    boolean constant = false;
                    for (String allowed : CONSTANT_LIGHT_FOREGROUNDS) {
                        if (value.equals(allowed)) constant = true;
                    }
                    assertTrue(name + " paints " + attr + "=\"" + value
                                    + "\" on its dark glass panel, which is not a palette-independent"
                                    + " light foreground (allowed: "
                                    + String.join(", ", CONSTANT_LIGHT_FOREGROUNDS) + ")",
                            constant);
                }
            }
            assertTrue(name + " must paint at least one foreground on its sheet", checked > 0);
        }
    }

    /**
     * The quick-search row's site name ({@code #site}) must stay on the wallpaper role.
     *
     * <p>Commit {@code 6bef992767} changed exactly one node: {@code adapter_quick.xml}'s
     * {@code #site} text was still {@code ?attr/colorOnSurfaceVariant}, which resolves through
     * {@code @color/webhtv_color_on_surface_variant} to a dark day-mode {@code #44474F} that
     * flips to a near-white {@code #C4C6D0} in the night table - a palette-following role, so it
     * cannot serve a row on the always-dark quick-search panel. The guard meant to catch it,
     * {@link #wallpaperPagesUseTheWallpaperForegroundRole}, is a whole-file check: it asks for
     * the quoted value {@code android:textColor="?attr/webhtvColorOnWallpaper"} and for the
     * absence of the complete quoted literal {@code android:textColor="?attr/colorOnSurface"},
     * closing quote included. That literal matches the value {@code colorOnSurface} itself and
     * nothing longer, so {@code colorOnSurfaceVariant} never matched it. The sibling
     * {@code #name} and {@code #remark} nodes already satisfied both halves, so the stale
     * {@code #site} value never failed it - the defect shipped green.
     *
     * <p>This is the per-node contract that closes that hole: the element carrying
     * {@code @+id/site} must resolve to the wallpaper role and to nothing on the
     * {@code colorOnSurface} family, so the wrong value cannot come back on this node while its
     * siblings stay correct.
     */
    @Test
    public void quickSearchRowSiteNodeUsesTheWallpaperForegroundRole() throws Exception {
        String source = withoutComments(read("src/mobile/res/layout/adapter_quick.xml"));
        String site = element("adapter_quick.xml", source, "android:id=\"@+id/site\"");
        assertEquals("#site must paint its text with the wallpaper role",
                "?attr/webhtvColorOnWallpaper", attribute(site, "android:textColor"));
        assertFalse("#site must not follow the palette on-surface family",
                site.contains("colorOnSurface"));
    }

    /**
     * The quick-search icon button must stay a constant 6dp rounded translucent-white glass chip.
     *
     * <p>Commit {@code 169bc59860} replaced the opaque light blue fill {@code #E8F0FE} of
     * {@code shape_quick_search_icon_button} with {@code #12FFFFFF} plus a {@code 1dp}
     * {@code #40FFFFFF} hairline, because the white magnifier icon disappeared into the light
     * blue chip that sat on the dark search panel. No existing guard covers this file:
     * {@link #darkGlassSheetsUseAConstantLightForeground} only scans the foreground attributes of
     * {@code DARK_GLASS_SHEETS} layouts and never opens a drawable, so a revert to the light blue
     * fill would have stayed green.
     *
     * <p>Both halves of the glass look are pinned per element - fill and stroke - so a later edit
     * cannot move one without the other, and the legacy light blue is rejected outright. The
     * {@code 6dp} corners are deliberate rather than incidental: they are byte-identical to the
     * neighbouring {@code shape_quick_search_input.xml}, so this is a chip, not a capsule.
     */
    @Test
    public void quickSearchIconButtonKeepsTheDarkGlassFillAndStroke() throws Exception {
        String name = "shape_quick_search_icon_button.xml";
        String source = withoutComments(read("src/mobile/res/drawable/" + name));
        assertEquals(name + " must fill the button with translucent white glass",
                "#12FFFFFF", attribute(element(name, source, "<solid"), "android:color"));
        assertFalse(name + " must not go back to the opaque light blue fill",
                source.contains("#E8F0FE"));
        String stroke = element(name, source, "<stroke");
        assertEquals(name + " must keep the glass hairline stroke",
                "#40FFFFFF", attribute(stroke, "android:color"));
        assertEquals(name + " must keep the 1dp stroke width",
                "1dp", attribute(stroke, "android:width"));
    }

    /**
     * The TMDB source chips must keep a constant translucent fill for the dark-page
     * variant.
     *
     * <p>{@code FlagAdapter.applyTmdbTheme} swaps the chip between two variants:
     * {@code selector_tmdb_flag_item} (light page, dark text) and
     * {@code selector_tmdb_flag_item_dark} (dark page, light text
     * {@code #F3F7FA} / {@code #8FE7B6}). Commit {@code e1ea7ab34} moved the dark variant
     * onto the palette-following surfaces {@code webhtv_color_surface_container*} and
     * {@code webhtv_color_success_container}. In the mobile day table those resolve to
     * {@code #ECEEF4} / {@code #E7E8EF} / {@code #C4EED0}, so the dark variant became
     * light-fill + near-white text: measured 1.08:1 and 1.16:1.
     *
     * <p>The chip that the player shows by default therefore lost its contrast, so the
     * dark variant must stay on constant translucent white. Its light sibling keeps the
     * semantic surfaces because there the text is dark.
     *
     * <p>Both files are stripped of XML comments before matching, so the prose in either
     * file cannot satisfy or defeat an assertion.
     */
    @Test
    public void tmdbDarkChipVariantKeepsAConstantTranslucentFill() throws Exception {
        String dark = withoutComments(read("src/mobile/res/drawable/selector_tmdb_flag_item_dark.xml"));
        for (String surface : new String[]{
                "webhtv_color_surface_container", "webhtv_color_success_container",
                "webhtv_color_primary_container", "webhtv_color_outline_variant"}) {
            assertFalse("the dark-page chip must not follow the palette surface " + surface,
                    dark.contains("@color/" + surface));
        }
        for (String fill : new String[]{"#26FFFFFF", "#33FFFFFF", "#664B8F72"}) {
            assertTrue("the dark-page chip must keep the constant translucent fill " + fill,
                    dark.contains(fill));
        }
        // Its paired text state list is the light-on-dark one; keep the two in step.
        String text = withoutComments(read("src/mobile/res/color/selector_tmdb_flag_text_dark.xml"));
        assertTrue(text.contains("#F3F7FA"));
        String light = withoutComments(read("src/mobile/res/drawable/selector_tmdb_flag_item.xml"));
        assertTrue("the light-page chip keeps its semantic light surface",
                light.contains("@color/webhtv_color_surface_container"));
    }

    /** Drops XML comments so file prose cannot satisfy or defeat a source assertion. */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", "");
    }

    /**
     * The single tag whose text contains {@code anchor}, from its opening {@code '<'} to its
     * closing {@code '>'}.
     *
     * <p>A per-node contract needs the element itself: a whole-file {@code contains} check passes
     * as soon as <em>any</em> node carries the wanted value, which is how a stale value on one
     * node of {@code adapter_quick.xml} survived a green suite. Fails loudly when the anchor is
     * missing or ambiguous, so a renamed id or element cannot turn the guard into a no-op.
     */
    private static String element(String name, String source, String anchor) {
        int at = source.indexOf(anchor);
        if (at < 0) {
            fail(name + " no longer contains \"" + anchor
                    + "\"; update this contract if the node was renamed or removed");
        }
        if (source.indexOf(anchor, at + 1) >= 0) {
            fail(name + " contains \"" + anchor + "\" more than once; the contract needs an"
                    + " anchor that identifies exactly one node");
        }
        int start = source.lastIndexOf('<', at);
        int end = source.indexOf('>', at);
        if (start < 0 || end < at) {
            fail(name + " has no complete tag around \"" + anchor + "\"");
        }
        return source.substring(start, end + 1);
    }

    /** The value of attribute {@code attr} in {@code tag}, or {@code null} when it is not set. */
    private static String attribute(String tag, String attr) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(attr) + "=\"([^\"]*)\"")
                .matcher(tag);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Selectable items must keep a state list, not a flat colour.
     *
     * <p>Their shared fill turns light when selected, so the text has to switch with it:
     * {@code selector_text} resolves to {@code colorOnSecondaryContainer}, and
     * {@code selector_video_text} resolves to the selected highlight, both falling back
     * to a light default. Collapsing either to one colour reintroduces an unreadable pair
     * on whichever state loses.
     */
    @Test
    public void selectableWallpaperItemsKeepAStateList() throws Exception {
        for (String[] entry : WALLPAPER_STATE_ITEMS) {
            String name = entry[0];
            String selector = entry[1];
            String source = read("src/mobile/res/layout/" + name);
            assertFalse(name + " must not fall back to the dialog on-surface role",
                    source.contains("android:textColor=\"?attr/colorOnSurface\""));
            assertTrue(name + " must paint its selectable text with " + selector,
                    source.contains("android:textColor=\"" + selector + "\""));
        }
    }

    /**
     * The state lists themselves must stay light by default and distinct when selected.
     *
     * <p>A flat list would silently defeat the guard above, so the semantics are pinned:
     * every entry resolves to a colour, the default entry is the light one used over the
     * dark wallpaper, and the selected entry is not the same as it.
     */
    @Test
    public void wallpaperStateListsAreLightByDefaultAndDistinctWhenSelected() throws Exception {
        String[][] selectors = {
                {"selector_text.xml", "@color/white"},
                {"selector_video_text.xml", "@color/white"},
        };
        for (String[] entry : selectors) {
            String name = entry[0];
            String source = read("src/mobile/res/color/" + name);
            assertTrue(name + " must declare a selected state",
                    source.contains("state_selected=\"true\""));
            assertTrue(name + " must fall back to the light wallpaper foreground",
                    source.contains("<item android:color=\"" + entry[1] + "\" />"));
            String selected = selectedColor(source);
            assertFalse(name + " selected colour must differ from its default",
                    entry[1].equals(selected));
            assertFalse(name + " must not reuse the dialog on-surface role",
                    selected.contains("colorOnSurface\""));
        }
    }

    /** Colour of the first entry guarded by {@code state_selected}. */
    private static String selectedColor(String selectorSource) {
        for (String line : selectorSource.split("\n")) {
            if (line.contains("state_selected=\"true\"")) {
                int start = line.indexOf("android:color=\"");
                assertTrue("selected entry has no colour: " + line, start >= 0);
                return line.substring(start + "android:color=\"".length(), line.indexOf('"', start + "android:color=\"".length()));
            }
        }
        throw new AssertionError("no state_selected entry found");
    }

    /**
     * The wallpaper foreground must stay light, identical in every palette, and out of
     * the binder's reach.
     *
     * <p>It is 0xFFFFFFFF in both tables because the wallpaper is dark in both
     * system night modes. {@code ThemeBinder} only rewrites a view colour when
     * {@code ThemeColorIndex.replacementFor} yields a different value, so the guard is:
     * for every baseline palette and every active palette, 0xFFFFFFFF must resolve to
     * either no replacement or the value it already has.
     *
     * <p>The two palettes keep the colour out of reach for different reasons, and both
     * are fragile, so both are pinned. In the light palette 0xFFFFFFFF is shared by
     * {@code onPrimary}, {@code onError}, {@code onSuccess} and {@code onWarning}; those
     * roles only agree when they resolve to the identical colour, and the index reports
     * that as no change. In the dark palette no indexed role carries the value at all
     * ({@code colorPlayerControl} is 0xFFFFFFFF in both palettes but is not a
     * {@link ThemeRole}), so the lookup finds nothing to rewrite. A future palette edit
     * that made an indexed role resolve 0xFFFFFFFF to a different colour would start
     * repainting wallpaper text - this test fails first.
     */
    @Test
    public void wallpaperForegroundIsLightAndNotBinderRewritable() throws Exception {
        String light = read("src/main/res/values/webhtv_tokens.xml");
        String night = read("src/main/res/values-night/webhtv_tokens.xml");
        // TV 复用 main 的两张表（day/night），不再有第三张恒深色表。
        for (String source : new String[]{light, night}) {
            assertTrue("every palette must declare webhtv_on_wallpaper",
                    source.contains("<color name=\"webhtv_on_wallpaper\">#FFFFFF</color>"));
            // 次级角色两张表同值，理由与上面一致：壁纸在明暗两种模式下都是深色，
            // 因此这一角色不需要随模式切换，声明两处只为让"每张表都自描述"这一点成立。
            assertTrue("every palette must declare webhtv_on_wallpaper_variant",
                    source.contains("<color name=\"webhtv_on_wallpaper_variant\">#CCFFFFFF</color>"));
        }

        String attrs = read("src/main/res/values/webhtv_attrs.xml");
        assertTrue("webhtvColorOnWallpaper must be declared",
                attrs.contains("name=\"webhtvColorOnWallpaper\" format=\"color\""));
        assertTrue("webhtvColorOnWallpaperVariant must be declared",
                attrs.contains("name=\"webhtvColorOnWallpaperVariant\" format=\"color\""));
        String theme = styleBody(read("src/main/res/values/webhtv_styles.xml"), "Theme.WebHTV");
        assertTrue("Theme.WebHTV must assign webhtvColorOnWallpaper",
                theme.contains("<item name=\"webhtvColorOnWallpaper\">@color/webhtv_on_wallpaper</item>"));
        assertTrue("Theme.WebHTV must assign webhtvColorOnWallpaperVariant",
                theme.contains("<item name=\"webhtvColorOnWallpaperVariant\">@color/webhtv_on_wallpaper_variant</item>"));

        // The binder only rewrites a colour when the index yields a different
        // replacement, so 0xFFFFFFFF must never resolve to one for any palette pair.
        ThemeTokens[] palettes = {ThemeTokens.light(), ThemeTokens.dark()};
        for (ThemeTokens baseline : palettes) {
            ThemeColorIndex index = ThemeColorIndex.of(baseline);
            for (ThemeTokens active : palettes) {
                assertNull("the wallpaper foreground must never be rewritten by the binder",
                        index.replacementFor(0xFFFFFFFF, active));
            }
        }
    }

    private static void assertAlphaVariants(String lightOrTvPath, String nightPath) throws Exception {
        for (String path : nightPath == null ? new String[]{lightOrTvPath} : new String[]{lightOrTvPath, nightPath}) {
            String source = read(path);
            String onSurface = hex(source, "webhtv_color_on_surface");
            assertEquals(path + " must declare the onSurface token", 6, onSurface.length());
            assertTrue(path + " webhtv_on_surface_20",
                    source.contains("<color name=\"webhtv_on_surface_20\">#33" + onSurface + "</color>"));
            assertTrue(path + " webhtv_on_surface_70",
                    source.contains("<color name=\"webhtv_on_surface_70\">#B3" + onSurface + "</color>"));
            assertTrue(path + " webhtv_on_surface_80",
                    source.contains("<color name=\"webhtv_on_surface_80\">#CC" + onSurface + "</color>"));
            assertTrue(path + " webhtv_on_surface_90",
                    source.contains("<color name=\"webhtv_on_surface_90\">#E6" + onSurface + "</color>"));
        }
    }

    private static String hex(String source, String colorName) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<color name=\"" + colorName + "\">#([0-9A-Fa-f]{6})</color>")
                .matcher(source);
        assertTrue(colorName + " is missing", matcher.find());
        return matcher.group(1).toUpperCase(java.util.Locale.ROOT);
    }

    private static String styleBody(String source, String style) {
        int start = source.indexOf("<style name=\"" + style + "\"");
        assertTrue(style + " is missing", start >= 0);
        int end = source.indexOf("</style>", start);
        assertTrue(style + " is not closed", end > start);
        return source.substring(start, end);
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
