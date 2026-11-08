package com.fongmi.android.tv.theme;

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
 * The leanback counterpart of {@link ThemeBaseWiringTest}'s dark-glass-sheet guard.
 *
 * <p>TV resolves the same day/night token tables as mobile now that the flavour no longer
 * ships a permanent dark {@code values/webhtv_tokens.xml}. That makes a
 * <em>palette-independent light</em> foreground ({@code ?attr/webhtvColorOnWallpaper},
 * {@code @color/webhtv_color_player_control_muted}) correct only where the surface behind
 * it is dark in <em>both</em> tables. On the app wallpaper, on the translucent glass
 * sheets, on the {@code shape_vod_*} badges and on the video surface that holds; on a
 * {@code ?attr/colorSurface*} panel or on one of the light card fills
 * ({@code selector_git_cloud_card} {@code #F8F9FA}, {@code selector_light_dialog_item}
 * {@code #FFFFFF}…) it turns into white-on-white in day mode.
 *
 * <p>That is exactly the mistake the first TV-light pass made: four leanback layouts were
 * migrated wholesale to the wallpaper role although their own rows sit on a light card.
 * Measured on {@code adapter_player_osd.xml} (over {@code #F8F9FA}) the pair was
 * <b>1.05:1</b>, and on {@code adapter_tmdb_item.xml} (over the focused
 * {@code #D3E3FD}) <b>1.16:1</b> — both below the 3:1 floor for non-text UI and far below
 * the 4.5:1 body-text floor.
 *
 * <p>The check is structural rather than a per-file list: for every leanback layout it
 * walks the tag tree, and for each constant-light foreground it resolves the nearest
 * ancestor (or self) that paints a background, then decides whether that background is
 * light in the day palette. Palette-following backgrounds are rejected outright, because
 * the day table is the light one.
 */
public class LeanbackForegroundContrastTest {

    /** Foregrounds that are light in every palette, with the value they resolve to. */
    private static final Map<String, String> CONSTANT_LIGHT_FOREGROUNDS = new LinkedHashMap<>();

    static {
        CONSTANT_LIGHT_FOREGROUNDS.put("?attr/webhtvColorOnWallpaper", "#FFFFFF");
        CONSTANT_LIGHT_FOREGROUNDS.put("@color/webhtv_color_player_control_muted", "#CCFFFFFF");
        // 壁纸上的次级前景。与上面两个同族：两张表里都是浅色，因此只允许落在深色宿主上
        // （卡片信息条带、设置行色带），落在浅色日间表面会白到看不见。
        CONSTANT_LIGHT_FOREGROUNDS.put("?attr/webhtvColorOnWallpaperVariant", "#CCFFFFFF");
    }

    /** Luminance above which a day-mode fill can no longer carry a light foreground. */
    private static final double LIGHT_SURFACE_LUMINANCE = 0.6;

    /** Minimum contrast a light foreground needs against the fill behind it. */
    private static final double MIN_CONTRAST = 3.0;

    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z0-9_.]+)((?:\"[^\"]*\"|[^>\"])*?)(/?)>", Pattern.DOTALL);
    private static final Pattern BACKGROUND = Pattern.compile("android:background=\"([^\"]+)\"");
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

    /** Day-palette {@code webhtv_*} values; the night table is not needed to decide "light". */
    private static final Map<String, String> DAY_TOKENS = new HashMap<>();

    /** Alias table for the literal colours, so {@code @color/x -> @color/webhtv_...} resolves. */
    private static final Map<String, String> LITERALS = new HashMap<>();

    private static final List<Path> LAYOUTS = new ArrayList<>();

    // ------------------------------------------------------------------ palette loading

    private static String readIfExists(String relative) {
        try {
            Path path = root().resolve(relative);
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n") : null;
        } catch (Exception error) {
            return null;
        }
    }

    private static Path root() {
        return Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
    }

    private static void loadPalette() {
        if (!DAY_TOKENS.isEmpty()) return;
        for (String file : new String[]{
                "src/main/res/values/webhtv_tokens.xml",
                "src/main/res/values/colors.xml",
                "src/leanback/res/values/colors.xml"}) {
            String source = readIfExists(file);
            if (source == null) continue;
            Matcher matcher = COLOR_ENTRY.matcher(withoutComments(source));
            while (matcher.find()) {
                String name = matcher.group(1);
                String value = matcher.group(2).trim();
                if (file.contains("webhtv_tokens")) DAY_TOKENS.put(name, value);
                LITERALS.put(name, value);
            }
        }
    }

    private static void loadLayouts() throws Exception {
        if (!LAYOUTS.isEmpty()) return;
        Path directory = root().resolve("src/leanback/res/layout");
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .forEach(LAYOUTS::add);
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
        // res/color/<name>.xml selectors may be the only definition.
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

    /** Relative luminance of {@code #RRGGBB}/{@code #AARRGGBB}; alpha is dropped. */
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

    // ------------------------------------------------------------------ background resolution

    /** Outcome of resolving one background: how it behaves in the day palette. */
    private enum Surface {DARK, LIGHT, PALETTE, UNKNOWN}

    private static Surface surfaceOf(String background) {
        if (background == null) return Surface.UNKNOWN;
        if (background.startsWith("?attr/colorSurface") || background.startsWith("?attr/colorBackground")
                || background.startsWith("?attr/android:colorBackground")) {
            return Surface.PALETTE;                       // the day table is the light one
        }
        if (background.startsWith("?attr/")) return Surface.UNKNOWN;
        if (background.startsWith("@color/")) return surfaceOfColor(background.substring("@color/".length()));
        if (background.startsWith("@drawable/")) return surfaceOfDrawable(background.substring("@drawable/".length()));
        return Surface.UNKNOWN;
    }

    private static Surface surfaceOfColor(String name) {
        String literal = literalOf(name, new HashMap<>());
        if (literal == null) return Surface.PALETTE;      // palette-following selector
        return luminance(literal) > LIGHT_SURFACE_LUMINANCE ? Surface.LIGHT : Surface.DARK;
    }

    /**
     * The day-mode fills of a drawable.
     *
     * <p>Strokes, padding and ripple mask items are dropped first: only a fill can sit
     * behind text. A {@code ?attr/} fill or an unresolvable {@code @color/} makes the whole
     * drawable palette-following.
     */
    private static Surface surfaceOfDrawable(String name) {
        boolean palette = false;
        List<String> fills = new ArrayList<>();
        for (String directory : new String[]{"src/main/res/drawable", "src/leanback/res/drawable"}) {
            String source = readIfExists(directory + "/" + name + ".xml");
            if (source == null) continue;
            String body = RIPPLE_COLOR.matcher(MASK_ITEM.matcher(PADDING.matcher(STROKE.matcher(withoutComments(source))
                    .replaceAll("")).replaceAll("")).replaceAll("")).replaceAll("<ripple>");
            palette |= body.contains("?attr/");
            Matcher solid = SOLID.matcher(body);
            while (solid.find()) {
                String value = solid.group(1);
                if (value.startsWith("#")) fills.add(value);
                else if (value.startsWith("@color/")) {
                    String literal = literalOf(value.substring("@color/".length()), new HashMap<>());
                    if (literal == null) palette = true;
                    else fills.add(literal);
                } else if (!value.contains("transparent")) {
                    palette = true;
                }
            }
            Matcher gradient = GRADIENT.matcher(body);
            while (gradient.find()) {
                String value = gradient.group(1);
                if (value.startsWith("#")) fills.add(value);
                else if (value.startsWith("@color/")) {
                    String literal = literalOf(value.substring("@color/".length()), new HashMap<>());
                    if (literal == null) palette = true;
                    else fills.add(literal);
                } else {
                    palette = true;
                }
            }
        }
        if (palette) return Surface.PALETTE;
        List<String> visible = fills.stream().filter(fill -> alpha(fill) >= 0x66).toList();
        if (visible.isEmpty()) return Surface.UNKNOWN;    // fully transparent: keep walking out
        boolean light = visible.stream().allMatch(fill -> luminance(fill) > LIGHT_SURFACE_LUMINANCE);
        return light ? Surface.LIGHT : Surface.DARK;
    }

    // ------------------------------------------------------------------ the guard

    /**
     * A palette-independent light foreground must not sit on a light day-mode surface.
     *
     * <p>Reported per file so a failure names the layout to fix; the message carries the
     * offending tag, the background that was resolved and the contrast measured for the
     * worst day-mode fill.
     */
    @Test
    public void constantLightForegroundsStayOnDarkSurfacesInDayMode() throws Exception {
        loadPalette();
        loadLayouts();
        List<String> failures = new ArrayList<>();
        for (Path layout : LAYOUTS) {
            String source = Files.readString(layout, StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (String failure : inspect(source)) {
                failures.add(layout.getFileName() + ": " + failure);
            }
        }
        if (!failures.isEmpty()) {
            fail("leanback layouts paint a light foreground on a light day-mode surface, which is"
                    + " white-on-white in day mode:\n  " + String.join("\n  ", failures));
        }
    }

    private static List<String> inspect(String source) {
        List<String> failures = new ArrayList<>();
        Deque<String> backgrounds = new ArrayDeque<>();
        Matcher matcher = TAG.matcher(source);
        while (matcher.find()) {
            boolean closing = !matcher.group(1).isEmpty();
            String tag = matcher.group(2);
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
            String foreground = CONSTANT_LIGHT_FOREGROUNDS.get(colorMatcher.group(1));
            if (foreground == null) continue;

            String nearest = own;
            for (String ancestor : backgrounds) {
                if (nearest == null || nearest.isEmpty()) nearest = ancestor;
                if (nearest != null && !nearest.isEmpty()) break;
            }
            Surface surface = surfaceOf(nearest);
            if (surface == Surface.DARK || surface == Surface.UNKNOWN) continue;
            if (surface == Surface.PALETTE) {
                failures.add(tag + " textColor=" + colorMatcher.group(1)
                        + " sits on the palette-following background " + nearest
                        + ", whose day value is a light surface");
                continue;
            }
            double worst = worstDayContrast(nearest, foreground);
            if (worst < MIN_CONTRAST) {
                failures.add(tag + " textColor=" + colorMatcher.group(1)
                        + " sits on " + nearest + " with " + String.format(Locale.ROOT, "%.2f:1", worst)
                        + " contrast in day mode");
            }
        }
        return failures;
    }

    /** Worst day-mode contrast of a light foreground over the fills of a background. */
    private static double worstDayContrast(String background, String foreground) {
        if (background == null) return Double.MAX_VALUE;
        List<String> fills = new ArrayList<>();
        if (background.startsWith("@color/")) {
            String literal = literalOf(background.substring("@color/".length()), new HashMap<>());
            if (literal != null) fills.add(literal);
        } else if (background.startsWith("@drawable/")) {
            String name = background.substring("@drawable/".length());
            for (String directory : new String[]{"src/main/res/drawable", "src/leanback/res/drawable"}) {
                String source = readIfExists(directory + "/" + name + ".xml");
                if (source == null) continue;
                String body = RIPPLE_COLOR.matcher(MASK_ITEM.matcher(PADDING.matcher(STROKE.matcher(withoutComments(source))
                        .replaceAll("")).replaceAll("")).replaceAll("")).replaceAll("<ripple>");
                Matcher solid = SOLID.matcher(body);
                while (solid.find()) {
                    String value = solid.group(1);
                    if (value.startsWith("#")) fills.add(value);
                    else if (value.startsWith("@color/")) {
                        String literal = literalOf(value.substring("@color/".length()), new HashMap<>());
                        if (literal != null) fills.add(literal);
                    }
                }
                Matcher gradient = GRADIENT.matcher(body);
                while (gradient.find()) {
                    String value = gradient.group(1);
                    if (value.startsWith("#")) fills.add(value);
                    else if (value.startsWith("@color/")) {
                        String literal = literalOf(value.substring("@color/".length()), new HashMap<>());
                        if (literal != null) fills.add(literal);
                    }
                }
            }
        }
        return fills.stream().filter(fill -> alpha(fill) >= 0x66)
                .mapToDouble(fill -> contrast(foreground, fill))
                .min().orElse(Double.MAX_VALUE);
    }

    /**
     * The four layouts that host their rows on a light card must keep the palette-following
     * role, so a future "migrate the remaining TV foregrounds" sweep cannot put the light
     * constant back on them.
     *
     * <p>Note this pins the <em>day</em> behaviour only. Those cards are fixed-light in both
     * palettes, so their palette-following foreground is unreadable in night mode
     * ({@code #E2E2E9} over {@code #F8F9FA} = 1.22:1). That is a pre-existing defect shared
     * with mobile and present on {@code beta}, not something the TV-light pass introduced;
     * it is deliberately not asserted here so it stays visible instead of being enshrined.
     */
    @Test
    public void lightCardRowsKeepThePaletteFollowingRole() throws Exception {
        String[][] expectations = {
                {"adapter_device.xml", "?attr/colorOnSurface", "?attr/colorOnSurfaceVariant"},
                {"adapter_player_osd.xml", "?attr/colorOnSurface"},
                {"adapter_recommendation_feedback.xml", "?attr/colorOnSurface", "?attr/colorOnSurfaceVariant"},
                {"adapter_tmdb_item.xml", "?attr/colorOnSurface", "?attr/colorOnSurfaceVariant"},
        };
        for (String[] expectation : expectations) {
            String source = readIfExists("src/leanback/res/layout/" + expectation[0]);
            assertTrue(expectation[0] + " is missing", source != null);
            for (int i = 1; i < expectation.length; i++) {
                assertTrue(expectation[0] + " must keep " + expectation[i]
                                + " for its rows over a light card",
                        source.contains("android:textColor=\"" + expectation[i] + "\""));
            }
            for (String constant : CONSTANT_LIGHT_FOREGROUNDS.keySet()) {
                assertTrue(expectation[0] + " must not paint its light-card rows with " + constant,
                        !source.contains("android:textColor=\"" + constant + "\""));
            }
        }
    }

    /**
     * The guard is only meaningful while those cards really are light in day mode.
     *
     * <p>Pins the two fills the migration got wrong, so moving the card onto a palette role
     * (which would make the constant foreground correct again) is a deliberate, visible edit
     * rather than something the first test silently stops covering.
     *
     * <p>It also pins the absence of a {@code drawable-night} variant. That is the fact which
     * forces these rows onto the palette-following role: a fixed-light card cannot carry a
     * fixed-light foreground in night mode. When a night variant is added, this assertion is
     * the place that announces the coupling has changed.
     */
    @Test
    public void theLightCardsAreStillLightInDayMode() throws Exception {
        loadPalette();
        for (String drawable : new String[]{
                "selector_git_cloud_card", "selector_light_dialog_item", "selector_tmdb_search_item"}) {
            assertTrue(drawable + " must keep its light day fill",
                    lightestFill(drawable) > LIGHT_SURFACE_LUMINANCE);
            assertTrue(drawable + " must have no night variant, which is why its rows follow the palette",
                    readIfExists("src/main/res/drawable-night/" + drawable + ".xml") == null
                            && readIfExists("src/leanback/res/drawable-night/" + drawable + ".xml") == null);
        }
    }

    private static double lightestFill(String drawable) {
        double darkest = Double.MAX_VALUE;
        for (String directory : new String[]{"src/main/res/drawable", "src/leanback/res/drawable"}) {
            String source = readIfExists(directory + "/" + drawable + ".xml");
            if (source == null) continue;
            String body = RIPPLE_COLOR.matcher(MASK_ITEM.matcher(PADDING.matcher(STROKE.matcher(withoutComments(source))
                    .replaceAll("")).replaceAll("")).replaceAll("")).replaceAll("<ripple>");
            Matcher matcher = SOLID.matcher(body);
            while (matcher.find()) {
                String value = matcher.group(1);
                String literal = value.startsWith("#") ? value
                        : value.startsWith("@color/") ? literalOf(value.substring("@color/".length()), new HashMap<>()) : null;
                if (literal != null && alpha(literal) >= 0x66) darkest = Math.min(darkest, luminance(literal));
            }
        }
        assertTrue(drawable + " has no visible fill", darkest != Double.MAX_VALUE);
        return darkest;
    }
}
