package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.codec.CodecCapabilityInspector;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

public final class CodecCapabilityDialog {

    private static final int MODE_CURRENT = -1;

    private final FragmentActivity activity;
    private final PlayerManager player;
    private LinearLayout list;
    private MaterialButton current;
    private MaterialButton all;
    private MaterialButton video;
    private MaterialButton audio;
    private EditText search;
    private ScrollView scroll;
    private Dialog dialog;
    private String reportText = "";
    private int mode = MODE_CURRENT;

    private CodecCapabilityDialog(FragmentActivity activity, PlayerManager player) {
        this.activity = activity;
        this.player = player;
    }

    public static void show(FragmentActivity activity, PlayerManager player) {
        new CodecCapabilityDialog(activity, player).show();
    }

    private void show() {
        dialog = LightDialog.create(activity, activity.getString(R.string.codec_capability_title), createContent(), activity.getString(R.string.codec_capability_copy), v -> copy(), activity.getString(R.string.dialog_negative), null);
        update(true);
        dialog.show();
        applySize();
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialTextView note = new MaterialTextView(activity);
        note.setText("芯片 " + chipText());
        note.setTextColor(Color.parseColor("#5F6368"));
        note.setTextSize(12);
        note.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.bottomMargin = ResUtil.dp2px(10);
        root.addView(note, noteParams);

        search = new EditText(activity);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setHint(R.string.codec_capability_search);
        search.setTextSize(14);
        search.setTextColor(Color.parseColor("#202124"));
        search.setHintTextColor(Color.parseColor("#7A7F85"));
        search.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(12), 0);
        search.setBackground(searchBackground());
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                update(true);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(42)));

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(36));
        tabParams.topMargin = ResUtil.dp2px(9);
        root.addView(tabs, tabParams);

        current = tab(R.string.codec_capability_current, v -> setMode(MODE_CURRENT));
        all = tab(R.string.codec_capability_all, v -> setMode(CodecCapabilityInspector.TYPE_ALL));
        video = tab(R.string.codec_capability_video, v -> setMode(CodecCapabilityInspector.TYPE_VIDEO));
        audio = tab(R.string.codec_capability_audio, v -> setMode(CodecCapabilityInspector.TYPE_AUDIO));
        tabs.addView(current);
        tabs.addView(all);
        tabs.addView(video);
        tabs.addView(audio);

        FrameLayout report = new FrameLayout(activity);
        report.setBackground(reportBackground());
        report.setPadding(ResUtil.dp2px(2), ResUtil.dp2px(2), ResUtil.dp2px(2), ResUtil.dp2px(2));
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getReportHeight());
        reportParams.topMargin = ResUtil.dp2px(10);
        root.addView(report, reportParams);

        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setFocusable(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setOnKeyListener((view, keyCode, event) -> onScrollKey(keyCode, event));
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8));
        list.setOnKeyListener((view, keyCode, event) -> onScrollKey(keyCode, event));
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        report.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private MaterialButton tab(int text, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(activity);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(13);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(ResUtil.dp2px(4), 0, ResUtil.dp2px(4), 0);
        button.setCornerRadius(ResUtil.dp2px(6));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setOnFocusChangeListener((view, hasFocus) -> styleTab(button, button.isSelected()));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.leftMargin = ResUtil.dp2px(4);
        params.rightMargin = ResUtil.dp2px(4);
        button.setLayoutParams(params);
        return button;
    }

    private String chipText() {
        String soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? join(" ", Build.SOC_MANUFACTURER, Build.SOC_MODEL) : "";
        return join(" / ", soc, "hardware " + empty(Build.HARDWARE), "board " + empty(Build.BOARD));
    }

    private String join(String separator, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (builder.length() > 0) builder.append(separator);
            builder.append(value);
        }
        return builder.toString();
    }

    private String empty(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private int getReportHeight() {
        int screen = DialogSafeArea.height(activity);
        if (Util.isLeanback()) return Math.min(ResUtil.dp2px(430), Math.round(screen * 0.58f));
        return Math.min(ResUtil.dp2px(330), Math.round(screen * (ResUtil.isLand(activity) ? 0.44f : 0.36f)));
    }

    private GradientDrawable searchBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#F8F9FA"));
        drawable.setCornerRadius(ResUtil.dp2px(6));
        drawable.setStroke(ResUtil.dp2px(1), Color.parseColor("#DADCE0"));
        return drawable;
    }

    private GradientDrawable reportBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#F8F9FA"));
        drawable.setCornerRadius(ResUtil.dp2px(6));
        drawable.setStroke(ResUtil.dp2px(1), Color.parseColor("#E0E3E7"));
        return drawable;
    }

    private void setMode(int mode) {
        this.mode = mode;
        update(true);
    }

    private void update() {
        update(false);
    }

    private void update(boolean resetPosition) {
        if (list == null) return;
        setSelected(current, mode == MODE_CURRENT);
        setSelected(all, mode == CodecCapabilityInspector.TYPE_ALL);
        setSelected(video, mode == CodecCapabilityInspector.TYPE_VIDEO);
        setSelected(audio, mode == CodecCapabilityInspector.TYPE_AUDIO);
        String keyword = search == null || search.getText() == null ? "" : search.getText().toString();
        reportText = mode == MODE_CURRENT ? CodecCapabilityInspector.buildCurrentMediaReport(activity, player, keyword) : CodecCapabilityInspector.buildDeviceReport(activity, player, keyword, mode);
        renderReport(reportText);
        if (resetPosition) resetScrollAndFocus();
    }

    private void renderReport(String text) {
        list.removeAllViews();
        if (TextUtils.isEmpty(text)) {
            addItem("无内容", false, false, false);
            return;
        }
        String[] blocks = text.split("\\n\\n");
        for (int i = 0; i < blocks.length; i++) {
            String block = blocks[i].trim();
            if (TextUtils.isEmpty(block)) continue;
            if (i == 0 && blocks.length > 1 && !block.contains("\n")) addHeader(block);
            else addItem(block, isSelectedBlock(block), isRelatedBlock(block), isRiskBlock(block));
        }
    }

    private void resetScrollAndFocus() {
        if (scroll == null || list == null) return;
        scroll.post(() -> {
            scroll.scrollTo(0, 0);
            if (!Util.isLeanback()) return;
            View first = firstFocusableItem();
            if (first != null) first.requestFocus();
            else scroll.requestFocus();
        });
    }

    private View firstFocusableItem() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.isFocusable()) return child;
        }
        return null;
    }

    private void addHeader(String text) {
        MaterialTextView header = new MaterialTextView(activity);
        header.setText(text);
        header.setTextColor(Color.parseColor("#5F6368"));
        header.setTextSize(13);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setSingleLine(true);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(28));
        list.addView(header, params);
    }

    private void addItem(String text, boolean selected, boolean matched, boolean risk) {
        MaterialButton item = new MaterialButton(activity);
        item.setAllCaps(false);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        item.setText(text);
        item.setTextSize(Util.isLeanback() ? 14 : 12);
        item.setTypeface(Typeface.MONOSPACE);
        item.setSingleLine(false);
        item.setMaxLines(Integer.MAX_VALUE);
        item.setMinWidth(0);
        item.setMinimumWidth(0);
        item.setMinHeight(ResUtil.dp2px(Util.isLeanback() ? 72 : 58));
        item.setInsetTop(0);
        item.setInsetBottom(0);
        item.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
        item.setCornerRadius(ResUtil.dp2px(6));
        item.setFocusable(true);
        item.setFocusableInTouchMode(Util.isLeanback());
        item.setOnFocusChangeListener((view, hasFocus) -> styleItem(item, selected, matched, risk, hasFocus));
        item.setOnKeyListener((view, keyCode, event) -> onScrollKey(keyCode, event));
        item.setOnClickListener(view -> copyText(text));
        styleItem(item, selected, matched, risk, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = ResUtil.dp2px(8);
        list.addView(item, params);
    }

    private boolean isSelectedBlock(String block) {
        return block.contains("已选中") || block.contains("可解当前选中");
    }

    private boolean isRelatedBlock(String block) {
        return block.contains(" / 未选中") || block.contains("可解当前媒体");
    }

    private boolean isRiskBlock(String block) {
        if (!block.contains("Media3轨道状态")) return false;
        return block.contains("超出设备声明能力")
                || block.contains("不支持，")
                || block.contains("未声明")
                || block.contains("没有该 MIME")
                || block.contains("无法识别 MIME")
                || block.contains("查询失败");
    }

    private void styleItem(MaterialButton item, boolean selected, boolean related, boolean risk, boolean focused) {
        int textColor = risk ? Color.parseColor("#A50E0E") : selected ? Color.parseColor("#174EA6") : related ? Color.parseColor("#137333") : Color.parseColor("#202124");
        int bgColor = risk ? Color.parseColor("#FCE8E6") : selected ? Color.parseColor("#D2E3FC") : related ? Color.parseColor("#E6F4EA") : focused ? Color.parseColor("#F8F9FA") : Color.WHITE;
        int strokeColor = focused ? Color.parseColor("#202124") : risk ? Color.parseColor("#D93025") : selected ? Color.parseColor("#1A73E8") : related ? Color.parseColor("#34A853") : Color.parseColor("#DADCE0");
        item.setTextColor(ColorStateList.valueOf(textColor));
        item.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        item.setStrokeColor(ColorStateList.valueOf(strokeColor));
        item.setStrokeWidth(ResUtil.dp2px(focused ? 3 : selected || risk ? 2 : 1));
    }

    private boolean onScrollKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || scroll == null) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            scroll.smoothScrollBy(0, ResUtil.dp2px(72));
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            scroll.smoothScrollBy(0, -ResUtil.dp2px(72));
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            scroll.smoothScrollBy(0, scroll.getHeight());
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            scroll.smoothScrollBy(0, -scroll.getHeight());
            return true;
        }
        return false;
    }

    private void setSelected(@NonNull MaterialButton button, boolean selected) {
        button.setSelected(selected);
        styleTab(button, selected);
    }

    private void styleTab(@NonNull MaterialButton button, boolean selected) {
        boolean focused = button.isFocused();
        button.setTextColor(ContextCompat.getColorStateList(activity, selected || focused ? R.color.dialog_primary_button_text : R.color.dialog_outlined_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(activity, selected || focused ? R.color.dialog_primary_button_bg : R.color.dialog_outlined_button_bg));
        button.setStrokeColor(ContextCompat.getColorStateList(activity, R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(selected || focused ? 0 : ResUtil.dp2px(1));
    }

    private void copy() {
        copyText(reportText);
    }

    private void copyText(String text) {
        if (TextUtils.isEmpty(text)) return;
        ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.codec_capability_title), text));
        Notify.show(R.string.copied);
    }

    private void applySize() {
        if (dialog == null || dialog.getWindow() == null) return;
        int width = Math.min(Math.round(ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.78f : 0.94f)), ResUtil.dp2px(820));
        dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
