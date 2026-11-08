package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.ResUtil;

public class WebDialog {

    private final AlertDialog dialog;

    private WebDialog(View view) {
        this.dialog = new WebHtvAlertDialogBuilder(App.activity()).setView(view).create();
        this.dialog.setOnDismissListener((DialogInterface.OnDismissListener) view);
    }

    public static WebDialog create(View view) {
        return new WebDialog(view);
    }

    public WebDialog show() {
        initDialog();
        return this;
    }

    public void dismiss() {
        dialog.setOnDismissListener(null);
        dialog.dismiss();
    }

    private void initDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.height = (int) (ResUtil.getScreenHeight() * 0.8f);
        params.width = (int) (ResUtil.getScreenWidth() * 0.8f);
        dialog.getWindow().setAttributes(params);
        dialog.show();
    }
}
