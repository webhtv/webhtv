package com.fongmi.android.tv.theme;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The single construction point for Material alert dialogs in this app.
 *
 * <p>{@code MaterialAlertDialogBuilder.show()} is implemented as
 * {@code create().show()} and {@code create()} is virtual, so overriding it here
 * binds the dialog window to the active theme tokens without the caller having to do
 * anything. Dialogs built with the raw Material builder were never passed to
 * {@link ThemeController#bindDialog(android.app.Dialog)}, so their panels and text
 * stayed on the compiled {@code webhtv_color_*} palette no matter which theme the
 * user picked - the reason "theme colour" only appeared to affect the site dialog.
 *
 * <p>Binding is a no-op whenever the active tokens equal the compiled baseline, so
 * the default appearance stays byte-identical to the previous behaviour and there is
 * no cost for users who never change the theme.
 */
public class WebHtvAlertDialogBuilder extends MaterialAlertDialogBuilder {

    public WebHtvAlertDialogBuilder(Context context) {
        super(context);
    }

    public WebHtvAlertDialogBuilder(Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        // The panel itself is this builder's window background, not a view background,
        // so it is recoloured separately from the view tree.
        ThemeController.bindWindowBackground(getBackground());
        ThemeController.bindDialog(dialog);
        return dialog;
    }
}
