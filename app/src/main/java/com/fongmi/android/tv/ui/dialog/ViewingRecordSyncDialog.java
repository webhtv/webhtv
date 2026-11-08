package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogViewingRecordSyncBinding;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncStore;
import com.fongmi.android.tv.playback.PlaybackWebhookStore;
import com.fongmi.android.tv.playback.RemoteSyncConfig;
import com.fongmi.android.tv.playback.ViewingRecordSyncStore;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.time.Instant;
import java.time.ZoneId;

import com.fongmi.android.tv.utils.Formatters;

public class ViewingRecordSyncDialog extends BaseAlertDialog {

    private DialogViewingRecordSyncBinding binding;
    private Runnable callback;

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        ViewingRecordSyncDialog dialog = new ViewingRecordSyncDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogViewingRecordSyncBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new WebHtvAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_Dialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = DialogSafeArea.width(requireActivity(), window);
        int screenHeight = DialogSafeArea.height(requireActivity(), window);
        boolean land = ResUtil.isLand(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.64f : 0.94f));
        params.height = land ? (int) (screenHeight * 0.82f) : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = land ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = land ? 1 : 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight(land ? 0 : (int) (screenHeight * 0.58f));
        binding.content.requestFocus();
    }

    @Override
    protected void initView() {
        render();
    }

    @Override
    protected void initEvent() {
        binding.negative.setOnClickListener(view -> dismiss());
        binding.positive.setOnClickListener(view -> dismiss());
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (callback != null) callback.run();
        super.onDismiss(dialog);
    }

    private void render() {
        binding.summary.setText(ViewingRecordSyncStore.summary(requireContext()));
        binding.content.removeAllViews();
        binding.content.addView(sectionTitle(R.string.viewing_record_sync_switches));
        binding.content.addView(switchRow(
                getString(R.string.setting_viewing_record_sync),
                getString(R.string.viewing_record_sync_master_desc),
                ViewingRecordSyncStore.isEnabled(),
                view -> {
                    ViewingRecordSyncStore.setEnabled(!ViewingRecordSyncStore.isEnabled());
                    render();
                }));
        binding.content.addView(switchRow(
                getString(R.string.viewing_record_sync_local_write),
                getString(R.string.viewing_record_sync_local_write_desc),
                ViewingRecordSyncStore.isLocalWriteEnabled(),
                view -> {
                    ViewingRecordSyncStore.setLocalWriteEnabled(!ViewingRecordSyncStore.isLocalWriteEnabled());
                    render();
                }));
        binding.content.addView(sectionTitle(R.string.playback_remote_sync));
        binding.content.addView(entryRow(
                getString(R.string.playback_remote_sync),
                remoteSummary(),
                R.string.playback_remote_sync_manage,
                view -> PlaybackRemoteSyncDialog.show(requireActivity(), this::render)));
        binding.content.addView(sectionTitle(R.string.playback_webhook));
        binding.content.addView(entryRow(
                getString(R.string.playback_webhook),
                getString(R.string.playback_webhook_summary, PlaybackWebhookStore.activeCount(), PlaybackWebhookStore.totalCount()),
                R.string.playback_webhook_manage,
                view -> PlaybackWebhookDialog.show(requireActivity(), this::render)));
    }

    private View sectionTitle(int text) {
        MaterialTextView view = text(getString(text), 12, ThemeController.current().colorOnSurfaceVariant(), true);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = binding.content.getChildCount() == 0 ? 0 : dp(12);
        view.setLayoutParams(params);
        return view;
    }

    private View switchRow(String title, String detail, boolean enabled, View.OnClickListener listener) {
        LinearLayoutCompat root = rowRoot();
        LinearLayoutCompat header = header(root);
        header.addView(text(title, 15, ThemeController.current().colorOnSurface(), true), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton toggle = actionButton(enabled ? R.string.setting_enable : R.string.setting_disable, enabled, false);
        toggle.setOnClickListener(listener);
        header.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(92), dp(36)));
        addDetail(root, detail);
        return root;
    }

    private View entryRow(String title, String detail, int actionText, View.OnClickListener listener) {
        LinearLayoutCompat root = rowRoot();
        root.setFocusable(true);
        root.setClickable(true);
        root.setOnClickListener(listener);
        LinearLayoutCompat header = header(root);
        header.addView(text(title, 15, ThemeController.current().colorOnSurface(), true), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton action = actionButton(actionText, true, false);
        action.setOnClickListener(listener);
        header.addView(action, new LinearLayoutCompat.LayoutParams(dp(92), dp(36)));
        addDetail(root, detail);
        return root;
    }

    private LinearLayoutCompat rowRoot() {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(10), dp(9), dp(10), dp(9));
        root.setBackground(rowBackground());
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        root.setLayoutParams(params);
        return root;
    }

    private LinearLayoutCompat header(LinearLayoutCompat root) {
        LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayoutCompat.HORIZONTAL);
        root.addView(header, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private String remoteSummary() {
        int active = PlaybackRemoteSyncStore.activeCount();
        int total = PlaybackRemoteSyncStore.totalCount();
        long last = 0;
        String error = "";
        for (RemoteSyncConfig config : PlaybackRemoteSyncStore.list()) {
            last = Math.max(last, Math.max(config.lastSuccessAt, config.lastSyncAt));
            if (error.isEmpty() && !config.lastError.isEmpty()) error = config.lastError;
        }
        String summary = getString(R.string.playback_remote_sync_summary, active, total);
        if (last > 0) summary += " · " + getString(R.string.playback_remote_sync_last_at, time(last));
        if (!error.isEmpty()) summary += " · " + getString(R.string.playback_remote_sync_last_error, error);
        return summary;
    }

    private void addDetail(LinearLayoutCompat root, String value) {
        MaterialTextView view = text(value, 12, ThemeController.current().colorOnSurfaceVariant(), false);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        root.addView(view, params);
    }

    private MaterialTextView text(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setSingleLine(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private MaterialButton actionButton(int text, boolean tonal, boolean danger) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setMinWidth(0);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        ColorStateList bg = ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_bg : R.color.dialog_outlined_button_bg);
        ColorStateList fg = danger ? ColorStateList.valueOf(ThemeController.current().colorError()) : ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_text : R.color.dialog_outlined_button_text);
        button.setBackgroundTintList(bg);
        button.setTextColor(fg);
        if (!tonal) {
            button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
            button.setStrokeWidth(dp(1));
        }
        return button;
    }

    /**
     * The row card fill and stroke follow the active palette.
     *
     * <p>These were fixed light literals ({@code #F5F6F7} fill, {@code #DADCE0} stroke), which
     * no {@code webhtv_color_*} role ever equals, so {@code ThemeBinder}'s exact-match rewrite
     * deliberately skipped them: under the night table the dialog panel went dark while every
     * row stayed a near-white card with black text. {@code colorSurfaceContainerLow} is the
     * semantic "card on a dialog panel" surface ({@code #F2F3F9} day / {@code #181C20} night)
     * and {@code colorOutlineVariant} its paired hairline.
     *
     * <p>The previous two stroke greys ({@code #DADCE0} usable / {@code #E2E5E8} not) differed
     * by 1.09:1, which carried no readable information - usability is already stated by the
     * badge text - so both branches now share one semantic outline.
     */
    private GradientDrawable rowBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ThemeController.current().colorSurfaceContainerLow());
        drawable.setStroke(dp(1), ThemeController.current().colorOutlineVariant());
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private String time(long millis) {
        return Formatters.LOCAL_DATETIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }
}
