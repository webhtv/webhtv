package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogLiveEpgBinding;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.LiveEpgSetting;
import com.fongmi.android.tv.ui.adapter.LiveEpgAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
 
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
 
public class LiveEpgDialog extends BaseBottomSheetDialog implements LiveEpgAdapter.OnClickListener {

    private DialogLiveEpgBinding binding;
    private LiveEpgAdapter adapter;
    private String editingUrl;

    public static LiveEpgDialog create() {
        return new LiveEpgDialog();
    }

    public LiveEpgDialog show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof LiveEpgDialog) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
        if (Util.isLeanback()) EventBus.getDefault().register(this);
    }
 
    @Override
    public void onStop() {
        super.onStop();
        if (Util.isLeanback()) EventBus.getDefault().unregister(this);
    }
 
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        binding.input.setText(event.text());
        binding.input.setSelection(binding.input.length());
        addInput();
    }
 
    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogLiveEpgBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter = new LiveEpgAdapter(this));
        if (Util.isLeanback()) {
            binding.qrSection.setVisibility(View.VISIBLE);
            binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(4), 200, 0));
            binding.info.setText(ResUtil.getString(com.fongmi.android.tv.R.string.push_info, Server.get().getAddress()).replace("\uff0c", "\n"));
        }
        binding.input.requestFocus();
    }

    @Override
    protected void initEvent() {
        binding.clear.setOnClickListener(v -> onFollowSource());
        binding.add.setOnClickListener(v -> addInput());
        binding.input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addInput();
                return true;
            }
            return false;
        });
    }

    /** 随源：清空所有勾选，使用直播源自带 EPG */
    private void onFollowSource() {
        LiveEpgSetting.clearSelected();
        adapter.reload();
        notifyChanged();
    }
 
    private void addInput() {
        String url = binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
        if (url.isEmpty()) {
            Notify.show(com.fongmi.android.tv.R.string.live_epg_empty);
            return;
        }
        if (editingUrl != null) {
            LiveEpgSetting.replaceHistory(editingUrl, url);
            editingUrl = null;
            binding.add.setText(com.fongmi.android.tv.R.string.live_epg_apply);
        } else {
            LiveEpgSetting.addHistory(url);
        }
        LiveEpgSetting.addSelected(url);
        adapter.reload();
        binding.input.setText("");
        notifyChanged();
    }

    private void notifyChanged() {
        ((Listener) requireActivity()).onLiveEpgSelected();
    }

    @Override
    public void onEpgClick(String url) {
        LiveEpgSetting.toggleSelected(url);
        adapter.reload();
        notifyChanged();
    }

    @Override
    public void onEpgEdit(String url) {
        editingUrl = url;
        binding.input.setText(url);
        binding.input.setSelection(binding.input.length());
        binding.add.setText(com.fongmi.android.tv.R.string.live_epg_save);
        binding.input.requestFocus();
    }

    @Override
    public void onEpgDelete(String url) {
        if (url.equals(editingUrl)) {
            editingUrl = null;
            binding.input.setText("");
            binding.add.setText(com.fongmi.android.tv.R.string.live_epg_apply);
        }
        LiveEpgSetting.removeHistory(url);
        adapter.reload();
        notifyChanged();
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, true);
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    @Override
    protected boolean stableOverlay() {
        return true;
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.setBackgroundColor(ResUtil.getColor(com.fongmi.android.tv.R.color.transparent));
        int height = getPanelHeight();
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = height;
        sheet.setLayoutParams(params);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setPeekHeight(height);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(false);
    }

    private int getPanelHeight() {
        int screen = ResUtil.getScreenHeight(requireContext());
        if (ResUtil.isLand(requireContext())) return Math.max(ResUtil.dp2px(260), Math.min(ResUtil.dp2px(430), Math.round(screen * 0.76f)));
        return Math.max(ResUtil.dp2px(330), Math.min(ResUtil.dp2px(560), Math.round(screen * 0.58f)));
    }

    public interface Listener {

        void onLiveEpgSelected();
    }
}
