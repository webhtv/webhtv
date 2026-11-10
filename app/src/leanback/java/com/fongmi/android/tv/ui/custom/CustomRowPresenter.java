package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.view.KeyEvent;
import android.view.View;

import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class CustomRowPresenter extends ListRowPresenter {

    public interface OnHorizontalEdgeListener {

        void onHorizontalEdge(boolean towardEnd);
    }

    private final int spacing;
    private final int strategy;
    private final boolean keepFocusInRow;
    private final OnHorizontalEdgeListener edgeListener;

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

    @Override
    @SuppressLint("RestrictedApi")
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);
        ViewHolder vh = (ViewHolder) holder;
        HorizontalGridView grid = vh.getGridView();
        grid.setFocusScrollStrategy(strategy);
        grid.setHorizontalSpacing(ResUtil.dp2px(spacing));
    }
}
