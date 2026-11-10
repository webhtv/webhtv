package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Toast;
import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FocusLoop;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog extends BaseGlassDialog implements SiteAdapter.OnClickListener, SiteAdapter.OnDeleteListener {

    private static final int GRID_COUNT = 3;
    private static final String TAG = "site_dialog";
    private static final int INITIAL_BATCH = 48;
    /** 站点卡片高度，必须与 adapter_site*.xml 的 layout_height 一致。 */
    private static final int ROW_HEIGHT_DP = 52;
    /** 网格行间距，必须与 SpaceItemDecoration(GRID_COUNT, 16) 的 16 一致。 */
    private static final int ROW_SPACING_DP = 16;
    /**
     * 面板占屏宽的比例，对齐仓库既有大弹窗惯例（CacheManagementDialog.SCREEN_FRACTION = 0.9f）。
     * 这里刻意不再叠加 dp 上限：真机电视常见 density 1.0，1920px 屏即 1920dp，旧上限 1180dp
     * 会把面板压到只剩 61% 屏宽，分组标签行放不下、末尾标签被硬裁。
     */
    private static final float PANEL_WIDTH_RATIO = 0.9f;
    /** 极窄屏兜底宽度，避免面板窄到无法阅读。 */
    private static final int PANEL_MIN_WIDTH_DP = 480;
    /**
     * 面板高度占可用高度的上限。同样对齐 0.9 惯例，让一屏能多显示几行站点；
     * 网格高度仍按整数行收敛，不会把末行裁半截。
     */
    private static final float PANEL_MAX_HEIGHT_RATIO = 0.9f;

    private static String selectedGroup = "";

    private RecyclerView.ItemDecoration decoration;
    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;
    private List<String> groups;
    private List<String> originalGroups;
    private String reorderingGroup = "";
    private long reorderStartedAt;
    private long showStart;
    private boolean groupReordering;
    private boolean groupSwitching;
    private boolean action;
    private boolean listLoaded;
    private int type;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog setListener(SiteListener listener) {
        this.listener = listener;
        return this;
    }

    public SiteDialog search() {
        type = 1;
        return this;
    }

    public SiteDialog action() {
        action = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        showStart = System.currentTimeMillis();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        log("click received action=%s type=%s", action, type);
        show(activity.getSupportFragmentManager(), TAG);
    }

    private int getCount() {
        return GRID_COUNT;
    }

    @Override
    protected float getWidthRatio() {
        return action ? 0.92f : 0.9f;
    }

    @Override
    protected boolean onMenuKey() {
        SiteSortDialog.create().setListener(() -> {
            // 排序完成后刷新站点列表并再次聚焦到当前站
            if (adapter != null) {
                adapter.refresh();
                binding.recycler.post(() -> scrollAndFocusActiveSite());
            }
        }).show(getActivity());
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (action && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (adapter != null && FocusLoop.handleRecyclerGrid(binding.recycler, adapter.getTotalCount(), GRID_COUNT, FocusLoop.Mode.BOTH, event))
                return true;
            if (KeyUtil.isMenuKey(event) && onMenuKey()) {
                dismiss();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(d -> {
            if (listLoaded) waitForLayoutAndFocus();
        });
        return dialog;
    }

    @Override
    protected void initView() {
        initShellView();
        loadList(true);
    }

    private void initShellView() {
        long start = System.currentTimeMillis();
        updateWindowWidth();
        setRecyclerHeight(INITIAL_BATCH);
        binding.header.setVisibility(View.VISIBLE);
        binding.title.setText(action ? R.string.site_dialog_title_action : R.string.site_dialog_title);
        binding.count.setVisibility(View.GONE);
        binding.searchBar.setVisibility(View.GONE);
        binding.keyword.setVisibility(View.GONE);
        binding.actionGap.setVisibility(View.GONE);
        binding.action.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.search.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.change.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.select.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.cancel.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.mode.setVisibility(View.GONE);
        SiteDialogTheme.applyShell(binding.getRoot(), binding.keyword, binding.config, binding.search,
                binding.change, binding.select, binding.cancel, binding.mode);
        setActionEnabled(false);
        binding.recycler.setAdapter(null);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        log("shell configured cost=%sms total=%sms", cost(start), cost());
    }

    private void loadList(boolean immediate) {
        if (binding == null || listLoaded) return;
        listLoaded = true;
        long start = System.currentTimeMillis();
        adapter = new SiteAdapter(this);
        adapter.setDisplayLimit(INITIAL_BATCH);
        groups = getGroups();
        normalizeSelectedGroup();
        if (!TextUtils.isEmpty(selectedGroup)) {
            adapter.filter(selectedGroup, "");
            adapter.setDisplayLimit(INITIAL_BATCH);
        }
        log("adapter created cost=%sms items=%s action=%s immediate=%s", cost(start), adapter.getTotalCount(), action, immediate);
        if (adapter.getTotalCount() == 0) {
            log("dismiss empty total=%sms", cost());
            dismiss();
            return;
        }
        long layoutStart = System.currentTimeMillis();
        setType(type);
        setRecyclerView();
        setMode();
        setGroupView();
        setRecyclerHeight(adapter.getItemCount());
        setActionEnabled(true);
        updateCountText();
        log("view configured cost=%sms total=%sms", cost(layoutStart), cost());
        runAfterRecyclerLayout(() -> {
            if (adapter != null) adapter.showAll();
            log("list expanded total=%sms items=%s", cost(), adapter == null ? -1 : adapter.getItemCount());
            // 首帧后 wrap_content 的标题/分组行才有真实高度，此时再收敛一次网格高度，
            // 面板高度才是「内容 + 整数行网格」，不会在屏幕外多留一行。
            if (binding != null && adapter != null) {
                setRecyclerHeight(adapter.getItemCount());
                updateCountText();
            }
            focusSelectedSite();
        });
    }

    private void waitForLayoutAndFocus() {
        if (binding == null || binding.recycler == null) return;
        binding.recycler.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (adapter == null || adapter.getItemCount() < adapter.getTotalCount()) {
                    return;
                }
                ViewTreeObserver obs = binding.recycler.getViewTreeObserver();
                if (obs.isAlive()) obs.removeOnGlobalLayoutListener(this);
                scrollAndFocusActiveSite();
            }
        });
    }

    private boolean mScrolledToActive = false;

    private void scrollAndFocusActiveSite() {
        if (mScrolledToActive || adapter == null || binding == null) return;
        List<Site> showList = adapter.getItems();
        Site active = VodConfig.get().getHome();
        if (active == null || active.getKey() == null || active.getKey().isEmpty()) return;
        int targetPos = -1;
        for (int i = 0; i < showList.size(); i++) {
            if (showList.get(i).getKey().equals(active.getKey())) {
                targetPos = i;
                break;
            }
        }
        RecyclerView.LayoutManager lm = binding.recycler.getLayoutManager();
        if (targetPos < 0 || !(lm instanceof GridLayoutManager glm)) return;
        mScrolledToActive = true;
        final int finalTargetPos = targetPos;
        binding.recycler.post(() -> {
            if (binding == null || binding.recycler == null || adapter == null) return;
            int height = binding.recycler.getHeight();
            if (height <= 0) {
                mScrolledToActive = false;
                binding.recycler.postDelayed(() -> scrollAndFocusActiveSite(), 50);
                return;
            }
            int itemHeight = ResUtil.dp2px(ITEM_HEIGHT) + ResUtil.dp2px(ITEM_SPACE);
            int centerOffset = Math.max(0, (height - itemHeight) / 2);
            glm.scrollToPositionWithOffset(finalTargetPos, centerOffset);
            binding.recycler.postDelayed(() -> {
                if (binding == null || binding.recycler == null) return;
                RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(finalTargetPos);
                if (holder != null && holder.itemView != null) {
                    holder.itemView.requestFocus();
                    log("success request focus pos=" + finalTargetPos);
                }
            }, 50);
        });
    }

    @Override
    protected void initEvent() {
        binding.config.setOnClickListener(v -> {
            FragmentActivity activity = requireActivity();
            dismiss();
            App.post(() -> HistoryDialog.create().vod().readOnly().show(activity, item -> loadConfig(activity, item)), 100);
        });
        binding.mode.setOnClickListener(this::onMode);
        binding.select.setOnClickListener(v -> {
            if (adapter != null) adapter.selectAll();
        });
        binding.cancel.setOnClickListener(v -> {
            if (adapter != null) adapter.cancelAll();
        });
        binding.search.setOnClickListener(v -> setType(v.isSelected() ? 0 : 1));
        binding.change.setOnClickListener(v -> setType(v.isSelected() ? 0 : 2));
        binding.keyword.addTextChangedListener(new com.fongmi.android.tv.ui.custom.CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter == null) return;
                adapter.filter(selectedGroup, s.toString());
                setRecyclerView();
                setRecyclerHeight(adapter.getItemCount());
                setMode();
                updateWindowWidth();
                scrollAndFocusActiveSite();
            }
        });
    }

    private void setRecyclerView() {
        if (binding.recycler.getAdapter() == null) binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration == null) binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        if (binding.recycler.getLayoutManager() == null) binding.recycler.setLayoutManager(new GridLayoutManager(requireActivity(), getCount()));
        log("recycler ready adapter=%s layout=%s total=%sms", binding.recycler.getAdapter() != null, binding.recycler.getLayoutManager() != null, cost());
    }

    private void setRecyclerHeight(int count) {
        // 面板不再铺满：网格高度收敛到整数行，多余的行交给滚动，避免最后一行被裁掉半截。
        // 高度在这里显式给定，因此不再需要 maxHeight 兜底（Integer.MAX_VALUE 还会被
        // CustomRecyclerView 折成 AT_MOST 的畸形 spec）。
        ViewGroup.LayoutParams params = binding.recycler.getLayoutParams();
        params.height = gridHeight(count);
        binding.recycler.setLayoutParams(params);
    }

    private void setType(int type) {
        binding.search.setSelected(type == 1);
        binding.change.setSelected(type == 2);
        binding.select.setClickable(type > 0);
        binding.cancel.setClickable(type > 0);
        this.type = type;
        if (adapter != null) adapter.setType(type);
        setActionEnabled(listLoaded && adapter != null);
    }

    private void setMode() {
        binding.mode.setEnabled(false);
    }

    private void onMode(View view) {
        setRecyclerView();
        setMode();
        updateWindowWidth();
    }

    private void setActionEnabled(boolean enabled) {
        binding.search.setEnabled(enabled);
        binding.change.setEnabled(enabled);
        // Keep bulk actions in the D-pad focus chain even in plain switch mode.
        // setType() controls whether they are clickable; enabled only reflects loading state.
        binding.select.setEnabled(enabled);
        binding.cancel.setEnabled(enabled);
    }

    @Override
    public void onItemClick(Site item) {
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    @Override
    public void onDelete(Site item) {
        if (!item.isFile()) return;
        FragmentActivity act = requireActivity();
        if (act == null || act.isFinishing() || act.isDestroyed()) {
            return;
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle(R.string.setting_site_delete_title)
                .setMessage(getString(R.string.setting_site_delete_message, item.getName()))
                .setPositiveButton(android.R.string.ok, (d, which) -> deleteFileSite(item))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(glassBackground());
            View content = w.getDecorView().findViewById(android.R.id.content);
            if (content != null) content.setBackground(null);
        }
    }

    private void deleteFileSite(Site item) {
        String type = item.getFileType();
        String fileName = item.getFileName();
        if (fileName.isEmpty()) return;
        String subDir = switch (type) {
            case "XBPQ" -> "sites-json";
            case "JS" -> "sites-js/api";
            case "PY" -> "sites-py";
            case "RAW" -> "sites";
            default -> "";
        };
        if (subDir.isEmpty()) return;
        File file = new File(Path.root() + "/tvbox/" + subDir, fileName);
        boolean deleted = file.exists() && file.delete();
        if (!deleted && file.exists()) {
            Toast.makeText(requireActivity(), R.string.setting_site_delete_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Site currentHome = VodConfig.get().getHome();
        List<Site> siteList = VodConfig.get().getSites();
        if (currentHome != null && currentHome.getKey().equals(item.getKey())) {
            int delIndex = -1;
            for (int i = 0; i < siteList.size(); i++) {
                if (siteList.get(i).getKey().equals(item.getKey())) {
                    delIndex = i;
                    break;
                }
            }
            Site fallbackHome = new Site();
            if (delIndex != -1 && siteList.size() > 1) {
                if (delIndex + 1 < siteList.size()) {
                    fallbackHome = siteList.get(delIndex + 1);
                } else {
                    fallbackHome = siteList.get(delIndex - 1);
                }
            }
            VodConfig.get().setHome(fallbackHome);
        }
        if (!siteList.isEmpty()) siteList.remove(item);
        Toast.makeText(requireActivity(), getString(R.string.setting_site_delete_done, item.getName()), Toast.LENGTH_SHORT).show();
        if (adapter != null && binding != null) {
            adapter.removeSite(item);
            setRecyclerHeight(adapter.getItemCount());
            binding.recycler.post(this::scrollAndFocusActiveSite);
        }
    }

    private void loadConfig(FragmentActivity activity, Config config) {
        if (config.getUrl().equals(VodConfig.getUrl())) return;
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                Notify.progress(activity);
            }

            @Override
            public void success() {
                Notify.dismiss();
                LiveConfig.get().clear();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        });
    }

    private void runAfterRecyclerLayout(Runnable action) {
        if (binding == null || binding.recycler == null) {
            if (action != null) action.run();
            return;
        }
        binding.recycler.post(action);
    }

    private long cost() {
        return cost(showStart);
    }

    private long cost(long start) {
        return System.currentTimeMillis() - start;
    }

    private void log(String msg, Object... args) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log(TAG, msg, args);
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter != null && adapter.getItemCount() == 0) dismiss();
        if (binding != null && binding.recycler != null && adapter != null) {
            binding.recycler.postDelayed(this::scrollAndFocusActiveSite, 50);
        }
    }

    private List<String> getGroups() {
        return SiteGroupOrderStore.sort(new ArrayList<>(Site.getGroups(VodConfig.get().getSites())));
    }

    private void normalizeSelectedGroup() {
        if (groups == null || groups.isEmpty()) {
            selectedGroup = "";
            return;
        }
        if (!TextUtils.isEmpty(selectedGroup) && !groups.contains(selectedGroup)) selectedGroup = "";
    }

    private void setGroupView() {
        if (groups == null || groups.isEmpty()) {
            selectedGroup = "";
            binding.groupScroll.setVisibility(View.GONE);
            binding.groupHint.setVisibility(View.GONE);
            return;
        }
        binding.groupScroll.setVisibility(View.VISIBLE);
        binding.groupList.removeAllViews();
        binding.groupList.addView(getGroupView("", getDialogActivity().getString(R.string.site_group_all)));
        for (String group : groups) binding.groupList.addView(getGroupView(group, group));
        updateGroupView();
        binding.groupList.post(this::requestGroupFocus);
    }

    private androidx.appcompat.widget.AppCompatTextView getGroupView(String group, String text) {
        androidx.appcompat.widget.AppCompatTextView button = new androidx.appcompat.widget.AppCompatTextView(getDialogActivity());
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 间距与内边距收紧到 10dp/14dp：面板 90% 宽时 14 个标签刚好放得下，
        // 不再让末尾标签被面板右缘裁掉半个（超出时仍由 groupScroll 横向滚动兜底）。
        params.setMarginEnd(ResUtil.dp2px(10));
        button.setLayoutParams(params);
        button.setTag(group);
        button.setText(text);
        button.setSingleLine(true);
        button.setAllCaps(false);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(ResUtil.dp2px(14), ResUtil.dp2px(8), ResUtil.dp2px(14), ResUtil.dp2px(8));
        SiteDialogTheme.applyGroup(button);
        button.setFocusable(true);
        button.setClickable(true);
        button.setNextFocusDownId(binding.recycler.getId());
        button.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !groupSwitching) selectGroup(group, button);
        });
        button.setOnClickListener(v -> onGroupClick(group, v));
        button.setOnKeyListener((v, keyCode, event) -> onGroupKey(group, keyCode, event));
        if (!TextUtils.isEmpty(group)) button.setOnLongClickListener(v -> startGroupReorder(group, v));
        return button;
    }

    private boolean startGroupReorder(String group, View view) {
        if (groups == null || groups.size() < 2 || TextUtils.isEmpty(group)) return false;
        if (groupReordering) return true;
        originalGroups = new ArrayList<>(groups);
        reorderingGroup = group;
        reorderStartedAt = SystemClock.uptimeMillis();
        groupReordering = true;
        selectedGroup = group;
        updateGroupView();
        centerGroup(view);
        Notify.show(R.string.site_group_sort_tv_active);
        return true;
    }

    private boolean onGroupKey(String group, int keyCode, KeyEvent event) {
        if (!groupReordering || !TextUtils.equals(group, reorderingGroup)) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) moveGroup(group, keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        if (KeyUtil.isEnterKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getDownTime() >= reorderStartedAt) saveGroupReorder();
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private void moveGroup(String group, int direction) {
        if (!SiteGroupOrderStore.move(groups, group, direction)) return;
        DialogSiteBinding current = binding;
        if (current == null) return;
        current.groupList.post(() -> {
            if (binding == current) setGroupView();
        });
    }

    private void saveGroupReorder() {
        if (!groupReordering) return;
        SiteGroupOrderStore.save(Site.getGroups(VodConfig.get().getSites()), groups);
        View active = findGroupView(reorderingGroup);
        resetGroupReorderState();
        updateGroupView();
        if (active != null) active.requestFocus();
        Notify.show(R.string.site_group_sort_saved);
    }

    private void cancelGroupReorder() {
        if (!groupReordering) return;
        if (originalGroups != null) {
            groups.clear();
            groups.addAll(originalGroups);
        }
        resetGroupReorderState();
        setGroupView();
        Notify.show(R.string.site_group_sort_cancelled);
    }

    private boolean onDialogKey(int keyCode, KeyEvent event) {
        if (!groupReordering || keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) return false;
        cancelGroupReorder();
        return true;
    }

    private void resetGroupReorderState() {
        groupReordering = false;
        groupSwitching = false;
        reorderingGroup = "";
        originalGroups = null;
        reorderStartedAt = 0;
    }

    private void requestGroupFocus() {
        if (binding == null || binding.groupList.getChildCount() == 0) return;
        View target = findGroupView(selectedGroup);
        if (target == null) target = binding.groupList.getChildAt(0);
        target.requestFocus();
    }

    private void onGroupClick(String group, View view) {
        if (groupReordering) {
            if (TextUtils.equals(group, reorderingGroup)) saveGroupReorder();
            return;
        }
        if (!TextUtils.isEmpty(group) && group.equals(selectedGroup)) {
            View all = findGroupView("");
            if (all != null && all.requestFocus()) return;
            selectGroup("", all == null ? view : all);
            return;
        }
        selectGroup(group, view);
    }

    private void selectGroup(String group, View view) {
        if (groupReordering && !TextUtils.equals(group, reorderingGroup)) {
            View active = findGroupView(reorderingGroup);
            if (active != null) active.post(active::requestFocus);
            return;
        }
        if (binding == null || adapter == null) return;
        if (group.equals(selectedGroup)) {
            centerGroup(view);
            return;
        }
        selectedGroup = group;
        updateGroupView();
        adapter.filter(selectedGroup, "");
        setRecyclerHeight(adapter.getItemCount());
        updateCountText();
        scrollRecyclerToTop();
        centerGroup(view);
    }

    private void scrollRecyclerToTop() {
        RecyclerView.LayoutManager manager = binding.recycler.getLayoutManager();
        if (manager != null) manager.scrollToPosition(0);
    }

    private void updateGroupView() {
        if (binding == null) return;
        boolean reorderable = groups != null && groups.size() > 1;
        binding.groupHint.setVisibility(reorderable ? View.VISIBLE : View.GONE);
        binding.groupHint.setText(groupReordering ? R.string.site_group_sort_tv_active : R.string.site_group_sort_tv_hint);
        for (int i = 0; i < binding.groupList.getChildCount(); i++) {
            View view = binding.groupList.getChildAt(i);
            String group = (String) view.getTag();
            boolean selected = TextUtils.equals(group, selectedGroup);
            boolean reordering = groupReordering && TextUtils.equals(group, reorderingGroup);
            String text = TextUtils.isEmpty(group) ? getDialogActivity().getString(R.string.site_group_all) : group;
            int description = reordering ? R.string.site_group_sort_tv_active_desc : R.string.site_group_sort_tv_desc;
            androidx.appcompat.widget.AppCompatTextView button = (androidx.appcompat.widget.AppCompatTextView) view;
            button.setText(reordering ? "↔ " + text : text);
            button.setContentDescription(TextUtils.isEmpty(group) ? text : getDialogActivity().getString(description, text));
            view.setAlpha(groupReordering && !reordering ? 0.55f : 1.0f);
            view.setSelected(selected);
            SiteDialogTheme.applyGroup(button);
        }
    }

    private View findGroupView(String group) {
        if (binding == null) return null;
        for (int i = 0; i < binding.groupList.getChildCount(); i++) {
            View child = binding.groupList.getChildAt(i);
            if (TextUtils.equals(group, (String) child.getTag())) return child;
        }
        return null;
    }

    private void centerGroup(View view) {
        DialogSiteBinding current = binding;
        if (current == null) return;
        current.groupScroll.post(() -> {
            if (binding != current) return;
            current.groupScroll.smoothScrollTo(Math.max(0, view.getLeft() + view.getWidth() / 2 - current.groupScroll.getWidth() / 2), 0);
        });
    }

    private void focusSelectedSite() {
        if (binding == null || adapter == null) return;
        int selectedPosition = getSelectedPosition();
        if (selectedPosition >= 0) {
            binding.recycler.post(() -> {
                if (binding == null) return;
                RecyclerView.LayoutManager manager = binding.recycler.getLayoutManager();
                if (manager != null) manager.scrollToPosition(selectedPosition);
                binding.recycler.post(() -> {
                    if (binding == null) return;
                    RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(selectedPosition);
                    if (holder != null && holder.itemView != null) {
                        holder.itemView.requestFocus();
                        log("focused selected site position=%s total=%sms", selectedPosition, cost());
                    }
                });
            });
        } else {
            requestGroupFocus();
        }
    }

    private int getSelectedPosition() {
        if (adapter == null) return -1;
        List<Site> items = adapter.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isSelected()) return i;
        }
        return -1;
    }
}
