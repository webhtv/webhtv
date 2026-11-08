package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSourceDialogInflationContractTest {

    @Test
    public void materialButtonLabelsAreAppliedAfterInflation() throws Exception {
        String layout = read(sourcePath().resolve(Path.of("..", "..", "main", "res", "layout", "dialog_tmdb_source.xml")));
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "TmdbSourceDialog.java")));

        assertFalse("TMDB dialog buttons must not resolve text from binary XML on API 25",
                buttonBlock(layout, "add").contains("android:text=")
                        || buttonBlock(layout, "addDisabled").contains("android:text=")
                        || buttonBlock(layout, "manage").contains("android:text=")
                        || buttonBlock(layout, "resetDefault").contains("android:text="));
        assertTrue(source.contains("addBtn.setText(R.string.dialog_tmdb_add)"));
        assertTrue(source.contains("addDisabledBtn.setText(R.string.dialog_tmdb_add)"));
        assertTrue(source.contains("manageBtn.setText(R.string.dialog_tmdb_site_manage)"));
        assertTrue(source.contains("resetBtn.setText(R.string.dialog_tmdb_reset_default)"));
    }

    @Test
    public void apiAndImageRoutesUseSeparateDropdownInputs() throws Exception {
        String layout = read(sourcePath().resolve(Path.of("..", "..", "main", "res", "layout", "dialog_tmdb_source.xml")));
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "TmdbSourceDialog.java")));
        assertTrue(layout.contains("@+id/apiHostInput"));
        assertTrue(layout.contains("@+id/imageHostInput"));
        assertTrue(layout.contains("MaterialAutoCompleteTextView"));
        assertTrue(layout.contains("TextInputLayout"));
        assertFalse(layout.contains("@+id/proxyHostInput"));
        assertTrue(source.contains("setupRouteDropdown(apiHostInput, apiOptionLabels(), activity.getString(R.string.dialog_tmdb_api_host_label))"));
        assertTrue(source.contains("setupRouteDropdown(imageHostInput, imageOptionLabels(), activity.getString(R.string.dialog_tmdb_image_host_label))"));
        assertTrue(source.contains("wireRouteDpadFocus(apiHostInput, apiOptionLabels(),\n"
                + "                activity.getString(R.string.dialog_tmdb_api_host_label), languageInput,\n"
                + "                isCustomVisible(apiCustomInput) ? apiCustomInput : imageHostInput)"));
        assertTrue(source.contains("wireRouteDpadFocus(imageHostInput, imageOptionLabels(),\n"
                + "                activity.getString(R.string.dialog_tmdb_image_host_label),\n"
                + "                isCustomVisible(apiCustomInput) ? apiCustomInput : apiHostInput,\n"
                + "                isCustomVisible(imageCustomInput) ? imageCustomInput : omdbApiKeyInput)"));
        assertTrue("route focus wiring must preserve the route activation key handler",
                source.indexOf("wireRouteDpadFocus(apiHostInput", source.indexOf("private void wireConfigDialogFocus")) > 0);
        assertTrue(source.contains("showRoutePicker(input, labels, title)"));
        assertTrue(source.contains(".setSingleChoiceItems(pickerLabels, checked"));
        assertTrue(source.contains("input.setText(pickerLabels[which], false)"));
        assertTrue(source.contains("input.setKeyListener(null)"));
        assertTrue(source.contains("input.setAdapter(null)"));
        assertTrue("route picker must open only on click or remote confirm, not on focus gain",
                !source.contains("input.post(routeFocusPicker)")
                        && !source.contains("setOnFocusChangeListener")
                        && !source.contains("clearRouteFocusPickers"));
        assertTrue(source.contains("keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER"));
        assertTrue(source.contains("apiDisplayFor(config)"));
        assertTrue(source.contains("imageDisplayFor(config)"));
    }

    @Test
    public void routeDropdownsSupportCustomOption() throws Exception {
        String layout = read(sourcePath().resolve(Path.of("..", "..", "main", "res", "layout", "dialog_tmdb_source.xml")));
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "TmdbSourceDialog.java")));
        assertTrue(layout.contains("@+id/apiCustomInput"));
        assertTrue(layout.contains("@+id/imageCustomInput"));
        assertTrue(source.contains("private String[] withCustomLabel(String[] labels)"));
        assertTrue(source.contains("activity.getString(R.string.dialog_tmdb_route_custom)"));
        assertTrue("selecting custom in the picker must reveal the custom input and move focus for TV users",
                source.contains("showCustomInput(input, customInput)"));
        assertTrue("leaving custom must hide the custom input and restore focus",
                source.contains("hideCustomInput(input, customInput)"));
        assertTrue(source.contains("customInput.post(() -> {"));
        assertTrue("custom inputs must join the D-pad focus chain",
                source.contains("wireTextDpadFocus(apiCustomInput, apiHostInput, imageHostInput, null, null)"));
        assertTrue(source.contains("wireTextDpadFocus(imageCustomInput, imageHostInput, omdbApiKeyInput, null, null)"));
        assertTrue("saved custom route must be normalized through TmdbProxy",
                source.contains("isApiCustomMode()"));
        assertTrue(source.contains("isImageCustomMode()"));
        assertTrue("existing custom route from old config must reopen in custom mode with its value",
                source.contains("apiCustomValueFor(config)"));
        assertTrue(source.contains("imageCustomValueFor(config)"));
        assertTrue(source.contains("updateRouteCustomVisibility()"));
        assertTrue("route picker must move focus into the checked list item so TV D-pad can select options",
                source.contains("picker.getListView().setSelection(focusIndex)"));
    }

    private static String buttonBlock(String layout, String id) {
        String marker = "android:id=\"@+id/" + id + "\"";
        int idStart = layout.indexOf(marker);
        if (idStart < 0) return "";
        int start = layout.lastIndexOf("<com.google.android.material.button.MaterialButton", idStart);
        int end = layout.indexOf("/>", idStart);
        return start >= 0 && end >= 0 ? layout.substring(start, end) : "";
    }

    private static String read(Path path) throws Exception {
        // 工作区行尾随平台而变（Windows autocrlf=true 时为 CRLF），归一化后再比对，
        // 让多行 contains 断言在 LF/CRLF 检出下行为一致（CI ubuntu 与本地 Windows 都能通过）。
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path sourcePath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
