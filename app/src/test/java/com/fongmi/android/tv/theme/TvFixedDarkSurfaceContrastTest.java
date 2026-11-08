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
 * The TV mirror of {@link LeanbackForegroundContrastTest}'s "constant light foreground" guard.
 *
 * <p>{@code LeanbackForegroundContrastTest} catches a <em>palette-independent light</em>
 * foreground on a light day-mode surface. This class catches the opposite, equally
 * unreadable pair: a <em>palette-following dark</em> foreground on a surface that is
 * palette-independent <em>dark</em>.
 *
 * <p>That combination cannot be seen while a flavour pins its token table, which is
 * exactly why it went unnoticed: TV used to compile a permanent dark
 * {@code leanback/res/values/webhtv_tokens.xml}, so {@code ?attr/colorOnSurface} was
 * {@code #E2E2E9} in every configuration and painting it on a fixed dark panel was
 * correct. Commit {@code 35a97894a2} ("真正打通 TV 浅色模式") deleted that table, so TV now
 * resolves the same day/night tables as mobile and the same markup becomes near-black
 * text on a dark panel. Measured on {@code 192.168.50.3:5559} (1920x1080, day mode) the
 * exit-confirm primary button went from 4.95:1 to <b>2.18:1</b>; {@code dialog_display}
 * 15.53:1 to <b>1.16:1</b>, {@code dialog_disc_menu} 12.81:1 to <b>1.04:1</b>,
 * {@code dialog_audio_comment} 13.48:1 to <b>1.02:1</b>.
 *
 * <p>The check is structural rather than a file list: for every leanback-reachable layout
 * it walks the tag tree, and for each palette-following foreground it resolves the nearest
 * ancestor (or self) that paints a background. When that background has no
 * {@code ?attr/} fill at all - i.e. it is the same colour in both tables - and its fills
 * are dark, the foreground must still clear the body-text floor.
 */
public class TvFixedDarkSurfaceContrastTest {

    /** Foregrounds that follow the palette, with the value the day table resolves them to. */
    private static final Map<String, String> PALETTE_FOREGROUNDS = new LinkedHashMap<>();

    static {
        PALETTE_FOREGROUNDS.put("?attr/colorOnSurface", "#1A1C1E");
        PALETTE_FOREGROUNDS.put("?attr/colorOnSurfaceVariant", "#44474F");
    }

    /** Minimum contrast for the body text these layouts carry. */
    private static final double MIN_CONTRAST = 4.5;

    /**
     * Fills above this luminance can no longer be called "designed for light text".
     *
     * <p>The panels below composite to {@code #181A21} / {@code #1B1F27} / {@code #090A0D};
     * the threshold only needs to separate those from a day-mode surface role.
     */
    private static final double DARK_SURFACE_LUMINANCE = 0.35;

    /**
     * Layouts owned by the Lab module, which pins {@code colorOnSurface} to
     * {@code #FFFFFFFF} in its own {@code Theme.App.Lab} / {@code Theme.App.Lab.Dialog}
     * styles. Its {@code ?attr/colorOnSurface} therefore does <em>not</em> follow the
     * app palette, and the module is already hard-coded dark by design
     * ({@code android:colorBackground = #111318}); routing it through the app tokens is
     * a separate concern from this defect class.
     */
    private static final String[] LAB_LAYOUTS = {
            "adapter_lab_command_compact.xml", "adapter_lab_package.xml",
            "activity_lab_output.xml", "dialog_lab_command_sheet.xml",
    };

    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z0-9_.]+)((?:\"[^\"]*\"|[^>\"])*?)(/?)>", Pattern.DOTALL);
    private static final Pattern BACKGROUND = Pattern.compile("(?:android:background|app:cardBackgroundColor|android:cardBackgroundColor)=\"([^\"]+)\"");
    private static final Pattern TEXT_COLOR = Pattern.compile("android:textColor=\"([^\"]+)\"");
    private static final Pattern SOLID = Pattern.compile("<solid\\s+android:color=\"([^\"]+)\"");
    private static final Pattern GRADIENT = Pattern.compile("android:(?:startColor|endColor|centerColor)=\"([^\"]+)\"");
    private static final Pattern COLOR_ENTRY = Pattern.compile("<color name=\"([^\"]+)\">([^<]+)</color>");
    private static final Pattern SELECTOR_COLOR = Pattern.compile("android:color=\"([^\"]+)\"");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern STROKE = Pattern.compile("<stroke\\b[^>]*/>|<stroke\\b.*?</stroke>", Pattern.DOTALL);
    private static final Pattern PADDING = Pattern.compile("<padding\\b[^>]*/>|<padding\\b.*?</padding>", Pattern.DOTALL);
    private static final Pattern MASK_ITEM = Pattern.compile("<item\\b[^>]*@android:id/mask[^>]*>.*?</item>", Pattern.DOTALL);
    private static final Pattern RIPPLE_COLOR = Pattern.compile("<ripple\\b[^>]*android:color=\"[^\"]*\"[^>]*>");

    private static final Map<String, String> LITERALS = new HashMap<>();

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

    private static void loadLiterals() {
        if (!LITERALS.isEmpty()) return;
        for (String file : new String[]{
                "src/main/res/values/webhtv_tokens.xml",
                "src/main/res/values/colors.xml",
                "src/leanback/res/values/colors.xml"}) {
            String source = readIfExists(file);
            if (source == null) continue;
            Matcher matcher = COLOR_ENTRY.matcher(withoutComments(source));
            while (matcher.find()) LITERALS.put(matcher.group(1), matcher.group(2).trim());
        }
    }

    private static String withoutComments(String source) {
        return COMMENT.matcher(source).replaceAll("");
    }

    /** Literal {@code #RRGGBB}/{@code #AARRGGBB} a {@code @color/<name>} resolves to, or null when it follows the palette. */
    private static String literalOf(String name, Map<String, String> seen) {
        if (seen.containsKey(name)) return null;
        seen.put(name, name);
        String value = LITERALS.get(name);
        if (value != null) {
            if (value.startsWith("#")) return value;
            if (value.startsWith("@color/")) return literalOf(value.substring("@color/".length()), seen);
        }
        for (String directory : new String[]{"src/main/res/color", "src/leanback/res/color"}) {
            String source = readIfExists(directory + "/" + name + ".xml");
            if (source == null) continue;
            if (withoutComments(source).contains("?attr/")) return null;
            Matcher matcher = SELECTOR_COLOR.matcher(withoutComments(source));
            while (matcher.find()) {
                String entry = matcher.group(1);
                if (entry.startsWith("#")) return entry;
                if (entry.startsWith("@color/")) {
                    String resolved = literalOf(entry.substring("@color/".length()), seen);
                    if (resolved != null) return resolved;
                }
            }
        }
        return null;
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
     * <p>The panels under test are translucent by design (the "glass sheet" look), so their
     * effective colour is darker than the raw value; the darkest shipped surface
     * ({@code #101418}) is used as the backdrop, which is the least favourable case for a
     * light foreground and the most favourable for the dark one.
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
     * The day-mode fills of a background, or {@code null} when it follows the palette.
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
                    String literal = literalOf(value.substring("@color/".length()), new HashMap<>());
                    if (literal == null) palette = true;
                    else fills.add(literal);
                } else if (!value.contains("transparent")) palette = true;
            }
            Matcher gradient = GRADIENT.matcher(body);
            while (gradient.find()) {
                String value = gradient.group(1);
                if (value.startsWith("#")) fills.add(value);
                else if (value.startsWith("@color/")) {
                    String literal = literalOf(value.substring("@color/".length()), new HashMap<>());
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

    // ------------------------------------------------------------------ the guard

    /**
     * A palette-following foreground must not sit on a palette-independent dark surface.
     *
     * <p>Reported per file so a failure names the layout to fix, together with the resolved
     * surface and the measured contrast.
     */
    @Test
    public void paletteForegroundsStayReadableOnFixedDarkSurfaces() throws Exception {
        loadLiterals();
        List<Path> layouts = new ArrayList<>();
        for (String directory : new String[]{"src/leanback/res/layout", "src/main/res/layout"}) {
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
            if (isLabLayout(name)) continue;
            String source = Files.readString(layout, StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (String[] hit : inspect(source)) {
                String foreground = hit[0];
                String surface = hit[1];
                double worst = Double.parseDouble(hit[2]);
                if (worst < MIN_CONTRAST) {
                    failures.add(name + ": " + foreground + " sits on the fixed dark surface " + surface
                            + " with " + String.format(Locale.ROOT, "%.2f:1", worst) + " in day mode");
                }
            }
        }
        if (!failures.isEmpty()) {
            fail("TV paints a palette-following foreground on a palette-independent dark surface,"
                    + " which is near-black on dark in day mode:\n  " + String.join("\n  ", failures));
        }
    }

    /**
     * Proves the guard above is not vacuous.
     *
     * <p>Every real instance of this defect class has been fixed, so the tree walk now
     * legitimately matches nothing. The probe feeds {@link #inspect} the exact markup that
     * was measured on device before the fix and requires it to be flagged with a
     * sub-threshold ratio, so a future regression cannot hide behind an empty match set.
     */
    @Test
    public void theGuardDetectsTheDefectItWasWrittenFor() {
        loadLiterals();
        String defective = "<LinearLayout android:background=\"@drawable/shape_audio_playlist_panel\">"
                + "<TextView android:textColor=\"?attr/colorOnSurface\" /></LinearLayout>";
        List<String[]> hits = inspect(defective);
        assertFalse("the guard must flag the measured pre-fix markup", hits.isEmpty());
        assertEquals("@drawable/shape_audio_playlist_panel", hits.get(0)[1]);
        assertTrue("the probe must reproduce the unreadable ratio", Double.parseDouble(hits.get(0)[2]) < MIN_CONTRAST);

        // The mirror of the same markup: a palette-independent light foreground must be ignored here.
        String alreadyCorrect = "<LinearLayout android:background=\"@drawable/shape_audio_playlist_panel\">"
                + "<TextView android:textColor=\"?attr/webhtvColorOnWallpaper\" /></LinearLayout>";
        assertTrue("a palette-independent foreground is not this guard's concern",
                inspect(alreadyCorrect).isEmpty());
    }

    private static boolean isLabLayout(String name) {
        for (String lab : LAB_LAYOUTS) if (lab.equals(name)) return true;
        return false;
    }

    /** {@code {foreground, surface, worstDayContrast}} for every matched element. */
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

            Matcher colorMatcher = TEXT_COLOR.matcher(attributes);
            if (!colorMatcher.find()) continue;
            String foreground = PALETTE_FOREGROUNDS.get(colorMatcher.group(1));
            if (foreground == null) continue;

            String nearest = own != null && !own.isEmpty() ? own : null;
            if (nearest == null) {
                for (String ancestor : backgrounds) {
                    if (ancestor != null && !ancestor.isEmpty()) {
                        nearest = ancestor;
                        break;
                    }
                }
            }
            if (nearest == null || !nearest.startsWith("@drawable/")) continue;
            List<String> fills = fixedFills(nearest.substring("@drawable/".length()));
            if (fills == null) continue;
            boolean dark = fills.stream().allMatch(fill -> luminance(fill) <= DARK_SURFACE_LUMINANCE);
            if (!dark) continue;
            double worst = fills.stream().mapToDouble(fill -> contrast(foreground, fill)).min().orElse(Double.MAX_VALUE);
            hits.add(new String[]{colorMatcher.group(1), nearest, String.valueOf(worst)});
        }
        return hits;
    }

    /**
     * Pins the four surfaces this defect was fixed on, so a later "migrate the TV
     * foregrounds" sweep cannot put a palette-following role back on them.
     */
    @Test
    public void fixedDarkPanelsKeepPaletteIndependentForegrounds() throws Exception {
        String[][] expectations = {
                {"src/main/res/layout/dialog_display.xml", "shape_display_dialog_panel"},
                {"src/main/res/layout/dialog_disc_menu.xml", "shape_disc_menu_panel"},
                {"src/main/res/layout/dialog_audio_comment.xml", "shape_audio_playlist_panel"},
                {"src/leanback/res/layout/dialog_exit_confirm.xml", "selector_exit_confirm_primary"},
        };
        for (String[] expectation : expectations) {
            String source = readIfExists(expectation[0]);
            assertTrue(expectation[0] + " is missing", source != null);
            assertTrue(expectation[0] + " must still paint its panel with " + expectation[1],
                    source.contains(expectation[1]));
            for (String[] hit : inspect(source)) {
                if (!expectation[1].equals(hit[1].substring("@drawable/".length()))) continue;
                fail(expectation[0] + " must not paint " + hit[0] + " on " + hit[1]
                        + " (measured " + String.format(Locale.ROOT, "%.2f:1", Double.parseDouble(hit[2])) + ")");
            }
        }
    }

    /**
     * The four panels must really be palette-independent and dark, otherwise the fix above
     * would be protecting the wrong thing.
     */
    @Test
    public void theFixedPanelsAreStillPaletteIndependentAndDark() throws Exception {
        loadLiterals();
        for (String drawable : new String[]{
                "shape_display_dialog_panel", "shape_disc_menu_panel", "shape_audio_playlist_panel",
                "selector_exit_confirm_primary"}) {
            List<String> fills = fixedFills(drawable);
            assertTrue(drawable + " must have no palette-following fill, or the guard is moot", fills != null);
            assertTrue(drawable + " must be dark in both tables",
                    fills.stream().allMatch(fill -> luminance(fill) <= DARK_SURFACE_LUMINANCE));
        }
    }
}
