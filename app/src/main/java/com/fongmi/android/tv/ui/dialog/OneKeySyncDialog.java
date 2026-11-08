package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.SyncOptions;
import com.fongmi.android.tv.databinding.DialogOneKeySyncBinding;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.fongmi.android.tv.ui.adapter.SyncDeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.MpvConfigSync;
import com.fongmi.android.tv.utils.ProgressRequestBody;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.SyncFiles;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class OneKeySyncDialog extends BaseBottomSheetDialog implements SyncDeviceAdapter.OnClickListener, ScanTask.Listener, NsdDeviceDiscovery.Listener {

    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY = 600;
    private static final MediaType ZIP = MediaType.parse("application/zip");
    private static final String TAG = "one_key_sync";

    private final OkHttpClient client;

    private DialogOneKeySyncBinding binding;
    private SyncDeviceAdapter adapter;
    private ScanTask scanTask;
    private NsdDeviceDiscovery discovery;
    private Device selected;
    private Call syncCall;
    /**
     * 同步代际：每次 {@link #startSync()} 自增，回调执行前比对。
     *
     * <p>取消/失败的回调都是异步投递的（OkHttp 线程 → {@code App.post}）。若用户在上一条
     * post 尚未执行时就重新点了「开始同步」，旧回调会把**新一次同步**的界面复位：进度清零、
     * 按钮文案回「开始同步」、方向按钮被重新启用。代际不相等即丢弃陈旧回调。
     *
     * <p>写入只在主线程（{@code startSync()} 由点击回调触发），但读取发生在回调线程：
     * {@code Task.execute} 的固定线程池、OkHttp 的 Dispatcher 线程。因此必须 volatile——
     * 同文件的 {@code syncing} 出于同样的原因已经是 volatile。
     */
    private volatile long syncGeneration;
    private long progressStart;
    private long prepareStart;
    private long prepareUpdate;
    private volatile boolean syncing;
    private boolean toRemote = true;
    private boolean scanning;
    private boolean focusFirstOnNextDevice;

    public OneKeySyncDialog() {
        client = OkHttp.client(Constant.TIMEOUT_SYNC_TRANSFER);
        scanTask = new ScanTask(this);
        discovery = new NsdDeviceDiscovery(this);
    }

    public static OneKeySyncDialog create() {
        return new OneKeySyncDialog();
    }

    public void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof OneKeySyncDialog) return;
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) == null) showNow(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogOneKeySyncBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        Server.get().start();
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter = new SyncDeviceAdapter(this));
        updateDevice(binding.localName, binding.localHost, binding.localIcon, Device.get());
        updateDirection();
        getDevice();
    }

    @Override
    protected void initEvent() {
        binding.directionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            setToRemote(checkedId == R.id.directionPush);
        });
        binding.refresh.setOnClickListener(v -> refresh());
        binding.changeDevice.setOnClickListener(v -> changeDevice());
        binding.selectAll.setOnClickListener(v -> toggleSelection());
        binding.spider.setOnCheckedChangeListener((buttonView, isChecked) -> updateSyncPathsVisible());
        binding.syncPaths.setOnClickListener(v -> SyncPathDialog.show(this, this::updateSyncPathSummary));
        binding.start.setOnClickListener(v -> {
            if (syncing) cancelSync();
            else startSync();
        });
    }

    private void getDevice() {
        restartScan();
        adapter.clear(() -> {
            updateVisible();
            scan();
        });
    }

    private void refresh() {
        restartScan();
        selected = null;
        binding.options.setVisibility(View.GONE);
        binding.actionBar.setVisibility(View.GONE);
        adapter.clear(() -> {
            scan();
        });
    }

    private void changeDevice() {
        restartScan();
        selected = null;
        binding.options.setVisibility(View.GONE);
        binding.actionBar.setVisibility(View.GONE);
        focusFirstOnNextDevice = true;
        updateVisible();
        focusFirstDevice();
        scan();
    }

    private void scan() {
        focusFirstOnNextDevice = true;
        setScanning(true);
        discovery.start();
        scanTask.start();
    }

    private void restartScan() {
        discovery.stop();
        scanTask.stop();
        scanTask = new ScanTask(this);
        setScanning(false);
    }

    private void setToRemote(boolean value) {
        if (toRemote == value) return;
        toRemote = value;
        updateDirection();
    }

    private void updateDirectionGroup() {
        int checked = toRemote ? R.id.directionPush : R.id.directionPull;
        if (binding.directionGroup.getCheckedButtonId() != checked) binding.directionGroup.check(checked);
    }

    private void toggleSelection() {
        select(!allSelected());
    }

    private void select(boolean checked) {
        for (MaterialCheckBox box : boxes()) box.setChecked(checked);
        updateSelectButton();
        updateSyncPathsVisible();
    }

    private boolean allSelected() {
        for (MaterialCheckBox box : boxes()) if (!box.isChecked()) return false;
        return true;
    }

    private MaterialCheckBox[] boxes() {
        return new MaterialCheckBox[]{binding.config, binding.spider, binding.mpvConfig, binding.loginState, binding.webHome, binding.search, binding.history, binding.keep, binding.follow, binding.settings, binding.remoteRelay};
    }

    private void updateSyncPathSummary() {
        binding.syncPathsSummary.setText(SyncFiles.getPathsText(SyncFiles.getPaths(Setting.getSyncPaths())));
    }

    private void updateSyncPathsVisible() {
        binding.syncPaths.setVisibility(binding.spider.isChecked() ? View.VISIBLE : View.GONE);
    }

    private SyncOptions options() {
        return SyncOptions.defaults()
                .config(binding.config.isChecked())
                .spider(binding.spider.isChecked())
                .mpvConfig(binding.mpvConfig.isChecked())
                .loginState(binding.loginState.isChecked())
                .webHome(binding.webHome.isChecked())
                .search(binding.search.isChecked())
                .history(binding.history.isChecked())
                .keep(binding.keep.isChecked())
                .follow(binding.follow.isChecked())
                .settings(binding.settings.isChecked())
                .remoteRelay(binding.remoteRelay.isChecked())
                .paths(Setting.getSyncPaths());
    }

    private void updateVisible() {
        if (selected != null) {
            binding.recycler.setVisibility(View.GONE);
            binding.status.setVisibility(View.GONE);
            return;
        }
        binding.recycler.setVisibility(adapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
        if (adapter.getItemCount() > 0) {
            binding.status.setVisibility(View.GONE);
        } else {
            binding.status.setVisibility(View.VISIBLE);
            binding.status.setText(scanning ? R.string.sync_discovering : R.string.sync_no_device);
        }
    }

    private void setScanning(boolean scanning) {
        this.scanning = scanning;
        updateVisible();
    }

    private void setSelected(Device device) {
        stopScan();
        selected = device;
        binding.options.setVisibility(View.VISIBLE);
        binding.actionBar.setVisibility(View.VISIBLE);
        updateDevice(binding.remoteName, binding.remoteHost, binding.remoteIcon, device);
        updateSyncPathSummary();
        updateSyncPathsVisible();
        updateDirection();
        updateSelectButton();
        updateVisible();
        binding.start.requestFocus();
    }

    private void updateDevice(TextView name, TextView host, ImageView icon, Device device) {
        name.setText(device.getName());
        host.setText(device.getHost());
        icon.setImageResource(device.isLeanback() ? R.drawable.ic_sync_tv : R.drawable.ic_sync_phone);
    }

    private void updateDirection() {
        binding.selected.setText(toRemote ? R.string.sync_mode_to_remote : R.string.sync_mode_from_remote);
        binding.mode.setText(toRemote ? R.string.sync_push : R.string.sync_pull);
        binding.mode.setCompoundDrawablesWithIntrinsicBounds(0, toRemote ? R.drawable.ic_sync_arrow_right_blue : R.drawable.ic_sync_arrow_left_blue, 0, 0);
        updateDirectionGroup();
        updateDirectionCard(binding.localCard, binding.localRole, toRemote);
        updateDirectionCard(binding.remoteCard, binding.remoteRole, !toRemote);
        binding.localRole.setText(toRemote ? R.string.sync_sender : R.string.sync_receiver);
        binding.remoteRole.setText(toRemote ? R.string.sync_receiver : R.string.sync_sender);
    }

    private void updateSelectButton() {
        binding.selectAll.setText(allSelected() ? R.string.sync_select_none : R.string.sync_select_all);
    }

    /**
     * 高亮"发送方"卡片，另一张退回布局的静态样式。
     *
     * <p>颜色必须走主题 token，不能用裸 hex：布局里两张卡片本来就是
     * {@code ?attr/colorSurfaceContainerLow} + {@code ?attr/colorOutlineVariant}，
     * 用固定浅色 hex 覆盖会让深色/自定义主题下的方向卡仍是浅底深字。
     * 这里用 M3 的成对语义色（容器色 + 其 on 色），对比度由主题契约保证。
     */
    private void updateDirectionCard(MaterialCardView card, TextView role, boolean sender) {
        ThemeTokens tokens = ThemeController.current();
        card.setCardBackgroundColor(sender ? tokens.colorPrimaryContainer() : tokens.colorSurfaceContainerLow());
        card.setStrokeColor(sender ? Color.TRANSPARENT : tokens.colorOutlineVariant());
        card.setStrokeWidth(sender ? 0 : ResUtil.dp2px(1));
        role.setTextColor(sender ? tokens.colorOnPrimaryContainer() : tokens.colorOnSurfaceVariant());
    }

    @Override
    public void onFind(Device device) {
        if (!isRemoteApp(device)) return;
        adapter.sort(device, () -> {
            updateVisible();
            focusFirstDevice();
        });
    }

    @Override
    public void onLost(Device device) {
        adapter.remove(device, this::updateVisible);
    }

    private boolean isRemoteApp(Device device) {
        return device != null && device.isApp() && !Device.get().equals(device);
    }

    @Override
    public void onFinish() {
        setScanning(false);
    }

    @Override
    public void onServiceFound(String url) {
        scanTask.start(url);
    }

    private void stopScan() {
        discovery.stop();
        scanTask.stop();
        scanTask = new ScanTask(this);
        setScanning(false);
    }

    private void focusFirstDevice() {
        if (!focusFirstOnNextDevice || selected != null || adapter.getItemCount() == 0) return;
        focusFirstOnNextDevice = false;
        binding.recycler.scrollToPosition(0);
    }

    @Override
    public void onItemClick(Device item) {
        setSelected(item);
    }

    private void startSync() {
        if (selected == null) {
            Notify.show(R.string.sync_select_device_first);
            return;
        }
        SyncOptions options = options();
        // 方向必须在入口快照：toRemote 由主线程的方向选择器改写，而下面的工作线程要读它
        // 决定请求体。若不快照，同步中切换方向会让 URL 的 mode 与请求体（backup/device）
        // 指向相反方向。
        final boolean toRemote = this.toRemote;
        // 代际同样在入口快照：陈旧回调靠它在执行前丢弃，不会复位新一次同步的界面。
        final long generation = ++syncGeneration;
        String mode = toRemote ? "1" : "2";
        String url = String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=backup", selected.getIp(), mode);
        binding.start.setEnabled(false);
        setSyncing(true);
        updateProgress(0, toRemote ? R.string.sync_prepare_files : R.string.sync_wait_remote);
        prepareStart = System.currentTimeMillis();
        prepareUpdate = 0;
        Task.execute(() -> {
            try {
                SyncFiles.Archive archive = toRemote && SyncFiles.hasPaths(options) ? SyncFiles.createArchive(SyncFiles.getPaths(options), () -> syncing && generation == syncGeneration, (count, size) -> onPrepareProgress(generation, count, size)) : null;
                MpvConfigSync.Archive mpvArchive = toRemote && options.isMpvConfig() ? MpvConfigSync.createArchive() : null;
                LoginStateSync.Archive loginArchive = toRemote && options.isLoginState() ? LoginStateSync.createArchive() : null;
                RequestBody body = buildBody(options, toRemote, archive, mpvArchive, loginArchive, generation);
                // 代际必须一起判：取消后立刻重开时 syncing 已是新一次同步的 true，
                // 只判 syncing 会让这条陈旧的准备线程继续把上一轮的 body 真发出去，
                // 并在 request() 里覆盖 syncCall（此后点「取消」取消的是错的 Call）。
                if (!syncing || generation != syncGeneration) {
                    if (archive != null) archive.delete();
                    if (mpvArchive != null) mpvArchive.delete();
                    if (loginArchive != null) loginArchive.delete();
                    return;
                }
                App.post(() -> binding.start.setEnabled(true));
                request(url, body, archive, mpvArchive, loginArchive, 0, generation);
            } catch (Exception e) {
                if (!syncing || generation != syncGeneration) return;
                App.post(() -> {
                    if (generation != syncGeneration) return;
                    setSyncing(false);
                    Notify.show(getString(R.string.sync_failed_with_reason, e.getMessage()));
                });
            }
        });
    }

    private RequestBody buildBody(SyncOptions options, boolean toRemote, SyncFiles.Archive archive, MpvConfigSync.Archive mpvArchive, LoginStateSync.Archive loginArchive, long generation) {
        if (archive == null && mpvArchive == null && loginArchive == null) {
            FormBody.Builder body = new FormBody.Builder();
            body.add("options", options.toString());
            body.add("force", "false");
            if (toRemote) body.add("backup", Backup.create(options).toString());
            else body.add("device", Device.get().toString());
            addRemoteRelay(body, options, toRemote);
            return body.build();
        }
        App.post(() -> updatePrepare(generation, archive, loginArchive));
        MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
        body.addFormDataPart("options", options.toString());
        body.addFormDataPart("force", "false");
        body.addFormDataPart("backup", Backup.create(options).toString());
        if (options.isRemoteRelay()) body.addFormDataPart("remoteRelay", RemoteStore.exportRelayConfig());
        if (archive != null) body.addFormDataPart(SyncFiles.PART_NAME, archive.getFile().getName(), new ProgressRequestBody(archive.getFile(), ZIP, (written, total) -> App.post(() -> updateUpload(generation, archive, written, total))));
        if (mpvArchive != null) body.addFormDataPart(MpvConfigSync.PART_NAME, mpvArchive.getFile().getName(), new ProgressRequestBody(mpvArchive.getFile(), ZIP, null));
        if (loginArchive != null) body.addFormDataPart(LoginStateSync.PART_NAME, loginArchive.getFile().getName(), new ProgressRequestBody(loginArchive.getFile(), ZIP, null));
        return body.build();
    }

    private void addRemoteRelay(FormBody.Builder body, SyncOptions options, boolean includeLocal) {
        if (options.isRemoteRelay() && includeLocal) body.add("remoteRelay", RemoteStore.exportRelayConfig());
    }

    private void request(String url, RequestBody body, SyncFiles.Archive archive, MpvConfigSync.Archive mpvArchive, LoginStateSync.Archive loginArchive, int retry, long generation) {
        syncCall = OkHttp.newCall(client, url, body);
        syncCall.enqueue(callback(url, body, archive, mpvArchive, loginArchive, retry, generation));
    }

    private void retry(String url, RequestBody body, SyncFiles.Archive archive, MpvConfigSync.Archive mpvArchive, LoginStateSync.Archive loginArchive, int retry, long generation, String msg) {
        if (generation != syncGeneration || !syncing) {
            if (archive != null) archive.delete();
            if (mpvArchive != null) mpvArchive.delete();
            if (loginArchive != null) loginArchive.delete();
            return;
        }
        if (retry >= MAX_RETRY) {
            if (archive != null) archive.delete();
            if (mpvArchive != null) mpvArchive.delete();
            if (loginArchive != null) loginArchive.delete();
            App.post(() -> {
                if (generation != syncGeneration) return;
                setSyncing(false);
                Notify.show(getString(R.string.sync_failed_with_reason, msg));
            });
        } else {
            Task.schedule(() -> {
                // 延迟重试也要复核代际：这段时间里用户可能已取消并重新开始同步，
                // 此时重发上一轮的请求会与新同步并发，并劫持 syncCall。
                if (generation != syncGeneration || !syncing) {
                    if (archive != null) archive.delete();
                    if (mpvArchive != null) mpvArchive.delete();
                    if (loginArchive != null) loginArchive.delete();
                    return;
                }
                request(url, body, archive, mpvArchive, loginArchive, retry + 1, generation);
            }, RETRY_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private Callback callback(String url, RequestBody body, SyncFiles.Archive archive, MpvConfigSync.Archive mpvArchive, LoginStateSync.Archive loginArchive, int retry, long generation) {
        return new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled() || !syncing) {
                    if (archive != null) archive.delete();
                    if (mpvArchive != null) mpvArchive.delete();
                    if (loginArchive != null) loginArchive.delete();
                    App.post(() -> {
                        if (generation != syncGeneration) return;
                        setSyncing(false);
                        Notify.show(R.string.sync_canceled);
                    });
                    return;
                }
                retry(url, body, archive, mpvArchive, loginArchive, retry, generation, e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response res = response) {
                    if (res.isSuccessful()) {
                        // 临时归档必须无条件清理：陈旧回调也要删，否则会留下本次同步的文件。
                        if (archive != null) archive.delete();
                        if (mpvArchive != null) mpvArchive.delete();
                        if (loginArchive != null) loginArchive.delete();
                        App.post(() -> {
                            if (generation != syncGeneration) return;
                            setSyncing(false);
                            Notify.show(R.string.sync_success);
                            dismiss();
                        });
                    }
                    else retry(url, body, archive, mpvArchive, loginArchive, retry, generation, res.message());
                }
            }
        };
    }

    private void setSyncing(boolean syncing) {
        this.syncing = syncing;
        binding.progressWrap.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.progressBox.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.start.setText(syncing ? R.string.sync_cancel : R.string.sync_start);
        binding.start.setEnabled(true);
        // 同步中锁住方向：请求的方向已在 startSync 入口快照，此时翻方向不会改变
        // 正在跑的这次同步，只会让界面显示的方向与实际请求相反。
        // 注意 ViewGroup.setEnabled 不会传播到子 View，必须逐个按钮设。
        binding.directionPush.setEnabled(!syncing);
        binding.directionPull.setEnabled(!syncing);
        if (!syncing) {
            syncCall = null;
            binding.progress.setProgress(0);
            binding.progressText.setText("");
        }
    }

    private void cancelSync() {
        syncing = false;
        if (syncCall != null) syncCall.cancel();
        else {
            setSyncing(false);
            Notify.show(R.string.sync_canceled);
        }
    }

    private void updatePrepare(long generation, SyncFiles.Archive archive, LoginStateSync.Archive loginArchive) {
        if (!syncing || generation != syncGeneration || binding == null) return;
        progressStart = System.currentTimeMillis();
        binding.progress.setProgress(100);
        int count = (archive == null ? 0 : archive.getCount()) + (loginArchive == null ? 0 : loginArchive.getCount());
        long rawSize = (archive == null ? 0 : archive.getRawSize()) + (loginArchive == null ? 0 : loginArchive.getRawSize());
        long zipSize = (archive == null ? 0 : archive.getZipSize()) + (loginArchive == null ? 0 : loginArchive.getZipSize());
        binding.progressText.setText(getString(R.string.sync_upload_prepare, count, FileUtil.byteCountToDisplaySize(rawSize), FileUtil.byteCountToDisplaySize(zipSize)));
    }

    private void onPrepareProgress(long generation, int count, long size) {
        long now = System.currentTimeMillis();
        if (now - prepareUpdate < 350) return;
        prepareUpdate = now;
        long elapsed = Math.max(now - prepareStart, 1);
        long speed = size * 1000 / elapsed;
        App.post(() -> updatePrepareProgress(generation, count, size, speed));
    }

    private void updatePrepareProgress(long generation, int count, long size, long speed) {
        if (!syncing || generation != syncGeneration || binding == null) return;
        binding.progress.setProgress(0);
        binding.progressText.setText(getString(R.string.sync_prepare_progress, count, FileUtil.byteCountToDisplaySize(size), FileUtil.byteCountToDisplaySize(speed)));
    }

    private void updateUpload(long generation, SyncFiles.Archive archive, long written, long total) {
        if (!syncing || generation != syncGeneration || binding == null) return;
        long elapsed = Math.max(System.currentTimeMillis() - progressStart, 1);
        long speed = written * 1000 / elapsed;
        int percent = total <= 0 ? 0 : (int) Math.min(100, written * 100 / total);
        binding.progress.setProgress(percent);
        binding.progressText.setText(getString(R.string.sync_upload_progress, percent, FileUtil.byteCountToDisplaySize(written), FileUtil.byteCountToDisplaySize(total), FileUtil.byteCountToDisplaySize(speed), archive.getCount()));
    }

    private void updateProgress(int progress, int text) {
        binding.progress.setProgress(progress);
        binding.progressText.setText(getString(text));
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        super.setBehavior(dialog);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        int maxHeight = (int) (ResUtil.getScreenHeight(requireContext()) * 0.9f);
        sheet.getLayoutParams().height = maxHeight;
        sheet.requestLayout();
        BottomSheetBehavior.from(sheet).setPeekHeight(maxHeight);
    }

    @Override
    public void onDestroyView() {
        // 关掉对话框同样作废代际：在途回调随后会走到 App.post 里，而那时 binding 已经销毁，
        // 继续 setSyncing(...) 会碰已解绑的视图。代际不等即丢弃，归档仍照常清理。
        ++syncGeneration;
        if (syncCall != null) syncCall.cancel();
        syncing = false;
        super.onDestroyView();
        discovery.stop();
        scanTask.stop();
    }
}
