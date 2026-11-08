package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogPlaybackRemoteSyncBinding;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncResult;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncStore;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncer;
import com.fongmi.android.tv.playback.RemoteSyncConfig;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class PlaybackRemoteSyncDialog extends BaseAlertDialog {

    private DialogPlaybackRemoteSyncBinding binding;
    private RemoteSyncConfig editing;
    private Runnable callback;
    private boolean editMode;
    private boolean advanced;
    private boolean editEnabled;

    public static void show(FragmentActivity activity, Runnable callback) {
        PlaybackRemoteSyncDialog dialog = new PlaybackRemoteSyncDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogPlaybackRemoteSyncBinding.inflate(getLayoutInflater());
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
        params.width = (int) (screenWidth * (land ? 0.72f : 0.94f));
        params.height = land ? (int) (screenHeight * 0.96f) : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = land ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = land ? 1 : 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight(land ? 0 : (int) (screenHeight * 0.56f));
        binding.add.requestFocus();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        editing = null;
        super.onDestroyView();
    }

    @Override
    protected void initView() {
        setupInputs();
        showList();
    }

    @Override
    protected void initEvent() {
        binding.add.setOnClickListener(view -> showEdit(new RemoteSyncConfig()));
        binding.negative.setOnClickListener(view -> {
            if (editMode) showList();
            else dismiss();
        });
        binding.positive.setOnClickListener(view -> {
            if (editMode) saveEdit();
            else dismiss();
        });
        binding.delete.setOnClickListener(view -> deleteEditing());
        binding.enabled.setOnClickListener(view -> {
            editEnabled = !editEnabled;
            updateEnabledButton();
        });
        binding.advancedToggle.setOnClickListener(view -> {
            advanced = !advanced;
            updateAdvanced();
        });
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                binding.urlLayout.setError(null);
            }
        });
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (callback != null) callback.run();
        super.onDismiss(dialog);
    }

    private void setupInputs() {
        setupScrollableText(binding.name);
        setupScrollableText(binding.url);
        setupScrollableText(binding.token);
        setupScrollableText(binding.siteKeys);
    }

    private void renderList() {
        List<RemoteSyncConfig> configs = PlaybackRemoteSyncStore.list();
        binding.summary.setText(getString(R.string.playback_remote_sync_summary, PlaybackRemoteSyncStore.activeCount(), configs.size()));
        binding.empty.setVisibility(configs.isEmpty() ? View.VISIBLE : View.GONE);
        binding.sourceList.removeAllViews();
        for (RemoteSyncConfig config : configs) binding.sourceList.addView(row(config));
    }

    private View row(RemoteSyncConfig config) {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(10), dp(9), dp(10), dp(9));
        root.setBackground(rowBackground());
        root.setFocusable(true);
        root.setClickable(true);
        root.setOnClickListener(view -> showEdit(copy(config)));
        LinearLayoutCompat.LayoutParams rootParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootParams.topMargin = dp(8);
        root.setLayoutParams(rootParams);

        LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayoutCompat.HORIZONTAL);
        root.addView(header, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(text(config.displayName(), 15, ThemeController.current().colorOnSurface(), true), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(badge(status(config), statusColor(config)));

        addDetail(root, config.url);
        addDetail(root, meta(config));
        if (!TextUtils.isEmpty(config.lastError)) addDetail(root, getString(R.string.playback_remote_sync_last_error, config.lastError), ThemeController.current().colorError());

        LinearLayoutCompat actions = new LinearLayoutCompat(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayoutCompat.HORIZONTAL);
        LinearLayoutCompat.LayoutParams actionParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(7);
        root.addView(actions, actionParams);

        MaterialButton toggle = actionButton(config.enabled ? R.string.setting_disable : R.string.setting_enable, !config.enabled, false);
        toggle.setOnClickListener(view -> {
            config.enabled = !config.enabled;
            PlaybackRemoteSyncStore.upsert(config);
            renderList();
        });
        actions.addView(toggle, actionLayout(0));

        MaterialButton sync = actionButton(R.string.playback_remote_sync_now, true, false);
        sync.setOnClickListener(view -> syncNow(config.id));
        actions.addView(sync, actionLayout(8));

        MaterialButton edit = actionButton(R.string.dialog_edit, false, false);
        edit.setOnClickListener(view -> showEdit(copy(config)));
        actions.addView(edit, actionLayout(8));

        MaterialButton delete = actionButton(R.string.setting_delete, false, true);
        delete.setOnClickListener(view -> confirmDelete(config));
        actions.addView(delete, actionLayout(8));
        return root;
    }

    private void showList() {
        editMode = false;
        editing = null;
        binding.title.setText(R.string.playback_remote_sync);
        binding.add.setVisibility(View.VISIBLE);
        binding.listPanel.setVisibility(View.VISIBLE);
        binding.editPanel.setVisibility(View.GONE);
        binding.delete.setVisibility(View.GONE);
        binding.negative.setText(R.string.dialog_negative);
        binding.positive.setText(R.string.dialog_positive);
        renderList();
        binding.add.requestFocus();
    }

    private void showEdit(RemoteSyncConfig config) {
        editMode = true;
        editing = config;
        editEnabled = config.enabled;
        advanced = false;
        binding.title.setText(R.string.playback_remote_sync_edit);
        binding.add.setVisibility(View.GONE);
        binding.listPanel.setVisibility(View.GONE);
        binding.editPanel.setVisibility(View.VISIBLE);
        binding.delete.setVisibility(TextUtils.isEmpty(config.url) ? View.GONE : View.VISIBLE);
        binding.negative.setText(R.string.playback_webhook_back);
        binding.positive.setText(R.string.playback_webhook_save);
        bind(config);
        updateAdvanced();
        updateEnabledButton();
        binding.urlLayout.setError(null);
        binding.url.requestFocus();
        binding.contentScroll.scrollTo(0, 0);
    }

    private void bind(RemoteSyncConfig config) {
        binding.name.setText(config.name);
        binding.url.setText(config.url);
        binding.token.setText(config.token);
        binding.siteKeys.setText(join(config.siteKeys));
        binding.interval.setText(String.valueOf(config.intervalMinutes));
        binding.maxItems.setText(String.valueOf(config.maxItems));
        binding.syncOnStartup.setChecked(config.syncOnStartup);
    }

    private void saveEdit() {
        RemoteSyncConfig config = editing == null ? new RemoteSyncConfig() : editing;
        String previousUrl = config.url;
        String previousToken = config.token;
        String previousSites = join(config.siteKeys);
        config.enabled = editEnabled;
        config.name = text(binding.name);
        config.url = text(binding.url);
        if (TextUtils.isEmpty(config.url) || !config.url.startsWith("http")) {
            binding.urlLayout.setError(getString(R.string.playback_webhook_url_invalid));
            Notify.show(R.string.playback_webhook_url_invalid);
            return;
        }
        config.token = text(binding.token);
        config.siteKeys = split(text(binding.siteKeys));
        if (!TextUtils.equals(previousUrl, config.url) || !TextUtils.equals(previousToken, config.token)
                || !TextUtils.equals(previousSites, join(config.siteKeys))) {
            if (config.cursors == null) config.cursors = new java.util.HashMap<>();
            config.cursors.clear();
        }
        config.intervalMinutes = Math.max(0, intValue(binding.interval, 0));
        config.maxItems = Math.max(1, Math.min(1000, intValue(binding.maxItems, 100)));
        config.syncOnStartup = binding.syncOnStartup.isChecked();
        config.lastError = "";
        PlaybackRemoteSyncStore.upsert(config);
        Notify.show(R.string.playback_remote_sync_saved);
        if (callback != null) callback.run();
        showList();
    }

    private void deleteEditing() {
        if (editing == null || TextUtils.isEmpty(editing.id)) return;
        confirmDelete(editing);
    }

    private void confirmDelete(RemoteSyncConfig config) {
        ChoiceDialog.showConfirm(this, R.string.playback_remote_sync_delete_title, getString(R.string.playback_remote_sync_delete_message, config.displayName()), R.string.setting_delete, () -> {
            PlaybackRemoteSyncStore.remove(config.id);
            if (callback != null) callback.run();
            showList();
        });
    }

    private void syncNow(String id) {
        Notify.show(R.string.sync_progress);
        Task.execute(() -> {
            PlaybackRemoteSyncResult result = PlaybackRemoteSyncer.sync(id);
            App.post(() -> {
                if (binding == null) return;
                renderList();
                Notify.show(result.success ? getString(R.string.playback_remote_sync_done, result.applied, result.deleted, result.skipped, result.failed) : result.message);
                if (callback != null) callback.run();
            });
        });
    }

    private void updateEnabledButton() {
        binding.enabled.setText(editEnabled ? R.string.setting_enable : R.string.setting_disable);
        binding.enabled.setAlpha(editEnabled ? 1.0f : 0.65f);
    }

    private void updateAdvanced() {
        binding.advancedPanel.setVisibility(advanced ? View.VISIBLE : View.GONE);
        binding.advancedToggle.setText(advanced ? R.string.playback_webhook_advanced_hide : R.string.playback_webhook_advanced);
    }

    private String meta(RemoteSyncConfig config) {
        String token = TextUtils.isEmpty(config.token) ? getString(R.string.playback_webhook_token_empty) : getString(R.string.playback_webhook_token_set);
        String sites = config.siteKeys == null || config.siteKeys.isEmpty() ? getString(R.string.playback_webhook_all_sites) : getString(R.string.playback_webhook_sites, join(config.siteKeys));
        String last = config.lastSuccessAt > 0 ? getString(R.string.playback_remote_sync_last_at, time(config.lastSuccessAt)) : getString(R.string.playback_remote_sync_never);
        String result = getString(R.string.playback_remote_sync_result, config.lastFetched, config.lastApplied, config.lastDeleted, config.lastSkipped, config.lastFailed);
        return token + " · " + sites + " · " + last + " · " + result;
    }

    private String status(RemoteSyncConfig config) {
        if (config.enabled && config.isUsable()) return getString(R.string.playback_webhook_active);
        return getString(config.enabled ? R.string.playback_webhook_incomplete : R.string.setting_disable);
    }

    private int statusColor(RemoteSyncConfig config) {
        if (config.enabled && config.isUsable()) return ThemeController.current().colorSuccess();
        return ThemeController.current().colorOnSurfaceVariant();
    }

    private RemoteSyncConfig copy(RemoteSyncConfig source) {
        RemoteSyncConfig target = new RemoteSyncConfig();
        target.id = source.id;
        target.name = source.name;
        target.enabled = source.enabled;
        target.url = source.url;
        target.token = source.token;
        target.siteKeys = source.siteKeys == null ? new ArrayList<>() : new ArrayList<>(source.siteKeys);
        target.syncOnStartup = source.syncOnStartup;
        target.intervalMinutes = source.intervalMinutes;
        target.maxItems = source.maxItems;
        target.lastSyncAt = source.lastSyncAt;
        target.lastSuccessAt = source.lastSuccessAt;
        target.lastFetched = source.lastFetched;
        target.lastApplied = source.lastApplied;
        target.lastDeleted = source.lastDeleted;
        target.lastSkipped = source.lastSkipped;
        target.lastFailed = source.lastFailed;
        target.lastError = source.lastError;
        target.cursors = source.cursors == null ? new java.util.HashMap<>() : new java.util.HashMap<>(source.cursors);
        return target;
    }

    private List<String> split(String text) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return result;
        for (String item : text.split("[,，\\s]+")) {
            String value = item.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : TextUtils.join(",", values);
    }

    private int intValue(EditText input, int fallback) {
        try {
            return Integer.parseInt(text(input));
        } catch (Exception e) {
            return fallback;
        }
    }

    private String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void setupScrollableText(EditText input) {
        input.setSelectAllOnFocus(false);
        input.setHorizontallyScrolling(true);
        input.setHorizontalScrollBarEnabled(false);
        input.setVerticalScrollBarEnabled(false);
        input.setOverScrollMode(View.OVER_SCROLL_NEVER);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) view.post(() -> disallowParentIntercept(view, false));
            else disallowParentIntercept(view, true);
            return false;
        });
    }

    private void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private LinearLayoutCompat.LayoutParams actionLayout(int marginStart) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        params.leftMargin = dp(marginStart);
        return params;
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

    private void addDetail(LinearLayoutCompat root, String value) {
        addDetail(root, value, ThemeController.current().colorOnSurfaceVariant());
    }

    private void addDetail(LinearLayoutCompat root, String value, int color) {
        if (TextUtils.isEmpty(value)) return;
        MaterialTextView view = text(value, 12, color, false);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(3);
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

    /**
     * The badge fill follows the palette so the outlined status chip stays legible in both
     * tables. It was a fixed {@code #FFFFFF}, which under the night table drew a white chip
     * with the night success/error text on it.
     */
    private MaterialTextView badge(String value, int color) {
        MaterialTextView view = text(value, 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ThemeController.current().colorSurfaceContainerLowest());
        drawable.setStroke(dp(1), color);
        drawable.setCornerRadius(dp(6));
        view.setBackground(drawable);
        return view;
    }

    /**
     * The row card fill and stroke follow the active palette; see
     * {@code ViewingRecordSyncDialog.rowBackground} for why the fixed light literals could
     * not be rescued by {@code ThemeBinder}.
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
