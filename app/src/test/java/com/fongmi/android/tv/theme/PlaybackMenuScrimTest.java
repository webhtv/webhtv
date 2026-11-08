package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackMenuScrimTest {

    @Test
    public void defaultStrengthKeepsTheMenuReadableOnBrightFrames() {
        int[] colors = PlaybackMenuScrim.gradientColors(0x000000, PlaybackMenuScrim.DEFAULT_OPACITY);
        assertEquals(0x99000000, colors[0]);
        assertEquals(0xCC000000, colors[1]);
    }

    @Test
    public void theSliderScalesTheWholeGradientAndClamps() {
        int[] full = PlaybackMenuScrim.gradientColors(0x000000, 1f);
        assertEquals(0xBF000000, full[0]);
        assertEquals(0xFF000000, full[1]);
        int[] off = PlaybackMenuScrim.gradientColors(0x000000, 0f);
        assertEquals(0x00000000, off[0]);
        assertEquals(0x00000000, off[1]);
    }

    @Test
    public void unsetSlotFallsBackToTheShippedDefault() {
        assertEquals(0.80f, PlaybackMenuScrim.resolveOpacity(null), 0.0001f);
        assertEquals(0.35f, PlaybackMenuScrim.resolveOpacity(0.35f), 0.0001f);
        assertEquals(1f, PlaybackMenuScrim.resolveOpacity(2f), 0.0001f);
        assertEquals(0f, PlaybackMenuScrim.resolveOpacity(-1f), 0.0001f);
    }

    @Test
    public void theScrimFollowsTheThemeSlotInsteadOfAHardcodedDrawable() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/theme/PlaybackMenuScrim.java");
        assertTrue(source.contains("ThemeController.configuredPlayerScrimOpacity("));
        assertTrue(source.contains("ThemeController.current().colorPlayerScrim()"));
        assertTrue(source.contains("GradientDrawable.Orientation.TOP_BOTTOM"));
        assertFalse(source.contains("shape_controller_scrim"));
    }

    @Test
    public void theThemeControllerExposesTheBoundedSlot() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("public static Float configuredPlayerScrimOpacity(boolean light)"));
        String validator = read("src/main/java/com/fongmi/android/tv/theme/ThemeProfileValidator.java");
        assertTrue(validator.contains("MIN_PLAYER_SCRIM_OPACITY = 0.20f"));
        assertTrue(validator.contains("MAX_PLAYER_SCRIM_OPACITY = 1f"));
    }

    private static String read(String path) throws Exception {
        Path root = Files.isDirectory(Path.of("src", "main", "java")) ? Path.of(".") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
