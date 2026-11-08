package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.DolbyVisionFormatLabel;
import com.fongmi.android.tv.player.AudioPlaybackDiagnostics;
import com.fongmi.android.tv.player.GpuLoadMonitor;
import com.fongmi.android.tv.player.MediaBadgeCatalog;
import com.fongmi.android.tv.player.MediaBadgeResolver;
import com.fongmi.android.tv.player.ijk.IjkDecodePressurePolicy;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.PlaybackPanelResourceMonitor;
import com.fongmi.android.tv.player.PlaybackDiagnosticsSourcePolicy;
import com.fongmi.android.tv.player.PlaybackSpeedMeter;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.PlaybackAnalyticsListener;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Util;
import com.google.android.flexbox.FlexboxLayout;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerOsdController {

    public interface Source {
        PlayerManager getPlayer();

        String getTitle();

        /**
         * 是否处于全屏播放。媒体信息徽标只属于全屏画面：半屏/小窗（详情页内嵌、竖屏小屏）里
         * 它会和一堆控件挤在一起，因此非全屏时无论屏显开关如何都不显示。
         */
        default boolean isFullscreen() {
            return true;
        }
    }

    private static volatile String cachedDeviceText;
    private static volatile String cachedSystemText;
    private static volatile String cachedWebViewText;
    private static volatile String cachedHevcDecoderText;
    /**
     * The verdict threshold for "掉帧较多". It used to be a bare raw count of 60, but media3
     * settles its dropped-frame accumulator whenever the renderer stops, so the count also
     * jumps at every pause. The rate is the comparable measure; it matches
     * ExoUtil.ENHANCED_DROPPED_FRAMES_PER_SECOND_THRESHOLD, the local downgrade trigger.
     */
    private static final double DROPPED_FRAMES_PER_SECOND_DIAGNOSIS = 4d;
    /** Below this played time a rate is not yet meaningful; the raw count is used instead. */
    private static final long DROPPED_FRAMES_RATE_MIN_PLAY_TIME_MS = 30_000L;
    private static final long DROPPED_FRAMES_RAW_COUNT_DIAGNOSIS = 60;

    private final SimpleDateFormat timeFormat;
    private final TextView topLeft;
    private final TextView topRight;
    private final TextView bottomLeft;
    private final TextView bottomRight;
    private final TextView diagnostics;
    private final TextView diagnosticsExtra;
    private final View diagnosticsPanel;
    private final ViewGroup mediaBadges;
    private final MiniProgressView miniProgress;
    private final Runnable update;
    private final Source source;
    private final View root;
    private final float miniSp;
    private final PlaybackPanelResourceMonitor resourceMonitor;

    private boolean suppressed;
    private final DecimalFormat frameFormat;
    private final DecimalFormat refreshFormat;
    private final DecimalFormat bitrateFormat;
    private final DecimalFormat frameDropRateFormat;
    private final PlaybackSpeedMeter speedMeter = new PlaybackSpeedMeter();
    private long lastSpeedKBps;
    private String lastSpeedText;
    private boolean controlsVisible;
    private boolean diagnosticsVisible;
    private boolean persistentSuppressed;
    private String mediaBadgeSignature = "";
    private View observedControlBar;
    private View.OnLayoutChangeListener observedControlBarListener;
    private boolean started;
    private PlayerManager diagnosticsSamplingPlayer;

    public PlayerOsdController(View root, TextView topLeft, TextView topRight, TextView bottomLeft, TextView bottomRight, TextView diagnostics, MiniProgressView miniProgress, Source source, float miniSp) {
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.bitrateFormat = new DecimalFormat("#.0");
        // "#.0" prints 0.5 as ".5"; a rate must keep its leading digit.
        this.frameDropRateFormat = new DecimalFormat("0.0");
        this.refreshFormat = new DecimalFormat("#.##");
        this.frameFormat = new DecimalFormat("#.###");
        this.miniProgress = miniProgress;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
        this.diagnostics = diagnostics;
        this.diagnosticsExtra = root.findViewById(R.id.osdDiagnosticsExtra);
        this.diagnosticsPanel = root.findViewById(R.id.osdDiagnosticsPanel);
        this.mediaBadges = root.findViewById(R.id.osdMediaBadges);
        this.topRight = topRight;
        this.topLeft = topLeft;
        this.miniSp = miniSp;
        this.source = source;
        this.root = root;
        this.update = this::update;
        this.resourceMonitor = new PlaybackPanelResourceMonitor(root.getContext());
        diagnosticsExtra.setVisibility(View.GONE);
        updateDiagnosticsWidth();
    }

    public void start() {
        started = true;
        if (suppressed) {
            root.setVisibility(View.GONE);
            return;
        }
        // 即使用户关闭所有 OSD，控制栏显示时也需要强制显示标题/分辨率/时间
        // 所以不能在 !isOsdEnabled() 时直接 return
        if (!PlayerSetting.isOsdEnabled() && !controlsVisible) {
            root.setVisibility(View.GONE);
            return;
        }
        resetSpeed();
        App.removeCallbacks(update);
        App.post(update, 0);
    }

    public void stop() {
        started = false;
        stopDiagnosticsSampling();
        App.removeCallbacks(update);
        root.setVisibility(View.GONE);
    }

    public void release() {
        stop();
        stopObservingControlBar();
    }

    public void setSuppressed(boolean suppressed) {
        if (this.suppressed == suppressed) return;
        this.suppressed = suppressed;
        App.removeCallbacks(update);
        if (suppressed) root.setVisibility(View.GONE);
        else if (started) start();
    }

    public void setControlsVisible(boolean controlsVisible) {
        if (this.controlsVisible == controlsVisible) return;
        this.controlsVisible = controlsVisible;
        if (started) {
            // 控制栏显示时，即使 OSD 全关也要启动更新循环（为了强制显示时间）
            if (controlsVisible && !PlayerSetting.isOsdEnabled()) {
                resetSpeed();
                App.removeCallbacks(update);
                App.post(update, 0);
            }
            render();
        }
    }

    public void setPersistentSuppressed(boolean persistentSuppressed) {
        if (this.persistentSuppressed == persistentSuppressed) return;
        this.persistentSuppressed = persistentSuppressed;
        if (started) render();
    }

    public boolean isDiagnosticsVisible() {
        return diagnosticsVisible;
    }

    public String sampleSpeedText() {
        return lastSpeedText;
    }

    public void setDiagnosticsVisible(boolean visible) {
        boolean next = visible && PlayerSetting.isOsdDiagnostics();
        if (diagnosticsVisible == next) return;
        diagnosticsVisible = next;
        if (!next) stopDiagnosticsSampling();
        if (started) render();
    }

    public void toggleDiagnostics() {
        if (!PlayerSetting.isOsdDiagnostics()) return;
        diagnosticsVisible = !diagnosticsVisible;
        if (!diagnosticsVisible) stopDiagnosticsSampling();
        if (started) render();
    }

    private void update() {
        if (render()) App.post(update, 1000);
    }

    private boolean render() {
        if (suppressed) {
            // mobile 播放器在控制栏出现时会抑制整个 OSD 根（控制栏自带标题与分辨率）。
            // 媒体信息徽标是唯一的例外：它只在「屏显设置 → 媒体信息」开启时才放行。
            if (controlsVisible && renderMediaBadgesOnly()) return true;
            root.setVisibility(View.GONE);
            return false;
        }
        setTextSize(miniSp);
        PlayerManager player = source.getPlayer();
        updateSpeed(player);

        // 控制栏显示时的处理：
        // - leanback: suppressed=false，强制显示 OSD 的标题/分辨率/时间（因为控制栏没有自己的 title/size）
        // - mobile: suppressed=true，已在上面 return，不会执行此分支（mobile 控制栏有自己的 title/size）
        if (controlsVisible) {
            stopDiagnosticsSampling();
            setTopLeftForControls(player);
            setTopRightForControls();
            bottomLeft.setVisibility(View.GONE);
            bottomRight.setVisibility(View.GONE);
            diagnosticsPanel.setVisibility(View.GONE);
            setMediaBadges(player);
            if (miniProgress != null) miniProgress.setVisibility(View.GONE);
            // 标题、时间、媒体信息徽标，至少一个可见就显示 root
            boolean hasVisible = topLeft.getVisibility() == View.VISIBLE || topRight.getVisibility() == View.VISIBLE
                    || (mediaBadges != null && mediaBadges.getVisibility() == View.VISIBLE);
            root.setVisibility(hasVisible ? View.VISIBLE : View.GONE);
            return true;
        }

        // 控制栏隐藏时，若用户关闭所有 OSD 屏显，停止刷新并隐藏
        boolean enabled = PlayerSetting.isOsdEnabled();
        if (!enabled) {
            stopDiagnosticsSampling();
            root.setVisibility(View.GONE);
            return false;
        }

        // 控制栏隐藏时，按用户设置显示常驻屏显；用户打开「媒体信息」开关后，
        // 徽标与标题 / 时间 / 进度 / 码率同级常驻，不再依赖控制栏是否唤醒。
        root.setVisibility(View.VISIBLE);
        if (persistentSuppressed) {
            hidePersistent();
            setDiagnosticsPanel(player);
            root.setVisibility(diagnosticsPanel.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
            return true;
        }
        setTopLeft(player);
        setTopRight();
        setBottomLeft(player);
        setBottomRight();
        setDiagnosticsPanel(player);
        setMiniProgress(player);
        setMediaBadges(player);
        // 常驻项（含媒体信息徽标）全不可见时不再保留一个空的 OSD 根
        boolean persistentVisible = topLeft.getVisibility() == View.VISIBLE
                || topRight.getVisibility() == View.VISIBLE
                || bottomLeft.getVisibility() == View.VISIBLE
                || bottomRight.getVisibility() == View.VISIBLE
                || diagnosticsPanel.getVisibility() == View.VISIBLE
                || (mediaBadges != null && mediaBadges.getVisibility() == View.VISIBLE)
                || (miniProgress != null && miniProgress.getVisibility() == View.VISIBLE);
        root.setVisibility(persistentVisible ? View.VISIBLE : View.GONE);
        return true;
    }

    /**
     * 控制栏出现但 OSD 根被抑制（mobile 播放器）时的渲染路径。
     * 只放行媒体信息徽标，四角文本、诊断面板与迷你进度一律隐藏；
     * 徽标自身的两条约束与常驻路径完全一致：必须全屏、且媒体信息总开关打开，
     * 否则控制栏唤醒也不显示（「媒体信息位置 = 关闭」时正是这种情况）。
     */
    private boolean renderMediaBadgesOnly() {
        if (!source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges()) {
            topLeft.setVisibility(View.GONE);
            topRight.setVisibility(View.GONE);
            bottomLeft.setVisibility(View.GONE);
            bottomRight.setVisibility(View.GONE);
            diagnosticsPanel.setVisibility(View.GONE);
            if (miniProgress != null) miniProgress.setVisibility(View.GONE);
            if (mediaBadges != null) {
                mediaBadges.setVisibility(View.GONE);
                mediaBadgeSignature = "";
            }
            root.setVisibility(View.GONE);
            return false;
        }
        topLeft.setVisibility(View.GONE);
        topRight.setVisibility(View.GONE);
        bottomLeft.setVisibility(View.GONE);
        bottomRight.setVisibility(View.GONE);
        diagnosticsPanel.setVisibility(View.GONE);
        if (miniProgress != null) miniProgress.setVisibility(View.GONE);
        setMediaBadges(source.getPlayer());
        boolean visible = mediaBadges != null && mediaBadges.getVisibility() == View.VISIBLE;
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
        return visible;
    }

    private void setTopLeft(PlayerManager player) {
        boolean showTitle = PlayerSetting.isOsdTitle();
        boolean showResolution = PlayerSetting.isOsdResolution();
        if ((!showTitle && !showResolution) || diagnosticsVisible) {
            topLeft.setVisibility(View.GONE);
            return;
        }
        String title = showTitle ? source.getTitle() : "";
        String size = showResolution && player != null ? player.getSizeText() : "";
        topLeft.setText(join("\n", title, size));
        topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
    }

    private void setTopLeftForControls(PlayerManager player) {
        // 控制栏显示时，强制显示标题和分辨率
        String title = source.getTitle();
        String size = player != null ? player.getSizeText() : "";
        topLeft.setText(join("\n", title, size));
        topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
    }

    private void setTopRight() {
        topRight.setVisibility(PlayerSetting.isOsdTime() ? View.VISIBLE : View.GONE);
        if (PlayerSetting.isOsdTime()) topRight.setText(timeFormat.format(new Date()));
    }

    private void setTopRightForControls() {
        // 控制栏显示时，强制显示时间，无论用户设置
        topRight.setText(timeFormat.format(new Date()));
        topRight.setVisibility(View.VISIBLE);
    }

    private void setBottomLeft(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdProgress() || player == null || player.isLive()) {
            bottomLeft.setVisibility(View.GONE);
            return;
        }
        long position = Math.max(0, player.getPosition());
        long duration = Math.max(0, player.getDuration());
        if (duration <= 0) {
            bottomLeft.setVisibility(View.GONE);
            return;
        }
        bottomLeft.setText(Util.timeMs(position) + " / " + Util.timeMs(duration));
        bottomLeft.setVisibility(View.VISIBLE);
    }

    private void setBottomRight() {
        bottomRight.setVisibility(PlayerSetting.isOsdTraffic() ? View.VISIBLE : View.GONE);
        if (!PlayerSetting.isOsdTraffic()) return;
        bottomRight.setText(lastSpeedText);
        bottomRight.setVisibility(TextUtils.isEmpty(lastSpeedText) ? View.GONE : View.VISIBLE);
    }

    private void setDiagnosticsPanel(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdDiagnostics() || !diagnosticsVisible || player == null) {
            stopDiagnosticsSampling();
            diagnosticsPanel.setVisibility(View.GONE);
            return;
        }
        startDiagnosticsSampling(player);
        DiagnosticsText text = getDiagnostics(player);
        boolean land = isLandscape();
        updateDiagnosticsWidth();
        diagnostics.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
        diagnosticsExtra.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
        diagnostics.setText(land ? text.main() : text.all());
        diagnosticsExtra.setText(text.extra());
        diagnosticsExtra.setVisibility(land && !TextUtils.isEmpty(text.extra()) ? View.VISIBLE : View.GONE);
        diagnosticsPanel.setVisibility(TextUtils.isEmpty(text.all()) ? View.GONE : View.VISIBLE);
    }

    private void updateDiagnosticsWidth() {
        int rootWidth = root.getWidth() > 0 ? root.getWidth() : App.get().getResources().getDisplayMetrics().widthPixels;
        int rootHeight = root.getHeight() > 0 ? root.getHeight() : App.get().getResources().getDisplayMetrics().heightPixels;
        if (rootWidth <= 0) return;
        boolean land = rootWidth >= rootHeight;
        int width = Math.round(rootWidth * (land ? 0.98f : 0.98f));
        ViewGroup.LayoutParams params = diagnosticsPanel.getLayoutParams();
        if (params != null && params.width != width) {
            params.width = width;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            diagnosticsPanel.setLayoutParams(params);
        }
        diagnosticsPanel.setPadding(dp(land ? 8 : 5), dp(land ? 7 : 5), dp(land ? 8 : 7), dp(land ? 7 : 5));
        diagnostics.setMaxWidth(width);
        diagnosticsExtra.setMaxWidth(width);
        if (rootHeight > 0) {
            int maxHeight = Math.round(rootHeight * (land ? 0.84f : 1.0f));
            diagnostics.setMaxHeight(maxHeight);
            diagnosticsExtra.setMaxHeight(maxHeight);
        }
        diagnostics.setTextScaleX(land ? 0.96f : 0.92f);
        diagnosticsExtra.setTextScaleX(land ? 0.96f : 0.92f);
    }

    private float getDiagnosticsSp() {
        boolean land = isLandscape();
        float target = land ? 10.2f : 8.0f;
        return Math.min(miniSp, target);
    }

    private boolean isLandscape() {
        int rootWidth = root.getWidth() > 0 ? root.getWidth() : App.get().getResources().getDisplayMetrics().widthPixels;
        int rootHeight = root.getHeight() > 0 ? root.getHeight() : App.get().getResources().getDisplayMetrics().heightPixels;
        return rootWidth >= rootHeight;
    }

    private void setMiniProgress(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdMini() || player == null || player.isLive()) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        long duration = Math.max(0, player.getDuration());
        if (duration <= 0) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        miniProgress.setProgress(player.getPosition(), duration);
        miniProgress.setVisibility(View.VISIBLE);
    }

    /**
     * 屏显媒体信息徽标：只显示播放器能直接证明的事实。
     * 每秒轮询都会走到这里，因此只有徽标文本发生变化时才重建子视图，
     * 避免每秒钟反复 addView 触发测量与布局。
     */
    private void setMediaBadges(PlayerManager player) {
        if (mediaBadges == null) return;
        // 三条判断：
        // 1. 只在全屏画面里出现——半屏/小窗里它会把本来就拥挤的控件堆得更挤；
        // 2. 媒体信息总开关（设置页「媒体信息位置」）必须打开——选「关闭」时任何情况都不显示；
        // 3. 控制栏唤醒时直接显示（用户要的就是唤醒时能看到），
        //    控制栏隐藏时则看「屏显设置」里的常驻开关，两者互不牵连。
        if (player == null || !source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges()
                || (!controlsVisible && !PlayerSetting.isDisplayMediaBadgesPersistent())) {
            mediaBadges.setVisibility(View.GONE);
            mediaBadgeSignature = "";
            return;
        }
        Format video = player.getVideoFormat();
        PlayerEngine.VideoPlaybackDetails details = player.getVideoPlaybackDetails();
        AudioPlaybackDiagnostics.Snapshot audio = player.getAudioPlaybackDiagnostics();
        List<MediaBadgeResolver.MediaBadge> badges = MediaBadgeResolver.resolve(video, details, audio == null ? null : audio.activeTrack(), player.getUrl(), source.getTitle());
        if (badges.isEmpty()) {
            mediaBadges.setVisibility(View.GONE);
            mediaBadgeSignature = "";
            return;
        }
        applyMediaBadgePosition();
        StringBuilder signature = new StringBuilder();
        for (MediaBadgeResolver.MediaBadge badge : badges) signature.append(badge.group()).append(':').append(badge.plate()).append(':').append(badge.text()).append('|');
        String next = signature.toString();
        if (!next.equals(mediaBadgeSignature)) {
            mediaBadgeSignature = next;
            mediaBadges.removeAllViews();
            for (MediaBadgeResolver.MediaBadge badge : badges) mediaBadges.addView(createMediaBadge(badge));
        }
        mediaBadges.setVisibility(View.VISIBLE);
    }

    /**
     * 徽标贴角规则：
     * 1. 控制栏唤醒时，控制栏占据画面顶部与底部，四角都要让开它的高度，否则会压住标题栏 /
     *    进度条；这是唯一的「无条件内缩」场景。
     * 2. 控制栏隐藏时徽标是常驻项，此时只有在同一个角**确实还有别的常驻文本**
     *    （同角的标题 / 时间 / 进度 / 网速）时才抬高一行躲开它；没有邻居就直接贴到画面边缘，
     *    这才是用户要的「最靠边缘」。
     * 3. 布局给四角留了内边距，而 margin 是从内沿开始量的；所以要把它折算掉，让「徽标到正下方
     *    障碍物」的肉眼间距无论控制栏在不在、手机还是电视，都正好等于 {@link #badgeGap()}。
     */
    private void applyMediaBadgePosition() {
        if (mediaBadges == null) return;
        ViewGroup.LayoutParams params = mediaBadges.getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) params;
        int position = PlayerSetting.getMediaBadgePosition();
        View neighbour = persistentNeighbour(position);
        boolean stacked = !controlsVisible && neighbour != null;
        // 邻居的实际高度 + 一点呼吸位；量不到时退回一条文本行的高度。
        int clear = (stacked ? Math.max(neighbour.getHeight(), dp(18)) : 0) + dp(4);
        // 贴边 / 避让控制栏用的 margin：都把容器内边距折算掉，肉眼间距才等于 badgeGap()。
        // 用负 margin 去顶边缘会顶进系统栏导致胶囊被裁切，所以下限取 0。
        View badgeParent = mediaBadges.getParent() instanceof View ? (View) mediaBadges.getParent() : null;
        int gap = badgeGap();
        int edgeTop = Math.max(0, gap - (badgeParent == null ? 0 : badgeParent.getPaddingTop()));
        int edgeBottom = Math.max(0, gap - (badgeParent == null ? 0 : badgeParent.getPaddingBottom()));
        int edgeStart = Math.max(0, gap - (badgeParent == null ? 0 : badgeParent.getPaddingLeft()));
        int edgeEnd = Math.max(0, gap - (badgeParent == null ? 0 : badgeParent.getPaddingRight()));
        // 控制栏唤醒时的避让：必须按控制栏的真实高度算，不能写死。
        // 电视端控制栏是底部一整条（按钮行 + 进度条），手机端高度也不同；写死 64/80dp 会让
        // 徽标落到按钮行上，看起来就像「被塞进了控制栏」。
        View bar = controlBar();
        observeControlBar(bar);
        int barTop = controlBarTopInset(bar, edgeBottom);
        int gravity;
        int marginStart;
        int marginEnd;
        int topMargin;
        int bottomMargin;
        switch (position) {
            case 0:
                gravity = Gravity.TOP | Gravity.START;
                marginStart = stacked ? 0 : edgeStart;
                marginEnd = 0;
                topMargin = controlsVisible ? dp(64) : (stacked ? clear : edgeTop);
                bottomMargin = 0;
                break;
            case 1:
                gravity = Gravity.TOP | Gravity.END;
                marginStart = 0;
                marginEnd = stacked ? 0 : edgeEnd;
                topMargin = controlsVisible ? dp(64) : (stacked ? clear : edgeTop);
                bottomMargin = 0;
                break;
            case 2:
                gravity = Gravity.BOTTOM | Gravity.START;
                marginStart = stacked ? 0 : edgeStart;
                marginEnd = 0;
                topMargin = 0;
                bottomMargin = controlsVisible ? barTop : (stacked ? clear : edgeBottom);
                break;
            default:
                gravity = Gravity.BOTTOM | Gravity.END;
                marginStart = 0;
                marginEnd = stacked ? 0 : edgeEnd;
                topMargin = 0;
                bottomMargin = controlsVisible ? barTop : (stacked ? clear : edgeBottom);
                break;
        }
        if (layoutParams.gravity == gravity && layoutParams.topMargin == topMargin && layoutParams.bottomMargin == bottomMargin
                && layoutParams.getMarginStart() == marginStart && layoutParams.getMarginEnd() == marginEnd) return;
        layoutParams.gravity = gravity;
        layoutParams.topMargin = topMargin;
        layoutParams.bottomMargin = bottomMargin;
        layoutParams.setMarginStart(marginStart);
        layoutParams.setMarginEnd(marginEnd);
        mediaBadges.setLayoutParams(layoutParams);
    }

    /**
     * 徽标与「正下方 / 正上方障碍物」之间要留的呼吸位。控制栏唤醒时是控制栏边缘，
     * 控制栏隐藏且同角无邻居时是画面边缘；手机与电视共用同一个值。
     *
     * <p>这个 16dp 是**扣除** app/src/main/res/layout/view_player_osd.xml 外层 FrameLayout 的
     * android:padding="14dp" 之后生效的肉眼间距；改那个 padding 会同步改变肉眼距离
     * （{@link #applyMediaBadgePosition()} 用 Math.max(0, gap - getPaddingXxx()) 折算）。
     */
    private int badgeGap() {
        return dp(16);
    }

    /**
     * 下两角控制栏可见时的 bottomMargin：控制栏高度 + 呼吸位，再扣掉容器内边距。
     * 电视端是底部一整条（按钮行 + 进度条），手机端真正占位的只是它内部的 bottom 条；
     * 写死数值会让徽标落到按钮行上，看起来像「被塞进控制栏」。
     * 量不到时退回旧常量，保证首帧不会跳到画面外。
     */
    private int controlBarTopInset(View bar, int edgeGap) {
        if (bar == null || bar.getVisibility() != View.VISIBLE) return dp(80) + edgeGap;
        // 手机端控制栏根是 match_parent（整屏铺底），真正占位的是它里面的 bottom 底部条；
        // 电视端控制栏根本身就是底部一整条。两种都按「实际占位的那个」来量。
        // 手机端内联控制栏的底部条是 {@code @+id/bottom}，电视端详情页内嵌播放器是 {@code @+id/playerBottom}。
        View bottom = bar.findViewById(R.id.bottom);
        if (bottom == null) bottom = bar.findViewById(R.id.playerBottom);
        int height = bottom != null && bottom.getVisibility() == View.VISIBLE && bottom.getHeight() > 0
                ? bottom.getHeight()
                : bar.getHeight();
        if (height <= 0 || height >= root.getHeight()) return dp(80) + edgeGap;
        return height + edgeGap;
    }

    /**
     * 控制栏与 OSD 是同一父容器下的兄弟节点，不是 OSD 的子节点，所以要从父容器里找。
     * 详情页内嵌播放器没有 {@code @+id/control}：手机端的内联控件容器是
     * {@code @+id/detailControlHost}，电视端是 {@code @+id/playerControls}。按可见性挑第一个，
     * 避免拿到 GONE 的那个容器从而错误回退 dp(80)；都还没有时返回 null，
     * 由 {@link #controlBarTopInset(View, int)} 回退 dp(80)。
     */
    private View controlBar() {
        ViewParent parent = root.getParent();
        if (!(parent instanceof View)) return null;
        View host = (View) parent;
        return firstVisible(host.findViewById(R.id.control), host.findViewById(R.id.detailControlHost), host.findViewById(R.id.playerControls));
    }

    private static View firstVisible(View... candidates) {
        for (View candidate : candidates) {
            if (candidate != null && candidate.getVisibility() == View.VISIBLE) return candidate;
        }
        for (View candidate : candidates) {
            if (candidate != null) return candidate;
        }
        return null;
    }

    /**
     * 控制栏唤醒的那一帧，它内部的按钮行 / 进度条可能还没完成布局，此时量到的高度偏小，
     * 徽标就会压到按钮行上（看起来像被塞进了控制栏）。所以登记一次布局监听：
     * 控制栏每次布局定稿后重算徽标位置，保证等到的永远是最终高度。
     */
    private void observeControlBar(View bar) {
        if (bar == null || bar == observedControlBar) return;
        stopObservingControlBar();
        observedControlBar = bar;
        observedControlBarListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                root.post(this::applyMediaBadgePosition);
        bar.addOnLayoutChangeListener(observedControlBarListener);
    }

    /**
     * 注销控制栏布局监听，避免 OSD 释放后仍被控制栏布局回调持有（回调泄漏）。
     */
    private void stopObservingControlBar() {
        if (observedControlBar != null && observedControlBarListener != null) {
            observedControlBar.removeOnLayoutChangeListener(observedControlBarListener);
        }
        observedControlBar = null;
        observedControlBarListener = null;
    }

    /**
     * 徽标所在的角在控制栏隐藏时是否还有别的常驻文本。只有此时才需要把徽标抬高 / 内缩，
     * 否则应当贴住画面边缘（用户要的「最靠边缘」）。
     */
    private View persistentNeighbour(int position) {
        switch (position) {
            case 0:
                return isShown(topLeft) ? topLeft : null;
            case 1:
                return isShown(topRight) ? topRight : null;
            case 2:
                return isShown(bottomLeft) ? bottomLeft : null;
            default:
                return isShown(bottomRight) ? bottomRight : null;
        }
    }

    private static boolean isShown(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /**
     * 一个徽标 = 成品牌 + 事实文本，整块包在分组色胶囊里。
     * 图形全部来自随包内置的参考实现牌（离线可用，不会出现首帧空白）；
     * 没有对应牌的纯数值事实（帧率、码率、容器、少见编码）只显示文本，
     * 不再用语义无关的自绘占位图标，避免「图标和内容不搭边」。
     */
    private View createMediaBadge(MediaBadgeResolver.MediaBadge badge) {
        Context context = root.getContext();
        boolean hasPlate = badge.plate() != null;
        boolean hasText = badge.text() != null && !badge.text().isEmpty();
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        // 固定胶囊高度：有牌的徽标由 16dp 牌撑高，纯文本徽标只有一行 10sp 文本，
        // 不固定高度时两者会差 4px 左右，一行里看起来参差不齐。
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(20));
        params.setMargins(0, dp(2), dp(4), 0);
        view.setLayoutParams(params);
        view.setBackground(categoryBackground(badge.group()));
        view.setPadding(dp(6), dp(2), dp(6), dp(2));

        int plate = hasPlate ? plateResource(badge.plate()) : 0;
        if (plate != 0) {
            ImageView icon = new ImageView(context);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(16));
            if (hasText) iconParams.setMarginEnd(dp(4));
            icon.setLayoutParams(iconParams);
            icon.setImageResource(plate);
            icon.setAdjustViewBounds(true);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.addView(icon);
        }

        if (hasText) {
            TextView text = new TextView(context);
            text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            text.setSingleLine(true);
            text.setText(badge.text());
            text.setTextColor(0xFFFFFFFF);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            text.setShadowLayer(2f, 1.2f, 1.2f, App.get().getResources().getColor(R.color.webhtv_color_player_scrim));
            view.addView(text);
        }
        return view;
    }

    /** 类别色胶囊：左侧偏类别色、右侧压暗，既有颜色又保证任意画面下白字可读。 */
    private GradientDrawable categoryBackground(MediaBadgeCatalog.Group group) {
        int color = mediaBadgeColor(group);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{withAlpha(color, 0x8A), 0xD9040A12});
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), withAlpha(color, 0xE6));
        return background;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * 每个分组的主题色。参考实现的 JSON 只给了黑白边框，
     * 这里的彩色是本项目自己分的组，让一行里「分辨率/画质/编码/音轨/来源」一眼可分。
     */
    private static int mediaBadgeColor(MediaBadgeCatalog.Group group) {
        switch (group) {
            case RESOLUTION:
                return 0xFF5A8DEE;
            case DYNAMIC_RANGE:
                return 0xFF7B61FF;
            case VIDEO_TECH:
                return 0xFF22D3EE;
            case VIDEO_CODEC:
                return 0xFF14B8A6;
            case AUDIO:
                return 0xFFEF4444;
            case CHANNELS:
                return 0xFFF97316;
            case FRAME_RATE:
                return 0xFF10B981;
            case BITRATE:
                return 0xFFF59E0B;
            case CONTAINER:
                return 0xFF64748B;
            case MEDIA_SOURCE:
                return 0xFF0EA5E9;
            case QUALITY:
                return 0xFFA855F7;
            case EDITION:
                return 0xFF8B5CF6;
            case SPECIAL_TAG:
                return 0xFFEC4899;
            case IMAX:
                return 0xFF2563EB;
            case STREAMING:
                return 0xFFDC2626;
            default:
                return 0xFF64748B;
        }
    }

    /**
     * 牌资源映射：键与 {@link MediaBadgeCatalog} 的 plate 一一对应，
     * 资源名规则为 {@code badge_<plate>}。这里写成显式常量引用，
     * 保证 release 的资源压缩不会把只在运行时拼名字的牌删掉。
     */
    private static int plateResource(String plate) {
        Integer resource = PLATES.get(plate);
        return resource == null ? 0 : resource;
    }

    private static final Map<String, Integer> PLATES = plates();

    private static Map<String, Integer> plates() {
        Map<String, Integer> map = new HashMap<>();
        map.put("gst_seadex", R.drawable.badge_gst_seadex);
        map.put("gst_hybrid", R.drawable.badge_gst_hybrid);
        map.put("gst_criterion", R.drawable.badge_gst_criterion);
        map.put("gst_proper", R.drawable.badge_gst_proper);
        map.put("gst_repack", R.drawable.badge_gst_repack);
        map.put("gst_remastered", R.drawable.badge_gst_remastered);
        map.put("gst_open_matte", R.drawable.badge_gst_open_matte);
        map.put("gst_regraded", R.drawable.badge_gst_regraded);
        map.put("gst_directors_cut", R.drawable.badge_gst_directors_cut);
        map.put("gst_extended", R.drawable.badge_gst_extended);
        map.put("gst_uncut", R.drawable.badge_gst_uncut);
        map.put("gst_uncensored", R.drawable.badge_gst_uncensored);
        map.put("gst_black_and_white", R.drawable.badge_gst_black_and_white);
        map.put("gst_true_hue", R.drawable.badge_gst_true_hue);
        map.put("gst_theatrical", R.drawable.badge_gst_theatrical);
        map.put("ged_cc", R.drawable.badge_ged_cc);
        map.put("ged_hk", R.drawable.badge_ged_hk);
        map.put("ged_tw", R.drawable.badge_ged_tw);
        map.put("ged_jp", R.drawable.badge_ged_jp);
        map.put("ged_us", R.drawable.badge_ged_us);
        map.put("ged_uk", R.drawable.badge_ged_uk);
        map.put("ged_de", R.drawable.badge_ged_de);
        map.put("ged_fr", R.drawable.badge_ged_fr);
        map.put("ged_eu", R.drawable.badge_ged_eu);
        map.put("ged_kr", R.drawable.badge_ged_kr);
        map.put("ged_arrow", R.drawable.badge_ged_arrow);
        map.put("ged_dieying", R.drawable.badge_ged_dieying);
        map.put("ged_allregion", R.drawable.badge_ged_allregion);
        map.put("gms_iso", R.drawable.badge_gms_iso);
        map.put("gms_high_bitrate", R.drawable.badge_gms_high_bitrate);
        map.put("gms_remux_1", R.drawable.badge_gms_remux_1);
        map.put("gms_remux_2", R.drawable.badge_gms_remux_2);
        map.put("gms_remux_3", R.drawable.badge_gms_remux_3);
        map.put("gms_blu_ray_1", R.drawable.badge_gms_blu_ray_1);
        map.put("gms_blu_ray_2", R.drawable.badge_gms_blu_ray_2);
        map.put("gms_blu_ray_3", R.drawable.badge_gms_blu_ray_3);
        map.put("gms_blu_ray_4", R.drawable.badge_gms_blu_ray_4);
        map.put("gms_blu_ray_5", R.drawable.badge_gms_blu_ray_5);
        map.put("gms_blu_ray_6", R.drawable.badge_gms_blu_ray_6);
        map.put("gms_blu_ray_7", R.drawable.badge_gms_blu_ray_7);
        map.put("gms_blu_ray_8", R.drawable.badge_gms_blu_ray_8);
        map.put("gms_web_1", R.drawable.badge_gms_web_1);
        map.put("gms_web_2", R.drawable.badge_gms_web_2);
        map.put("gms_web_3", R.drawable.badge_gms_web_3);
        map.put("gms_web_4", R.drawable.badge_gms_web_4);
        map.put("gms_web_5", R.drawable.badge_gms_web_5);
        map.put("gms_web_6", R.drawable.badge_gms_web_6);
        map.put("gr_8k", R.drawable.badge_gr_8k);
        map.put("gr_4k", R.drawable.badge_gr_4k);
        map.put("gr_1440p", R.drawable.badge_gr_1440p);
        map.put("gr_1080p", R.drawable.badge_gr_1080p);
        map.put("gr_720p", R.drawable.badge_gr_720p);
        map.put("gr_576p", R.drawable.badge_gr_576p);
        map.put("gr_480p", R.drawable.badge_gr_480p);
        map.put("gr_360p", R.drawable.badge_gr_360p);
        map.put("gr_240p", R.drawable.badge_gr_240p);
        map.put("gr_144p", R.drawable.badge_gr_144p);
        map.put("gq_remux", R.drawable.badge_gq_remux);
        map.put("gq_bluray", R.drawable.badge_gq_bluray);
        map.put("gq_web_dl", R.drawable.badge_gq_web_dl);
        map.put("gq_webrip", R.drawable.badge_gq_webrip);
        map.put("gq_hdrip", R.drawable.badge_gq_hdrip);
        map.put("gq_hc_hdrip", R.drawable.badge_gq_hc_hdrip);
        map.put("gq_dvdrip", R.drawable.badge_gq_dvdrip);
        map.put("gq_hdtv", R.drawable.badge_gq_hdtv);
        map.put("gq_scr", R.drawable.badge_gq_scr);
        map.put("gq_tc", R.drawable.badge_gq_tc);
        map.put("gq_ts", R.drawable.badge_gq_ts);
        map.put("gq_cam", R.drawable.badge_gq_cam);
        map.put("imax_imax_enhanced", R.drawable.badge_imax_imax_enhanced);
        map.put("imax_imax", R.drawable.badge_imax_imax);
        map.put("gv_dolby_vision_hdr10_plus", R.drawable.badge_gv_dolby_vision_hdr10_plus);
        map.put("gv_dolby_vision_hdr10", R.drawable.badge_gv_dolby_vision_hdr10);
        map.put("gv_dolby_vision_hdr", R.drawable.badge_gv_dolby_vision_hdr);
        map.put("gv_12bit", R.drawable.badge_gv_12bit);
        map.put("gv_8bit", R.drawable.badge_gv_8bit);
        map.put("gvf_hdrvivid", R.drawable.badge_gvf_hdrvivid);
        map.put("gvf_3d", R.drawable.badge_gvf_3d);
        map.put("gv_dolby_vision", R.drawable.badge_gv_dolby_vision);
        map.put("gv_hdr10_plus", R.drawable.badge_gv_hdr10_plus);
        map.put("gv_hdr10", R.drawable.badge_gv_hdr10);
        map.put("gv_hdr", R.drawable.badge_gv_hdr);
        map.put("gv_hlg", R.drawable.badge_gv_hlg);
        map.put("gv_10bit", R.drawable.badge_gv_10bit);
        map.put("gv_sdr", R.drawable.badge_gv_sdr);
        map.put("gv_ai", R.drawable.badge_gv_ai);
        map.put("ga_dolby_atmos_truehd", R.drawable.badge_ga_dolby_atmos_truehd);
        map.put("ga_dts_x_hd_ma", R.drawable.badge_ga_dts_x_hd_ma);
        map.put("ga_dolby_atmos_digital_plus", R.drawable.badge_ga_dolby_atmos_digital_plus);
        map.put("ga_dts_x_hd", R.drawable.badge_ga_dts_x_hd);
        map.put("ga_dolby_atmos", R.drawable.badge_ga_dolby_atmos);
        map.put("ga_dts_x", R.drawable.badge_ga_dts_x);
        map.put("ga_dolby_truehd", R.drawable.badge_ga_dolby_truehd);
        map.put("ga_dts_hd_ma", R.drawable.badge_ga_dts_hd_ma);
        map.put("ga_flac", R.drawable.badge_ga_flac);
        map.put("ga_dts_hd", R.drawable.badge_ga_dts_hd);
        map.put("ga_dolby_digital_plus", R.drawable.badge_ga_dolby_digital_plus);
        map.put("ga_dts_es", R.drawable.badge_ga_dts_es);
        map.put("ga_dts", R.drawable.badge_ga_dts);
        map.put("ga_opus", R.drawable.badge_ga_opus);
        map.put("ga_dolby_digital", R.drawable.badge_ga_dolby_digital);
        map.put("ga_aac", R.drawable.badge_ga_aac);
        map.put("ga_lpcm", R.drawable.badge_ga_lpcm);
        map.put("ga_lossless", R.drawable.badge_ga_lossless);
        map.put("gc_7_1", R.drawable.badge_gc_7_1);
        map.put("gc_6_1", R.drawable.badge_gc_6_1);
        map.put("gc_5_1", R.drawable.badge_gc_5_1);
        map.put("gc_2_0", R.drawable.badge_gc_2_0);
        map.put("gc_7_1_4", R.drawable.badge_gc_7_1_4);
        map.put("gc_5_1_4", R.drawable.badge_gc_5_1_4);
        map.put("gc_5_1_2", R.drawable.badge_gc_5_1_2);
        map.put("ge_av1", R.drawable.badge_ge_av1);
        map.put("ge_vvc", R.drawable.badge_ge_vvc);
        map.put("gfr_hfr", R.drawable.badge_gfr_hfr);
        map.put("ge_hevc", R.drawable.badge_ge_hevc);
        map.put("ge_avc", R.drawable.badge_ge_avc);
        map.put("ge_xvid", R.drawable.badge_ge_xvid);
        map.put("ge_divx", R.drawable.badge_ge_divx);
        map.put("gs_peacock", R.drawable.badge_gs_peacock);
        map.put("gs_netflix", R.drawable.badge_gs_netflix);
        map.put("gs_prime_video", R.drawable.badge_gs_prime_video);
        map.put("gs_apple_tv_plus", R.drawable.badge_gs_apple_tv_plus);
        map.put("gs_disney_plus", R.drawable.badge_gs_disney_plus);
        map.put("gs_hbo_max", R.drawable.badge_gs_hbo_max);
        map.put("gs_hulu", R.drawable.badge_gs_hulu);
        map.put("gs_paramount_plus", R.drawable.badge_gs_paramount_plus);
        map.put("gs_crunchyroll", R.drawable.badge_gs_crunchyroll);
        return map;
    }

    private void setTextSize(float sp) {
        topLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        topLeft.setTextColor(0xFFFFFFFF);
        topRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        topRight.setTextColor(0xFFFFFFFF);
        bottomLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomLeft.setTextColor(0xFFFFFFFF);
        bottomRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomRight.setTextColor(0xFFFFFFFF);
        diagnostics.setTextColor(0xFFFFFFFF);
        diagnostics.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
        diagnosticsExtra.setTextColor(0xFFFFFFFF);
        diagnosticsExtra.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
    }

    private void hidePersistent() {
        topLeft.setVisibility(View.GONE);
        topRight.setVisibility(View.GONE);
        bottomLeft.setVisibility(View.GONE);
        bottomRight.setVisibility(View.GONE);
        miniProgress.setVisibility(View.GONE);
        if (mediaBadges != null) mediaBadges.setVisibility(View.GONE);
    }

    private void updateSpeed(PlayerManager player) {
        // No player means no playback to report on; app-wide traffic would mislead here.
        if (player == null) {
            resetSpeed();
            return;
        }
        speedMeter.sample(player);
        lastSpeedKBps = speedMeter.getBytesPerSecond() / 1024;
        lastSpeedText = speedMeter.getText();
    }

    private DiagnosticsText getDiagnostics(PlayerManager player) {
        GpuLoadMonitor.process().requestSample();
        resourceMonitor.requestSample();
        PlaybackAnalyticsListener.Snapshot snapshot = player.isExo() ? PlaybackAnalyticsListener.getSnapshot() : PlaybackAnalyticsListener.Snapshot.empty();
        Format video = snapshot.videoFormat() != null ? snapshot.videoFormat() : snapshot.errorFormat() != null ? snapshot.errorFormat() : player.getVideoFormat();
        Format audio = snapshot.audioFormat();
        String state = stateText(player.getPlaybackState()) + (player.isLoading() ? " / 正在加载" : "");
        String buffer = join(" / ", formatDuration(player.getBufferedDuration()), player.getBufferedPercentage() > 0 ? player.getBufferedPercentage() + "%" : "");
        // 只有 Exo 有 analytics 快照；IJK/MPV 走的是 PlayerManager 的引擎无关缓冲追踪器。
        // 原先只对 MPV 分支，IJK 因此落到 Exo 分支读到 Snapshot.empty()，重缓冲计数恒为 0。
        int rebufferCount = player.isExo() ? snapshot.rebufferCount() : player.getRebufferCount();
        long rebufferTotalMs = player.isExo() ? snapshot.rebufferTotalMs() : player.getRebufferTotalMs();
        String rebuffer = rebufferCount <= 0 ? "0 次" : rebufferCount + " 次 / " + formatDuration(rebufferTotalMs);
        long stableThroughput = player.getNetworkProtectionStableThroughput();
        long consumption = player.getNetworkProtectionConsumption();
        String networkProtection = player.getNetworkProtectionText();
        String strategy = join(" / ",
                TextUtils.isEmpty(networkProtection) ? "" : networkProtection,
                "可支撑 " + new DecimalFormat("0.00x").format(player.getNetworkProtectionSupportedSpeed()),
                "当前 " + new DecimalFormat("0.00x").format(player.getEffectiveSpeed()));
        boolean localSource = PlaybackDiagnosticsSourcePolicy.isLocal(player.getUrl());
        String currentNetworkSpeed = !TextUtils.isEmpty(lastSpeedText)
                ? lastSpeedText : getBandwidthEstimateText(snapshot);
        String network = localSource ? "本地文件 / 不检测网速" : player.isExo() ? join(" / ",
                !TextUtils.isEmpty(currentNetworkSpeed) ? "当前 " + currentNetworkSpeed : "",
                consumption > 0 ? "消费需求 " + formatBitrate(consumption) : "",
                stableThroughput > 0 ? "稳定吞吐 " + formatBitrate(stableThroughput) : "",
                stableThroughput > 0 && consumption > 0 ? "网络余量 " + formatSignedBitrate(stableThroughput - consumption) : "")
                : join(" / ", "当前 " + emptyDash(lastSpeedText));
        if (TextUtils.isEmpty(network)) network = "待采样";
        String renderDiagnostics = player.isMpv() ? player.getRenderDiagnostics() : "";
        String runtimeDiagnostics = player.isMpv() ? player.getRuntimeDiagnostics() : "";
        String gpu = join(" / ", getSystemGpuText(),
                player.getGpuLoadDiagnostics());
        PlaybackPanelResourceMonitor.Snapshot resources = resourceMonitor.snapshot();
        String cpu = getCpuText(resources);
        String memory = getMemoryText(resources);
        String frameTiming = player.isExo() ? summarizeFrameTiming() : "";
        PlayerEngine.VideoPlaybackDetails videoDetails =
                player.getVideoPlaybackDetails();
        VideoTrackState videoTrack = getVideoTrackState(player);
        String videoText = summarizeVideo(video, player,
                snapshot.videoDecoderName(), videoTrack,
                videoDetails);
        AudioTrackState audioTrack = getAudioTrackState(player);
        AudioPlaybackDiagnostics.Snapshot audioDetails = player.getAudioPlaybackDiagnostics();
        String runtimeAudio = AudioPlaybackDiagnostics.format(audioDetails);
        String audioText = TextUtils.isEmpty(runtimeAudio)
                ? summarizeAudio(audio, audioTrack, snapshot.audioDecoderName())
                : runtimeAudio;
        String render = PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "Surface" : "Texture";
        String tunnel = switchText(PlayerSetting.isTunnelingEnabled());
        String performance = PlaybackPerformanceSetting.getProfileName(player.getPlayerType());
        String passThrough = player.getAudioPassThroughText();
        String preload = "预载" + switchText(PreloadSetting.isPreload(player.getPlayerType()));
        String frameRateMatch = player.isExo() ? "帧率匹配 开" : "";
        String softTune = getSoftDecodeTuneText(player);
        String playerText = join(" / ", player.getPlayerText(), player.getDecodeText(), render, "隧道" + tunnel, "性能" + performance, frameRateMatch, preload, softTune, player.isExo() ? "兜底开" : "");
        String playback = join(" / ", state, buffer, "重缓冲 " + rebuffer, droppedFrameText(player));
        String startup = getStartupText(player);
        String error = getErrorText(player, snapshot);
        String main = join("\n",
                TextUtils.isEmpty(error) ? "" : row("错误", error),
                row("视频", videoText),
                row("音频", audioText),
                row("网络", network),
                player.isExo() && !localSource ? row("动态网络保护", strategy) : "",
                TextUtils.isEmpty(renderDiagnostics) ? "" : row("MPV渲染", renderDiagnostics),
                TextUtils.isEmpty(runtimeDiagnostics) ? "" : row("MPV运行", runtimeDiagnostics),
                TextUtils.isEmpty(gpu) ? "" : row("GPU", gpu),
                TextUtils.isEmpty(cpu) ? "" : row("CPU", cpu),
                TextUtils.isEmpty(memory) ? "" : row("内存", memory),
                TextUtils.isEmpty(frameTiming) ? "" : row("帧调度", frameTiming),
                row("播放", playback),
                row("配置", playerText),
                TextUtils.isEmpty(startup) ? "" : row("起播", startup),
                row("结论", getDiagnosis(player, snapshot, video, videoTrack, audioTrack, audioDetails, localSource)));
        String extra = join("\n",
                row("设备", getDeviceText()),
                row("系统", getSystemText()),
                row("芯片", getChipText()),
                row("屏幕", getDisplayText()),
                row("WebView", getWebViewText()),
                row("网络环境", getNetworkEnvironmentText()));
        return new DiagnosticsText(main, extra);
    }

    /**
     * Startup timeline plus the stage that consumed the most time, so a slow start can
     * be attributed on-device instead of requiring a debug log export.
     */
    private String getStartupText(PlayerManager player) {
        String summary = player.getStartupSummary();
        if (TextUtils.isEmpty(summary)) return "";
        String slowest = player.getSlowestStartupStage();
        return TextUtils.isEmpty(slowest) ? summary : summary + "  最慢 " + slowest;
    }

    /**
     * "掉帧 12（1.4/秒）" for EXO. The rate is what the number should be read against: media3
     * settles a renderer's dropped-frame accumulator from onStopped(), so a pause adds
     * whatever tail accumulated while playing and a bare total looks like pause damage.
     * MPV and the other engines keep reporting their own raw counter unchanged.
     */
    private String droppedFrameText(PlayerManager player) {
        long dropped = player.getDroppedFrames();
        if (!player.isExo()) return "掉帧 " + dropped;
        double rate = PlaybackAnalyticsListener.droppedFramesPerSecond();
        return "掉帧 " + dropped + "（" + frameDropRateFormat.format(rate) + "/秒）";
    }

    /**
     * Whether the drop count is worth a verdict. The raw total is only comparable to a fixed
     * budget while there is no measured play time to divide by; after that the rate decides,
     * so a pause that settles the accumulator no longer reads as decoder pressure.
     */
    private boolean excessiveDroppedFrames(PlayerManager player) {
        if (!player.isExo()) return player.getDroppedFrames() >= DROPPED_FRAMES_RAW_COUNT_DIAGNOSIS;
        long playTimeMs = PlaybackAnalyticsListener.playTimeMs();
        if (playTimeMs < DROPPED_FRAMES_RATE_MIN_PLAY_TIME_MS) {
            return player.getDroppedFrames() >= DROPPED_FRAMES_RAW_COUNT_DIAGNOSIS;
        }
        return PlaybackAnalyticsListener.droppedFramesPerSecond() >= DROPPED_FRAMES_PER_SECOND_DIAGNOSIS;
    }

    private String getDiagnosis(PlayerManager player, PlaybackAnalyticsListener.Snapshot snapshot, Format video, VideoTrackState videoTrack, AudioTrackState audioTrack, AudioPlaybackDiagnostics.Snapshot audioDetails, boolean localSource) {
        if (audioDetails.runtimeState() == AudioPlaybackDiagnostics.RuntimeState.FAILED) return "音频链路失败，先看音频和错误行";
        if (isDecodeError(snapshot) && player.isHardDecode()) return "硬件解码失败：设备可能不支持该视频编码、分辨率、帧率或规格";
        if (!TextUtils.isEmpty(snapshot.errorCode())) return "播放器报错，先看错误行";
        if (audioTrack.hasTracks() && audioTrack.isUnsupported()) return "音频轨不支持：" + summarizeAudioFormat(audioTrack.format()) + " / " + supportText(audioTrack.support());
        if (player.isExo() && audioTrack.hasTracks() && !audioTrack.selected() && snapshot.audioFormat() == null && player.getPlaybackState() == androidx.media3.common.Player.STATE_READY) return "已发现音轨但未选中，可能无声";
        // A video track without any usable decoder is dropped silently: playback
        // keeps advancing with audio and subtitles only, so nothing else in this
        // panel would flag it and the verdict would read as healthy. "Handled" is
        // the test rather than the unsupported codes alone: EXCEEDS_CAPABILITIES
        // drops the track the same way, and it is one of the two states
        // PlayerManager.needsSoftDecode reacts to.
        if (videoTrack.hasTracks() && !videoTrack.selected() && !videoTrack.isHandled()) return videoDiagnosis(player, videoTrack);
        if (!localSource) {
            long mediaBitrate = getMediaBitrate(video, snapshot.audioFormat() != null ? snapshot.audioFormat() : audioTrack.format());
            long availableBitrate = snapshot.bandwidthEstimate() > 0 ? snapshot.bandwidthEstimate() : lastSpeedKBps * 1024 * 8;
            if (availableBitrate > 0 && mediaBitrate > 0 && availableBitrate < mediaBitrate * 13 / 10) return "网速可能低于资源码率";
            // Must use the raw buffered duration: getBufferedDuration() folds in completed
            // disk ranges, which reads high enough that this hint could never fire.
            if (player.isLoading() && player.getNativeBufferedDuration() < 3000) return "缓冲偏少，可能是网络或源响应慢";
        }
        if (excessiveDroppedFrames(player)) return "掉帧较多，可能是解码或渲染压力";
        if (!localSource && formatBitrateValue(video) >= 30_000_000) return "资源码率较高，对网络和解码要求高";
        // Use the same observed output as the audio row. A missing decoder name
        // is not a failure signal, and encoded output does not need an app decoder.
        if (audioDetails.runtimeState() == AudioPlaybackDiagnostics.RuntimeState.ACTIVE) {
            return switch (audioDetails.outputMode()) {
                case PASSTHROUGH -> "音频直通，无需 App 解码器";
                case COMPRESSED_DIRECT -> "音频压缩直出，由音频设备解码";
                case OFFLOAD -> "音频卸载，由音频设备解码";
                case PCM -> TextUtils.isEmpty(audioDetails.decoderName())
                        ? "PCM 输出已建立，解码器名称未上报" : "正常";
                case UNKNOWN -> "音频输出状态待确认";
            };
        }
        if (player.isExo() && audioTrack.hasTracks() && snapshot.audioFormat() == null) return "正在等待音频轨信息";
        if (audioTrack.hasTracks() || audioDetails.runtimeState() == AudioPlaybackDiagnostics.RuntimeState.PENDING) return "音频输出状态待确认";
        return "正常";
    }

    /**
     * The verdict for a video track no renderer can decode. The support code is spelled out the
     * way the audio rows and the video summary row already do it, and an encrypted track does not
     * get the "switch to software decode" advice: no decoder choice fixes a DRM rejection.
     */
    private String videoDiagnosis(PlayerManager player, VideoTrackState videoTrack) {
        String support = supportText(videoTrack.support());
        if (videoTrack.support() == C.FORMAT_UNSUPPORTED_DRM) return "视频轨" + support + "，加密轨请更换资源";
        return player.isHardDecode()
                ? "视频轨无可用解码器：" + support + "，请切软解或更换资源"
                : "视频轨无可用解码器：" + support + "，请更换资源";
    }

    private String getSystemGpuText() {
        GpuLoadMonitor.Snapshot system = GpuLoadMonitor.process().snapshot();
        if (!system.available()) return "";
        return join(" / ",
                String.format(Locale.US, "当前 %.0f%%", system.percent()),
                String.format(Locale.US, "峰值 %.0f%%", system.peakPercent()),
                formatGpuFrequency(system.frequencyHz()));
    }

    private String getCpuText(PlaybackPanelResourceMonitor.Snapshot snapshot) {
        if (snapshot == null || !snapshot.cpuAvailable()) return "";
        return String.format(Locale.US,
                "App 当前 %.0f%% / 10秒 %.0f%% / 峰值 %.0f%%",
                snapshot.cpuPercent(), snapshot.cpuAveragePercent(),
                snapshot.cpuPeakPercent());
    }

    private String getMemoryText(PlaybackPanelResourceMonitor.Snapshot snapshot) {
        if (snapshot == null || !snapshot.memoryAvailable()) return "";
        String javaHeap = snapshot.javaHeapUsedBytes() < 0 ? ""
                : "Java " + formatMemoryBytes(snapshot.javaHeapUsedBytes())
                + (snapshot.javaHeapLimitBytes() >= 0
                ? "/" + formatMemoryBytes(snapshot.javaHeapLimitBytes()) : "");
        String system = snapshot.systemAvailableBytes() < 0 ? ""
                : "系统可用 " + formatMemoryBytes(snapshot.systemAvailableBytes())
                + (snapshot.systemTotalBytes() >= 0
                ? "/" + formatMemoryBytes(snapshot.systemTotalBytes()) : "");
        return join(" / ",
                snapshot.pssBytes() >= 0
                        ? "PSS " + formatMemoryBytes(snapshot.pssBytes()) : "",
                snapshot.graphicsPssBytes() > 0
                        ? "图形 " + formatMemoryBytes(snapshot.graphicsPssBytes()) : "",
                javaHeap,
                snapshot.nativeHeapBytes() >= 0
                        ? "Native " + formatMemoryBytes(snapshot.nativeHeapBytes()) : "",
                system);
    }

    private String formatGpuFrequency(long frequencyHz) {
        if (frequencyHz <= 0) return "";
        if (frequencyHz >= 1_000_000_000L) {
            return String.format(Locale.US, "%.2fGHz", frequencyHz / 1_000_000_000.0);
        }
        return String.format(Locale.US, "%.0fMHz", frequencyHz / 1_000_000.0);
    }

    private String formatMemoryBytes(long bytes) {
        if (bytes < 0) return "";
        double mib = bytes / (1024.0 * 1024.0);
        if (mib >= 1024) return String.format(Locale.US, "%.1fGB", mib / 1024.0);
        if (mib >= 100) return String.format(Locale.US, "%.0fMB", mib);
        return String.format(Locale.US, "%.1fMB", mib);
    }

    private void startDiagnosticsSampling(PlayerManager player) {
        if (diagnosticsSamplingPlayer != null
                && diagnosticsSamplingPlayer != player) {
            diagnosticsSamplingPlayer.setGpuLoadDiagnosticsEnabled(false);
        }
        diagnosticsSamplingPlayer = player;
        player.setGpuLoadDiagnosticsEnabled(true);
        if (!resourceMonitor.isActive()) resourceMonitor.start();
        GpuLoadMonitor.process().start();
    }

    private void stopDiagnosticsSampling() {
        if (diagnosticsSamplingPlayer != null) {
            diagnosticsSamplingPlayer.setGpuLoadDiagnosticsEnabled(false);
            diagnosticsSamplingPlayer = null;
        }
        resourceMonitor.stop();
        GpuLoadMonitor.process().stop();
    }

    private String getErrorText(PlayerManager player, PlaybackAnalyticsListener.Snapshot snapshot) {
        String raw = join(" ", snapshot.errorCode(), shortText(snapshot.errorMessage(), 72));
        String decoder = TextUtils.isEmpty(snapshot.errorDecoderName()) ? "" : "decoder " + snapshot.errorDecoderName();
        String diagnostic = TextUtils.isEmpty(snapshot.errorDiagnosticInfo()) ? "" : "diagnostic " + snapshot.errorDiagnosticInfo();
        String secure = snapshot.errorSecureDecoderRequired() ? "secure required" : "";
        String cause = TextUtils.isEmpty(snapshot.errorCause()) ? "" : "cause " + shortText(snapshot.errorCause(), 72);
        String explanation = getErrorExplanation(player, snapshot);
        return join(" / ", raw, decoder, diagnostic, secure, cause, explanation);
    }

    private String getErrorExplanation(PlayerManager player, PlaybackAnalyticsListener.Snapshot snapshot) {
        if (isDecodeError(snapshot) && player.isHardDecode()) return "中文说明：硬解失败，设备硬件解码器可能不支持当前视频规格";
        if (isDecodeError(snapshot)) return "中文说明：软解/解码流程失败，请尝试切回硬解或更换资源";
        return "";
    }

    private String getSoftDecodeTuneText(PlayerManager player) {
        // The hard-decode profile still falls back to the FFmpeg renderer for codecs
        // MediaCodec refuses, and that fallback now gets load shedding too. Hiding the status
        // whenever the profile says hardware would conceal it in exactly the case where the
        // user needs to confirm it is active.
        if (player.isHardDecode() && !player.isHardProfileRunningSoftware()) return "";
        if (player.isIjk()) {
            // Report what IJK actually applied. It forces TuneMode.OFF in the hard-decode
            // profile even with a mode configured, so claiming shedding is active whenever
            // this row is reachable would be false exactly in the fallback case.
            IjkDecodePressurePolicy.TuneMode mode = player.getAppliedIjkTuneMode();
            if (mode == null || mode == IjkDecodePressurePolicy.TuneMode.OFF) return "软解降负载 关";
            return "软解降负载 IJK跳帧/滤波 " + mode.label();
        }
        if (player.isMpv()) return "软解降负载 MPV hwdec=no";
        return PlaybackPerformanceSetting.isSoftVideoTuneEnabled() ? "软解降负载 EXO滤波/低分辨" : "软解降负载 关";
    }

    private boolean isDecodeError(PlaybackAnalyticsListener.Snapshot snapshot) {
        String code = snapshot.errorCode();
        return "ERROR_CODE_DECODER_INIT_FAILED".equals(code) || "ERROR_CODE_DECODER_QUERY_FAILED".equals(code) || "ERROR_CODE_DECODING_FAILED".equals(code);
    }

    private String summarizeVideo(Format format, PlayerManager player,
                                  String decoder,
                                  VideoTrackState videoTrack,
                                  PlayerEngine.VideoPlaybackDetails details) {
        if (videoTrack.hasTracks()) format = mergeFormat(format, videoTrack.format());
        String size = getSize(format, player);
        String fps = getFrameRate(format, player);
        String bitrate = getBitrate(format, player);
        boolean dolbyVision = details != null
                && details.hasDolbyVisionSource();
        String formatName = dolbyVision
                ? DolbyVisionFormatLabel.formatName(details)
                : getVideoCodecName(format);
        String codecValue = dolbyVision
                ? DolbyVisionFormatLabel.codecText(details)
                : format == null ? "" : format.codecs;
        String codec = TextUtils.isEmpty(codecValue)
                ? "codec -" : "codec " + codecValue;
        String color = getColor(format).replace("color ", "色彩 ");
        String support = videoTrack.hasTracks() && !videoTrack.isHandled() ? supportText(videoTrack.support()) : "";
        String decode = "decoder " + emptyDash(decoderText(player, decoder));
        return join(" / ",
                "格式 " + emptyDash(formatName),
                "分辨率 " + emptyDash(size),
                "帧率 " + emptyDash(fps),
                "码率 " + emptyDash(bitrate),
                codec,
                TextUtils.isEmpty(color) ? "色彩 -" : color,
                decode,
                support,
                videoTrack.supportSummary());
    }

    private Format mergeFormat(Format primary, Format fallback) {
        if (primary == null) return fallback;
        if (fallback == null) return primary;
        Format.Builder builder = primary.buildUpon();
        if (TextUtils.isEmpty(primary.sampleMimeType) && !TextUtils.isEmpty(fallback.sampleMimeType)) builder.setSampleMimeType(fallback.sampleMimeType);
        if (TextUtils.isEmpty(primary.codecs) && !TextUtils.isEmpty(fallback.codecs)) builder.setCodecs(fallback.codecs);
        if (primary.width <= 0 && fallback.width > 0) builder.setWidth(fallback.width);
        if (primary.height <= 0 && fallback.height > 0) builder.setHeight(fallback.height);
        if (primary.frameRate <= 0 && fallback.frameRate > 0) builder.setFrameRate(fallback.frameRate);
        if (formatBitrateValue(primary) <= 0 && formatBitrateValue(fallback) > 0) builder.setAverageBitrate(formatBitrateValue(fallback));
        if (primary.colorInfo == null && fallback.colorInfo != null) builder.setColorInfo(fallback.colorInfo);
        return builder.build();
    }

    private String summarizeAudio(Format format, AudioTrackState audioTrack, String decoder) {
        if (format == null) {
            if (!audioTrack.hasTracks()) return "未发现音轨";
            return join(" / ", summarizeAudioFormat(audioTrack.format()), supportText(audioTrack.support()), audioTrack.supportSummary(), audioTrack.selected() ? "已选中" : "未选中");
        }
        String support = audioTrack.hasTracks() && !audioTrack.isHandled() ? supportText(audioTrack.support()) : "";
        return join(" / ", join(" ", summarizeAudioFormat(format), getBitrate(format)), TextUtils.isEmpty(audioDecoderText(decoder)) ? "" : "dec " + audioDecoderText(decoder), support);
    }

    private String decoderText(PlayerManager player, String decoder) {
        if (!TextUtils.isEmpty(decoder)) return decoder;
        if (player == null) return "";
        if (player.isMpv()) {
            String hwdec = player.getVideoPlaybackDetails().hwdecCurrent();
            if (!TextUtils.isEmpty(hwdec)) return "MPV " + hwdec;
            return player.isHardDecode() ? "MPV mediacodec" : "MPV ffmpeg";
        }
        if (player.isIjk()) return player.isHardDecode() ? "IJK mediacodec" : "IJK ffmpeg";
        return "";
    }

    private String audioDecoderText(String decoder) {
        if (!TextUtils.isEmpty(decoder)) return decoder;
        return "";
    }

    private String summarizeAudioFormat(Format format) {
        if (format == null) return "";
        String channels = format.channelCount <= 0 ? "" : format.channelCount + "ch";
        String sampleRate = format.sampleRate <= 0 ? "" : format.sampleRate % 1000 == 0 ? (format.sampleRate / 1000) + "kHz" : bitrateFormat.format(format.sampleRate / 1000f) + "kHz";
        return join(" ", getAudioMime(format), channels, sampleRate, TextUtils.isEmpty(format.language) ? "" : format.language);
    }

    private AudioTrackState getAudioTrackState(PlayerManager player) {
        if (player == null) return AudioTrackState.empty();
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null) return AudioTrackState.empty();
        AudioTrackCandidate selected = null;
        AudioTrackCandidate handled = null;
        AudioTrackCandidate unsupported = null;
        AudioTrackCandidate first = null;
        int total = 0;
        int supported = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                total++;
                int support = group.getTrackSupport(i);
                boolean isSelected = group.isTrackSelected(i);
                if (support == C.FORMAT_HANDLED) supported++;
                AudioTrackCandidate candidate = new AudioTrackCandidate(group.getTrackFormat(i), support, isSelected);
                if (first == null) first = candidate;
                if (isSelected) selected = candidate;
                if (handled == null && support == C.FORMAT_HANDLED) handled = candidate;
                if (unsupported == null && isUnsupportedSupport(support)) unsupported = candidate;
            }
        }
        AudioTrackCandidate candidate = selected != null ? selected : handled != null ? handled : unsupported != null ? unsupported : first;
        return candidate == null ? new AudioTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, total, supported) : new AudioTrackState(candidate.format(), candidate.support(), candidate.selected(), total, supported);
    }

    private VideoTrackState getVideoTrackState(PlayerManager player) {
        if (player == null) return VideoTrackState.empty();
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null) return VideoTrackState.empty();
        VideoTrackCandidate selected = null;
        VideoTrackCandidate handled = null;
        VideoTrackCandidate exceeds = null;
        VideoTrackCandidate unsupported = null;
        VideoTrackCandidate first = null;
        int total = 0;
        int supported = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                total++;
                int support = group.getTrackSupport(i);
                boolean isSelected = group.isTrackSelected(i);
                if (support == C.FORMAT_HANDLED) supported++;
                VideoTrackCandidate candidate = new VideoTrackCandidate(group.getTrackFormat(i), support, isSelected);
                if (first == null) first = candidate;
                if (isSelected) selected = candidate;
                if (handled == null && support == C.FORMAT_HANDLED) handled = candidate;
                if (exceeds == null && support == C.FORMAT_EXCEEDS_CAPABILITIES) exceeds = candidate;
                if (unsupported == null && isUnsupportedSupport(support)) unsupported = candidate;
            }
        }
        VideoTrackCandidate candidate = selected != null ? selected : handled != null ? handled : exceeds != null ? exceeds : unsupported != null ? unsupported : first;
        return candidate == null ? new VideoTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, total, supported) : new VideoTrackState(candidate.format(), candidate.support(), candidate.selected(), total, supported);
    }

    private boolean isUnsupportedSupport(int support) {
        return support == C.FORMAT_UNSUPPORTED_TYPE || support == C.FORMAT_UNSUPPORTED_SUBTYPE || support == C.FORMAT_UNSUPPORTED_DRM;
    }

    private String supportText(int support) {
        return switch (support) {
            case C.FORMAT_HANDLED -> "支持";
            case C.FORMAT_EXCEEDS_CAPABILITIES -> "超出设备声明能力: EXCEEDS_CAPABILITIES，不应判定为可硬解";
            case C.FORMAT_UNSUPPORTED_DRM -> "不支持: NO_UNSUPPORTED_DRM";
            case C.FORMAT_UNSUPPORTED_SUBTYPE -> "不支持: NO_UNSUPPORTED_SUBTYPE";
            case C.FORMAT_UNSUPPORTED_TYPE -> "不支持: NO_UNSUPPORTED_TYPE";
            default -> "支持状态 " + support;
        };
    }

    private String getSize(Format format, PlayerManager player) {
        int width = format == null || format.width <= 0 ? player.getVideoWidth() : format.width;
        int height = format == null || format.height <= 0 ? player.getVideoHeight() : format.height;
        return width <= 0 || height <= 0 ? "" : width + "x" + height;
    }

    private String getFrameRate(Format format) {
        if (format == null || format.frameRate <= 0) return "";
        return frameFormat.format(format.frameRate) + "fps";
    }

    private String getFrameRate(Format format, PlayerManager player) {
        String declared = getFrameRate(format);
        if (!TextUtils.isEmpty(declared) || player == null || !player.isExo()) return declared;
        PlaybackAnalyticsListener.DisplayFrameRateEstimate estimate = PlaybackAnalyticsListener.getDisplayFrameRateEstimate();
        return estimate.frameRate() <= 0 ? "" : frameFormat.format(estimate.frameRate()) + "fps";
    }

    private String getBitrate(Format format) {
        return format == null ? "" : formatBitrate(formatBitrateValue(format));
    }

    private String getBitrate(Format format, PlayerManager player) {
        String declared = getBitrate(format);
        if (!TextUtils.isEmpty(declared) || player == null || !player.isExo()) return declared;
        PlaybackAnalyticsListener.DisplayMediaBitrateEstimate estimate = PlaybackAnalyticsListener.getDisplayMediaBitrateEstimate(format);
        return estimate.bitrateBitsPerSecond() <= 0 ? "" : formatBitrate(estimate.bitrateBitsPerSecond());
    }

    private String getBandwidthEstimateText(PlaybackAnalyticsListener.Snapshot snapshot) {
        if (snapshot.bandwidthEstimate() > 0) return formatBitrate(snapshot.bandwidthEstimate());
        long realtimeEstimate = lastSpeedKBps * 1024L * 8L;
        return realtimeEstimate > 0 ? formatBitrate(realtimeEstimate) : "";
    }

    private String formatSignedBitrate(long bitsPerSecond) {
        return (bitsPerSecond >= 0 ? "+" : "-") + formatBitrate(Math.abs(bitsPerSecond));
    }

    private String summarizeFrameTiming() {
        com.fongmi.android.tv.player.exo.ExoFrameTimingMetrics.Snapshot timing = PlaybackAnalyticsListener.getFrameTimingSnapshot();
        com.fongmi.android.tv.player.exo.ExoFrameSchedulingExperimentMetrics.Snapshot experiment = PlaybackAnalyticsListener.getFrameSchedulingExperimentSnapshot();
        String unit = experiment.experimentApplied()
                ? "帧调度A/B " + experiment.earlySchedulingThresholdUs() / 1000
                + "ms / 耗时推进"
                + (experiment.durationToProgressRequested() ? "开" : "关")
                : "";
        if (timing.frameCount() <= 0 && timing.releaseFrameCount() <= 0 && timing.codecErrorCount() <= 0) return unit;
        if (timing.codecErrorCount() > 0) return join(" / ", unit, "解码错误 " + timing.codecErrorCount());
        if (timing.lateReleaseFrameCount() > 0) {
            return join(" / ", unit, "释放滞后 " + timing.lateReleaseFrameCount() + " 帧 / 最大延迟 " + bitrateFormat.format(timing.maxLateReleaseUs() / 1000f) + "ms");
        }
        if (timing.lateBatchCount() > 0) return join(" / ", unit, "调度滞后 " + timing.lateBatchCount() + " 批");
        if (timing.releaseJitterSampleCount() > 0 && timing.averageReleaseJitterUs() >= 5_000) {
            return join(" / ", unit, "释放抖动 " + bitrateFormat.format(timing.averageReleaseJitterUs() / 1000f) + "ms");
        }
        return unit;
    }

    private String getColor(Format format) {
        if (format == null || format.colorInfo == null) return "";
        String color = format.colorInfo.toLogString();
        if (TextUtils.isEmpty(color)) return "";
        color = color.replace("Limited range", "Limited").replace("Full range", "Full").replace("SMPTE 170M", "SMPTE170M");
        return "color " + join(" ", outputHdrName(format), color);
    }

    private String outputHdrName(Format format) {
        if (format == null || format.colorInfo == null) return "";
        if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084) return "HDR10";
        if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) return "HLG";
        if (androidx.media3.common.ColorInfo.isTransferHdr(format.colorInfo)) return "HDR";
        if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_SDR
                || format.colorInfo.colorTransfer == C.COLOR_TRANSFER_SRGB
                || format.colorInfo.colorTransfer == C.COLOR_TRANSFER_LINEAR) return "SDR";
        return "";
    }

    private long getMediaBitrate(Format video, Format audio) {
        long bitrate = 0;
        if (video != null && formatBitrateValue(video) > 0) bitrate += formatBitrateValue(video);
        if (audio != null && formatBitrateValue(audio) > 0) bitrate += formatBitrateValue(audio);
        return bitrate;
    }

    private int formatBitrateValue(Format format) {
        if (format == null) return 0;
        if (format.bitrate > 0) return format.bitrate;
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.peakBitrate > 0) return format.peakBitrate;
        return 0;
    }

    private String getMime(Format format) {
        if (format == null) return "";
        if (!TextUtils.isEmpty(format.sampleMimeType)) {
            int index = format.sampleMimeType.indexOf('/');
            return index >= 0 && index + 1 < format.sampleMimeType.length() ? format.sampleMimeType.substring(index + 1) : format.sampleMimeType;
        }
        return TextUtils.isEmpty(format.codecs) ? "" : format.codecs;
    }

    private String getVideoCodecName(Format format) {
        if (format == null) return "";
        String mime = TextUtils.isEmpty(format.sampleMimeType) ? "" : format.sampleMimeType.toLowerCase(Locale.ROOT);
        String codecs = TextUtils.isEmpty(format.codecs) ? "" : format.codecs.toLowerCase(Locale.ROOT);
        if ("video/dolby-vision".equals(mime) || codecs.startsWith("dvhe") || codecs.startsWith("dvh1")) return "Dolby Vision";
        if (MimeTypes.VIDEO_H265.equals(mime) || codecs.startsWith("hvc1") || codecs.startsWith("hev1")) return "H.265 / HEVC";
        if (MimeTypes.VIDEO_H264.equals(mime) || codecs.startsWith("avc1") || codecs.startsWith("avc3")) return "H.264 / AVC";
        if (MimeTypes.VIDEO_AV1.equals(mime) || codecs.startsWith("av01")) return "AV1";
        if (MimeTypes.VIDEO_VP9.equals(mime) || codecs.startsWith("vp09")) return "VP9";
        if (MimeTypes.VIDEO_VP8.equals(mime) || codecs.startsWith("vp08")) return "VP8";
        if (MimeTypes.VIDEO_MPEG2.equals(mime)) return "MPEG-2";
        if (MimeTypes.VIDEO_MP4V.equals(mime)) return "MPEG-4";
        return getMime(format);
    }

    private String getAudioMime(Format format) {
        if (format == null) return "";
        String mime = format.sampleMimeType;
        if (isCodec(format, MimeTypes.CODEC_DTS_HD_MA_X_IMAX)) return "DTS:X IMAX";
        if (isCodec(format, MimeTypes.CODEC_DTS_HD_MA_X)) return "DTS:X";
        if (MimeTypes.AUDIO_DTS_HD_MA.equals(mime) || MimeTypes.AUDIO_MEDIA3_DTS_HD_MA_CORELESS.equals(mime)) return "DTS-HD MA";
        if (MimeTypes.AUDIO_DTS_EXPRESS.equals(mime)) return "DTS-HD LBR";
        if (MimeTypes.AUDIO_DTS_UHD_P2.equals(mime)) return "DTS-UHD P2";
        if (MimeTypes.AUDIO_DTS_HD.equals(mime)) return "DTS-HD";
        if (MimeTypes.AUDIO_DTS.equals(mime)) return "DTS";
        if (MimeTypes.AUDIO_TRUEHD.equals(mime)) return "TrueHD";
        if (MimeTypes.AUDIO_E_AC3_JOC.equals(mime)) return "E-AC3 JOC";
        if (MimeTypes.AUDIO_E_AC3.equals(mime)) return "E-AC3";
        if (MimeTypes.AUDIO_AC3.equals(mime)) return "AC3";
        if (MimeTypes.AUDIO_AAC.equals(mime)) return "AAC";
        if (MimeTypes.AUDIO_FLAC.equals(mime)) return "FLAC";
        if (MimeTypes.AUDIO_MPEG.equals(mime)) return "MP3";
        if (MimeTypes.AUDIO_OPUS.equals(mime)) return "Opus";
        if (MimeTypes.AUDIO_AMR.equals(mime) || MimeTypes.AUDIO_AMR_NB.equals(mime)) return "AMR-NB";
        if (MimeTypes.AUDIO_AMR_WB.equals(mime)) return "AMR-WB";
        return getMime(format);
    }

    private boolean isCodec(Format format, String codec) {
        return !TextUtils.isEmpty(format.codecs) && format.codecs.contains(codec);
    }

    private String formatBitrate(long bitrate) {
        if (bitrate <= 0) return "";
        float mbps = bitrate / 1_000_000f;
        if (mbps < 1) return Math.round(bitrate / 1000f) + "Kbps";
        return bitrateFormat.format(mbps) + "Mbps";
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "";
        float kb = bytes / 1024f;
        if (kb < 1024) return Math.round(kb) + "KB";
        return bitrateFormat.format(kb / 1024f) + "MB";
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "";
        if (ms >= 60_000) return Util.timeMs(ms);
        return bitrateFormat.format(ms / 1000f) + " s";
    }

    private String getDisplayRefreshText() {
        if (root.getDisplay() == null || root.getDisplay().getRefreshRate() <= 0) return "";
        return refreshFormat.format(root.getDisplay().getRefreshRate()) + " Hz";
    }

    private String getDeviceText() {
        String value = cachedDeviceText;
        if (value != null) return value;
        return cachedDeviceText = join(" / ",
                join(" ", emptyDash(Build.MANUFACTURER), emptyDash(Build.MODEL)),
                "device " + emptyDash(Build.DEVICE),
                "abi " + String.join(",", Build.SUPPORTED_ABIS));
    }

    private String getSystemText() {
        String value = cachedSystemText;
        if (value != null) return value;
        return cachedSystemText = join(" / ",
                "Android " + emptyDash(Build.VERSION.RELEASE),
                "SDK " + Build.VERSION.SDK_INT,
                "incremental " + emptyDash(Build.VERSION.INCREMENTAL));
    }

    private String getChipText() {
        return join(" / ",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? "soc " + emptyDash(Build.SOC_MANUFACTURER) + " " + emptyDash(Build.SOC_MODEL) : "",
                "hardware " + emptyDash(Build.HARDWARE),
                "board " + emptyDash(Build.BOARD));
    }

    private String getWebViewText() {
        String value = cachedWebViewText;
        if (value != null) return value;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return cachedWebViewText = "provider unavailable / SDK " + Build.VERSION.SDK_INT;
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info == null) return cachedWebViewText = "provider unavailable";
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
            return cachedWebViewText = join(" / ", info.versionName, info.packageName, "code " + code);
        } catch (Throwable e) {
            return cachedWebViewText = "provider query failed: " + e.getClass().getSimpleName();
        }
    }

    private String getDisplayText() {
        Display display = root.getDisplay();
        DisplayMetrics metrics = App.get().getResources().getDisplayMetrics();
        String appSize = metrics.widthPixels > 0 && metrics.heightPixels > 0 ? "app " + metrics.widthPixels + "x" + metrics.heightPixels : "";
        String refresh = getDisplayRefreshText();
        if (display == null) return join(" / ", appSize, TextUtils.isEmpty(refresh) ? "" : refresh);
        Display.Mode mode = display.getMode();
        String modeText = mode == null ? "" : Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight()) + "x" + Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight()) + "@" + refreshFormat.format(mode.getRefreshRate()) + "Hz";
        return join(" / ", appSize, modeText, TextUtils.isEmpty(refresh) ? "" : "current " + refresh, getDisplayModesText(display));
    }

    private String getDisplayModesText(Display display) {
        try {
            Display.Mode[] modes = display.getSupportedModes();
            if (modes == null || modes.length <= 1) return "";
            StringBuilder builder = new StringBuilder("modes ");
            int count = 0;
            for (Display.Mode mode : modes) {
                String hz = refreshFormat.format(mode.getRefreshRate());
                if (builder.toString().contains(hz + "Hz")) continue;
                if (count++ > 0) builder.append("/");
                builder.append(hz).append("Hz");
                if (count >= 6) break;
            }
            return builder.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getNetworkEnvironmentText() {
        return join(" / ", getActiveNetworkText(), getSystemProxyText(), getAppProxyText());
    }

    private String getActiveNetworkText() {
        try {
            ConnectivityManager manager = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "";
            NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
            if (caps == null) return "network unavailable";
            String type = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "WiFi" :
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" :
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Cellular" :
                                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN" : "Other";
            String validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "validated" : "not validated";
            String metered = manager.isActiveNetworkMetered() ? "metered" : "unmetered";
            return join(" ", type, validated, metered);
        } catch (Throwable e) {
            return "network query failed";
        }
    }

    private String getSystemProxyText() {
        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");
        return TextUtils.isEmpty(host) ? "system proxy 关" : "system proxy " + host + (TextUtils.isEmpty(port) ? "" : ":" + port);
    }

    private String getAppProxyText() {
        if (!Setting.isShellProxy()) return "app proxy 关";
        String url = Setting.getShellProxyUrl();
        return "app proxy 开" + (TextUtils.isEmpty(url) ? "" : " " + shortText(url, 36));
    }

    private String getHevcDecoderText() {
        String value = cachedHevcDecoderText;
        if (value != null) return value;
        try {
            HevcDecoderSummary best = null;
            int count = 0;
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder() || !isHardwareCodec(info)) continue;
                HevcDecoderSummary summary = summarizeHevcDecoder(info);
                if (summary == null) continue;
                count++;
                if (best == null || summary.score() > best.score()) best = summary;
            }
            if (best == null) return cachedHevcDecoderText = "未发现硬件 HEVC decoder";
            return cachedHevcDecoderText = best.text() + (count > 1 ? " / decoders " + count : "");
        } catch (Throwable e) {
            return cachedHevcDecoderText = "query failed: " + e.getClass().getSimpleName();
        }
    }

    private HevcDecoderSummary summarizeHevcDecoder(MediaCodecInfo info) {
        try {
            MediaCodecInfo.VideoCapabilities caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).getVideoCapabilities();
            boolean uhd60 = supports(caps, 3840, 2160, 60);
            boolean uhd30 = supports(caps, 3840, 2160, 30);
            boolean qhd60 = supports(caps, 2560, 1440, 60);
            boolean fhd60 = supports(caps, 1920, 1080, 60);
            int score = (uhd60 ? 8 : 0) + (uhd30 ? 4 : 0) + (qhd60 ? 2 : 0) + (fhd60 ? 1 : 0);
            String bitrate = "";
            try {
                android.util.Range<Integer> range = caps.getBitrateRange();
                if (range != null && range.getUpper() > 0) bitrate = "bitrate<=" + formatBitrate(range.getUpper());
            } catch (Throwable ignored) {
            }
            String text = join(" / ",
                    "decoder " + info.getName(),
                    "4K60=" + yesNo(uhd60),
                    "4K30=" + yesNo(uhd30),
                    "1440p60=" + yesNo(qhd60),
                    "1080p60=" + yesNo(fhd60),
                    TextUtils.isEmpty(bitrate) ? "" : bitrate.replace("bitrate<=", "码率上限 "));
            return new HevcDecoderSummary(score, text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean supports(MediaCodecInfo.VideoCapabilities caps, int width, int height, double fps) {
        try {
            return caps.areSizeAndRateSupported(width, height, fps) || caps.areSizeAndRateSupported(height, width, fps);
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean isHardwareCodec(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated();
        String name = info.getName().toLowerCase(Locale.US);
        return !name.contains("google") && !name.contains("android") && !name.contains("ffmpeg") && !name.contains("software") && !name.startsWith("c2.android");
    }

    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private String summarizeSource(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String type = sourceType(scheme, path, url);
            String ext = extension(path);
            return join(" ", type, TextUtils.isEmpty(host) ? emptyDash(scheme) : scheme + "://" + host, ext);
        } catch (Throwable ignored) {
            return shortText(url, 80);
        }
    }

    private String sourceType(String scheme, String path, String url) {
        String lower = url.toLowerCase(Locale.US);
        if ("file".equals(scheme) || "content".equals(scheme)) return "local";
        PlaybackRoute route = PlaybackRoute.classify(url);
        if (route == PlaybackRoute.APP_LOCAL_SERVICE) return "app-local-service";
        if (route == PlaybackRoute.EXTERNAL_LOOPBACK_PROXY) return "external-local-endpoint";
        if (lower.contains(".m3u8")) return "hls";
        if (lower.contains(".mpd")) return "dash";
        if (lower.startsWith("rtsp")) return "rtsp";
        if (lower.startsWith("rtp")) return "rtp";
        if (path != null && path.contains(".")) return "file";
        return TextUtils.isEmpty(scheme) ? "unknown" : scheme;
    }

    private String extension(String path) {
        if (TextUtils.isEmpty(path)) return "";
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot + 1 >= path.length()) return "";
        String ext = path.substring(dot + 1);
        return ext.length() > 8 ? "" : ext;
    }

    private String stateName(int state) {
        return switch (state) {
            case androidx.media3.common.Player.STATE_IDLE -> "IDLE";
            case androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING";
            case androidx.media3.common.Player.STATE_READY -> "READY";
            case androidx.media3.common.Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private String stateText(int state) {
        return switch (state) {
            case androidx.media3.common.Player.STATE_IDLE -> "空闲(IDLE)";
            case androidx.media3.common.Player.STATE_BUFFERING -> "缓冲中(BUFFERING)";
            case androidx.media3.common.Player.STATE_READY -> "就绪(READY)";
            case androidx.media3.common.Player.STATE_ENDED -> "结束(ENDED)";
            default -> stateName(state);
        };
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

    private String row(String label, String value) {
        return label + "  " + (TextUtils.isEmpty(value) ? "-" : value);
    }

    private String switchText(boolean enabled) {
        return enabled ? "开" : "关";
    }

    private String emptyDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private int dp(int value) {
        return Math.round(value * App.get().getResources().getDisplayMetrics().density);
    }

    private String shortText(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "...";
    }

    private void resetSpeed() {
        speedMeter.reset();
        lastSpeedKBps = 0;
        lastSpeedText = "";
    }

    private record AudioTrackCandidate(Format format, int support, boolean selected) {
    }

    private record VideoTrackCandidate(Format format, int support, boolean selected) {
    }

    private record HevcDecoderSummary(int score, String text) {
    }

    private record DiagnosticsText(String main, String extra) {

        String all() {
            return TextUtils.isEmpty(extra) ? main : main + "\n" + extra;
        }
    }

    private record AudioTrackState(Format format, int support, boolean selected, int total, int supported) {

        static AudioTrackState empty() {
            return new AudioTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, 0, 0);
        }

        boolean hasTracks() {
            return total > 0;
        }

        boolean isHandled() {
            return support == C.FORMAT_HANDLED;
        }

        boolean isUnsupported() {
            return support == C.FORMAT_UNSUPPORTED_TYPE || support == C.FORMAT_UNSUPPORTED_SUBTYPE || support == C.FORMAT_UNSUPPORTED_DRM;
        }

        String supportSummary() {
            return total <= 1 ? "" : "音轨 " + supported + "/" + total + " 支持";
        }
    }

    private record VideoTrackState(Format format, int support, boolean selected, int total, int supported) {

        static VideoTrackState empty() {
            return new VideoTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, 0, 0);
        }

        boolean hasTracks() {
            return total > 0;
        }

        boolean isHandled() {
            return support == C.FORMAT_HANDLED;
        }

        String supportSummary() {
            return total <= 1 ? "" : "视频轨 " + supported + "/" + total + " 支持";
        }
    }
}
