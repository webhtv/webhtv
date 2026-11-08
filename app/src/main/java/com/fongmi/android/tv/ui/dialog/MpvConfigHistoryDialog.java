package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.adapter.MpvConfigHistoryAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MpvConfigHistoryDialog extends BaseAlertDialog implements MpvConfigHistoryAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private MpvConfigHistoryAdapter adapter;
    private Listener listener;
    private String target;

    public static void show(FragmentManager manager, String target, Listener listener) {
        MpvConfigHistoryDialog dialog = new MpvConfigHistoryDialog();
        dialog.target = target;
        dialog.listener = listener;
        dialog.show(manager, null);
    }

    public interface Listener {

        void onHistoryClick(int position);

        void onHistoryChanged();
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        MaterialAlertDialogBuilder builder = Util.isLeanback() ? builder() : new WebHtvAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_Dialog);
        return builder.setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new MpvConfigHistoryAdapter(MpvConfigStore.historyLabels(target), this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, Util.isLeanback() ? 16 : 8));
        binding.recycler.setAdapter(adapter);
    }

    @Override
    public void onTextClick(int position) {
        if (listener != null) listener.onHistoryClick(position);
        dismiss();
    }

    @Override
    public void onDeleteClick(int position) {
        if (!MpvConfigStore.removeHistory(target, position)) return;
        adapter.remove(position);
        if (listener != null) listener.onHistoryChanged();
        if (adapter.getItemCount() == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) {
            dismiss();
        } else if (Util.isLeanback()) {
            setWidth(0.4f);
        } else {
            configureWindow();
        }
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.5f : 0.84f)), ResUtil.dp2px(520));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
