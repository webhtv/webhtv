package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;

public class WebDialog {

    private final AlertDialog dialog;

    public static WebDialog create(View view) {
        return new WebDialog(view);
    }

    private WebDialog(View view) {
        this.dialog = new WebHtvAlertDialogBuilder(App.activity()).setView(view).create();
        this.dialog.setOnDismissListener((DialogInterface.OnDismissListener) view);
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
        dialog.show();
    }
}
