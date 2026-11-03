package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Wiring contract for the live-page channel drawer scrim.
 *
 * <p>The drawer used to be a fixed {@code shape_live_list} gradient; it is now one flat
 * black whose alpha follows the theme profile slot {@code liveListScrimOpacity} through
 * {@link LiveListScrim}. The "unset slot == shipped look", "one uniform colour" and "the
 * slider reaches 100%" requirements are protected by asserting the two hosts still read
 * the slot and that the static XML fallback stayed flat too. These are source assertions
 * on purpose: both activities need an Android runtime for their real behaviour.
 */
public class LiveListScrimWiringTest {

    @Test
    public void theLeanbackLiveActivityPaintsItsChannelDrawerFromTheThemeSlot() throws Exception {
        String activity = read("src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java");
        assertTrue(activity.contains("import com.fongmi.android.tv.theme.LiveListScrim;"));
        assertTrue(activity.contains("LiveListScrim.create(this)"));
        assertTrue(activity.contains("mBinding.recycler.setBackground("));
        assertFalse("the playback overlay must not grow a drawer dependency",
                bodyOf(activity, "private void applyPlaybackOverlay()").contains("LiveListScrim"));
    }

    @Test
    public void theMobileLiveActivityKeepsTheEmbeddedDrawerOnItsOwnDrawable() throws Exception {
        String activity = read("src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java");
        assertTrue(activity.contains("mBinding.recycler.setBackground(LiveListScrim.create(this));"));
        assertTrue(activity.contains("R.drawable.shape_live_embedded_list"));
    }

    @Test
    public void theStaticDrawableFallbackIsOneFlatBlackInBothHosts() throws Exception {
        // The layouts still name @drawable/shape_live_list as a fallback; if it stayed a
        // gradient, any host that skipped the Java path would fade out again to the right.
        for (String path : new String[]{
                "src/leanback/res/drawable/shape_live_list.xml",
                "src/mobile/res/drawable/shape_live_list.xml"}) {
            String drawable = read(path);
            assertTrue(path + " must stay a flat solid", drawable.contains("<solid"));
            assertTrue(path + " must stay black_80", drawable.contains("@color/black_80"));
            assertFalse(path + " must not fade out", drawable.contains("<gradient"));
        }
    }

    @Test
    public void theProfileValidatorBoundsTheLiveListScrimSlotForBothModes() throws Exception {
        String validator = read("src/main/java/com/fongmi/android/tv/theme/ThemeProfileValidator.java");
        assertTrue(validator.contains("MIN_LIVE_LIST_SCRIM_OPACITY = 0.20f"));
        assertTrue(validator.contains("MAX_LIVE_LIST_SCRIM_OPACITY = 1f"));
        assertTrue(validator.contains("liveListScrimOpacity"));
    }

    @Test
    public void theThemeControllerExposesTheUnsetSlotAsNull() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(controller.contains("public static Float configuredLiveListScrimOpacity(boolean light)"));
    }

    @Test
    public void theProfileCarriesTheNewOpacitySlotWithoutADefault() throws Exception {
        String profile = read("src/main/java/com/fongmi/android/tv/theme/ThemeProfile.java");
        assertTrue(profile.contains("public Float liveListScrimOpacity;"));
    }

    @Test
    public void theEditorAndPreviewExposeTheNewOpacitySlot() throws Exception {
        String editor = read("src/main/java/com/fongmi/android/tv/theme/ThemeEditor.java");
        assertTrue(editor.contains("LIVE_LIST_SCRIM_OPACITY"));
        String preview = read("src/main/java/com/fongmi/android/tv/theme/ThemePreviewView.java");
        assertTrue(preview.contains(
                "case LIVE_LIST_SCRIM_OPACITY -> R.string.theme_editor_slot_live_list_scrim_opacity;"));
    }

    @Test
    public void everyLocaleNamesTheNewOpacitySlot() throws Exception {
        String english = read("src/main/res/values/strings.xml");
        assertTrue(english.contains("theme_editor_slot_live_list_scrim_opacity"));
        assertTrue(english.contains("Live list overlay opacity"));
        String simplified = read("src/main/res/values-zh-rCN/strings.xml");
        assertTrue(simplified.contains("theme_editor_slot_live_list_scrim_opacity"));
        assertTrue(simplified.contains("直播列表遮盖层不透明度"));
        String traditional = read("src/main/res/values-zh-rTW/strings.xml");
        assertTrue(traditional.contains("theme_editor_slot_live_list_scrim_opacity"));
        assertTrue(traditional.contains("直播列表遮罩不透明度"));
    }

    private static String bodyOf(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing " + signature, start >= 0);
        int depth = 0;
        for (int index = source.indexOf('{', start); index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start, index + 1);
        }
        return "";
    }

    private static String read(String path) throws Exception {
        Path root = Files.isDirectory(Path.of("src", "main", "java")) ? Path.of(".") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
