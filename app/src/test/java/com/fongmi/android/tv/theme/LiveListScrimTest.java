package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveListScrimTest {

    @Test
    public void defaultOpacityIsTheShippedLiveListBlack() {
        // shape_live_list.xml is a flat black_80 (#CC000000), so an unset slot must stay
        // byte-identical to what shipped: one uniform 80% black, not a left-to-right ramp.
        assertEquals(0.80f, LiveListScrim.DEFAULT_OPACITY, 0.0001f);
        assertEquals(0xCC000000, LiveListScrim.withAlpha(LiveListScrim.DEFAULT_OPACITY));
    }

    @Test
    public void theSliderReachesAFullyOpaqueBlackAtOneHundredPercent() {
        assertEquals(0xFF000000, LiveListScrim.withAlpha(1f));
    }

    @Test
    public void aLowSliderValueDimsUniformlyWithoutTouchingTheHue() {
        // 20% is the validator's floor and must be a plain linear reading of the slider.
        int dim = LiveListScrim.withAlpha(0.20f);
        assertEquals(0x33000000, dim);
        // Only alpha may move: the scrim is pure black at every strength.
        assertEquals(0, dim & 0x00FFFFFF);
        assertEquals(0, LiveListScrim.withAlpha(1f) & 0x00FFFFFF);
        assertEquals(0, LiveListScrim.withAlpha(0.80f) & 0x00FFFFFF);
    }

    @Test
    public void zeroOpacityHidesTheScrimCompletely() {
        assertEquals(0x00000000, LiveListScrim.withAlpha(0f));
    }

    @Test
    public void unsetSlotFallsBackToTheShippedDefault() {
        assertEquals(0.80f, LiveListScrim.resolveOpacity(null), 0.0001f);
        assertEquals(0.35f, LiveListScrim.resolveOpacity(0.35f), 0.0001f);
        assertEquals(1f, LiveListScrim.resolveOpacity(2f), 0.0001f);
        assertEquals(0f, LiveListScrim.resolveOpacity(-1f), 0.0001f);
    }

    @Test
    public void withAlphaScalesOnlyTheBlackAlpha() {
        assertEquals(0xCC000000, LiveListScrim.withAlpha(0.80f));
        assertEquals(0xFF000000, LiveListScrim.withAlpha(1f));
        assertEquals(0x00000000, LiveListScrim.withAlpha(0f));
    }

    @Test
    public void theScrimIsOneFlatColourThatFollowsTheThemeSlot() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/theme/LiveListScrim.java");
        assertTrue(source.contains("ThemeController.configuredLiveListScrimOpacity("));
        // A flat scrim, deliberately: the drawer must not fade out towards the right edge.
        assertTrue(source.contains("new ColorDrawable("));
        assertFalse(source.contains("GradientDrawable"));
        assertFalse(source.contains("BASE_ALPHAS"));
        // The live drawer must never borrow the playback menu hue.
        assertFalse(source.contains("colorPlayerScrim"));
    }

    private static String read(String path) throws Exception {
        Path root = Files.isDirectory(Path.of("src", "main", "java")) ? Path.of(".") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
