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
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.PlaybackSpeedDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.PlayerOsdDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LiveActivity extends PlaybackActivity implements GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, TrackDialog.Listener, PassListener, ConfigListener, LiveListener {

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
    private View mOldView;
    private Group mGroup;
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
    private int count;
    private boolean mFailedThisSession;
    /**
     * 本轮播放失败自动换线的起点线路下标，配合 {@link #mLineFallbackExhausted} 使用。
     * -1 表示当前没有进行中的失败回退轮换；自动换线绕回该下标即本频道所有线路都已试过，
     * 置位 exhausted 阻止 onError 立即开启新一轮死循环；手动换线/换台/换源或播放成功后重置。
     */
    private int mLineFallbackAnchor = -1;
    private boolean mLineFallbackExhausted;

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
        setViewModel();
        setupActionButtons();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        mBinding.channel.setListener(this);
        mBinding.epgData.setListener(this);
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.action.speed.setDownListener(this::onSpeedSub);
        mBinding.control.action.text.setUpListener(this::onSubtitleClick);
        mBinding.control.action.text.setDownListener(this::onSubtitleClick);
        mBinding.control.action.source.setOnClickListener(view -> onLiveSource());
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
        mViewModel.live().observe(this, this::renderLive);
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
        Live live = getHome();
        setSource(live);
        mViewModel.parse(live);
        showProgress();
    }

    /**
     * 控制栏的「直播源」按钮显示当前直播源名称：固定写「直播源」看不出此刻播的是哪一路源，
     * 用户换源（含失效自动换源）都会重新走 {@link #getLive()} 刷新。名称为空时回退原文案。
     * 按钮文案被源名取代后读屏会只剩一个孤立的源名，因此同步补回「直播源」动作语义。
     */
    private void setSource(Live live) {
        String name = live == null ? "" : live.getDisplayName();
        boolean named = !name.isEmpty();
        String label = named ? name : getString(R.string.live_control_source);
        mBinding.control.action.source.setText(label);
        mBinding.control.action.source.setContentDescription(named ? getString(R.string.live_control_source_named, name) : null);
    }

    private void renderLive(Live live) {
        if (live == null || live.getGroups().isEmpty()) {
            if (LiveSetting.isSourceFallback()) startSourceFallback();
            return;
        }
        mViewModel.parseXml(live);
        setGroup(live);
        setWidth(live);
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        for (Group group : live.getGroups()) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.addAll(items);
        setPosition(LiveConfig.get().findKeepPosition(items));
    }

    private void setWidth(Live live) {
        int padding = ResUtil.dp2px(52);
        if (live.getWidth() == 0) for (Group item : live.getGroups()) live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(item.getDisplayName(), 16)));
        int width = live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + padding, ResUtil.getScreenWidth() / 4);
        setWidth(mBinding.group, width);
    }

    private Group setWidth(Group group) {
        int logo = ResUtil.dp2px(60);
        int padding = ResUtil.dp2px(64);
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel item : group.getChannel()) group.setWidth(Math.max(group.getWidth(), (item.getLogo().isEmpty() ? 0 : logo) + ResUtil.getTextWidth(item.getNumber() + item.getName(), 16)));
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
            if (params.width == width) return;
            params.width = width;
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

    private void onOsd() {
        PlayerOsdDialog.show(this, ResUtil.getStringArray(R.array.select_live_player_osd), PlayerSetting.getLiveDisplayChecked(), checked -> {
            PlayerSetting.putLiveDisplayChecked(checked);
            if (mOsd != null) {
                mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
                mOsd.start();
            }
        });
        hideControl();
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
        App.removeCallbacks(mR4);
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        setPosition();
        setUITimer();
        hideEpg();
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
        App.removeCallbacks(mBufferingTimeout);
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
        Result result = mViewModel.url().getValue();
        if (result != null) start(result);
    }

    @Override
    protected void onStateChanged(int state) {
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
        if (isPlaying) App.removeCallbacks(mBufferingTimeout);
        if (isPlaying || isPaused()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.action.action.setText(isPlaying ? R.string.pause : R.string.play);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        applyResizeMode(LiveSetting.getScale());
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    protected void onSurfaceAttached() {
        applyResizeMode(LiveSetting.getScale());
    }

    @Override
    public void showEpg(Channel item) {
        if (mChannel == null || mChannel.getData(mViewModel.getZoneId()).getList().isEmpty() || mEpgDataAdapter.getItemCount() == 0 || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        mBinding.epgData.setSelectedPosition(mChannel.getData(mViewModel.getZoneId()).getSelected());
        mBinding.epgData.setVisibility(View.VISIBLE);
        mBinding.channel.setVisibility(View.GONE);
        mBinding.group.setVisibility(View.GONE);
        mBinding.epgData.requestFocus();
    }

    @Override
    public void hideEpg() {
        mBinding.channel.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.epgData.setVisibility(View.GONE);
        mBinding.channel.requestFocus();
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
        // 与 mobile 一致：唤出控制栏时重算轨道类按钮，用户重新开启后不必等下一次轨道变化。
        if (service() != null) setTrackVisible();
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
        mBinding.widget.bottom.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic, service() == null ? null : player());
        App.post(mR2, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE);
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
        if (!item.getData(mViewModel.getZoneId()).getList().isEmpty() && item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            showEpg(item);
        } else if (mGroup != null) {
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
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        boolean hasTitle = !data.getTitle().isEmpty();
        mEpgDataAdapter.addAll(epg.getList());
        if (hasTitle) mBinding.widget.title.setText(getString(R.string.detail_title, mChannel.getShow(), data.getTitle()));
        mBinding.widget.name.setMaxEms(hasTitle ? 12 : 48);
        mBinding.widget.play.setText(data.format());
        setWidth(epg);
        setMetadata();
    }

    private void setEpg(boolean success) {
        if (mChannel != null && success) mViewModel.getEpg(mChannel);
    }

    private void fetch(EpgData item) {
        if (mChannel == null) return;
        App.removeCallbacks(mEndRetry);
        App.removeCallbacks(mBufferingTimeout);
        App.post(mBufferingTimeout, LIVE_BUFFERING_TIMEOUT);
        playbackCatchup = true;
        mViewModel.getUrl(mChannel, item);
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
        player().clear();
        player().stop();
        showProgress();
    }

    private void start(Result result) {
        String realUrl = result.getRealUrl();
        if (isSameReloadUrl(realUrl)) {
            String msg = mPendingReloadMsg;
            clearPendingReload();
            handleSameReloadUrl(msg);
            return;
        }
        clearPendingReload();
        mPlaybackKey = realUrl;
        startPlayer(mPlaybackKey, result, false, getHome().getTimeout(), buildMetadata());
        mBinding.control.action.speed.setText(player().setSpeed(playbackCatchup ? PlayerSetting.getDefaultSpeed() : 1f));
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
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.widget.title.setText("");
        mEpgDataAdapter.clear();
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
        setPlayParamsState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
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
        Source.get().exit();
        App.removeCallbacks(mR0, mR1, mR2, mR3, mR4);
        App.removeCallbacks(mEndRetry);
        App.removeCallbacks(mBufferingTimeout);
        if (mOsd != null) mOsd.release();
        mViewModel.url().removeObserver(mObserveUrl);
        mViewModel.epg().removeObserver(mObserveEpg);
        super.onDestroy();
    }
}
