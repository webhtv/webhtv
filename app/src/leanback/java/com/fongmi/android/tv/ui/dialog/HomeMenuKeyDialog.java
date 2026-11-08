package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;

public class HomeMenuKeyDialog {

    public static void show(FragmentActivity activity, Runnable callback) {
        String[] items = activity.getResources().getStringArray(R.array.select_home_menu_key);
        int current = Setting.getHomeMenuKey();

        new WebHtvAlertDialogBuilder(activity)
            .setTitle(R.string.setting_home_menu_key)
            .setSingleChoiceItems(items, current, (dialog, which) -> {
                Setting.putHomeMenuKey(which);
                if (callback != null) callback.run();
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
