package com.fongmi.android.tv.ui.dialog;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.textview.MaterialTextView;

public final class DebugLogDialog {

    private DebugLogDialog() {
    }

    public static void show(Fragment fragment) {
        show(fragment.requireActivity());
    }

    public static void show(FragmentActivity activity) {
        Server.get().start();
        String localUrl = Server.get().getAddress("/debug/logs");
        String lanUrl = Server.get().getAddress(false) + "/debug/logs";
        SpiderDebug.log("debug", "logs service ready url=%s lan=%s", localUrl, lanUrl);
        String message = activity.getString(R.string.debug_log_dialog_message, lanUrl, localUrl);
        MaterialTextView content = new MaterialTextView(activity);
        content.setText(message);
        // 说明文字是次要前景：跟随调色板的 onSurfaceVariant。原先硬编码 #5F6368，
        // 而承载它的 shape_shell_proxy_dialog 面板是 ?attr/colorSurfaceContainerHigh
        // （日间浅色、夜间深色），于是夜间变成深底深字（实测 2.23:1）。
        content.setTextColor(ThemeController.current().colorOnSurfaceVariant());
        content.setTextSize(14);
        content.setLineSpacing(ResUtil.dp2px(2), 1f);
        android.widget.LinearLayout panel = new android.widget.LinearLayout(activity); panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.addView(content);
        for (com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category category : com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.values()) {
            androidx.appcompat.widget.SwitchCompat toggle = new androidx.appcompat.widget.SwitchCompat(activity);
            toggle.setText(category.title);
            // 开关标签是主要前景：跟随调色板的 onSurface（原先硬编码 #202124，夜间仅 1.19:1）。
            toggle.setTextColor(ThemeController.current().colorOnSurface());
            toggle.setPadding(0, ResUtil.dp2px(8), 0, ResUtil.dp2px(8));
            toggle.setFocusable(true);
            toggle.setChecked(com.github.catvod.crawler.diagnostics.DiagnosticCategories.accepts(com.github.catvod.crawler.DebugLogStore.categories(), category));
            toggle.setOnCheckedChangeListener((button, checked) -> com.github.catvod.crawler.DebugLogStore.setCategory(category, checked));
            panel.addView(toggle);
        }
        MaterialTextView captureNote = new MaterialTextView(activity);
        captureNote.setText("标准日志按容量轮转；深度统计只保留数值，不保存画面或声音。");
        captureNote.setTextSize(14); panel.addView(captureNote);
        android.widget.Button mark = new android.widget.Button(activity); mark.setText("标记此刻故障"); mark.setFocusable(true); panel.addView(mark);
        mark.setOnClickListener(v -> new WebHtvAlertDialogBuilder(activity).setTitle("选择当前现象")
                .setItems(com.fongmi.android.tv.player.DiagnosticControls.SYMPTOMS, (d, which) -> {
                    try { com.fongmi.android.tv.player.DiagnosticControls.mark(com.fongmi.android.tv.player.DiagnosticControls.SYMPTOMS[which]); Notify.show("已标记，继续记录后 15 秒"); }
                    catch (RuntimeException error) { Notify.show(error.getMessage()); }
                }).show());
        android.widget.Button depth = new android.widget.Button(activity); depth.setText("深度统计 60 秒"); depth.setFocusable(true); panel.addView(depth);
        depth.setOnClickListener(v -> new WebHtvAlertDialogBuilder(activity).setTitle("限时深度统计")
                .setMessage("对当前播放做少量低分辨率画面和 PCM 数值统计，不保存图像或声音。到期、切换播放或关闭诊断自动停止。")
                .setNegativeButton("取消", null).setPositiveButton("开启 60 秒", (d, which) -> {
                    try { com.fongmi.android.tv.player.DiagnosticControls.startDepth(60); Notify.show("限时统计已开启"); }
                    catch (RuntimeException error) { Notify.show(error.getMessage()); }
                }).show());
        android.widget.Button stop = new android.widget.Button(activity); stop.setText("停止深度统计"); stop.setFocusable(true); panel.addView(stop);
        stop.setOnClickListener(v -> { com.github.catvod.crawler.diagnostics.DiagnosticCapture.stop("user-stopped"); Notify.show("深度统计已停止"); });
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity); scroll.addView(panel);
        android.app.Dialog dialog = LightDialog.create(activity, activity.getString(R.string.setting_debug_log), scroll, activity.getString(R.string.debug_log_open_browser), v -> open(activity, localUrl), activity.getString(R.string.dialog_negative), null, activity.getString(R.string.debug_log_copy_url), v -> copy(activity, lanUrl));
        dialog.show();
    }

    private static void open(FragmentActivity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Notify.show(R.string.debug_log_no_browser);
        }
    }

    private static void copy(FragmentActivity activity, String url) {
        ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.setting_debug_log), url));
        Notify.show(R.string.debug_log_url_copied);
    }
}
