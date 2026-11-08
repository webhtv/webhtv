package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogLoginStateLearnBinding;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoginStateLearnDialog extends BaseAlertDialog {

    private static final int MAX_SECTION_ROWS = 4;

    private DialogLoginStateLearnBinding binding;
    private Runnable callback;
    private boolean learning;
    private int bindGeneration;

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof LoginStateLearnDialog && fragment.isAdded() && !fragment.isRemoving()) return;
        LoginStateLearnDialog dialog = new LoginStateLearnDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogLoginStateLearnBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new WebHtvAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_Dialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        bindState();
    }

    @Override
    protected void initEvent() {
        binding.reset.setOnClickListener(v -> onReset());
        binding.manage.setOnClickListener(v -> onManage());
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
    }

    @Override
    public void onStart() {
        super.onStart();
        resize();
        binding.positive.requestFocus();
    }

    private void bindState() {
        FragmentActivity activity = requireActivity();
        learning = LoginStateSync.hasLearningSnapshot();
        binding.positive.setText(learning ? R.string.login_state_finish : R.string.login_state_start);
        binding.quickContent.removeAllViews();
        int generation = ++bindGeneration;
        Task.execute(() -> {
            List<String> learned = LoginStateSync.learnedPaths();
            List<String> pending = LoginStateSync.pendingPaths();
            List<LoginStateSync.Candidate> findings = LoginStateSync.findings();
            QuickState state = QuickState.create(learned, pending, findings);
            App.post(() -> {
                if (binding == null || generation != bindGeneration || !isAdded()) return;
                binding.message.setText(activity.getString(learning ? R.string.login_state_learning_message : R.string.login_state_message, state.learnedCount()));
                binding.reset.setVisibility(state.pendingCount() == 0 && state.findingsCount() == 0 ? View.GONE : View.VISIBLE);
                binding.quickContent.removeAllViews();
                binding.quickContent.addView(quickView(activity, state), new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                setScrollHeight(activity);
            });
        });
    }

    private void setScrollHeight(FragmentActivity activity) {
        ViewGroup.LayoutParams params = binding.contentScroll.getLayoutParams();
        params.height = Math.max(ResUtil.dp2px(190), Math.min(ResUtil.dp2px(300), (int) (DialogSafeArea.height(activity) * 0.34f)));
        binding.contentScroll.setLayoutParams(params);
    }

    private void resize() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min((int) (ResUtil.getScreenWidth(requireContext()) * (land ? 0.68f : 0.92f)), ResUtil.dp2px(640));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private void onReset() {
        LoginStateSync.resetLearningResults();
        Notify.show(R.string.login_state_reset_results_done);
        if (callback != null) callback.run();
        bindState();
    }

    private void onManage() {
        FragmentActivity activity = requireActivity();
        dismiss();
        LoginStatePathDialog.show(activity, callback);
    }

    private void onPositive() {
        FragmentActivity activity = requireActivity();
        boolean finish = learning;
        dismiss();
        run(activity, finish, callback);
    }

    private static LinearLayoutCompat quickView(FragmentActivity activity, QuickState state) {
        LinearLayoutCompat container = new LinearLayoutCompat(activity);
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(0, 0, 0, ResUtil.dp2px(2));
        addSection(activity, container, R.string.login_state_learned_title, state.learned(), state.learnedCount(), 0xFFEAF3FF, 0xFF174EA6);
        addSection(activity, container, R.string.login_state_pending_title, state.pending(), state.pendingCount(), 0xFFFFF4E5, 0xFF9A5B00);
        addSection(activity, container, R.string.login_state_findings_title, state.findings(), state.findingsCount(), 0xFFEAF7EE, 0xFF137333);
        if (container.getChildCount() == 0) addEmpty(activity, container);
        return container;
    }

    private static List<QuickItem> pathItems(List<String> paths, int reasonRes) {
        List<QuickItem> result = new ArrayList<>();
        int count = 0;
        for (String path : paths) {
            if (count++ >= MAX_SECTION_ROWS) break;
            result.add(new QuickItem(path, ResUtil.getString(reasonRes), LoginStateSync.displayPath(path)));
        }
        return result;
    }

    private static List<QuickItem> candidateItems(List<LoginStateSync.Candidate> candidates) {
        List<QuickItem> result = new ArrayList<>();
        int count = 0;
        for (LoginStateSync.Candidate item : candidates) {
            if (count++ >= MAX_SECTION_ROWS) break;
            result.add(new QuickItem(item.getPath(), item.getReason(), item.getDisplayPath()));
        }
        return result;
    }

    private static List<QuickItem> pendingCandidates(List<LoginStateSync.Candidate> findings, List<String> pending) {
        Map<String, LoginStateSync.Candidate> byPath = new LinkedHashMap<>();
        for (LoginStateSync.Candidate item : findings) byPath.put(LoginStateSync.normalizePath(item.getPath()), item);
        List<QuickItem> result = new ArrayList<>();
        int count = 0;
        for (String path : pending) {
            if (count++ >= MAX_SECTION_ROWS) break;
            path = LoginStateSync.normalizePath(path);
            LoginStateSync.Candidate item = byPath.get(path);
            result.add(item == null ? new QuickItem(path, ResUtil.getString(R.string.login_state_state_pending), LoginStateSync.displayPath(path)) : new QuickItem(item.getPath(), item.getReason(), item.getDisplayPath()));
        }
        return result;
    }

    private static void addSection(FragmentActivity activity, LinearLayoutCompat parent, int titleRes, List<QuickItem> items, int totalCount, int bgColor, int accentColor) {
        if (items == null || items.isEmpty()) return;
        LinearLayoutCompat card = sectionCard(activity, titleRes, bgColor, accentColor);
        int count = 0;
        for (QuickItem item : items) {
            if (count++ >= MAX_SECTION_ROWS) break;
            addRow(activity, card, item.path, item.reason, item.displayPath);
        }
        if (totalCount > MAX_SECTION_ROWS) addMore(activity, card, totalCount - MAX_SECTION_ROWS);
        parent.addView(card, sectionParams(parent));
    }

    private static LinearLayoutCompat sectionCard(FragmentActivity activity, int titleRes, int bgColor, int accentColor) {
        LinearLayoutCompat card = new LinearLayoutCompat(activity);
        card.setOrientation(LinearLayoutCompat.VERTICAL);
        card.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(9), ResUtil.dp2px(12), ResUtil.dp2px(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(bgColor);
        background.setCornerRadius(ResUtil.dp2px(8));
        background.setStroke(1, Color.argb(42, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        card.setBackground(background);
        TextView title = new TextView(activity);
        title.setText(titleRes);
        title.setTextColor(accentColor);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static LinearLayoutCompat.LayoutParams sectionParams(LinearLayoutCompat parent) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) params.topMargin = ResUtil.dp2px(8);
        return params;
    }

    private static void addRow(FragmentActivity activity, LinearLayoutCompat parent, String path, String reason, String displayPath) {
        TextView row = new TextView(activity);
        String meta = TextUtils.isEmpty(reason) ? "" : reason + "\n";
        row.setText(meta + (TextUtils.isEmpty(displayPath) ? LoginStateSync.displayPath(path) : displayPath));
        row.setTextColor(0xCC000000);
        row.setTextSize(12);
        row.setLineSpacing(0, 1.12f);
        row.setTextIsSelectable(true);
        row.setPadding(0, ResUtil.dp2px(7), 0, 0);
        parent.addView(row, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addMore(FragmentActivity activity, LinearLayoutCompat parent, int count) {
        TextView more = new TextView(activity);
        more.setText(activity.getString(R.string.login_state_more_count, count));
        more.setTextColor(0x99000000);
        more.setTextSize(12);
        more.setPadding(0, ResUtil.dp2px(7), 0, 0);
        parent.addView(more, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addEmpty(FragmentActivity activity, LinearLayoutCompat parent) {
        TextView empty = new TextView(activity);
        empty.setText(R.string.login_state_empty);
        empty.setTextColor(0x99000000);
        empty.setTextSize(13);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, ResUtil.dp2px(28), 0, ResUtil.dp2px(28));
        parent.addView(empty, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void run(FragmentActivity activity, boolean finish, Runnable callback) {
        Task.execute(() -> {
            if (finish) finish(activity, callback);
            else begin(callback);
        });
    }

    private static void begin(Runnable callback) {
        LoginStateSync.beginLearning();
        App.post(() -> {
            Notify.show(R.string.login_state_started);
            if (callback != null) callback.run();
        });
    }

    private static void finish(FragmentActivity activity, Runnable callback) {
        LoginStateSync.LearnResult result = LoginStateSync.finishLearning();
        int selected = result.getSelected().size();
        int pending = LoginStateSync.pendingPaths().size();
        App.post(() -> {
            Notify.show(App.get().getString(R.string.login_state_finished, selected, pending));
            if (callback != null) callback.run();
            if (!activity.isFinishing()) show(activity, callback);
        });
    }

    private static class QuickItem {

        private final String path;
        private final String reason;
        private final String displayPath;

        private QuickItem(String path, String reason, String displayPath) {
            this.path = path;
            this.reason = reason;
            this.displayPath = displayPath;
        }
    }

    private record QuickState(List<QuickItem> learned, int learnedCount, List<QuickItem> pending, int pendingCount, List<QuickItem> findings, int findingsCount) {

        private static QuickState create(List<String> learned, List<String> pending, List<LoginStateSync.Candidate> findings) {
            return new QuickState(
                    pathItems(learned, R.string.login_state_state_selected), learned.size(),
                    pendingCandidates(findings, pending), pending.size(),
                    candidateItems(findings), findings.size());
        }
    }
}
