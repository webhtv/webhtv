package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogUaBinding;
import com.fongmi.android.tv.impl.DanmakuListener;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DanmakuApiDialog extends BaseAlertDialog {

    private DialogUaBinding binding;

    public static void show(Fragment fragment) {
        new DanmakuApiDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogUaBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.danmaku_api).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        String text;
        // 只回填真实配置过的地址：用 getEffectiveApiUrl() 会把本地包运行时算出来的回环地址
        // 显示在输入框里，用户不改直接确定就把它固化进 prefs。
        binding.text.setText(text = DanmakuSetting.getConfiguredApiUrl());
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void initEvent() {
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((DanmakuListener) requireParentFragment()).setDanmakuApi(binding.text.getText().toString().trim());
        dismiss();
    }
}