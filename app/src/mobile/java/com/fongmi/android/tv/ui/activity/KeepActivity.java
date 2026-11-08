package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.fongmi.android.tv.utils.Notify;

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
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        setEmptyContent();
        setRecyclerView();
        getKeep();
    }

    /**
     * 空态内容：设计文档 §9.9 要求「标题 + 解释 + 一个主动作」。
     *
     * <p>收藏页是最典型的空态——用户第一次进来时列表必为空。改动前只有一句
     * {@code error_empty}（「这里什麽都没有 👀」），既不解释为什么空，也不给出下一步。
     */
    private void setEmptyContent() {
        mBinding.progressLayout.setEmptyContent(
                R.string.keep_empty_title,
                R.string.keep_empty_description,
                R.string.keep_empty_action,
                view -> goHome());
    }

    /** 空态主按钮：回点播首页去挑一部。与 LiveActivity/VideoActivity 的回首页写法一致。 */
    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        // 列表数量变化后统一驱动状态机（BaseDiffAdapter 的钩子）：注册一次，之后无论从哪条
        // 路径改动列表（单条删除、删除全部、刷新）状态都会跟着列表走，宿主不可能再忘。
        mAdapter.setOnCountChanged(count -> mBinding.progressLayout.showContent(true, count));
        mBinding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                recyclerView.post(() -> {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) updateMarquee();
                    else mAdapter.setMarqueeRange(RecyclerView.NO_POSITION, RecyclerView.NO_POSITION);
                });
            }
        });
        mAdapter.setSize(Product.getSpec(this));
    }

    private void getKeep() {
        mAdapter.setItems(Keep.getVod(), () -> {
            mBinding.recycler.post(this::updateMarquee);
        });
    }

    private void updateMarquee() {
        if (mBinding.recycler.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            mAdapter.setMarqueeRange(RecyclerView.NO_POSITION, RecyclerView.NO_POSITION);
            return;
        }
        int[] range = findMarqueeRange(mBinding.recycler);
        mAdapter.setMarqueeRange(range[0], range[1]);
    }

    private int[] findMarqueeRange(RecyclerView recyclerView) {
        int first = RecyclerView.NO_POSITION;
        int last = RecyclerView.NO_POSITION;
        int top = recyclerView.getPaddingTop();
        int bottom = recyclerView.getHeight() - recyclerView.getPaddingBottom();
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            View info = child.findViewById(R.id.history_info);
            int infoTop = child.getTop() + info.getTop();
            int infoBottom = child.getTop() + info.getBottom();
            if (infoBottom <= top || infoTop >= bottom) continue;
            int position = recyclerView.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;
            first = first == RecyclerView.NO_POSITION ? position : Math.min(first, position);
            last = Math.max(last, position);
        }
        return new int[]{first, last};
    }

    private void onSync() {
        SyncDialog.create().keep().show(this);
    }

    private void onDelete() {
        if (mAdapter.isDelete()) {
            new WebHtvAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear()).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        }
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
                // 改动前这里只弹一个会消失的吐司，页面没有任何变化。重试复用同一条加载路径，
                // 所以重试成功与首次成功的行为完全一致。
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
        if (event.getType().equals(RefreshEvent.Type.KEEP)) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) SearchActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mAdapter.remove(item.delete(), () -> {
            // 状态由 BaseDiffAdapter 的 setOnCountChanged 钩子统一驱动，这里不再手写——
            // 「手写」正是此前漏掉删除全部那条路径的原因。
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
            mBinding.recycler.post(this::updateMarquee);
        });
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_keep, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.delete) onDelete();
        else if (item.getItemId() == R.id.sync) onSync();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
}
