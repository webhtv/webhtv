package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Xml;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MpvCustomButtonDialog extends DialogFragment {

    private static final int INITIAL_SLOT_COUNT = 8;
    private Runnable callback;
    private LinearLayout list;

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importButtons);
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/xml"), this::exportButtons);

    public static void show(FragmentManager manager, Runnable callback) {
        MpvCustomButtonDialog dialog = new MpvCustomButtonDialog();
        dialog.callback = callback;
        dialog.show(manager, "mpv-custom-buttons");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable android.os.Bundle savedInstanceState) {
        LinearLayout root = column(requireContext(), 16);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);

        LinearLayout header = row(requireContext());
        LinearLayout heading = column(requireContext(), 0);
        TextView title = text(requireContext(), getString(R.string.mpv_config_custom_button_title), 21, true);
        TextView subtitle = text(requireContext(), getString(R.string.mpv_config_custom_button_subtitle), 12, false);
        subtitle.setTextColor(Color.rgb(95, 99, 104));
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        AppCompatImageButton close = iconButton(R.drawable.ic_dialog_close, R.string.mpv_config_close);
        close.setOnClickListener(view -> dismissAllowingStateLoss());
        header.addView(close, new LinearLayout.LayoutParams(ResUtil.dp2px(44), ResUtil.dp2px(40)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(58)));

        ScrollView scroll = new ScrollView(requireContext());
        list = column(requireContext(), 0);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        refresh();
        return new WebHtvAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_WebHTV_Dialog).setView(root).create();
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        List<MpvConfigStore.CustomButton> buttons = MpvConfigStore.customButtons();
        // Keep the familiar initial layout, then append one empty slot so the
        // list remains extensible instead of imposing a button-count cap.
        int slotCount = Math.max(INITIAL_SLOT_COUNT, buttons.size() + 1);
        for (int index = 0; index < slotCount; index++) addSlot(index, index < buttons.size() ? buttons.get(index) : null);
        addImportExportCard();
    }

    private void addSlot(int index, @Nullable MpvConfigStore.CustomButton item) {
        String slot = index < 4 ? "L" + (index + 1) : "R" + (index - 3);
        LinearLayout row = row(requireContext());
        row.setMinimumHeight(ResUtil.dp2px(72));
        row.setPadding(ResUtil.dp2px(6), ResUtil.dp2px(6), ResUtil.dp2px(6), ResUtil.dp2px(6));
        row.setFocusable(true);
        row.setFocusableInTouchMode(Util.isLeanback());
        if (item != null) row.setBackgroundResource(R.drawable.selector_mpv_profile_card);

        TextView badge = text(requireContext(), slot, 12, false);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.rgb(83, 101, 132));
        badge.setBackground(badgeBackground(index < 4 ? 0xFFE7EEF9 : 0xFFF0EAF4));
        row.addView(badge, new LinearLayout.LayoutParams(ResUtil.dp2px(48), ResUtil.dp2px(48)));

        TextView name = text(requireContext(), item == null ? getString(R.string.mpv_config_custom_button_empty_slot) : item.title, 15, item != null);
        name.setTextColor(item == null ? Color.rgb(128, 134, 139) : Color.rgb(32, 33, 36));
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (item != null) {
            AppCompatImageButton delete = iconButton(R.drawable.ic_action_delete, R.string.mpv_config_delete);
            delete.setColorFilter(Color.rgb(197, 34, 31));
            delete.setBackgroundTintList(ColorStateList.valueOf(0xFFFFE3E0));
            delete.setOnClickListener(view -> confirmDelete(item));
            row.addView(delete, new LinearLayout.LayoutParams(ResUtil.dp2px(44), ResUtil.dp2px(44)));
        }
        AppCompatImageButton drag = iconButton(R.drawable.ic_action_drag, R.string.mpv_config_custom_button_drag);
        drag.setAlpha(item == null ? 0.35f : 0.75f);
        row.addView(drag, new LinearLayout.LayoutParams(ResUtil.dp2px(44), ResUtil.dp2px(44)));
        row.setOnClickListener(view -> openEditor(item));
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(72)));
    }

    private void addImportExportCard() {
        LinearLayout card = column(requireContext(), 16);
        card.setBackgroundResource(R.drawable.selector_mpv_profile_card);
        TextView title = text(requireContext(), getString(R.string.mpv_config_custom_button_import_export), 16, true);
        TextView desc = text(requireContext(), getString(R.string.mpv_config_custom_button_import_export_desc), 12, false);
        desc.setTextColor(Color.rgb(95, 99, 104));
        card.addView(title);
        card.addView(desc);
        LinearLayout actions = row(requireContext());
        MaterialButton importButton = actionButton(R.string.mpv_config_custom_button_import, R.drawable.ic_git_cloud_download, false);
        importButton.setOnClickListener(view -> importLauncher.launch(new String[]{"text/xml", "application/xml", "text/*"}));
        MaterialButton exportButton = actionButton(R.string.mpv_config_custom_button_export, R.drawable.ic_git_cloud_upload, true);
        exportButton.setOnClickListener(view -> exportLauncher.launch("custom_buttons_" + System.currentTimeMillis() + ".xml"));
        actions.addView(importButton, new LinearLayout.LayoutParams(0, ResUtil.dp2px(44), 1));
        actions.addView(exportButton, new LinearLayout.LayoutParams(0, ResUtil.dp2px(44), 1));
        card.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(52)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ResUtil.dp2px(12);
        params.bottomMargin = ResUtil.dp2px(8);
        list.addView(card, params);
    }

    private void openEditor(@Nullable MpvConfigStore.CustomButton button) {
        Editor.show(getChildFragmentManager(), button, this::refresh);
    }

    private void confirmDelete(MpvConfigStore.CustomButton button) {
        new WebHtvAlertDialogBuilder(requireContext())
                .setTitle(R.string.mpv_config_custom_button_delete)
                .setMessage(getString(R.string.mpv_config_custom_button_delete_message, button.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mpv_config_delete, (dialog, which) -> {
                    try {
                        MpvConfigStore.deleteCustomButton(button.id);
                        refresh();
                        if (callback != null) callback.run();
                    } catch (Throwable error) {
                        Notify.show(message(error));
                    }
                }).show();
    }

    private void importButtons(@Nullable Uri uri) {
        if (uri == null) return;
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException(getString(R.string.mpv_config_custom_button_file_invalid));
            List<MpvConfigStore.CustomButton> buttons = parseXml(input);
            for (MpvConfigStore.CustomButton button : buttons) {
                MpvConfigStore.saveCustomButton(button.id, button.title, button.content, button.longPressContent, button.onStartup, button.enabled);
            }
            refresh();
            if (callback != null) callback.run();
            Notify.show(getString(R.string.mpv_config_custom_button_imported, buttons.size()));
        } catch (Throwable error) {
            Notify.show(message(error));
        }
    }

    private void exportButtons(@Nullable Uri uri) {
        if (uri == null) return;
        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException(getString(R.string.mpv_config_custom_button_file_invalid));
            output.write(buildXml(MpvConfigStore.customButtons()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Notify.show(R.string.mpv_config_custom_button_exported);
        } catch (Throwable error) {
            Notify.show(message(error));
        }
    }

    private static String buildXml(List<MpvConfigStore.CustomButton> buttons) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<customButtons version=\"1\">\n");
        for (MpvConfigStore.CustomButton button : buttons) {
            xml.append("  <button id=\"").append(attribute(button.id)).append("\" title=\"").append(attribute(button.title)).append("\" enabled=\"").append(button.enabled).append("\">\n");
            xml.append(cdata("short", button.content));
            xml.append(cdata("longPress", button.longPressContent));
            xml.append(cdata("startup", button.onStartup));
            xml.append("  </button>\n");
        }
        return xml.append("</customButtons>\n").toString();
    }

    private static List<MpvConfigStore.CustomButton> parseXml(InputStream input) throws Exception {
        List<MpvConfigStore.CustomButton> result = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(input, "UTF-8");
        MpvConfigStore.CustomButton current = null;
        String field = null;
        StringBuilder value = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("button".equals(parser.getName())) {
                    current = new MpvConfigStore.CustomButton();
                    current.id = parser.getAttributeValue(null, "id");
                    current.title = parser.getAttributeValue(null, "title");
                    current.enabled = !"false".equalsIgnoreCase(parser.getAttributeValue(null, "enabled"));
                } else if (current != null && ("short".equals(parser.getName()) || "longPress".equals(parser.getName()) || "startup".equals(parser.getName()))) {
                    field = parser.getName();
                    value = new StringBuilder();
                }
            } else if (event == XmlPullParser.TEXT && value != null) {
                value.append(parser.getText());
            } else if (event == XmlPullParser.END_TAG) {
                if (current != null && value != null && parser.getName().equals(field)) {
                    if ("short".equals(field)) current.content = value.toString();
                    else if ("longPress".equals(field)) current.longPressContent = value.toString();
                    else current.onStartup = value.toString();
                    field = null;
                    value = null;
                } else if ("button".equals(parser.getName()) && current != null) {
                    if (!TextUtils.isEmpty(current.title)) result.add(current);
                    current = null;
                }
            }
        }
        return result;
    }

    private static String cdata(String tag, String value) {
        String text = value == null ? "" : value.replace("]]>", "]]]]><![CDATA[>");
        return "    <" + tag + "><![CDATA[" + text + "]]></" + tag + ">\n";
    }

    private static String attribute(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.min((int) (DialogSafeArea.width(requireActivity(), window) * (Util.isLeanback() ? 0.68f : 0.94f)), ResUtil.dp2px(760));
        params.height = (int) (DialogSafeArea.height(requireActivity(), window) * (Util.isLeanback() ? 0.88f : 0.9f));
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private AppCompatImageButton iconButton(int icon, int description) {
        AppCompatImageButton view = new AppCompatImageButton(requireContext());
        view.setBackgroundResource(R.drawable.selector_mpv_icon_button);
        view.setContentDescription(getString(description));
        view.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(10), ResUtil.dp2px(10), ResUtil.dp2px(10));
        view.setImageResource(icon);
        view.setColorFilter(Color.rgb(95, 99, 104));
        return view;
    }

    private MaterialButton actionButton(int text, int icon, boolean primary) {
        MaterialButton view = button(requireContext(), getString(text));
        view.setIconResource(icon);
        view.setIconPadding(ResUtil.dp2px(6));
        view.setMinHeight(0);
        view.setMinWidth(0);
        if (!primary) {
            view.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            view.setTextColor(Color.rgb(60, 64, 67));
            view.setStrokeWidth(ResUtil.dp2px(1));
            view.setStrokeColor(ColorStateList.valueOf(0xFFB7B9BE));
        }
        return view;
    }

    private static GradientDrawable badgeBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    private static LinearLayout row(android.content.Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static LinearLayout column(android.content.Context context, int padding) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        if (padding > 0) view.setPadding(ResUtil.dp2px(padding), ResUtil.dp2px(padding), ResUtil.dp2px(padding), ResUtil.dp2px(padding));
        return view;
    }

    private static TextView text(android.content.Context context, String value, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(Color.rgb(32, 33, 36));
        view.setTextSize(size);
        view.setTypeface(view.getTypeface(), bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private static MaterialButton button(android.content.Context context, String value) {
        MaterialButton view = new MaterialButton(context);
        view.setText(value);
        view.setMinHeight(0);
        view.setMinWidth(0);
        return view;
    }

    private static String message(Throwable error) {
        return TextUtils.isEmpty(error.getMessage()) ? error.getClass().getSimpleName() : error.getMessage();
    }

    public static final class Editor extends DialogFragment {

        private MpvConfigStore.CustomButton source;
        private Runnable callback;
        private TextInputEditText title;
        private TextInputEditText shortCode;
        private TextInputEditText longCode;
        private TextInputEditText startupCode;
        private CheckBox enabled;

        static void show(FragmentManager manager, MpvConfigStore.CustomButton source, Runnable callback) {
            Editor dialog = new Editor();
            dialog.source = source;
            dialog.callback = callback;
            dialog.show(manager, "mpv-custom-button-editor");
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable android.os.Bundle savedInstanceState) {
            LinearLayout root = column(requireContext(), 16);
            root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
            TextView heading = text(requireContext(), getString(source == null ? R.string.mpv_config_custom_button_new : R.string.mpv_config_custom_button_edit), 19, true);
            root.addView(heading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(42)));
            title = input(requireContext(), getString(R.string.mpv_config_custom_button_name), source == null ? "" : source.title, false);
            root.addView(title, inputParams());
            enabled = new CheckBox(requireContext());
            enabled.setText(R.string.mpv_config_custom_button_enabled);
            enabled.setChecked(source == null || source.enabled);
            root.addView(enabled, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView lua = text(requireContext(), getString(R.string.mpv_config_custom_button_lua_section), 14, true);
            lua.setTextColor(Color.rgb(60, 64, 67));
            root.addView(lua, inputParams());
            shortCode = input(requireContext(), getString(R.string.mpv_config_custom_button_short), source == null ? "" : source.content, true);
            longCode = input(requireContext(), getString(R.string.mpv_config_custom_button_long), source == null ? "" : source.longPressContent, true);
            startupCode = input(requireContext(), getString(R.string.mpv_config_custom_button_startup), source == null ? "" : source.onStartup, true);
            root.addView(shortCode, inputParams());
            root.addView(longCode, inputParams());
            root.addView(startupCode, inputParams());
            LinearLayout actions = row(requireContext());
            MaterialButton cancel = button(requireContext(), getString(R.string.mpv_config_close));
            cancel.setOnClickListener(view -> dismissAllowingStateLoss());
            MaterialButton save = button(requireContext(), getString(R.string.mpv_config_save));
            save.setOnClickListener(view -> save());
            actions.addView(cancel, new LinearLayout.LayoutParams(0, ResUtil.dp2px(46), 1));
            actions.addView(save, new LinearLayout.LayoutParams(0, ResUtil.dp2px(46), 1));
            root.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ScrollView scroll = new ScrollView(requireContext());
            scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new WebHtvAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_WebHTV_Dialog).setView(scroll).create();
        }

        private void save() {
            try {
                String id = source == null ? "" : source.id;
                MpvConfigStore.saveCustomButton(id, value(title), value(shortCode), value(longCode), value(startupCode), enabled.isChecked());
                Notify.show(R.string.mpv_config_custom_button_saved);
                if (callback != null) callback.run();
                dismissAllowingStateLoss();
            } catch (Throwable error) {
                Notify.show(message(error));
            }
        }

        private static TextInputEditText input(android.content.Context context, String hint, String value, boolean multiline) {
            TextInputEditText edit = new TextInputEditText(context);
            edit.setHint(hint);
            edit.setText(value);
            edit.setTextSize(14);
            edit.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            edit.setSingleLine(!multiline);
            if (multiline) {
                edit.setMinLines(3);
                edit.setGravity(Gravity.TOP | Gravity.START);
                edit.setBackgroundResource(R.drawable.shape_mpv_editor);
                edit.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(10), ResUtil.dp2px(12), ResUtil.dp2px(10));
            }
            return edit;
        }

        private static LinearLayout.LayoutParams inputParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = ResUtil.dp2px(8);
            return params;
        }

        private static String value(EditText edit) {
            return edit.getText() == null ? "" : edit.getText().toString();
        }

        @Override
        public void onStart() {
            super.onStart();
            Dialog dialog = getDialog();
            Window window = dialog == null ? null : dialog.getWindow();
            if (window == null) return;
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min((int) (DialogSafeArea.width(requireActivity(), window) * (Util.isLeanback() ? 0.68f : 0.94f)), ResUtil.dp2px(760));
            params.height = (int) (DialogSafeArea.height(requireActivity(), window) * (Util.isLeanback() ? 0.9f : 0.92f));
            params.dimAmount = 0.58f;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(params);
            window.setLayout(params.width, params.height);
        }
    }
}
