package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.DialogFragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.cache.CacheCenter;
import com.fongmi.android.tv.cache.CacheCleanupManager;
import com.fongmi.android.tv.cache.CacheCleanupMode;
import com.fongmi.android.tv.cache.CacheCleanupPlan;
import com.fongmi.android.tv.cache.CacheCleanupProgress;
import com.fongmi.android.tv.cache.CacheCleanupResult;
import com.fongmi.android.tv.cache.CacheCleanupStatus;
import com.fongmi.android.tv.cache.CacheFormat;
import com.fongmi.android.tv.cache.CacheLimitOptions;
import com.fongmi.android.tv.cache.CacheMeasurement;
import com.fongmi.android.tv.cache.CacheModuleId;
import com.fongmi.android.tv.cache.CachePolicyEngine;
import com.fongmi.android.tv.cache.CachePolicyStore;
import com.fongmi.android.tv.cache.CacheScheduler;
import com.fongmi.android.tv.cache.CacheSnapshot;
import com.fongmi.android.tv.databinding.DialogCacheManagementBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.android.material.button.MaterialButton;

public class CacheManagementDialog extends DialogFragment {

    private static final CacheModuleId[] MODULE_ORDER = CacheModuleId.values();
    private static final float SCREEN_FRACTION = 0.9f;
    private DialogCacheManagementBinding binding;
    private boolean loading;
    private CharSequence resultLine;
    private final java.util.ArrayList<MaterialButton> moduleButtons = new java.util.ArrayList<>();

    public static void show(Fragment fragment) {
        new CacheManagementDialog().show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity) {
        new CacheManagementDialog().show(activity.getSupportFragmentManager(), null);
    }

    /**
     * Runs the settings row's long-press shortcut: every cache the panel can name is cleared the way
     * the single-key clear of the pre-split settings row left them, and the outcome is reported as a
     * notification.
     *
     * <p>The panel deliberately stays closed. This entry re-creates a gesture from before the cache
     * management split, and that one-key clear never opened a screen either: holding the key is the
     * deliberate act, the cleanup runs in the background, and the settings row re-reads itself once
     * the cleanup publishes its change. Opening the panel here turned a one-key action into "open a
     * screen, then watch it", and put a cancel button in front of an action the user had just
     * confirmed by holding the key.</p>
     */
    public static void cleanEverything() {
        if (CacheCleanupManager.isRunning()) return;
        Resources resources = shortcutResources();
        Notify.show(resources.getString(R.string.cache_cleanup_full_started));
        CacheCleanupManager.execute(CachePolicyEngine.plan(CacheCleanupMode.FULL), "shortcut",
                result -> Notify.show(describe(result, resources)));
    }

    /**
     * The resources the shortcut's toasts resolve their text from.
     *
     * <p>{@link Notify} renders through the application context, whose resources carry the system
     * locale instead of the in-app language the settings screens apply, so a shortcut toast would
     * otherwise disagree with the panel that reports the identical outcome. Resolving through the
     * same helper the settings screens use keeps both surfaces in one language.</p>
     */
    private static Resources shortcutResources() {
        return Setting.wrapLanguage(App.get()).getResources();
    }

    /**
     * Words a cleanup outcome exactly as the panel's status line does, so the same action reads the
     * same way whether it was started from the panel or from the settings row's shortcut.
     *
     * <p>The resources are passed in rather than read globally: the panel renders - and notifies -
     * through its host context, which carries the in-app language choice, while the shortcut has to
     * ask for that same choice explicitly.</p>
     */
    static String describe(CacheCleanupResult result, Resources resources) {
        int message;
        if (result.status() == CacheCleanupStatus.COMPLETED) {
            message = R.string.cache_cleanup_done;
        } else if (result.status() == CacheCleanupStatus.CANCELLED) {
            message = R.string.cache_cleanup_cancelled;
        } else if (result.status() == CacheCleanupStatus.FAILED) {
            message = R.string.cache_cleanup_failed;
        } else if (result.status() == CacheCleanupStatus.NOT_ALLOWED) {
            message = R.string.cache_cleanup_not_allowed;
        } else if (result.status() == CacheCleanupStatus.DEFERRED) {
            message = R.string.cache_cleanup_deferred;
        } else {
            message = R.string.cache_cleanup_partial;
        }
        if (result.status() == CacheCleanupStatus.COMPLETED) {
            return resources.getString(message, FileUtil.byteCountToDisplaySize(result.releasedBytes()),
                    result.deletedFiles());
        }
        if (result.status() == CacheCleanupStatus.PARTIAL) {
            return resources.getString(message, FileUtil.byteCountToDisplaySize(result.releasedBytes()),
                    result.deletedFiles(), result.skippedFiles());
        }
        return resources.getString(message);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(getBinding().getRoot());
        dialog.setCanceledOnTouchOutside(true);
        // Design §15.2: while a cleanup is running the BACK key must request cancellation
        // instead of silently closing the panel and leaving the job running invisibly.
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) return false;
            if (!CacheCleanupManager.isRunning()) return false;
            CacheCleanupManager.cancel();
            return true;
        });
        initView();
        initEvent();
        return dialog;
    }

    protected ViewBinding getBinding() {
        return binding = DialogCacheManagementBinding.inflate(getLayoutInflater());
    }

    protected void initView() {
        renderModules(null);
        refresh(false);
    }

    protected void initEvent() {
        binding.refresh.setOnClickListener(view -> refresh(true));
        binding.cancel.setOnClickListener(view -> CacheCleanupManager.cancel());
        binding.cleanupLight.setOnClickListener(view -> {
            if (!CacheCleanupManager.isRunning()) confirm(CacheCleanupMode.LIGHT, view);
        });
        binding.cleanupStandard.setOnClickListener(view -> {
            if (!CacheCleanupManager.isRunning()) confirm(CacheCleanupMode.STANDARD, view);
        });
        binding.cleanupDeep.setOnClickListener(view -> {
            if (!CacheCleanupManager.isRunning()) confirmDeep(view);
        });
        binding.autoCleanup.setOnClickListener(view -> toggleAutoCleanup());
        binding.retention.setOnClickListener(view -> chooseRetention());
        binding.totalLimit.setOnClickListener(view -> chooseTotalLimit());
        // The close button must follow the same rule as BACK: during a cleanup it requests cancellation instead of hiding a still-running job.
        binding.close.setOnClickListener(view -> {
            if (CacheCleanupManager.isRunning()) {
                CacheCleanupManager.cancel();
                return;
            }
            dismiss();
        });
        updatePolicyButtons();
    }

    @Override
    public void onStart() {
        super.onStart();
        applyWindowSize();
    }

    private void applyWindowSize() {
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        int width = Math.round(ResUtil.getScreenWidth(requireContext()) * SCREEN_FRACTION);
        int height = Math.round(ResUtil.getScreenHeight(requireContext()) * SCREEN_FRACTION);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = height;
        params.dimAmount = 0.6f;
        window.setAttributes(params);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(width, height);
        // Custom Dialog: the window IS the panel, so it must paint its own themed surface.
        // MaterialAlertDialog would keep a wrap_content inner panel (~60% height) regardless of
        // the window size, which is what made the earlier TV dialog look too small.
        GradientDrawable surface = new GradientDrawable();
        surface.setShape(GradientDrawable.RECTANGLE);
        surface.setColor(themeColor(android.R.attr.colorBackground, 0xFF1E1B22));
        surface.setCornerRadius(ResUtil.dp2px(20));
        surface.setStroke(ResUtil.dp2px(1), 0x33FFFFFF);
        binding.getRoot().setBackground(surface);
    }

    private void toggleAutoCleanup() {
        boolean enabled = !CachePolicyStore.isAutoCleanupEnabled();
        CachePolicyStore.putAutoCleanupEnabled(enabled);
        if (enabled) CacheScheduler.get().start();
        else CacheScheduler.get().cancelPersistent(requireContext());
        updatePolicyButtons();
    }

    private void chooseRetention() {
        String[] labels = {getString(R.string.cache_retention_7), getString(R.string.cache_retention_30),
                getString(R.string.cache_retention_90), getString(R.string.cache_retention_forever)};
        int[] days = {7, 30, 90, 3650};
        int checked = 0;
        int current = CachePolicyStore.getRetentionDays();
        for (int index = 0; index < days.length; index++) if (days[index] == current) checked = index;
        ChoiceDialog.showSingle(this, R.string.cache_retention_title, labels, checked, which -> {
            CachePolicyStore.putRetentionDays(days[which]);
            updatePolicyButtons();
        });
    }

    private void chooseTotalLimit() {
        String[] labels = new String[CacheLimitOptions.BYTES.length];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = CacheLimitOptions.BYTES[index] <= 0
                    ? getString(R.string.cache_limit_unlimited)
                    : FileUtil.byteCountToDisplaySize(CacheLimitOptions.BYTES[index]);
        }
        ChoiceDialog.showSingle(this, R.string.cache_total_limit_title, labels,
                CacheLimitOptions.indexOf(CachePolicyStore.getTotalLimitBytes()), which -> {
                    CachePolicyStore.putTotalLimitBytes(CacheLimitOptions.BYTES[which]);
                    updatePolicyButtons();
                });
    }

    private void updatePolicyButtons() {
        binding.autoCleanup.setText(CachePolicyStore.isAutoCleanupEnabled()
                ? R.string.cache_auto_on : R.string.cache_auto_off);
        int days = CachePolicyStore.getRetentionDays();
        binding.retention.setText(getString(R.string.cache_retention_summary,
                days >= 3650 ? getString(R.string.cache_retention_forever) : days + "d"));
        long totalLimit = CachePolicyStore.getTotalLimitBytes();
        binding.totalLimit.setText(getString(R.string.cache_total_limit_summary,
                totalLimit <= 0 ? getString(R.string.cache_limit_unlimited)
                        : FileUtil.byteCountToDisplaySize(totalLimit)));
    }

    private void confirm(CacheCleanupMode mode, View returnFocus) {
        int message = switch (mode) {
            case LIGHT -> R.string.cache_cleanup_confirm_light;
            case STANDARD -> R.string.cache_cleanup_confirm_standard;
            default -> R.string.cache_cleanup_confirm_deep;
        };
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cache_cleanup_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (ignored, which) -> startCleanup(mode))
                .create();
        focusNegativeOnShow(dialog);
        restoreFocusOnDismiss(dialog, returnFocus);
        dialog.show();
    }

    private void confirmModule(CacheModuleId id, View returnFocus) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cache_cleanup_confirm_title)
                .setMessage(getString(R.string.cache_cleanup_confirm_module, getModuleName(id)))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (ignored, which) -> startCleanup(
                        com.fongmi.android.tv.cache.CachePolicyEngine.module(id)))
                .create();
        focusNegativeOnShow(dialog);
        restoreFocusOnDismiss(dialog, returnFocus);
        dialog.show();
    }

    /** Restore the trigger after a confirmation closes, instead of letting geometry choose again. */
    private void restoreFocusOnDismiss(AlertDialog dialog, @Nullable View returnFocus) {
        if (returnFocus == null) return;
        dialog.setOnDismissListener(ignored -> returnFocus.post(() -> {
            if (!returnFocus.isAttachedToWindow() || !returnFocus.isShown()
                    || !returnFocus.isEnabled() || !returnFocus.isFocusable()) return;
            returnFocus.requestFocus();
        }));
    }

    /**
     * Design §15.1: cleanup confirmation dialogs must open with the focus on "cancel" so a
     * single remote OK press can never delete cache data by accident.
     */
    private void focusNegativeOnShow(AlertDialog dialog) {
        dialog.setOnShowListener(ignored -> {
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative == null) return;
            negative.setFocusable(true);
            negative.setFocusableInTouchMode(true);
            // requestFocus() during onShow is too early: the dialog lays out afterwards and
            // drops the focus again, leaving no button focused (a remote OK press then does
            // nothing). Posting defers it until after layout.
            negative.post(negative::requestFocus);
        });
    }

    private void confirmDeep(View returnFocus) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cache_cleanup_confirm_title)
                .setMessage(R.string.cache_cleanup_confirm_deep)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.cache_cleanup_continue, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setFocusable(true);
                negative.setFocusableInTouchMode(true);
                negative.post(negative::requestFocus);
            }
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(view -> {
                // Keep the second confirmation in the same window. Creating a second alert here
                // made one remote confirmation look like multiple stacked dialogs.
                positive.setText(R.string.cache_cleanup_deep_start);
                dialog.setMessage(getString(R.string.cache_cleanup_confirm_deep_final));
                positive.setOnClickListener(confirmed -> {
                    dialog.dismiss();
                    startCleanup(CacheCleanupMode.DEEP);
                });
                // The step-2 text replaces step-1, so re-seat focus on cancel to keep the same
                // "safe default" rule for the irreversible action.
                if (negative != null) negative.post(negative::requestFocus);
            });
        });
        restoreFocusOnDismiss(dialog, returnFocus);
        dialog.show();
    }

    private void startCleanup(CacheCleanupMode mode) {
        if (CacheCleanupManager.isRunning()) return;
        startCleanup(buildPlan(mode));
    }

    private void startCleanup(CacheCleanupPlan plan) {
        if (CacheCleanupManager.isRunning()) return;
        setCleanupInteractive(false);
        setDismissableWhileIdle(false);
        binding.cancel.setVisibility(android.view.View.VISIBLE);
        CacheCleanupManager.execute(plan, this::renderProgress, this::renderResult);
    }

    /**
     * Keeps the panel on screen for the whole cleanup so the user always sees progress and the
     * final outcome; BACK is still handled separately as a cancellation request.
     */
    private void setDismissableWhileIdle(boolean dismissable) {
        Dialog dialog = getDialog();
        if (dialog != null) dialog.setCanceledOnTouchOutside(dismissable);
    }

    private CacheCleanupPlan buildPlan(CacheCleanupMode mode) {
        return com.fongmi.android.tv.cache.CachePolicyEngine.plan(mode);
    }

    private void renderProgress(CacheCleanupProgress progress) {
        if (binding == null || !isAdded()) return;
        binding.status.setText(getString(R.string.cache_cleanup_progress,
                getModuleName(progress.moduleId()), progress.completedModules() + 1, progress.totalModules()));
    }

    private void renderResult(CacheCleanupResult result) {
        if (binding == null || !isAdded()) return;
        setCleanupInteractive(true);
        setDismissableWhileIdle(true);
        binding.cancel.setVisibility(android.view.View.GONE);
        String text = describe(result, getResources());
        // The cleanup already finished, so a modal confirmation here would force the user to
        // dismiss an extra dialog for an action that is already done. Notify passively instead
        // and keep the outcome visible in the panel's own status line.
        Notify.show(text);
        resultLine = text;
        refresh(true);
    }

    private void setCleanupInteractive(boolean enabled) {
        setCleanupButtonInteractive(binding.cleanupLight, enabled);
        setCleanupButtonInteractive(binding.cleanupStandard, enabled);
        setCleanupButtonInteractive(binding.cleanupDeep, enabled);
    }

    /**
     * Keep cleanup buttons focusable while a cleanup runs, but prevent a second click. Disabling
     * the trigger before the confirmation dialog dismisses removes the only safe focus target and
     * makes Android TV move focus to an arbitrary neighbouring button.
     */
    private void setCleanupButtonInteractive(MaterialButton button, boolean enabled) {
        button.setClickable(enabled);
        button.setAlpha(enabled ? 1f : 0.55f);
    }

    private void refresh(boolean force) {
        if (loading) return;
        loading = true;
        binding.status.setText(R.string.cache_management_scanning);
        CacheCenter.get().requestSnapshot(force, this::render);
    }

    private void render(CacheSnapshot snapshot) {
        loading = false;
        if (binding == null || !isAdded()) return;
        long quota = snapshot.systemQuotaBytes();
        String total = FileUtil.byteCountToDisplaySize(snapshot.totalBytes());
        binding.summary.setText(quota > 0
                ? getString(R.string.cache_management_summary_with_quota, total, FileUtil.byteCountToDisplaySize(quota))
                : getString(R.string.cache_management_summary, total));
        if (resultLine != null) {
            binding.status.setText(resultLine);
            resultLine = null;
        } else binding.status.setText(snapshot.warnings().isEmpty()
                ? getString(R.string.cache_management_scanned_at, new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()))
                : getString(R.string.cache_management_partial, snapshot.warnings().size()));
        renderModules(snapshot);
    }

    private void renderModules(@Nullable CacheSnapshot snapshot) {
        ModuleFocusTag focusedModule = focusedModuleTag(binding.getRoot().findFocus());
        binding.modules.removeAllViews();
        moduleButtons.clear();
        long totalBytes = snapshot == null ? 0 : snapshot.totalBytes();
        java.util.ArrayList<CacheMeasurement> ordered = snapshot == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(snapshot.modules());
        ordered.sort((left, right) -> {
            int byBytes = Long.compare(right.bytes(), left.bytes());
            if (byBytes != 0) return byBytes;
            return Integer.compare(left.id().ordinal(), right.id().ordinal());
        });
        if (snapshot == null) for (CacheModuleId id : MODULE_ORDER) addRow(id, null, 0);
        else for (CacheMeasurement measurement : ordered) addRow(measurement.id(), measurement, totalBytes);
        wireFocusOrder();
        restoreModuleFocus(focusedModule);
    }

    @Nullable
    private ModuleFocusTag focusedModuleTag(@Nullable View focused) {
        Object tag = focused == null ? null : focused.getTag();
        return tag instanceof ModuleFocusTag moduleTag ? moduleTag : null;
    }

    private void restoreModuleFocus(@Nullable ModuleFocusTag target) {
        if (target == null) return;
        for (MaterialButton button : moduleButtons) {
            if (!target.equals(button.getTag())) continue;
            button.post(() -> {
                if (!button.isAttachedToWindow() || !button.isShown() || !button.isEnabled()) return;
                button.requestFocus();
            });
            return;
        }
    }

    private void addRow(CacheModuleId id, @Nullable CacheMeasurement measurement, long totalBytes) {
        boolean mobile = Util.isMobile();
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(mobile ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int padding = mobile ? ResUtil.dp2px(8) : 10;
        row.setPadding(0, padding, 0, padding);
        LinearLayout detailColumn = new LinearLayout(requireContext());
        detailColumn.setOrientation(LinearLayout.VERTICAL);
        String title = getModuleName(id);
        if (measurement != null) title += "  " + CacheFormat.percent(measurement.bytes(), totalBytes);
        detailColumn.addView(text(title, 18, themeColor(android.R.attr.textColorPrimary, 0xFF1F1F1F)));
        String detail;
        if (measurement == null) detail = getString(R.string.cache_management_scanning);
        else detail = getString(R.string.cache_management_module_detail,
                FileUtil.byteCountToDisplaySize(measurement.bytes()),
                measurement.fileCount(),
                formatTime(measurement.newestModifiedMs()));
        detailColumn.addView(text(detail, 14, themeColor(android.R.attr.textColorSecondary, 0xFF5F6368)));
        if (mobile) {
            // Phone text must not compete with two minimum-width action buttons.
            row.addView(detailColumn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout actions = new LinearLayout(requireContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton[] buttons = {moduleButton(id), limitButton(id)};
            for (int index = 0; index < buttons.length; index++) {
                MaterialButton button = buttons[index];
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setMinHeight(ResUtil.dp2px(48));
                button.setMinimumHeight(ResUtil.dp2px(48));
                button.setPaddingRelative(ResUtil.dp2px(8), button.getPaddingTop(),
                        ResUtil.dp2px(8), button.getPaddingBottom());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (index > 0) params.setMarginStart(ResUtil.dp2px(8));
                actions.addView(button, params);
            }
            row.addView(actions, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            row.addView(detailColumn, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(moduleButton(id));
            row.addView(limitButton(id));
        }
        binding.modules.addView(row);
    }

    private MaterialButton limitButton(CacheModuleId id) {
        MaterialButton button = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setMinHeight(ResUtil.dp2px(44));
        button.setMinWidth(ResUtil.dp2px(96));
        button.setMinimumHeight(ResUtil.dp2px(44));
        button.setTextSize(14);
        button.setForeground(androidx.core.content.ContextCompat.getDrawable(requireContext(),
                R.drawable.selector_cache_button_focus));
        button.setText(R.string.cache_limit_button);
        button.setEnabled(supportsLimit(id));
        if (supportsLimit(id)) {
            button.setTag(new ModuleFocusTag(id, true));
            button.setOnClickListener(view -> chooseLimit(id));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(8);
        button.setLayoutParams(params);
        moduleButtons.add(button);
        return button;
    }

    private boolean supportsLimit(CacheModuleId id) {
        return switch (id) {
            case GLIDE, LYRICS, KARAOKE, WEBHOME_EXT, EPG, PLUGIN_SCRIPTS, TEMP_FILES -> true;
            default -> false;
        };
    }

    private void chooseLimit(CacheModuleId id) {
        String[] labels = new String[CacheLimitOptions.BYTES.length];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = CacheLimitOptions.BYTES[index] <= 0
                    ? getString(R.string.cache_limit_unlimited)
                    : FileUtil.byteCountToDisplaySize(CacheLimitOptions.BYTES[index]);
        }
        ChoiceDialog.showSingle(this, R.string.cache_limit_title,
                labels, CacheLimitOptions.indexOf(CachePolicyStore.getLimit(id)), which -> {
                    CachePolicyStore.putLimit(id, CacheLimitOptions.BYTES[which]);
                    refresh(true);
                });
    }

    private MaterialButton moduleButton(CacheModuleId id) {
        MaterialButton button = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonTonalStyle);
        button.setMinHeight(ResUtil.dp2px(44));
        button.setMinWidth(ResUtil.dp2px(112));
        button.setMinimumHeight(ResUtil.dp2px(44));
        button.setTextSize(14);
        button.setForeground(androidx.core.content.ContextCompat.getDrawable(requireContext(),
                R.drawable.selector_cache_button_focus));
        // Read the restriction from the registry instead of hardcoding one module: an entry that
        // declares allowManualCleanup=false must not be offered as directly cleanable.
        boolean restricted = !CachePolicyEngine.manualCleanupAllowed(id, requireContext().getCacheDir());
        button.setEnabled(!restricted);
        button.setText(restricted ? R.string.cache_cleanup_owner_managed : R.string.cache_cleanup_module);
        if (!restricted) {
            button.setTag(new ModuleFocusTag(id, false));
            button.setOnClickListener(view -> confirmModule(id, view));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(8);
        button.setLayoutParams(params);
        moduleButtons.add(button);
        return button;
    }

    private record ModuleFocusTag(CacheModuleId id, boolean limitButton) {
    }

    /**
     * The default geometric focus search skipped whole rows (for example UP from "refresh" landed
     * on the policy row instead of the cleanup row). Link the action area explicitly so remote
     * focus always moves through policy row -> cleanup row -> footer without skipping a row.
     */
    private void wireFocusOrder() {
        // Mobile actions wrap; focus should follow their actual positions, not TV columns.
        if (Util.isMobile()) return;
        linkVertical(binding.autoCleanup, null, binding.cleanupLight);
        linkVertical(binding.retention, null, binding.cleanupStandard);
        linkVertical(binding.totalLimit, null, binding.cleanupDeep);
        linkVertical(binding.cleanupLight, binding.autoCleanup, binding.refresh);
        linkVertical(binding.cleanupStandard, binding.retention, binding.refresh);
        linkVertical(binding.cleanupDeep, binding.totalLimit, binding.close);
        linkVertical(binding.refresh, binding.cleanupStandard, null);
        linkVertical(binding.close, binding.cleanupDeep, null);
        binding.refresh.setNextFocusLeftId(binding.cancel.getVisibility() == View.VISIBLE
                ? R.id.cancel : View.NO_ID);
        binding.refresh.setNextFocusRightId(R.id.close);
        binding.close.setNextFocusLeftId(R.id.refresh);
        if (moduleButtons.size() >= 2) {
            MaterialButton lastClean = moduleButtons.get(moduleButtons.size() - 2);
            MaterialButton lastLimit = moduleButtons.get(moduleButtons.size() - 1);
            lastClean.setNextFocusDownId(R.id.autoCleanup);
            lastLimit.setNextFocusDownId(R.id.totalLimit);
            // Close the loop upwards as well, so UP from the policy row returns to the module list
            // instead of jumping to an unrelated neighbour chosen by geometry.
            binding.autoCleanup.setNextFocusUpId(lastClean.getId());
            binding.retention.setNextFocusUpId(lastLimit.getId());
            binding.totalLimit.setNextFocusUpId(lastLimit.getId());
        }
    }

    private void linkVertical(View view, @Nullable View up, @Nullable View down) {
        if (view == null) return;
        view.setNextFocusUpId(up == null ? View.NO_ID : up.getId());
        view.setNextFocusDownId(down == null ? View.NO_ID : down.getId());
    }

    private int themeColor(int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(attribute, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            if (value.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(requireContext(), value.resourceId);
            }
        }
        return fallback;
    }

    private android.widget.TextView text(CharSequence value, int size, int color) {
        android.widget.TextView view = new android.widget.TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String getModuleName(CacheModuleId id) {
        return getString(switch (id) {
            case EXO -> R.string.cache_module_exo;
            case MPV_HLS -> R.string.cache_module_mpv_hls;
            case MPV_DEMUXER -> R.string.cache_module_mpv_demuxer;
            case MPV_RUNTIME -> R.string.cache_module_mpv_runtime;
            case LYRICS -> R.string.cache_module_lyrics;
            case KARAOKE -> R.string.cache_module_karaoke;
            case WEBHOME_EXT -> R.string.cache_module_webhome_ext;
            case WEBHOME_RAW -> R.string.cache_module_webhome_raw;
            case EPG -> R.string.cache_module_epg;
            case GLIDE -> R.string.cache_module_glide;
            case PLUGIN_SCRIPTS -> R.string.cache_module_plugin_scripts;
            case TEMP_FILES -> R.string.cache_module_temp_files;
            case DIAGNOSTIC_LOGS -> R.string.cache_module_diagnostic_logs;
            case LEGACY_FILES -> R.string.cache_module_legacy_files;
            case UNCLASSIFIED -> R.string.cache_module_unclassified;
        });
    }

    private String formatTime(long value) {
        if (value <= 0) return getString(R.string.none);
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(value));
    }

}
