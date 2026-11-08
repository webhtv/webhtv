package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.CastVideo;
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
import com.fongmi.android.tv.player.VideoAspectMode;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.LiveEpgSetting;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.LiveListScrim;
import com.fongmi.android.tv.theme.PlaybackMenuScrim;
import com.fongmi.android.tv.ui.adapter.ChannelAdapter;
import com.fongmi.android.tv.ui.helper.PipExitDecision;
import com.fongmi.android.tv.ui.adapter.EpgDataAdapter;
import com.fongmi.android.tv.ui.adapter.GroupAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDown;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.PlaybackSpeedDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.LiveControlDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.LiveEpgDialog;
import com.fongmi.android.tv.ui.dialog.LiveLineDialog;
import com.fongmi.android.tv.ui.dialog.LiveProgramDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Biometric;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LiveActivity extends PlaybackActivity implements CustomKeyDown.Listener, TrackDialog.Listener, Biometric.Callback, PassListener, ConfigListener, LiveListener, GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener, LiveControlDialog.Listener, LiveEpgDialog.Listener {

    private static final int LIVE_PIP_WIDTH = 16;
    private static final int LIVE_PIP_HEIGHT = 9;
    private static final long PLAYBACK_END_RETRY_DELAY = 500;
    private static final long LIVE_BUFFERING_TIMEOUT = 15000;
    private static final String ORIENTATION_TAG = "LiveOrientation";

    private ActivityLiveBinding mBinding;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private Observer<Result> mObserveUrl;
    private GroupAdapter mGroupAdapter;
    private Observer<Epg> mObserveEpg;
    private LiveViewModel mViewModel;
    private CustomKeyDown mKeyDown;
    private PlayerOsdController mOsd;
    private Result mPendingStartResult;
    private List<Group> mHides;
    private Map<String, View> mActionButtons;
    private String mPlaybackKey;
    private String mPendingReloadUrl;
    private String mPendingReloadMsg;
    private Channel mChannel;
    private Group mGroup;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mEndRetry;
    private Runnable mBufferingTimeout;
    private boolean rotate;
    /**
     * 平板全屏内的方向覆盖：true 表示全屏时请求竖屏。
     * 直播的 {@link #rotate} 语义是「是否全屏」，与点播的「是否竖屏」不同，
     * 因此用手动旋转必须用独立状态，避免破坏 {@link #isEmbeddedLiveUi()} 的判定。
     */
    private boolean livePortraitFullscreen;
    private int count;
    private boolean mFailedThisSession;
    /**
     * 本轮播放失败自动换线的起点线路下标，配合 {@link #mLineFallbackExhausted} 使用。
     * -1 表示当前没有进行中的失败回退轮换；自动换线绕回该下标即本频道所有线路都已试过，
     * 置位 exhausted 阻止 onError 立即开启新一轮死循环；手动换线/换台/换源或播放成功后重置。
     */
    private int mLineFallbackAnchor = -1;
    private boolean mLineFallbackExhausted;
    private PiP mPiP;
    private boolean mKeepPlaybackAfterPipExit;
    private OneShotPreDrawListener pipEntryListener;
    private boolean liveMenuRendered;
    private Boolean embeddedUiMode;
    private Channel lastLineClickChannel;
    private CustomTarget<Drawable> mArtworkTarget;
    private long lastLineClickTime;
    private boolean pendingShowEpg;
    private boolean pendingShowProgram;
    private boolean playbackCatchup;
    private boolean liveMenuOverlay;
    private boolean pipEntryPending;
    private VideoSize videoSize;
    private int groupBasePaddingBottom;
    private int channelBasePaddingBottom;
    private int epgBasePaddingBottom;
    private int statusBarInset;

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
        return true;
    }

    @Override
    protected boolean isLutAllowed() {
        return false;
    }

    @Override
    protected boolean customWallMotion() {
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
        if (mPendingStartResult != null) {
            Result result = mPendingStartResult;
            mPendingStartResult = null;
            start(result);
        } else {
            checkLive();
        }
    }

    @Override
    protected void onPlayerRebuilt() {
        setPlayerKernel();
        setDecode();
        refreshControlDialog();
    }

    private void refreshControlDialog() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof LiveControlDialog dialog) dialog.setPlayer();
        }
    }

    @Override
    protected boolean shouldAutoPlay() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestOrientation("launch", getLaunchOrient());
        super.onCreate(savedInstanceState);
        updateSystemUI();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        applyPlaybackOverlay();
        mKeyDown = CustomKeyDown.create(this, mBinding.exo);
        captureLiveListBasePadding();
        setupWindowInsets();
        updateControlInsets();
        updateLiveMenuInsets();
        mObserveEpg = this::setEpg;
        mObserveUrl = this::start;
        mHides = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mBufferingTimeout = this::onBufferingTimeout;
        mR3 = this::hideInfo;
        mEndRetry = this::checkNext;
        mPiP = new PiP();
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

            @Override
            public boolean isFullscreen() {
                // PiP 小窗不算全屏：媒体徽标只在真全屏显示。
                return !isEmbeddedLiveUi() && !isInPictureInPictureMode();
            }
        }, 12f);
        setVideoView();
        setNavigation();
        setViewModel();
        applyPadLiveMode();
        setupActionButtons();
        mBinding.exo.post(() -> applyLiveResizeMode(LiveSetting.getScale()));
    }

    private void applyPlaybackOverlay() {
        mBinding.control.getRoot().setBackgroundResource(R.color.transparent);
        if (Setting.isPlaybackOverlayEnabled()) mBinding.control.bottom.setBackground(PlaybackMenuScrim.create(this));
        else mBinding.control.bottom.setBackgroundResource(R.color.transparent);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.osdDiagnostics.setOnClickListener(view -> onPlayParams());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> nextChannel());
        mBinding.control.prev.setOnClickListener(view -> prevChannel());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.right.pip.setOnClickListener(view -> onPiP());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.source.setOnClickListener(view -> onFullscreenLiveSource());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onPlayerKernelLong());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.playParams.setOnClickListener(view -> onPlayParams());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        if (mBinding.liveSource != null) {
            mBinding.liveSource.setOnClickListener(view -> onLiveSource());
            mBinding.liveSource.setOnTouchListener(this::onLiveSettingTouch);
        }
        if (mBinding.liveSetting != null) {
            mBinding.liveSetting.setOnClickListener(view -> onLiveSetting());
            mBinding.liveSetting.setOnTouchListener(this::onLiveSettingTouch);
        }
        if (mBinding.liveCurrent != null) mBinding.liveCurrent.setOnClickListener(view -> onLiveProgram());
        if (mBinding.liveProgram != null) mBinding.liveProgram.setOnClickListener(view -> onLiveProgram());
        if (mBinding.liveProgramNext != null) mBinding.liveProgramNext.setOnClickListener(view -> onLiveProgram());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.group.setHasFixedSize(true);
        mBinding.channel.setHasFixedSize(true);
        mBinding.epgData.setHasFixedSize(true);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.channel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                mChannelAdapter.scheduleWindowUpdate(recyclerView);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (isFinishing() || isDestroyed()) return;
                if (newState == RecyclerView.SCROLL_STATE_IDLE) Glide.with(recyclerView).resumeRequests();
                else Glide.with(recyclerView).pauseRequests();
            }
        });
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
    }

    private void captureLiveListBasePadding() {
        groupBasePaddingBottom = mBinding.group.getPaddingBottom();
        channelBasePaddingBottom = mBinding.channel.getPaddingBottom();
        epgBasePaddingBottom = mBinding.epgData.getPaddingBottom();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (view, insets) -> {
            updateStatusBarInset(insets);
            updateLiveListBottomInset(insets);
            return insets;
        });
        ViewCompat.requestApplyInsets(mBinding.getRoot());
    }

    private void setVideoView() {
        updateVideoHeight(VideoSize.UNKNOWN);
        setScale(LiveSetting.getScale());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
        applyLiveListStyle();
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
    }

    private void setNavigation() {
        mBinding.navigation.getMenu().findItem(R.id.vod).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.live).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.setting).setVisible(true);
        mBinding.navigation.setSelectedItemId(R.id.live);
        mBinding.navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.live) return true;
            int position = item.getItemId() == R.id.setting ? 1 : 0;
            startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(HomeActivity.EXTRA_NAV_POSITION, position));
            finish();
            return true;
        });
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setPlayerKernel() {
        mBinding.control.action.player.setText(player().getPlayerText());
    }

    /**
     * 直播动作栏登记点播同名的播放器按钮，使「设置-播放-播放器按钮」的开关与排序对直播同样生效。
     * 直播专属按钮（配置、直播源、线路、反色、跨类）不登记，始终按布局显示。
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

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        applyLiveResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void applyLiveResizeMode(int scale) {
        applyResizeMode(scale);
        updatePlayerBounds(scale);
        mBinding.exo.post(() -> {
            int current = LiveSetting.getScale();
            applyResizeMode(current);
            updatePlayerBounds(current);
        });
    }

    private void updatePlayerBounds(int scale) {
        int parentWidth = mBinding.video.getWidth();
        int parentHeight = mBinding.video.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) return;
        FrameLayout.LayoutParams params = getPlayerLayoutParams();
        float viewportRatio = (float) parentWidth / parentHeight;
        float ratio = VideoAspectMode.resolve(scale, viewportRatio, PlayerSetting.getCustomAspectRatio()).targetAspectRatio();
        if (ratio > 0f) {
            int width = parentWidth;
            int height = Math.round(width / ratio);
            if (height > parentHeight) {
                height = parentHeight;
                width = Math.round(height * ratio);
            }
            if (params.width == width && params.height == height && params.gravity == Gravity.CENTER) return;
            params.width = width;
            params.height = height;
            params.gravity = Gravity.CENTER;
        } else {
            if (params.width == ViewGroup.LayoutParams.MATCH_PARENT && params.height == ViewGroup.LayoutParams.MATCH_PARENT && params.gravity == Gravity.CENTER) return;
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.CENTER;
        }
        mBinding.exo.setLayoutParams(params);
    }

    private FrameLayout.LayoutParams getPlayerLayoutParams() {
        ViewGroup.LayoutParams params = mBinding.exo.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams frame) return frame;
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
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
        if (!live.getGroups().isEmpty()) renderLive(live);
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
        refreshControlDialog();
    }

    private void renderLive(Live live) {
        if (live == null || live.getGroups().isEmpty()) {
            if (LiveSetting.isSourceFallback()) startSourceFallback();
            return;
        }
        if (liveMenuRendered) return;
        liveMenuRendered = true;
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
        if (isEmbeddedLiveUi()) return;
        int padding = ResUtil.dp2px(48);
        if (live.getWidth() == 0) for (Group item : live.getGroups()) live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(item.getDisplayName(), 14)));
        int width = live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + padding, ResUtil.getScreenWidth() / 4);
        setWidth(mBinding.group, width);
    }

    @Override
    public void setWidth(Group group) {
        if (isEmbeddedLiveUi()) return;
        int logo = ResUtil.dp2px(56);
        int padding = ResUtil.dp2px(60);
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel item : group.getChannel()) group.setWidth(Math.max(group.getWidth(), (item.getLogo().isEmpty() ? 0 : logo) + ResUtil.getTextWidth(item.getNumber() + item.getName(), 14)));
        int width = group.getWidth() == 0 ? 0 : Math.min(group.getWidth() + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.channel, width);
    }

    private void setWidth(Epg epg) {
        if (isEmbeddedLiveUi()) return;
        int padding = ResUtil.dp2px(48);
        if (epg.getList().isEmpty()) return;
        int minWidth = ResUtil.getTextWidth(epg.getList().get(0).getTime(), 12);
        if (epg.getWidth() == 0) for (EpgData item : epg.getList()) epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(item.getTitle(), 14)));
        int width = epg.getWidth() == 0 ? 0 : Math.min(Math.max(epg.getWidth(), minWidth) + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.epgData, width);
    }

    private void setWidth(View view, int width) {
        if (isEmbeddedLiveUi()) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.width == width) return;
        params.width = width;
        view.setLayoutParams(params);
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mGroup.setPosition(position[1]);
        onItemClick(mGroup);
        selectChannel(mGroup.current(), true);
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mGroupAdapter.getPosition() != position;
        if (change) mGroupAdapter.setSelected(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel(), mGroup.getPosition());
        if (!change) mChannelAdapter.setSelected(mGroup.getPosition());
        scrollToChannelPosition(mGroup.getPosition());
        scrollToPosition(mBinding.group, position);
    }

    private void onBack() {
        if (isPadLiveFullscreenMode() && !isEmbeddedLiveUi()) {
            finishLivePlayback();
            return;
        }
        if (!isEmbeddedLiveUi()) {
            exitFullscreenLive();
            return;
        }
        finishLivePlayback();
    }

    private void finishLivePlayback() {
        markPlaybackExiting();
        if (service() != null) service().shutdown();
        else stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void onCast() {
        CastDialog.create().video(new CastVideo(mBinding.control.title.getText().toString(), player().getUrl(), androidx.media3.common.C.TIME_UNSET, player().getHeaders())).fm(false).show(this);
    }

    private void onInfo() {
        InfoDialog.create().title(mBinding.control.title.getText()).headers(player().getHeaders()).url(player().getUrl()).show(this);
    }

    private void onLock() {
        int requested = getRequestedOrientation();
        setLock(!isLock());
        Log.i(ORIENTATION_TAG, "lock changed locked=" + isLock() + " requestedUnchanged=" + requested + " " + orientationState());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        Log.i(ORIENTATION_TAG, "rotate clicked embedded=" + isEmbeddedLiveUi() + " " + orientationState());
        if (isEmbeddedLiveUi()) {
            enterFullscreenLive();
        } else if (ResUtil.isPad()) {
            toggleFullscreenOrientation();
        } else {
            exitFullscreenLive();
        }
    }

    /**
     * 平板全屏内的旋转：只切换全屏方向（竖屏全屏↔横屏全屏），不退出全屏，
     * 与点播 {@code VideoActivity#onRotate} 的语义保持一致。
     */
    private void toggleFullscreenOrientation() {
        livePortraitFullscreen = !livePortraitFullscreen;
        Log.i(ORIENTATION_TAG, "rotate fullscreen portrait=" + livePortraitFullscreen + " " + orientationState());
        setR1Callback();
        requestOrientation("rotate-fullscreen-pad", getFullscreenOrient());
    }

    private void onPiP() {
        if (pipEntryPending || isInPictureInPictureMode()) return;
        if (!preparePiP("panel")) return;
        prepareLivePiPView();
        pipEntryPending = true;
        scheduleLivePiPEntry();
    }

    private void prepareLivePiPView() {
        hideControl();
        hideInfo();
        mBinding.recycler.setVisibility(View.GONE);
        mBinding.navigation.setVisibility(View.GONE);
        setVideoView(true);
        mBinding.video.requestLayout();
    }

    private void scheduleLivePiPEntry() {
        pipEntryListener = OneShotPreDrawListener.add(mBinding.video, this::enterPreparedLivePiP);
    }

    private void enterPreparedLivePiP() {
        pipEntryListener = null;
        if (!pipEntryPending) return;
        pipEntryPending = false;
        if (isInPictureInPictureMode()) return;
        if (isFinishing() || isDestroyed() || service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) {
            restoreAfterFailedLivePiP();
            return;
        }
        mPiP.update(this, mBinding.video);
        boolean entered = mPiP.enter(this, LIVE_PIP_WIDTH, LIVE_PIP_HEIGHT, LiveSetting.getScale(), true);
        if (!entered && !isInPictureInPictureMode()) restoreAfterFailedLivePiP();
    }

    private void restoreAfterFailedLivePiP() {
        if (mBinding == null || isFinishing() || isDestroyed()) return;
        setVideoView(false);
        updateEmbeddedUiMode();
        showControl();
    }

    private void cancelPendingLivePiP(boolean restoreUi) {
        if (pipEntryListener != null) {
            pipEntryListener.removeListener();
            pipEntryListener = null;
        }
        if (!pipEntryPending) return;
        pipEntryPending = false;
        if (restoreUi && !isInPictureInPictureMode()) restoreAfterFailedLivePiP();
    }

    private void enterFullscreenLive() {
        setRotate(true);
        hideInfo();
        hideControl();
        hideUI();
        updateEmbeddedUiMode();
        Util.hideSystemUI(this);
        requestOrientation("enter-fullscreen", getFullscreenOrient());
    }

    private void enterFullscreenLiveOnPad() {
        if (!isPadLiveFullscreenMode() || isRotate() || isInPictureInPictureMode()) return;
        requestOrientation("enter-fullscreen-pad", getFullscreenOrient());
        if (!ResUtil.isLand(this)) return;
        enterFullscreenLive();
    }

    private void exitFullscreenLive() {
        livePortraitFullscreen = false;
        setRotate(false);
        hideInfo();
        hideControl();
        requestOrientation("exit-fullscreen", getEmbeddedOrient());
    }

    private int getLaunchOrient() {
        if (isPadLiveFullscreenMode()) return ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
        return PlaybackOrientation.getPortraitVideoSizeOrientation();
    }

    private int getFullscreenOrient() {
        if (ResUtil.isPad()) return livePortraitFullscreen ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
        return PlaybackOrientation.getEnterFullscreenOrientation(player().isPortrait());
    }

    private int getEmbeddedOrient() {
        if (isPadLiveFullscreenMode()) return ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
        return PlaybackOrientation.getPortraitVideoSizeOrientation();
    }

    private void requestOrientation(String reason, int orientation) {
        Log.i(ORIENTATION_TAG, "request reason=" + reason + " from=" + getRequestedOrientation() + " to=" + orientation + " " + orientationState());
        setRequestedOrientation(orientation);
    }

    private String orientationState() {
        return "config=" + getResources().getConfiguration().orientation
                + " displayRotation=" + ResUtil.getDisplay(this).getRotation()
                + " rotate=" + isRotate()
                + " locked=" + isLock();
    }

    private boolean isPadLiveFullscreenMode() {
        return ResUtil.isPad() && PlayerSetting.isPadLiveFullscreen();
    }

    private void applyPadLiveMode() {
        if (!ResUtil.isPad() || isInPictureInPictureMode()) return;
        if (PlayerSetting.isPadLiveFullscreen()) {
            enterFullscreenLiveOnPad();
        } else if (!isRotate()) {
            requestOrientation("apply-pad-embedded", getEmbeddedOrient());
        }
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
        refreshInjectedLives();
        if (LiveConfig.isOnly()) setLive(getHome());
        else LiveDialog.show(this);
        hideControl();
    }

    private void onLiveSource() {
        refreshInjectedLives();
        LiveDialog.show(this);
        hideControl();
        hideInfo();
    }

    private void onFullscreenLiveSource() {
        refreshInjectedLives();
        LiveDialog.create().drawer().show(getSupportFragmentManager(), null);
        hideControl();
        hideInfo();
    }

    private void refreshInjectedLives() {
        if (!CustomCspSetting.hasLives()) return;
        Live home = getHome();
        CustomCspSetting.inject(LiveConfig.get().getLives(), "");
        LiveConfig.get().getLives().removeIf(Live::isEmpty);
        LiveConfig.get().getLives().forEach(item -> item.setSelected(home));
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else showResizeModeDialog(LiveSetting.getScale(), this::setScale);
        setR1Callback();
    }

    private void onSpeed() {
        if (!player().isVod()) return;
        PlaybackSpeedDialog.show(this, player().getSpeed(), speed -> {
            if (!isServiceReady() || !isOwner() || !player().isVod()) return;
            mBinding.control.action.speed.setText(player().setSpeed(speed));
            PlayerSetting.putDefaultSpeed(player().getSpeed());
            setR1Callback();
        });
    }

    private boolean onSpeedLong() {
        if (!player().isVod()) return true;
        mBinding.control.action.speed.setText(player().toggleSpeed());
        PlayerSetting.putDefaultSpeed(player().getSpeed());
        setR1Callback();
        return true;
    }

    private void onConfig() {
        HistoryDialog.create().live().readOnly().show(this);
        hideControl();
    }

    private void onLiveSetting() {
        LiveControlDialog.create().parent(mBinding).show(this);
        hideInfo();
    }

    private boolean onLiveSettingTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            view.animate().scaleX(1.08f).scaleY(1.08f).alpha(0.82f).setDuration(90).start();
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start();
        }
        return false;
    }

    private void onInvert() {
        setR1Callback();
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
    }

    private void onAcross() {
        setR1Callback();
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
    }

    private void onChange() {
        setR1Callback();
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void onDecode() {
        player().toggleDecode();
        setR1Callback();
        setDecode();
    }

    private void onPlayerKernel() {
        PlayerKernelDialog.show(this, player().getPlayerType(), this::switchPlayerKernel, this::onExternalPlayer);
    }

    private void onExternalPlayer() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.control.title.getText());
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

    private boolean onTextLong() {
        if (!player().haveTrack(C.TRACK_TYPE_TEXT)) return false;
        onSubtitleClick();
        return true;
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) setR1Callback();
        return false;
    }

    private void hideUI() {
        hideUI(true);
    }

    private void hideUI(boolean syncPosition) {
        if (isEmbeddedLiveUi()) {
            keepLiveMenuVisible();
            if (syncPosition) setPosition();
            return;
        }
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isEmbeddedLiveUi()) {
            keepLiveMenuVisible();
            setPosition();
            return;
        }
        setLiveMenuOverlay(true);
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
        updateOverlayMenuWidths();
        setPosition();
        hideEpg();
    }

    private void showEpg(Channel item) {
        if (isEmbeddedLiveUi()) return;
        if (mChannel == null || mChannel.getData(mViewModel.getZoneId()).getList().isEmpty() || mEpgDataAdapter.getItemCount() == 0 || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        scrollToPosition(mBinding.epgData, item.getData(mViewModel.getZoneId()).getSelected());
        mBinding.epgData.setVisibility(View.VISIBLE);
        mBinding.channel.setVisibility(View.GONE);
        mBinding.group.setVisibility(View.GONE);
    }

    private void hideEpg() {
        mBinding.channel.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.epgData.setVisibility(View.GONE);
    }

    private boolean isEmbeddedLiveUi() {
        return !ResUtil.isLand(this) && !isRotate() && !isInPictureInPictureMode();
    }

    private void keepLiveMenuVisible() {
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.channel.setVisibility(View.VISIBLE);
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset(mBinding.progress.traffic);
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.error.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.error.setText("");
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        // 与点播一致：每次唤出控制栏都重算轨道类按钮。applyVisibility 只隐藏不恢复，
        // 若直播存活期间（如 PiP）在设置里重新开启字幕/音轨/视轨，只有这里能把它们恢复。
        setTrackVisible();
        setPlayParamsState();
        boolean embedded = isEmbeddedLiveUi();
        if (!embedded && isVisible(mBinding.recycler)) hideUI(false);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(View.GONE);
        mBinding.control.right.rotate.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.action.getRoot().setVisibility(embedded ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        if (mOsd != null) mOsd.setControlsVisible(true);
        setR1Callback();
        hideInfo();
        hideWidgetOverlay();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
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
        boolean visible = mOsd != null && mOsd.isDiagnosticsVisible();
        mBinding.control.action.playParams.setSelected(visible);
        mBinding.control.osdDiagnostics.setSelected(visible);
        mBinding.control.osdDiagnostics.setAlpha(visible ? 1f : 0.6f);
        // 「播放参数」被播放器按钮设置关闭后，顶栏快捷图标一并隐藏；未关闭时保留一键开关。
        boolean entry = PlayerButtonSetting.isVisible(PlayerButtonSetting.PLAY_PARAMS) && service() != null && !player().isEmpty();
        mBinding.control.osdDiagnostics.setVisibility(entry ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private void showInfo() {
        mBinding.widget.infoPip.setVisibility(isInPictureInPictureMode() ? View.VISIBLE : View.GONE);
        mBinding.widget.info.setVisibility(isInPictureInPictureMode() ? View.GONE : View.VISIBLE);
        setR3Callback();
        hideControl();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.infoPip.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void hideWidgetOverlay() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
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
        if (isEmbeddedLiveUi()) {
            if (isVisible(mBinding.control.getRoot())) hideControl();
            else showControl();
        } else if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork() {
        clearArtworkTarget();
        if (isFinishing() || isDestroyed()) return;
        int size = ResUtil.dp2px(128);
        ImgUtil.load(getApplicationContext(), mChannel.getLogo(), size, size, mArtworkTarget = new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (isFinishing() || isDestroyed()) return;
                mBinding.exo.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (isFinishing() || isDestroyed()) return;
                mBinding.exo.setDefaultArtwork(errorDrawable);
            }
        });
    }

    @Override
    public void onItemClick(Group item) {
        mGroupAdapter.setSelected(mGroup = item);
        mChannelAdapter.addAll(item.getChannel(), getCurrentChannelPosition(item));
        scrollToChannelPosition(Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        if (Biometric.enable()) Biometric.show(this);
        else PassDialog.create().show(this);
        resetPass();
    }

    private int getCurrentChannelPosition(Group group) {
        if (mChannel == null || mChannel.getGroup() == null || !group.equals(mChannel.getGroup())) return -1;
        for (int i = 0; i < group.getChannel().size(); i++) if (group.getChannel().get(i) == mChannel) return i;
        return group.getChannel().indexOf(mChannel);
    }

    @Override
    public void onItemClick(Channel item) {
        selectChannel(item, false);
    }

    private void selectChannel(Channel item, boolean syncPosition) {
        if (item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup) && isLineDoubleClick(item)) {
            showLineDialog(item);
        } else if (!item.getData(mViewModel.getZoneId()).getList().isEmpty() && item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            if (!isEmbeddedLiveUi()) {
                hideUI();
                hideControl();
                hideInfo();
                return;
            }
            showEpg(item);
        } else if (mGroup != null) {
            mGroup.setPosition(mChannelAdapter.setSelected(item.group(mGroup)));
            mChannel = item;
            resetLineFallback();
            setArtwork();
            showInfo();
            hideUI(syncPosition);
            fetch();
            rememberLineClick(item);
        }
    }

    private boolean isLineDoubleClick(Channel item) {
        long now = System.currentTimeMillis();
        boolean result = lastLineClickChannel != null && lastLineClickChannel.equals(item) && now - lastLineClickTime <= ViewConfiguration.getDoubleTapTimeout();
        rememberLineClick(item, now);
        return result && !item.isOnly();
    }

    private void rememberLineClick(Channel item) {
        rememberLineClick(item, System.currentTimeMillis());
    }

    private void rememberLineClick(Channel item, long time) {
        lastLineClickChannel = item;
        lastLineClickTime = time;
    }

    private void showLineDialog(Channel item) {
        hideControl();
        hideInfo();
        LiveLineDialog.create().channel(item).listener(this::setLine).show(this);
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
            mBinding.control.title.setText(getString(R.string.detail_title, mChannel.getShow(), item.getTitle()));
            Notify.show(getString(R.string.play_ready, item.getTitle()));
            mEpgDataAdapter.setSelected(item);
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
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mBinding.widget.play.setText("");
        mBinding.widget.name.setMaxEms(48);
        mChannel.loadLogo(mBinding.widget.logo);
        setLiveHeader();
        mBinding.control.title.setSelected(true);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.control.title.setText(mChannel.getShow());
        setSizeText();
        mBinding.widget.namePip.setText(mChannel.getShow());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.widget.numberPip.setText(mChannel.getNumber());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setText(mBinding.widget.line.getText());
        mBinding.control.action.line.setVisibility(mBinding.widget.line.getVisibility());
    }

    private void setLiveHeader() {
        if (mBinding.liveTitle == null || mChannel == null) return;
        mBinding.liveTitle.setText(mChannel.getShow());
        mBinding.liveTitle.setSelected(true);
        mBinding.liveLogoFallback.setVisibility(mChannel.getLogo().isEmpty() ? View.VISIBLE : View.GONE);
        mChannel.loadLogo(mBinding.liveLogo);
        setLiveProgram(null);
    }

    private void setLiveProgram(Epg epg) {
        if (mBinding.liveProgram == null) return;
        String empty = getString(R.string.live_no_epg);
        if (epg == null || epg.getList().isEmpty()) {
            mBinding.liveProgram.setText(empty);
            mBinding.liveProgramNext.setText(empty);
            mBinding.liveProgramNext.setVisibility(View.VISIBLE);
            return;
        }
        int selected = epg.getSelected();
        EpgData current = selected >= 0 && selected < epg.getList().size() ? epg.getList().get(selected) : epg.getEpgData();
        EpgData next = selected + 1 < epg.getList().size() ? epg.getList().get(selected + 1) : null;
        mBinding.liveProgram.setText(getProgramText(current, empty));
        mBinding.liveProgramNext.setText(getProgramText(next, empty));
        mBinding.liveProgramNext.setVisibility(View.VISIBLE);
    }

    private void onLiveProgram() {
        if (mChannel == null) return;
        if (!mChannel.getDataList().isEmpty()) {
            showLiveProgram();
            return;
        }
        pendingShowProgram = true;
        mViewModel.getEpg(mChannel);
        Notify.show(R.string.live_program_empty);
    }

    private void showLiveProgram() {
        if (mChannel == null || mChannel.getDataList().isEmpty()) {
            Notify.show(R.string.live_program_empty);
            return;
        }
        LiveProgramDialog.create().channel(mChannel).zoneId(mViewModel.getZoneId()).listener(this::onItemClick).show(this);
        hideControl();
        hideInfo();
    }

    private String getProgramText(EpgData data, String empty) {
        return data == null || data.format().isEmpty() ? empty : data.format();
    }

    private void setEpg(Epg epg) {
        if (mChannel == null) return;
        if (!mChannel.getTvgId().equals(epg.getKey())) {
            pendingShowEpg = false;
            pendingShowProgram = false;
            return;
        }
        EpgData data = epg.getEpgData();
        boolean hasTitle = !data.getTitle().isEmpty();
        mEpgDataAdapter.addAll(epg.getList());
        if (hasTitle) mBinding.control.title.setText(getString(R.string.detail_title, mChannel.getShow(), data.getTitle()));
        mBinding.widget.name.setMaxEms(hasTitle ? 12 : 48);
        mBinding.widget.play.setText(data.format());
        setLiveProgram(epg);
        setWidth(epg);
        setMetadata();
        if (pendingShowEpg) {
            pendingShowEpg = false;
            showEpg(mChannel);
        }
        if (pendingShowProgram) {
            pendingShowProgram = false;
            showLiveProgram();
        }
    }

    private void setEpg(boolean success) {
        if (mChannel != null && success)
            mViewModel.getEpg(mChannel);
    }

    private void fetch(EpgData item) {
        App.removeCallbacks(mEndRetry);
        if (mChannel == null) return;
        App.removeCallbacks(mBufferingTimeout);
        App.post(mBufferingTimeout, LIVE_BUFFERING_TIMEOUT);
        playbackCatchup = true;
        mViewModel.getUrl(mChannel, item);
        if (service() != null) {
            player().clear();
            player().stop();
        }
        hideUI();
    }

    private void fetch() {
        App.removeCallbacks(mEndRetry);
        if (mChannel == null) return;
        App.removeCallbacks(mBufferingTimeout);
        App.post(mBufferingTimeout, LIVE_BUFFERING_TIMEOUT);
        playbackCatchup = false;
        LiveConfig.get().setKeep(mChannel);
        mViewModel.getUrl(mChannel);
        if (service() != null) {
            player().clear();
            player().stop();
        }
        showProgress();
    }

    private void start(Result result) {
        if (service() == null) {
            mPendingStartResult = result;
            return;
        }
        String realUrl = result.getRealUrl();
        if (isSameReloadUrl(realUrl)) {
            String msg = mPendingReloadMsg;
            clearPendingReload();
            handleSameReloadUrl(msg);
            return;
        }
        clearPendingReload();
        mPlaybackKey = realUrl;
        updateNavigationKey();
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

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void setSizeText() {
        String text = service() == null ? "" : player().getSizeText();
        mBinding.control.size.setText(text);
        mBinding.control.size.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void resetAdapter() {
        liveMenuRendered = false;
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.control.title.setText("");
        mBinding.control.size.setText("");
        mBinding.control.size.setVisibility(View.GONE);
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
    }

    @Override
    public void onLiveConfigPanel() {
        onConfig();
    }

    @Override
    public void onLiveSourcePanel() {
        onHome();
    }

    @Override
    public void onLiveEpgPanel() {
        LiveEpgDialog.create().show(this);
        hideControl();
        hideInfo();
    }

    @Override
    public void onLiveEpgSelected(String url) {
        if (mChannel == null) return;
        LiveEpgSetting.apply(getHome());
        pendingShowEpg = true;
        if (LiveEpgSetting.isGlobalXmlUrl(LiveEpgSetting.getUrl()) || (LiveEpgSetting.getUrl().isEmpty() && !getHome().getEpgXml().isEmpty())) {
            mViewModel.parseXml(getHome());
        } else {
            mViewModel.getEpg(mChannel);
        }
        hideControl();
        hideInfo();
    }

    @Override
    public void onLiveCastPanel() {
        onCast();
        hideControl();
    }

    @Override
    public void onLivePiPPanel() {
        dismissLiveControlDialog();
        App.post(this::onPiP, 100);
    }

    @Override
    public void onLiveBackgroundPanel() {
        dismissLiveControlDialog();
        switchToAudioBackground();
    }

    @Override
    public void onLiveListStylePanel(boolean classic) {
        LiveSetting.putListStyleClassic(classic);
        applyLiveListStyle();
    }

    @Override
    public void onLiveDisplayChanged() {
        if (mOsd == null) return;
        mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
        mOsd.start();
    }

    @Override
    public void onLiveScalePanel(int scale) {
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        setScale(scale);
        setR1Callback();
    }

    @Override
    public void onLiveTrackPanel(int type) {
        TrackDialog.create().type(type).player(player()).show(this);
        hideControl();
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

        @Override
        public void onAudio() {
            switchToAudioBackground();
        }
    };

    @Override
    protected void onPrepare() {
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
                checkControl();
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
    protected void onSizeChanged(VideoSize size) {
        mPiP.update(this, LIVE_PIP_WIDTH, LIVE_PIP_HEIGHT, LiveSetting.getScale());
        videoSize = size;
        updateVideoHeight(size);
        applyLiveResizeMode(LiveSetting.getScale());
        if (!isEmbeddedLiveUi() && !isLock() && !ResUtil.isPad()) requestOrientation("video-size-fullscreen", getFullscreenOrient());
        setSizeText();
    }

    @Override
    protected void onSurfaceAttached() {
        applyLiveResizeMode(LiveSetting.getScale());
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) App.removeCallbacks(mBufferingTimeout);
        if (isPlaying || isPaused()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mPiP.update(this, isPlaying);
        mBinding.control.play.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).player(player()).show(this);
        hideControl();
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

    @Override
    public void onBiometricSuccess() {
        unlock(null);
    }

    private void unlock(String pass) {
        boolean first = true;
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(item);
            if (first) onItemClick(item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case LIVE -> setLive(getHome());
            case PLAYER -> fetch();
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
        String title = mChannel == null ? "" : mChannel.getShow();
        String logo = mChannel == null ? "" : mChannel.getLogo();
        return PlayerManager.buildMetadata(title, artist, logo);
    }

    private void setMetadata() {
        if (service() == null) return;
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

    private boolean prevGroup() {
        int position = mGroupAdapter.getPosition() - 1;
        if (position < 0) position = mGroupAdapter.getItemCount() - 1;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = mGroupAdapter.get(position);
        mGroupAdapter.setSelected(position);
        if (mGroup.skip()) return prevGroup();
        mGroup.setPosition(mGroup.getChannel().size() - 1);
        mChannelAdapter.addAll(mGroup.getChannel(), mGroup.getPosition());
        return true;
    }

    private boolean nextGroup() {
        int position = mGroupAdapter.getPosition() + 1;
        if (position > mGroupAdapter.getItemCount() - 1) position = 0;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = mGroupAdapter.get(position);
        mGroupAdapter.setSelected(position);
        if (mGroup.skip()) return nextGroup();
        mGroup.setPosition(0);
        mChannelAdapter.addAll(mGroup.getChannel(), mGroup.getPosition());
        return true;
    }

    private void prevChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() - 1;
        boolean limit = position < 0;
        if (LiveSetting.isAcross() & limit) prevGroup();
        else mGroup.setPosition(limit ? mGroup.getChannel().size() - 1 : position);
        if (!mGroup.isEmpty()) selectChannel(mGroup.current(), true);
    }

    private void nextChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() + 1;
        boolean limit = position > mGroup.getChannel().size() - 1;
        if (LiveSetting.isAcross() && limit) nextGroup();
        else mGroup.setPosition(limit ? 0 : position);
        if (!mGroup.isEmpty()) selectChannel(mGroup.current(), true);
    }

    private void checkNext() {
        if (mChannel == null) return;
        int current = mChannel.getData(mViewModel.getZoneId()).getInRange();
        int position = mChannel.getData(mViewModel.getZoneId()).getSelected() + 1;
        boolean hasNext = position <= current && position > 0;
        if (hasNext) onItemClick(mChannel.getData(mViewModel.getZoneId()).getList().get(position));
        else fetch();
    }

    private void nextLine(boolean show) {
        if (mChannel == null || mChannel.isOnly()) return;
        resetLineFallback();
        mChannel.switchLine(true);
        if (show) showInfo();
        else setInfo();
        fetch();
    }

    private void setLine(int position) {
        if (mChannel == null || position < 0 || position >= mChannel.getUrls().size()) return;
        if (mChannel.getIndex() == position) return;
        resetLineFallback();
        mChannel.setIndex(position);
        setInfo();
        fetch();
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        Log.i(ORIENTATION_TAG, "rotate state=" + rotate + " " + orientationState());
        updateSystemUI();
        if (rotate) {
            noPadding(mBinding.recycler);
            noPadding(mBinding.control.getRoot());
        } else {
            updateLiveMenuInsets();
            updateControlInsets();
        }
        updateVideoHeight(videoSize);
        applyLiveResizeMode(LiveSetting.getScale());
    }

    private void scrollToPosition(RecyclerView view, int position) {
        RecyclerView.Adapter<?> adapter = view.getAdapter();
        if (adapter == null || position < 0 || position >= adapter.getItemCount()) return;
        view.post(() -> view.scrollToPosition(position));
    }

    private void scrollToChannelPosition(int position) {
        int adapterPosition = mChannelAdapter.ensurePosition(position);
        if (adapterPosition != -1) scrollToPosition(mBinding.channel, adapterPosition);
    }

    private void clearArtworkTarget() {
        if (mArtworkTarget == null) return;
        Glide.with(getApplicationContext()).clear(mArtworkTarget);
        mArtworkTarget = null;
    }

    @Override
    public void onCasted() {
        player().stop();
    }

    @Override
    public void onSpeedUp() {
        if (player().isLive()) return;
        if (!player().isPlaying()) return;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getSpeed()));
    }

    @Override
    public void onSpeedEnd() {
        if (player().isLive()) return;
        mBinding.widget.speed.clearAnimation();
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getDefaultSpeed()));
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onFlingDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onSeeking(long time) {
        if (player().isLive()) return;
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (player().isLive()) return;
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isLock()) {
            Log.i(ORIENTATION_TAG, "single tap blocked while locked " + orientationState());
            showControl();
            return;
        }
        onToggle();
    }

    @Override
    public void onSingleTap(float x, float width) {
        if (isLock()) {
            Log.i(ORIENTATION_TAG, "split tap blocked while locked x=" + x + " width=" + width + " " + orientationState());
            hideInfo();
            hideUI(false);
            showControl();
            return;
        }
        if (width <= 0 || isEmbeddedLiveUi()) {
            onToggle();
            return;
        }
        hideInfo();
        if (x < width / 2f) {
            if (isVisible(mBinding.recycler)) hideUI();
            else {
                hideControl();
                showUI();
            }
        } else {
            if (isVisible(mBinding.control.getRoot())) hideControl();
            else {
                hideUI(false);
                showControl();
            }
        }
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) {
            Log.i(ORIENTATION_TAG, "double tap blocked while locked " + orientationState());
            showControl();
            return;
        }
        if (isVisible(mBinding.recycler)) hideUI();
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    @Override
    public void onShare(CharSequence title) {
        PlayerHelper.share(this, player().getUrl(), player().getHeaders(), title);
        setRedirect(true);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            preparePiP("userLeaveHint");
        } else {
            requestPiP("userLeaveHint");
        }
    }

    @Override
    public boolean onPictureInPictureRequested() {
        return requestPiP("systemRequest");
    }

    private boolean preparePiP(String reason) {
        if (isRedirect() || isPlaybackExiting()) return false;
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        if (syncPiPForPlaybackMode()) return false;
        mPiP.update(this, LIVE_PIP_WIDTH, LIVE_PIP_HEIGHT, LiveSetting.getScale());
        return true;
    }

    private boolean requestPiP(String reason) {
        if (!preparePiP(reason)) return false;
        if (isLock()) App.post(this::onLock, 500);
        return enterPiP(reason);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            mKeepPlaybackAfterPipExit = false;
            cancelPendingLivePiP(false);
        }
        setVideoView(isInPictureInPictureMode);
        // PiP 窗口里也要按当前模式重算状态栏占位，否则嵌入式高度会把 video 往下挤。
        applyStatusBarInset();
        if (isInPictureInPictureMode) {
            dismissLiveControlDialog();
            hideControl();
            hideInfo();
            hideUI();
        } else {
            hideInfo();
            // PiP 窗口点 × 关闭时，主动停止播放，避免声音继续。
            // 不能依赖 isStop() 时序，改为等生命周期 settle 后按最终状态判定。
            App.post(this::finishIfPipClosed, 0);
        }
    }

    private void switchToAudioBackground() {
        boolean audioOnly = isAudioOnly();
        mKeepPlaybackAfterPipExit = isInPictureInPictureMode();
        setAudioOnly(true);
        syncPiPForPlaybackMode();
        if (!moveTaskToBack(true)) {
            mKeepPlaybackAfterPipExit = false;
            setAudioOnly(audioOnly);
            syncPiPForPlaybackMode();
        }
    }

    private boolean syncPiPForPlaybackMode() {
        boolean audioMode = isAudioOnly();
        if (mPiP != null) mPiP.setAudioMode(this, audioMode);
        return audioMode;
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            // PiP 模式：显式让 video 填满整个 PiP 窗口，避免残留 embedded 布局参数
            // （原布局 height=0dp+weight=9）导致 SurfaceView 渲染区域与 PiP 窗口尺寸不匹配，
            // 表现为画面显示异常，退出后系统合成层残留最后一帧（水印残留）。
            ViewGroup.LayoutParams params = mBinding.video.getLayoutParams();
            if (params instanceof LinearLayoutCompat.LayoutParams layout) {
                if (params.height == ViewGroup.LayoutParams.MATCH_PARENT
                        && params.width == ViewGroup.LayoutParams.MATCH_PARENT
                        && layout.weight == 0) return;
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                layout.weight = 0;
                mBinding.video.setLayoutParams(params);
            }
        } else {
            // 退出 PiP：按当前 UI 模式恢复合适的布局（嵌入式小窗 vs 全屏）
            updateVideoHeight(videoSize);
        }
    }

    private boolean enterPiP(String reason) {
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        if (syncPiPForPlaybackMode()) return false;
        dismissLiveControlDialog();
        return mPiP.enter(this, LIVE_PIP_WIDTH, LIVE_PIP_HEIGHT, LiveSetting.getScale());
    }

    private void finishIfPipClosed() {
        boolean atLeastStarted = getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        boolean keepPlayback = mKeepPlaybackAfterPipExit;
        mKeepPlaybackAfterPipExit = false;
        if (PipExitDecision.shouldFinishAfterPipExit(atLeastStarted, isFinishing(), isDestroyed(), keepPlayback)) finishLivePlayback();
    }

    private void dismissLiveControlDialog() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof LiveControlDialog dialog) dialog.dismissAllowingStateLoss();
        }
        getSupportFragmentManager().executePendingTransactions();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(ORIENTATION_TAG, "configuration changed new=" + newConfig.orientation + " " + orientationState());
        updateSystemUI();
        applyPadLiveMode();
        mBinding.exo.post(() -> applyLiveResizeMode(LiveSetting.getScale()));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updateSystemUI();
    }

    private void updateSystemUI() {
        updateEmbeddedUiMode();
        updateLiveMenuInsets();
        if (isEmbeddedLiveUi()) {
            Util.showSystemUI(this);
            ViewCompat.requestApplyInsets(mBinding.getRoot());
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            insets.setAppearanceLightStatusBars(false);
            insets.setAppearanceLightNavigationBars(false);
        } else {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            Util.hideSystemUI(this);
        }
    }

    private void updateEmbeddedUiMode() {
        boolean embedded = isEmbeddedLiveUi();
        setLiveMenuOverlay(!embedded);
        mBinding.navigation.setVisibility(View.GONE);
        if (embeddedUiMode != null && embeddedUiMode && !embedded) {
            hideControl();
            hideUI();
        } else if (embedded) {
            keepLiveMenuVisible();
        }
        embeddedUiMode = embedded;
        applyStatusBarInset();
        updateControlInsets();
        updateVideoHeight(videoSize);
        applyLiveResizeMode(LiveSetting.getScale());
    }

    private void setLiveMenuOverlay(boolean overlay) {
        if (liveMenuOverlay == overlay) return;
        liveMenuOverlay = overlay;
        if (overlay) {
            moveLiveMenuToVideo();
            mBinding.recycler.setOrientation(LinearLayoutCompat.HORIZONTAL);
            if (mBinding.liveCurrent != null) mBinding.liveCurrent.setVisibility(View.GONE);
            setLiveListContainerOverlay();
            updateOverlayMenuWidths();
        } else {
            moveLiveMenuToRoot();
            mBinding.recycler.setOrientation(LinearLayoutCompat.VERTICAL);
            if (mBinding.liveCurrent != null) mBinding.liveCurrent.setVisibility(View.VISIBLE);
            setLiveListContainerEmbedded();
            restoreEmbeddedMenuWidths();
        }
        applyLiveListStyle();
    }

    private void applyLiveListStyle() {
        if (mBinding == null) return;
        if (liveMenuOverlay) {
            mBinding.recycler.setBackground(LiveListScrim.create(this));
        } else {
            mBinding.recycler.setBackgroundResource(LiveSetting.isListStyleClassic() ? R.color.transparent : R.drawable.shape_live_embedded_list);
        }
        if (mBinding.liveCurrent != null) {
            mBinding.liveCurrent.setBackgroundResource(LiveSetting.isListStyleClassic() ? R.drawable.shape_live_classic : R.drawable.shape_live);
        }
        if (mGroupAdapter != null) mGroupAdapter.notifyDataSetChanged();
        if (mChannelAdapter != null) mChannelAdapter.notifyDataSetChanged();
        if (mEpgDataAdapter != null) mEpgDataAdapter.notifyDataSetChanged();
    }

    private void moveLiveMenuToVideo() {
        if (mBinding.recycler.getParent() == mBinding.video) return;
        ViewGroup parent = (ViewGroup) mBinding.recycler.getParent();
        if (parent != null) parent.removeView(mBinding.recycler);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        mBinding.video.addView(mBinding.recycler, params);
    }

    private void moveLiveMenuToRoot() {
        if (mBinding.recycler.getParent() == mBinding.getRoot()) return;
        ViewGroup parent = (ViewGroup) mBinding.recycler.getParent();
        if (parent != null) parent.removeView(mBinding.recycler);
        if (mBinding.getRoot() instanceof LinearLayoutCompat root) {
            LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
            params.weight = 14;
            root.addView(mBinding.recycler, Math.min(root.indexOfChild(mBinding.video) + 1, root.getChildCount()), params);
        } else if (mBinding.getRoot() instanceof FrameLayout root) {
            root.addView(mBinding.recycler, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        }
    }

    private void setLiveListContainerOverlay() {
        if (mBinding.recycler.getChildCount() < 2) return;
        View view = mBinding.recycler.getChildAt(1);
        if (!(view instanceof LinearLayoutCompat layout)) return;
        layout.setOrientation(LinearLayoutCompat.HORIZONTAL);
        layout.setPadding(ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
    }

    private void setLiveListContainerEmbedded() {
        if (mBinding.recycler.getChildCount() < 2) return;
        View view = mBinding.recycler.getChildAt(1);
        if (!(view instanceof LinearLayoutCompat layout)) return;
        layout.setOrientation(LinearLayoutCompat.HORIZONTAL);
        layout.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(12), ResUtil.dp2px(10));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        params.weight = 1;
        layout.setLayoutParams(params);
    }

    private void updateOverlayMenuWidths() {
        if (!liveMenuOverlay) return;
        if (getHome() != null) setWidth(getHome());
        if (mGroup != null) setWidth(mGroup);
        if (mChannel != null) setWidth(mChannel.getData(mViewModel.getZoneId()));
    }

    private void restoreEmbeddedMenuWidths() {
        setWeightedLayout(mBinding.group, 31);
        setWeightedLayout(mBinding.channel, 57);
        setWeightedLayout(mBinding.epgData, 0);
    }

    private void setWeightedLayout(View view, float weight) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = weight;
        view.setLayoutParams(params);
    }

    private void updateStatusBarInset(@Nullable WindowInsetsCompat insets) {
        int top = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        // 全屏期间状态栏被隐藏，派发的 inset 是 0；退出全屏后仍要用最后一次可见的高度。
        if (top > 0) statusBarInset = top;
        applyStatusBarInset();
    }

    private void applyStatusBarInset() {
        if (mBinding.statusBar == null) return;
        ViewGroup.LayoutParams params = mBinding.statusBar.getLayoutParams();
        int height = isEmbeddedLiveUi() ? statusBarInset : 0;
        if (params.height == height) return;
        params.height = height;
        mBinding.statusBar.setLayoutParams(params);
    }

    private void updateLiveMenuInsets() {
        if (isEmbeddedLiveUi()) noPadding(mBinding.recycler);
        else setPadding(mBinding.recycler, true);
        updateLiveListBottomInset(ViewCompat.getRootWindowInsets(mBinding.getRoot()));
    }

    private void updateControlInsets() {
        if (isEmbeddedLiveUi()) noPadding(mBinding.control.getRoot());
        else setPadding(mBinding.control.getRoot());
    }

    private void updateLiveListBottomInset(@Nullable WindowInsetsCompat insets) {
        int bottom = isEmbeddedLiveUi() && insets != null ? insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom : 0;
        int extra = isEmbeddedLiveUi() ? bottom + ResUtil.dp2px(6) : 0;
        setRecyclerBottomPadding(mBinding.group, groupBasePaddingBottom + extra);
        setRecyclerBottomPadding(mBinding.channel, channelBasePaddingBottom + extra);
        setRecyclerBottomPadding(mBinding.epgData, epgBasePaddingBottom + extra);
    }

    private void setRecyclerBottomPadding(RecyclerView view, int bottom) {
        if (view.getPaddingBottom() == bottom) return;
        view.setPaddingRelative(view.getPaddingStart(), view.getPaddingTop(), view.getPaddingEnd(), bottom);
    }

    private void updateVideoHeight(VideoSize size) {
        ViewGroup.LayoutParams params = mBinding.video.getLayoutParams();
        if (!(params instanceof LinearLayoutCompat.LayoutParams layout)) {
            if (!isEmbeddedLiveUi()) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                mBinding.video.setLayoutParams(params);
            }
            return;
        }
        if (!isEmbeddedLiveUi()) {
            if (params.height == ViewGroup.LayoutParams.MATCH_PARENT && layout.weight == 0) return;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layout.weight = 0;
            mBinding.video.setLayoutParams(params);
            return;
        }
        int height = getEmbeddedVideoHeight(size);
        if (params.height == height && layout.weight == 0) return;
        params.height = height;
        layout.weight = 0;
        mBinding.video.setLayoutParams(params);
    }

    private int getEmbeddedVideoHeight(VideoSize size) {
        int width = ResUtil.getScreenWidth();
        int screen = ResUtil.getScreenHeight(this);
        int height = Math.round(width * 9f / 16f);
        int max = Math.round(screen * 0.40f);
        return Math.max(ResUtil.dp2px(180), Math.min(height, max));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mOsd != null) {
            mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
            mOsd.start();
        }
        setPlayParamsState();
        setAudioOnly(false);
        mPiP.resetAudioMode();
        setStop(false);
    }

    @Override
    protected void onStop() {
        cancelPendingLivePiP(true);
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (isLock()) {
            return;
        } else {
            onBack();
        }
    }

    @Override
    protected void onDestroy() {
        cancelPendingLivePiP(false);
        clearArtworkTarget();
        Source.get().exit();
        App.removeCallbacks(mR1, mR2, mR3);
        App.removeCallbacks(mEndRetry);
        App.removeCallbacks(mBufferingTimeout);
        if (mOsd != null) mOsd.release();
        mViewModel.url().removeObserver(mObserveUrl);
        mViewModel.epg().removeObserver(mObserveEpg);
        if (mKeyDown != null) mKeyDown.release();
        super.onDestroy();
    }
}
