package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.model.SearchProgress;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteGroupOrderStore;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.SiteOrderStore;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.dialog.SliderNumberDialog;
import com.fongmi.android.tv.ui.helper.TouchOptimizationHelper;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchModeStore;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CollectActivity extends BaseActivity implements CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private static final float SEARCH_CARD_RATIO = 0.72f;
    private static final int SEARCH_LIST_ROW_HEIGHT_DP = 116;
    private static final int GROUP_POPUP_ITEM_HEIGHT_DP = 52;
    private static final int GROUP_POPUP_ITEM_GAP_DP = 2;
    private static final int GROUP_POPUP_MAX_ITEMS = 7;
    private static final int GROUP_POPUP_MIN_WIDTH_DP = 184;
    private static final int GROUP_POPUP_PADDING_DP = 8;

    private ActivityCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private Runnable mSearchFocusRequest;
    private int mSearchFocusGeneration;
    private SiteViewModel mViewModel;
    private RecyclerView.OnScrollListener mImageScrollListener;
    private List<Site> mSites;
    private List<String> mGroups = new ArrayList<>();
    private final List<Collect> mAllCollectItems = new ArrayList<>();
    private String mFilterGroup = "";
    private PopupWindow mGroupPopup;
    private int mSimilarity;
    private boolean mSearchCompleted;
    private final SearchPageState mPaging = new SearchPageState();
    private final List<Vod> mPendingItems = new ArrayList<>();
    private Runnable mApplyCollect;
    private int mPendingCollectPosition = RecyclerView.NO_POSITION;
    private int mLastSearchFocus;
    private boolean mScrolling;
    private boolean mLeavingForPlayback;
    private final Map<String, SearchPosition> mSearchPositions = new HashMap<>();

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        start(activity, keyword, siteKey, null, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String group) {
        start(activity, keyword, siteKey, group, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String group, String pic, String wallPic) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("siteKey", siteKey);
        intent.putExtra("group", group);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        activity.startActivity(intent);
    }

    public static void startFromHistory(Activity activity, History history) {
        startFromHistory(activity, history, history.getVodName(), VodConfig.getCid());
    }

    public static void startFromHistory(Activity activity, History history, String keyword) {
        startFromHistory(activity, history, keyword, VodConfig.getCid());
    }

    public static void startFromHistory(Activity activity, History history, String keyword, int targetCid) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword == null || keyword.isEmpty() ? history.getVodName() : keyword);
        intent.putExtra("pic", history.getVodPic());
        intent.putExtra("wallPic", history.getWallPic());
        intent.putExtra("historyResumeCid", history.getCid());
        intent.putExtra("historyResumeKey", HistoryResumePayload.encode(history));
        intent.putExtra("historyResumeTargetCid", targetCid);
        activity.startActivity(intent);
    }

    private int getHistoryResumeCid() {
        return getIntent().getIntExtra("historyResumeCid", -1);
    }

    private String getHistoryResumeKey() {
        return Objects.toString(getIntent().getStringExtra("historyResumeKey"), "");
    }

    private int getHistoryResumeTargetCid() {
        return getIntent().getIntExtra("historyResumeTargetCid", -1);
    }

    private boolean isHistoryResume() {
        return getHistoryResumeCid() >= 0 && getHistoryResumeTargetCid() >= 0 && !getHistoryResumeKey().isEmpty();
    }

    private String getKeyword() {
        return Objects.toString(getIntent().getStringExtra("keyword"), "");
    }

    private String getSiteKey() {
        return Objects.toString(getIntent().getStringExtra("siteKey"), "");
    }

    private String getGroup() {
        return Objects.toString(getIntent().getStringExtra("group"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getWallPic() {
        return Objects.toString(getIntent().getStringExtra("wallPic"), "");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        if (mViewModel != null) mViewModel.stopSearch();
        mFilterGroup = "";
        mSimilarity = Setting.getSearchSimilarity();
        saveKeyword();
        setSites();
        updateFilterControls();
        search();
        focusInitialSource();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mSimilarity = Setting.getSearchSimilarity();
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setSearchColumn();
        updateFilterControls();
        search();
        initModeToggle();
        mBinding.collect.post(() -> mBinding.collect.requestFocus());
    }

    private void initModeToggle() {
        mBinding.modeToggle.setOnClickListener(v -> onModeToggle());
        updateModeToggleIcon();
    }

    private void updateModeToggleIcon() {
        int iconRes;
        switch (Setting.getSearchColumn()) {
            case SearchAdapter.MODE_LIST: iconRes = R.drawable.ic_site_detail; break;
            case SearchAdapter.MODE_TEXT: iconRes = R.drawable.ic_site_single_column; break;
            default: iconRes = R.drawable.ic_site_double_column; break;
        }
        mBinding.modeToggle.setImageResource(iconRes);
    }

    private void onModeToggle() {
        Setting.putSearchColumn(Setting.getSearchColumn() % 3 + 1);
        applyMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            onModeToggle();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void applyMode() {
        int count = getCount();
        ((GridLayoutManager) mBinding.recycler.getLayoutManager()).setSpanCount(count);
        mBinding.recycler.setItemViewCacheSize(count * 3);
        mSearchAdapter.setSize(getItemWidth(count), getItemHeight(count));
        mSearchAdapter.setMode(Setting.getSearchColumn());
        updateModeToggleIcon();
        mLastSearchFocus = 0;
        mBinding.recycler.scrollToPosition(0);
    }

    private void setRecyclerView() {
        int count = getCount();
        mScroller = new CustomScroller(this);
        mCollectAdapter = new CollectAdapter(this);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setVerticalSpacing(ResUtil.dp2px(12));
        mBinding.collect.setAdapter(mCollectAdapter);
        mBinding.collect.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (TouchOptimizationHelper.isTouchActive(parent)) return;
                scheduleCollect(position, 260);
            }
        });
        mBinding.collect.setOnKeyListener((view, keyCode, event) -> false);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setItemViewCacheSize(count * 3);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, count));
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.addOnScrollListener(mImageScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                saveSearchPosition(getActiveSiteKey());
                if (!canLoadImage()) return;
                recyclerView.post(() -> {
                    ensureSearchRows(getCount(), 2);
                    preloadNextRows(getCount());
                });
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!canLoadImage()) return;
                boolean scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                if (scrolling == mScrolling) return;
                mScrolling = scrolling;
                if (mScrolling) {
                    recyclerView.post(() -> {
                        ensureSearchRows(getCount(), 2);
                        preloadNextRows(getCount());
                    });
                } else {
                    Glide.with(CollectActivity.this).resumeRequests();
                    recyclerView.post(() -> {
                        flushPendingItems();
                        ensureSearchRows(getCount(), 2);
                        preloadNextRows(getCount());
                    });
                }
            }
        });
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this, getItemWidth(count), getItemHeight(count)));
        mBinding.recycler.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus || mBinding.recycler.getFocusedChild() != null) return;
            restoreSearchFocus();
        });
        mSearchAdapter.setMode(Setting.getSearchColumn());
    }

    private boolean canLoadImage() {
        return !isFinishing() && !isDestroyed();
    }

    private void setSearchLayout() {
        boolean horizontal = isSearchLandscape();
        mBinding.collectHorizontal.setVisibility(horizontal ? android.view.View.VISIBLE : android.view.View.GONE);
        mBinding.collect.setVisibility(horizontal ? android.view.View.GONE : android.view.View.VISIBLE);
        mBinding.recycler.setPadding(ResUtil.dp2px(horizontal ? 24 : 0), 0, ResUtil.dp2px(24), ResUtil.dp2px(24));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getSearchProgress().observe(this, this::setSearchProgress);
        mViewModel.getResult().observe(this, this::setSearch);
    }

    private void setSearchProgress(SearchProgress progress) {
        if (progress == null) return;
        mCollectAdapter.setProgress(progress.current(), progress.total());
        mSearchCompleted = progress.total() > 0 && progress.current() >= progress.total();
        if (mSearchCompleted && mCollectAdapter.getItemCount() > 0) updateEmptyState(mCollectAdapter.getActivated());
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        mSites = new ArrayList<>(SearchModeStore.filterSites(VodConfig.get().getSites(), getSiteKey()));
        mSites.removeIf(SiteBlockSetting::isBlocked);
        if (Setting.isSiteHealthSort()) SiteHealthStore.sortSites(mSites);
        else SiteOrderStore.sortSites(mSites);
    }

    private void search() {
        mSearchCompleted = false;
        mSearchPositions.clear();
        mBoundSearchSiteKey = "";
        mPaging.clear();
        removeApplyCollect();
        mCollectAdapter.clear();
        mSearchAdapter.clear();
        mPendingItems.clear();
        mAllCollectItems.clear();
        mScroller.reset();
        mBinding.result.setText(getResultTitle());
        mBinding.empty.setVisibility(View.GONE);
        if (mSites.isEmpty()) return;
        mAllCollectItems.add(Collect.all());
        if (Setting.getSearchResultSort() == 1) {
            for (Site site : mSites) mAllCollectItems.add(Collect.create(site));
        }
        applyFilters("all");
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private String getResultTitle() {
        if (!getGroup().isEmpty()) return getString(R.string.search_result_group, getGroup(), getKeyword());
        if (!getSiteKey().isEmpty()) return getString(R.string.search_result_current, getKeyword());
        return getString(R.string.collect_result, getKeyword());
    }

    private int getCount() {
        return Math.max(1, Setting.getSearchColumn() == SearchAdapter.MODE_GRID ? Product.getColumn() : 2);
    }

    private int getItemWidth(int count) {
        int width = getResultWidth();
        int spacing = ResUtil.dp2px(8) * (count - 1);
        return (width - spacing) / count;
    }

    private int getResultWidth() {
        return ResUtil.getScreenWidth() - ResUtil.dp2px(isSearchLandscape() ? 48 : 220);
    }

    private int getItemHeight(int count) {
        return isListMode(count) ? ResUtil.dp2px(SEARCH_LIST_ROW_HEIGHT_DP) : (int) (getItemWidth(count) / SEARCH_CARD_RATIO);
    }

    private void setCollect(Result result) {
        if (mLeavingForPlayback || result == null || result.getList().isEmpty()) return;
        mPaging.recordInitial(result.getVod().getSiteKey(), result.getPageCount(), SearchPageState.pageToken(result.getList()));
        applySearchResult(result);
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        Collect activated = mCollectAdapter.getActivated();
        boolean same = !result.getList().isEmpty() && activated.getSite() != null && activated.getSite().equals(result.getVod().getSite());
        if (same) activated.getList().addAll(result.getList());
        if (same) addSearchItems(result.getList());
    }

    private void addSearchItems(List<Vod> items) {
        if (items == null || items.isEmpty()) return;
        if (mScrolling) mPendingItems.addAll(items);
        else mSearchAdapter.appendSource(items, getCount() * 4);
    }

    private void flushPendingItems() {
        if (mPendingItems.isEmpty()) return;
        mSearchAdapter.appendSource(new ArrayList<>(mPendingItems), getCount() * 4);
        mPendingItems.clear();
    }

    private void focusSearchTarget(int position) {
        int generation = ++mSearchFocusGeneration;
        if (mSearchFocusRequest != null) App.removeCallbacks(mSearchFocusRequest);
        mSearchFocusRequest = () -> {
            if (generation != mSearchFocusGeneration || isFinishing() || isDestroyed()) return;
            if (!canFocusSearchResult(position)) return;
            RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
            View target = manager == null ? null : manager.findViewByPosition(position);
            if (target != null) {
                alignSearchResultCard(position, target);
                requestSearchResultFocus(position, generation);
            } else {
                scrollToSearchResult(position);
                mBinding.recycler.post(() -> {
                    if (generation != mSearchFocusGeneration || isFinishing() || isDestroyed()) return;
                    View laidOutTarget = mBinding.recycler.getLayoutManager() == null ? null : mBinding.recycler.getLayoutManager().findViewByPosition(position);
                    if (laidOutTarget != null) {
                        alignSearchResultCard(position, laidOutTarget);
                        requestSearchResultFocus(position, generation);
                    }
                });
            }
        };
        App.post(mSearchFocusRequest);
    }

    private void requestSearchResultFocus(int position, int generation) {
        mBinding.recycler.post(() -> {
            if (generation != mSearchFocusGeneration || isFinishing() || isDestroyed()) return;
            View target = mBinding.recycler.getLayoutManager() == null ? null : mBinding.recycler.getLayoutManager().findViewByPosition(position);
            if (target != null) target.requestFocus();
        });
    }

    private boolean canFocusSearchResult(int position) {
        return mBinding != null && mBinding.recycler != null && mSearchAdapter != null
                && position >= 0 && position < mSearchAdapter.getItemCount();
    }

    private void scrollToSearchResult(int position) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (manager instanceof GridLayoutManager layoutManager) layoutManager.scrollToPosition(position);
    }

    private void alignSearchResultCard(int position, View focusedView) {
        if (focusedView == null || mBinding == null || mBinding.recycler.getHeight() <= 0) return;
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int cardHeight = focusedView.getHeight();
        if (cardHeight <= 0) return;
        int spanCount = Math.max(1, layoutManager.getSpanCount());
        int rowStart = Math.max(0, position - position % spanCount);
        int padding = ResUtil.dp2px(8);
        int bottom = mBinding.recycler.getHeight() - mBinding.recycler.getPaddingBottom() - padding;
        int offset = Math.max(mBinding.recycler.getPaddingTop(), bottom - cardHeight);
        mBinding.recycler.stopScroll();
        layoutManager.scrollToPositionWithOffset(rowStart, offset);
    }

    private void preloadNextRows(int count) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first < 0 || last < 0) return;
        int direction = last >= first ? last + 1 : first + 1;
        ensureSearchItemsLoaded(direction, count * 2);
    }

    private void ensureSearchRows(int count, int rows) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int last = layoutManager.findLastVisibleItemPosition();
        if (last < 0) return;
        ensureSearchItemsLoaded(last + 1, count * rows);
    }

    private void ensureSearchItemsLoaded(int position, int count) {
        if (mBinding.recycler.isComputingLayout()) {
            mBinding.recycler.post(() -> ensureSearchItemsLoaded(position, count));
            return;
        }
        mSearchAdapter.ensureLoaded(position, count);
    }

    @Override
    public void onItemClick(int position, Collect item) {
        scheduleCollect(position, 0);
    }

    @Override
    public boolean onCollectKey(int position, int keyCode, KeyEvent event) {
        return false;
    }

    private void scheduleCollect(int position, long delayMillis) {
        if (position < 0 || position >= mCollectAdapter.getItemCount()) return;
        saveSearchPosition(getActiveSiteKey());
        Collect item = mCollectAdapter.get(position);
        String siteKey = item.getSite().getKey();
        boolean same = siteKey.equals(getActiveSiteKey());
        mCollectAdapter.setSelected(position);
        mSearchAdapter.setAllMode("all".equals(item.getSite().getKey()));
        mScroller.reset();
        mScroller.setPage(item.getPage());
        mPendingItems.clear();
        if (same && delayMillis > 0 && siteKey.equals(mPendingCollectSiteKey)) return;
        applyCollectDeferred(siteKey, delayMillis);
    }

    private void applyCollectDeferred(String siteKey, long delayMillis) {
        removeApplyCollect();
        mPendingCollectSiteKey = siteKey;
        mApplyCollect = () -> {
            if (isFinishing() || isDestroyed()) return;
            Collect activated = mCollectAdapter.findActivated(siteKey);
            if (activated == null) return;
            setSearchItemsLazy(new ArrayList<>(activated.getList()));
            updateEmptyState(activated);
            maybeLoadSelectedPage();
        };
        App.post(mApplyCollect, delayMillis);
    }

    private void removeApplyCollect() {
        if (mApplyCollect != null) App.removeCallbacks(mApplyCollect);
        mApplyCollect = null;
        mPendingCollectSiteKey = "";
    }

    private void setSearchItemsLazy(List<Vod> items) {
        mSearchAdapter.setSource(items, getCount() * 4);
        mLastSearchFocus = 0;
        mBinding.recycler.post(() -> {
            scrollSearchToTop();
            mBinding.recycler.post(() -> {
                ensureSearchRows(getCount(), 2);
                preloadNextRows(getCount());
            });
        });
    }

    private void saveSearchPosition(String siteKey) {
        if (TextUtils.isEmpty(siteKey) || mBinding == null || mBinding.recycler == null) return;
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int position = layoutManager.findFirstVisibleItemPosition();
        if (position == RecyclerView.NO_POSITION) return;
        View first = layoutManager.findViewByPosition(position);
        if (first == null) return;
        int offset = first.getTop() - mBinding.recycler.getPaddingTop();
        mSearchPositions.put(siteKey, new SearchPosition(position, offset));
    }

    private void restoreSearchPosition(String siteKey) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int itemCount = mSearchAdapter == null ? 0 : mSearchAdapter.getItemCount();
        if (itemCount <= 0) return;
        SearchPosition saved = mSearchPositions.get(siteKey);
        int position = saved == null ? 0 : Math.min(saved.adapterPosition, itemCount - 1);
        int offset = saved == null ? 0 : saved.offset;
        layoutManager.scrollToPositionWithOffset(Math.max(0, position), offset);
    }

    @Override
    public void onItemClick(Vod item) {
        if (!item.isFolder() && isHistoryResume()) {
            HistoryResumeCoordinator.openSelected(this, getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid(), item);
            return;
        }
        long start = System.currentTimeMillis();
        setResult(Activity.RESULT_OK);
        mLeavingForPlayback = true;
        mPaging.cancelPending();
        restoreScroller(getActiveSiteKey());
        removeApplyCollect();
        SpiderDebug.log("collect-flow", "item click site=%s id=%s name=%s folder=%s", item.getSiteKey(), item.getId(), item.getName(), item.isFolder());
        if (item.isFolder()) {
            if (isHistoryResume()) VodActivity.start(this, item.getSiteKey(), Result.folder(item), 0, getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid());
            else VodActivity.start(this, item.getSiteKey(), Result.folder(item));
        } else {
            String pic = item.getPic().isEmpty() ? getPic() : item.getPic();
            VideoActivity.collect(this, item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic(), getKeyword());
        }
        SpiderDebug.log("collect-flow", "activity launch requested cost=%dms", System.currentTimeMillis() - start);
        App.post(() -> {
            long cleanup = System.currentTimeMillis();
            if (mViewModel != null) mViewModel.stopSearch();
            mPendingItems.clear();
            if (canLoadImage()) Glide.with(this).pauseRequests();
            SpiderDebug.log("collect-flow", "leave cleanup cost=%dms", System.currentTimeMillis() - cleanup);
        }, 200);
    }

    @Override
    public boolean onItemKey(int position, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (position < 0) return true;
        mLastSearchFocus = position;
        int count = getCount();
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return onSearchDown(position, count);
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return position % count == count - 1;
        return false;
    }

    private void restoreSearchFocus() {
        if (mLastSearchFocus < 0) return;
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int pos = Math.min(mLastSearchFocus, mSearchAdapter.getItemCount() - 1);
        if (pos < 0) return;
        View target = layoutManager.findViewByPosition(pos);
        if (target != null) {
            target.requestFocus();
            return;
        }
        layoutManager.scrollToPositionWithOffset(pos, 0);
        mBinding.recycler.post(() -> {
            View view = layoutManager.findViewByPosition(pos);
            if (view != null) view.requestFocus();
        });
    }

    private boolean onSearchDown(int position, int count) {
        int next = position + count;
        if (next + count >= mSearchAdapter.getItemCount()) flushPendingItems();
        mSearchAdapter.ensureLoaded(next + 1, count * 3);
        if (next < mSearchAdapter.getItemCount()) {
            moveSearchFocus(next);
        } else {
            mScroller.checkMore();
        }
        return true;
    }

    private void moveSearchFocus(int position) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        View target = layoutManager.findViewByPosition(position);
        if (target != null) {
            target.requestFocus();
            return;
        }
        int itemHeight = 0;
        View child = layoutManager.getChildAt(0);
        if (child != null) itemHeight = child.getHeight();
        int offset = Math.max(0, layoutManager.getHeight() - itemHeight - ResUtil.dp2px(8));
        layoutManager.scrollToPositionWithOffset(position, offset);
        mBinding.recycler.post(() -> {
            View view = layoutManager.findViewByPosition(position);
            if (view != null) view.requestFocus();
        });
    }

    @Override
    public boolean onLoadMore(String page) {
        String siteKey = getActiveSiteKey();
        if ("all".equals(siteKey)) return false;
        Collect raw = findCollect(mAllCollectItems, siteKey);
        if (raw == null || raw.getList().isEmpty()) return false;
        int requestedPage;
        try {
            requestedPage = Integer.parseInt(page);
        } catch (NumberFormatException e) {
            return false;
        }
        if (!mPaging.begin(siteKey, requestedPage)) return false;
        try {
            mViewModel.searchContent(raw.getSite(), getKeyword(), false, page);
        } catch (RuntimeException e) {
            mPaging.cancelPending();
            restoreScroller(siteKey);
            return false;
        }
        return true;
    }

    @Override
    protected void onBackInvoked() {
        removeApplyCollect();
        mPaging.cancelPending();
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLeavingForPlayback = false;
        if (canLoadImage()) Glide.with(this).resumeRequests();
    }

    private static final class SearchPosition {
        private final int adapterPosition;
        private final int offset;

        private SearchPosition(int adapterPosition, int offset) {
            this.adapterPosition = Math.max(0, adapterPosition);
            this.offset = offset;
        }
    }

    @Override
    protected void onDestroy() {
        if (mBinding != null) {
            mBinding.recycler.removeOnScrollListener(mScroller);
            if (mImageScrollListener != null) mBinding.recycler.removeOnScrollListener(mImageScrollListener);
        }
        if (mViewModel != null) mViewModel.stopSearch();
        mPaging.clear();
        removeApplyCollect();
        if (mGroupPopup != null) mGroupPopup.dismiss();
        mPendingItems.clear();
        mAllCollectItems.clear();
        SiteHealthStore.flush();
        super.onDestroy();
    }
}
