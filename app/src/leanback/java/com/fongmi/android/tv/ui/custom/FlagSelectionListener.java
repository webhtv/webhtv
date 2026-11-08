package com.fongmi.android.tv.ui.custom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;

public final class FlagSelectionListener extends OnChildViewHolderSelectedListener implements Runnable {

    private final HorizontalGridView view;
    private final FlagAdapter adapter;
    private final FlagAdapter.OnClickListener listener;
    private Flag pendingItem;
    private Flag dispatchedItem;

    public FlagSelectionListener(HorizontalGridView view, FlagAdapter adapter, FlagAdapter.OnClickListener listener) {
        this.view = view;
        this.adapter = adapter;
        this.listener = listener;
    }

    @Override
    public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
        view.removeCallbacks(this);
        Flag item = position >= 0 && position < adapter.getItemCount() ? adapter.get(position) : null;
        // 只有「选到另一条线路」才结束回声窗口：之后哪怕再选回原来那条，也必须正常派发。
        // 行刷新途中会短暂派发 NO_POSITION（item == null），那不算换成别的线路；若也拿它清窗口，
        // 同一条线路的自刷新就能再次进来，回声会重新接上。
        if (item != null && item != dispatchedItem) dispatchedItem = null;
        pendingItem = item;
        // Leanback may dispatch selection inside layout. The whole route switch can
        // refresh this grid through focus changes and seamless episode selection.
        if (pendingItem != null) view.post(this);
    }

    @Override
    public void run() {
        if (!view.isAttachedToWindow() || view.getAdapter() != adapter) {
            pendingItem = null;
            return;
        }
        if (view.isComputingLayout()) {
            view.postOnAnimation(this);
            return;
        }
        Flag item = pendingItem;
        pendingItem = null;
        int position = view.getSelectedPosition();
        // A newer selection or a refreshed route list makes the queued item stale.
        if (item != null && position >= 0 && position < adapter.getItemCount() && adapter.get(position) == item) {
            // 选中回声：线路切换自身就会重设选中位（setSelectedPosition / notifyItemsChanged /
            // setEpisodeAdapter -> updateFocus -> notifyDataSetChanged），Leanback 于是再派发一次
            // 同一条线路的选中，把 onItemClick 又拉回来，形成 200ms 级自激切换循环
            // （日志表现为 from==to 反复刷屏、选集选中位被反复重置）。
            // 只在「没有选过别的线路」时拦下这一次回声；用户改选另一条线路后
            // dispatchedItem 已被清空，再选回原线路仍会正常派发。
            // 点击（getRoot() 的 OnClickListener）不走这里，永远不会被拦。
            if (item == dispatchedItem) return;
            dispatchedItem = item;
            listener.onItemClick(item);
        }
    }
}
