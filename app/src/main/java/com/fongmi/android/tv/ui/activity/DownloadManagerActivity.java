package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.databinding.ActivityDownloadManagerBinding;
import com.fongmi.android.tv.download.DownloadManager;
import com.fongmi.android.tv.download.DownloadTask;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.DownloadAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;

import java.io.File;
import java.util.List;

/** 下载管理界面：任务列表（暂停/继续/重试/取消/删除）+ 存储路径设置；由状态栏下载通知点击进入。 */
public class DownloadManagerActivity extends BaseActivity implements DownloadAdapter.OnClickListener {

    private static final String TAG = "DownloadManagerActivity";

    public static void start(Context context) {
        context.startActivity(new Intent(context, DownloadManagerActivity.class));
    }
 
    private final ActivityResultLauncher<Intent> dirLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                String path = FileChooser.getPathFromUri(uri);
                if (path == null || path.isEmpty()) return;
                Setting.setDownloadDir(path);
                Notify.show(R.string.download_manager_path_changed);
                updatePath();
            });

    private ActivityDownloadManagerBinding mBinding;
    private DownloadAdapter mAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshList();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDownloadManagerBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        setTitle(R.string.download_manager_title);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new DownloadAdapter(this));
        mBinding.pathRow.setOnClickListener(v -> FileChooser.from(dirLauncher).showDirectory());
        updatePath();
        refreshList();
    }

    private void updatePath() {
        mBinding.path.setText(Setting.getDownloadDir());
    }

    private void refreshList() {
        List<DownloadTask> tasks = DownloadManager.get().getAllTasks();
        mAdapter.setItems(tasks);
        mBinding.empty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    @Override
    public void onPause(DownloadTask task) {
        DownloadManager.get().pauseTask(task.id);
        refreshList();
    }
 
    @Override
    public void onResume(DownloadTask task) {
        DownloadManager.get().resumeTask(task.id);
        refreshList();
    }
 
    @Override
    public void onRetry(DownloadTask task) {
        DownloadManager.get().retryTask(task.id);
        refreshList();
    }
 
    @Override
    public void onCancel(DownloadTask task) {
        DownloadManager.get().cancelTask(task.id);
        refreshList();
    }

    @Override
    public void onRemove(DownloadTask task) {
        DownloadManager.get().removeTask(task.id);
        refreshList();
    }

    /** 播放成品。 */
    @Override
    public void onPlay(DownloadTask task) {
        File file = DownloadAdapter.outputFile(task);
        if (file == null) {
            // 成品被外部删掉了：刷新列表让播放按钮消失，别再弹「下载失败」
            refreshList();
            return;
        }
        try {
            startVideo(file);
        } catch (Throwable e) {
            Log.e(TAG, "play failed", e);
            if (!playExternal(file)) Notify.show(Notify.getError(R.string.play, e));
        }
    }

    /** 直接按 VideoActivity.start() 的 Intent 协议拉起播放页。
     *
     *  这里不能用反射调 VideoActivity.file()：release 包开了 R8，
     *  file() 的方法名会被混淆掉 —— Class.forName 还能找到类（manifest 声明的 Activity
     *  会被 aapt 的 keep 规则保留），但 getDeclaredMethod("file") 必然 NoSuchMethodException，
     *  于是走进 catch 弹出「下载失败」。这就是点了播放却提示下载失败的原因。
     *  改为只依赖类名（被 manifest keep）+ extras 的字符串 key（字面量不参与混淆），
     *  debug / release 都能用，mobile 与 leanback 两个 flavor 的 extras 协议一致。
     *
     *  id 传 "file://绝对路径" 而不是直接把路径丢给播放器：VideoActivity 内部会经
     *  SiteApi.toLanHttpUrl() 换算成本机代理地址 http://192.168.x.x:port/file/...，
     *  这样局域网设备也拉得到流；用 127.0.0.1 只能本机播。 */
    private void startVideo(File file) throws Exception {
        Intent intent = new Intent();
        intent.setClassName(this, "com.fongmi.android.tv.ui.activity.VideoActivity");
        intent.putExtra("key", SiteApi.PUSH);
        intent.putExtra("id", "file://" + file.getAbsolutePath());
        intent.putExtra("name", file.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** 兜底：交给系统播放器（走 FileProvider，不需要读权限）。 */
    private boolean playExternal(File file) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "external play failed", e);
            return false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        return super.onOptionsItemSelected(item);
    }
}
