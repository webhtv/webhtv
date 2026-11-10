package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.PassListener;
import com.fongmi.android.tv.model.LiveViewModel;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.LiveSourceFallbackPolicy;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.LiveEpgSetting;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.LiveListScrim;
import com.fongmi.android.tv.theme.PlaybackMenuScrim;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.ChannelAdapter;
import com.fongmi.android.tv.ui.adapter.EpgDataAdapter;
import com.fongmi.android.tv.ui.adapter.GroupAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDownLive;
import com.fongmi.android.tv.ui.custom.CustomLiveListView;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.custom.WebViewPlayer;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.PlaybackSpeedDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.LiveEpgDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.PlayerOsdDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.github.catvod.crawler.SpiderDebug;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class LiveActivity extends PlaybackActivity implements GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, TrackDialog.Listener, PassListener, ConfigListener, LiveListener, LiveEpgDialog.Listener {

    private static final long PLAYBACK_END_RETRY_DELAY = 500;
    private static final long LIVE_BUFFERING_TIMEOUT = 15000;

    @Override
    protected boolean shouldAutoPlay() {
        return true;
    }

    private ActivityLiveBinding mBinding;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private GroupAdapter mGroupAdapter;
    private Observer<Result> mObserveUrl;
    private CustomKeyDownLive mKeyDown;
    private Observer<Epg> mObserveEpg;
    private LiveViewModel mViewModel;
    private PlayerOsdController mOsd;
    private List<Group> mHides;
    private Map<String, View> mActionButtons;
    private String mPlaybackKey;
    private String mPendingReloadUrl;
    private String mPendingReloadMsg;
    private Channel mChannel;
    private Channel mEpgChannel;
    private View mOldView;
    private Group mGroup;
    private WebViewPlayer mWebViewPlayer;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mEndRetry;
    private Runnable mBufferingTimeout;
    private Clock mClock;
    private View mFocus2;
    private boolean playbackCatchup;
    private boolean mEpgFirstOpen;
    private boolean liveParsed;      // [NEW] parse(LIVE) 是否已完成（groups 结构已稳定）
    private boolean epgRequested;    // [NEW] 本轮是否已触发过 parseXml，避免重复
    private int count;
    private boolean mFailedThisSession;
    /**
     * 本轮播放失败自动换线的起点线路下标，配合 {@link #mLineFallbackExhausted} 使用。
     * -1 表示当前没有进行中的失败回退轮换；自动换线绕回该下标即本频道所有线路都已试过，
     * 置位 exhausted 阻止 onError 立即开启新一轮死循环；手动换线/换台/换源或播放成功后重置。
     */
    private int mLineFallbackAnchor = -1;
    private boolean mLineFallbackExhausted;

    // [NEW] 节目单跨天滚动
    private static final int EPG_DAY_LIMIT = 7;     // 相对今天最多向前 / 向后扩展的天数
    private static final int EPG_PRELOAD_NEAR = 3;  // 焦点距边缘几条时提前填充相邻一天
    private int mEpgHeaderHeight;                   // 悬浮日期条高度，同时作为列表顶部留白
    private Observer<Epg> mObserveDay;              // 按天加载回调
    private final List<Epg> mEpgDays = new ArrayList<>(); // 已展示的节目单，按日期升序
    private int mEpgMinOffset;                      // 已填充的最早一天（相对今天）
    private int mEpgMaxOffset;                      // 已填充的最晚一天（相对今天）
    private int mEpgPendingOffset;                  // 正在后台拉取的那一天
    private boolean mEpgDayLoading;                 // 是否正在后台拉取
    private boolean mEpgFromEdge;                   // 本次后台拉取是不是由边界按键触发

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()));
    }

    private boolean isEmpty() {
        return getIntent().getBooleanExtra("empty", true);
    }

    private Group getKeep() {
        return mGroupAdapter.get(0);
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected boolean isLutAllowed() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected String getPlaybackKey() {
        return mPlaybackKey;
    }

    @Override
    protected PlayerView getExoView() {
        return mBinding.exo;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        setPlayerKernel();
        setDecode();
        mBinding.control.action.speed.setText(player().getSpeedText());
        checkLive();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        applyPlaybackOverlay();
        applyLiveListScrim();
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownLive.create(this);
        mObserveEpg = this::setEpg;
        mObserveDay = this::setEpgDay;
        mObserveUrl = this::start;
        mHides = new ArrayList<>();
        mR0 = this::setSelected;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mBufferingTimeout = this::onBufferingTimeout;
        mR3 = this::hideInfo;
        mR4 = this::hideUI;
        mEndRetry = this::checkNext;
        setRecyclerView();
        mOsd = new PlayerOsdController(mBinding.osd.getRoot(), mBinding.osd.osdTopLeft, mBinding.osd.osdTopRight, mBinding.osd.osdBottomLeft, mBinding.osd.osdBottomRight, mBinding.osd.osdDiagnostics, mBinding.osd.osdMiniProgress, new PlayerOsdController.Source() {
            @Override
            public PlayerManager getPlayer() {
                return service() == null ? null : player();
            }

            @Override
            public String getTitle() {
                return mChannel == null ? "" : mChannel.getName();
            }
        }, 14f);
        setVideoView();
        mWebViewPlayer = new WebViewPlayer();
        setViewModel();
        setupActionButtons();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        mBinding.channel.setListener(this);
        mBinding.epgData.setListener(this);
        // [NEW] 节目单不再头尾回绕：到顶继续播上一日、到底继续播下一日
        mBinding.epgData.setEdgeListener(new CustomLiveListView.EdgeListener() {
            @Override
            public boolean onEdgeUp() {
                return expandEpgDay(-1);
            }

            @Override
            public boolean onEdgeDown() {
                return expandEpgDay(1);
            }
        });
        mBinding.epgData.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                checkEpgPreload(position);
                updateEpgSticky();
            }
        });
        mBinding.epgData.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateEpgSticky();
            }
        });
        mBinding.program.setOnFocusChangeListener((v, hasFocus) -> onEpgFocusChange());
        mBinding.epgData.setOnFocusChangeListener((v, hasFocus) -> onEpgFocusChange());
        mBinding.channel.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChannelSelected(position);
            }
        });
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.action.speed.setDownListener(this::onSpeedSub);
        mBinding.control.action.text.setUpListener(this::onSubtitleClick);
        mBinding.control.action.text.setDownListener(this::onSubtitleClick);
        mBinding.control.action.source.setOnClickListener(view -> onLiveSource());
        mBinding.control.action.epg.setOnClickListener(view -> onLiveEpg());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.osdSettings.setOnClickListener(view -> onOsd());
        mBinding.control.action.action.setOnClickListener(view -> onAction());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onPlayerKernelLong());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.playParams.setOnClickListener(view -> onPlayParams());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mGroupAdapter.getItemCount() > 0) onChildSelected(child, mGroup = mGroupAdapter.get(position));
            }
        });
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
        // [NEW] 顶部留出悬浮日期条的位置，避免它压住第一条节目
        mEpgHeaderHeight = ResUtil.dp2px(34);
        int pad = ResUtil.dp2px(12);
        mBinding.epgData.setPadding(pad, pad + mEpgHeaderHeight, pad, pad);
    }

    /**
     * 直播动作栏登记点播同名的播放器按钮，使「设置-播放-播放器按钮」的开关与排序对直播同样生效。
     * 直播专属按钮（配置、OSD 设置、直播源、播放/暂停、线路、反色、跨类）不登记，始终按布局显示。
     */
    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.PLAY_PARAMS, mBinding.control.action.playParams);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.CHANGE, mBinding.control.action.change);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    /**
     * 先恢复无动态条件的按钮，再统一套用用户关闭项。
     * 顺序不能反过来：applyVisibility 只隐藏、不恢复，必须在条件判定之后执行。
     */
    private void applyActionButtonVisibility() {
        if (mActionButtons == null) return;
        mBinding.control.action.player.setVisibility(View.VISIBLE);
        mBinding.control.action.decode.setVisibility(View.VISIBLE);
        mBinding.control.action.playParams.setVisibility(View.VISIBLE);
        mBinding.control.action.scale.setVisibility(View.VISIBLE);
        mBinding.control.action.change.setVisibility(View.VISIBLE);
        PlayerButtonSetting.applyVisibility(mActionButtons);
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        findViewById(R.id.timeBar).setNextFocusUpId(R.id.config);
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void applyPlaybackOverlay() {
        if (Setting.isPlaybackOverlayEnabled()) mBinding.control.getRoot().setBackground(PlaybackMenuScrim.create(this));
        else mBinding.control.getRoot().setBackgroundResource(R.color.transparent);
    }

    private void applyLiveListScrim() {
        mBinding.recycler.setBackground(LiveListScrim.create(this));
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setPlayerKernel() {
        mBinding.control.action.player.setText(player().getPlayerText());
    }

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        applyResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mViewModel.url().observeForever(mObserveUrl);
        mViewModel.xml().observe(this, this::setEpg);
        mViewModel.epg().observeForever(mObserveEpg);
        mViewModel.day().observeForever(mObserveDay);
        mViewModel.live().observe(this, this::onLiveParsed);
    }

    private void checkLive() {
        if (LiveConfig.isEmpty()) {
            LiveConfig.get().init().load(getCallback());
        } else {
            getLive();
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                getLive();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void getLive() {
        liveParsed = false;      // [NEW]
        epgRequested = false;    // [NEW]
        mViewModel.parse(getHome());
        showProgress();
    }

    /**
     * [NEW] parse(LIVE) 完成后的回调：此时 LiveApi.parse 已跑完，groups 不再被改动，
     * 才允许触发 EPG 解析。原来在 lambda 里无条件 parseXml，若仍有其它线程改 groups → CME。
     */
    private void onLiveParsed(Live live) {
        liveParsed = true;
        setGroup(live);
        setWidth(live);
        // parse 失败时 live 是 new Live()（groups 为空），回退到 getHome() 保证 EPG 仍会加载
        Live target = (live != null && !live.getGroups().isEmpty()) ? live : getHome();
        if (target != null && !target.getGroups().isEmpty()) startEpg(target);
    }

    /**
     * [NEW] 触发 EPG 解析，本轮只触发一次。
     */
    private void startEpg(Live live) {
        if (epgRequested) return;
        epgRequested = true;
        mViewModel.parseXml(live);
    }

    /**
     * [NEW] 对 groups 做快照后再遍历。
     * 主线程遍历 setGroup / setWidth 与后台修改同样会 CME，这里一并防住。
     * 主线程不 sleep，失败直接重试。
     */
    private List<Group> snapshotGroups(Live live) {
        List<Group> copy = new ArrayList<>();
        if (live == null || live.getGroups() == null) return copy;
        for (int i = 0; i < 3; i++) {
            try {
                copy.clear();
                copy.addAll(new ArrayList<>(live.getGroups()));
                return copy;
            } catch (Throwable e) {
                SpiderDebug.log("EpgParser", "snapshotGroups重试 " + (i + 1) + "：" + e);
            }
        }
        return copy;
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        for (Group group : snapshotGroups(live)) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.addAll(items);
        setPosition(LiveConfig.get().findKeepPosition(items));
    }

    private void setWidth(Live live) {
        int padding = ResUtil.dp2px(52);
        if (live.getWidth() == 0) for (Group item : snapshotGroups(live)) live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(item.getName(), 16)));
        int width = live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + padding, ResUtil.getScreenWidth() / 4);
        setWidth(mBinding.group, width);
    }

    private Group setWidth(Group group) {
        int logo = ResUtil.dp2px(60);
        int padding = ResUtil.dp2px(64);
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel item : group.getChannel()) {
            StringBuilder sb = new StringBuilder(item.getNumber()).append("MM");
            if (!item.getLogo().isEmpty()) sb.append("M");
            sb.append(item.getName());
            int nameWidth = (item.getLogo().isEmpty() ? 0 : logo) + ResUtil.getTextWidth(sb.toString(), 16);
            int epgWidth = ResUtil.getTextWidth(item.getData().getCurrent().getTitle(), 13);
            group.setWidth(Math.max(group.getWidth(), Math.max(nameWidth, epgWidth)));
        }
        int width = group.getWidth() == 0 ? 0 : Math.min(group.getWidth() + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.channel, width);
        return group;
    }

    private void setWidth(Epg epg) {
        int padding = ResUtil.dp2px(52);
        if (epg.getList().isEmpty()) return;
        int minWidth = ResUtil.getTextWidth(epg.getList().get(0).getTime(), 14);
        if (epg.getWidth() == 0) for (EpgData item : epg.getList()) epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(item.getTitle(), 16)));
        int width = epg.getWidth() == 0 ? 0 : Math.min(Math.max(epg.getWidth(), minWidth) + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.epgData, width);
    }

    private void setWidth(View view, int width) {
        view.post(() -> {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            int minW = 0, maxW = 0;
            if (view == mBinding.channel) {
                minW = ResUtil.dp2px(180);
                maxW = ResUtil.dp2px(360);
            } else if (view == mBinding.group) {
                minW = ResUtil.dp2px(100);
                maxW = ResUtil.dp2px(220);
            }
            int finalWidth;
            if (minW == 0 && maxW == 0) {
                finalWidth = width;
            } else {
                finalWidth = Math.max(minW, Math.min(width, maxW));
            }
            if (params.width == finalWidth) return;
            params.width = finalWidth;
            view.setLayoutParams(params);
        });
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mBinding.group.setSelectedPosition(position[0]);
        mGroup.setPosition(position[1]);
        onItemClick(mGroup);
        onItemClick(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mBinding.group.getSelectedPosition() != position;
        if (change) mBinding.group.setSelectedPosition(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        mBinding.channel.setSelectedPosition(mGroup.getPosition());
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, Group group) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        onItemClick(group);
        resetPass();
    }

    private void setSelected() {
        if (isFinishing() || isDestroyed()) return;
        mChannelAdapter.setSelected(mChannel);
        notifyItemChanged(mBinding.channel, mChannelAdapter);
        fetch();
    }

    private void setSelected(EpgData item) {
        mEpgDataAdapter.setSelected(item);
        notifyItemChanged(mBinding.epgData, mEpgDataAdapter);
    }

    private void checkPlay() {
        if (player().isPlaying()) onPaused();
        else onPlay();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
        hideControl();
    }

    private void onHome() {
        if (LiveConfig.isOnly()) setLive(getHome());
        else LiveDialog.create().show(this);
        hideControl();
    }

    private void onLiveSource() {
        LiveDialog.create().drawer().show(this);
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        showResizeModeDialog(LiveSetting.getScale(), this::setScale);
    }

    private void onSpeed() {
        if (!player().isVod()) return;
        PlaybackSpeedDialog.show(this, player().getSpeed(), speed -> {
            if (!isServiceReady() || !isOwner() || !player().isVod()) return;
            mBinding.control.action.speed.setText(player().setSpeed(speed));
            PlayerSetting.putDefaultSpeed(player().getSpeed());
        });
    }

    private void onSpeedAdd() {
        if (!player().isVod()) return;
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
        PlayerSetting.putDefaultSpeed(player().getSpeed());
    }

    private void onSpeedSub() {
        if (!player().isVod()) return;
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
        PlayerSetting.putDefaultSpeed(player().getSpeed());
    }

    private boolean onSpeedLong() {
        if (!player().isVod()) return true;
        mBinding.control.action.speed.setText(player().toggleSpeed());
        PlayerSetting.putDefaultSpeed(player().getSpeed());
        return true;
    }

    private void onConfig() {
        HistoryDialog.create().live().readOnly().show(this);
        hideControl();
    }

    private void onLiveEpg() {
        LiveEpgDialog.create().show(this);
        hideControl();
    }

    @Override
    public void onLiveEpgSelected() {
        if (mChannel == null) return;
        LiveEpgSetting.apply(getHome());
        if (!LiveEpgSetting.getXmlUrls(getHome()).isEmpty()) {
            // [CHANGED] 走安全入口；若 parse 还没跑完，onLiveParsed 会自动补上
            if (liveParsed) {
                epgRequested = false;
                startEpg(getHome());
            }
        } else {
            mViewModel.getEpg(mChannel);
        }
    }

    private void onAction() {
        checkPlay();
    }

    private void onInvert() {
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
    }

    private void onAcross() {
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
    }

    private void onChange() {
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void onPlayerKernel() {
        PlayerKernelDialog.show(this, player().getPlayerType(), this::switchPlayerKernel, this::onExternalPlayer);
    }

    private void onExternalPlayer() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
        setRedirect(true);
    }

    private void switchPlayerKernel(int type) {
        player().switchPlayer(type);
        setPlayerKernel();
        setDecode();
        setR1Callback();
    }

    private boolean onPlayerKernelLong() {
        onPlayerKernel();
        return true;
    }

    private void onDecode() {
        player().toggleDecode();
        setDecode();
    }

    private void hideUI() {
        if (isFinishing() || isDestroyed()) return;
        App.removeCallbacks(mR4);
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        setPosition();
        hideEpg();
        mBinding.channel.post(new Runnable() {
            @Override
            public void run() {
                mBinding.channel.requestFocus();
            }
        });
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            nextChannel();
        }

        @Override
        public void onPrev() {
            prevChannel();
        }

        @Override
        public void onStop() {
            finishLivePlayback();
        }
    };

    @Override
    protected void onPrepare() {
        setDecode();
    }

    @Override
    protected void onPlayerRebuilt() {
        setPlayerKernel();
        setDecode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected boolean onSourceHttpError(int statusCode, String msg) {
        // HTTP 播放错误优先交给直播回退流程：开启自动换线或接口回退任一时短路播放器重试链。
        if (!LiveSetting.isChange() && !LiveSetting.isSourceFallback()) return false;
        onError(msg);
        return true;
    }

    @Override
    protected void onError(String msg) {
        if (isFinishing() || isDestroyed()) return;
        Track.delete(player().getKey());
        player().resetTrack();
        player().reset();
        player().stop();
        if (!mFailedThisSession) {
            mFailedThisSession = true;
            showError(msg);
            startFlow();
        }
    }

    /**
     * 缓冲超时（既没出画面也没报错）：等价于本条线路播放失败。
     * <p>
     * 与 onError 不同，本路径不受 mFailedThisSession 限制：上一轮换线后新线路若一直卡在
     * 解析/缓冲阶段，mFailedThisSession 仍为 true，但此时必须仍能继续换线；
     * 确实无线路可换（单线路或本轮已绕回起点）时给出可见的失败提示，避免进度条一直转。
     */
    private void onBufferingTimeout() {
        if (!startFlow()) showError(ResUtil.getString(R.string.error_play_url));
    }

    @Override
    protected void onReload(String msg) {
        if (isFinishing() || isDestroyed()) return;
        if (mChannel == null) {
            onError(msg);
            return;
        }
        mPendingReloadUrl = mPlaybackKey != null ? mPlaybackKey : player().getUrl();
        mPendingReloadMsg = msg;
        player().resetTrack();
        player().reset();
        player().stop();
        fetch();
    }

    @Override
    protected void onReclaim() {
        if (isFinishing() || isDestroyed()) return;
        Result result = mViewModel.url().getValue();
        if (result != null) start(result);
    }

    @Override
    protected void onStateChanged(int state) {
        if (isFinishing() || isDestroyed()) return;
        switch (state) {
            case Player.STATE_BUFFERING:
                mFailedThisSession = false;
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                player().reset();
                mFailedThisSession = false;
                resetLineFallback();
                break;
            case Player.STATE_ENDED:
                checkEnded();
                updatePlayControl(false);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isFinishing() || isDestroyed()) return;
        if (isPlaying || isPaused()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.action.action.setText(isPlaying ? R.string.pause : R.string.play);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        if (isFinishing() || isDestroyed()) return;
        applyResizeMode(LiveSetting.getScale());
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    protected void onSurfaceAttached() {
        if (isFinishing() || isDestroyed()) return;
        applyResizeMode(LiveSetting.getScale());
    }

    private void onChannelSelected(int position) {
        if (mChannelAdapter == null || position < 0 || position >= mChannelAdapter.getItemCount()) return;
        Channel channel = mChannelAdapter.get(position);
        boolean hasEpg = channel != null && !channel.getData(mViewModel.getZoneId()).getList().isEmpty();
        mBinding.program.setVisibility(hasEpg ? View.VISIBLE : View.GONE);
    }

    private void onEpgFocusChange() {
        mBinding.program.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            boolean focusEpg = mBinding.program.hasFocus() || mBinding.epgData.hasFocus();
            if (focusEpg) {
                if (!mEpgFirstOpen) {
                    showEpg(getFocusedChannel());
                    mEpgFirstOpen = true;
                }
            } else {
                mEpgFirstOpen = false;
            }
        });
    }

    private Channel getFocusedChannel() {
        int position = mBinding.channel.getSelectedPosition();
        if (mChannelAdapter == null || position < 0 || position >= mChannelAdapter.getItemCount()) return null;
        return mChannelAdapter.get(position);
    }

    public void showEpg(Channel item) {
        if (item == null) return;
        Epg today = item.getData(mViewModel.getZoneId());
        if (today.getList().isEmpty()) return;
        mEpgChannel = item;
        mEpgDayLoading = false;
        mEpgFromEdge = false;
        mEpgPendingOffset = 0;
        mEpgDays.clear();
        mEpgDays.add(today);
        mEpgMinOffset = 0;
        mEpgMaxOffset = 0;
        mEpgDataAdapter.setDays(buildDays(mEpgDays));
        mBinding.epgDate.post(this::updateEpgSticky);

        int selectedPos;
        if (mChannel != null && item.getTvgId().equals(mChannel.getTvgId())) {
            selectedPos = today.getSelected();
        } else {
            selectedPos = today.getInRange();
        }
        if (selectedPos < 0) selectedPos = 0;

        int finalPos = selectedPos;
        mBinding.epgData.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            mBinding.epgData.setSelectedPosition(finalPos);
            // [NEW] 首屏就把前后各一天填上，滚到今日两头时不会再等加载
            checkEpgPreload(finalPos);
        });
        mBinding.epgData.setVisibility(View.VISIBLE);
        mBinding.epgDate.setVisibility(View.VISIBLE);
        mBinding.epgData.post(this::updateEpgSticky);
        App.removeCallbacks(mR4);
    }

    // ==================== [NEW] 节目单跨天无缝滚动 ====================

    // ==================== [NEW] 悬浮日期条 ====================

    /** [CHANGED] 悬浮日期跟随焦点所在的那条 */
    private void updateEpgSticky() {
        if (mBinding == null || isFinishing() || isDestroyed()) return;
        if (mEpgDataAdapter == null || mEpgDataAdapter.getItemCount() == 0) return;
        int position = mBinding.epgData.getSelectedPosition();
        if (position < 0) position = 0;
        String label = mEpgDataAdapter.getLabel(position);
        if (label.equals(mBinding.epgDate.getText().toString())) return;
        mBinding.epgDate.setTranslationY(0);
        mBinding.epgDate.setText(label);
    }

    /**
     * [NEW] 改 adapter 数据的操作一律避开布局阶段：
     * onChildViewHolderSelected 是在 GridLayoutManager.onLayoutChildren 里回调的，
     * 此时 notify 会抛 "Cannot call this method while RecyclerView is computing a layout or scrolling"。
     */
    private void runEpgSafe(Runnable task) {
        if (mBinding == null || isFinishing() || isDestroyed()) return;
        if (mBinding.epgData.isComputingLayout()) mBinding.epgData.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            task.run();
        });
        else task.run();
    }

    private String getToday() {
        return LocalDate.now(mViewModel.getZoneId()).format(Formatters.DATE);
    }

    private String getDate(int offset) {
        return LocalDate.now(mViewModel.getZoneId()).plusDays(offset).format(Formatters.DATE);
    }

    private int offsetOf(String date) {
        try {
            return (int) ChronoUnit.DAYS.between(LocalDate.parse(getToday(), Formatters.DATE), LocalDate.parse(date, Formatters.DATE));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 每天第一条上方显示的日期标签。
     * [CHANGED] 今天也显示（10-08 周三 · 今天），想让今天不显示就删掉下面 today 那一行的判断。
     */
    private String getDayLabel(String date) {
        if (date.isEmpty()) return "";
        try {
            LocalDate local = LocalDate.parse(date, Formatters.DATE);
            String text = local.format(DateTimeFormatter.ofPattern("MM-dd", Locale.ROOT)) + " " + local.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
            if (date.equals(getDate(-1))) return text + " · " + getString(R.string.live_epg_yesterday);
            if (date.equals(getToday())) return text + " · " + getString(R.string.live_epg_today);
            if (date.equals(getDate(1))) return text + " · " + getString(R.string.live_epg_tomorrow);
            return text;
        } catch (Exception e) {
            return date;
        }
    }

    private List<EpgDataAdapter.Day> buildDays(List<Epg> days) {
        List<EpgDataAdapter.Day> result = new ArrayList<>();
        for (Epg epg : days) result.add(new EpgDataAdapter.Day(epg, getDayLabel(epg.getDate())));
        return result;
    }

    /** 取某一天的节目单，包含已解析但为空的（返回 null 表示这一整天还没数据） */
    private Epg findDay(int offset) {
        if (mEpgChannel == null) return null;
        String date = getDate(offset);
        for (int i = 0; i < 3; i++) {
            try {
                for (Epg epg : new ArrayList<>(mEpgChannel.getDataList())) if (epg.equal(date)) return epg;
                return null;
            } catch (Throwable e) {
                SpiderDebug.log("EpgParser", "findDay重试 " + (i + 1) + "：" + e);
            }
        }
        return null;
    }

    /** 焦点接近两头时提前填充相邻一天 */
    private void checkEpgPreload(int position) {
        runEpgSafe(() -> checkEpgPreloadInternal(position));
    }

    private void checkEpgPreloadInternal(int position) {
        if (mEpgChannel == null || isFinishing() || isDestroyed()) return;
        int count = mEpgDataAdapter.getItemCount();
        if (count == 0) return;
        if (position <= EPG_PRELOAD_NEAR) fillEpgDay(-1);
        if (position >= count - 1 - EPG_PRELOAD_NEAR) fillEpgDay(1);
    }

    /** 填充相邻一天：direction = -1 往上（往日）、1 往下（明日） */
    private void fillEpgDay(int direction) {
        if (mEpgChannel == null) return;
        int offset = direction < 0 ? mEpgMinOffset - 1 : mEpgMaxOffset + 1;
        if (offset < -EPG_DAY_LIMIT || offset > EPG_DAY_LIMIT) return;
        Epg epg = findDay(offset);
        if (epg == null) {
            if (!mEpgDayLoading) loadEpgDay(offset, false);
            return;
        }
        if (epg.getList().isEmpty()) {
            // 这天确实没节目，跳过它继续往更早 / 更晚找
            if (direction < 0) mEpgMinOffset = offset;
            else mEpgMaxOffset = offset;
            fillEpgDay(direction);
            return;
        }
        if (direction < 0) insertDayTop(offset, epg);
        else appendDayBottom(offset, epg);
    }

    /** 同一天只允许进列表一次，防止连续预加载重复插入 */
    private boolean containsDay(Epg epg) {
        for (Epg item : mEpgDays) if (item.equal(epg.getDate())) return true;
        return false;
    }

    private void insertDayTop(int offset, Epg epg) {
        if (containsDay(epg)) return;
        mEpgDays.add(0, epg);
        mEpgMinOffset = offset;
        int anchor = mBinding.epgData.getSelectedPosition();
        int anchorTop = getAnchorTop(anchor);
        int size = mEpgDataAdapter.insertDaysTop(buildDays(Collections.singletonList(epg)));
        if (size == 0) return;
        int target = anchor + size;
        mBinding.epgData.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            keepAnchor(target, anchorTop);
            updateEpgSticky();
        });
    }

    private void appendDayBottom(int offset, Epg epg) {
        if (containsDay(epg)) return;
        mEpgDays.add(epg);
        mEpgMaxOffset = offset;
        mEpgDataAdapter.appendDaysBottom(buildDays(Collections.singletonList(epg)));
        mBinding.epgData.post(this::updateEpgSticky);
    }

    private void loadEpgDay(int offset, boolean fromEdge) {
        if (mEpgChannel == null || mEpgDayLoading) return;
        mEpgDayLoading = true;
        mEpgFromEdge = fromEdge;
        mEpgPendingOffset = offset;
        mViewModel.getEpgDay(mEpgChannel, offset);
    }

    private void setEpgDay(Epg epg) {
        mEpgDayLoading = false;
        if (mEpgChannel == null || epg == null) return;
        if (isFinishing() || isDestroyed()) return;
        if (epg.getDate().isEmpty() || epg.getList().isEmpty()) return;
        if (!epg.getKey().equals(mEpgChannel.getTvgId())) return;
        int offset = offsetOf(epg.getDate());
        if (offset != mEpgPendingOffset) return;
        runEpgSafe(() -> setEpgDayInternal(epg, offset));
    }

    private void setEpgDayInternal(Epg epg, int offset) {
        if (mEpgChannel == null || isFinishing() || isDestroyed()) return;
        try {
            mEpgChannel.setData(epg);
        } catch (Throwable e) {
            SpiderDebug.log("EpgParser", "setEpgDay 写入失败：" + e);
        }
        if (offset < 0) {
            if (containsDay(epg)) return;
            int anchor = mBinding.epgData.getSelectedPosition();
            int anchorTop = getAnchorTop(anchor);
            int size = mEpgDataAdapter.insertDaysTop(buildDays(Collections.singletonList(epg)));
            if (size == 0) return;
            mEpgDays.add(0, epg);
            mEpgMinOffset = offset;
            if (mEpgFromEdge) focusEpgPosition(size - 1);
            else mBinding.epgData.post(() -> {
                keepAnchor(anchor + size, anchorTop);
                updateEpgSticky();
            });
        } else if (offset > 0) {
            if (containsDay(epg)) return;
            int start = mEpgDataAdapter.appendDaysBottom(buildDays(Collections.singletonList(epg)));
            if (start < 0) return;
            mEpgDays.add(epg);
            mEpgMaxOffset = offset;
            mBinding.epgData.post(this::updateEpgSticky);
            if (mEpgFromEdge) focusEpgPosition(start);
        }
        mEpgFromEdge = false;
    }

    /** 已经在最上面还往上 / 最下面还往下：接着展示前一天 / 后一天，没数据就停住 */
    private boolean expandEpgDay(int direction) {
        runEpgSafe(() -> expandEpgDayInternal(direction));
        return true;
    }

    private void expandEpgDayInternal(int direction) {
        if (mEpgChannel == null) return;
        int offset = direction < 0 ? mEpgMinOffset - 1 : mEpgMaxOffset + 1;
        if (offset < -EPG_DAY_LIMIT || offset > EPG_DAY_LIMIT) return;
        int before = mEpgDataAdapter.getItemCount();
        int minBefore = mEpgMinOffset;
        int maxBefore = mEpgMaxOffset;
        if (mEpgDayLoading) return;
        fillEpgDay(direction);
        int after = mEpgDataAdapter.getItemCount();
        if (after > before) {
            // 新一天已经填进来，焦点落在紧挨着的那一头，视觉上无缝衔接
            if (direction < 0) focusEpgPosition(after - before - 1);
            else focusEpgPosition(before);
        } else if (mEpgDayLoading) {
            // 这一边的数据正在后台拉取，回来后由 setEpgDay 接管焦点
            if (direction < 0 && mEpgPendingOffset < 0) mEpgFromEdge = true;
            if (direction > 0 && mEpgPendingOffset > 0) mEpgFromEdge = true;
        } else if (direction < 0 && mEpgMinOffset != minBefore) {
            expandEpgDayInternal(direction);
        } else if (direction > 0 && mEpgMaxOffset != maxBefore) {
            expandEpgDayInternal(direction);
        }
    }

    private int getAnchorTop(int position) {
        if (mBinding.epgData.getLayoutManager() == null) return 0;
        View view = mBinding.epgData.getLayoutManager().findViewByPosition(position);
        return view == null ? 0 : view.getTop();
    }

    /** 往日插到顶部后把画面拉回原位，避免内容整体下移 */
    private void keepAnchor(int position, int anchorTop) {
        if (isFinishing() || isDestroyed()) return;
        if (mBinding.epgData.getLayoutManager() == null) return;
        View view = mBinding.epgData.getLayoutManager().findViewByPosition(position);
        if (view == null) return;
        int delta = view.getTop() - anchorTop;
        if (delta != 0) mBinding.epgData.scrollBy(0, delta);
        if (mBinding.epgData.getSelectedPosition() != position) mBinding.epgData.setSelectedPosition(position);
    }

    private void focusEpgPosition(int position) {
        mBinding.epgData.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            mBinding.epgData.setSelectedPosition(position);
            mBinding.epgData.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (mBinding.epgData.getLayoutManager() == null) return;
                View view = mBinding.epgData.getLayoutManager().findViewByPosition(position);
                if (view != null) view.requestFocus();
            });
        });
    }

    /** EPG 整体刷新后按当前已填充的天重建列表 */
    private void refreshEpgDays() {
        runEpgSafe(this::refreshEpgDaysInternal);
    }

    private void refreshEpgDaysInternal() {
        if (mEpgChannel == null || mEpgDays.isEmpty()) return;
        List<Epg> days = new ArrayList<>();
        for (int offset = mEpgMinOffset; offset <= mEpgMaxOffset; offset++) {
            Epg epg = findDay(offset);
            if (epg != null && !epg.getList().isEmpty()) days.add(epg);
        }
        if (days.isEmpty()) return;
        mEpgDays.clear();
        mEpgDays.addAll(days);
        int position = mBinding.epgData.getSelectedPosition();
        mEpgDataAdapter.setDays(buildDays(mEpgDays));
        if (position >= 0 && position < mEpgDataAdapter.getItemCount()) mBinding.epgData.setSelectedPosition(position);
        mBinding.epgData.post(this::updateEpgSticky);
    }

    @Override
    public void hideEpg() {
        mEpgChannel = null;
        mEpgDayLoading = false;
        mEpgFromEdge = false;
        mEpgDays.clear();
        mBinding.epgData.setVisibility(View.GONE);
        mBinding.epgDate.setVisibility(View.GONE);
        setUITimer();
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset(mBinding.progress.traffic);
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl(View view) {
        setPlayParamsState();
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        // 控制栏显示时统一由 PlayerOsdController 显示标题、分辨率和时间，避免旧栏重影
        mBinding.widget.top.setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(true);
        App.post(view::requestFocus, 25);
        setR1Callback();
        hideInfo();
    }

    private void hideControl() {
        if (isFinishing() || isDestroyed()) return;
        mBinding.control.getRoot().setVisibility(View.GONE);
        mBinding.widget.top.setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(false);
        App.removeCallbacks(mR1);
    }

    private void onPlayParams() {
        if (mOsd == null) return;
        boolean visible = !mOsd.isDiagnosticsVisible();
        PlayerSetting.putOsdDiagnostics(visible);
        mOsd.setDiagnosticsVisible(visible);
        setPlayParamsState();
        hideControl();
    }

    private void setPlayParamsState() {
        mBinding.control.action.playParams.setSelected(mOsd != null && mOsd.isDiagnosticsVisible());
        applyActionButtonVisibility();
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showInfo() {
        mBinding.widget.bottom.setVisibility(View.VISIBLE);
        setR3Callback();
        setInfo();
    }

    private void hideInfo() {
        if (isFinishing() || isDestroyed()) return;
        mBinding.widget.bottom.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void setTraffic() {
        if (isFinishing() || isDestroyed()) return;
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE + 3 * 1000L);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork() {
        ImgUtil.load(this, mChannel.getLogo(), new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.exo.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.exo.setDefaultArtwork(errorDrawable);
            }
        });
    }

    @Override
    public void onItemClick(Group item) {
        mChannelAdapter.addAll(setWidth(item).getChannel());
        mBinding.channel.setSelectedPosition(Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        PassDialog.create().show(this);
        App.removeCallbacks(mR4);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (mGroup != null) {
            mGroup.setPosition(mBinding.channel.getSelectedPosition());
            setChannel(item.group(mGroup));
            hideUI();
        }
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = Keep.exist(item.getName());
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        return true;
    }

    @Override
    public void onItemClick(EpgData item) {
        if (item.isSelected()) {
            fetch(item);
        } else if (mChannel.hasCatchup() || mChannel.isRtsp()) {
            mBinding.widget.title.setText(getString(R.string.detail_title, mChannel.getShow(), item.getTitle()));
            Notify.show(getString(R.string.play_ready, item.getTitle()));
            setSelected(item);
            fetch(item);
        }
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        keep.save();
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        if (mChannelAdapter.getItemCount() == 0) mBinding.group.requestFocus();
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setChannel(Channel item) {
        App.post(mR0, 100);
        mChannel = item;
        resetLineFallback();
        setArtwork();
        showInfo();
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mBinding.widget.play.setText("");
        mBinding.widget.name.setMaxEms(48);
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.title.setSelected(true);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.widget.title.setText(mChannel.getShow());
        mBinding.control.action.line.setText(mChannel.getLine());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setVisibility(mChannel.getLineVisible());
    }

    private void setEpg(Epg epg) {
        if (isFinishing() || isDestroyed()) return;
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        boolean hasTitle = !data.getTitle().isEmpty();
        if (mEpgChannel == null || mEpgChannel.equals(mChannel)) {
            // [CHANGED] 只换今日会丢掉已展开的往日 / 明日，改为按已填充的天整体重建
            refreshEpgDays();
        }
        if (hasTitle) mBinding.widget.title.setText(getString(R.string.detail_title, mChannel.getShow(), data.getTitle()));
        mBinding.widget.name.setMaxEms(hasTitle ? 12 : 48);
        mBinding.widget.play.setText(data.format());
        setWidth(epg);
        setMetadata();
    }

    /**
     * [CHANGED] notify 不再依赖 success：EPG 是增强信息，只要列表存在就刷新一次，
     * 避免 parseXml 返回 false 时可见项一直不重绘（必须滚动才显示）。
     */
    private void setEpg(boolean success) {
        SpiderDebug.log("EpgParser", "setEpg success=" + success + " adapterCount=" + (mChannelAdapter != null ? mChannelAdapter.getItemCount() : -1) + " firstChannelEpg=" + (mChannel != null ? mChannel.getDataList().size() : -1));
        mChannelAdapter.notifyDataSetChanged();
        if (mChannel != null) mViewModel.getEpg(mChannel);
    }

    private void fetch(EpgData item) {
        if (mChannel == null) return;
        App.removeCallbacks(mEndRetry);
        App.removeCallbacks(mBufferingTimeout);
        App.post(mBufferingTimeout, LIVE_BUFFERING_TIMEOUT);
        playbackCatchup = true;
        mViewModel.getUrl(mChannel, item);
        mWebViewPlayer.detach();
        player().clear();
        player().stop();
        hideUI();
    }

    private void fetch() {
        if (mChannel == null) return;
        App.removeCallbacks(mEndRetry);
        App.removeCallbacks(mBufferingTimeout);
        App.post(mBufferingTimeout, LIVE_BUFFERING_TIMEOUT);
        playbackCatchup = false;
        LiveConfig.get().setKeep(mChannel);
        mViewModel.getUrl(mChannel);
        mWebViewPlayer.detach();
        player().clear();
        player().stop();
        showProgress();
    }

    private void start(Result result) {
        if (isFinishing() || isDestroyed()) return;
        String realUrl = result.getRealUrl();
        if (isSameReloadUrl(realUrl)) {
            String msg = mPendingReloadMsg;
            clearPendingReload();
            handleSameReloadUrl(msg);
            return;
        }
        clearPendingReload();
        mPlaybackKey = realUrl;
        if (isWebViewChannel()) {
            startWebView(realUrl);
            return;
        }
        mWebViewPlayer.detach();
        startPlayer(mPlaybackKey, result, false, getHome().getTimeout(), buildMetadata());
        mBinding.control.action.speed.setText(player().setSpeed(playbackCatchup ? PlayerSetting.getDefaultSpeed() : 1f));
    }

    private boolean isWebViewChannel() {
        return mChannel != null && mChannel.getCurrent().startsWith("webview://");
    }

    private void startWebView(String url) {
        if (player() != null) {
            player().stop();
            player().clear();
        }
        hideProgress();
        mWebViewPlayer.attach(this, mBinding.video, url);
        bringOverlaysToFront();
    }

    private void bringOverlaysToFront() {
        if (mBinding.widget != null) mBinding.widget.getRoot().bringToFront();
        if (mBinding.control != null) mBinding.control.getRoot().bringToFront();
        if (mBinding.progress != null) mBinding.progress.getRoot().bringToFront();
        if (mBinding.osd != null) mBinding.osd.getRoot().bringToFront();
    }

    private boolean isSameReloadUrl(String realUrl) {
        return !TextUtils.isEmpty(mPendingReloadUrl) && TextUtils.equals(mPendingReloadUrl, realUrl);
    }

    private void clearPendingReload() {
        mPendingReloadUrl = null;
        mPendingReloadMsg = null;
    }

    private void handleSameReloadUrl(String msg) {
        // 同 URL 重载失败等价于该线路播放失败：走带绕圈/耗尽保护的自动换线，
        // 不重置本轮回退状态，避免 reload 路径重新开启死循环。
        // 沿用重载路径既有语义（不校验用户的自动换线开关），只补齐保护：
        // 换不动（单线路或本轮已绕回起点）时必须给出可见失败反馈，
        // 否则 fetch() 刚 showProgress() 的进度条会一直转、错误文案永远不出现。
        if (advanceLineForFallback()) return;
        App.removeCallbacks(mBufferingTimeout);
        showError(msg);
    }

    private void resetAdapter() {
        liveParsed = false;      // [NEW]
        epgRequested = false;    // [NEW]
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.widget.title.setText("");
        mEpgDataAdapter.clear();
        mEpgDays.clear();
        mEpgDayLoading = false;
        mEpgFromEdge = false;
        mEpgMinOffset = 0;
        mEpgMaxOffset = 0;
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).player(player()).show(this);
        App.post(this::hideControl, 100);
    }

    @Override
    public void setConfig(Config config) {
        Config current = LiveConfig.get().getConfig();
        LiveConfig.load(config, getCallback(current));
    }

    private Callback getCallback(Config config) {
        return new Callback() {
            @Override
            public void start() {
                showProgress();
            }

            @Override
            public void success() {
                setLive(getHome());
            }

            @Override
            public void error(String msg) {
                LiveConfig.load(config, new Callback());
                Notify.show(msg);
                hideProgress();
            }
        };
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
        if (mWebViewPlayer != null) mWebViewPlayer.detach();
        player().reset();
        player().clear();
        player().stop();
        resetLineFallback();
        resetAdapter();
        hideControl();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    private void unlock(String pass) {
        boolean first = true;
        int position = mGroupAdapter.getItemCount();
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(mGroupAdapter.getItemCount(), item);
            if (first) mBinding.group.setSelectedPosition(position);
            if (first) onItemClick(mGroup = item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isFinishing() || isDestroyed()) return;
        switch (event.getType()) {
            case LIVE:
                setLive(getHome());
                break;
            case PLAYER:
                fetch();
                break;
        }
    }

    private void checkEnded() {
        App.removeCallbacks(mEndRetry);
        App.post(mEndRetry, PLAYBACK_END_RETRY_DELAY);
    }

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.speed.setVisibility(player().isVod() ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private MediaMetadata buildMetadata() {
        String artist = mBinding.widget.play.getText().toString();
        return PlayerManager.buildMetadata(mChannel.getShow(), artist, mChannel.getLogo());
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    /**
     * 线路播放失败的自动回退：只在当前频道的线路间轮换（含最后一条绕回），
     * 试完一圈即停，绝不因播放失败直接跳到配置里的下一个直播接口。
     * 调用点：onError、缓冲超时（mBufferingTimeout -> onBufferingTimeout）。
     * <p>
     * 本方法只负责「还能不能再换一条线路」，换不动时返回 false，
     * 失败文案由调用方负责显示。
     *
     * @return true 表示已换到另一条线路并重新拉流
     */
    private boolean startFlow() {
        LiveSourceFallbackPolicy.Action action = LiveSourceFallbackPolicy.decideLineFailure(
                LiveSetting.isChange(),
                mChannel != null,
                mChannel != null && !mChannel.isOnly());
        return action == LiveSourceFallbackPolicy.Action.NEXT_LINE && advanceLineForFallback();
    }

    /**
     * 直播接口节目列表拉取失败（renderLive 空结果）时的回退：只有此时才直接
     * 跳转到配置里的下一个直播接口。
     */
    private void startSourceFallback() {
        Live next = LiveConfig.getNextHome();
        LiveSourceFallbackPolicy.Action action = LiveSourceFallbackPolicy.decideSourceFailure(
                LiveSetting.isSourceFallback(),
                next != null);
        if (action == LiveSourceFallbackPolicy.Action.NEXT_SOURCE) setLive(next);
    }

    /**
     * 播放失败自动换线：从失败起点开始轮换，绕回起点即本轮所有线路都已试过，置位
     * exhausted 停止后续回退；否则刷新线路信息并重新拉流。
     * <p>
     * 本方法是唯一改动 mChannel 线路下标的地方，因此 exhausted 短路必须放在这里：
     * 无论从 onError、缓冲超时还是同 URL 重载进来，都不可能再开启新一轮
     * A→B→A 死循环（否则 anchor 未清时换线会重新绕回起点并重新拉流）。
     *
     * @return true 表示已换到另一条线路并重新拉流；false 表示没有可换的线路
     * （单线路）或本轮线路已绕回起点（回退耗尽），调用方需要自行给出失败反馈。
     */
    private boolean advanceLineForFallback() {
        if (isLineFallbackExhausted()) return false;
        if (mChannel == null || mChannel.isOnly()) return false;
        if (mLineFallbackAnchor < 0) mLineFallbackAnchor = mChannel.getIndex();
        mChannel.switchLine(true);
        if (mChannel.getIndex() == mLineFallbackAnchor) {
            mLineFallbackExhausted = true;
            return false;
        }
        showInfo();
        fetch();
        return true;
    }

    /**
     * 播放失败回退已耗尽（本轮所有线路都试过且都失败）时不再自动换线，避免死循环。
     */
    private boolean isLineFallbackExhausted() {
        return mLineFallbackExhausted;
    }

    /**
     * 手动换线、换台、换直播接口或播放成功后，重置失败回退轮换状态。
     */
    private void resetLineFallback() {
        mLineFallbackAnchor = -1;
        mLineFallbackExhausted = false;
    }

    private void prevChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() - 1;
        boolean limit = position < 0;
        if (LiveSetting.isAcross() & limit) prevGroup();
        else mGroup.setPosition(limit ? mChannelAdapter.getItemCount() - 1 : position);
        if (!mGroup.isEmpty()) setChannel(mGroup.current());
    }

    private void nextChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() + 1;
        boolean limit = position > mChannelAdapter.getItemCount() - 1;
        if (LiveSetting.isAcross() && limit) nextGroup();
        else mGroup.setPosition(limit ? 0 : position);
        if (!mGroup.isEmpty()) setChannel(mGroup.current());
    }

    private boolean nextGroup() {
        int position = mBinding.group.getSelectedPosition() + 1;
        if (position > mGroupAdapter.getItemCount() - 1) position = 0;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = mGroupAdapter.get(position);
        mBinding.group.setSelectedPosition(position);
        if (mGroup.skip()) return nextGroup();
        mChannelAdapter.addAll(mGroup.getChannel());
        mGroup.setPosition(0);
        return true;
    }

    private boolean prevGroup() {
        int position = mBinding.group.getSelectedPosition() - 1;
        if (position < 0) position = mGroupAdapter.getItemCount() - 1;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = mGroupAdapter.get(position);
        mBinding.group.setSelectedPosition(position);
        if (mGroup.skip()) return prevGroup();
        mChannelAdapter.addAll(mGroup.getChannel());
        mGroup.setPosition(mGroup.getChannel().size() - 1);
        return true;
    }

    private void checkNext() {
        if (mChannel == null) return;
        int current = mChannel.getData(mViewModel.getZoneId()).getInRange();
        int position = mChannel.getData(mViewModel.getZoneId()).getSelected() + 1;
        boolean hasNext = position <= current && position > 0;
        if (hasNext) onItemClick(mChannel.getData(mViewModel.getZoneId()).getList().get(position));
        else fetch();
    }

    private void prevLine() {
        if (mChannel == null || mChannel.isOnly()) return;
        resetLineFallback();
        mChannel.switchLine(false);
        showInfo();
        fetch();
    }

    private void nextLine(boolean show) {
        if (mChannel == null || mChannel.isOnly()) return;
        resetLineFallback();
        mChannel.switchLine(true);
        if (show) showInfo();
        else setInfo();
        fetch();
    }

    private void seek(long time) {
        mKeyDown.reset();
        seekTo(time);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE ? mBinding.control.action.config : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (mKeyDown.hasEvent(event) && service() != null) mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setUITimer() {
        App.post(mR4, Constant.INTERVAL_HIDE);
    }

    @Override
    public boolean dispatch(boolean check) {
        return !check || isGone(mBinding.recycler) && isGone(mBinding.control.getRoot());
    }

    @Override
    public void onShow(String number) {
        mBinding.widget.digital.setText(number);
        mBinding.widget.digital.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFind(String number) {
        mBinding.widget.digital.setVisibility(View.GONE);
        setPosition(LiveConfig.get().findByChannelNumber(number, mGroupAdapter.unmodifiableList()));
    }

    @Override
    public void onSeeking(long time) {
        if (player().isLive()) return;
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onKeyUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onKeyDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onKeyLeft(long time) {
        if (player().isLive()) prevLine();
        else App.post(() -> seek(time), 250);
    }

    @Override
    public void onKeyRight(long time) {
        if (player().isLive()) nextLine(true);
        else App.post(() -> seek(time), 250);
    }

    @Override
    public void onKeyCenter() {
        hideInfo();
        showUI();
    }

    @Override
    public void onMenu() {
        showControl(getFocus2());
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        else if (isVisible(mBinding.control.getRoot())) hideControl();
        else onMenu();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        if (mOsd != null) {
            mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
            mOsd.start();
        }
        if (mWebViewPlayer != null) mWebViewPlayer.onResume();
        setPlayParamsState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (mWebViewPlayer != null) mWebViewPlayer.onPause();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.bottom)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else {
            finishLivePlayback();
        }
    }

    private void finishLivePlayback() {
        markPlaybackExiting();
        if (service() != null) service().shutdown();
        else stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        if (mWebViewPlayer != null) mWebViewPlayer.detach();
        Source.get().exit();
        App.removeCallbacks(mR0, mR1, mR2, mR3, mR4);
        App.removeCallbacks(mEndRetry);
        App.removeCallbacks(mBufferingTimeout);
        if (mOsd != null) mOsd.release();
        mViewModel.url().removeObserver(mObserveUrl);
        mViewModel.epg().removeObserver(mObserveEpg);
        if (mObserveDay != null) mViewModel.day().removeObserver(mObserveDay);
        super.onDestroy();
    }
}
