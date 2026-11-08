package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogManagePageBatteryBinding;
import com.fongmi.android.tv.databinding.DialogManagePageBinding;
import com.fongmi.android.tv.service.ManageService;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;

public final class ManagePageDialog {

    private ManagePageDialog() {
    }

    public static void show(Fragment fragment) {
        show(fragment.requireActivity());
    }

    public static void show(FragmentActivity activity) {
        PermissionUtil.requestFile(activity, granted -> {
            if (granted) showInternal(activity);
            else Notify.show(R.string.setting_custom_csp_permission_required);
        });
    }

    private static void showInternal(FragmentActivity activity) {
        ManageService.start(activity);
        String localUrl = ManageService.getLocalUrl();
        String lanUrl = ManageService.getLanUrl();
        SpiderDebug.log("server", "manage page ready url=%s lan=%s", localUrl, lanUrl);

        DialogManagePageBinding binding = DialogManagePageBinding.inflate(LayoutInflater.from(activity));
        binding.message.setText(activity.getString(R.string.manage_page_dialog_message, lanUrl, localUrl));
        setScrollHeight(activity, binding.contentScroll, 0.34f, 300);

        Dialog dialog = LightDialog.create(activity, null, binding.getRoot(), 0.68f, 0.92f, 640);
        binding.open.setOnClickListener(v -> open(activity, localUrl));
        binding.copy.setOnClickListener(v -> copy(activity, lanUrl));
        binding.stop.setOnClickListener(v -> {
            ManageService.stop(activity);
            dialog.dismiss();
        });
        dialog.show();
        binding.open.requestFocus();
    }

    private static void open(FragmentActivity activity, String url) {
        if (ManageService.shouldOpenBackgroundPowerSettings(activity)) {
            showBatteryDialog(activity, () -> openBrowser(activity, url));
        } else {
            openBrowser(activity, url);
        }
    }

    private static void showBatteryDialog(FragmentActivity activity, Runnable openAction) {
        DialogManagePageBatteryBinding binding = DialogManagePageBatteryBinding.inflate(LayoutInflater.from(activity));
        binding.message.setText(activity.getString(R.string.manage_page_battery_message, ManageService.getBackgroundPowerGuide(activity)));
        setScrollHeight(activity, binding.contentScroll, 0.42f, 360);

        Dialog dialog = LightDialog.create(activity, null, binding.getRoot(), 0.70f, 0.92f, 680);
        binding.openAnyway.setOnClickListener(v -> {
            ManageService.confirmBackgroundPowerHandled();
            dialog.dismiss();
            openAction.run();
        });
        binding.openSettings.setOnClickListener(v -> {
            if (ManageService.openBackgroundPowerSettings(activity)) dialog.dismiss();
            else Notify.show(R.string.manage_page_battery_open_failed);
        });
        dialog.show();
        binding.openSettings.requestFocus();
    }

    private static void setScrollHeight(FragmentActivity activity, View view, float screenFactor, int maxDp) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = Math.min(ResUtil.dp2px(maxDp), Math.round(DialogSafeArea.height(activity) * screenFactor));
        view.setLayoutParams(params);
    }

    private static void openBrowser(FragmentActivity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Notify.show(R.string.manage_page_no_browser);
        }
    }

    private static void copy(FragmentActivity activity, String url) {
        ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.setting_manage_page), url));
        Notify.show(R.string.manage_page_url_copied);
    }
}
