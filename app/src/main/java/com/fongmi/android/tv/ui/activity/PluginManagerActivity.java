package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityPluginManagerBinding;
import com.fongmi.android.tv.databinding.AdapterPluginEntryBinding;
import com.fongmi.android.tv.plugin.PluginBootstrap;
import com.fongmi.android.tv.plugin.PluginPackageImporter;
import com.fongmi.android.tv.plugin.PluginSourceConfig;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        mBinding.toolbar.setNavigationOnClickListener(view -> finish());
        mAdapter = new EntryAdapter(new EntryAdapter.Listener() {
            @Override
            public void onToggle(PluginSourceConfig.Entry entry, boolean enabled) {
                setEnabled(entry, enabled);
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
        mBinding.reloadPlugins.setOnClickListener(view -> scheduleReload());
    }

    @Override
    protected void onDestroy() {
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

    private void confirmDelete(PluginSourceConfig.Entry entry) {
        new MaterialAlertDialogBuilder(this)
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
            mReloadQueued = true;
        }
        mWorker.execute(() -> {
            synchronized (PluginManagerActivity.this) {
                mReloadQueued = false;
            }
            PluginBootstrap.installAndReloadQuietly();
            publishEntries();
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
        mBinding.reloadPlugins.setEnabled(false);
        return true;
    }

    private void endBusy() {
        mBusy = false;
        if (!canTouchView()) return;
        mBinding.importPlugin.setEnabled(true);
        mBinding.reloadPlugins.setEnabled(true);
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
            holder.binding.entryEnabled.setOnCheckedChangeListener((button, checked) -> listener.onToggle(entry, checked));
            holder.binding.entryDelete.setOnClickListener(view -> listener.onDelete(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private interface Listener {

            void onToggle(PluginSourceConfig.Entry entry, boolean enabled);

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
}
