package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityPluginManagerBinding;
import com.fongmi.android.tv.databinding.AdapterPluginEntryBinding;
import com.fongmi.android.tv.databinding.DialogPluginCandidatesBinding;
import com.fongmi.android.tv.databinding.ItemPluginCandidateBinding;
import com.fongmi.android.tv.plugin.PluginBootstrap;
import com.fongmi.android.tv.plugin.PluginBundleIndex;
import com.fongmi.android.tv.plugin.PluginCapabilityRegistry;
import com.fongmi.android.tv.plugin.PluginEnvironmentStore;
import com.fongmi.android.tv.plugin.PluginPackageImporter;
import com.fongmi.android.tv.plugin.PluginRemoteSource;
import com.fongmi.android.tv.plugin.PluginSourceConfig;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 插件管理整页：列出已导入插件、逐条启停、整包导入与删除。
 *
 * <p><b>为什么全部后台工作跑在单线程执行器上。</b>{@link PluginBootstrap#installAndReloadQuietly()}
 * 是阻塞调用（QuickJS 初始化最长 5 秒），而 {@code PluginController.reload} 不是可重入的：
 * 连点开关、导入、删除如果各投一个后台任务，就会有两个 reload 同时改同一份运行时表。
 * 单线程队列把"装载"天然串行化，比加锁更简单，也不会阻塞主线程。
 *
 * <p><b>为什么开关与删除都先写配置再回到 {@link PluginSourceConfig#entries()} 重读列表。</b>
 * 界面上的开关状态必须与持久化状态一致；本地改一份、配置改一份，复用 ViewHolder 时就会
 * 出现"看着开着、其实没启用"。所有结果都以后台重读出来的快照为准。
 */
public class PluginManagerActivity extends BaseActivity {

    /** 远程插件包的临时文件名（位于 {@link #getCacheDir()} 下，流程的任何出口都会删掉它）。 */
    private static final String REMOTE_ZIP_NAME = "plugin-bundle.zip";

    /** 下载超时：够慢网络拉一个脚本包，又不至于让界面在"获取中"停太久。 */
    private static final int REMOTE_TIMEOUT_SECONDS = 20;

    private final ExecutorService mWorker = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "plugin-manager"));
    private final ActivityResultLauncher<Intent> mDirectoryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onDirectoryPicked);

    private ActivityPluginManagerBinding mBinding;
    private EntryAdapter mAdapter;
    private boolean mBusy;
    private boolean mReloadQueued;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, PluginManagerActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityPluginManagerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (Util.isMobile()) {
            View root = mBinding.getRoot();
            int left = root.getPaddingLeft();
            int top = root.getPaddingTop();
            int right = root.getPaddingRight();
            int bottom = root.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(root);
        }
        mBinding.toolbar.setNavigationOnClickListener(view -> finish());
        mAdapter = new EntryAdapter(new EntryAdapter.Listener() {
            @Override
            public void onToggle(PluginSourceConfig.Entry entry, boolean enabled) {
                setEnabled(entry, enabled);
            }

            @Override
            public void onParams(PluginSourceConfig.Entry entry) {
                editParams(entry);
            }

            @Override
            public void onDelete(PluginSourceConfig.Entry entry) {
                confirmDelete(entry);
            }
        });
        mBinding.pluginList.setLayoutManager(new LinearLayoutManager(this));
        mBinding.pluginList.setAdapter(mAdapter);
        refreshEntries();
    }

    @Override
    protected void initEvent() {
        mBinding.importPlugin.setOnClickListener(view -> chooseDirectory());
        mBinding.emptyImport.setOnClickListener(view -> chooseDirectory());
        mBinding.remoteImportPlugin.setOnClickListener(view -> importFromUrl());
        mBinding.reloadPlugins.setOnClickListener(view -> scheduleReload());
    }

    @Override
    protected void onDestroy() {
        // 页面在候选框还开着时被销毁，对话框的 dismiss 回调未必会来，临时包就留在缓存里；
        // 这里补一刀。只在没有后台任务时删：正在跑的那条流程自己会在出口处清理，
        // 从外面删掉它正在读的 zip 只会把一次正常导入变成中途失败。
        if (!mBusy) delete(new File(getCacheDir(), REMOTE_ZIP_NAME));
        mWorker.shutdownNow();
        super.onDestroy();
    }

    private void chooseDirectory() {
        if (mBusy) return;
        // 选目录要读外部存储里的插件包；无权限时 FileActivity 只会显示一个空目录。
        PermissionUtil.requestFile(this, granted -> {
            if (!granted) {
                Notify.show(R.string.plugin_manager_permission_required);
                return;
            }
            FileChooser.from(mDirectoryLauncher).showDirectory();
        });
    }

    private void onDirectoryPicked(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        Uri uri = result.getData().getData();
        // FileActivity 用 Uri.fromFile 回传，getPath() 已是明文绝对路径；
        // 再做一次 URL 解码会把路径里的 "+" 变成空格，所以这里只取 getPath()。
        String path = uri == null ? null : uri.getPath();
        if (path == null || path.isEmpty()) {
            Notify.show(R.string.plugin_manager_invalid_path);
            return;
        }
        importPlugin(new File(path));
    }

    private void importPlugin(File source) {
        if (!beginBusy()) return;
        mWorker.execute(() -> {
            PluginPackageImporter.Result result = PluginPackageImporter.importFrom(source);
            boolean imported = result != null && result.entry() != null;
            String name = imported ? result.entry().name() : null;
            String error = imported || result == null ? null : result.error();
            // 导入成功后条目默认启用，但只有重新装载才真正生效。
            if (imported) PluginBootstrap.installAndReloadQuietly();
            Snapshot snapshot = readEntries();
            App.post(() -> {
                endBusy();
                if (!canTouchView()) return;
                applySnapshot(snapshot);
                if (imported) Notify.show(getString(R.string.plugin_manager_import_ok, name == null ? "" : name));
                else Notify.show(getString(R.string.plugin_manager_import_failed, error == null ? "" : error));
            });
        });
    }

    /**
     * 远程导入第一步：要一条地址。
     *
     * <p>形状照 {@link #editParams}：同一个对话框主题、外框 + 就地校验，只是这里输入的是纯文本，
     * 所以校验放在"点确认之后、关闭之前"——地址能不能用必须先知道，否则用户要等一次失败的下载
     * 才被告知地址写错了。
     *
     * <p><b>地址不落盘。</b>它只在本次流程的局部变量与闭包里活着：不写 SharedPreferences、
     * 不写数据库、不写文件。这个功能是一次性导入，没有"记住上次地址"这回事。
     */
    private void importFromUrl() {
        if (mBusy) return;
        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(getString(R.string.plugin_manager_remote_hint));
        // helperText 必须先启用再赋值：只调 setHelperText 在未启用时不会显示，
        // 而"只接受 https / GitHub 或直链 zip"这句话恰恰决定了用户该粘什么。
        layout.setHelperTextEnabled(true);
        layout.setHelperText(getString(R.string.plugin_manager_remote_helper));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        MaterialButton paste = new MaterialButton(this);
        paste.setText(R.string.plugin_manager_remote_paste);
        paste.setAllCaps(false);
        paste.setOnClickListener(view -> pasteClipboard(input));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(20);
        container.setPadding(horizontal, dp(8), horizontal, 0);
        container.addView(layout);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pasteParams.topMargin = dp(8);
        container.addView(paste, pasteParams);

        androidx.appcompat.app.AlertDialog dialog = new WebHtvAlertDialogBuilder(this, R.style.Theme_WebHTV_Dialog)
                .setTitle(getString(R.string.plugin_manager_remote_title))
                .setView(container)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.plugin_manager_remote_start, null)
                .create();
        dialog.setOnShowListener(shown -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                PluginRemoteSource.Resolution resolution = PluginRemoteSource.resolve(value);
                if (!resolution.isSuccess()) {
                    layout.setError(resolution.error());
                    return;
                }
                dialog.dismiss();
                startRemoteImport(value);
            });
            // 遥控器进对话框先落在输入框上，返回键退到"取消"，方向键往下能到"粘贴/获取"。
            input.requestFocus();
        });
        dialog.show();
        LightDialog.apply(dialog);
    }

    /** 从剪贴板填地址；空剪贴或非文本剪贴什么都不做，不留一句"没有内容"的多余提示。 */
    private void pasteClipboard(TextInputEditText input) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) return;
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).getText();
        if (text == null || text.length() == 0) return;
        input.setText(text.toString().trim());
        input.setSelection(input.length());
    }

    /**
     * 远程导入第二步：下载 + 索引，全程在 {@link #mWorker} 上。
     *
     * <p>临时包落在 {@code getCacheDir()}：它不是用户数据，放 files 目录会让人以为盘上有一份
     * "已安装的插件"；也刻意不放进插件根目录，否则装载期会把半成品当真实插件扫到。
     */
    private void startRemoteImport(String rawUrl) {
        if (!beginBusy()) {
            // 校验通过到点确认之间，别的操作可能已经把页面占住了；这时候不能悄悄什么都不做。
            Notify.show(R.string.plugin_manager_remote_busy);
            return;
        }
        mWorker.execute(() -> {
            File zip = new File(getCacheDir(), REMOTE_ZIP_NAME);
            // 上一次流程异常退出可能留下同名文件；不先删就会把旧包当成这次下载的结果。
            delete(zip);

            PluginRemoteSource.Resolution resolution = PluginRemoteSource.resolve(rawUrl);
            if (!resolution.isSuccess()) {
                failRemoteImport(zip, resolution.error());
                return;
            }
            try {
                PluginRemoteSource.download(resolution.urls(), zip, REMOTE_TIMEOUT_SECONDS);
            } catch (IOException | RuntimeException error) {
                failRemoteImport(zip, error.getMessage());
                return;
            }

            PluginBundleIndex.Result index = PluginBundleIndex.inspect(zip);
            if (index == null || !index.isSuccess()) {
                failRemoteImport(zip, index == null ? null : index.error());
                return;
            }
            if (index.candidates().isEmpty()) {
                failRemoteImport(zip, getString(R.string.plugin_manager_remote_empty));
                return;
            }
            List<PluginBundleIndex.Candidate> candidates = index.candidates();
            App.post(() -> {
                endBusy();
                if (!canTouchView()) {
                    delete(zip);
                    return;
                }
                showCandidates(candidates, zip, rawUrl);
            });
        });
    }

    private void failRemoteImport(File zip, String error) {
        delete(zip);
        App.post(() -> {
            endBusy();
            if (!canTouchView()) return;
            Notify.show(getString(R.string.plugin_manager_remote_failed, error == null ? "" : error));
        });
    }

    /**
     * 第三步：让用户挑插件。
     *
     * <p>默认全选：用户刚指了一个包，最常见的意思是"把这个包装上"。但每条都仍然是独立可取消的
     * 勾选——一个 GitHub 仓库常带多个插件，装哪一个只能由用户决定。
     *
     * @param rawUrl 只用于显示来源主机，绝不被保存
     */
    private void showCandidates(List<PluginBundleIndex.Candidate> candidates, File zip, String rawUrl) {
        DialogPluginCandidatesBinding binding = DialogPluginCandidatesBinding.inflate(getLayoutInflater());
        binding.candidateCount.setText(getString(R.string.plugin_manager_remote_found, candidates.size()));
        binding.candidateSource.setText(getString(R.string.plugin_manager_remote_source, hostOf(rawUrl)));

        CandidateAdapter adapter = new CandidateAdapter(candidates);
        binding.candidateList.setLayoutManager(new LinearLayoutManager(this));
        binding.candidateList.setAdapter(adapter);

        androidx.appcompat.app.AlertDialog dialog = new WebHtvAlertDialogBuilder(this, R.style.Theme_WebHTV_Dialog)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.plugin_manager_remote_do_import, null)
                .create();
        boolean[] handedOff = new boolean[1];
        final Button[] positive = new Button[1];

        // 勾选集合变化后两处状态要一起动：全选按钮的文案、导入按钮的可点性。
        adapter.onCheckedChanged = () -> {
            binding.selectAll.setText(adapter.isAllChecked()
                    ? R.string.plugin_manager_remote_select_none
                    : R.string.plugin_manager_remote_select_all);
            if (positive[0] != null) positive[0].setEnabled(adapter.checkedCount() > 0);
        };
        binding.selectAll.setOnClickListener(view -> {
            adapter.setAllChecked(!adapter.isAllChecked());
            adapter.notifyDataSetChanged();
            adapter.onCheckedChanged.run();
        });

        dialog.setOnShowListener(shown -> {
            positive[0] = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            positive[0].setOnClickListener(view -> {
                List<PluginBundleIndex.Candidate> selected = adapter.checkedCandidates();
                // 一个都没勾时按钮本就是灰的；这里再判一次，避免无障碍/脚本路径绕过可用性。
                if (selected.isEmpty()) return;
                handedOff[0] = true;
                dialog.dismiss();
                confirmInstall(selected, zip);
            });
            positive[0].setEnabled(adapter.checkedCount() > 0);
            adapter.onCheckedChanged.run();
            // 焦点先落在"全选/取消全选"上：它是确定可达的实体控件，
            // 从这里按方向键向下就进列表；把焦点直接交给 RecyclerView 是否落到首行取决于布局时序。
            binding.selectAll.requestFocus();
        });
        // 只有"没走到确认"的关闭才由这里删包：交给 confirmInstall 之后，清理归它的每个出口管。
        dialog.setOnDismissListener(shown -> {
            if (!handedOff[0]) delete(zip);
        });
        dialog.show();
        LightDialog.apply(dialog);
    }

    /** 第四步：安装前的唯一一次确认。 */
    private void confirmInstall(List<PluginBundleIndex.Candidate> selected, File zip) {
        StringBuilder names = new StringBuilder();
        Set<String> union = new LinkedHashSet<>();
        for (PluginBundleIndex.Candidate candidate : selected) {
            if (names.length() > 0) names.append(getString(R.string.plugin_manager_remote_name_separator));
            String name = candidate.name() == null || candidate.name().isEmpty() ? candidate.id() : candidate.name();
            names.append(name);
            if (candidate.permissions() != null) union.addAll(candidate.permissions());
        }

        SpannableStringBuilder message = new SpannableStringBuilder(getString(
                R.string.plugin_manager_remote_confirm_message, selected.size(), names.toString(), permissionText(union)));
        // 这句必须无条件出现：它说的是"没有任何沙箱与安全校验"，不是某个权限的注解，
        // 不能因为权限清单里恰好没有 net.request/ui.panel 就一起消失。
        message.append("\n\n").append(getString(R.string.plugin_manager_remote_confirm_not_sandboxed));

        // 两个高风险权限各用一句大白话单独说一遍：摘要里的"联网（net.request）"只是一个词，
        // 用户未必读得出后果；只在确实申请时才出现，避免变成一段永远在的噪声。
        int riskStart = message.length();
        if (union.contains(PluginCapabilityRegistry.PERMISSION_NET_REQUEST)) {
            message.append("\n\n").append(getString(R.string.plugin_manager_perm_net_request_plain));
        }
        if (union.contains(PluginCapabilityRegistry.PERMISSION_UI_PANEL)) {
            message.append("\n").append(getString(R.string.plugin_manager_perm_ui_panel_plain));
        }
        if (message.length() > riskStart) {
            message.setSpan(new ForegroundColorSpan(ThemeController.current().colorError()), riskStart, message.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            message.setSpan(new StyleSpan(Typeface.BOLD), riskStart, message.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 取消只有两条路：负向按钮，或者返回键/点外部触发的 cancel。两个都显式删包，
        // 不用 setOnDismissListener 统一处理——按钮监听与 dismiss 的先后由 AppCompat 决定，
        // 依赖它会让"点安装"偶尔先把包删掉。
        new WebHtvAlertDialogBuilder(this, R.style.Theme_WebHTV_Dialog)
                .setTitle(R.string.plugin_manager_remote_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_cancel, (clicked, which) -> delete(zip))
                .setOnCancelListener(cancelled -> delete(zip))
                .setPositiveButton(R.string.plugin_manager_remote_install, (clicked, which) -> runBundleImport(selected, zip))
                .show();
    }

    /**
     * 第五步：真正安装。
     *
     * <p>只有 {@code selected} 里被勾选的候选会传进导入器——未勾选的插件在这一步之后没有任何
     * 写入路径，界面上的勾选状态就是安装集合本身。
     */
    private void runBundleImport(List<PluginBundleIndex.Candidate> selected, File zip) {
        if (!beginBusy()) {
            // 同一时刻只允许一个导入流程；按钮此时是禁用的，走到这里只会是重复回调。
            delete(zip);
            Notify.show(R.string.plugin_manager_remote_busy);
            return;
        }
        mWorker.execute(() -> {
            PluginPackageImporter.BundleResult result = PluginPackageImporter.importBundle(zip, selected);
            boolean installed = false;
            if (result != null && result.isSuccess()) {
                for (PluginPackageImporter.ItemResult item : result.items()) {
                    if (item.entry() != null) installed = true;
                }
            }
            // 与 importPlugin(File) 同一条刷新路径：先重装生效，再重读配置，最后回主线程刷界面。
            if (installed) PluginBootstrap.installAndReloadQuietly();
            Snapshot snapshot = readEntries();
            delete(zip);
            App.post(() -> {
                endBusy();
                if (!canTouchView()) return;
                applySnapshot(snapshot);
                showRemoteResult(result);
            });
        });
    }

    private void showRemoteResult(PluginPackageImporter.BundleResult result) {
        if (result == null || !result.isSuccess()) {
            Notify.show(getString(R.string.plugin_manager_remote_batch_failed, result == null ? "" : result.error()));
            return;
        }
        List<String> lines = new ArrayList<>();
        int installed = 0;
        for (PluginPackageImporter.ItemResult item : result.items()) {
            String name = item.candidate() == null ? "" : item.candidate().name();
            if (name == null || name.isEmpty()) name = item.candidate() == null ? "" : item.candidate().id();
            if (item.entry() != null) {
                installed++;
                lines.add(getString(R.string.plugin_manager_remote_item_ok, name));
            } else {
                lines.add(getString(R.string.plugin_manager_remote_item_failed, name, item.error() == null ? "" : item.error()));
            }
        }
        if (lines.isEmpty()) {
            Notify.show(R.string.plugin_manager_remote_empty);
            return;
        }
        if (installed == lines.size()) {
            Notify.show(getString(R.string.plugin_manager_remote_all_ok, installed));
            return;
        }
        new WebHtvAlertDialogBuilder(this)
                .setTitle(R.string.plugin_manager_remote_result_title)
                .setMessage(String.join("\n", lines))
                .setPositiveButton(R.string.dialog_confirm, null)
                .show();
    }

    /** 来源主机：解析用户输入的那条地址，取不到主机时退回原文，绝不显示空行。 */
    private static String hostOf(String rawUrl) {
        try {
            String host = Uri.parse(rawUrl).getHost();
            if (host != null && !host.isEmpty()) return host;
        } catch (RuntimeException ignored) {
        }
        return rawUrl == null ? "" : rawUrl;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        // 删不掉无法补救（占用/权限）；缓存目录会被系统回收，所以这里只尽力一次。
        file.delete();
    }

    /**
     * 权限令牌 → 人话。
     *
     * <p>未知令牌原样显示：把它藏起来等于让用户在不知情的情况下授权，比显示一个生僻英文词糟得多。
     */
    private String permissionLabel(String token) {
        if (PluginCapabilityRegistry.PERMISSION_NET_REQUEST.equals(token)) return getString(R.string.plugin_manager_perm_net_request);
        if (PluginCapabilityRegistry.PERMISSION_UI_PANEL.equals(token)) return getString(R.string.plugin_manager_perm_ui_panel);
        if (PluginCapabilityRegistry.PERMISSION_UI_FLOATING.equals(token)) return getString(R.string.plugin_manager_perm_ui_floating);
        if (PluginCapabilityRegistry.PERMISSION_UI_TOOLBAR.equals(token)) return getString(R.string.plugin_manager_perm_ui_toolbar);
        if (PluginCapabilityRegistry.PERMISSION_UI_PAGE.equals(token)) return getString(R.string.plugin_manager_perm_ui_page);
        if (PluginCapabilityRegistry.PERMISSION_EVENTS.equals(token)) return getString(R.string.plugin_manager_perm_events);
        if (PluginCapabilityRegistry.PERMISSION_EPISODE_DATA.equals(token)) return getString(R.string.plugin_manager_perm_episode_data);
        return token;
    }

    /** 高风险权限：联网与自绘面板都能把数据带出设备或把界面画成任意样子，必须显眼。 */
    private static boolean isHighRisk(String token) {
        return PluginCapabilityRegistry.PERMISSION_NET_REQUEST.equals(token)
                || PluginCapabilityRegistry.PERMISSION_UI_PANEL.equals(token);
    }

    private CharSequence permissionText(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return getString(R.string.plugin_manager_perm_none);
        List<String> sorted = new ArrayList<>(permissions);
        Collections.sort(sorted);
        StringBuilder text = new StringBuilder();
        for (String token : sorted) {
            if (text.length() > 0) text.append(getString(R.string.plugin_manager_perm_separator));
            text.append(permissionLabel(token));
        }
        return text.toString();
    }

    /** 单个候选行的权限展示：高风险项加粗并用错误色，令牌本身始终可见。 */
    private CharSequence permissionSpans(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return getString(R.string.plugin_manager_perm_none);
        List<String> sorted = new ArrayList<>(permissions);
        Collections.sort(sorted);
        SpannableStringBuilder text = new SpannableStringBuilder();
        for (String token : sorted) {
            if (text.length() > 0) text.append(getString(R.string.plugin_manager_perm_separator));
            int start = text.length();
            text.append(permissionLabel(token));
            if (isHighRisk(token)) {
                text.setSpan(new ForegroundColorSpan(ThemeController.current().colorError()), start, text.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return text;
    }

    /**
     * 编辑某个插件的参数（用户自填的键值 JSON）。
     *
     * <p>形状照 {@code SubtitleSettingsDialog.showSourceEnvironment}：多行输入 + 外框 + 就地校验，
     * 因为用户填的是手打 JSON，错误必须在对话框里当场指出，而不是保存后在别处才炸。
     *
     * <p>页面本身不重建布局：WebHtvAlertDialogBuilder 里 setView 一个临时容器即可。
     */
    private void editParams(PluginSourceConfig.Entry entry) {
        String name = entry.name() == null || entry.name().isEmpty() ? entry.id() : entry.name();
        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(false);
        input.setMinLines(4);
        if (Util.isLeanback()) input.setMaxLines(8);
        input.setText(PluginEnvironmentStore.get().get(entry.id()));

        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(getString(R.string.plugin_manager_params_hint));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(20);
        container.setPadding(horizontal, dp(8), horizontal, 0);
        container.addView(layout);

        androidx.appcompat.app.AlertDialog dialog = new WebHtvAlertDialogBuilder(this, R.style.Theme_WebHTV_Dialog)
                .setTitle(getString(R.string.plugin_manager_params_title, name))
                .setView(container)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!value.isEmpty() && !isJsonObject(value)) {
                        layout.setError(getString(R.string.plugin_manager_params_invalid));
                        if (Util.isLeanback()) input.requestFocus();
                        return;
                    }
                    PluginEnvironmentStore store = PluginEnvironmentStore.get();
                    if (!store.put(entry.id(), value)) {
                        layout.setError(getString(R.string.plugin_manager_params_too_large));
                        return;
                    }
                    dialog.dismiss();
                    // 参数改了要重装插件：脚本在 init 阶段就把 config 读走了，不重装等于没改。
                    scheduleReload();
                }));
        dialog.show();
        LightDialog.apply(dialog);
        if (Util.isLeanback()) wireParameterDialogFocus(dialog, input);
    }

    private static void wireParameterDialogFocus(androidx.appcompat.app.AlertDialog dialog, TextInputEditText input) {
        Button cancel = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        Button confirm = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        ThemeTokens tokens = ThemeController.current();
        int[][] states = {{-android.R.attr.state_enabled}, {android.R.attr.state_focused},
                {android.R.attr.state_pressed}, {}};
        ColorStateList background = new ColorStateList(states, new int[]{tokens.colorSurfaceContainerHighest(),
                tokens.colorPrimaryContainer(), tokens.colorPrimaryContainer(), Color.TRANSPARENT});
        ColorStateList text = new ColorStateList(states, new int[]{tokens.colorOnSurfaceVariant(),
                tokens.colorPrimary(), tokens.colorPrimary(), tokens.colorPrimary()});
        ColorStateList stroke = new ColorStateList(new int[][]{{android.R.attr.state_focused},
                {android.R.attr.state_pressed}, {}}, new int[]{tokens.colorOnPrimaryContainer(),
                tokens.colorOnPrimaryContainer(), Color.TRANSPARENT});
        for (Button action : new Button[]{cancel, confirm}) {
            action.setBackgroundTintList(background);
            action.setTextColor(text);
            if (action instanceof MaterialButton material) {
                material.setStrokeColor(stroke);
                material.setStrokeWidth(Math.round(input.getResources().getDisplayMetrics().density * 2));
            }
        }
        if (input.getId() == View.NO_ID) input.setId(View.generateViewId());
        input.setNextFocusDownId(cancel.getId());
        input.setNextFocusForwardId(cancel.getId());
        cancel.setNextFocusRightId(confirm.getId());
        confirm.setNextFocusLeftId(cancel.getId());
        cancel.setNextFocusUpId(input.getId());
        confirm.setNextFocusUpId(input.getId());
        cancel.setFocusableInTouchMode(true);
        confirm.setFocusableInTouchMode(true);
        input.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                    || !event.hasNoModifiers() || input.hasSelection()) return false;
            Layout textLayout = input.getLayout();
            if (textLayout == null) return false;
            int cursor = Math.max(0, input.getSelectionEnd());
            if (textLayout.getLineForOffset(cursor) < textLayout.getLineCount() - 1) return false;
            return cancel.requestFocus();
        });
        input.requestFocus();
    }

    private static boolean isJsonObject(String value) {
        try {
            JsonElement element = JsonParser.parseString(value);
            return element != null && element.isJsonObject();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void confirmDelete(PluginSourceConfig.Entry entry) {
        new WebHtvAlertDialogBuilder(this)
                .setTitle(R.string.plugin_manager_delete_title)
                .setMessage(getString(R.string.plugin_manager_delete_message, entry.name()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.setting_delete, (dialog, which) -> deletePlugin(entry))
                .show();
    }

    private void deletePlugin(PluginSourceConfig.Entry entry) {
        if (!beginBusy()) return;
        mWorker.execute(() -> {
            // 用单元素数组而不是局部变量：下面的 App.post lambda 要求捕获实际上的最终变量。
            String[] saveError = new String[1];
            try {
                PluginSourceConfig config = PluginSourceConfig.get();
                config.remove(entry.id());
                config.save();
                // 参数必须跟着插件一起删：留着的话，用户日后重新装同名 id 的插件会静默继承旧 token。
                PluginEnvironmentStore.get().remove(entry.id());
            } catch (Throwable error) {
                saveError[0] = error.toString();
            }
            // 配置删成功才删目录：反过来的话，删目录失败会留下一个指向空目录的条目。
            boolean removed = removePackageDir(entry.directory());
            PluginBootstrap.installAndReloadQuietly();
            Snapshot snapshot = readEntries();
            App.post(() -> {
                endBusy();
                if (!canTouchView()) return;
                applySnapshot(snapshot);
                if (saveError[0] != null) Notify.show(getString(R.string.plugin_manager_save_failed, saveError[0]));
                else if (!removed) Notify.show(getString(R.string.plugin_manager_delete_failed, entry.directory() == null ? "" : entry.directory()));
            });
        });
    }

    private void setEnabled(PluginSourceConfig.Entry entry, boolean enabled) {
        try {
            PluginSourceConfig config = PluginSourceConfig.get();
            config.setEnabled(entry.id(), enabled);
            config.save();
        } catch (Throwable error) {
            Notify.show(getString(R.string.plugin_manager_save_failed, error.toString()));
            // 写失败时开关已经翻过去了，重读一次把它拨回持久化的真实状态。
            refreshEntries();
            return;
        }
        scheduleReload();
    }

    /**
     * 排队一次"重装 + 重读"。
     *
     * <p>连点开关会连排多次重装，每次都要把启用清单重跑一遍脚本，代价明显；
     * 这里用一个排队标记把同一时刻的多余请求合并成一次。
     */
    private void scheduleReload() {
        synchronized (this) {
            if (mReloadQueued) return;
            if (!beginBusy()) return;
            mReloadQueued = true;
        }
        mWorker.execute(() -> {
            synchronized (PluginManagerActivity.this) {
                mReloadQueued = false;
            }
            PluginBootstrap.installAndReloadQuietly();
            Snapshot snapshot = readEntries();
            App.post(() -> {
                endBusy();
                applySnapshot(snapshot);
            });
        });
    }

    private void refreshEntries() {
        mWorker.execute(this::publishEntries);
    }

    private void publishEntries() {
        Snapshot snapshot = readEntries();
        App.post(() -> applySnapshot(snapshot));
    }

    private void applySnapshot(Snapshot snapshot) {
        if (!canTouchView()) return;
        bindEntries(snapshot.entries);
        if (snapshot.error != null) Notify.show(getString(R.string.plugin_manager_load_failed, snapshot.error));
    }

    private void bindEntries(List<PluginSourceConfig.Entry> entries) {
        mAdapter.submit(entries);
        boolean empty = entries.isEmpty();
        mBinding.emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        mBinding.pluginList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private boolean beginBusy() {
        if (mBusy) return false;
        mBusy = true;
        mBinding.importPlugin.setEnabled(false);
        mBinding.remoteImportPlugin.setEnabled(false);
        mBinding.reloadPlugins.setEnabled(false);
        updateBusyState();
        return true;
    }

    private void endBusy() {
        mBusy = false;
        if (!canTouchView()) return;
        mBinding.importPlugin.setEnabled(true);
        mBinding.remoteImportPlugin.setEnabled(true);
        mBinding.reloadPlugins.setEnabled(true);
        updateBusyState();
    }

    private void updateBusyState() {
        mBinding.emptyImport.setEnabled(!mBusy);
        View progress = mBinding.getRoot().findViewWithTag("pluginManagerProgress");
        if (progress != null) progress.setVisibility(mBusy ? View.VISIBLE : View.GONE);
        mAdapter.busy = mBusy;
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
    }

    private boolean canTouchView() {
        return !isFinishing() && !isDestroyed() && mBinding != null;
    }

    /**
     * 只删插件根目录下的条目目录。
     *
     * <p>条目里的 directory 来自 SharedPreferences，被改坏时可能指向任意路径；
     * 没有这道"必须在根目录之下"的判断，删一次插件就能删掉用户目录。
     * 根目录归属交给导入器判定：根目录名只有一处定义，它改名后这里不会悄悄失效。
     */
    private static boolean removePackageDir(String directory) {
        if (!PluginPackageImporter.isOwnedDirectory(directory)) return false;
        try {
            File target = new File(directory).getCanonicalFile();
            Path.clear(target);
            return !target.exists();
        } catch (Throwable error) {
            return false;
        }
    }

    /** 后台线程里读一次配置；失败只记一句话，不抛到界面。 */
    private static Snapshot readEntries() {
        try {
            return new Snapshot(new ArrayList<>(PluginSourceConfig.get().entries()), null);
        } catch (Throwable error) {
            return new Snapshot(Collections.emptyList(), error.toString());
        }
    }

    private static final class Snapshot {

        private final List<PluginSourceConfig.Entry> entries;
        private final String error;

        private Snapshot(List<PluginSourceConfig.Entry> entries, String error) {
            this.entries = entries;
            this.error = error;
        }
    }

    private static final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.Holder> {

        private final List<PluginSourceConfig.Entry> items = new ArrayList<>();
        private final Listener listener;
        private boolean busy;

        private EntryAdapter(Listener listener) {
            this.listener = listener;
        }

        private void submit(List<PluginSourceConfig.Entry> entries) {
            items.clear();
            items.addAll(entries);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(AdapterPluginEntryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PluginSourceConfig.Entry entry = items.get(position);
            Context context = holder.binding.getRoot().getContext();
            String name = entry.name() == null || entry.name().isEmpty() ? entry.id() : entry.name();
            String state = context.getString(entry.enabled() ? R.string.setting_enable : R.string.setting_disable);
            holder.binding.entryName.setText(name);
            holder.binding.entryMeta.setText(context.getString(R.string.plugin_manager_entry_meta, entry.version(), entry.runtimeApi(), state));
            // 先摘监听器再设状态：复用 ViewHolder 时旧的监听器会用上一条数据再切一次。
            holder.binding.entryEnabled.setOnCheckedChangeListener(null);
            holder.binding.entryEnabled.setChecked(entry.enabled());
            holder.binding.entryEnabled.setEnabled(!busy);
            holder.binding.entryParams.setEnabled(!busy);
            holder.binding.entryDelete.setEnabled(!busy);
            if (Util.isMobile()) {
                holder.binding.entryEnabled.setText(state);
                holder.binding.entryEnabled.setContentDescription(name + " · " + state);
                holder.binding.entryParams.setContentDescription(context.getString(R.string.plugin_manager_params) + " · " + name);
                holder.binding.entryDelete.setContentDescription(context.getString(R.string.setting_delete) + " · " + name);
            }
            holder.binding.entryEnabled.setOnCheckedChangeListener((button, checked) -> {
                if (!busy) listener.onToggle(entry, checked);
            });
            holder.binding.entryParams.setOnClickListener(view -> {
                if (!busy) listener.onParams(entry);
            });
            holder.binding.entryDelete.setOnClickListener(view -> {
                if (!busy) listener.onDelete(entry);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private interface Listener {

            void onToggle(PluginSourceConfig.Entry entry, boolean enabled);

            void onParams(PluginSourceConfig.Entry entry);

            void onDelete(PluginSourceConfig.Entry entry);
        }

        private static final class Holder extends RecyclerView.ViewHolder {

            private final AdapterPluginEntryBinding binding;

            private Holder(AdapterPluginEntryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    /**
     * 候选插件的勾选适配器。
     *
     * <p>勾选状态按 {@code entryPath} 记：它是 zip 内目录，天然唯一；用位置记的话，滚动回收后
     * 同一个 ViewHolder 会带着上一行的状态回来。默认全选——用户刚指了一个包，最常见的意思是
     * 把整包装上，但每条仍然可以单独取消。
     *
     * <p>整行可聚焦、整行点击切换。勾选框本身 {@code focusable=false}：遥控器上如果它自己
     * 拿焦点，方向键就会在"行"与"框"之间反复跳，而按一次只翻一行——这正是项目里常见的
     * list-select 写法（行负责焦点，框只显示状态）。
     */
    private final class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.Holder> {

        private final List<PluginBundleIndex.Candidate> items;
        private final Set<String> checked = new HashSet<>();
        private Runnable onCheckedChanged;

        private CandidateAdapter(List<PluginBundleIndex.Candidate> candidates) {
            this.items = new ArrayList<>(candidates);
            for (PluginBundleIndex.Candidate candidate : items) checked.add(candidate.entryPath());
        }

        private int checkedCount() {
            return checked.size();
        }

        private boolean isAllChecked() {
            return !items.isEmpty() && checked.size() == items.size();
        }

        private void setAllChecked(boolean value) {
            checked.clear();
            if (value) for (PluginBundleIndex.Candidate candidate : items) checked.add(candidate.entryPath());
        }

        /** 已勾选的候选，顺序与列表一致：导入顺序稳定，用户看到的行序就是安装顺序。 */
        private List<PluginBundleIndex.Candidate> checkedCandidates() {
            List<PluginBundleIndex.Candidate> selected = new ArrayList<>(checked.size());
            for (PluginBundleIndex.Candidate candidate : items) {
                if (checked.contains(candidate.entryPath())) selected.add(candidate);
            }
            return selected;
        }

        private void toggle(int position) {
            String key = items.get(position).entryPath();
            if (!checked.remove(key)) checked.add(key);
            notifyItemChanged(position);
            if (onCheckedChanged != null) onCheckedChanged.run();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemPluginCandidateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PluginBundleIndex.Candidate candidate = items.get(position);
            Context context = holder.binding.getRoot().getContext();
            // entryPath 只是 zip 内定位标识（如 repo-main/douban），不能当插件名展示。
            String name = candidate.name() == null || candidate.name().isEmpty() ? candidate.id() : candidate.name();
            boolean isChecked = checked.contains(candidate.entryPath());
            holder.binding.candidateName.setText(name);
            holder.binding.candidateMeta.setText(context.getString(
                    R.string.plugin_manager_remote_candidate_meta, candidate.version(), candidate.runtimeApi()));
            holder.binding.candidatePermissions.setText(permissionSpans(candidate.permissions()));
            holder.binding.candidateCheck.setChecked(isChecked);
            holder.binding.getRoot().setOnClickListener(view -> {
                // 绑定位置在行被回收后会变成 NO_POSITION；按它取值会越界，所以先问一次。
                int current = holder.getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return;
                toggle(current);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static final class Holder extends RecyclerView.ViewHolder {

            private final ItemPluginCandidateBinding binding;

            private Holder(ItemPluginCandidateBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
