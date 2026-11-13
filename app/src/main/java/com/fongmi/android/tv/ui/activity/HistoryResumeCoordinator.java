package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.history.HistorySourceResolver;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.TmdbSitePolicy;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.ui.novel.NovelRouter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class HistoryResumeCoordinator {

    private static final AtomicInteger REQUESTS = new AtomicInteger();

    private HistoryResumeCoordinator() {
    }

    /**
     * 与当前源历史完全同一套分流：详情页模式是独立 TMDB 详情且该历史有 TMDB 身份时进详情页，
     * 否则直接播放。退化原因必须留痕，不静默改变用户可见的入口。
     */
    public static void routeOriginHistory(Activity activity, History history) {
        String route = com.fongmi.android.tv.playback.origin.OriginHistoryRoute.describe(
                Setting.isTmdbDetailModeConfigured(),
                Setting.isStandaloneTmdbDetailMode(Setting.getDetailOpenMode()),
                TmdbSitePolicy.isEnabled(history.getSiteKey(), history.getVodId()));
        com.github.catvod.crawler.SpiderDebug.log("origin-route", "cid=%d key=%s mode=%d route=%s",
                history.getCid(), history.getKey(), Setting.getDetailOpenMode(), route);
        if (com.fongmi.android.tv.playback.origin.OriginHistoryRoute.isTmdbDetail(route)) {
            TmdbDetailActivity.startFromOriginHistory(activity, history);
            return;
        }
        VideoActivity.startFromOriginHistory(activity, history);
    }
    public static void open(Activity activity, History history) {
        if (activity == null || history == null) return;
        REQUESTS.incrementAndGet();
        if (!Setting.isGlobalHistoryEnabled() || history.isCurrentSourceAvailable()) {
            if (NovelRouter.openHistory(activity, history, VodConfig.getCid(),
                    () -> VideoActivity.startFromHistory(activity, history))) return;
            VideoActivity.startFromHistory(activity, history);
            return;
        }
        // 跨配置历史：原源在先，原源不可用或起播前失败才按全局历史续播设置回落。
        // 系统版本不是门槛：低版本同样尝试原源，只有“需要 WebView 嗅探”的那次解析才会明确报隔离不足。
        if (com.fongmi.android.tv.bean.Config.find(history.getCid()) != null) {
            routeOriginHistory(activity, history);
            return;
        }
        fallbackToCurrent(activity, history);
    }

    /** 原源不可用或起播前失败后的全局续播；不由已交付播放器的错误调用。 */
    public static void fallbackToCurrent(Activity activity, History history) {
        fallbackToCurrent(activity, history, null);
    }

    public static void fallbackToCurrent(Activity activity, History history, Runnable onLaunching) {
        if (activity == null || history == null || !isAlive(activity)) return;
        if (!Setting.isGlobalHistoryEnabled()) {
            Notify.show("全局历史续播已关闭，未进行当前源匹配");
            return;
        }
        REQUESTS.incrementAndGet();
        if (Setting.isGlobalHistorySearch()) {
            SearchActivity.directFromHistory(activity, history);
            if (onLaunching != null) onLaunching.run();
            return;
        }
        resolveAuto(activity, history, onLaunching);
    }

    public static void openSelected(Activity activity, int sourceCid, String sourceKey, int targetCid, Vod selected) {
        if (activity == null || selected == null) return;
        if (VodConfig.getCid() != targetCid) {
            Notify.show(R.string.history_config_changed);
            return;
        }
        History history = HistoryResumePayload.restore(sourceCid, sourceKey);
        if (history == null) {
            Notify.show(R.string.history_source_load_failed);
            return;
        }
        int request = REQUESTS.incrementAndGet();
        Dialog loading = showLoading(activity);
        Task.execute(() -> {
            HistorySourceResolver.Resolved resolved = HistorySourceResolver.resolveSelected(history, selected);
            App.post(() -> {
                dismiss(loading);
                if (!isCurrent(activity, request)) return;
                if (VodConfig.getCid() != targetCid) {
                    Notify.show(R.string.history_config_changed);
                    return;
                }
                History current = HistoryResumePayload.restore(sourceCid, sourceKey);
                if (current == null) {
                    Notify.show(R.string.history_record_missing);
                    return;
                }
                if (!isSameResumeVersion(history, current)) {
                    openSelected(activity, sourceCid, sourceKey, targetCid, selected);
                    return;
                }
                if (resolved == null) {
                    Notify.show(R.string.history_source_episode_missing);
                    return;
                }
                if (NovelRouter.openHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode(), targetCid,
                        () -> VideoActivity.startFromResolvedHistory(activity, current,
                                resolved.vod(), resolved.flag(), resolved.episode()))) return;
                VideoActivity.startFromResolvedHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode());
            });
        });
    }

    public static void openSearch(Activity activity, int sourceCid, String sourceKey, int targetCid, String keyword) {
        if (activity == null) return;
        if (VodConfig.getCid() != targetCid) {
            Notify.show(R.string.history_config_changed);
            return;
        }
        History history = HistoryResumePayload.restore(sourceCid, sourceKey);
        if (history == null) {
            Notify.show(R.string.history_source_load_failed);
            return;
        }
        REQUESTS.incrementAndGet();
        SearchActivity.directFromHistory(activity, history, keyword, targetCid);
    }

    private static void resolveAuto(Activity activity, History history) {
        resolveAuto(activity, history, null);
    }

    private static void resolveAuto(Activity activity, History history, Runnable onLaunching) {
        int request = REQUESTS.incrementAndGet();
        int targetCid = VodConfig.getCid();
        String resumePayload = HistoryResumePayload.encode(history);
        Dialog loading = showLoading(activity);
        Task.execute(() -> {
            HistorySourceResolver.Resolved resolved = HistorySourceResolver.resolveAuto(history);
            App.post(() -> {
                dismiss(loading);
                if (!isCurrent(activity, request)) return;
                if (VodConfig.getCid() != targetCid) {
                    Notify.show(R.string.history_config_changed);
                    return;
                }
                History current = HistoryResumePayload.restore(history.getCid(), resumePayload);
                if (current == null) {
                    Notify.show(R.string.history_record_missing);
                    return;
                }
                if (!isSameResumeVersion(history, current)) {
                    if (onLaunching == null) resolveAuto(activity, current);
                    else resolveAuto(activity, current, onLaunching);
                    return;
                }
                if (resolved == null) {
                    if (onLaunching == null) showSearchFallback(activity, current, targetCid);
                    else showSearchFallback(activity, current, targetCid, onLaunching);
                    return;
                }
                if (NovelRouter.openHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode(), targetCid,
                        () -> {
                            if (onLaunching != null) onLaunching.run();
                            VideoActivity.startFromResolvedHistory(activity, current,
                                    resolved.vod(), resolved.flag(), resolved.episode());
                        })) return;
                // 同 key/id 的 VideoActivity 是 singleTop，先结束原源页再启动正常当前源页。
                if (onLaunching != null) onLaunching.run();
                VideoActivity.startFromResolvedHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode());
            });
        });
    }

    private static Dialog showLoading(Activity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        int padding = ResUtil.dp2px(24);
        content.setPadding(padding, padding, padding, padding);

        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        int progressSize = ResUtil.dp2px(32);
        content.addView(progress, new LinearLayout.LayoutParams(progressSize, progressSize));

        TextView message = new TextView(activity);
        message.setText(R.string.history_source_searching);
        message.setTextColor(ContextCompat.getColor(activity, R.color.black_80));
        message.setTextSize(18);
        message.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.leftMargin = ResUtil.dp2px(16);
        content.addView(message, messageParams);

        AlertDialog loading = new WebHtvAlertDialogBuilder(activity, R.style.Theme_WebHTV_Dialog)
                .setView(content)
                .setCancelable(false)
                .create();
        loading.setCanceledOnTouchOutside(false);
        loading.show();
        LightDialog.apply(loading);
        return loading;
    }

    private static void showSearchFallback(Activity activity, History history, int targetCid) {
        showSearchFallback(activity, history, targetCid, null);
    }

    private static void showSearchFallback(Activity activity, History history, int targetCid, Runnable onLaunching) {
        AlertDialog dialog = new WebHtvAlertDialogBuilder(activity, R.style.Theme_WebHTV_Dialog)
                .setMessage(R.string.history_source_not_found)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.history_source_open_search, (ignored, which) -> {
                    openSearch(activity, history.getCid(), HistoryResumePayload.encode(history), targetCid, history.getVodName());
                    if (onLaunching != null && VodConfig.getCid() == targetCid && isAlive(activity)) onLaunching.run();
                })
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private static void dismiss(Dialog dialog) {
        if (dialog == null) return;
        try {
            dialog.dismiss();
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isCurrent(Activity activity, int request) {
        return request == REQUESTS.get() && isAlive(activity);
    }

    private static boolean isAlive(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    static boolean isSameResumeVersion(History expected, History current) {
        return expected != null && current != null
                && expected.getCid() == current.getCid()
                && expected.getTmdbId() == current.getTmdbId()
                && expected.getTmdbSeasonNumber() == current.getTmdbSeasonNumber()
                && expected.getTmdbEpisodeNumber() == current.getTmdbEpisodeNumber()
                && expected.getCreateTime() == current.getCreateTime()
                && expected.getPosition() == current.getPosition()
                && expected.getDuration() == current.getDuration()
                && Objects.equals(expected.getKey(), current.getKey())
                && Objects.equals(expected.getMediaType(), current.getMediaType())
                && Objects.equals(expected.getEpisodeUrl(), current.getEpisodeUrl())
                && Objects.equals(expected.getSourceBindingKey(), current.getSourceBindingKey());
    }
}
