package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mirror of {@link TvFixedDarkSurfaceContrastTest}: a <em>palette-following</em>
 * foreground on a surface whose fill is palette-independent <em>light</em>.
 *
 * <p>That pair breaks in the opposite direction. A drawable with a literal light fill keeps
 * that fill in every table, so the only thing that moves is the foreground role: readable
 * {@code #44474F} in the day table, but {@code #C4C6D0} in the night table - 1.63:1 on
 * {@code #F8FAFD}. Measured on the TV exit-confirm dialog on 2026-10-24 the secondary
 * ("取消") button was a near-white pill with an almost invisible label, because
 * {@code dialog_exit_confirm.xml} painted it with {@code ?attr/colorOnSurfaceVariant} while
 * {@code selector_exit_confirm_secondary} kept its literal light fill.
 *
 * <p>Neither existing guard covers this: {@link LeanbackForegroundContrastTest} only tracks
 * <em>constant light</em> foregrounds, and {@link TvFixedDarkSurfaceContrastTest} only tracks
 * fixed <em>dark</em> surfaces (and only in day mode). This class therefore resolves each
 * palette role in <em>both</em> tables and requires the worst of the two to clear the
 * body-text floor, so a role that only becomes unreadable after dark cannot hide.
 */
public class TvFixedLightSurfaceContrastTest {

    /** Minimum contrast for the button and label text these layouts carry. */
    private static final double MIN_CONTRAST = 4.5;

    /**
     * Fills below this luminance can no longer be called "designed for dark text".
     *
     * <p>The surfaces below are {@code #F8FAFD} / {@code #DCEAFF} / {@code #FAFBFC}; the
     * threshold only needs to separate those from a dark panel role.
     */
    private static final double LIGHT_SURFACE_LUMINANCE = 0.6;

    /**
     * Layouts exempt from this guard, one reason per entry, no empty slots.
     *
     * <p>It was non-empty for exactly one day: the first full run of this guard on 2026-10-24
     * found the same defect on ten more screens (ad-block stats, the MPV profile card, the AI
     * recommendation chips, the CSP step buttons, the MPV config close buttons and the GitHub
     * proxy row). Those were then fixed in the same change as the exit-confirm dialog, so the
     * list is empty again and every layout in all three source sets is covered by
     * {@link #paletteForegroundsStayReadableOnFixedLightSurfaces()}. Add an entry only with a
     * reason that still holds.
     */
    private static final String[] EXEMPT_LAYOUTS = {};

    /**
     * Layouts owned by the Lab module, which pins {@code colorOnSurface} to
     * {@code #FFFFFFFF} in its own {@code Theme.App.Lab} styles. Its roles do not follow the
     * app tables, and the module is hard-coded dark by design, so nothing here applies.
     */
    private static final String[] LAB_LAYOUTS = {
            "adapter_lab_command_compact.xml", "adapter_lab_package.xml",
            "activity_lab_output.xml", "dialog_lab_command_sheet.xml",
    };

    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z0-9_.]+)((?:\"[^\"]*\"|[^>\"])*?)(/?)>", Pattern.DOTALL);
    private static final Pattern BACKGROUND = Pattern.compile("(?:android:background|app:cardBackgroundColor|android:cardBackgroundColor)=\"([^\"]+)\"");
    /**
     * Every attribute that paints a foreground glyph on the element's own background.
     *
     * <p>{@code android:tint} matters for the icon-only buttons ({@code ic_close}) whose colour
     * comes from the drawable tint rather than from a text colour, and the two tab attributes
     * matter because the ad-stats {@code TabLayout} carries its own fixed light fill.
     */
    private static final Pattern FOREGROUND = Pattern.compile(
            "(?:android:textColor|android:textColorHint|android:tint|app:tint"
                    + "|app:tabTextColor|app:tabSelectedTextColor)=\"([^\"]+)\"");
    private static final Pattern SOLID = Pattern.compile("<solid\\s+android:color=\"([^\"]+)\"");
    private static final Pattern GRADIENT = Pattern.compile("android:(?:startColor|endColor|centerColor)=\"([^\"]+)\"");
    private static final Pattern COLOR_ENTRY = Pattern.compile("<color name=\"([^\"]+)\">([^<]+)</color>");
    private static final Pattern SELECTOR_COLOR = Pattern.compile("android:color=\"([^\"]+)\"");
    private static final Pattern STYLE_COLOR_ENTRY = Pattern.compile("<item name=\"([A-Za-z0-9_]+)\">(@color/[A-Za-z0-9_]+)</item>");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern STROKE = Pattern.compile("<stroke\\b[^>]*/>|<stroke\\b.*?</stroke>", Pattern.DOTALL);
    private static final Pattern PADDING = Pattern.compile("<padding\\b[^>]*/>|<padding\\b.*?</padding>", Pattern.DOTALL);
    private static final Pattern MASK_ITEM = Pattern.compile("<item\\b[^>]*@android:id/mask[^>]*>.*?</item>", Pattern.DOTALL);
    private static final Pattern RIPPLE_COLOR = Pattern.compile("<ripple\\b[^>]*android:color=\"[^\"]*\"[^>]*>");

    /** Day-table colour literals, then the night table layered on top by qualifier order. */
    private static final Map<String, String> DAY = new HashMap<>();
    private static final Map<String, String> NIGHT = new HashMap<>();

    /** {@code ?attr/} name to the {@code @color/} resource its theme style points at. */
    private static final Map<String, String> STYLE_COLORS = new LinkedHashMap<>();

    private static boolean loaded;

    // ------------------------------------------------------------------ palette loading

    private static Path root() {
        return Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
    }

    private static String readIfExists(String relative) {
        try {
            Path path = root().resolve(relative);
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n") : null;
        } catch (Exception error) {
            return null;
        }
    }

    private static void loadTables() {
        if (loaded) return;
        loaded = true;
        loadLiterals("src/main/res/values/webhtv_tokens.xml", DAY);
        loadLiterals("src/main/res/values/colors.xml", DAY);
        loadLiterals("src/leanback/res/values/colors.xml", DAY);
        NIGHT.putAll(DAY);
        loadLiterals("src/main/res/values-night/webhtv_tokens.xml", NIGHT);
        loadLiterals("src/main/res/values-night/colors.xml", NIGHT);
        loadLiterals("src/leanback/res/values-night/colors.xml", NIGHT);
        String styles = readIfExists("src/main/res/values/webhtv_styles.xml");
        if (styles == null) return;
        Matcher matcher = STYLE_COLOR_ENTRY.matcher(withoutComments(styles));
        while (matcher.find()) STYLE_COLORS.putIfAbsent(matcher.group(1), matcher.group(2));
    }

    private static void loadLiterals(String file, Map<String, String> target) {
        String source = readIfExists(file);
        if (source == null) return;
        Matcher matcher = COLOR_ENTRY.matcher(withoutComments(source));
        while (matcher.find()) target.put(matcher.group(1), matcher.group(2).trim());
    }

    private static String withoutComments(String source) {
        return COMMENT.matcher(source).replaceAll("");
    }

    /** Literal a {@code @color/} resource resolves to in one table, or null when it follows the palette. */
    private static String literalOf(String name, Map<String, String> table, boolean night) {
        return literalOf(name, table, night, new HashMap<>());
    }

    private static String literalOf(String name, Map<String, String> table, boolean night, Map<String, String> seen) {
        if (seen.put(name, name) != null) return null;
        String value = table.get(name);
        if (value != null) {
            if (value.startsWith("#")) return value;
            if (value.startsWith("@color/")) return literalOf(value.substring("@color/".length()), table, night, seen);
            return null;
        }
        for (String directory : new String[]{"src/main/res/color", "src/leanback/res/color"}) {
            String source = night ? readIfExists(directory + "-night/" + name + ".xml") : null;
            if (source == null) source = readIfExists(directory + "/" + name + ".xml");
            if (source == null) continue;
            if (withoutComments(source).contains("?attr/")) return null;
            Matcher matcher = SELECTOR_COLOR.matcher(withoutComments(source));
            while (matcher.find()) {
                String entry = matcher.group(1);
                if (entry.startsWith("#")) return entry;
                if (entry.startsWith("@color/")) {
                    String resolved = literalOf(entry.substring("@color/".length()), table, night, seen);
                    if (resolved != null) return resolved;
                }
            }
        }
        return null;
    }

    /**
     * The colours a foreground paints in each table, or {@code null} when the guard cannot say.
     *
     * <p>Both kinds of palette-following foreground matter: a bare {@code ?attr/} role, and an
     * {@code @color/} selector that is state-aware and whose states point at such a role (the
     * ad-block labels used {@code @color/dialog_outlined_button_text}, whose every state resolves
     * to {@code ?attr/colorPrimary}). Resolution therefore descends through selectors and theme
     * attributes instead of stopping at the outer resource name.
     *
     * <p>A foreground that resolves to the <em>same</em> literal in both tables is deliberately
     * reported as {@code null}: a palette-independent foreground on a fixed light surface is
     * exactly the fix this guard asks for, not a defect.
     */
    @SuppressWarnings("unchecked")
    private static List<String>[] foregroundColors(String value) {
        List<String> day = new ArrayList<>();
        List<String> night = new ArrayList<>();
        resolve(value, DAY, false, day, new HashMap<>());
        resolve(value, NIGHT, true, night, new HashMap<>());
        if (day.isEmpty() || night.isEmpty()) return null;
        // Identical literals in both tables: palette-independent, i.e. exactly the desired fix.
        if (new java.util.TreeSet<>(day).equals(new java.util.TreeSet<>(night))) return null;
        return new List[]{day, night};
    }

    private static void resolve(String value, Map<String, String> table, boolean night, List<String> out, Map<String, String> seen) {
        if (value == null || seen.put(value, value) != null) return;
        if (value.startsWith("#")) {
            out.add(value);
            return;
        }
        if (value.startsWith("?attr/")) {
            String color = STYLE_COLORS.get(value.substring("?attr/".length()));
            if (color != null) resolve(color, table, night, out, seen);
            return;
        }
        if (value.startsWith("@android:color/")) return;
        if (!value.startsWith("@color/")) return;
        String name = value.substring("@color/".length());
        String literal = table.get(name);
        if (literal != null) {
            resolve(literal, table, night, out, seen);
            return;
        }
        for (String directory : new String[]{"src/main/res/color", "src/leanback/res/color", "src/mobile/res/color"}) {
            String source = night ? readIfExists(directory + "-night/" + name + ".xml") : null;
            if (source == null) source = readIfExists(directory + "/" + name + ".xml");
            if (source == null) continue;
            Matcher matcher = SELECTOR_COLOR.matcher(withoutComments(source));
            while (matcher.find()) resolve(matcher.group(1), table, night, out, seen);
            return;
        }
    }

    private static double luminance(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() == 8) digits = digits.substring(2);
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16) / 255.0;
            channels[i] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    private static int alpha(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        return digits.length() == 8 ? Integer.parseInt(digits.substring(0, 2), 16) : 0xFF;
    }

    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /**
     * Composites a translucent fill over the app's dark content backdrop.
     *
     * <p>A light panel that is translucent becomes darker, which can only move a fill
     * <em>away</em> from the light threshold. The result is therefore conservative: such a
     * panel drops out of this guard rather than producing a false failure.
     */
    private static String compositeOver(String fill, String backdrop) {
        double a = alpha(fill) / 255.0;
        String top = fill.startsWith("#") ? fill.substring(1) : fill;
        if (top.length() == 8) top = top.substring(2);
        String bottom = backdrop.startsWith("#") ? backdrop.substring(1) : backdrop;
        StringBuilder out = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            int front = Integer.parseInt(top.substring(i * 2, i * 2 + 2), 16);
            int back = Integer.parseInt(bottom.substring(i * 2, i * 2 + 2), 16);
            out.append(String.format(Locale.ROOT, "%02X", Math.round(front * a + back * (1 - a))));
        }
        return out.toString();
    }

    /**
     * The fills of one background value: a {@code @drawable/} shape or selector, or a
     * {@code @color/} literal painted with {@code android:background}.
     *
     * <p>The colour form matters: the ad-stats log page lays its column headers and filter
     * buttons on a solid {@code @color/} band rather than on a drawable. That band used to be
     * {@code @color/webhtv_color_scrim} (translucent black over the fixed light panel), which
     * no drawable walk can see, so the headers were measured against the panel underneath and
     * looked fine while the band on screen was mid-grey. It is now a fixed light literal, and
     * this branch is what keeps it in the guard.
     */
    private static List<String> surfaceFills(String background) {
        if (background == null) return null;
        if (background.startsWith("@color/")) {
            String literal = fixedLiteral(background.substring("@color/".length()));
            if (literal == null || alpha(literal) < 0x66) return null;
            return List.of(compositeOver(literal, "#101418"));
        }
        if (!background.startsWith("@drawable/")) return null;
        return fixedFills(background.substring("@drawable/".length()));
    }

    /**
     * The fills of a background in both tables, or {@code null} when it follows the palette.
     *
     * <p>A {@code ?attr/} fill, an unresolvable {@code @color/} or a non-colour value makes
     * the whole drawable palette-following, which is the case this guard does not cover.
     */
    private static List<String> fixedFills(String name) {
        List<String> fills = new ArrayList<>();
        boolean palette = false;
        for (String directory : new String[]{"src/main/res/drawable", "src/leanback/res/drawable"}) {
            String source = readIfExists(directory + "/" + name + ".xml");
            if (source == null) continue;
            String body = RIPPLE_COLOR.matcher(MASK_ITEM.matcher(PADDING.matcher(STROKE.matcher(withoutComments(source))
                    .replaceAll("")).replaceAll("")).replaceAll("")).replaceAll("<ripple>");
            if (body.contains("?attr/")) palette = true;
            Matcher solid = SOLID.matcher(body);
            while (solid.find()) {
                String value = solid.group(1);
                if (value.startsWith("#")) fills.add(value);
                else if (value.startsWith("@color/")) {
                    String literal = fixedLiteral(value.substring("@color/".length()));
                    if (literal == null) palette = true;
                    else fills.add(literal);
                } else if (!value.contains("transparent")) palette = true;
            }
            Matcher gradient = GRADIENT.matcher(body);
            while (gradient.find()) {
                String value = gradient.group(1);
                if (value.startsWith("#")) fills.add(value);
                else if (value.startsWith("@color/")) {
                    String literal = fixedLiteral(value.substring("@color/".length()));
                    if (literal == null) palette = true;
                    else fills.add(literal);
                } else palette = true;
            }
        }
        if (palette) return null;
        List<String> visible = fills.stream().filter(fill -> alpha(fill) >= 0x66).toList();
        if (visible.isEmpty()) return null;
        return visible.stream().map(fill -> compositeOver(fill, "#101418")).toList();
    }

    /**
     * The one literal an {@code @color/} resource paints in <em>both</em> tables.
     *
     * <p>A token whose day and night values differ follows the palette - it is simply not
     * the "fixed fill" this guard is about - so it returns null and drops the whole drawable
     * from the guard, exactly like a {@code ?attr/} fill would.
     */
    private static String fixedLiteral(String name) {
        String day = literalOf(name, DAY, false);
        String night = literalOf(name, NIGHT, true);
        if (day == null || night == null) return null;
        return day.equalsIgnoreCase(night) ? day : null;
    }

    // ------------------------------------------------------------------ the guard

    /**
     * A palette-following foreground must not sit on a palette-independent light surface.
     *
     * <p>Reported per file so a failure names the layout to fix, together with the resolved
     * surface and the worst ratio across the day and night tables.
     */
    @Test
    public void paletteForegroundsStayReadableOnFixedLightSurfaces() throws Exception {
        loadTables();
        List<Path> layouts = new ArrayList<>();
        for (String directory : new String[]{"src/leanback/res/layout", "src/main/res/layout", "src/mobile/res/layout"}) {
            Path base = root().resolve(directory);
            if (!Files.isDirectory(base)) continue;
            try (var paths = Files.walk(base)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".xml"))
                        .sorted()
                        .forEach(layouts::add);
            }
        }
        assertFalse("no layouts were found to inspect", layouts.isEmpty());

        List<String> failures = new ArrayList<>();
        for (Path layout : layouts) {
            String name = layout.getFileName().toString();
            if (isExcluded(name)) continue;
            String source = Files.readString(layout, StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (String[] hit : inspect(source)) {
                double worst = Double.parseDouble(hit[2]);
                if (worst < MIN_CONTRAST) {
                    failures.add(name + ": " + hit[0] + " sits on the fixed light surface " + hit[1]
                            + " with " + ratio(hit[2]) + " at worst (day " + ratio(hit[3]) + ", night " + ratio(hit[4]) + ")");
                }
            }
        }
        if (!failures.isEmpty()) {
            fail("TV paints a palette-following foreground on a palette-independent light surface,"
                    + " which becomes near-white on near-white in the night table:\n  " + String.join("\n  ", failures));
        }
    }

    /**
     * Proves the guard above is not vacuous.
     *
     * <p>The probe feeds {@link #inspect} the exact markup the exit-confirm dialog carried
     * before the fix and requires it to be flagged with a sub-threshold worst-case ratio, so a
     * future regression cannot hide behind an empty match set.
     */
    @Test
    public void theGuardDetectsTheDefectItWasWrittenFor() {
        loadTables();
        String defective = "<LinearLayout android:background=\"@drawable/selector_exit_confirm_secondary\">"
                + "<TextView android:textColor=\"?attr/colorOnSurfaceVariant\" /></LinearLayout>";
        List<String[]> hits = inspect(defective);
        assertFalse("the guard must flag the measured pre-fix markup", hits.isEmpty());
        assertEquals("@drawable/selector_exit_confirm_secondary", hits.get(0)[1]);
        assertTrue("the probe must reproduce the unreadable ratio", Double.parseDouble(hits.get(0)[2]) < MIN_CONTRAST);

        // The ad-block stats found on the second sweep: a state-aware @color/ selector whose states are
        // palette roles, plus a drawable tint instead of a text colour. Both shapes must be caught.
        String selectorDriven = "<LinearLayout android:background=\"@drawable/shape_ad_stats_content\">"
                + "<TextView android:textColor=\"@color/dialog_outlined_button_text\" /></LinearLayout>";
        List<String[]> selectorHits = inspect(selectorDriven);
        assertFalse("a palette-following @color/ selector must be caught too", selectorHits.isEmpty());
        assertTrue("the selector probe must reproduce the unreadable ratio",
                Double.parseDouble(selectorHits.get(0)[2]) < MIN_CONTRAST);

        // The mobile ad-stats log page: a palette role sitting on the fixed light band rather than
        // on a drawable. Without this probe the colour branch of surfaceFills could regress to
        // ignoring @color/ fills and the mobile flavour would silently drift back to 2.37:1.
        String bandDriven = "<LinearLayout android:background=\"@color/fixed_light_band\">"
                + "<TextView android:textColor=\"@color/dialog_outlined_button_text\" /></LinearLayout>";
        List<String[]> bandHits = inspect(bandDriven);
        assertFalse("a palette-following foreground on a fixed @color/ band must be caught", bandHits.isEmpty());
        assertTrue("the band probe must reproduce the unreadable ratio",
                Double.parseDouble(bandHits.get(0)[2]) < MIN_CONTRAST);

        String tinted = "<LinearLayout android:background=\"@drawable/selector_mpv_icon_button\">"
                + "<ImageView android:tint=\"?attr/colorOnSurfaceVariant\" /></LinearLayout>";
        assertFalse("a palette-following drawable tint must be caught", inspect(tinted).isEmpty());

        // The mirror of the same markup: a palette-independent dark foreground must be ignored here.
        String alreadyCorrect = "<LinearLayout android:background=\"@drawable/selector_exit_confirm_secondary\">"
                + "<TextView android:textColor=\"@color/exit_confirm_secondary_text\" /></LinearLayout>";
        assertTrue("a palette-independent foreground is not this guard's concern",
                inspect(alreadyCorrect).isEmpty());
    }

    /**
     * Pins the surface this defect was fixed on, so a later "migrate the TV foregrounds"
     * sweep cannot put a palette-following role back on it.
     */
    @Test
    public void fixedLightPanelsKeepPaletteIndependentForegrounds() throws Exception {
        loadTables();
        String[][] expectations = {
                {"src/leanback/res/layout/dialog_exit_confirm.xml", "@drawable/selector_exit_confirm_secondary",
                        "@color/exit_confirm_secondary_text"},
                {"src/leanback/res/layout/adapter_ad_block_log.xml", "@drawable/selector_ad_stats_item", "@color/fixed_light_focus_text"},
                {"src/leanback/res/layout/adapter_ad_stats_item.xml", "@drawable/selector_ad_stats_item", "@color/fixed_light_focus_text"},
                {"src/leanback/res/layout/dialog_ad_block_stats.xml", "@drawable/shape_ad_stats_content", "@color/fixed_light_focus_text"},
                // The same dialog's log page: a solid @color/ band, not a drawable, so it needs the
                // colour branch of surfaceFills - and its headers plus its eight filter buttons.
                {"src/leanback/res/layout/dialog_ad_block_stats.xml", "@color/fixed_light_band", "@color/fixed_light_focus_text"},

                // The mobile flavour of the same dialog kept the scrim band for one more cycle: its
                // twelve column headers and eight filter buttons still drew palette roles on a band
                // that composites to mid-grey, so the night table painted #A8C7FA on #9D9DA2 (2.37:1).
                {"src/mobile/res/layout/dialog_ad_block_stats.xml", "@color/fixed_light_band", "@color/fixed_light_focus_text"},
                {"src/main/res/layout/adapter_mpv_config_profile.xml", "@drawable/selector_mpv_profile_card", "@color/fixed_light_focus_text"},
                {"src/main/res/layout/dialog_ai_recommendation_info.xml", "@drawable/shape_recommendation_source_chip", "@color/fixed_light_accent"},
                {"src/main/res/layout/dialog_custom_csp.xml", "@drawable/selector_dialog_step_button", "@color/fixed_light_focus_text"},
                {"src/main/res/layout/dialog_mpv_config_create.xml", "@drawable/selector_mpv_icon_button", "@color/fixed_light_focus_icon"},
                {"src/main/res/layout/dialog_mpv_config_manager.xml", "@drawable/selector_mpv_icon_button", "@color/fixed_light_focus_icon"},
                {"src/main/res/layout/dialog_mpv_config_rename.xml", "@drawable/selector_mpv_icon_button", "@color/fixed_light_focus_icon"},
                {"src/mobile/res/layout/adapter_github_proxy.xml", "@drawable/selector_custom_csp_item", "@color/fixed_light_focus_text"},
                {"src/leanback/res/layout/adapter_ad_rule.xml", "@drawable/selector_dialog_switch", "@color/fixed_light_focus_icon"},
                {"src/main/res/layout/adapter_custom_csp.xml", "@drawable/selector_dialog_switch", "@color/fixed_light_focus_icon"},
                {"src/main/res/layout/dialog_update_settings.xml", "@drawable/selector_dialog_step_button", "@color/fixed_light_focus_icon"},
                {"src/leanback/res/layout/dialog_update.xml", "@drawable/selector_dialog_step_button", "@color/fixed_light_focus_icon"},
                {"src/mobile/res/layout/adapter_github_proxy.xml", "@drawable/selector_dialog_switch", "@color/fixed_light_error"},
        };
        for (String[] expectation : expectations) {
            String source = readIfExists(expectation[0]);
            assertTrue(expectation[0] + " is missing", source != null);
            assertTrue(expectation[0] + " must still paint its surface with " + expectation[1],
                    source.contains(expectation[1]));
            assertTrue(expectation[0] + " must paint that surface with the fixed " + expectation[2]
                            + " foreground, not a palette role",
                    source.contains(expectation[2]));
            for (String[] hit : inspect(source)) {
                if (!expectation[1].equals(hit[1])) continue;
                fail(expectation[0] + " must not paint " + hit[0] + " on " + hit[1]
                        + " (worst " + ratio(hit[2]) + ", day " + ratio(hit[3]) + ", night " + ratio(hit[4]) + ")");
            }
        }
    }

    /**
     * The pinned surface must really be palette-independent and light, otherwise the fix
     * above would be protecting the wrong thing.
     */
    @Test
    public void theFixedLightSurfacesAreStillPaletteIndependentAndLight() throws Exception {
        loadTables();
        for (String drawable : new String[]{
                "selector_exit_confirm_secondary", "selector_ad_stats_item", "shape_ad_stats_content",
                "selector_mpv_profile_card", "shape_mpv_active_badge", "shape_recommendation_source_chip",
                "shape_douban_rating_chip_light", "selector_dialog_step_button", "selector_mpv_icon_button",
                "selector_custom_csp_item", "selector_dialog_switch"}) {
            List<String> fills = fixedFills(drawable);
            assertTrue(drawable + " must have no palette-following fill, or the guard is moot", fills != null);
            assertTrue(drawable + " must be light in both tables",
                    fills.stream().allMatch(fill -> luminance(fill) >= LIGHT_SURFACE_LUMINANCE));
        }
    }

    private static String ratio(String value) {
        return String.format(Locale.ROOT, "%.2f:1", Double.parseDouble(value));
    }

    private static boolean isExcluded(String name) {
        for (String exempt : EXEMPT_LAYOUTS) if (exempt.equals(name)) return true;
        for (String lab : LAB_LAYOUTS) if (lab.equals(name)) return true;
        return false;
    }

    /**
     * {@code {foreground, surface, worstRatio, dayRatio, nightRatio}} for every matched element.
     */
    private static List<String[]> inspect(String source) {
        List<String[]> hits = new ArrayList<>();
        Deque<String> backgrounds = new ArrayDeque<>();
        Matcher matcher = TAG.matcher(source);
        while (matcher.find()) {
            boolean closing = !matcher.group(1).isEmpty();
            String attributes = matcher.group(3);
            boolean selfClosing = !matcher.group(4).isEmpty();
            if (closing) {
                if (!backgrounds.isEmpty()) backgrounds.pop();
                continue;
            }
            Matcher backgroundMatcher = BACKGROUND.matcher(attributes);
            String own = backgroundMatcher.find() ? backgroundMatcher.group(1) : null;
            if (!selfClosing) backgrounds.push(own == null ? "" : own);

            Matcher colorMatcher = FOREGROUND.matcher(attributes);
            List<String[]> found = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            while (colorMatcher.find()) {
                List<String>[] palette = foregroundColors(colorMatcher.group(1));
                if (palette == null) continue;
                found.add(new String[]{palette[0].get(0), palette[1].get(0)});
                labels.add(colorMatcher.group(1));
                found.set(found.size() - 1, new String[]{
                        String.join(",", palette[0]), String.join(",", palette[1])});
            }
            if (found.isEmpty()) continue;

            String nearest = own != null && !own.isEmpty() ? own : null;
            if (nearest == null) {
                for (String ancestor : backgrounds) {
                    if (ancestor != null && !ancestor.isEmpty()) {
                        nearest = ancestor;
                        break;
                    }
                }
            }
            List<String> fills = surfaceFills(nearest);
            if (fills == null) continue;
            boolean light = fills.stream().allMatch(fill -> luminance(fill) >= LIGHT_SURFACE_LUMINANCE);
            if (!light) continue;
            for (int index = 0; index < found.size(); index++) {
                double day = Double.MAX_VALUE;
                for (String color : found.get(index)[0].split(",")) {
                    day = Math.min(day, fills.stream().mapToDouble(fill -> contrast(color, fill)).min().orElse(Double.MAX_VALUE));
                }
                double night = Double.MAX_VALUE;
                for (String color : found.get(index)[1].split(",")) {
                    night = Math.min(night, fills.stream().mapToDouble(fill -> contrast(color, fill)).min().orElse(Double.MAX_VALUE));
                }
                double worst = Math.min(day, night);
                hits.add(new String[]{labels.get(index), nearest,
                        String.valueOf(worst), String.valueOf(day), String.valueOf(night)});
            }
        }
        return hits;
    }
}
