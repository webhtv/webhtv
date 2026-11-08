package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeBinderContractTest {

    @Test
    public void explicitRoleTagsAreParsedCaseInsensitivelyAndBounded() {
        assertEquals(ThemeRole.PRIMARY, ThemeRole.fromTag("webhtv:primary"));
        assertEquals(ThemeRole.PRIMARY, ThemeRole.fromTag(" webhtv:PRIMARY "));
        assertEquals(ThemeRole.SURFACE_CONTAINER_HIGH, ThemeRole.fromTag("webhtv:surface_container_high"));
        assertNull(ThemeRole.fromTag("webhtv:"));
        assertNull(ThemeRole.fromTag("webhtv:playerControl"));
        assertNull(ThemeRole.fromTag("something-else"));
        assertNull(ThemeRole.fromTag(null));
        assertNull(ThemeRole.fromTag(42));
    }

    @Test
    public void rolesExposeOnlyUserSlotsAndTheirDerivedForegrounds() {
        int userSlots = 0;
        for (ThemeRole role : ThemeRole.values()) if (role.isUserSlot()) userSlots++;
        assertEquals(13, userSlots);
        for (ThemeRole role : ThemeRole.values()) {
            assertTrue(role.name(), role.name().matches("[A-Z_]+"));
        }
        // Player, media, health and brand roles must not be representable.
        assertNull(ThemeRole.fromTag("webhtv:player_control"));
        assertNull(ThemeRole.fromTag("webhtv:health_good"));
    }

    @Test
    public void indexResolvesUniqueBaselineColorsOnly() {
        ThemeTokens baseline = ThemeTokens.light();
        ThemeColorIndex index = ThemeColorIndex.of(baseline);
        assertFalse(index.isEmpty());
        assertEquals(ThemeRole.ERROR, index.uniqueRoleFor(baseline.colorError()));
        assertEquals(ThemeRole.SURFACE, index.uniqueRoleFor(baseline.colorSurface()));
        // The light palette deliberately shares one blue between primary and focus,
        // so that value is ambiguous and the binder requires both to agree.
        assertTrue(index.rolesFor(baseline.colorFocus()).contains(ThemeRole.PRIMARY));
        assertTrue(index.rolesFor(baseline.colorFocus()).contains(ThemeRole.FOCUS));
        assertNull(index.uniqueRoleFor(0xFF123456));
        assertNull(ThemeColorIndex.of(null).uniqueRoleFor(0xFF123456));
        assertTrue(ThemeColorIndex.of(null).isEmpty());
    }

    @Test
    public void sharedBaselineColorsAreReportedAsAmbiguous() {
        ThemeColorIndex index = ThemeColorIndex.of(ThemeTokens.light());
        Set<ThemeRole> roles = index.rolesFor(0xFFFFFFFF);
        assertTrue("white must be shared by several foreground roles", roles.size() > 1);
        assertNull(index.uniqueRoleFor(0xFFFFFFFF));
    }

    @Test
    public void replacementIsReturnedOnlyWhenEverySharingRoleAgrees() {
        ThemeTokens baseline = ThemeTokens.light();
        ThemeColorIndex index = ThemeColorIndex.of(baseline);

        ThemeProfile outlineProfile = ThemeProfile.defaultProfile();
        outlineProfile.light.outline = "#000000";
        ThemeTokens outlineActive = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, outlineProfile, null, false);
        assertEquals(Integer.valueOf(0xFF000000), index.replacementFor(baseline.colorOutline(), outlineActive));

        // White is shared by onPrimary/onSecondary/onTertiary; overriding only the
        // primary makes those roles disagree, so the binder must keep the static color.
        ThemeProfile primaryProfile = ThemeProfile.defaultProfile();
        primaryProfile.light.primary = "#FFFFFF";
        ThemeTokens primaryActive = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, primaryProfile, null, false);
        assertNull(index.replacementFor(baseline.colorOnPrimary(), primaryActive));
    }

    @Test
    public void unknownAndUnchangedColorsAreNeverRewritten() {
        ThemeTokens baseline = ThemeTokens.light();
        ThemeColorIndex index = ThemeColorIndex.of(baseline);
        assertNull(index.replacementFor(0xFF123456, ThemeTokens.dark()));
        assertNull(index.replacementFor(baseline.colorPrimary(), baseline));
        assertNull(index.replacementFor(baseline.colorPrimary(), null));
    }

    @Test
    public void binderIsControlledMainThreadOnlyAndPlayerSafe() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java");
        assertTrue(source.contains("Looper.myLooper() != Looper.getMainLooper()"));
        assertTrue(source.contains("baseline.equals(active)"));
        assertTrue(source.contains("webhtv:ignore"));
        assertTrue(source.contains("addOnChildAttachStateChangeListener"));
        assertTrue(source.contains("isStateful()"));
        assertFalse("no Resources/AssetManager reflection", source.contains("AssetManager"));
        assertFalse("no reflection bypass", source.contains("setAccessible"));
        assertFalse("no style application bypass", source.contains("applyStyle"));
        // Class#getRecordComponents does not exist on Android API < 33 and crashed
        // the app on API 28; the signature must stay reflection-free.
        assertFalse("no Java-16 record reflection on the runtime path",
                source.contains("getRecordComponents()") || source.contains("getDeclaredConstructor("));
        assertTrue(source.contains("tokens.hashCode()"));
        assertTrue(source.contains("WeakHashMap"));
    }

    @Test
    public void everyActivityBindsTheThemeTreeTwice() throws Exception {
        for (String flavour : new String[]{"mobile", "leanback"}) {
            String source = read("src/" + flavour + "/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
            int content = source.indexOf("View content = getBinding().getRoot();");
            int setContent = source.indexOf("setContentView(content);", content);
            int first = source.indexOf("ThemeController.bindTheme(content);", setContent);
            int second = source.indexOf("ThemeController.bindTheme(content);", first + 1);
            assertTrue(flavour + " must resolve one inflated binding as the content view", content > 0);
            assertTrue(flavour + " must bind after setContentView", setContent > content && first > setContent);
            assertTrue(flavour + " must re-bind after initView", second > first);
            assertTrue(flavour + " must bind before initEvent",
                    source.indexOf("initEvent();", second) > second);
        }
    }

    @Test
    public void presetSeedsMustResolveToTheirOwnPaletteInsteadOfFallingBack() {
        for (int seed : new int[]{0xFF0B57D0, 0xFF00897B, 0xFF146C2E, 0xFFFB8C00, 0xFFB3261E, 0xFF8E24AA}) {
            String hex = "#" + Integer.toHexString(seed).toUpperCase(java.util.Locale.ROOT);
            ThemeTokens tokens = ThemeResolver.resolve(
                    ThemeMode.LIGHT, ThemeSeed.EXPLICIT, seed, 0, null, null, false);
            String diagnostic = ThemeResolver.lastDiagnostic();
            assertFalse("seed " + hex + " silently fell back: " + diagnostic,
                    diagnostic.startsWith("fallback"));
            assertNotEquals("seed " + hex + " resolved to the frozen palette",
                    ThemeTokens.light().colorPrimary(), tokens.colorPrimary());
        }
    }

    @Test
    public void backgroundChannelCoversMaterialShapeAndInsetWrappers() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java");
        assertTrue("must unwrap Material's inset window background", source.contains("instanceof InsetDrawable inset"));
        assertTrue("must reopen the wrapped drawable", source.contains("bindDrawable(inset.getDrawable()"));
        assertTrue("must cover Material shape panels", source.contains("instanceof MaterialShapeDrawable shape"));
        assertTrue(source.contains("shape.getFillColor()"));
        assertTrue(source.contains("shape.setFillColor(color)"));
        // Still no reflection or hidden API on the widening path.
        assertFalse(source.contains("setAccessible"));
        assertFalse(source.contains("getDeclaredField"));
    }

    /**
     * Every Material alert dialog must be constructed through the themed builder,
     * whose overridden {@code create()} binds the dialog window to the active tokens.
     * Raw {@code MaterialAlertDialogBuilder} instances were never passed to
     * {@code bindDialog}, which is why a custom theme used to leave nearly every
     * dialog on the compiled palette.
     *
    /**
     * A {@code themeResId} handed to a dialog builder must resolve {@code colorPrimary}
     * to something concrete. {@code MaterialAlertDialogBuilder} validates the dialog
     * context through {@code ThemeEnforcement.checkAppCompatTheme}, which only checks
     * that {@code ?attr/colorPrimary} exists - so an overlay whose *nearest* definition
     * of the role is the self-reference {@code ?attr/colorPrimary} throws inside the
     * constructor. That is exactly how "cancel following" crashed with
     * {@code ThemeOverlay.WebHTV.FollowingConfirmDialog}.
     *
     * <p>Inheritance is honoured: {@code ThemeOverlay.WebHTV.Dialog.NoInset} defines no
     * primary of its own and safely inherits the concrete token from
     * {@code ThemeOverlay.WebHTV.Dialog}. Only the nearest definition decides, because a
     * child that re-declares the role as {@code ?attr/colorPrimary} shadows its ancestor
     * and resolves to nothing.
     */
    @Test
    public void dialogThemeArgumentsResolveColorPrimaryConcretely() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        java.util.Map<String, String> body = new java.util.HashMap<>();
        java.util.Map<String, String> parent = new java.util.HashMap<>();
        java.util.regex.Pattern stylePattern = java.util.regex.Pattern
                .compile("<style name=\"([^\"]+)\"(?:[^>]*parent=\"([^\"]*)\")?[^>]*>(.*?)</style>",
                        java.util.regex.Pattern.DOTALL);
        for (Path res : Files.walk(root.resolve("src")).filter(p -> p.toString().endsWith(".xml")).toList()) {
            java.util.regex.Matcher m = stylePattern.matcher(Files.readString(res, StandardCharsets.UTF_8));
            while (m.find()) {
                body.put(m.group(1), m.group(3));
                if (m.group(2) != null) parent.put(m.group(1), m.group(2));
            }
        }
        java.util.List<String> offenders = new java.util.ArrayList<>();
        java.util.regex.Pattern usage = java.util.regex.Pattern
                .compile("(?:WebHtvAlertDialogBuilder|MaterialAlertDialogBuilder)\\([^;]*?R\\.style\\.(ThemeOverlay_[A-Za-z_]+)",
                        java.util.regex.Pattern.DOTALL);
        for (Path sourceFile : Files.walk(root.resolve("src")).filter(p -> p.toString().endsWith(".java")).toList()) {
            if (sourceFile.toString().contains("graphify-out")) continue;
            java.util.regex.Matcher m = usage.matcher(Files.readString(sourceFile, StandardCharsets.UTF_8));
            while (m.find()) {
                String name = m.group(1).replace('_', '.');
                String nearest = null;
                for (int hop = 0; hop < 24 && name != null; hop++) {
                    String block = body.get(name);
                    if (block != null) {
                        java.util.regex.Matcher item = java.util.regex.Pattern
                                .compile("<item name=\"colorPrimary\">([^<]+)</item>").matcher(block);
                        if (item.find()) { nearest = item.group(1).trim(); break; }
                    }
                    name = parent.get(name);
                }
                if (nearest == null || !nearest.startsWith("@color/")) {
                    offenders.add(sourceFile.getFileName() + " -> " + m.group(1) + " resolves colorPrimary to " + nearest);
                }
            }
        }
        assertTrue("these dialog themes cannot satisfy ThemeEnforcement: " + offenders, offenders.isEmpty());
    }

    @Test
    public void materialAlertDialogsAreBuiltThroughTheThemedBuilder() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        // CrashActivity is deliberately exempt: the recovery screen runs in its own
        // process with Theme.Crash and must render identically no matter what palette
        // the user saved, so it must not depend on the theme contract at all.
        Set<String> exempt = Set.of("CrashActivity.java");
        // Matches `new AlertDialog.Builder(`, `new android.app.AlertDialog.Builder(` and
        // `new androidx.appcompat.app.AlertDialog.Builder(` alike, but never the themed
        // `new WebHtvAlertDialogBuilder(`.
        java.util.regex.Pattern forbidden = java.util.regex.Pattern.compile(
                "new\\s+(?:[A-Za-z_][\\w.]*\\.)?AlertDialog\\.Builder\\(");
        // Same shape for the Material builder: the qualified `new com.google.android.material
        // .dialog.MaterialAlertDialogBuilder(` must be caught too. The previous version used a
        // literal `contains("new MaterialAlertDialogBuilder(")`, so every call site that spelled
        // the class out in full slipped through - five of them in the two VideoActivity files
        // were building dialogs that never reached ThemeController.bindDialog, which meant a
        // user-chosen theme silently did not apply to them.
        java.util.regex.Pattern forbiddenMaterial = java.util.regex.Pattern.compile(
                "new\\s+(?:[A-Za-z_][\\w.]*\\.)?MaterialAlertDialogBuilder\\(");
        java.util.List<String> raw = new java.util.ArrayList<>();
        for (String sourceSet : new String[]{"main", "mobile", "leanback"}) {
            Path base = root.resolve("src/" + sourceSet);
            if (!Files.exists(base)) continue;
            try (java.util.stream.Stream<Path> paths = Files.walk(base)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    if (path.toString().contains("graphify-out")) continue;
                    if (exempt.contains(path.getFileName().toString())) continue;
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    if (forbiddenMaterial.matcher(text).find()) {
                        raw.add(sourceSet + "/" + path.getFileName() + " -> MaterialAlertDialogBuilder");
                    }
                    if (forbidden.matcher(text).find()) {
                        raw.add(sourceSet + "/" + path.getFileName() + " -> AlertDialog.Builder");
                    }
                }
            }
        }
        assertTrue("build these through WebHtvAlertDialogBuilder instead: " + raw, raw.isEmpty());
    }

    /**
     * The editor exposes a dialog-opacity slider, and the design record documents it as
     * "the dialog/BottomSheet shell only, never the text alpha". It used to reach only the
     * preview swatch and the Web snapshot, so on a real dialog the lower bound and the upper
     * bound produced byte-identical screens - measured on device as 0 changed pixels. The
     * window background is the shell, so it must carry the opacity.
     */
    @Test
    public void dialogOpacityReachesTheWindowShellAndNotTheText() throws Exception {
        String binder = read("src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java");
        assertTrue("the window background must carry the shell opacity",
                binder.contains("applyShellOpacity(drawable, active.dialogOpacity())"));
        assertTrue("shell opacity must unwrap Material's inset wrapper",
                binder.contains("applyShellOpacity(inset.getDrawable(), opacity)"));
        // 1.0 is the shipped default: it must stay a strict no-op.
        assertTrue("a fully opaque shell must be left untouched", binder.contains("if (drawable == null || opacity >= 1f) return false;"));
        // Text alpha is governed by the view-tree pass, which this method never touches.
        assertFalse("shell opacity must not be applied through the view lambda",
                binder.contains("applyShellOpacity(view"));

        // Alpha arithmetic, including the documented 0.70..1.00 bounds.
        assertEquals("default opacity must be a no-op", 0xFF7B1FA2, ThemeBinder.scaleAlpha(0xFF7B1FA2, 1.0f));
        assertEquals("lower bound", 0xB37B1FA2, ThemeBinder.scaleAlpha(0xFF7B1FA2, 0.70f));
        // 0.70 * 255 = 178.5, and Math.round takes it up to 179.
        assertEquals("matches the clamped manual computation", 179, ThemeBinder.scaleAlpha(0xFF7B1FA2, 0.70f) >>> 24);
        assertEquals("existing transparency must be preserved, not replaced",
                0x407B1FA2, ThemeBinder.scaleAlpha(0x807B1FA2, 0.50f));
        assertEquals("out-of-range values must clamp, never wrap",
                0xFF7B1FA2, ThemeBinder.scaleAlpha(0xFF7B1FA2, 2.0f));
    }

    @Test
    public void dialogWindowBackgroundIsRecolouredOutsideTheViewTree() throws Exception {
        String binder = read("src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java");
        assertTrue(binder.contains("public static boolean bindWindowBackground(Drawable drawable, ThemeTokens baseline, ThemeTokens active)"));
        assertTrue("must reuse the shared drawable pass",
                binder.contains("bindDrawable(drawable, null, ThemeColorIndex.of(baseline), active)"));

        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("public static void bindWindowBackground(Drawable background)"));

        String builder = read("src/main/java/com/fongmi/android/tv/theme/WebHtvAlertDialogBuilder.java");
        // The panel is the builder's own background field, which is the exact instance
        // handed to Window.setBackgroundDrawable by MaterialAlertDialogBuilder.
        assertTrue(builder.contains("ThemeController.bindWindowBackground(getBackground())"));
        assertTrue(builder.contains("ThemeController.bindDialog(dialog)"));
    }

    @Test
    public void followingPageOptsIntoTheSharedAppearanceContract() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");
        int apply = source.indexOf("ThemeController.applyFromPreferences(this);");
        int setContent = source.indexOf("setContentView(binding.getRoot());");
        int first = source.indexOf("ThemeController.bindTheme(binding.getRoot());");
        int initView = source.indexOf("initView();");
        int second = source.indexOf("ThemeController.bindTheme(binding.getRoot());", first + 1);
        assertTrue("must resolve tokens before the first content view", apply > 0 && apply < setContent);
        assertTrue("must bind right after setContentView", first > setContent);
        assertTrue("must re-bind after initView", second > first && initView > first && second > initView);
    }

    /**
     * Every Activity that hosts native views must participate in the theme contract.
     * Activities outside BaseActivity are the exception and are pinned here so a new
     * one cannot silently ship with a statically coloured tree.
     */
    @Test
    public void nativeActivitiesOutsideBaseActivityAreExplicitlyAccountedFor() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        Set<String> exempt = Set.of(
                "LabActivity", "LabDetailActivity", "LabOutputActivity", "LabTerminalActivity",
                "CatWebActivity", "GameWebActivity", "WebReaderActivity");
        java.util.List<String> unbound = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root.resolve("src/main/java/com/fongmi/android/tv"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                if (!source.contains("extends AppCompatActivity")) continue;
                String name = path.getFileName().toString().replace(".java", "");
                if (exempt.contains(name)) continue;
                if (!source.contains("ThemeController.bindTheme(")) unbound.add(name);
            }
        }
        assertTrue("these activities host native views but never bind the theme: " + unbound, unbound.isEmpty());
    }

    @Test
    public void binderRewritesTheMaterialButtonAndBackgroundTintChannels() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java");
        assertTrue(source.contains("instanceof MaterialButton button"));
        assertTrue(source.contains("view.getBackgroundTintList()"));
        assertTrue(source.contains("view.setBackgroundTintList(tint)"));
        assertTrue(source.contains("view.setStrokeColor(stroke)"));
        assertTrue(source.contains("view.setIconTint(icon)"));
        // The visible contract behind this: native pages fill their primary
        // buttons through app:backgroundTint, not through a GradientDrawable.
        String following = read("src/main/res/layout/activity_following.xml");
        assertTrue(following.contains("app:backgroundTint=\"?attr/colorPrimary\""));
    }

    @Test
    public void alertDialogFamiliesBindThroughTheSharedDialogChannel() throws Exception {
        String light = read("src/main/java/com/fongmi/android/tv/ui/dialog/LightDialog.java");
        assertEquals("both LightDialog entry points must bind",
                2, light.split("ThemeController\\.bindDialog\\(dialog\\)", -1).length - 1);
        assertTrue("LightDialog must bind the window shell as well as the view tree",
                light.contains("ThemeController.bindWindowBackground(background)"));
        assertTrue("custom LightDialog shells must use the active semantic surface",
                light.contains("ThemeController.current().colorSurfaceContainerHigh()"));
        // dialogOpacity has exactly one owner: ThemeBinder.bindWindowBackground. Pre-multiplying
        // it into the shape scaled the value twice (0.70 -> 0.49) and made the fill stop matching
        // its baseline role, so the shell no longer followed a surfaceContainerHigh override.
        assertFalse("the shell must ship opaque so dialogOpacity is applied only once",
                light.contains("withAlpha(tokens.colorSurfaceContainerHigh(), tokens.dialogOpacity())"));
        assertFalse("the dialog title must not be a hard-coded hex colour",
                light.contains("Color.parseColor(\"#202124\")"));
        assertTrue(light.contains("titleView.setTextColor(ThemeController.current().colorOnSurface())"));

        String base = read("src/main/java/com/fongmi/android/tv/ui/dialog/BaseAlertDialog.java");
        assertTrue(base.contains("ThemeController.bindDialog(dialog)"));
        assertFalse("MaterialAlertDialogBuilder owns the dialog OnShowListener",
                base.contains("setOnShowListener"));

        // The channels the binder rewrites must really resolve through the
        // semantic tokens, otherwise the exact-match guard could never fire.
        for (String name : new String[]{"dialog_primary_button_bg", "dialog_primary_button_text",
                "dialog_outlined_button_bg", "dialog_outlined_button_text", "dialog_outlined_button_stroke"}) {
            String selector = read("src/main/res/color/" + name + ".xml");
            assertTrue(name + " must resolve through webhtv tokens",
                    selector.contains("@color/webhtv_color_"));
        }
    }

    @Test
    public void seedDerivedTokensStayMappableFromTheFrozenBaseline() {
        ThemeTokens baseline = ThemeTokens.light();
        ThemeTokens active = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.EXPLICIT, 0xFFB3261E, 0, null, null, false);
        assertNotEquals("an explicit seed must really move the palette",
                baseline.colorPrimary(), active.colorPrimary());
        // The seed derives focus from primary, so the shared-baseline ambiguity
        // resolves and the view colour can be rewritten.
        assertEquals(Integer.valueOf(active.colorPrimary()),
                ThemeColorIndex.of(baseline).replacementFor(baseline.colorPrimary(), active));
        assertEquals(Integer.valueOf(active.colorSurface()),
                ThemeColorIndex.of(baseline).replacementFor(baseline.colorSurface(), active));
    }

    @Test
    public void aSeedDerivedBaselineWouldSilentlyDisableTheBinder() {
        ThemeTokens staticPalette = ThemeTokens.light();
        ThemeTokens active = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.EXPLICIT, 0xFFB3261E, 0, null, null, false);
        ThemeTokens seedBaseline = ThemeResolver.resolve(
                ThemeMode.LIGHT, ThemeSeed.EXPLICIT, 0xFFB3261E, 0, null, null, false);
        // Regression guard for the shipped defect: when the baseline was resolved
        // through the legacy theme_color seed it matched no inflated view colour,
        // so the binder bound nothing outside the legacy site dialog.
        assertNull(ThemeColorIndex.of(seedBaseline)
                .replacementFor(staticPalette.colorPrimary(), active));
        assertNull(ThemeColorIndex.of(seedBaseline)
                .replacementFor(staticPalette.colorSurface(), active));
    }

    /**
     * The TV flavour compiles the dark table into {@code values/} and ships no light
     * table of its own, yet {@code frozenPalette()} used to derive the baseline from
     * uiMode alone. On a light-mode device the baseline was therefore
     * {@link ThemeTokens#light()} while inflation produced {@link ThemeTokens#dark()};
     * because every rewrite requires an exact baseline match, the whole TV theme
     * channel bound nothing. Device evidence behind this guard: the same probe against
     * the same build changed 0 pixels on TV while changing ~49k pixels on mobile, and
     * switching the device to dark mode made TV respond (159174 px).
     */
    @Test
    public void tvFlavourBaselineMustDescribeTheCompiledResources() throws Exception {
        ThemeTokens light = ThemeTokens.light();
        ThemeTokens compiled = ThemeTokens.dark();
        ThemeColorIndex fromLight = ThemeColorIndex.of(light);
        ThemeColorIndex fromCompiled = ThemeColorIndex.of(compiled);

        // A light baseline cannot see a single colour the TV build actually inflated.
        assertNull(fromLight.replacementFor(compiled.colorPrimary(), light));
        assertNull(fromLight.replacementFor(compiled.colorSurface(), light));
        // The compiled table does index them, so the corrected baseline can bind again.
        assertFalse(fromCompiled.rolesFor(compiled.colorPrimary()).isEmpty());
        assertFalse(fromCompiled.rolesFor(compiled.colorSurface()).isEmpty());

        // Pin the fix itself: the baseline must be read from the compiled resources
        // instead of being inferred from uiMode, and it must be read through the context
        // that inflates the views. Probing the application context instead re-broke the
        // same invariant the other way round: on API < 31 a forced MODE_NIGHT_YES only
        // reaches the activity's resources, so the application context still reported the
        // light table while every inflated ?attr/color* was dark.
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("compiledDarkPalette(context)"));
        assertTrue(controller.contains("R.color.webhtv_color_primary"));
        assertTrue(controller.contains("R.color.webhtv_color_on_warning"));
        assertTrue(controller.contains("resolvedDark(context)"));
        assertTrue(controller.contains("baseline = frozenPalette(activity);"));
        assertTrue(controller.contains("current = resolveWith(profile, activity);"));
        assertFalse("the probe must not read the application context as its primary source",
                controller.contains("Context context = App.get();"));
        assertFalse("the frozen palette must not be derived from uiMode alone",
                controller.contains("ThemeResolver.resolve(currentThemeMode(), ThemeSeed.NONE, 0, 0, null, null, systemDark)"));
    }

    /**
     * Pins the shared dark/light decision so the TV fix cannot silently re-break, and so
     * the mobile answer is provably unchanged:
     *
     * <ul>
     *   <li>mobile light/dark: the compiled table equals the canonical table, so the
     *       result matches the pre-fix {@code mode}/{@code uiMode} answer;
     *   <li>TV: the compiled table is dark in every configuration, so TV stays dark even
     *       on a light-mode device or when the user explicitly picked light;
     *   <li>unidentifiable table: the historical rule is kept instead of guessing.
     * </ul>
     */
    @Test
    public void darkPaletteDecisionKeepsMobileStableAndForcesTvOntoItsCompiledTable() {
        // Mobile: compiled table matches the canonical one, so both branches agree and
        // the pre-fix behaviour is preserved exactly.
        assertFalse(ThemeController.darkPaletteFor(false, ThemeMode.SYSTEM, false));
        assertTrue(ThemeController.darkPaletteFor(true, ThemeMode.SYSTEM, true));
        assertTrue(ThemeController.darkPaletteFor(true, ThemeMode.DARK, false));

        // TV: the compiled table wins over a light system or an explicit light choice.
        assertTrue(ThemeController.darkPaletteFor(true, ThemeMode.SYSTEM, false));
        assertTrue(ThemeController.darkPaletteFor(true, ThemeMode.LIGHT, false));
        assertTrue(ThemeController.darkPaletteFor(true, ThemeMode.LIGHT, true));

        // Unidentifiable table: keep the historical rule rather than guessing.
        assertFalse(ThemeController.darkPaletteFor(null, ThemeMode.SYSTEM, false));
        assertTrue(ThemeController.darkPaletteFor(null, ThemeMode.SYSTEM, true));
        assertFalse(ThemeController.darkPaletteFor(null, ThemeMode.LIGHT, true));
        assertTrue(ThemeController.darkPaletteFor(null, ThemeMode.DARK, false));

        // The canonical tables must stay identifiable from the flavour resources; a
        // drift here is what silently disabled every TV rewrite.
        assertNotEquals(ThemeTokens.light().colorPrimary(), ThemeTokens.dark().colorPrimary());
        assertNotEquals(ThemeTokens.light().colorSurface(), ThemeTokens.dark().colorSurface());
    }

    @Test
    public void bottomSheetsBindThroughTheSharedDialogChannel() throws Exception {
        String sheet = read("src/main/java/com/fongmi/android/tv/ui/dialog/BaseBottomSheetDialog.java");
        assertTrue(sheet.contains("ThemeController.bindDialog(dialog)"));
        assertTrue(sheet.contains("bindDialogTheme();"));
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("public static void bindTheme(View root)"));
        assertTrue(controller.contains("public static void bindDialog(Dialog dialog)"));
        assertTrue(controller.contains("public static boolean hasProfileOverrides()"));
        assertTrue(controller.contains("ThemeProfileStore.load()"));
    }

    /**
     * scrimOpacity has to stay a strict no-op while unset.
     *
     * <p>The modal scrims in this app do not use the token colour: the episode-detail and
     * TMDB-person dialogs paint a translucent white scrim in light mode and translucent
     * black in dark. Replacing those with {@code colorScrim()} (translucent black in both)
     * was measured to invert the light-mode scrim, so the wiring keeps each dialog's own
     * colour and only replaces its alpha when the user explicitly set the slot. This pins
     * the two halves of that contract: an unset slot must return the shipped literal
     * untouched, and a set slot must change only the alpha.
     */
    @Test
    public void scrimOpacityKeepsTheDialogOwnColourAndOnlyMovesAlphaWhenSet() throws Exception {
        String episode = read("src/mobile/java/com/fongmi/android/tv/ui/dialog/EpisodeDetailDialog.java");
        String person = read("src/main/java/com/fongmi/android/tv/ui/dialog/TmdbPersonDialog.java");
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        for (String source : new String[]{episode, person}) {
            // The shipped literals must survive as the base colour...
            assertTrue(source.contains("0x99F4F7FA"));
            assertTrue(source.contains("applyScrimOpacity("));
            // ...the token must NOT be substituted for the dialog's own scrim colour...
            assertFalse(source.contains("colorScrim()"));
            // ...and the scrim maths must be delegated, not re-implemented.
            assertTrue(source.contains(
                    "ThemeController.applyScrimOpacity(base, ThemeController.configuredScrimOpacity(light))"));
        }

        // Behaviour of the shared maths, asserted directly rather than by string match.
        int lightScrim = 0x99F4F7FA;
        int darkScrim = 0xB3000000;
        // Unset is a strict no-op: this is what keeps the shipped look byte-identical.
        assertEquals(lightScrim, ThemeController.applyScrimOpacity(lightScrim, null));
        assertEquals(darkScrim, ThemeController.applyScrimOpacity(darkScrim, null));
        // Set replaces only the alpha, preserving hue.
        assertEquals(0x00F4F7FA, ThemeController.applyScrimOpacity(lightScrim, 0f));
        // clamped low
        assertEquals(0x00F4F7FA, ThemeController.applyScrimOpacity(lightScrim, -1f));
        assertEquals(0xFFF4F7FA, ThemeController.applyScrimOpacity(lightScrim, 1f));
        // clamped high
        assertEquals(0xFFF4F7FA, ThemeController.applyScrimOpacity(lightScrim, 2f));
        assertEquals(0xD9F4F7FA, ThemeController.applyScrimOpacity(lightScrim, 0.85f));
        assertEquals(0xD9000000, ThemeController.applyScrimOpacity(darkScrim, 0.85f));
        // The accessor must report "unset" rather than the shipped default, otherwise the
        // default rendering could change whenever the stored value equals the default.
        assertTrue(controller.contains("public static Float configuredScrimOpacity(boolean light)"));
        assertTrue(controller.contains("Float value = slots.scrimOpacity;"));
        assertTrue(controller.contains("if (value == null || !Float.isFinite(value)) return null;"));
        // The slot must be read from the same light/dark answer the dialog resolved.
        assertTrue(controller.contains("ThemeProfile.SlotSet slots = light ? active.light : active.dark;"));
    }

    /**
     * The accessor must distinguish "user set the slot" from "the value happens to equal
     * the shipped default", because the dialogs only move their own scrim alpha when the
     * slot is genuinely set. Reads the private active profile via reflection rather than
     * adding a test-only production setter.
     */
    @Test
    public void configuredScrimOpacityReportsUnsetSeparatelyFromAnyValue() throws Exception {
        java.lang.reflect.Field field = ThemeController.class.getDeclaredField("profile");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            ThemeProfile profile = ThemeProfile.defaultProfile();

            field.set(null, profile);
            assertNull("an untouched profile must read as unset", ThemeController.configuredScrimOpacity(true));
            assertNull("an untouched profile must read as unset", ThemeController.configuredScrimOpacity(false));

            profile.light.scrimOpacity = 0.85f;
            assertEquals(Float.valueOf(0.85f), ThemeController.configuredScrimOpacity(true));
            assertNull("the dark half stays unset when only light was set",
                    ThemeController.configuredScrimOpacity(false));

            // A value equal to the token baseline is still a user choice, not "unset".
            profile.light.scrimOpacity = 0.322f;
            assertEquals(Float.valueOf(0.322f), ThemeController.configuredScrimOpacity(true));

            profile.dark.scrimOpacity = 0f;
            assertEquals(Float.valueOf(0f), ThemeController.configuredScrimOpacity(false));
        } finally {
            field.set(null, previous);
        }
    }

    /**
     * overlayOpacity has no correct native landing site (its token is a divider colour and
     * the only real image veil uses the frozen overlayDark), so it is documented in the
     * editor as web-only instead of being wired to a wrong surface.
     */
    @Test
    public void overlayOpacityIsLabelledAsWebOnly() throws Exception {
        assertTrue(read("src/main/res/values/strings.xml")
                .contains("Overlay opacity (web theme only)"));
        assertTrue(read("src/main/res/values-zh-rCN/strings.xml")
                .contains("浮层透明度（仅 Web 主题生效）"));
        assertTrue(read("src/main/res/values-zh-rTW/strings.xml")
                .contains("浮層透明度（僅 Web 主題生效）"));
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
