package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMpvConfigRenameBinding;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MpvConfigRenameDialog extends BaseAlertDialog {

    public interface Listener {
        boolean onRename(String name);
    }

    private DialogMpvConfigRenameBinding binding;
    private Listener listener;
    private String name;

    public static void show(FragmentManager manager, String name, Listener listener) {
        MpvConfigRenameDialog dialog = new MpvConfigRenameDialog();
        dialog.name = name;
        dialog.listener = listener;
        dialog.show(manager, "mpv-config-rename");
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMpvConfigRenameBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new WebHtvAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_Dialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.name.setText(name);
        binding.name.setSelection(binding.name.length());
        setupTvFocus();
    }

    private void setupTvFocus() {
        if (!Util.isLeanback()) return;
        tvFocusable(binding.close);
        tvFocusable(binding.name);
        tvFocusable(binding.cancel);
        tvFocusable(binding.rename);
        binding.close.setNextFocusDownId(R.id.name);
        binding.name.setNextFocusUpId(R.id.close);
        binding.name.setNextFocusDownId(R.id.cancel);
        binding.cancel.setNextFocusUpId(R.id.name);
        binding.cancel.setNextFocusRightId(R.id.rename);
        binding.rename.setNextFocusUpId(R.id.name);
        binding.rename.setNextFocusLeftId(R.id.cancel);
    }

    private static void tvFocusable(android.view.View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> dismiss());
        binding.cancel.setOnClickListener(view -> dismiss());
        binding.rename.setOnClickListener(view -> rename());
        binding.name.setOnEditorActionListener((view, actionId, event) -> {
            rename();
            return true;
        });
    }

    private void rename() {
        String value = binding.name.getText() == null ? "" : binding.name.getText().toString().trim();
        if (TextUtils.isEmpty(value)) {
            binding.nameLayout.setError(getString(R.string.mpv_config_name_required));
            binding.name.requestFocus();
            return;
        }
        binding.nameLayout.setError(null);
        if (listener == null || listener.onRename(value)) dismissAllowingStateLoss();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.min((int) (ResUtil.getScreenWidth(requireContext()) * 0.9f), ResUtil.dp2px(480));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        if (Util.isLeanback()) binding.name.post(() -> binding.name.requestFocus());
    }
}
