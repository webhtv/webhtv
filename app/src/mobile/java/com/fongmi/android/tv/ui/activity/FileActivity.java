package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFileBinding;
import com.fongmi.android.tv.ui.adapter.FileAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.web.WebReaderActivity;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileActivity extends BaseActivity implements FileAdapter.OnClickListener {

    private ActivityFileBinding mBinding;
    private FileAdapter mAdapter;
    private File dir;
    private boolean selectDir;
    private boolean readMode;

    private boolean isRoot() {
        return Path.root().equals(dir);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFileBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("");
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        selectDir = getIntent().getBooleanExtra("select_dir", false);
        readMode = getIntent().getBooleanExtra("read_mode", false);
        setSupportActionBar(mBinding.toolbar);
        setEmptyContent();
        setRecyclerView();
        checkPermission();
    }

    /**
     * 空态内容：设计文档 §9.9 要求「标题 + 解释 + 一个主动作」。
     *
     * <p><b>主动作选「重新扫描」而不是「返回上级」</b>：根目录没有上级（返回会退出页面，
     * 与按钮文案不符），而"重新扫描当前目录"在任何目录都成立，且能反映外部变化
     * （刚用别的应用放进来的文件）——这正是本地文件浏览器最需要的能力。
     *
     * <p>重新扫描复用 {@link #update(File)}：它是本页**唯一**的列表填充入口，既重算
     * {@link #list(File)} 又驱动状态机，所以"扫描后仍为空"会正确地停在空态而不是留白。
     */
    private void setEmptyContent() {
        mBinding.progressLayout.setEmptyContent(
                R.string.file_empty_title,
                R.string.file_empty_description,
                R.string.file_empty_action,
                view -> rescan());
    }

    /** 重新扫描当前目录；尚未拿到目录时（权限回调之前）退回根目录。 */
    private void rescan() {
        update(dir == null ? Path.root() : dir);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setAdapter(mAdapter = new FileAdapter(this));
        mBinding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                mAdapter.scheduleWindowUpdate(recyclerView);
            }
        });
    }

    private void checkPermission() {
        PermissionUtil.requestFile(this, allGranted -> update(Path.root()));
    }

    private void update(File dir) {
        mBinding.recycler.scrollToPosition(0);
        mAdapter.addAll(this.dir = dir, list(dir), selectDir);
        mBinding.title.setText(dir.getAbsolutePath());
        mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
    }

    private List<File> list(File dir) {
        if (!selectDir) return Path.list(dir);
        File[] files = dir.listFiles(File::isDirectory);
        if (files == null) return new ArrayList<>();
        Path.sort(files);
        return Arrays.asList(files);
    }

    @Override
    public void onItemClick(File file) {
        if (file.isDirectory()) {
            update(file);
        } else if (readMode && isReadable(file)) {
            startReader(file);
        } else {
            setResult(RESULT_OK, new Intent().setData(Uri.fromFile(file)));
            finish();
        }
    }

    private boolean isReadable(File file) {
        String n = file.getName().toLowerCase();
        return n.endsWith(".txt") || n.endsWith(".html") || n.endsWith(".htm")
                || n.endsWith(".epub") || n.endsWith(".zip") || n.endsWith(".pdf")
                || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    private void startReader(File file) {
        Intent intent = new Intent(this, WebReaderActivity.class);
        intent.putExtra(WebReaderActivity.EXTRA_LOCAL_PATH, file.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onCurrentDirClick(File dir) {
        if (dir == null) return;
        setResult(RESULT_OK, new Intent().setData(Uri.fromFile(dir)));
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (isRoot()) {
            super.onBackInvoked();
        } else {
            update(dir.getParentFile());
        }
    }
}
