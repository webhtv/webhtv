package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogPlaybackSettingBinding;
import com.fongmi.android.tv.ui.fragment.SettingPlayerFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * 播放页里的「播放设置」半透明面板：内容直接复用设置页的 {@link SettingPlayerFragment}，
 * 不重做界面，也不跳转 Activity，关掉面板就回到播放画面（不会闪一下播放页）。
 */
public class PlaybackSettingDialog extends BaseBottomSheetDialog {

    private static final String TAG = "PlaybackSettingDialog";
    private DialogPlaybackSettingBinding binding;
    private Runnable onDismissListener;

    public static boolean show(FragmentActivity activity, Runnable onDismiss) {
        if (activity == null || activity.getSupportFragmentManager().isStateSaved()) return false;
        FragmentManager manager = activity.getSupportFragmentManager();
        Fragment existing = manager.findFragmentByTag(TAG);
        if (existing instanceof PlaybackSettingDialog) return false;
        PlaybackSettingDialog dialog = new PlaybackSettingDialog();
        dialog.onDismissListener = onDismiss;
        dialog.show(manager, TAG);
        return true;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogPlaybackSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (getChildFragmentManager().findFragmentById(R.id.container) == null) {
            getChildFragmentManager().beginTransaction().replace(R.id.container, SettingPlayerFragment.newInstance()).commitAllowingStateLoss();
        }
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        super.setBehavior(dialog);
        if (getDialog() != null && getDialog().getWindow() != null) getDialog().getWindow().setDimAmount(0.55f);
        android.widget.FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.setBackgroundColor(0x00000000);
        }
    }

    public static boolean isShowing(FragmentActivity activity) {
        if (activity == null) return false;
        Fragment existing = activity.getSupportFragmentManager().findFragmentByTag(TAG);
        return existing instanceof PlaybackSettingDialog && existing.isAdded()
                && ((PlaybackSettingDialog) existing).getDialog() != null
                && ((PlaybackSettingDialog) existing).getDialog().isShowing();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        Runnable callback = onDismissListener;
        onDismissListener = null;
        if (callback != null) callback.run();
    }
}
