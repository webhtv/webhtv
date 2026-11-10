package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;

import com.fongmi.android.tv.utils.KeyUtil;

public class CustomLiveListView extends VerticalGridView {

    private Callback listener;
    private EdgeListener edgeListener;

    public CustomLiveListView(@NonNull Context context) {
        super(context);
    }

    public CustomLiveListView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomLiveListView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setListener(Callback listener) {
        this.listener = listener;
    }

    /**
     * [NEW] 设置了边缘监听后，列表不再在头尾之间循环回绕，
     * 而是把「已经在最上面还往上」「已经在最下面还往下」交给外部处理（例如节目单跨天续接）。
     */
    public void setEdgeListener(EdgeListener edgeListener) {
        this.edgeListener = edgeListener;
    }

    private int getCount() {
        return getAdapter() == null ? 0 : getAdapter().getItemCount();
    }

    private boolean onKeyDown() {
        if (edgeListener != null) {
            if (getCount() == 0) return true;
            if (getSelectedPosition() < getCount() - 1) return false;
            return edgeListener.onEdgeDown();
        }
        if (getSelectedPosition() != getCount() - 1) return false;
        setSelectedPosition(0);
        return true;
    }

    private boolean onKeyUp() {
        if (edgeListener != null) {
            if (getSelectedPosition() > 0) return false;
            return edgeListener.onEdgeUp();
        }
        if (getSelectedPosition() != 0) return false;
        setSelectedPosition(getCount() - 1);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (getVisibility() == View.GONE || event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        if (getVisibility() == View.VISIBLE && listener != null) listener.setUITimer();
        if (KeyUtil.isDownKey(event)) return onKeyDown();
        if (KeyUtil.isUpKey(event)) return onKeyUp();
        return super.dispatchKeyEvent(event);
    }

    public interface Callback {

        void setUITimer();
    }

    /**
     * [NEW] 头尾边界按键回调，返回 true 表示事件已消费（焦点保持不动或由外部重新定位）。
     */
    public interface EdgeListener {

        boolean onEdgeUp();

        boolean onEdgeDown();
    }
}
