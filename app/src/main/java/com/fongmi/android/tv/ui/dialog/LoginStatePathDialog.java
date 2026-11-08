package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterLoginStateTreeBinding;
import com.fongmi.android.tv.databinding.DialogLoginStatePathBinding;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.custom.SafeScrollEditText;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.LoginStatePathIndex;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LoginStatePathDialog extends BaseAlertDialog {

    private static final String ROOT_APP = "app";
    private static final String ROOT_SDCARD = "sdcard";
    private static final int INDENT_DP = 18;
    private static final int MAX_EDIT_CHARS = 32 * 1024;
    private static final int MAX_EDIT_LINE_CHARS = 1600;
    private static final int TEXT_ROW_CHARS = 1200;
    private static final int MAX_AUTO_EXPAND_PATHS = 128;

    private final Set<String> selected = new LinkedHashSet<>();
    private final Set<String> expanded = new LinkedHashSet<>();
    private final Set<String> pending = new LinkedHashSet<>();
    private final Map<String, LoginStateSync.Candidate> findings = new HashMap<>();
    private final List<Row> appRows = new ArrayList<>();
    private final List<Row> sdcardRows = new ArrayList<>();
    private LoginStatePathIndex selectedIndex = new LoginStatePathIndex();
    private LoginStatePathIndex pendingIndex = new LoginStatePathIndex();
    private LoginStatePathIndex visiblePendingIndex = new LoginStatePathIndex();

    private DialogLoginStatePathBinding binding;
    private TreeAdapter appAdapter;
    private TreeAdapter sdcardAdapter;
    private Runnable callback;
    private Dialog editor;
    private boolean stateLoaded;
    private int loadGeneration;
    private boolean appPanelCollapsed;
    private boolean sdcardPanelCollapsed;
    private boolean appPanelTouched;
    private boolean sdcardPanelTouched;
    private boolean treeTruncated;

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        LoginStatePathDialog dialog = new LoginStatePathDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogLoginStatePathBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new WebHtvAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_Dialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.appRecycler.setHasFixedSize(false);
        binding.sdcardRecycler.setHasFixedSize(false);
        binding.appRecycler.addItemDecoration(new SpaceItemDecoration(1, 4));
        binding.sdcardRecycler.addItemDecoration(new SpaceItemDecoration(1, 4));
        binding.appRecycler.setAdapter(appAdapter = new TreeAdapter(appRows));
        binding.sdcardRecycler.setAdapter(sdcardAdapter = new TreeAdapter(sdcardRows));
        binding.positive.setEnabled(false);
        loadStateAsync(false);
    }

    @Override
    protected void initEvent() {
        binding.refresh.setOnClickListener(v -> {
            loadStateAsync(true);
        });
        binding.appTitle.setOnClickListener(v -> togglePanel(ROOT_APP));
        binding.sdcardTitle.setOnClickListener(v -> togglePanel(ROOT_SDCARD));
        binding.selectSafe.setOnClickListener(v -> revealPending());
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        setCancelable(false);
        getDialog().setCanceledOnTouchOutside(false);
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = DialogSafeArea.width(requireActivity(), window);
        int screenHeight = DialogSafeArea.height(requireActivity(), window);
        boolean land = ResUtil.isLand(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.82f : 0.96f));
        params.height = (int) (screenHeight * (land ? 0.96f : 0.86f));
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
        rootParams.height = params.height;
        binding.getRoot().setLayoutParams(rootParams);
        if (!appPanelCollapsed) binding.appRecycler.requestFocus();
        else if (!sdcardPanelCollapsed) binding.sdcardRecycler.requestFocus();
        else binding.appTitle.requestFocus();
    }

    @Override
    public void onDestroyView() {
        if (editor != null && editor.isShowing()) editor.dismiss();
        editor = null;
        super.onDestroyView();
    }

    private void loadStateAsync(boolean preserveSelection) {
        int generation = ++loadGeneration;
        stateLoaded = false;
        binding.refresh.setEnabled(false);
        binding.positive.setEnabled(false);
        Task.execute(() -> {
            List<String> learned = LoginStateSync.learnedPaths();
            List<String> pendingPaths = LoginStateSync.pendingPaths();
            List<LoginStateSync.Candidate> candidateList = LoginStateSync.findings();
            Map<String, LoginStateSync.Candidate> candidateMap = new HashMap<>();
            for (LoginStateSync.Candidate item : candidateList) {
                String path = LoginStateSync.normalizePath(item.getPath());
                if (!path.isEmpty()) candidateMap.put(path, item);
            }
            App.post(() -> {
                if (binding == null || generation != loadGeneration || !isAdded()) return;
                if (!preserveSelection) {
                    selected.clear();
                    selected.addAll(learned);
                }
                pending.clear();
                pending.addAll(pendingPaths);
                findings.clear();
                findings.putAll(candidateMap);
                stateLoaded = true;
                binding.refresh.setEnabled(true);
                binding.positive.setEnabled(true);
                expandLearnedAndPending();
                rebuild();
            });
        });
    }

    private void expandLearnedAndPending() {
        expanded.add(ROOT_APP);
        expanded.add(ROOT_SDCARD);
        if (selected.size() + pending.size() > MAX_AUTO_EXPAND_PATHS) return;
        for (String path : selected) expandPath(path);
        for (String path : pending) expandPath(path);
        for (LoginStateSync.PathState state : LoginStateSync.pathStates(new ArrayList<>(selected))) {
            if (state.isExists() && !state.isFile()) expanded.add(state.getPath());
        }
    }

    private void expandPath(String path) {
        path = normalize(path);
        while (!path.isEmpty()) {
            int index = path.lastIndexOf('/');
            if (index < 0) {
                expanded.add(path);
                return;
            }
            path = path.substring(0, index);
            if (!path.isEmpty()) expanded.add(path);
        }
    }

    private void rebuild() {
        rebuildIndexes();
        treeTruncated = false;
        appRows.clear();
        sdcardRows.clear();
        buildRows(ROOT_APP, 0, appRows);
        buildRows(ROOT_SDCARD, 0, sdcardRows);
        appendMissingRows(ROOT_APP, appRows);
        appendMissingRows(ROOT_SDCARD, sdcardRows);
        appAdapter.notifyDataSetChanged();
        sdcardAdapter.notifyDataSetChanged();
        updateState();
    }

    private void buildRows(String dir, int depth, List<Row> rows) {
        LoginStateSync.Tree tree = LoginStateSync.tree(dir);
        if (tree.isTruncated()) treeTruncated = true;
        for (LoginStateSync.TreeItem item : tree.getItems()) {
            Row row = new Row(item, depth);
            rows.add(row);
            if (item.isDir() && expanded.contains(item.getPath())) buildRows(item.getPath(), depth + 1, rows);
        }
    }

    private void appendMissingRows(String root, List<Row> rows) {
        if (selected.size() + pending.size() > MAX_AUTO_EXPAND_PATHS) return;
        Set<String> visible = new LinkedHashSet<>();
        for (Row row : rows) visible.add(row.item.getPath());
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(selected);
        paths.addAll(pending);
        for (String path : paths) {
            if (!path.startsWith(root + "/") || visible.contains(path) || !isMissing(path)) continue;
            rows.add(new Row(new LoginStateSync.TreeItem(name(path), path, false, 0, 0, true), depth(path)));
            visible.add(path);
        }
    }

    private void revealPending() {
        List<String> list = pendingToConfirm();
        if (list.isEmpty()) {
            Notify.show(R.string.login_state_confirm_empty);
            return;
        }
        for (String path : list) expandPath(path);
        rebuild();
        scrollToFirstPending();
        Notify.show(getString(R.string.login_state_pending_revealed, list.size()));
    }

    private void scrollToFirstPending() {
        int appIndex = firstPendingIndex(appRows);
        if (appIndex >= 0) {
            binding.appRecycler.scrollToPosition(appIndex);
            return;
        }
        int sdcardIndex = firstPendingIndex(sdcardRows);
        if (sdcardIndex >= 0) binding.sdcardRecycler.scrollToPosition(sdcardIndex);
    }

    private int firstPendingIndex(List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) if (isPending(rows.get(i).item.getPath())) return i;
        return -1;
    }

    private void toggleExpanded(String path) {
        path = normalize(path);
        if (expanded.contains(path)) expanded.remove(path);
        else expanded.add(path);
        rebuild();
    }

    private void toggleSelected(String path) {
        if (TextUtils.isEmpty(path)) return;
        if (stateOf(path) == MaterialCheckBox.STATE_CHECKED) removePath(path);
        else addPath(path);
        rebuild();
    }

    private void addPath(String path) {
        path = normalize(path);
        if (path.isEmpty()) return;
        String target = path;
        selected.removeIf(item -> covers(target, item) || covers(item, target));
        selected.add(target);
        rebuildIndexes();
    }

    private void removePath(String path) {
        path = normalize(path);
        if (path.isEmpty()) return;
        String target = path;
        selected.removeIf(item -> covers(item, target));
        rebuildIndexes();
    }

    private void onPositive() {
        if (!stateLoaded) return;
        LoginStateSync.savePaths(new ArrayList<>(selected));
        if (callback != null) callback.run();
        dismiss();
    }

    private void updateState() {
        int ready = 0;
        int missing = 0;
        for (LoginStateSync.PathState state : LoginStateSync.pathStates(new ArrayList<>(selected))) {
            if (state.isExists()) ready++;
            else missing++;
        }
        int pendingCount = pendingToConfirm().size();
        String summary = selected.isEmpty() ? getString(R.string.login_state_paths_empty_selected_with_pending, pendingCount) : getString(R.string.login_state_paths_selected_count_with_pending, selected.size(), ready, missing, pendingCount);
        if (treeTruncated) summary += " · " + getString(R.string.login_state_tree_truncated);
        binding.summary.setText(summary);
        binding.selectSafe.setEnabled(pendingCount > 0);
        binding.selectSafe.setText(pendingCount > 0 ? getString(R.string.login_state_reveal_pending_count, pendingCount) : getString(R.string.login_state_reveal_pending));
        binding.empty.setVisibility(appRows.isEmpty() && sdcardRows.isEmpty() ? View.VISIBLE : View.GONE);
        updatePanelState();
    }

    private void updatePanelState() {
        PanelCount app = panelCount(ROOT_APP);
        PanelCount sdcard = panelCount(ROOT_SDCARD);
        binding.appTitle.setText(getString(R.string.login_state_tree_title_count, getString(R.string.login_state_tree_app), app.selected, app.pending));
        binding.sdcardTitle.setText(getString(R.string.login_state_tree_title_count, getString(R.string.login_state_tree_sdcard), sdcard.selected, sdcard.pending));
        if (!appPanelTouched) appPanelCollapsed = app.isEmpty() && !sdcard.isEmpty();
        if (!sdcardPanelTouched) sdcardPanelCollapsed = sdcard.isEmpty() && !app.isEmpty();
        if (app.isEmpty() && sdcard.isEmpty()) {
            appPanelCollapsed = false;
            sdcardPanelCollapsed = false;
        }
        applyPanelLayout(binding.appPanel, binding.appRecycler, appPanelCollapsed);
        applyPanelLayout(binding.sdcardPanel, binding.sdcardRecycler, sdcardPanelCollapsed);
    }

    private PanelCount panelCount(String root) {
        int selectedCount = 0;
        int pendingCount = 0;
        for (String path : selected) if (path.equals(root) || path.startsWith(root + "/")) selectedCount++;
        for (String path : pendingToConfirm()) if (path.equals(root) || path.startsWith(root + "/")) pendingCount++;
        return new PanelCount(selectedCount, pendingCount);
    }

    private void togglePanel(String root) {
        if (ROOT_APP.equals(root)) {
            appPanelTouched = true;
            appPanelCollapsed = !appPanelCollapsed;
        } else {
            sdcardPanelTouched = true;
            sdcardPanelCollapsed = !sdcardPanelCollapsed;
        }
        updatePanelState();
    }

    private void applyPanelLayout(ViewGroup panel, RecyclerView recycler, boolean collapsed) {
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        if (params instanceof LinearLayoutCompat.LayoutParams layoutParams) {
            layoutParams.height = collapsed ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            layoutParams.weight = collapsed ? 0 : 1;
            panel.setLayoutParams(layoutParams);
        }
        recycler.setVisibility(collapsed ? View.GONE : View.VISIBLE);
    }

    private List<String> pendingToConfirm() {
        List<String> result = new ArrayList<>();
        for (String path : pending) if (!selectedIndex.hasAncestor(path)) result.add(path);
        return result;
    }

    private int stateOf(String path) {
        path = normalize(path);
        if (selectedIndex.hasAncestor(path)) return MaterialCheckBox.STATE_CHECKED;
        if (selectedIndex.hasDescendant(path)) return MaterialCheckBox.STATE_INDETERMINATE;
        return MaterialCheckBox.STATE_UNCHECKED;
    }

    private boolean covers(String parent, String child) {
        parent = normalize(parent);
        child = normalize(child);
        return !parent.isEmpty() && (parent.equals(child) || child.startsWith(parent + "/"));
    }

    private boolean isPending(String path) {
        path = normalize(path);
        return pendingIndex.contains(path) && !selectedIndex.hasAncestor(path);
    }

    private boolean hasPendingChild(String path) {
        path = normalize(path);
        return visiblePendingIndex.hasDescendant(path);
    }

    private void rebuildIndexes() {
        selectedIndex = new LoginStatePathIndex(selected);
        pendingIndex = new LoginStatePathIndex(pending);
        visiblePendingIndex = new LoginStatePathIndex();
        for (String path : pending) if (!selectedIndex.hasAncestor(path)) visiblePendingIndex.add(path);
    }

    private boolean isMissing(String path) {
        return !LoginStateSync.pathState(path).isExists();
    }

    private String normalize(String path) {
        return LoginStateSync.normalizePath(path);
    }

    private void edit(String path) {
        if (TextUtils.isEmpty(path)) return;
        Task.execute(() -> {
            try {
                LoginStateSync.TextPreview preview = LoginStateSync.preview(path);
                PreviewPayload payload = PreviewPayload.create(preview);
                App.post(() -> showEditor(payload));
            } catch (Exception e) {
                App.post(() -> Notify.show(e.getMessage()));
            }
        });
    }

    private void showEditor(PreviewPayload payload) {
        LoginStateSync.TextPreview preview = payload.preview;
        if (editor != null && editor.isShowing()) editor.dismiss();

        LinearLayoutCompat container = new LinearLayoutCompat(requireContext());
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(ResUtil.dp2px(20), ResUtil.dp2px(8), ResUtil.dp2px(20), 0);
        TextView label = new TextView(requireContext());
        label.setText(preview.getPath());
        label.setTextColor(ThemeController.current().colorOnSurfaceVariant());
        label.setTextSize(12);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        container.addView(label, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (preview.isTruncated() || !preview.isEditable()) {
            TextView meta = new TextView(requireContext());
            meta.setText(preview.isTruncated() ? R.string.login_state_preview_truncated : R.string.login_state_preview_readonly);
            meta.setTextColor(ThemeController.current().colorWarning());
            meta.setTextSize(12);
            meta.setPadding(0, ResUtil.dp2px(6), 0, 0);
            container.addView(meta, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        LinearLayoutCompat.LayoutParams inputParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (DialogSafeArea.height(requireActivity()) * 0.52f));
        inputParams.topMargin = ResUtil.dp2px(8);
        SafeScrollEditText input = null;
        if (payload.listPreview) {
            RecyclerView previewList = new RecyclerView(requireContext());
            previewList.setLayoutManager(new LinearLayoutManager(requireContext()));
            previewList.setAdapter(new TextRowAdapter(payload.rows));
            previewList.setBackground(editorBackground());
            previewList.setClipToPadding(false);
            previewList.setPadding(0, ResUtil.dp2px(4), 0, ResUtil.dp2px(4));
            container.addView(previewList, inputParams);
        } else {
            input = editText(preview);
            container.addView(input, inputParams);
        }

        SafeScrollEditText finalInput = input;
        editor = LightDialog.create(requireContext(), getString(R.string.login_state_edit), container, payload.editable && finalInput != null ? getString(R.string.dialog_positive) : null, v -> save(editor, preview.getPath(), finalInput.getText() == null ? "" : finalInput.getText().toString()), getString(R.string.dialog_negative), null);
        editor.setCancelable(false);
        editor.setCanceledOnTouchOutside(false);
        editor.show();
    }

    private SafeScrollEditText editText(LoginStateSync.TextPreview preview) {
        SafeScrollEditText input = new SafeScrollEditText(requireContext());
        input.setText(preview.getContent(), TextView.BufferType.EDITABLE);
        input.setTextColor(ThemeController.current().colorOnSurface());
        input.setSelectAllOnFocus(false);
        input.setSingleLine(false);
        input.setHorizontallyScrolling(true);
        input.setMinLines(8);
        input.setTextSize(12);
        input.setTypeface(Typeface.MONOSPACE);
        input.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(10), ResUtil.dp2px(10), ResUtil.dp2px(10));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setGravity(Gravity.START | Gravity.TOP);
        input.setBackground(editorBackground());
        input.setCursorVisible(preview.isEditable());
        if (!preview.isEditable()) input.setKeyListener(null);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) view.getParent().requestDisallowInterceptTouchEvent(false);
            else view.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        return input;
    }

    private GradientDrawable editorBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ThemeController.current().colorSurfaceContainerHigh());
        drawable.setCornerRadius(ResUtil.dp2px(8));
        drawable.setStroke(1, ThemeController.current().colorOutlineVariant());
        return drawable;
    }

    private void save(Dialog dialog, String path, String content) {
        Task.execute(() -> {
            try {
                LoginStateSync.write(path, content);
                addPath(path);
                App.post(() -> {
                    dialog.dismiss();
                    Notify.show(R.string.login_state_saved);
                    loadStateAsync(false);
                    expandPath(path);
                });
            } catch (Exception e) {
                App.post(() -> Notify.show(e.getMessage()));
            }
        });
    }

    private String detail(Row row) {
        LoginStateSync.TreeItem item = row.item;
        String relative = shortPath(item.getPath());
        LoginStateSync.Candidate candidate = findings.get(item.getPath());
        if (candidate != null && isPending(item.getPath())) return getString(R.string.login_state_pending_detail, candidate.getReason(), relative, FileUtil.byteCountToDisplaySize(item.getSize()));
        if (item.isDir()) return relative;
        String size = FileUtil.byteCountToDisplaySize(item.getSize());
        String time = item.getModified() <= 0 ? "" : Formatters.LOCAL_DATETIME.format(Instant.ofEpochMilli(item.getModified()).atZone(ZoneId.systemDefault()));
        return TextUtils.isEmpty(time) ? relative + " · " + size : relative + " · " + size + " · " + time;
    }

    private String shortPath(String path) {
        path = normalize(path);
        int index = path.indexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private String name(String path) {
        path = normalize(path);
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private int depth(String path) {
        path = shortPath(path);
        if (path.isEmpty()) return 0;
        int depth = 0;
        for (int i = 0; i < path.length(); i++) if (path.charAt(i) == '/') depth++;
        return depth;
    }

    private String stateText(Row row) {
        String path = row.item.getPath();
        if (isMissing(path)) return getString(R.string.login_state_state_missing);
        if (isPending(path)) return getString(R.string.login_state_state_pending);
        if (row.item.isDir() && hasPendingChild(path)) return getString(R.string.login_state_state_has_pending);
        int state = stateOf(path);
        if (state == MaterialCheckBox.STATE_CHECKED) return getString(R.string.login_state_state_selected);
        if (state == MaterialCheckBox.STATE_INDETERMINATE) return getString(R.string.login_state_state_partial);
        return "";
    }

    private int stateColor(Row row) {
        String path = row.item.getPath();
        if (isMissing(path)) return ThemeController.current().colorError();
        if (isPending(path) || (row.item.isDir() && hasPendingChild(path))) return ThemeController.current().colorWarning();
        return ThemeController.current().colorPrimary();
    }

    private int nameColor(Row row) {
        if (isPending(row.item.getPath()) || (row.item.isDir() && hasPendingChild(row.item.getPath()))) return ThemeController.current().colorWarning();
        if (isMissing(row.item.getPath())) return ThemeController.current().colorError();
        return ThemeController.current().colorOnSurface();
    }

    private int iconFor(LoginStateSync.TreeItem item) {
        if (item.isDir()) return R.drawable.ic_folder;
        return item.isText() ? R.drawable.ic_login_state_file : R.drawable.ic_file;
    }

    private int expandIconFor(Row row) {
        return expanded.contains(row.item.getPath()) ? R.drawable.ic_detail_minus : R.drawable.ic_detail_plus;
    }

    private String contentDescription(Row row) {
        if (row.item.isDir()) return getString(expanded.contains(row.item.getPath()) ? R.string.login_state_tree_collapse : R.string.login_state_tree_expand);
        return getString(R.string.login_state_edit);
    }

    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.ViewHolder> {

        private final List<Row> rows;

        private TreeAdapter(List<Row> rows) {
            this.rows = rows;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterLoginStateTreeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(rows.get(position));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterLoginStateTreeBinding binding;

            private ViewHolder(@NonNull AdapterLoginStateTreeBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            private void bind(Row row) {
                LoginStateSync.TreeItem item = row.item;
                String state = stateText(row);
                ViewGroup.LayoutParams indentParams = binding.indent.getLayoutParams();
                indentParams.width = ResUtil.dp2px(row.depth * INDENT_DP);
                binding.indent.setLayoutParams(indentParams);
                binding.name.setText(item.getName());
                binding.name.setTextColor(nameColor(row));
                binding.path.setText(detail(row));
                binding.icon.setImageResource(iconFor(item));
                binding.check.setCheckedState(stateOf(item.getPath()));
                binding.check.setVisibility(item.isSelectable() ? View.VISIBLE : View.INVISIBLE);
                binding.state.setVisibility(TextUtils.isEmpty(state) ? View.GONE : View.VISIBLE);
                binding.state.setText(state);
                binding.state.setTextColor(stateColor(row));
                binding.expand.setVisibility(item.isDir() ? View.VISIBLE : View.INVISIBLE);
                binding.expand.setImageResource(expandIconFor(row));
                binding.expand.setContentDescription(contentDescription(row));
                binding.expand.setOnClickListener(v -> {
                    if (item.isDir()) toggleExpanded(item.getPath());
                });
                binding.check.setOnClickListener(v -> toggleSelected(item.getPath()));
                binding.getRoot().setOnClickListener(v -> {
                    if (item.isDir()) toggleExpanded(item.getPath());
                    else edit(item.getPath());
                });
                binding.getRoot().setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN || !item.isDir()) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !expanded.contains(item.getPath())) {
                        toggleExpanded(item.getPath());
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && expanded.contains(item.getPath())) {
                        toggleExpanded(item.getPath());
                        return true;
                    }
                    return false;
                });
            }
        }
    }

    private static class Row {

        private final LoginStateSync.TreeItem item;
        private final int depth;

        private Row(LoginStateSync.TreeItem item, int depth) {
            this.item = item;
            this.depth = depth;
        }
    }

    private record PanelCount(int selected, int pending) {

        private boolean isEmpty() {
            return selected == 0 && pending == 0;
        }
    }

    private record PreviewPayload(LoginStateSync.TextPreview preview, List<String> rows, boolean listPreview, boolean editable) {

        private static PreviewPayload create(LoginStateSync.TextPreview preview) {
            List<String> rows = rows(preview.getContent());
            boolean listPreview = !preview.isEditable() || preview.getContent().length() > MAX_EDIT_CHARS || maxLine(preview.getContent()) > MAX_EDIT_LINE_CHARS;
            return new PreviewPayload(preview, rows, listPreview, preview.isEditable() && !listPreview);
        }

        private static int maxLine(String text) {
            int max = 0;
            int count = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') {
                    max = Math.max(max, count);
                    count = 0;
                } else {
                    count++;
                }
            }
            return Math.max(max, count);
        }

        private static List<String> rows(String text) {
            List<String> result = new ArrayList<>();
            String value = text == null ? "" : text;
            int lineStart = 0;
            for (int i = 0; i <= value.length(); i++) {
                if (i < value.length() && value.charAt(i) != '\n' && value.charAt(i) != '\r') continue;
                addChunks(result, value.substring(lineStart, i));
                if (i + 1 < value.length() && value.charAt(i) == '\r' && value.charAt(i + 1) == '\n') i++;
                lineStart = i + 1;
            }
            if (result.isEmpty()) result.add("");
            return result;
        }

        private static void addChunks(List<String> result, String line) {
            if (line.length() == 0) {
                result.add("");
                return;
            }
            for (int start = 0; start < line.length(); start += TEXT_ROW_CHARS) {
                int end = Math.min(line.length(), start + TEXT_ROW_CHARS);
                result.add(line.substring(start, end));
            }
        }
    }

    private static class TextRowAdapter extends RecyclerView.Adapter<TextRowAdapter.ViewHolder> {

        private final List<String> rows;

        private TextRowAdapter(List<String> rows) {
            this.rows = rows;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            text.setTextColor(ThemeController.current().colorOnSurface());
            text.setTextSize(12);
            text.setTypeface(Typeface.MONOSPACE);
            text.setLineSpacing(0, 1.08f);
            text.setTextIsSelectable(true);
            text.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(3), ResUtil.dp2px(10), ResUtil.dp2px(3));
            text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.text.setText(rows.get(position));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView text;

            private ViewHolder(@NonNull TextView itemView) {
                super(itemView);
                this.text = itemView;
            }
        }
    }
}
