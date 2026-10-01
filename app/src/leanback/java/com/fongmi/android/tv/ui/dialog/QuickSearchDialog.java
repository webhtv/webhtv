package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.DialogQuickSearchBinding;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class QuickSearchDialog extends BaseAlertDialog implements QuickAdapter.OnClickListener {

    private static final int RENDER_BATCH_SIZE = 8;

    private final List<Vod> pending;
    private DialogQuickSearchBinding binding;
    private QuickAdapter.OnClickListener listener;
    private DialogInterface.OnDismissListener dismissListener;
    private QuickAdapter adapter;
    private boolean firstItemFocused;
    private boolean draining;
    private int panelWidth;
    private int progressCurrent;
    private int progressTotal;
    private boolean searchFinished;
    private String currentSiteKey = "";

    public QuickSearchDialog() {
        pending = new ArrayList<>();
    }

    public static QuickSearchDialog create() {
        return new QuickSearchDialog();
    }

    public QuickSearchDialog listener(QuickAdapter.OnClickListener listener) {
        this.listener = listener;
        return this;
    }

    public QuickSearchDialog currentSiteKey(String currentSiteKey) {
        this.currentSiteKey = currentSiteKey == null ? "" : currentSiteKey;
        if (adapter != null) adapter.setCurrentSiteKey(this.currentSiteKey);
        return this;
    }

    public QuickSearchDialog dismissListener(DialogInterface.OnDismissListener dismissListener) {
        this.dismissListener = dismissListener;
        return this;
    }

    public QuickSearchDialog items(List<Vod> items) {
        if (!items.isEmpty()) pending.addAll(items);
        return this;
    }

    public void addAll(List<Vod> items) {
        if (items.isEmpty()) return;
        pending.addAll(items);
        updateLoading();
        drainPending();
    }

    public void setProgress(int current, int total, boolean finished) {
        progressTotal = Math.max(0, total);
        progressCurrent = Math.max(0, Math.min(current, progressTotal));
        searchFinished = finished || progressCurrent >= progressTotal;
        updateProgress();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogQuickSearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        panelWidth = Math.round(ResUtil.getScreenWidth() * 0.42f);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter = new QuickAdapter(this));
        adapter.setCurrentSiteKey(currentSiteKey);
        adapter.setWidth(panelWidth - ResUtil.dp2px(32));
        adapter.setNextFocus(0, 0);
        updateProgress();
        drainPending();
        binding.loading.requestFocus();
    }

    private void drainPending() {
        if (adapter == null || binding == null || draining) return;
        draining = true;
        binding.recycler.post(this::drainNextBatch);
    }

    private void drainNextBatch() {
        if (adapter == null || binding == null) {
            draining = false;
            return;
        }
        if (pending.isEmpty()) {
            draining = false;
            updateLoading();
            return;
        }
        int start = adapter.getItemCount();
        int count = Math.min(RENDER_BATCH_SIZE, pending.size());
        List<Vod> items = new ArrayList<>(pending.subList(0, count));
        pending.subList(0, count).clear();
        if (start == 0) {
            binding.loading.setVisibility(View.GONE);
            binding.recycler.setVisibility(View.VISIBLE);
        }
        adapter.addAll(items);
        if (start == 0 && !firstItemFocused) {
            firstItemFocused = true;
            binding.recycler.post(() -> focusPosition(0));
        }
        if (pending.isEmpty()) draining = false;
        else binding.recycler.postDelayed(this::drainNextBatch, 16);
        updateLoading();
    }

    private void updateProgress() {
        if (binding == null) return;
        binding.status.setText(getString(R.string.search_progress, progressCurrent, progressTotal));
        updateLoading();
    }

    private void updateLoading() {
        if (binding == null || adapter == null) return;
        boolean empty = adapter.getItemCount() == 0 && pending.isEmpty();
        binding.loading.setText(getString(searchFinished && empty ? R.string.search_no_result : R.string.search_loading));
        if (empty) {
            binding.loading.setVisibility(View.VISIBLE);
            binding.recycler.setVisibility(View.GONE);
        }
    }

    private void focusPosition(int position) {
        if (adapter == null || adapter.getItemCount() <= position) return;
        binding.recycler.scrollToPosition(position);
        RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(position);
        if (holder != null) holder.itemView.requestFocus();
        else binding.recycler.requestFocus();
    }

    @Override
    public void onItemClick(Vod item) {
        dismiss();
        if (listener != null) listener.onItemClick(item);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        pending.clear();
        draining = false;
        if (dismissListener != null) dismissListener.onDismiss(dialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable android.os.Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setWindowAnimations(0);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.getDecorView().setPadding(0, 0, 0, 0);
        clearParentPaddingAndFillHeight();
        window.setGravity(Gravity.END | Gravity.BOTTOM);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = panelWidth;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.x = 0;
        params.y = 0;
        window.setAttributes(params);
        window.setLayout(panelWidth, WindowManager.LayoutParams.MATCH_PARENT);
        binding.getRoot().post(() -> {
            clearParentPaddingAndFillHeight();
            window.setLayout(panelWidth, WindowManager.LayoutParams.MATCH_PARENT);
        });
    }

    private void clearParentPaddingAndFillHeight() {
        View view = binding.getRoot();
        fillHeight(view);
        while (view.getParent() instanceof View parent) {
            if (parent instanceof ViewGroup group) group.setPadding(0, 0, 0, 0);
            fillHeight(parent);
            view = parent;
        }
    }

    private void fillHeight(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(params);
        }
        view.setMinimumHeight(ResUtil.getScreenHeight(requireContext()));
    }
}
