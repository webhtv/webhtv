package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.AppearanceRowTheme;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeProfile;
import com.fongmi.android.tv.theme.ThemeProfileStore;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public final class AppearanceDialog extends DialogFragment {

    private final List<Row> rows = new ArrayList<>();
    private String[] uiScales;
    private String[] languages;
    private String[] imageSizes;
    private MaterialTextView uiScaleValue;
    private MaterialTextView themeModeValue;
    private MaterialTextView themeValue;
    private MaterialTextView imageSizeValue;
    private MaterialTextView languageValue;

    public static void show(Fragment fragment) {
        new AppearanceDialog().show(fragment.getChildFragmentManager(), AppearanceDialog.class.getSimpleName());
    }

    /** One dialog row, kept so a theme change can re-colour it without a rebuild. */
    private record Row(LinearLayout root, MaterialTextView title, MaterialTextView summary) {
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        uiScales = ResUtil.getStringArray(R.array.select_ui_scale);
        languages = ResUtil.getStringArray(R.array.select_language);
        imageSizes = ResUtil.getStringArray(R.array.select_size);
        return LightDialog.create(requireContext(), getString(R.string.setting_appearance), createContent(), getString(R.string.dialog_close), null, null, null);
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        uiScaleValue = addRow(content, R.string.setting_ui_scale, uiScales[Setting.getUiScaleIndex()], this::chooseUiScale);
        themeModeValue = addRow(content, R.string.setting_theme_mode, getThemeModeText(), this::chooseThemeMode);
        themeValue = addRow(content, R.string.setting_theme_color, getThemeText(), view -> ThemeDialog.show(this));
        imageSizeValue = addRow(content, R.string.setting_size, imageSizes[PlayerSetting.getSize()], this::chooseImageSize);
        languageValue = addRow(content, R.string.setting_language, languages[Setting.getLanguageIndex()], this::chooseLanguage);
        return content;
    }

    private MaterialTextView addRow(LinearLayout content, int titleRes, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setOnClickListener(listener);

        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(titleRes);
        title.setTextSize(15);
        title.setSingleLine(true);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialTextView summary = new MaterialTextView(requireContext());
        summary.setText(value);
        summary.setTextSize(14);
        summary.setGravity(Gravity.END);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        summaryParams.leftMargin = dp(12);
        row.addView(summary, summaryParams);

        // Row fill, stroke and both text colours must come from one palette. The row used
        // to keep the fixed light selector_git_cloud_card while its text already followed
        // ThemeController.current(), which on the TV dark table measured 1.16:1 / 1.36:1.
        AppearanceRowTheme.apply(row, title, summary, ThemeController.current());
        rows.add(new Row(row, title, summary));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        rowParams.bottomMargin = dp(8);
        content.addView(row, rowParams);
        return summary;
    }

    /**
     * Re-colours the rows with the active palette.
     *
     * <p>Called when the dialog is shown and again after a theme change, so an open
     * dialog follows a new profile without being rebuilt.
     */
    private void applyRowTheme() {
        for (Row row : rows) AppearanceRowTheme.apply(row.root(), row.title(), row.summary(), ThemeController.current());
    }

    private void chooseUiScale(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_ui_scale, uiScales, Setting.getUiScaleIndex(), which -> {
            if (which == Setting.getUiScaleIndex()) return;
            uiScaleValue.setText(uiScales[which]);
            Setting.putUiScaleIndex(which);
            dismissAllowingStateLoss();
            requireActivity().recreate();
        });
    }

    private void chooseThemeMode(View view) {
        String[] modes = {getString(R.string.setting_theme_mode_system), getString(R.string.setting_theme_mode_light), getString(R.string.setting_theme_mode_dark)};
        int current = switch (Setting.getThemeMode()) {
            case 0 -> 1;
            case 1 -> 2;
            default -> 0;
        };
        ChoiceDialog.showSingle(this, R.string.setting_theme_mode, modes, current, which -> {
            int mode = which == 0 ? -1 : which - 1;
            if (mode == Setting.getThemeMode()) return;
            Setting.putThemeMode(mode);
            themeModeValue.setText(getThemeModeText());
            ThemeController.applyNightModeToApp();
            dismissAllowingStateLoss();
            RefreshEvent.theme();
        });
    }

    private String getThemeModeText() {
        return switch (Setting.getThemeMode()) {
            case 0 -> getString(R.string.setting_theme_mode_light);
            case 1 -> getString(R.string.setting_theme_mode_dark);
            default -> getString(R.string.setting_theme_mode_system);
        };
    }

    private void chooseImageSize(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_size, imageSizes, PlayerSetting.getSize(), which -> {
            imageSizeValue.setText(imageSizes[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
        });
    }

    private void chooseLanguage(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_language, languages, Setting.getLanguageIndex(), which -> {
            if (which == Setting.getLanguageIndex()) return;
            languageValue.setText(languages[which]);
            Setting.putLanguageIndex(which);
            dismissAllowingStateLoss();
            RefreshEvent.language();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // A profile applied while this dialog stayed on screen must reach the rows, and the
        // rows are only attached after createContent(), so the repaint happens here.
        applyRowTheme();
    }

    void onThemeProfileApplied() {
        themeValue.setText(getThemeText());
        applyRowTheme();
        dismissAllowingStateLoss();
        RefreshEvent.theme();
    }

    private String getThemeText() {
        ThemeProfile profile = ThemeProfileStore.load();
        String name = profile.displayName();
        return "Default".equals(name) ? themeText(Setting.getThemeColor()) : name;
    }

    private String themeText(int color) {
        if (color == -1) return getString(R.string.setting_off);
        return getString(color == 0 ? R.string.setting_auto : R.string.setting_custom);
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }
}
