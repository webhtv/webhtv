package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.view.KeyEvent;
import android.view.View;

import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.utils.ResUtil;

public class CustomRowPresenter extends ListRowPresenter {

    public interface OnHorizontalEdgeListener {

        void onHorizontalEdge(boolean towardEnd);
    }

    private final int spacing;
    private final int strategy;
    private final boolean keepFocusInRow;
    private final OnHorizontalEdgeListener edgeListener;
    private boolean wrapFocus;

    public CustomRowPresenter(int spacing) {
        this(spacing, false);
    }

    public CustomRowPresenter(int spacing, boolean keepFocusInRow) {
        this(spacing, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ITEM, keepFocusInRow, null);
    }

    public CustomRowPresenter(int spacing, OnHorizontalEdgeListener edgeListener) {
        this(spacing, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ITEM, true, edgeListener);
    }

    @SuppressLint("RestrictedApi")
    public CustomRowPresenter(int spacing, int focusZoomFactor) {
        this(spacing, focusZoomFactor, HorizontalGridView.FOCUS_SCROLL_ITEM, false, null);
    }

    public CustomRowPresenter(int spacing, int focusZoomFactor, int strategy) {
        this(spacing, focusZoomFactor, strategy, false, null);
    }

    public CustomRowPresenter(int spacing, int focusZoomFactor, int strategy, boolean keepFocusInRow) {
        this(spacing, focusZoomFactor, strategy, keepFocusInRow, null);
    }

    private CustomRowPresenter(int spacing, int focusZoomFactor, int strategy, boolean keepFocusInRow, OnHorizontalEdgeListener edgeListener) {
        super(focusZoomFactor);
        this.spacing = spacing;
        this.strategy = strategy;
        this.keepFocusInRow = keepFocusInRow;
        this.edgeListener = edgeListener;
        setShadowEnabled(false);
        setSelectEffectEnabled(false);
        setKeepChildForeground(false);
    }

    /**
     * 行内按到最左/最右时环绕到另一端。必须在行首次绑定前调用：{@link #initializeRowViewHolder} 只读一次。
     * 同时开启 keepFocusInRow 时环绕优先，该行的 edgeListener 不再收到回调。
     */
    public CustomRowPresenter setWrapFocus(boolean wrapFocus) {
        this.wrapFocus = wrapFocus;
        return this;
    }

    @Override
    @SuppressLint("RestrictedApi")
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);
        ViewHolder vh = (ViewHolder) holder;
        HorizontalGridView grid = vh.getGridView();
        grid.setFocusScrollStrategy(strategy);
        grid.setHorizontalSpacing(ResUtil.dp2px(spacing));
        if (keepFocusInRow || wrapFocus) grid.setOnKeyInterceptListener(event -> onKeyIntercept(grid, event));
    }

    private boolean onKeyIntercept(HorizontalGridView grid, KeyEvent event) {
        if (!isHorizontalEdge(grid, event)) return false;
        boolean towardEnd = isTowardEnd(grid, event);
        if (wrapFocus) return wrapToEdge(grid, towardEnd, event);
        if (event.getAction() == KeyEvent.ACTION_DOWN && edgeListener != null) edgeListener.onHorizontalEdge(towardEnd);
        return true;
    }

    // 目标项可能尚未布局（行宽超出屏幕）：手写逐步 scrollBy + 重试会在失败时静默吞掉按键，
    // 因此改为交给 grid 自己滚到目标位置、由 attach 回调换焦（仓库既有写法，如 CollectActivity
    // 搜索结果回到首项）；项数不足时返回 false 放行，保持该行在改动前的默认按键行为。
    private boolean wrapToEdge(HorizontalGridView grid, boolean towardEnd, KeyEvent event) {
        RecyclerView.Adapter<?> adapter = grid.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 1) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int target = towardEnd ? 0 : adapter.getItemCount() - 1;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(target);
        // 目标项已在场就不需要滚动：换焦失败必须放行。落到下面的 setSelectedPosition 只会对同一个
        // itemView 再请求一次焦点（同步执行），结果仍是失败，却已经把这次按键消费掉了。
        if (holder != null) return holder.itemView.requestFocus();
        grid.setSelectedPosition(target, attached -> {
            if (attached != null) attached.itemView.requestFocus();
        });
        return true;
    }

    private boolean isHorizontalEdge(HorizontalGridView grid, KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        RecyclerView.Adapter<?> adapter = grid.getAdapter();
        int position = grid.getSelectedPosition();
        if (adapter == null || position == RecyclerView.NO_POSITION || adapter.getItemCount() == 0) return false;
        return isTowardEnd(grid, event) ? position == adapter.getItemCount() - 1 : position == 0;
    }

    private boolean isTowardEnd(HorizontalGridView grid, KeyEvent event) {
        boolean rtl = grid.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        return event.getKeyCode() == (rtl ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
    }
}
