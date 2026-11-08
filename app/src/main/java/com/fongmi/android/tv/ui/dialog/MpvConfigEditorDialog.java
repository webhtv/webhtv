package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMpvConfigEditorBinding;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MpvConfigEditorDialog extends BaseAlertDialog {

    public interface Listener {
        boolean onSave(String content);
    }

    private DialogMpvConfigEditorBinding binding;
    private String configName;
    private String content;
    private boolean creating;
    private Listener listener;

    public static void show(FragmentManager manager, String name, String content, boolean creating, Listener listener) {
        MpvConfigEditorDialog dialog = new MpvConfigEditorDialog();
        dialog.configName = name;
        dialog.content = content;
        dialog.creating = creating;
        dialog.listener = listener;
        dialog.show(manager, "mpv-config-editor");
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMpvConfigEditorBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new WebHtvAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_Dialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.title.setText(creating ? R.string.mpv_config_editor_new : R.string.mpv_config_editor_edit);
        binding.name.setText(configName);
        binding.editor.setText(content == null ? "" : content);
        binding.editor.setSelection(0);
        updateStats(binding.editor.length() == 0 ? "" : binding.editor.getText().toString());
        setupTvFocus();
    }

    private void setupTvFocus() {
        if (!Util.isLeanback()) return;
        tvFocusable(binding.back);
        tvFocusable(binding.save);
        tvFocusable(binding.editor);
        binding.back.setNextFocusRightId(R.id.save);
        binding.back.setNextFocusDownId(R.id.editor);
        binding.save.setNextFocusLeftId(R.id.back);
        binding.save.setNextFocusDownId(R.id.editor);
        binding.editor.setNextFocusUpId(R.id.back);
    }

    private static void tvFocusable(android.view.View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
    }

    @Override
    protected void initEvent() {
        binding.back.setOnClickListener(view -> dismiss());
        binding.save.setOnClickListener(view -> save());
        binding.editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateStats(s == null ? "" : s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void updateStats(String text) {
        int lines = text.isEmpty() ? 1 : text.split("\\r?\\n", -1).length;
        binding.stats.setText(getString(R.string.mpv_config_stats, lines, text.length()));
    }

    private void save() {
        String text = binding.editor.getText() == null ? "" : binding.editor.getText().toString();
        if (listener == null || listener.onSave(text)) dismissAllowingStateLoss();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        boolean land = ResUtil.isLand(requireContext());
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (DialogSafeArea.width(requireActivity(), window) * (land ? 0.72f : 0.96f));
        params.height = (int) (DialogSafeArea.height(requireActivity(), window) * (land ? 0.9f : 0.88f));
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
        if (rootParams != null) {
            rootParams.height = params.height;
            binding.getRoot().setLayoutParams(rootParams);
        }
        binding.getRoot().post(() -> window.setLayout(params.width, params.height));
        if (Util.isLeanback()) binding.back.post(() -> binding.back.requestFocus());
        else if (creating) binding.editor.requestFocus();
    }
}
