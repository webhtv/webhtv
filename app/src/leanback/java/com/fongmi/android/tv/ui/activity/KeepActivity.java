package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.BaseGlassDialog;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setEmptyContent();
        setRecyclerView();
        setTitleBar();
        getKeep();
    }

    private void setTitleBar() {
        mBinding.btnDelete.setOnClickListener(v -> {
            if (mAdapter.getItemCount() > 0) mAdapter.setDelete(true);
        });
        mBinding.btnClear.setOnClickListener(v -> {
            if (mAdapter.getItemCount() > 0) showClearDialog();
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        // 列表数量变化后统一驱动状态机（BaseDiffAdapter 的钩子）：注册一次，之后无论从哪条
        // 路径改动列表（单条删除、删除全部、刷新）状态都会跟着列表走，宿主不可能再忘。
        mAdapter.setOnCountChanged(count -> mBinding.progressLayout.showContent(true, count));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getKeep() {
        mAdapter.setItems(Keep.getVod(), () -> {
            mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
            focusFirstItem();
        });
    }

    private void focusFirstItem() {
        if (mAdapter.getItemCount() <= 0) return;
        mBinding.recycler.post(() -> {
            View view = mBinding.recycler.getLayoutManager().findViewByPosition(0);
            if (view != null) view.requestFocus();
            else mBinding.recycler.requestFocus();
        });
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                // 设计文档 §9.9：错误态要给出「错误色 + 原因 + 重试动作」。
                // 改动前这里只弹一个会消失的吐司，页面没有任何变化——用户既不知道原因会
                // 停留多久，也没有重试入口（只能再点一次卡片，但卡片看起来毫无反应）。
                // 重试直接复用同一条加载路径，所以重试成功与首次成功的行为完全一致。
                mBinding.progressLayout.showError(
                        R.string.error_config_load,
                        msg,
                        R.string.error_retry,
                        view -> {
                            mBinding.progressLayout.showProgress();
                            loadConfig(config, item);
                        });
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.KEEP) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mAdapter.remove(item.delete(), () -> {
            // 状态由 BaseDiffAdapter 的 setOnCountChanged 钩子统一驱动，这里不再手写——
            // 「手写」正是此前漏掉删除全部那条路径的原因。
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(true);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event) && mAdapter.getItemCount() > 0) showClearDialog();
        return super.dispatchKeyEvent(event);
    }

    private void showClearDialog() {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("清空收藏视频")
                .setMessage("是否清空收藏视频，删除后无法恢复！")
                .setPositiveButton(android.R.string.ok, (d, which) -> clearKeep())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(BaseGlassDialog.glassBackground());
            View content = w.getDecorView().findViewById(android.R.id.content);
            if (content != null) content.setBackground(null);
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).requestFocus();
    }

    private void clearKeep() {
        Keep.deleteAll();
        RefreshEvent.keep();
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
}
