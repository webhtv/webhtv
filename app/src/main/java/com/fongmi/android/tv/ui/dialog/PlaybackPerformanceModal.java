package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.function.IntConsumer;

/** Programmatic modal surfaces shared by playback performance settings. */
final class PlaybackPerformanceModal {

    private static final int COLOR_TEXT = Color.rgb(32, 33, 36);
    private static final int COLOR_SECONDARY = Color.rgb(95, 99, 104);
    private static final int COLOR_BLUE = Color.rgb(26, 115, 232);
    private static final int COLOR_BLUE_TEXT = Color.rgb(23, 78, 166);
    private static final int COLOR_BLUE_LIGHT = Color.rgb(232, 240, 254);
    private static final int COLOR_STROKE = Color.rgb(196, 199, 197);
    private static final int COLOR_BLUE_STROKE = Color.rgb(138, 180, 248);

    private PlaybackPerformanceModal() {
    }

    static Dialog confirm(
            Context context,
            CharSequence title,
            CharSequence message,
            CharSequence cancelLabel,
            CharSequence confirmLabel,
            Runnable onConfirm) {
        Shell shell = createShell(context, title);
        addMessage(context, shell.body(), message);
        addFooterButton(shell, cancelLabel, false, shell.dialog()::dismiss);
        addFooterButton(shell, confirmLabel, true, () -> {
            shell.dialog().dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        return shell.dialog();
    }

    static Dialog choices(
            Context context,
            CharSequence title,
            String[] labels,
            int selected,
            IntConsumer onSelected) {
        Shell shell = createShell(context, title);
        if (labels != null) {
            for (int index = 0; index < labels.length; index++) {
                int choice = index;
                addListButton(
                        context,
                        shell.body(),
                        labels[index],
                        index == selected,
                        () -> {
                            shell.dialog().dismiss();
                            if (onSelected != null) onSelected.accept(choice);
                        });
            }
        }
        return shell.dialog();
    }

    private static Shell createShell(
            Context context,
            CharSequence title) {
        Dialog dialog = new Dialog(context, R.style.Theme_WebHTV_Dialog);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        root.setPadding(dp(context, 22), dp(context, 20),
                dp(context, 22), dp(context, 18));

        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        MaterialTextView titleView = new MaterialTextView(context);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton close = closeButton(context, dialog::dismiss);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                dp(context, 42), dp(context, 42));
        closeParams.leftMargin = dp(context, 12);
        titleBar.addView(close, closeParams);
        root.addView(titleBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 42)));

        ScrollView scroll = new MaxHeightScrollView(
                context,
                Math.min(dp(context, 460),
                        DialogSafeArea.height(context) * 3 / 5));
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(context, 12), 0, dp(context, 4));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(context, 12);
        root.addView(footer, footerParams);

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(ignored -> resize(dialog, context));
        return new Shell(dialog, body, footer);
    }

    private static void resize(Dialog dialog, Context context) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(context)
                * (ResUtil.isLand(context) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.6f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private static void addMessage(
            Context context,
            LinearLayout body,
            CharSequence message) {
        MaterialTextView view = new MaterialTextView(context);
        view.setText(message);
        view.setTextColor(COLOR_SECONDARY);
        view.setTextSize(14);
        view.setLineSpacing(dp(context, 3), 1f);
        view.setTextIsSelectable(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 8);
        body.addView(view, params);
    }

    private static void addListButton(
            Context context,
            LinearLayout body,
            CharSequence text,
            boolean selected,
            Runnable action) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setSingleLine(false);
        button.setText((selected ? "✓  " : "") + text);
        button.setTextSize(14);
        button.setMinHeight(dp(context, 48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(context, 6));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        styleListButton(button, selected, false);
        button.setOnFocusChangeListener((view, focused) ->
                styleListButton(button, selected, focused));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 50));
        params.bottomMargin = dp(context, 7);
        body.addView(button, params);
    }

    private static void styleListButton(
            MaterialButton button,
            boolean selected,
            boolean focused) {
        int text = focused ? Color.WHITE
                : selected ? COLOR_BLUE_TEXT : COLOR_TEXT;
        int background = focused ? COLOR_BLUE
                : selected ? COLOR_BLUE_LIGHT : Color.WHITE;
        int stroke = focused || selected ? COLOR_BLUE : COLOR_STROKE;
        button.setTextColor(ColorStateList.valueOf(text));
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setStrokeWidth(dp(button.getContext(),
                focused || selected ? 2 : 1));
    }

    private static void addFooterButton(
            Shell shell,
            CharSequence label,
            boolean primary,
            Runnable action) {
        MaterialButton button = actionButton(
                shell.footer().getContext(), label, primary, action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(
                shell.footer().getContext(), 38));
        if (shell.footer().getChildCount() > 0) {
            params.leftMargin = dp(shell.footer().getContext(), 8);
        }
        shell.footer().addView(button, params);
    }

    private static MaterialButton actionButton(
            Context context,
            CharSequence label,
            boolean primary,
            Runnable action) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setMinWidth(dp(context, 72));
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 38));
        button.setMinimumHeight(dp(context, 38));
        button.setPaddingRelative(dp(context, 12), 0, dp(context, 12), 0);
        button.setInsetLeft(0);
        button.setInsetRight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setCornerRadius(dp(context, 6));
        styleActionButton(button, primary, false);
        button.setOnFocusChangeListener((view, focused) ->
                styleActionButton(button, primary, focused));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private static void styleActionButton(
            MaterialButton button,
            boolean primary,
            boolean focused) {
        boolean filled = primary || focused;
        button.setTextColor(ColorStateList.valueOf(
                filled ? Color.WHITE : COLOR_BLUE_TEXT));
        button.setBackgroundTintList(ColorStateList.valueOf(
                filled ? COLOR_BLUE : Color.WHITE));
        button.setStrokeColor(ColorStateList.valueOf(
                filled ? COLOR_BLUE : COLOR_BLUE_STROKE));
        button.setStrokeWidth(dp(button.getContext(), 1));
    }

    private static MaterialButton closeButton(
            Context context,
            Runnable action) {
        MaterialButton button = new MaterialButton(context);
        button.setText("×");
        button.setTextSize(20);
        button.setContentDescription(context.getString(
                R.string.player_performance_help_close));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 32));
        button.setMinimumHeight(dp(context, 32));
        button.setPadding(dp(context, 6), 0, dp(context, 6), 0);
        button.setInsetLeft(0);
        button.setInsetRight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setCornerRadius(dp(context, 6));
        styleCloseButton(button, false);
        button.setOnFocusChangeListener((view, focused) ->
                styleCloseButton(button, focused));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private static void styleCloseButton(
            MaterialButton button,
            boolean focused) {
        button.setTextColor(ColorStateList.valueOf(
                focused ? Color.WHITE : COLOR_SECONDARY));
        button.setBackgroundTintList(ColorStateList.valueOf(
                focused ? COLOR_BLUE : Color.WHITE));
        button.setStrokeColor(ColorStateList.valueOf(
                focused ? COLOR_BLUE : COLOR_STROKE));
        button.setStrokeWidth(dp(button.getContext(), focused ? 1 : 0));
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private static final class MaxHeightScrollView extends ScrollView {

        private final int maxHeight;

        private MaxHeightScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedHeight = MeasureSpec.makeMeasureSpec(
                    maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedHeight);
        }
    }

    private record Shell(
            Dialog dialog,
            LinearLayout body,
            LinearLayout footer) {
    }
}
