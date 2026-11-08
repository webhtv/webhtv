package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public abstract class BaseBottomSheetDialog extends BottomSheetDialogFragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            if (!isAdded() || getContext() == null) return;
            setBehavior(dialog);
        });
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        if (stableOverlay()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setDimAmount(0f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            WindowCompat.setDecorFitsSystemWindows(window, true);
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (Util.isFullscreen(getActivity())) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        bindDialogTheme();
        initEvent();
    }

    private void bindDialogTheme() {
        Dialog dialog = getDialog();
        if (dialog == null) return;
        ThemeController.bindDialog(dialog);
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected boolean transparent() {
        return false;
    }

    protected boolean stableOverlay() {
        return false;
    }

    protected void setBehavior(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.setClickable(true);
        sheet.setFocusable(true);
        if (transparent()) {
            int color = ResUtil.getColor(R.color.transparent);
            sheet.setBackgroundColor(color);
            if (sheet.getParent() instanceof View) ((View) sheet.getParent()).setBackgroundColor(color);
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }
}
