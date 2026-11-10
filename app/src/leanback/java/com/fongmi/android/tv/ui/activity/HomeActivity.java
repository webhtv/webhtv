package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.leanback.widget.HorizontalGridView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.setting.ConfigSyncPolicy;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.following.FollowingPlaybackBridge;
import com.fongmi.android.tv.following.FollowingScheduler;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.AutoBackupPolicy;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.HomeMenuDialog;
import com.fongmi.android.tv.ui.dialog.MultiRepoDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.CrashRestartMode;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.web.HomeWebController;
import com.github.catvod.utils.Prefers;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Json;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, FuncPresenter.OnBoundaryListener, TypeAdapter.OnClickListener, HomeWebController.Listener, HomeMenuDialog.Listener {

    private static final String TV_NORMAL = "tv-normal";
    private static final String TV_TOOLBAR_HIDDEN = "tv-toolbar-hidden";
    private static final String TV_OVERLAY = "tv-overlay";
    private static final String TV_FULL = "tv-full";
    private static final long TYPE_SWITCH_DELAY_MS = 100;
    private static final long CONFIRM_LONG_PRESS_MS = 550;

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private TypeAdapter mTypeAdapter;
    private FolderFragment mFolder;
    private HomeWebController mWeb;
    private WebView mHomeWeb;
    private Result mResult;
    private Result mHomeResult;
    private Clock mClock;
    private View mSelectedTypeView;
    private Class mCurrentType;
    private int mPendingTypePosition = -1;
    private String mCategorySiteKey = "";
    private String webChromeMode = TV_NORMAL;
    private String webDefaultChromeMode = TV_FULL;
    private boolean webToolbarVisible = true;
    private boolean toolbarRequestedVisible = true;
    private boolean loadingHomeCategory;
    private boolean mStartupActionDone;
    private int mMenuButtonCount;

    public static List<Vod> sRecommendCache = new ArrayList<>();
    public static Vod sPendingVideo = null;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_App);
        App.resumeBackgroundServices();
        super.onCreate(savedInstanceState);
        if (Prefers.getBoolean("skip_startup", false)) {
            Prefers.remove("skip_startup");
            mStartupActionDone = true;
        }
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        SpiderDebug.log("startup", "home initView start cost=%sms", System.currentTimeMillis() - App.time());
        mResult = Result.empty();
        mHomeResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        setLogo();
        syncHomeSiteLock();
        mBinding.progressLayout.showProgress();
        setRecyclerView();
        setViewModel();
        setAdapter();
        runAfterFirstFrame(this::initAfterFirstFrame);
        SpiderDebug.log("startup", "home initView end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void initAfterFirstFrame() {
        SpiderDebug.log("startup", "home first frame cost=%sms", System.currentTimeMillis() - App.time());
        App.post(this::initConfig, 80);
        App.post(() -> PermissionUtil.requestFile(this, allGranted -> {
            PermissionUtil.requestNotify(this);
            if (allGranted) initConfig();
        }), 1800);
        App.post(() -> DLNARendererService.start(this), 2500);
    }

    private void runAfterFirstFrame(Runnable runnable) {
        View root = mBinding.getRoot();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(runnable);
                return true;
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.toolbar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            syncNativeContentInset();
            syncWebOverlayLayout();
        });
    }

    /**
     * 类型栏的结构性修改（换 adapter 数据）在 RecyclerView 正在计算布局时必须让路：布局
     * 过程中增删子 View 会让某个子 View 拿不到 ViewHolder，RecyclerView 随后在
     * dispatchLayoutStep1 里崩溃。
     *
     * <p>这是类型栏结构性写入的唯一入口。本方法自己决定「立即执行」还是「入槽延后」，并且
     * 两个分支都由本方法负责真正调用 {@code update}：调用方只允许写一句裸语句
     * {@code runTypeMutation(...)}，不得再自己执行目标，也不得把本方法当成判定条件去决定
     * 是否重复执行。（历史上它返回 boolean，于是非延后路径「方法内 run 一次 + 调用方再 run
     * 一次」造成双执行，属于真缺陷，不是风格问题。）
     *
     * <p>单槽合并成立的前提是「写入均为整表替换」：槽位里只允许放整体覆盖 adapter 数据的
     * 写入，后来的写入直接顶掉先前的，所以被顶掉的旧写入不需要单独执行，也不需要任何代际号。
     *
     * <p>延后不是一次性的：投递到下一帧动画时布局仍可能没结束，所以闭包必须像
     * {@code BaseActivity#postRecyclerUpdate} 那样重新复查 {@link #isTypeLayoutBusy()}，
     * 未就绪就把同一个 Runnable 再挂一帧。
     *
     * <p>排队语义是「单槽合并 + 必执行」，不是「代际号丢弃」：丢弃式代际号会让一次布局中的
     * 写入被随后的焦点失效静默作废，类型栏就永久空白。这里的待写入只有一个槽位
     * （{@link #mPendingTypeUpdate}），后来的写入直接覆盖先前的，因此旧写入不会盖掉新写入；
     * 排队只允许存在一个 runner（{@link #mTypeUpdatePosted}），它执行时取出并执行槽位里的
     * 最新写入，所以最新写入必然落地，不会变成无人执行的孤儿。丢弃只发生在一种情况下：
     * 宿主正在结束或已销毁（{@code isFinishing() || isDestroyed()}）时，那时的 adapter
     * 写入没有任何意义。
     */
    private void runTypeMutation(Runnable update) {
        final RecyclerView view = mBinding.typeRecycler;
        if (!isTypeLayoutBusy()) {
            // 这次写入立刻落地，此前排队的那次已被它取代，必须清掉，否则旧数据会在下一帧
            // 覆盖刚刚同步写入的新数据。
            mPendingTypeUpdate = null;
            update.run();
            return;
        }
        // 单槽合并：后写者覆盖前者，这就是「旧不盖新」的全部实现，不需要任何代际号。
        mPendingTypeUpdate = update;
        // 同一时刻只允许一个 runner 在排队；已在排队时它执行时自然读到刚放进去的最新写入。
        if (mTypeUpdatePosted) return;
        mTypeUpdatePosted = true;
        view.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                mTypeUpdatePosted = false;
                // 宿主正在结束或已销毁：此时的 adapter 写入没有任何意义，丢弃是安全的。
                if (!canDeferFragmentWork()) {
                    mPendingTypeUpdate = null;
                    return;
                }
                if (isTypeLayoutBusy()) {
                    mTypeUpdatePosted = true;
                    view.postOnAnimation(this);
                    return;
                }
                // 取出并执行槽位里的最新写入：它必然被执行，不会被静默丢弃。
                Runnable pending = mPendingTypeUpdate;
                mPendingTypeUpdate = null;
                if (pending != null) pending.run();
            }
        });
    }

    /**
     * 布局忙碌判定：类型栏自己或内容行 recycler 正在算布局时，结构性写入都必须让路。
     * 单点定义，避免同一个条件在守卫里出现两遍、改一处漏一处。
     */
    private boolean isTypeLayoutBusy() {
        return mBinding.typeRecycler.isComputingLayout() || mBinding.recycler.isComputingLayout();
    }

    private void scheduleTypeSwitch(int position) {
        mPendingTypePosition = position;
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        mBinding.typeRecycler.postDelayed(mTypeSwitch, TYPE_SWITCH_DELAY_MS);
    }

    private void resumeTypeSwitch() {
        if (!Setting.isHomeVodAutoLoad() || mTypeSelectionFromTouch || mBinding.typeRecycler.getVisibility() != View.VISIBLE) return;
        int position = mBinding.typeRecycler.getSelectedPosition();
        if (position >= 0) scheduleTypeSwitch(position);
    }

    private void switchType() {
        int position = mPendingTypePosition;
        if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
        if (!Setting.isHomeVodAutoLoad() || position < 0 || position >= mTypeAdapter.getItemCount()) return;
        Class item = mTypeAdapter.get(position);
        if (item.isHome()) showHomeContent();
        else showCategoryContent(item);
    }

    @Override
    public void onCategoryContentHorizontalEdge(Class item, int contentRow, boolean towardEnd) {
        if (!isCurrentCategory(item) || contentRow < 0) return;
        mTypeSelectionFromTouch = false;
        item = getAdjacentCategory(item, towardEnd);
        if (item == null) return;
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        int position = mTypeAdapter.indexOf(item);
        mBinding.typeRecycler.setSelectedPosition(position);
        focusCategoryButton(item);
    }

    private Class getAdjacentCategory(Class item, boolean towardEnd) {
        int position = mTypeAdapter.indexOf(item);
        int target = position + (towardEnd ? 1 : -1);
        if (position < 0 || target < 0 || target >= mTypeAdapter.getItemCount()) return null;
        Class candidate = mTypeAdapter.get(target);
        return candidate.isHome() ? null : candidate;
    }

    private void focusFirstCard(Class item) {
        showCategoryContent(item);
        getSupportFragmentManager().executePendingTransactions();
        if (mFolder != null && isCurrentCategory(item)) mFolder.requestContentFocus(0, focusGeneration);
    }

    private void focusCategoryButton(Class item) {
        if (item == null || getSupportFragmentManager().isStateSaved()) return;
        showCategoryContent(item);
        // Complete the switch before checking the new page; otherwise the posted callback can
        // run while the target fragment is not added yet and drop the header/focus restoration.
        getSupportFragmentManager().executePendingTransactions();
        final int generation = ++focusGeneration;
        mPendingCategoryFocus = () -> {
            if (generation != focusGeneration) return;
            mPendingCategoryFocus = null;
            if (isFinishing() || isDestroyed() || !isCurrentCategory(item) || mFolder == null) return;
            int position = mTypeAdapter.indexOf(item);
            if (position < 0 || mBinding.typeRecycler.getSelectedPosition() != position) return;
            mBinding.typeRecycler.setVisibility(View.VISIBLE);
            updateToolbarVisibility(true);
            mBinding.typeRecycler.requestFocus();
            mFolder.scrollContentToTop(generation);
            mBinding.typeRecycler.setSelectedPosition(position, holder -> {
                if (generation != focusGeneration || !mBinding.typeRecycler.hasFocus()) return;
                if (isCurrentCategory(item) && mBinding.typeRecycler.getSelectedPosition() == position) {
                    holder.itemView.requestFocus();
                }
            });
        };
        mBinding.typeRecycler.post(mPendingCategoryFocus);
    }

    private void invalidatePendingFocusRequests() {
        focusGeneration++;
        if (mBinding != null && mPendingCategoryFocus != null) {
            mBinding.typeRecycler.removeCallbacks(mPendingCategoryFocus);
        }
        if (mBinding != null) mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        mPendingCategoryFocus = null;
    }

    private void showHomeContent() {
        invalidatePendingFocusRequests();
        mCurrentType = null;
        // 分类页里的滚动隐藏是分类页的临时状态，不该跨内容模式留下来：否则在分类页往下滚过
        // 再返回首页，记录下来的“不想显示”会让首页工具条一直藏着（CustomVerticalGridView
        // 会把 typeRecycler 和 toolbar 一起藏掉并回调到这里）。
        toolbarRequestedVisible = true;
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        mBinding.categoryContainer.setVisibility(View.GONE);
        applyToolbarVisibility();
        if (mFolder != null && mFolder.isAdded() && !mFolder.isHidden()) {
            mFolder.clearContentFocusRequest();
            mFolder.setUserVisibleHint(false);
            // 这条路径也会从选中监听（updateTypeRecyclerVisibility）进来，同样可能
            // 落在已保存状态窗口里；本地可见性状态照旧清，事务则只在能提交时才提交。
            if (canCommitFragmentTransaction()) getSupportFragmentManager().beginTransaction().hide(mFolder).commit();
        }
    }

    private void showCategoryContent(Class item) {
        showCategoryContent(item, false);
    }

    private void showCategoryContent(Class item, boolean toggleFilter) {
        if (getSupportFragmentManager().isStateSaved()) {
            invalidatePendingFocusRequests();
            return;
        }
        final int generation = ++focusGeneration;
        if (mBinding != null && mPendingCategoryFocus != null) mBinding.typeRecycler.removeCallbacks(mPendingCategoryFocus);
        mPendingCategoryFocus = null;
        if (mFolder != null) mFolder.clearContentFocusRequest();
        getSupportFragmentManager().executePendingTransactions();
        if (item == null || item.isHome()) {
            showHomeContent();
            return;
        }
        if (isCurrentCategory(item)) {
            if (toggleFilter) updateFilter(item);
            return;
        }
        boolean keepTypeFocus = mBinding.typeRecycler.hasFocus();
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        mBinding.progressLayout.setVisibility(View.GONE);
        mBinding.categoryContainer.setVisibility(View.VISIBLE);

        String tag = "home-category:" + mCategorySiteKey + ":" + item.getTypeId();
        Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
        FolderFragment target = existing instanceof FolderFragment ? (FolderFragment) existing : FolderFragment.newInstance(getHome().getKey(), item);
        if (target == mFolder && target.isAdded() && !target.isHidden()) {
            mCurrentType = item;
            applyToolbarVisibility();
            if (toggleFilter) updateFilter(item);
            return;
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (mFolder != null && mFolder.isAdded()) {
            mFolder.clearContentFocusRequest();
            mFolder.setUserVisibleHint(false);
            transaction.hide(mFolder);
        }
        if (target.isAdded()) transaction.show(target);
        else transaction.add(R.id.categoryContainer, target, tag);
        target.setUserVisibleHint(true);
        transaction.runOnCommit(() -> {
            if (generation != focusGeneration || target != mFolder || !isCurrentCategory(item)) return;
            target.scrollContentToTop(generation);
            restoreTypeFocus(keepTypeFocus, item);
            if (toggleFilter && target == mFolder && isCurrentCategory(item)) updateFilter(item);
        });
        transaction.commit();
        mFolder = target;
        mCurrentType = item;
        applyToolbarVisibility();
    }

    private void restoreTypeFocus(boolean keepTypeFocus, Class item) {
        if (!keepTypeFocus) return;
        int position = mTypeAdapter.indexOf(item);
        if (!isCategoryVisible() || position < 0 || mBinding.typeRecycler.getSelectedPosition() != position) return;
        mBinding.typeRecycler.setVisibility(View.VISIBLE);
        updateToolbarVisibility(true);
        mBinding.typeRecycler.requestFocus();
    }

    private void syncCategorySite() {
        String key = getHome().getKey();
        if (TextUtils.equals(mCategorySiteKey, key)) return;
        mCategorySiteKey = key;
        clearCategoryContent();
    }

    /**
     * 宿主已保存状态、正在结束或已销毁时不能再提交 Fragment 事务。
     *
     * <p>首页的分类页重建由**进程级**单例 {@code VodConfig} 的加载回调驱动
     * （{@code BaseConfig.loadConfig()} 用 {@code App.post(callback::success)} 投递），
     * 回调与 Activity 生命周期无关：外观页改主题色/主题模式/UI 缩放都会
     * {@code recreate()}，而配置加载可能在这个重建窗口里才返回。此前
     * {@link #clearCategoryContent()} 无条件 {@code commit()}，于是这条延迟回调在
     * 已保存状态上抛 {@code IllegalStateException: Can not perform this action after
     * onSaveInstanceState} 并崩溃（见 docs/CRASH-FRAGMENT-STATE-20261022.md）。
     *
     * <p>判定条件与文件内既有的 4 处守卫（{@code switchType}、
     * {@code focusCategoryButton}、{@code showCategoryContent}、
     * {@code openCustomCsp}）保持一致。
     */
    private boolean canCommitFragmentTransaction() {
        return !isFinishing() && !isDestroyed() && !getSupportFragmentManager().isStateSaved();
    }

    /**
     * 宿主仍然存活、只是被保存了状态（例如配置回调在用户切走之后才返回）时，
     * 被 {@link #canCommitFragmentTransaction()} 拒绝的工作要推迟到回到前台补做；
     * 正在结束或已销毁的实例没有可恢复的宿主，直接丢弃。
     */
    private boolean canDeferFragmentWork() {
        return !isFinishing() && !isDestroyed();
    }

    private void clearCategoryContent() {
        invalidatePendingFocusRequests();
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        // 换源时不需要额外作废上一站点在途的写入：清理本身是最后一次写入，单槽合并让
        // 它直接覆盖先前排队的那次（后写者胜）。
        clearStaleSiteTypes();
        mCurrentType = null;
        mFolder = null;
        toolbarRequestedVisible = true;
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        mBinding.categoryContainer.setVisibility(View.GONE);
        applyToolbarVisibility();
        // 状态清理是纯内存赋值，与能否提交事务无关；这里必须已经丢弃 mFolder，
        // 否则 isCurrentCategory() 会把一个即将消失的 Fragment 当成当前分类。
        if (!canCommitFragmentTransaction()) {
            // 这次移除不能丢：旧站点的分类页（含子 Fragment）会一直留在容器里，
            // 等容器再次可见就会和新站点的页面叠加。
            if (canDeferFragmentWork()) mPendingCategoryCleanup = true;
            return;
        }
        removeCategoryFragments();
    }

    /** 移除所有已加入的分类页；调用方必须先通过 {@link #canCommitFragmentTransaction()}。 */
    private void removeCategoryFragments() {
        FragmentTransaction transaction = null;
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (!(fragment instanceof FolderFragment folder)) continue;
            folder.clearContentFocusRequest();
            if (transaction == null) transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(fragment);
        }
        if (transaction != null) transaction.commit();
    }

    private void clearStaleSiteTypes() {
        // Site switching starts asynchronously. Remove the old type row immediately so stale
        // category buttons cannot be mistaken for the newly selected site during loading.
        // 隐藏与选中的撤销是纯状态量，必须同步做完：延后它们会让旧分类按钮至少多活一帧，
        // 期间它仍然可见、可聚焦，switchType() 还会按旧 adapter 判定并打开旧 Class。
        mPendingTypePosition = -1;
        mBinding.typeRecycler.setVisibility(View.GONE);
        // 只有真正的结构性写入（adapter 换数据）需要让路：这条链路
        // （clearCategoryContent ← syncCategorySite ← 站点切换）就是目标崩溃场景。
        runTypeMutation(this::clearStaleTypeItems);
    }

    /** 清空旧站点的分类按钮；结构性修改只允许经 {@link #runTypeMutation(Runnable)} 这条守卫进入。 */
    private void clearStaleTypeItems() {
        mTypeAdapter.addAll(Collections.emptyList());
    }

    private void updateToolbarVisibility(boolean visible) {
        toolbarRequestedVisible = visible;
        applyToolbarVisibility();
    }

    /**
     * 首页顶部工具条（logo + 站点名 + 时钟）只属于首页内容。
     *
     * <p>点播内联分类页要和关闭「默认加载点播」后 VodActivity 分类页保持一致：那边只有分类按钮行 +
     * 内容容器，没有这条工具条（见 {@code app/src/leanback/res/layout/activity_vod.xml}），所以分类页
     * 把工具条藏起来；分类按钮行照旧留在顶部，方向键还能继续切分类。首页里按最近一次滚动请求
     * 恢复，切换内容模式时（离开或回到首页）这个请求会复位成显示。
     */
    private void applyToolbarVisibility() {
        boolean show = toolbarRequestedVisible && webToolbarVisible && !isCategoryVisible();
        mBinding.toolbar.setVisibility(show ? View.VISIBLE : View.GONE);
        syncNativeContentInset();
        syncWebOverlayLayout();
    }

    private void syncNativeContentInset() {
        int top = isToolbarVisible() ? toolbarHeight() : 0;
        if (mBinding.nativeContent.getPaddingTop() != top) {
            mBinding.nativeContent.setPadding(mBinding.nativeContent.getPaddingLeft(), top, mBinding.nativeContent.getPaddingRight(), mBinding.nativeContent.getPaddingBottom());
        }
        mBinding.progressLayout.setProgressTopOffset(top * 3);
    }

    private void syncWebOverlayLayout() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mBinding.webOverlay.getLayoutParams();
        int top = constrainWebBelowToolbar() ? toolbarHeight() : 0;
        if (params.topMargin == top) return;
        params.topMargin = top;
        mBinding.webOverlay.setLayoutParams(params);
    }

    private boolean constrainWebBelowToolbar() {
        return (TV_NORMAL.equals(webChromeMode) || TV_OVERLAY.equals(webChromeMode)) && isToolbarVisible();
    }

    private boolean isToolbarVisible() {
        return mBinding.toolbar.getVisibility() == View.VISIBLE;
    }

    private int toolbarHeight() {
        int height = mBinding.toolbar.getHeight();
        if (height <= 0) height = mBinding.toolbar.getMeasuredHeight();
        return height > 0 ? height : ResUtil.dp2px(80);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.typeRecycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.typeRecycler.setRowHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.typeRecycler.setAdapter(mTypeAdapter = new TypeAdapter(this));
        FuncPresenter funcPresenter = new FuncPresenter(this);
        funcPresenter.setOnBoundaryListener(this);
        mBinding.funcRecycler.setAdapter(new ItemBridgeAdapter(mFuncAdapter = new ArrayObjectAdapter(funcPresenter)));
        mBinding.funcRecycler.setHorizontalSpacing(ResUtil.dp2px(4));
        mBinding.funcRecycler.setRowHeight(ResUtil.dp2px(40));
    }

    private void setWebView() {
        try {
            SpiderDebug.log("startup", "webview create start cost=%sms", System.currentTimeMillis() - App.time());
            WebView webView = getHomeWeb();
            if (webView == null) {
                SpiderDebug.log("startup", "webview unavailable, web home disabled");
                return;
            }
            mWeb = new HomeWebController(this, webView, this);
            mWeb.setViewport(tvViewport(webChromeMode));
            SpiderDebug.log("startup", "webview create end cost=%sms", System.currentTimeMillis() - App.time());
        } catch (Throwable e) {
            SpiderDebug.log("startup", "webview init failed: %s", e.toString());
            mHomeWeb = null;
            mWeb = null;
        }
    }

    private void ensureWebView() {
        if (mWeb == null) setWebView();
    }

    private WebView getHomeWeb() {
        if (mHomeWeb != null) return mHomeWeb;
        try {
            mHomeWeb = new WebView(this);
            mHomeWeb.setFocusable(true);
            mHomeWeb.setFocusableInTouchMode(true);
            mHomeWeb.setVisibility(View.GONE);
            mBinding.webOverlay.addView(mHomeWeb, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return mHomeWeb;
        } catch (Throwable e) {
            SpiderDebug.log("startup", "webview construction failed: %s", e.toString());
            mHomeWeb = null;
            return null;
        }
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            boolean categoryResult = isHomeCategoryResult(result);
            mAdapter.remove("progress");
            if (!categoryResult) {
                Cache.clear().put(result);
                setTypes(mHomeResult = result);
            }
            mResult = result;
            addVideo(result);
            // 如果用户手动点击了"点播"按钮，数据加载完成后自动进入分类页
            if (pendingOpenVod && !result.getTypes().isEmpty()) {
                pendingOpenVod = false;
                openCategory(result.getTypes().get(0));
            }
        });
    }

    private boolean isHomeCategoryResult(Result result) {
        return loadingHomeCategory && result.getTypes().isEmpty();
    }

    private void setAdapter() {
    }

    private void setTitle() {
        mBinding.title.setText(AppBranding.getDisplayName(this, getHome().getDisplayName(), getConfig().getName()));
    }

    private void syncHomeSiteLock() {
        mBinding.title.setSiteLocked(Setting.isHomeSiteLock());
    }

    private void initConfig() {
        if (CrashRestartMode.consume()) {
            SpiderDebug.log("startup", "skip config load once after crash restart");
            showContent();
            return;
        }
        SpiderDebug.log("startup", "config load start cost=%sms", System.currentTimeMillis() - App.time());
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                SpiderDebug.log("startup", "config load success cost=%sms", System.currentTimeMillis() - App.time());
                skipNextVodConfigRefresh = true;
                showContent();
            }

            /**
             * 还没有配置任何内容源（重置应用、重装或恢复出厂后的首次启动）。
             *
             * <p>这不是失败：没有地址可请求、没有失败原因、重试也永远不会成功。改动前
             * {@code BaseConfig} 把它当 {@code error("")} 抛出来，首页便显示「内容源加载失败 /
             * 重试」——一个不存在的故障加一个点了没用的按钮。这里改走「未配置」状态：
             * 不接管首页、直接进入内容态（导航恢复）；添加接口仍走既有的
             * 「设置 → 点播」里的管理面板（`HistoryDialog` 的「添加」→ `ConfigDialog`）。
             */
            @Override
            public void noConfig() {
                SpiderDebug.log("startup", "no vod config configured");
                skipNextVodConfigRefresh = true;
                showNoConfig();
            }

            @Override
            public void error(String msg) {
                SpiderDebug.log("startup", "config load error cost=%sms msg=%s", System.currentTimeMillis() - App.time(), msg);
                Notify.show(msg);
                skipNextVodConfigRefresh = true;
                // 设计文档 §9.9：错误态要给出「错误色 + 原因 + 重试动作」。
                // 改动前这里只弹一个会消失的吐司，然后 showContent() 留一个没有任何内容源的
                // 空首页——用户既不知道原因，也没有重试入口。吐司保留（不删既有行为），
                // 页面改为进入错误态。
                showConfigError(msg);
            }
        };
    }

    /** 内容源加载失败：进入错误态，原因取服务端返回的 message，主动作是重新加载配置。 */
    private void showConfigError(String msg) {
        mBinding.progressLayout.showError(
                R.string.error_config_load,
                msg,
                R.string.error_retry,
                view -> {
                    mBinding.progressLayout.showProgress();
                    initConfig();
                });
    }

    /**
     * 还没有配置内容源：这不是失败，所以**不接管首页**。
     *
     * <p>点播/直播/设置这一行不是独立导航栏，而是首页列表的第一行（{@link #setFunc()}
     * 往 {@code mFuncAdapter} 里填按钮）；一旦走 {@code progressLayout.showEmpty()}，
     * {@code ProgressLayout} 会把内容视图整块 INVISIBLE，导航行跟着消失——新装用户只剩一个
     * 「添加接口」按钮，连设置都进不去。因此这里照常进入内容态（导航恢复）；导航行仍只由
     * {@code HomeButton.getVisibleButtons()} 构成，不再额外插入「添加接口」入口（添加接口走
     * 既有的「设置 → 点播」管理面板）。
     */
    private void showNoConfig() {
        showContent();
    }

    private void showContent() {
        SpiderDebug.log("startup", "home showContent start cost=%sms", System.currentTimeMillis() - App.time());
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setTitle();
        setLogo();
        setFunc();
        getVideo();
        setFocus();
        App.post(this::prewarmWebView, 1500);
        SpiderDebug.log("startup", "home showContent end cost=%sms", System.currentTimeMillis() - App.time());
        runStartupAction();
    }

    private void runStartupAction() {
        if (mStartupActionDone) return;
        if (shouldSkipStartup()) return;
        mStartupActionDone = true;
        switch (Setting.getDefaultLaunch()) {
            case Setting.DEFAULT_LAUNCH_LIVE:
                LiveActivity.start(this);
                break;
            case Setting.DEFAULT_LAUNCH_RECENT:
                List<History> history = History.get();
                if (!history.isEmpty()) {
                    History first = history.get(0);
                    // 诊断用：打印启动选中的那条历史，以及排序紧随其后的两条。
                    // 若这里的 vodName 与实际播放的片子不一致，说明 DB 里存在
                    // key(id) 与 vodName 不匹配的行 —— 本次播放会把它改对，
                    // 所以事后查历史看不到问题，只有开机自动启动时才暴露。
                    SpiderDebug.log("startup", "recent launch key=%s name=%s time=%d | total=%d",
                            first.getKey(), first.getVodName(), first.getCreateTime(), history.size());
                    for (int i = 1; i < Math.min(3, history.size()); i++) {
                        SpiderDebug.log("startup", "recent alt[%d] key=%s name=%s time=%d",
                                i, history.get(i).getKey(), history.get(i).getVodName(), history.get(i).getCreateTime());
                    }
                    String siteKey = first.getSiteKey();
                    Task.execute(() -> {
                        Site site = VodConfig.get().getSite(siteKey);
                        // 站点在当前配置里已经不存在时不能启动：VideoActivity 会退化成
                        // startCastSearch(getName())，拿历史里的旧片名去全站搜索，
                        // 搜出来的第一条是不确定的 —— 这就是「下次开机默认启动变成了别的片子」
                        if (site.isEmpty()) return;
                        site.spider();
                        App.post(() -> VideoActivity.startFullscreen(this, first));
                    });
                }
                break;
            default:
                break;
        }
    }

    private void prewarmWebView() {
        if (isFinishing() || mWeb != null) return;
        boolean hasWebHome = VodConfig.get().getSites().stream().anyMatch(Site::hasHomePage);
        if (!hasWebHome) return;
        SpiderDebug.log("startup", "webview prewarm start cost=%sms", System.currentTimeMillis() - App.time());
        ensureWebView();
        SpiderDebug.log("startup", "webview prewarm end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        mBinding.title.setSelected(true);
        mBinding.title.setFocusable(true);
        mBinding.title.requestFocus();
    }

    private void getVideo() {
        getVideo(false);
    }

    private void getVideo(boolean forceNative) {
        syncCategorySite();
        // 这次刷新打给的是一个已经过去（已保存状态）的窗口：重建后的实例会由
        // initAfterFirstFrame → initConfig 自己加载。继续重排只会再撞一次事务守卫。
        if (!canCommitFragmentTransaction()) {
            // 没有重建、只是被保存了状态时这次刷新不能丢：回到前台（onResume）再补。
            if (canDeferFragmentWork()) {
                mPendingContentRefresh = true;
                mPendingContentRefreshForceNative |= forceNative;
            }
            return;
        }
        if (!forceNative && WebHomeTarget.canLoad(getHome())) {
            ensureWebView();
        }
        if (!forceNative && mWeb != null && mWeb.load(getHome())) {
            mBinding.typeRecycler.setVisibility(View.GONE);
            mBinding.recycler.setVisibility(View.GONE);
            if (mWeb.isReady()) {
                mBinding.progressLayout.showContent();
                showWebOverlay();
            } else {
                hideWebOverlay();
                mBinding.progressLayout.showProgress();
            }
            return;
        }
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        applyTvChrome(TV_NORMAL);
        mBinding.recycler.setVisibility(View.VISIBLE);
        mResult = Result.empty();
        mHomeResult = Result.empty();
        loadingHomeCategory = false;
        if (!Setting.isHomeVodAutoLoad()) mBinding.typeRecycler.setVisibility(View.GONE);
        clearRecommendRows();
        mAdapter.add("progress");
        mViewModel.homeContent();
    }

    private void showWebOverlay() {
        mBinding.webOverlay.setVisibility(View.VISIBLE);
        syncWebOverlayLayout();
    }

    private void hideWebOverlay() {
        mBinding.webOverlay.setVisibility(View.GONE);
    }

    private void setTypes(Result result) {
        if (result.getTypes().isEmpty()) {
            // 守卫自己负责执行或入槽；这里直接返回，绝不能自己再执行一次目标。
            runTypeMutation(this::clearTypes);
            return;
        }
        List<Class> types = new ArrayList<>();
        if (!result.getList().isEmpty()) {
            Class recommend = new Class();
            recommend.setTypeName(ResUtil.getString(R.string.home_recommend));
            types.add(recommend);
        }
        types.addAll(result.getTypes());
        mTypeAdapter.addAll(types);
        mBinding.typeRecycler.setVisibility(View.VISIBLE);
    }

    private void addVideo(Result result) {
        if (!loadingHomeCategory && result.getList().isEmpty() && !result.getTypes().isEmpty()) {
            Class type = result.getTypes().get(0);
            SpiderDebug.log("home", "home list empty, auto open first category key=%s tid=%s", getHome().getKey(), type.getTypeId());
            loadingHomeCategory = true;
            mAdapter.add("progress");
            mViewModel.categoryContent(getHome().getKey(), type.getTypeId(), "1", true, new java.util.HashMap<>());
            return;
        }
        loadingHomeCategory = false;
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    private void clearRecommendRows() {
        mAdapter.remove("progress");
        int index = getRecommendIndex();
        if (mAdapter.size() > index) mAdapter.removeItems(index, mAdapter.size() - index);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void setFunc() {
        mFuncAdapter.setItems(getFuncItems(), new BaseDiffCallback<Func>());
        int count = mFuncAdapter.size();
        if (count > 0) {
            android.view.ViewGroup.LayoutParams params = mBinding.funcRecycler.getLayoutParams();
            params.width = ResUtil.dp2px(64 * count + 4 * (count - 1) + 68);
            mBinding.funcRecycler.setLayoutParams(params);
        }
    }

    private List<Func> getFuncItems() {
        List<Func> items = new ArrayList<>();
        if (LiveConfig.hasLoadedLives()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_history));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        return items;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    private void setLogo() {
        AppBranding.applyLogo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                if (skipNextVodConfigRefresh) {
                    skipNextVodConfigRefresh = false;
                    SpiderDebug.log("startup", "skip duplicate vod config refresh");
                } else {
                    RefreshEvent.history();
                    RefreshEvent.home();
                }
                setLogo();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setTitle();
                dismissStateIfConfigured();
                SpiderDebug.log("site-dialog", "home refresh start key=%s homePage=%s", getHome().getKey(), getHome().hasHomePage());
                if (mWeb != null && mWeb.isVisible()) {
                    if (!mWeb.load(getHome(), true)) getVideo(true);
                } else {
                    getVideo();
                }
                SpiderDebug.log("site-dialog", "home refresh end key=%s", getHome().getKey());
                break;
            case SIZE:
                if (mWeb != null && mWeb.isVisible()) return;
                getVideo();
                break;
        }
    }

    /**
     * 状态浮层只会被「又一次启动 / 点重试」收掉，而配置可能在别的路径上变得可用：
     * 恢复备份、远程指令切换接口、设置页切换接口、接收推送接口。这些路径发的是
     * {@link RefreshEvent#home()} 而不是重新走 {@link #showContent()}，于是用户在这些页面上
     * 操作完回到首页，首页仍顶着那张状态卡，**必须杀进程冷启动才恢复**。
     *
     * <p>这里把「配置真的可用了」当成收卡的唯一判据：HOME 刷新意味着首页马上会重新拉内容，
     * 此时若还停在空态/错误态就直接收起，不依赖任何调用方记得收。
     */
    private void dismissStateIfConfigured() {
        if (VodConfig.get().getSites().isEmpty()) return;
        if (!mBinding.progressLayout.isEmpty() && !mBinding.progressLayout.isError()) return;
        mBinding.progressLayout.showContent();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text(), event.name());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (App.activity() instanceof VideoActivity video) {
            video.finishVideoForCast();
            App.post(() -> dispatchCast(event), 300);
        } else {
            dispatchCast(event);
        }
    }

    private void dispatchCast(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history());
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_vod) {
            // mHomeResult 才可靠保存首页分类（mResult 可能被分类内容结果覆盖）
            Result homeResult = mHomeResult != null && !mHomeResult.getTypes().isEmpty() ? mHomeResult : mResult;
            if (homeResult.getTypes().isEmpty()) {
                // 内容未加载（如 Web 站源首页未拉取原生分类），强制加载原生数据后自动进分类页
                pendingOpenVod = true;
                getVideo(true);
            } else {
                // 内容已加载，进入第一个分类
                openCategory(homeResult.getTypes().get(0));
            }
        } else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_history) HistoryActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
        else if (item.getResId() == R.string.home_cast) PushActivity.start(this, 3);
        else if (item.getResId() == R.string.home_history_button) HistoryActivity.start(this);
        else if (item.getResId() == R.string.home_custom_csp) openCustomCsp();
    }

    private void openCustomCsp() {
        PermissionUtil.requestFile(this, granted -> {
            if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
            if (granted) CustomCspDialog.show(this, this::setFunc);
            else Notify.show(R.string.setting_custom_csp_permission_required);
        });
    }

    @Override
    public boolean onLongClick(Func item) {
        if (item.getResId() != R.string.home_search) return false;
        SearchActivity.start(this, "", getHome().getKey());
        return true;
    }

    private boolean isCategoryVisible() {
        return mCurrentType != null && mBinding.categoryContainer.getVisibility() == View.VISIBLE;
    }

    private boolean isCurrentCategory(Class item) {
        return item != null && item.equals(mCurrentType) && mFolder != null && mFolder.isAdded() && !mFolder.isHidden() && isCategoryVisible();
    }

    private boolean isFilterVisible() {
        return isCategoryVisible() && mCurrentType.getFilter();
    }

    private void updateFilter(Class item) {
        if (item == null || item.isHome() || !isCurrentCategory(item)) return;
        item.setFilter(!item.getFilter());
        mFolder.toggleFilter(item.getFilter());
        int position = mTypeAdapter.indexOf(item);
        if (position >= 0) mTypeAdapter.notifyItemRangeChanged(position, 1);
    }

    @Override
    public void closeFilter() {
        if (isFilterVisible()) updateFilter(mCurrentType);
    }

    @Override
    public int[] getScrollHeaderIds() {
        return new int[]{R.id.typeRecycler, R.id.toolbar};
    }

    @Override
    public void onScrollHeaderVisibilityChanged(boolean visible) {
        updateToolbarVisibility(visible);
    }

    private void openCategory(Class item) {
        if (Setting.isHomeVodAutoLoad()) {
            int position = mTypeAdapter.indexOf(item);
            if (position >= 0) {
                mBinding.typeRecycler.setSelectedPosition(position);
                mBinding.typeRecycler.requestFocus();
                scheduleTypeSwitch(position);
                return;
            }
        }
        Result result = mHomeResult == null || mHomeResult.getTypes().isEmpty() ? mResult : mHomeResult;
        VodActivity.start(this, getHome().getKey(), result, result.getTypes().indexOf(item));
    }

    @Override
    public void onItemClick(Class item) {
        Result result = mHomeResult == null || mHomeResult.getTypes().isEmpty() ? mResult : mHomeResult;
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        int typeIndex = mTypeAdapter.indexOf(item);
        int realIndex = hasRecommend ? typeIndex - 1 : typeIndex;
        if (hasRecommend) {
            sRecommendCache = new ArrayList<>(mHomeResult.getList());
            List<Class> types = new ArrayList<>();
            Class recommend = new Class();
            recommend.setTypeId("home");
            recommend.setTypeName(ResUtil.getString(R.string.home_recommend));
            types.add(recommend);
            types.addAll(result.getTypes());
            Result merged = new Result();
            merged.setTypes(types);
            VodActivity.start(this, getHome().getKey(), merged, hasRecommend && item.getTypeName().equals(ResUtil.getString(R.string.home_recommend)) ? 0 : realIndex + 1);
        } else {
            VodActivity.start(this, getHome().getKey(), result, realIndex);
        }
    }

    @Override
    public void onRefresh(Class item) {
        if (item.isHome()) onRefresh();
        else if (mFolder != null && item.equals(mCurrentType)) mFolder.onRefresh();
    }

    @Override
    public void onLongPress(Class item) {
        if (item == null || item.isHome() || !isCurrentCategory(item)) return;
        mFolder.resetFilter();
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getHome().getKey(), item.getAction());
            return;
        }
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        if (hasRecommend) {
            sRecommendCache = new ArrayList<>(mHomeResult.getList());
            sPendingVideo = item;
            List<Class> types = new ArrayList<>();
            Class recommend = new Class();
            recommend.setTypeId("home");
            recommend.setTypeName(ResUtil.getString(R.string.home_recommend));
            types.add(recommend);
            types.addAll(mHomeResult.getTypes());
            Result merged = new Result();
            merged.setTypes(types);
            VodActivity.start(this, getHome().getKey(), merged, 0);
        } else {
            if (item.isFolder()) {
                Class type = new Class();
                type.setTypeId(item.getId());
                type.setTypeName(item.getName());
                Result result = new Result();
                result.setTypes(java.util.Collections.singletonList(type));
                VodActivity.start(this, getHome().getKey(), result);
            } else if (getHome().isIndex()) {
                CollectActivity.start(this, item.getName());
            } else {
                VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
            }
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void showDialog() {
        long start = System.currentTimeMillis();
        SpiderDebug.log("site-dialog", "open requested cost=%sms", System.currentTimeMillis() - App.time());
        SiteDialog.create().setListener(this).show(this);
        SpiderDebug.log("site-dialog", "show returned delay=%sms", System.currentTimeMillis() - start);
    }

    private void showHomeMenu() {
        List<Func> funcs = getFuncItems();
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        List<Class> types = mHomeResult == null ? new ArrayList<>() : mHomeResult.getTypes();
        mMenuButtonCount = 2 + funcs.size(); // 选站 + 多仓 + funcs
        int categoryCount = (hasRecommend ? 1 : 0) + types.size();
        String[] items = new String[mMenuButtonCount + 1 + categoryCount];
        items[0] = ResUtil.getString(R.string.home_switch);
        items[1] = ResUtil.getString(R.string.multi_repo);
        for (int i = 0; i < funcs.size(); i++) items[2 + i] = funcs.get(i).getText();
        items[mMenuButtonCount] = ResUtil.getString(R.string.home_refresh);
        int offset = mMenuButtonCount + 1;
        if (hasRecommend) items[offset++] = ResUtil.getString(R.string.home_recommend);
        for (int i = 0; i < types.size(); i++) items[offset + i] = types.get(i).getTypeName();
        HomeMenuDialog.create().items(items).show(this);
    }

    @Override
    public void onHomeMenu(int which) {
        if (which == 0) {
            showDialog();
            return;
        }
        if (which == 1) {
            MultiRepoDialog.show(this);
            return;
        }
        if (which >= 2 && which < mMenuButtonCount) {
            onItemClick(getFuncItems().get(which - 2));
            return;
        }
        if (which == mMenuButtonCount) {
            initConfig();
            return;
        }
        int categoryIndex = which - mMenuButtonCount - 1;
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        if (hasRecommend && categoryIndex == 0) {
            getVideo();
            return;
        }
        List<Class> types = mHomeResult == null ? new ArrayList<>() : mHomeResult.getTypes();
        int realIndex = hasRecommend ? categoryIndex - 1 : categoryIndex;
        if (realIndex < 0 || realIndex >= types.size()) return;
        Result result = mHomeResult == null || mHomeResult.getTypes().isEmpty() ? mResult : mHomeResult;
        VodActivity.start(this, getHome().getKey(), result, realIndex);
    }

    @Override
    public void onTitleLeft() {
        focusLastFunc();
    }

    @Override
    public void onTitleRight() {
        focusFirstFunc();
    }

    @Override
    public boolean onLeftBoundary() {
        mBinding.title.setFocusable(true);
        mBinding.title.post(() -> mBinding.title.requestFocus());
        return true;
    }

    @Override
    public boolean onRightBoundary() {
        mBinding.title.setFocusable(true);
        mBinding.title.post(() -> mBinding.title.requestFocus());
        return true;
    }

    private void focusFirstFunc() {
        mBinding.funcRecycler.post(() -> {
            RecyclerView.ViewHolder holder = mBinding.funcRecycler.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void focusLastFunc() {
        int count = mFuncAdapter.size();
        if (count <= 0) return;
        mBinding.funcRecycler.post(() -> {
            RecyclerView.ViewHolder holder = mBinding.funcRecycler.findViewHolderForAdapterPosition(count - 1);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void onHomeMenuKey() {
        int index = Setting.getHomeMenuKey();
        if (index == 0) HomeMenuDialog.create().show(this);
        else onHomeMenuItem(index);
    }

    /**
     * 执行 select_home_menu_key 中某一项对应的动作，下标 1..11（0 是「选项弹窗」自身，不会走到这里）。
     */
    @Override
    public void onHomeMenuItem(int index) {
        switch (index) {
            case 1 -> SiteDialog.create().action().show(this);
            case 2 -> HistoryDialog.create().vod().show(this);
            case 3 -> LiveActivity.start(this);
            case 4 -> HistoryActivity.start(this);
            case 5 -> SearchActivity.start(this);
            case 6 -> PushActivity.start(this);
            case 7 -> PushActivity.start(this, 3);
            case 8 -> KeepActivity.start(this);
            case 9 -> SettingActivity.start(this);
            case 10 -> FollowingActivity.start(this, null);
            case 11 -> openCustomCsp();
        }
    }

    @Override
    public void onRefresh() {
        initConfig();
    }

    @Override
    public void reloadConfig() {
        onRefresh();
    }

    @Override
    public void setConfig(Config config) {
        if (config.getType() != 0) return;
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> loadVodConfig(config));
        } else {
            loadVodConfig(config);
        }
    }

    private void loadVodConfig(Config config) {
        String previousVodUrl = VodConfig.getUrl();
        VodConfig.load(config, getCallback());
        if (!ConfigSyncPolicy.shouldSyncLive(previousVodUrl, LiveConfig.getUrl())) return;
        Config liveConfig = AppDatabase.get().getConfigDao().find(config.getUrl(), 1);
        if (liveConfig != null) LiveConfig.load(liveConfig, new Callback());
    }

    @Override
    public void setSite(Site item) {
        SpiderDebug.log("site-dialog", "set site key=%s name=%s homePage=%s", item.getKey(), item.getName(), item.hasHomePage());
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) {
            showHomeMenu();
            return true;
        }
        // 视频列表/筛选左右环形跳转（放在最前面，确保在VerticalGridView处理之前消费）
        if (KeyUtil.isActionDown(event) && (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event))) {
            View focus = getCurrentFocus();
            if (focus != null && mBinding.recycler.getVisibility() == View.VISIBLE) {
                HorizontalGridView grid = findHorizontalGridView(focus);
                if (grid != null && grid.getAdapter() != null) {
                    int position = grid.getSelectedPosition();
                    int count = grid.getAdapter().getItemCount();
                    if (count > 1 && position >= 0) {
                        if (KeyUtil.isLeftKey(event) && position == 0) {
                            grid.setSelectedPosition(count - 1);
                            return true;
                        }
                        if (KeyUtil.isRightKey(event) && position == count - 1) {
                            grid.setSelectedPosition(0);
                            return true;
                        }
                    }
                }
            }
        }
        if (mWeb != null && mWeb.isVisible()) {
            if (KeyUtil.isBackKey(event)) {
                if (KeyUtil.isActionUp(event)) onBackInvoked();
                return true;
            }
            if (mBinding.toolbar.hasFocus()) {
                if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event)) return requestWebFocus();
                return super.dispatchKeyEvent(event);
            }
            if (KeyUtil.isEnterKey(event)) return dispatchWebConfirmKey(event);
            if (KeyUtil.isUpKey(event) && isToolbarVisible()) return super.dispatchKeyEvent(event);
            if (mWeb.dispatchKeyEvent(event)) return true;
            return super.dispatchKeyEvent(event);
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isLeftKey(event) && mBinding.typeRecycler.hasFocus()) {
            if (mBinding.typeRecycler.getSelectedPosition() == 0 && mTypeAdapter.getItemCount() > 1) {
                mBinding.typeRecycler.setSelectedPosition(mTypeAdapter.getItemCount() - 1);
                return true;
            }
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isRightKey(event) && mBinding.typeRecycler.hasFocus()) {
            if (mBinding.typeRecycler.getSelectedPosition() == mTypeAdapter.getItemCount() - 1 && mTypeAdapter.getItemCount() > 1) {
                mBinding.typeRecycler.setSelectedPosition(0);
                return true;
            }
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isUpKey(event) && mBinding.typeRecycler.hasFocus()) return requestTitleFocus();
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && mBinding.typeRecycler.hasFocus()) {
            if (mBinding.recycler.getVisibility() == View.VISIBLE && mBinding.recycler.getChildCount() > 0) return requestContentFocus();
            return true;
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && (getCurrentFocus() == mBinding.title || mBinding.funcRecycler.hasFocus())) {
            if (hasHomeContent()) {
                return requestHomeFocus();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private HorizontalGridView findHorizontalGridView(View view) {
        View parent = view;
        while (parent != null) {
            if (parent instanceof HorizontalGridView) return (HorizontalGridView) parent;
            if (!(parent.getParent() instanceof View)) break;
            parent = (View) parent.getParent();
        }
        return null;
    }

    private boolean hasHomeContent() {
        return mBinding.typeRecycler.getVisibility() == View.VISIBLE || mBinding.recycler.getChildCount() > 0;
    }

    private boolean requestTitleFocus() {
        updateToolbarVisibility(true);
        mBinding.title.setFocusable(true);
        return mBinding.title.requestFocus();
    }

    private boolean requestHomeFocus() {
        if (mBinding.typeRecycler.getVisibility() == View.VISIBLE) return mBinding.typeRecycler.requestFocus();
        return requestContentFocus();
    }

    private boolean requestWebFocus() {
        return mWeb != null && mWeb.isVisible() && mWeb.requestFocus("toolbar-down");
    }

    private boolean requestContentFocus() {
        if (isCategoryVisible()) {
            invalidatePendingFocusRequests();
            if (mFolder == null) return false;
            mFolder.clearContentFocusRequest();
            return mFolder.requestContentFocus();
        }
        if (mBinding.recycler.getVisibility() != View.VISIBLE || mBinding.recycler.getChildCount() == 0) return false;
        View child = mBinding.recycler.getFocusedChild();
        if (child == null) child = mBinding.recycler.getChildAt(0);
        return child != null && child.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        syncHomeSiteLock();
        if (mWeb != null) mWeb.onResume();
        FollowingScheduler.ensurePeriodic(this);
        FollowingScheduler.enqueueDueNow(this);
        setFunc();
        refreshFollowingCount();
        syncTypeItems();
        resumeTypeSwitch();
        resumeDeferredFragmentWork();
    }

    /** 回到前台后补做在「已保存状态」窗口里被推迟的分类页清理与首页内容刷新。 */
    private void resumeDeferredFragmentWork() {
        if (!canCommitFragmentTransaction()) return;
        if (mPendingCategoryCleanup) {
            mPendingCategoryCleanup = false;
            removeCategoryFragments();
        }
        if (!mPendingContentRefresh) return;
        mPendingContentRefresh = false;
        boolean forceNative = mPendingContentRefreshForceNative;
        mPendingContentRefreshForceNative = false;
        getVideo(forceNative);
    }

    @Override
    protected void onPause() {
        invalidatePendingFocusRequests();
        if (mFolder != null) mFolder.clearContentFocusRequest();
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        cancelWebConfirmKey();
        if (mWeb != null) mWeb.onPause();
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mWeb != null && mWeb.isVisible() && mWeb.handleBack()) {
            return;
        } else if (mWeb != null && mWeb.isVisible() && consumeTvFullscreenBack()) {
            return;
        } else if (mWeb != null && mWeb.isVisible()) {
            exitHome();
            return;
        } else if (isCategoryVisible()) {
            if (isFilterVisible()) closeFilter();
            else if (mFolder != null && mFolder.canBack()) mFolder.goBack();
            else selectHomeType();
        } else if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mBinding.recycler.hasFocus()) {
            mBinding.typeRecycler.requestFocus();
        } else {
            exitHome();
        }
    }

    private void selectHomeType() {
        if (mTypeAdapter.getItemCount() == 0 || !mTypeAdapter.get(0).isHome()) {
            showHomeContent();
            return;
        }
        mBinding.typeRecycler.setSelectedPosition(0);
        mBinding.typeRecycler.requestFocus();
        showHomeContent();
    }

    private boolean consumeTvFullscreenBack() {
        if (!TV_FULL.equals(webChromeMode) && !TV_TOOLBAR_HIDDEN.equals(webChromeMode)) return false;
        applyTvChrome(TV_NORMAL);
        requestTitleFocus();
        return true;
    }

    private void exitHome() {
        confirmExitHome();
    }

    /**
     * 返回键确认退出：后台线程同步完成退出备份后彻底结束进程，
     * 避免只 finish Activity 导致进程残留（外挂 jar 悬浮窗、播放服务等仍存活）。
     * 想进后台请用 Home 键，返回键即真正退出。
     */
    private void confirmExitHome() {
        Task.execute(() -> {
            AppDatabase.backupOnExitSync();
            if (Setting.isAutoClearCache()) Path.clear(Path.cache());
            Process.killProcess(Process.myPid());
            System.exit(0);
        });
    }

    @Override
    protected void onDestroy() {
        invalidatePendingFocusRequests();
        if (mFolder != null) mFolder.clearContentFocusRequest();
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        cancelPendingStartupTasks();
        if (mWeb != null) mWeb.destroy();
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }

    @Override
    public void onBackgroundPlayback() {
        continueInBackground();
    }

    @Override
    public void onFullExit() {
        exitCompletely();
    }

    @Override
    public void onWebLoading() {
        cancelWebConfirmKey();
        hideWebOverlay();
        mBinding.progressLayout.showProgress();
    }

    @Override
    public void onWebReady() {
        showWebOverlay();
        mBinding.progressLayout.showContent();
        mBinding.typeRecycler.setVisibility(View.GONE);
        mBinding.recycler.setVisibility(View.GONE);
    }

    @Override
    public void onWebError() {
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        mBinding.recycler.setVisibility(View.VISIBLE);
        getVideo(true);
    }

    @Override
    public void setToolbar(boolean visible) {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(visible ? webDefaultChromeMode : TV_TOOLBAR_HIDDEN);
    }

    @Override
    public void applyDefaultChrome(Site site) {
        if (!Setting.isWebHomeFullscreen()) {
            webDefaultChromeMode = TV_NORMAL;
            applyTvChrome(TV_NORMAL);
            return;
        }
        webDefaultChromeMode = tvDefaultMode(site == null ? "" : site.getChromeMode());
        applyTvChrome(webDefaultChromeMode);
    }

    @Override
    public void setChrome(JsonObject payload) {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(tvRuntimeMode(Json.safeString(payload, "mode")));
    }

    @Override
    public void restoreChrome() {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(webDefaultChromeMode);
    }

    @Override
    public WebHomeViewport getViewport() {
        return tvViewport(webChromeMode);
    }

    @Override
    public void openVod() {
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        getVideo(true);
    }

    @Override
    public void openSite() {
        showDialog();
    }

    @Override
    public void openSetting() {
        SettingActivity.start(this);
    }

    private void applyTvChrome(String mode) {
        webChromeMode = mode;
        webToolbarVisible = TV_NORMAL.equals(mode) || TV_OVERLAY.equals(mode);
        updateToolbarVisibility(webToolbarVisible);
        syncWebOverlayLayout();
        if (mWeb != null) mWeb.setViewport(tvViewport(mode));
    }

    private String tvDefaultMode(String mode) {
        return tvMode(mode, TV_FULL);
    }

    private String tvRuntimeMode(String mode) {
        return tvMode(mode, webChromeMode);
    }

    private String tvMode(String mode, String fallback) {
        String value = TextUtils.isEmpty(mode) ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (TV_NORMAL.equals(value) || "normal".equals(value)) return TV_NORMAL;
        if (TV_TOOLBAR_HIDDEN.equals(value)) return TV_TOOLBAR_HIDDEN;
        if (TV_OVERLAY.equals(value)) return TV_OVERLAY;
        if (TV_FULL.equals(value) || "edge".equals(value) || "immersive".equals(value)) return TV_FULL;
        return fallback;
    }

    private WebHomeViewport tvViewport(String mode) {
        return WebHomeViewport.fixed(ResUtil.dp2px(28), ResUtil.dp2px(48), ResUtil.dp2px(28), ResUtil.dp2px(48), mode);
    }

}
