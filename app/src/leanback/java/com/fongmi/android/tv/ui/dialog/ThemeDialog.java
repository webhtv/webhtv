package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.ThemeColorPickerDialog;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeEditor;
import com.fongmi.android.tv.theme.ThemeEditorUi;
import com.fongmi.android.tv.theme.ThemePaletteStyle;
import com.fongmi.android.tv.theme.ThemePresets;
import com.fongmi.android.tv.theme.ThemePreviewView;
import com.fongmi.android.tv.theme.ThemeProfile;
import com.fongmi.android.tv.theme.ThemeProfileCodec;
import com.fongmi.android.tv.theme.ThemeProfileStore;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.EnumMap;

/** Locally themed editing session. Only Save publishes the draft to the rest of the app. */
public final class ThemeDialog extends DialogFragment implements ThemePreviewView.Callbacks {

    private final EnumMap<ThemePresets.Preset, PresetCard> cards = new EnumMap<>(ThemePresets.Preset.class);
    private final EnumMap<ThemePaletteStyle, Button> styles = new EnumMap<>(ThemePaletteStyle.class);
    private ThemeEditor editor;
    private ThemeTokens previewTokens;
    private boolean dark;
    private boolean modeChanged;
    private int wallpaperColor;
    private int savedScroll;
    /**
     * System-bar insets reported by the dialog window, or {@code null} before they arrive.
     *
     * <p>{@code onStart} runs before the window is always laid out, so the insets can still
     * be missing when the window is first sized; the listener installed in {@link #onStart}
     * re-sizes the window once they are known.
     */
    private ThemeDialogLayout.Insets windowInsets;
    private LinearLayout root;
    private ThemePreviewView panel;
    private ScrollView scroll;
    private TextView title;
    private TextView note;
    private TextView presetTitle;
    private TextView paletteTitle;
    private TextView modeTitle;
    private TextView status;
    private Button lightButton;
    private Button darkButton;
    private Button importButton;
    private Button resetButton;
    private Button cancelButton;
    private Button saveButton;

    public static void show(Fragment fragment) {
        new ThemeDialog().show(fragment.getChildFragmentManager(), ThemeDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        editor = ThemeEditor.load();
        int savedMode = Setting.getThemeMode();
        dark = savedMode == 1 || (savedMode < 0 && ThemeController.isNight(requireContext()));
        wallpaperColor = Setting.getWallColor();
        if (savedInstanceState != null) {
            dark = savedInstanceState.getBoolean("preview_dark", dark);
            modeChanged = savedInstanceState.getBoolean("mode_changed", false);
            savedScroll = savedInstanceState.getInt("preview_scroll", 0);
            String draft = savedInstanceState.getString("preview_draft");
            if (draft != null) {
                try {
                    editor.replace(ThemeProfileCodec.parse(draft));
                } catch (RuntimeException ignored) { /* Invalid saved UI state must not block opening settings. */ }
            }
        }
        previewTokens = editor.preview(dark, wallpaperColor);
        root = ThemeEditorUi.column(requireContext());
        // This subtree belongs to the draft, not the builder's persisted-theme binder.
        root.setTag("webhtv:ignore");
        ThemeEditorUi.padding(root, 18, 16);
        buildHeader();

        scroll = new ScrollView(requireContext());
        scroll.setClipToPadding(false);
        LinearLayout content = ThemeEditorUi.column(requireContext());
        content.setPadding(0, 0, 0, dp(12));
        presetTitle = heading(R.string.theme_editor_presets);
        content.addView(presetTitle, ThemeEditorUi.fullWidth(requireContext(), 14));
        // Default is first in the very first configuration group, ahead of palette styles.
        content.addView(buildPresetRow(), ThemeEditorUi.fullWidth(requireContext(), 10));
        content.addView(buildModeRow(), ThemeEditorUi.fullWidth(requireContext(), 14));
        paletteTitle = heading(R.string.theme_editor_palette_style);
        content.addView(paletteTitle, ThemeEditorUi.fullWidth(requireContext(), 12));
        content.addView(buildPaletteRow(), ThemeEditorUi.fullWidth(requireContext(), 8));
        panel = ThemePreviewView.createPanel(requireContext(), editor, dark, previewTokens, this);
        content.addView(panel);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        buildFooter();
        render();
        // Custom actions avoid AlertDialog's unconditional positive-button dismissal on save failure.
        return new WebHtvAlertDialogBuilder(requireContext()).setView(root).create();
    }

    private void buildHeader() {
        LinearLayout header = ThemeEditorUi.row(requireContext());
        title = ThemeEditorUi.text(requireContext(), getString(R.string.setting_theme_color), 22, previewTokens.colorOnSurface());
        ThemeEditorUi.heading(title);
        header.addView(title, ThemeEditorUi.weighted());
        importButton = ThemeEditorUi.button(requireContext(), R.string.theme_editor_import);
        importButton.setOnClickListener(view -> ThemeImportDialog.show(this, profile -> {
            ThemeEditor.Result result = editor.replace(profile);
            if (result.success()) render();
            else setStatus(result.error());
        }));
        header.addView(importButton);
        root.addView(header);
        note = ThemeEditorUi.text(requireContext(), getString(R.string.theme_editor_inline_hint), 12, previewTokens.colorOnSurfaceVariant());
        root.addView(note, ThemeEditorUi.fullWidth(requireContext(), 6));
    }

    private View buildPresetRow() {
        LinearLayout row = ThemeEditorUi.row(requireContext());
        row.setPadding(dp(2), dp(2), dp(2), dp(2));
        for (ThemePresets.Preset preset : ThemePresets.Preset.values()) {
            PresetCard card = new PresetCard(preset);
            cards.put(preset, card);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(128), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(10));
            row.addView(card.view, params);
        }
        return horizontalScroll(row);
    }

    private View buildModeRow() {
        LinearLayout row = ThemeEditorUi.row(requireContext());
        modeTitle = heading(R.string.theme_editor_edit_mode);
        row.addView(modeTitle, ThemeEditorUi.weighted());
        lightButton = ThemeEditorUi.button(requireContext(), R.string.theme_editor_light);
        darkButton = ThemeEditorUi.button(requireContext(), R.string.theme_editor_dark);
        lightButton.setOnClickListener(view -> selectMode(false));
        darkButton.setOnClickListener(view -> selectMode(true));
        LinearLayout.LayoutParams lightParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lightParams.setMarginEnd(dp(8));
        row.addView(lightButton, lightParams);
        row.addView(darkButton);
        return row;
    }

    private View buildPaletteRow() {
        LinearLayout row = ThemeEditorUi.row(requireContext());
        row.setPadding(dp(2), dp(2), dp(2), dp(2));
        for (ThemePaletteStyle style : ThemePaletteStyle.values()) {
            Button button = ThemeEditorUi.button(requireContext(), paletteLabel(style));
            button.setOnClickListener(view -> {
                editor.replace(ThemePresets.withPaletteStyle(editor.draft(), style));
                render();
            });
            styles.put(style, button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            row.addView(button, params);
        }
        return horizontalScroll(row);
    }

    private View horizontalScroll(View child) {
        HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(child);
        return scroll;
    }

    private void buildFooter() {
        status = ThemeEditorUi.text(requireContext(), "", 12, previewTokens.colorOnSurfaceVariant());
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status, ThemeEditorUi.fullWidth(requireContext(), 10));
        LinearLayout actions = ThemeEditorUi.row(requireContext());
        cancelButton = ThemeEditorUi.button(requireContext(), R.string.theme_editor_cancel);
        resetButton = ThemeEditorUi.button(requireContext(), R.string.theme_editor_reset);
        saveButton = ThemeEditorUi.button(requireContext(), R.string.theme_editor_apply);
        cancelButton.setOnClickListener(view -> dismiss());
        resetButton.setOnClickListener(view -> { editor.reset(); render(); });
        saveButton.setOnClickListener(view -> applyDraft());
        for (Button button : new Button[]{cancelButton, resetButton, saveButton}) {
            LinearLayout.LayoutParams params = ThemeEditorUi.weighted();
            if (button != saveButton) params.setMarginEnd(dp(8));
            actions.addView(button, params);
        }
        root.addView(actions, ThemeEditorUi.fullWidth(requireContext(), 8));
    }

    /** One snapshot drives every real control; no view is recreated during a colour edit or drag. */
    private void render() {
        previewTokens = editor.preview(dark, wallpaperColor);
        ThemeTokens tokens = previewTokens;
        title.setTextColor(tokens.colorOnSurface());
        note.setTextColor(tokens.colorOnSurfaceVariant());
        for (TextView heading : new TextView[]{presetTitle, paletteTitle, modeTitle}) {
            heading.setTextColor(tokens.colorOnSurfaceVariant());
        }
        ThemeEditorUi.buttonColors(importButton, tokens.colorSecondaryContainer(), tokens.colorOnSecondaryContainer(), tokens, false);
        modeButton(lightButton, !dark);
        modeButton(darkButton, dark);
        ThemeProfile draft = editor.draft();
        for (PresetCard card : cards.values()) card.render(draft);
        for (ThemePaletteStyle style : styles.keySet()) {
            boolean selected = style.id().equals(draft.paletteStyle);
            modeButton(styles.get(style), selected);
        }
        if (panel != null) panel.render(editor, dark, tokens);
        ThemeEditorUi.buttonColors(cancelButton, tokens.colorSurfaceContainer(), tokens.colorOnSurface(), tokens, false);
        ThemeEditorUi.buttonColors(resetButton, tokens.colorSurfaceContainer(), tokens.colorOnSurface(), tokens, false);
        ThemeEditorUi.buttonColors(saveButton, tokens.colorPrimary(), tokens.colorOnPrimary(), tokens, false);
        setStatus(getString(editor.isDirty() ? R.string.theme_editor_dirty : R.string.theme_editor_saved_state));
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(ThemeEditorUi.shape(requireContext(),
                    ThemeEditorUi.withAlpha(tokens.colorSurface(), tokens.dialogOpacity()), 0, 0, 24));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = ThemeEditorUi.alpha(tokens.colorScrim());
            window.setAttributes(params);
        }
    }

    private void modeButton(Button button, boolean selected) {
        ThemeTokens tokens = previewTokens;
        ThemeEditorUi.buttonColors(button, selected ? tokens.colorPrimaryContainer() : tokens.colorSurfaceContainer(),
                selected ? tokens.colorOnPrimaryContainer() : tokens.colorOnSurface(), tokens, selected);
    }

    private final class PresetCard {
        final ThemePresets.Preset preset;
        final ThemeProfile profile;
        final ThemeTokens light;
        final ThemeTokens night;
        final LinearLayout view;
        final TextView name;
        final TextView detail;
        final View[] swatches = new View[4];

        PresetCard(ThemePresets.Preset preset) {
            this.preset = preset;
            profile = preset.profile();
            ThemeEditor sample = new ThemeEditor(profile);
            light = sample.preview(false, wallpaperColor);
            night = sample.preview(true, wallpaperColor);
            view = ThemeEditorUi.column(requireContext());
            ThemeEditorUi.padding(view, 12, 12);
            view.setFocusable(true);
            view.setTag("theme-preset:" + preset.name());
            view.setOnClickListener(ignored -> {
                ThemeProfile next = profile.copy();
                if (preset != ThemePresets.Preset.DEFAULT) next.name = getString(preset.label);
                next.mode = editor.draft().mode;
                editor.replace(next);
                ThemeDialog.this.render();
            });
            name = ThemeEditorUi.text(requireContext(), getString(preset.label), 14, light.colorOnSurface());
            ThemeEditorUi.heading(name);
            name.setSingleLine(true);
            view.addView(name);
            detail = ThemeEditorUi.text(requireContext(), "", 10, light.colorOnSurfaceVariant());
            detail.setSingleLine(true);
            view.addView(detail, ThemeEditorUi.fullWidth(requireContext(), 6));
            LinearLayout row = ThemeEditorUi.row(requireContext());
            for (int i = 0; i < swatches.length; i++) {
                swatches[i] = new View(requireContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(18), 1);
                if (i < swatches.length - 1) params.setMarginEnd(dp(4));
                row.addView(swatches[i], params);
            }
            view.addView(row, ThemeEditorUi.fullWidth(requireContext(), 10));
        }

        void render(ThemeProfile draft) {
            ThemeTokens tokens = dark ? night : light;
            boolean active = ThemePresets.find(draft) == preset;
            boolean modified = false;
            if (active) {
                ThemeProfile comparable = draft.copy();
                comparable.name = profile.name;
                comparable.mode = profile.mode;
                modified = !ThemeProfileCodec.encode(comparable).equals(ThemeProfileCodec.encode(profile));
            }
            name.setText((active ? "✓ " : "") + getString(preset.label));
            name.setTextColor(tokens.colorOnSurface());
            detail.setText(modified ? R.string.theme_editor_preset_modified
                    : preset == ThemePresets.Preset.DEFAULT ? R.string.theme_editor_preset_original
                    : preset == ThemePresets.Preset.WALLPAPER ? R.string.theme_editor_preset_dynamic : R.string.theme_editor_preset_pair);
            detail.setTextColor(tokens.colorOnSurfaceVariant());
            view.setSelected(active);
            view.setContentDescription(getString(preset.label) + (active ? " · " + getString(R.string.theme_editor_selected) : ""));
            view.setBackground(ThemeEditorUi.interactive(requireContext(), tokens.colorSurface(), tokens.colorOutline(),
                    previewTokens.colorFocus(), active));
            int[] colors = {tokens.colorPrimary(), tokens.colorSecondaryContainer(), tokens.colorSurfaceContainerHigh(), tokens.colorSuccess()};
            for (int i = 0; i < swatches.length; i++) swatches[i].setBackground(ThemeEditorUi.shape(requireContext(), colors[i], 0, 0, 5));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        configureWindow(dialog);
        render();
        // The window can be laid out before the platform reports its system-bar insets, in
        // which case the first sizing falls back to the display bounds and the footer row
        // ends up under the navigation bar. Re-sizing when the real insets arrive is what
        // makes the fix independent of that ordering.
        Window window = dialog.getWindow();
        View decorView = window == null ? null : window.getDecorView();
        if (decorView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(decorView, (view, insets) -> {
                androidx.core.graphics.Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                ThemeDialogLayout.Insets next = new ThemeDialogLayout.Insets(
                        bars.left, bars.top, bars.right, bars.bottom);
                if (!next.equals(windowInsets)) {
                    windowInsets = next;
                    Dialog current = getDialog();
                    if (current != null && current.getWindow() != null) configureWindow(current);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(decorView);
        }
        if (savedScroll > 0) scroll.post(() -> scroll.scrollTo(0, savedScroll));
    }

    private void configureWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int margin = ResUtil.dp2px(ThemeDialogLayout.marginDp(Util.isLeanback()));
        View decorView = window.getDecorView();
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        ThemeDialogLayout.Area area = ThemeDialogLayout.safeArea(screenWidth, screenHeight,
                systemBarInsets(screenWidth, screenHeight));
        int width = ThemeDialogLayout.width(area.width(), margin);
        int height = ThemeDialogLayout.height(area.height(), margin);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = height;
        params.gravity = Gravity.CENTER;
        Drawable panel = ThemeDialogLayout.panelBackground(decorView.getBackground());
        if (panel != null) window.setBackgroundDrawable(panel);
        decorView.setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(width, height);
        if (root != null && root.getLayoutParams() != null) {
            ViewGroup.LayoutParams rootParams = root.getLayoutParams();
            rootParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            rootParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            root.setLayoutParams(rootParams);
        }
    }

    /**
     * The system bars the editor window has to stay clear of.
     *
     * <p>Insets reported by the dialog window are exact and preferred. Until they arrive, the
     * space the host already gave up to the bars (the display minus its content view) is
     * used; that value also covers the host's own chrome, so it can only over-reserve, never
     * clip. A host content view that has not been laid out yet reports nothing, and the
     * display is used until the listener re-sizes the window.
     */
    private ThemeDialogLayout.Insets systemBarInsets(int screenWidth, int screenHeight) {
        // 1) The dialog window's own report, which is the exact value whenever the platform
        //    actually fills it in.
        if (windowInsets != null && !windowInsets.isEmpty()) return windowInsets;
        // 2) The host activity's root window insets. This is the same source Util.isFullscreen
        //    already trusts, and it stays correct while a floating dialog window keeps reporting
        //    systemBars() = [0,0,0,0] on API 28.
        ThemeDialogLayout.Insets host = hostWindowInsets();
        if (!host.isEmpty()) return host;
        // 3) The space the host content view has already given up to the bars.
        ThemeDialogLayout.Insets fallback =
                ThemeDialogLayout.Insets.ofContent(screenWidth, screenHeight, contentWidth(), contentHeight());
        return fallback.isEmpty() ? ThemeDialogLayout.Insets.none() : fallback;
    }

    /**
     * The system bars the host activity has to keep clear.
     *
     * <p>Used because a floating dialog window is not guaranteed to report its own insets: on
     * API 28 the editor's window reports {@code systemBars() = [0,0,0,0]} while the navigation bar
     * is on screen, and accepting that as "no bars" is what pushed the footer row under it.</p>
     */
    private ThemeDialogLayout.Insets hostWindowInsets() {
        try {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(requireActivity().getWindow().getDecorView());
            if (insets == null) return ThemeDialogLayout.Insets.none();
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            return new ThemeDialogLayout.Insets(bars.left, bars.top, bars.right, bars.bottom);
        } catch (RuntimeException error) {
            return ThemeDialogLayout.Insets.none();
        }
    }

    /**
     * The host activity's content view size.
     *
     * <p>Its difference from the display is the system-bar space the host gave up, which is
     * the safe-area fallback while the dialog window's own insets are still unknown.
     */
    private int contentWidth() {
        View content = requireActivity().findViewById(android.R.id.content);
        return content == null ? 0 : content.getWidth();
    }

    private int contentHeight() {
        View content = requireActivity().findViewById(android.R.id.content);
        return content == null ? 0 : content.getHeight();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (editor != null) state.putString("preview_draft", ThemeProfileCodec.encode(editor.draft()));
        state.putBoolean("preview_dark", dark);
        state.putBoolean("mode_changed", modeChanged);
        state.putInt("preview_scroll", scroll == null ? savedScroll : scroll.getScrollY());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cards.clear();
        styles.clear();
        root = null;
        panel = null;
        scroll = null;
        // The next window reports its own insets; a stale value from this one must not be reused.
        windowInsets = null;
    }

    @Override
    public void onColorSlotClicked(ThemeEditor.Slot slot, boolean slotDark) {
        String initial = ThemeEditorUi.hex(ThemePreviewView.colorOf(previewTokens, slot));
        ThemeColorPickerDialog.create(requireContext(), getString(ThemePreviewView.labelOf(slot)), initial, previewTokens, hex -> {
            ThemeEditor.Result result = editor.set(slot, slotDark, hex, 0f);
            if (result.success()) render();
            else setStatus(result.error());
        }).show();
    }

    @Override
    public void onDraftChanged() {
        render();
    }

    private void selectMode(boolean dark) {
        this.dark = dark;
        modeChanged = true;
        editor.setMode(dark ? ThemeProfile.MODE_DARK : ThemeProfile.MODE_LIGHT);
        render();
    }

    private void applyDraft() {
        ThemeProfileStore.ApplyResult result = editor.apply();
        if (!result.success()) {
            setStatus(getString(R.string.theme_editor_save_failed, result.error()));
            return;
        }
        if (modeChanged) {
            Setting.putThemeMode(dark ? 1 : 0);
            ThemeController.applyNightModeToApp();
        }
        dismissAllowingStateLoss();
        // AppearanceDialog already publishes the refresh event. Do not post a second recreation.
        if (getParentFragment() instanceof AppearanceDialog appearance) appearance.onThemeProfileApplied();
        else RefreshEvent.theme();
    }

    private TextView heading(int label) {
        TextView heading = ThemeEditorUi.text(requireContext(), getString(label), 13, previewTokens.colorOnSurfaceVariant());
        ThemeEditorUi.heading(heading);
        return heading;
    }

    private void setStatus(String message) {
        status.setTextColor(previewTokens.colorOnSurfaceVariant());
        status.setText(message == null ? "" : message);
    }

    private int paletteLabel(ThemePaletteStyle style) {
        return switch (style) {
            case TONAL_SPOT -> R.string.theme_editor_palette_tonal;
            case VIBRANT -> R.string.theme_editor_palette_vibrant;
            case EXPRESSIVE -> R.string.theme_editor_palette_expressive;
            case RAINBOW -> R.string.theme_editor_palette_rainbow;
            case FRUIT_SALAD -> R.string.theme_editor_palette_fruit_salad;
            case FIDELITY -> R.string.theme_editor_palette_fidelity;
            case CONTENT -> R.string.theme_editor_palette_content;
            case NEUTRAL -> R.string.theme_editor_palette_neutral;
            case MONOCHROME -> R.string.theme_editor_palette_monochrome;
        };
    }

    private int dp(int value) {
        return ThemeEditorUi.dp(requireContext(), value);
    }
}
