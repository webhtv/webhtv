package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.FocusRestorer;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.ViewingReportRangeDialog;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;
    private final FocusRestorer mFocusRestorer = new FocusRestorer();

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
    }

   @Override
   protected void initView(Bundle savedInstanceState) {
       setEmptyContent();
       setRecyclerView();
       getHistory();
        mBinding.deleteButton.setOnClickListener(v -> onDelete());
       mBinding.reportButton.setOnClickListener(v -> onReport());
   }

   /**
    * 空态内容：设计文档 §9.9 要求「标题 + 解释 + 一个主动作」。
    *
    * <p>改动前这一页只显示布局里的默认单行文案 {@code @string/error_empty}
    * （「这里什麽都没有 👀」），既不解释为什么空，也不给出下一步——与电视端收藏页
    * 此前的问题相同，只是当时只接了收藏页。
    */
   private void setEmptyContent() {
       mBinding.progressLayout.setEmptyContent(
               R.string.history_empty_title,
               R.string.history_empty_description,
               R.string.history_empty_action,
               view -> goHome());
   }

   /** 空态主按钮：回点播首页去挑一部。与收藏页/播放页的回首页写法一致。 */
   private void goHome() {
       startActivity(new Intent(this, HomeActivity.class).addFlags(
               Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
   }

   private void onReport() {
       ViewingReportRangeDialog.create(this)
               .callback(range -> ViewingReportActivity.start(this, range))
               .show();
   }

    private void onDelete() {
        if (mAdapter.isDelete()) {
            new WebHtvAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_record)
                .setMessage(Setting.isGlobalHistoryEnabled() ? R.string.dialog_delete_global_history : R.string.dialog_delete_history)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear())
                .show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        }
    }

   private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        // 列表数量变化后统一驱动状态机（BaseDiffAdapter 的钩子）：注册一次，之后无论从哪条
        // 路径改动列表（单条删除、删除全部、刷新）状态都会跟着列表走，宿主不可能再忘。
        mAdapter.setOnCountChanged(count -> mBinding.progressLayout.showContent(true, count));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getHistory() {
        int position = focusedPosition();
        History focused = position < 0 || position >= mAdapter.getItemCount() ? null : mAdapter.getItem(position);
        mAdapter.setItems(History.getForDisplay(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
            else if (focused != null) restoreFocus(reposition(focused, position));
        });
    }

    // 刷新会按 createTime 重排，优先按条目身份重新定位；条目已不在新列表里（被别处删掉）
    // 就退回原位置，让焦点留在附近而不是掉给右上角的删除按钮
    private int reposition(History focused, int position) {
        int found = mAdapter.getItems().indexOf(focused);
        if (found >= 0) return found;
        return FocusRestorer.nextPosition(position, mAdapter.getItemCount(), Product.getColumn());
    }

    private int focusedPosition() {
        if (isFinishing() || isDestroyed() || !mBinding.recycler.hasFocus()) return RecyclerView.NO_POSITION;
        View focus = mBinding.recycler.getFocusedChild();
        return focus == null ? RecyclerView.NO_POSITION : mBinding.recycler.getChildAdapterPosition(focus);
    }

    // 只在列表原本持有焦点时恢复：条目被移除时 RecyclerView 已 clearFocus，
    // 焦点被窗口兜底给了右上角的删除按钮，需要主动交回列表
    private void restoreFocus(int position) {
        if (isFinishing() || isDestroyed()) return;
        mFocusRestorer.restore(mBinding.recycler, position);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.isVod() && mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.HISTORY) getHistory();
    }

    @Override
    public void onItemClick(History item) {
        HistoryResumeCoordinator.open(this, item);
    }

    @Override
    public void onItemDelete(History item, int position) {
        // diff 挂起时 holder 的绑定下标会失效，退回当前聚焦位置（遥控下点击必然带焦点）
        int target = position >= 0 ? position : focusedPosition();
        mAdapter.remove(item.deleteDisplayItem(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
            else restoreFocus(FocusRestorer.nextPosition(target, mAdapter.getItemCount(), Product.getColumn()));
        });
    }

    @Override
    public boolean onLongClick() {
        mFocusRestorer.cancel();
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    protected void onStop() {
        mFocusRestorer.cancel();
        super.onStop();
    }

    @Override
    protected void onBackInvoked() {
        mFocusRestorer.cancel();
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }

}
