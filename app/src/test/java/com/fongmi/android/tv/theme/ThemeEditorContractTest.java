package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeEditorContractTest {

    @Test
    public void editorStartsFromThePersistedProfileAndIsNotDirty() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertFalse(editor.isDirty());
        assertNotNull(editor.draft());
        // The draft must be a copy: mutating it cannot touch the original.
        ThemeProfile draft = editor.draft();
        draft.light.primary = "#123456";
        assertFalse(editor.isDirty());
    }

    @Test
    public void everyColorSlotRoundTripsThroughTheDraft() {
        for (ThemeEditor.Slot slot : ThemeEditor.Slot.values()) {
            if (!slot.isColor()) continue;
            ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
            ThemeEditor.Result result = editor.set(slot, false, "#12AB34", 0f);
            assertTrue(slot.name(), result.success());
            assertEquals(slot.name(), "#12AB34", editor.valueOf(slot, false));
        }
    }

    @Test
    public void everyOpacitySlotEnforcesItsOwnRange() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertTrue(editor.set(ThemeEditor.Slot.SCRIM_OPACITY, false, null, 0.85f).success());
        assertTrue(editor.set(ThemeEditor.Slot.DIALOG_OPACITY, false, null, 0.70f).success());
        assertTrue(editor.set(ThemeEditor.Slot.OVERLAY_OPACITY, false, null, 0.05f).success());
        assertTrue(editor.set(ThemeEditor.Slot.PLAYER_SCRIM_OPACITY, false, null, 0.20f).success());
        assertTrue(editor.set(ThemeEditor.Slot.PLAYER_SCRIM_OPACITY, false, null, 1.00f).success());

        assertFalse(editor.set(ThemeEditor.Slot.SCRIM_OPACITY, false, null, 0.86f).success());
        assertFalse(editor.set(ThemeEditor.Slot.DIALOG_OPACITY, false, null, 0.69f).success());
        assertFalse(editor.set(ThemeEditor.Slot.OVERLAY_OPACITY, false, null, 0.61f).success());
        assertFalse(editor.set(ThemeEditor.Slot.OVERLAY_OPACITY, false, null, Float.NaN).success());
        assertFalse(editor.set(ThemeEditor.Slot.PLAYER_SCRIM_OPACITY, false, null, 0.19f).success());
        assertFalse(editor.set(ThemeEditor.Slot.PLAYER_SCRIM_OPACITY, false, null, 1.01f).success());
        assertFalse(editor.set(ThemeEditor.Slot.PLAYER_SCRIM_OPACITY, false, null, Float.NaN).success());
    }

    /**
     * 条目遮罩是"底色 + 遮罩上的文字色 + 强度"三者联动的同一个视觉机制。三者必须同时可编辑，
     * 否则用户改了底色却改不动强度，编辑器就会给出一个只调了一半的面板。
     */
    @Test
    public void itemOverlayGroupIsEditableThroughTheSameSlotApi() {
        assertTrue(ThemeEditor.Slot.ITEM_OVERLAY.isColor());
        assertTrue(ThemeEditor.Slot.ON_ITEM_OVERLAY.isColor());
        assertFalse(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY.isColor());

        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertTrue(editor.set(ThemeEditor.Slot.ITEM_OVERLAY, false, "#801122", 0f).success());
        assertTrue(editor.set(ThemeEditor.Slot.ON_ITEM_OVERLAY, false, "#FFEE11", 0f).success());
        assertEquals("#801122", editor.valueOf(ThemeEditor.Slot.ITEM_OVERLAY, false));
        assertEquals("#FFEE11", editor.valueOf(ThemeEditor.Slot.ON_ITEM_OVERLAY, false));
        assertTrue(editor.isDirty());

        assertTrue(editor.clear(ThemeEditor.Slot.ITEM_OVERLAY, false).success());
        assertTrue(editor.clear(ThemeEditor.Slot.ON_ITEM_OVERLAY, false).success());
        assertNull(editor.valueOf(ThemeEditor.Slot.ITEM_OVERLAY, false));
        assertNull(editor.valueOf(ThemeEditor.Slot.ON_ITEM_OVERLAY, false));
    }

    @Test
    public void itemOverlayOpacityIsClampedToItsOwnRange() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertTrue(editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MIN_ITEM_OVERLAY_OPACITY).success());
        assertTrue(editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MAX_ITEM_OVERLAY_OPACITY).success());
        // 条目遮罩强度与 overlayOpacity 的有效区间不同，越界必须被拒绝而不是静默夹取。
        assertFalse(editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MIN_ITEM_OVERLAY_OPACITY - 0.01f).success());
        assertFalse(editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null,
                ThemeProfileValidator.MAX_ITEM_OVERLAY_OPACITY + 0.01f).success());
        assertFalse(editor.set(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, false, null, Float.NaN).success());
    }

    /** 三个新槽位在编辑器里显示的是三语各自的本土文案，不能只剩英文兜底。 */
    @Test
    public void itemOverlayGroupIsLabelledInEveryLocale() throws Exception {
        String[] keys = {
                "theme_editor_group_item_overlay",
                "theme_editor_slot_item_overlay",
                "theme_editor_slot_on_item_overlay",
                "theme_editor_slot_item_overlay_opacity"};
        for (String locale : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String xml = read("src/main/res/" + locale + "/strings.xml");
            for (String key : keys) {
                assertTrue(locale + " must label " + key, xml.contains("name=\"" + key + "\""));
            }
        }
    }

    @Test
    public void invalidColorsAreRejectedWithoutTouchingTheDraft() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertFalse(editor.set(ThemeEditor.Slot.PRIMARY, false, "#12", 0f).success());
        assertFalse(editor.set(ThemeEditor.Slot.PRIMARY, false, "#80112233", 0f).success());
        assertFalse(editor.set(ThemeEditor.Slot.PRIMARY, false, "red", 0f).success());
        assertNull(editor.valueOf(ThemeEditor.Slot.PRIMARY, false));
    }

    @Test
    public void clearingASlotRestoresInheritance() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertTrue(editor.set(ThemeEditor.Slot.SURFACE, false, "#101820", 0f).success());
        assertTrue(editor.clear(ThemeEditor.Slot.SURFACE, false).success());
        assertNull(editor.valueOf(ThemeEditor.Slot.SURFACE, false));
        assertFalse(editor.isDirty());
    }

    @Test
    public void lightAndDarkSlotsAreIndependent() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        editor.set(ThemeEditor.Slot.PRIMARY, false, "#111111", 0f);
        editor.set(ThemeEditor.Slot.PRIMARY, true, "#222222", 0f);
        assertEquals("#111111", editor.valueOf(ThemeEditor.Slot.PRIMARY, false));
        assertEquals("#222222", editor.valueOf(ThemeEditor.Slot.PRIMARY, true));
    }

    @Test
    public void modeAndSeedMutationsAreNormalised() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertEquals(ThemeProfile.MODE_DARK, editor.setMode("dark").profile().mode);
        assertEquals(ThemeProfile.MODE_SYSTEM, editor.setMode("nonsense").profile().mode);

        ThemeEditor.Result custom = editor.setSeed(ThemeProfile.SEED_CUSTOM, "#0B57D0");
        assertTrue(custom.success());
        assertEquals("#0B57D0", custom.profile().seedColor);
        assertFalse(editor.setSeed(ThemeProfile.SEED_CUSTOM, "nope").success());

        ThemeEditor.Result none = editor.setSeed(ThemeProfile.SEED_NONE, null);
        assertTrue(none.success());
        assertNull(none.profile().seedColor);
    }

    @Test
    public void previewResolvesTheDraftWithoutPersistingIt() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        editor.set(ThemeEditor.Slot.PRIMARY, false, "#123456", 0f);
        ThemeTokens tokens = editor.preview(ThemeMode.LIGHT, ThemeSeed.NONE, 0, 0, false);
        assertEquals(0xFF123456, tokens.colorPrimary());
        // A draft-only edit must not reach persistence.
        assertEquals(-1, ThemeProfileStore.legacyThemeColor(ThemeProfileStore.load()));
        assertEquals(ThemeProfile.SCHEMA_VERSION, editor.draft().schemaVersion);
    }

    @Test
    public void editorRejectsUnknownSlotAndNullColorInsteadOfCrashing() {
        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertEquals(false, editor.set(null, false, "#FFFFFF", 0f).success());
        assertEquals(false, editor.clear(null, false).success());
        assertFalse(editor.set(ThemeEditor.Slot.PRIMARY, false, null, 0f).success());
        assertFalse(editor.set(ThemeEditor.Slot.PRIMARY, true, "   ", 0f).success());
        assertNull(editor.valueOf(null, false));
    }

    @Test
    public void dialogsExposeTheSharedEditorWithoutLegacyColorOnlyPath() throws Exception {
        for (String flavour : new String[]{"mobile", "leanback"}) {
            String dialog = read("src/" + flavour + "/java/com/fongmi/android/tv/ui/dialog/ThemeDialog.java");
            assertTrue(flavour, dialog.contains("ThemeEditor.load()"));
            assertTrue(flavour, dialog.contains("ThemePreviewView.createPanel"));
            assertTrue(flavour, dialog.contains("ThemeColorPickerDialog.create"));
            assertTrue(flavour, dialog.contains("editor.apply()"));
            assertTrue(flavour, dialog.contains("editor.reset()"));
            assertFalse("the old 14-swatch dialog must be gone", dialog.contains("private static final int[] COLORS"));
            String appearance = read("src/" + flavour + "/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java");
            assertFalse(flavour + " must not write theme_color directly", appearance.contains("Setting.putThemeColor("));
        }
    }

    @Test
    public void previewAndPickerAvoidPlayerTokensAndReflection() throws Exception {
        String preview = read("src/main/java/com/fongmi/android/tv/theme/ThemePreviewView.java");
        assertFalse(preview.contains("colorPlayerControl"));
        assertFalse(preview.contains("colorHealth"));
        String picker = read("src/main/java/com/fongmi/android/tv/theme/ThemeColorPickerDialog.java");
        assertTrue(picker.contains("ThemeProfileValidator.normalizeColor"));
        assertFalse(picker.contains("setAccessible"));
        assertFalse(picker.contains("getRecordComponents"));
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }

    /**
     * The editor builds its panels from platform widgets ({@code new TextView(context)},
     * {@code new Button(...)}, {@code new EditText(...)}). Those carry no Material colour
     * role, so they keep the framework default - measured on device as Material's
     * {@code #49454F}, which stayed put no matter which surface the user picked and left
     * the labels unreadable on a dark custom surface. Every programmatic text-bearing
     * widget in the editor must therefore be given a token colour.
     *
     * <p>The check follows the declared variable, so colouring it anywhere in the class
     * (for example the status line, which is recoloured on every update) satisfies it.
     */
    @Test
    public void programmaticEditorWidgetsAlwaysGetTokenColours() throws Exception {
        java.util.regex.Pattern construction = java.util.regex.Pattern
                .compile("(?:([A-Za-z_][\\w]*)\\s*=\\s*)?new\\s+(?:android\\.widget\\.)?(TextView|EditText|Button)\\(");
        for (String path : new String[]{
                "src/main/java/com/fongmi/android/tv/theme/ThemeColorPickerDialog.java",
                "src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeDialog.java",
                "src/leanback/java/com/fongmi/android/tv/ui/dialog/ThemeDialog.java"}) {
            String source = read(path);
            java.util.regex.Matcher m = construction.matcher(source);
            while (m.find()) {
                String variable = m.group(1);
                assertNotNull(path + ": programmatic " + m.group(2) + " must be assigned to a named widget",
                        variable);
                assertTrue(path + ": " + variable + " (" + m.group(2)
                                + ") must set an explicit token text colour",
                        source.contains(variable + ".setTextColor("));
            }
        }
    }

    @Test
    public void theLiveListScrimSlotIsTheLastSlotAndUsesItsOwnRange() {
        ThemeEditor.Slot[] slots = ThemeEditor.Slot.values();
        ThemeEditor.Slot last = slots[slots.length - 1];
        assertEquals("LIVE_LIST_SCRIM_OPACITY", last.name());
        // 位置仍是守卫的一部分：isColor() 现在显式列出不透明度槽，但末尾这个槽必须继续读作强度而不是颜色。
        assertFalse(last.isColor());

        ThemeEditor editor = new ThemeEditor(ThemeProfile.defaultProfile());
        assertTrue(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, false, null, 0.20f).success());
        assertTrue(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, false, null, 1.00f).success());
        assertTrue(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, true, null, 0.20f).success());
        assertTrue(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, true, null, 1.00f).success());

        assertFalse(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, false, null, 0.19f).success());
        assertFalse(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, false, null, 1.01f).success());
        assertFalse(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, false, null, Float.NaN).success());
        assertFalse(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, true, null, 0.19f).success());
        assertFalse(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, true, null, 1.01f).success());
        assertFalse(editor.set(ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY, true, null, Float.NaN).success());
    }
}
