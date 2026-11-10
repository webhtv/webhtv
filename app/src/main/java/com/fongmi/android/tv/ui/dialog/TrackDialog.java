package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.ui.DefaultTrackNameProvider;
import androidx.media3.ui.TrackNameProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.DialogTrackBinding;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.exo.TrackUtil;
import com.fongmi.android.tv.service.AiSubtitleTranslationService;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleController;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationRequest;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationResult;
import com.fongmi.android.tv.ui.adapter.TrackAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackDialog extends BaseBottomSheetDialog implements TrackAdapter.OnClickListener, RealtimeSubtitleController.Listener {

    private static WeakReference<TrackDialog> activeAiDialog = new WeakReference<>(null);

    private final TrackNameProvider provider;
    private final TrackAdapter adapter;
    private DialogTrackBinding binding;
    private PlayerManager player;
    private Runnable searchAction;
    private boolean aiTranslating;
    private RealtimeSubtitleController.State realtimeState = RealtimeSubtitleController.State.OFF;
    private long aiStartedAt;
    private boolean secondarySubtitle;
    private int type;

    public static TrackDialog create() {
        return new TrackDialog();
    }

    public TrackDialog() {
        this.adapter = new TrackAdapter(this);
        this.provider = new DefaultTrackNameProvider(App.get().getResources());
    }

    public TrackDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public TrackDialog type(int type) {
        this.type = type;
        return this;
    }

    public TrackDialog search(Runnable searchAction) {
        this.searchAction = searchAction;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof TrackDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean hasChoose() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && player.isVod();
    }

    private boolean hasShooter() {
        // 射手走文件特征哈希，三类 URL 都能跑：
        //   1) file:// 本地路径
        //   2) http(s)://127.0.0.1 / localhost / 局域网 IP 代理（含远程推送过来的 9978/file）
        //   3) http(s)://CDN 直链视频（排除 m3u8/mpd/txt/html 等索引/清单/接口文件）
        if (!hasChoose()) return false;
        String url = player.getUrl();
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("file://")) return true;
        if (lower.startsWith("http://127.0.0.1")
                || lower.startsWith("http://localhost")
                || lower.matches("^https?://\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?/.*")) return true;
        // 在线 CDN 直链：URL path 以视频容器扩展名结尾，且不是 HLS/DASH/API
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            String path = stripQuery(lower);
            if (path == null) return false;
            int slash = path.lastIndexOf('/');
            String fileName = slash >= 0 ? path.substring(slash + 1) : path;
            String ext = extension(fileName);
            if (ext.isEmpty()) return false;
            switch (ext) {
                case "mp4": case "mkv": case "avi": case "mov": case "flv":
                case "webm": case "wmv": case "mpg": case "mpeg": case "m4v":
                case "m2ts": case "mts": case "3gp": case "rmvb": case "ts":
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }
 
    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        int cut = url.length();
        if (q >= 0) cut = Math.min(cut, q);
        if (h >= 0) cut = Math.min(cut, h);
        return url.substring(0, cut);
    }
 
    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1);
    }
 
    private boolean hasText() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT &&
               (player.haveTrack(type) || player.getSelectedSubtitleSub() != null);
    }

    private boolean hasAudio() {
        return type == C.TRACK_TYPE_AUDIO && player.haveTrack(type);
    }

    private boolean hasSearch() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && searchAction != null;
    }

    private boolean hasAi() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && player.isVod();
    }

    private boolean hasRealtimeAi() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && player.isExo();
    }

    private boolean hasAiRegenerate() {
        return hasAi() && isAiTranslatedSubtitle(player.getSelectedSubtitleSub());
    }

    private boolean hasSecondarySubtitle() {
        return type == C.TRACK_TYPE_TEXT && player.supportsSecondarySubtitle() && player.haveTrack(type);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogTrackBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        refreshTrackList();
        RealtimeSubtitleController.get().setListener(this);
    }

    @Override
    protected void initEvent() {
        binding.secondary.setOnClickListener(this::onSecondary);
        binding.offset.setOnClickListener(this::onOffset);
        binding.choose.setOnClickListener(this::onChoose);
        binding.search.setOnClickListener(this::onSearch);
        binding.ai.setOnClickListener(this::onAiTranslate);
        binding.realtimeAi.setOnClickListener(this::onRealtimeAi);
        binding.realtimeLanguage.setOnClickListener(this::onRealtimeLanguage);
        binding.aiRegenerate.setOnClickListener(this::onAiRegenerate);
        binding.subtitle.setOnClickListener(this::onSubtitle);
        binding.shooter.setOnClickListener(this::onShooter);
    }

    private void refreshTrackList() {
        adapter.replaceAll(getTrack());
        binding.title.setText(getTitle());
        binding.secondary.setText(secondarySubtitle ? R.string.play_track_primary_subtitle : R.string.play_track_secondary_subtitle);
        binding.secondary.setContentDescription(binding.secondary.getText());
        binding.secondary.setVisibility(hasSecondarySubtitle() ? View.VISIBLE : View.GONE);
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.offset.setVisibility(hasText() || hasAudio() ? View.VISIBLE : View.GONE);
        binding.choose.setVisibility(hasChoose() ? View.VISIBLE : View.GONE);
        binding.search.setVisibility(hasSearch() ? View.VISIBLE : View.GONE);
        binding.ai.setVisibility(hasAi() ? View.VISIBLE : View.GONE);
        binding.realtimeAi.setVisibility(hasRealtimeAi() ? View.VISIBLE : View.GONE);
        binding.realtimeLanguage.setVisibility(hasRealtimeAi() ? View.VISIBLE : View.GONE);
        updateRealtimeLanguage();
        updateRealtimeButton();
        binding.aiRegenerate.setVisibility(hasAiRegenerate() ? View.VISIBLE : View.GONE);
        binding.aiStatus.setVisibility(View.GONE);
        binding.subtitle.setVisibility(hasText() ? View.VISIBLE : View.GONE);
        binding.shooter.setVisibility(hasShooter() ? View.VISIBLE : View.GONE);
    }

    private String getTitle() {
        if (secondarySubtitle) return getString(R.string.select_secondary_subtitle_track);
        return ResUtil.getStringArray(R.array.select_track)[type - 1];
    }

    private void onSecondary(View view) {
        secondarySubtitle = !secondarySubtitle;
        refreshTrackList();
    }

    @Override
    public void onDestroyView() {
        if (activeAiDialog.get() == this) activeAiDialog.clear();
        RealtimeSubtitleController.get().setListener(null);
        super.onDestroyView();
        binding = null;
    }

    private void onOffset(View view) {
        OffsetDialog.create().player(player).type(type).show(requireActivity());
    }

    private void onChoose(View view) {
        stopRealtimeAi();
        FileChooser.from(launcher).show(new String[]{MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA, MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_TTML, "audio/*", "text/*", "application/octet-stream"});
        player.pause();
    }

    private void onShooter(View view) {
        Listener listener = (Listener) requireActivity();
        App.post(listener::onShooterClick, 100);
        dismiss();
    }
 
    private void onSubtitle(View view) {
        Listener listener = (Listener) requireActivity();
        App.post(listener::onSubtitleClick, 100);
        dismiss();
    }

    private List<Track> getTrack() {
        List<Track> items = new ArrayList<>();
        addTrack(items);
        addDisableTrack(items);
        return items;
    }

    private void addTrack(List<Track> items) {
        // 只取一次轨道快照：MPV/IJK 的快照由原生回调线程写入，分两次读可能拿到不同的轨道集合。
        Tracks tracks = player.getCurrentTracks();
        List<Tracks.Group> groups = tracks.getGroups();
        Format active = activeVideoFormat(tracks);
        for (int i = 0; i < groups.size(); i++) {
            Tracks.Group trackGroup = groups.get(i);
            if (trackGroup.getType() != type) continue;
            for (int j = 0; j < trackGroup.length; j++) {
                Format format = trackGroup.getTrackFormat(j);
                // One subtitle cannot occupy both roles (the same rule is enforced by MPV).
                if (type == C.TRACK_TYPE_TEXT && (secondarySubtitle
                        ? player.isPrimarySubtitleSelected(format) : player.isSecondarySubtitleSelected(format))) continue;
                String name = provider.getTrackName(format);
                Log.d("TrackDialog", "track type=" + type + " id=" + format.id + " label=" + format.label + " lang=" + format.language + " codec=" + format.codecs + " mime=" + format.sampleMimeType + " name=" + name);
                // Keep the player's native track id with the visible item. Runtime track
                // switching must target this stable id directly; the formatted description
                // is only persisted for restoring a preference on the next playback.
                Track item = new Track(type, name, PlayerHelper.describeFormat(format)).playerId(format.id);
                item.setSelected(type == C.TRACK_TYPE_TEXT
                        ? secondarySubtitle ? player.isSecondarySubtitleSelected(format) : player.isPrimarySubtitleSelected(format)
                        : trackGroup.isTrackSelected(j));
                items.add(item);
            }
        }
        addPendingSubtitleTrack(items);
    }

    private boolean isSelected(Tracks.Group trackGroup, int trackIndex, Format format, Format active) {
        if (secondarySubtitle) return player.isSecondarySubtitleSelected(format);
        // 自适应选轨会同时选中同组里的多条轨道，列表因此会高亮出多项。已经确认当前正在解码的
        // 那一条时只高亮它，其余情况仍沿用播放器的选中标记。
        // 这里按引用比对：uniqueActiveFormat 返回的就是轨道列表里的那个实例，而字段完全相同的
        // 两条轨道用 equals 会同时命中，反而又变成多项高亮。media3 的 TrackGroup.indexOf 同样按引用定位。
        if (active != null) return format == active;
        return trackGroup.isTrackSelected(trackIndex);
    }

    private Format activeVideoFormat(Tracks tracks) {
        if (secondarySubtitle || type != C.TRACK_TYPE_VIDEO) return null;
        return TrackUtil.uniqueActiveFormat(tracks, type, player.getVideoFormat());
    }

    private void addPendingSubtitleTrack(List<Track> items) {
        if (type != C.TRACK_TYPE_TEXT || !items.isEmpty()) return;
        Track item = selectedSubtitleFallbackTrack(player.getSelectedSubtitleSub(), type);
        if (item != null) items.add(item);
    }

    private void addDisableTrack(List<Track> items) {
        if (type != C.TRACK_TYPE_TEXT) return;
        Track item = Track.disabled(type, getString(secondarySubtitle ? R.string.play_track_disable_secondary_subtitle : R.string.play_track_disable_subtitle));
        item.setSelected(items.stream().noneMatch(Track::isSelected));
        items.add(0, item);
    }

    static SubtitleAsset subtitleAssetFrom(Sub sub) {
        if (sub == null) return null;
        String path = localSubtitlePath(sub.getUrl());
        if (TextUtils.isEmpty(path)) return null;
        File file = new File(path);
        if (!file.isFile()) return null;
        String name = TextUtils.isEmpty(sub.getName()) ? file.getName() : sub.getName();
        String format = TextUtils.isEmpty(sub.getFormat()) ? PlayerHelper.getSubtitleMimeType(name) : sub.getFormat();
        return new SubtitleAsset(path, path, name, sub.getLang(), format, sub.getFlag(), false, 0L);
    }

    static Track selectedSubtitleFallbackTrack(Sub sub, int type) {
        if (sub == null || TextUtils.isEmpty(sub.getUrl())) return null;
        String name = TextUtils.isEmpty(sub.getName()) ? subtitleNameFromUrl(sub.getUrl()) : sub.getName();
        String format = TextUtils.isEmpty(sub.getFormat()) ? PlayerHelper.getSubtitleMimeType(name) : sub.getFormat();
        Track item = new Track(type, name, subtitleTrackFormat(name, sub.getLang(), format));
        item.setSelected(true);
        return item;
    }

    private static String subtitleTrackFormat(String name, String language, String format) {
        StringJoiner joiner = new StringJoiner(",");
        if (!TextUtils.isEmpty(name)) joiner.add(name);
        if (!TextUtils.isEmpty(language)) joiner.add(language);
        if (!TextUtils.isEmpty(format)) joiner.add(format);
        return joiner.toString();
    }

    private static String subtitleNameFromUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int slash = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < url.length() ? url.substring(slash + 1) : url;
    }

    static boolean isLocalSrt(SubtitleAsset asset) {
        if (asset == null) return false;
        String path = asset.getLocalPath().toLowerCase();
        String mime = asset.getMimeType().toLowerCase();
        return path.endsWith(".srt") || mime.contains("subrip");
    }

    private static String localSubtitlePath(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        if (value.startsWith("file://")) return value.substring("file://".length());
        if (value.contains("://")) return "";
        return value;
    }

    private Sub sourceSubtitleForAi(boolean useCache) {
        Sub selected = player.getSelectedSubtitleSub();
        return useCache ? selected : originalSubtitleForRegenerate(selected, player.getSubtitleSubs());
    }

    static Sub originalSubtitleForRegenerate(Sub selected, List<Sub> subtitles) {
        if (!isAiTranslatedSubtitle(selected)) return selected;
        String sourceStem = aiSourceStem(selected);
        Sub firstLocalSrt = null;
        if (subtitles != null) {
            for (Sub sub : subtitles) {
                if (sub == null || isAiTranslatedSubtitle(sub)) continue;
                SubtitleAsset asset = subtitleAssetFrom(sub);
                if (asset == null || !isLocalSrt(asset)) continue;
                if (firstLocalSrt == null) firstLocalSrt = sub;
                if (!TextUtils.isEmpty(sourceStem) && TextUtils.equals(sourceStem, subtitleStem(sub))) return sub;
            }
        }
        return firstLocalSrt;
    }

    static boolean isAiTranslatedSubtitle(Sub sub) {
        if (sub == null) return false;
        String path = localSubtitlePath(sub.getUrl()).replace('\\', '/').toLowerCase(Locale.ROOT);
        String name = subtitleName(sub).toLowerCase(Locale.ROOT);
        return path.contains("/subtitle_translation/") || name.endsWith(".zh-hans.srt");
    }

    private static String aiSourceStem(Sub sub) {
        String name = subtitleName(sub);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zh-hans.srt")) return name.substring(0, name.length() - ".zh-Hans.srt".length());
        return "";
    }

    private static String subtitleStem(Sub sub) {
        String name = subtitleName(sub);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String subtitleName(Sub sub) {
        if (sub == null) return "";
        if (!TextUtils.isEmpty(sub.getName())) return sub.getName();
        return subtitleNameFromUrl(sub.getUrl());
    }

    private static boolean isAiSuccess(SubtitleTranslationResult result) {
        return result.getTranslatedAsset() != null && (result.getStatus() == SubtitleTranslationResult.Status.TRANSLATED || result.getStatus() == SubtitleTranslationResult.Status.CACHE_HIT);
    }

    private static String readableAiReason(String reason) {
        return TextUtils.isEmpty(reason) ? ResUtil.getString(R.string.subtitle_ai_unknown_reason) : reason;
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(1L, Math.round(millis / 1000.0));
        if (seconds < 60) return seconds + "s";
        return Math.max(1L, Math.round(seconds / 60.0)) + "m";
    }

    static final class AiTaskState {

        private static final Map<String, AiTaskState> STATES = new ConcurrentHashMap<>();

        private final boolean translating;
        private final long startedAt;
        private final String message;

        private AiTaskState(boolean translating, long startedAt, String message) {
            this.translating = translating;
            this.startedAt = startedAt;
            this.message = message == null ? "" : message;
        }

        static void start(String playbackKey, String message, long startedAt) {
            if (TextUtils.isEmpty(playbackKey)) return;
            STATES.put(playbackKey, new AiTaskState(true, startedAt, message));
        }

        static void update(String playbackKey, String message) {
            if (TextUtils.isEmpty(playbackKey)) return;
            AiTaskState current = STATES.get(playbackKey);
            if (current != null && !current.translating) return;
            long startedAt = current == null ? System.currentTimeMillis() : current.startedAt;
            STATES.put(playbackKey, new AiTaskState(true, startedAt, message));
        }

        static void finish(String playbackKey, String message) {
            if (TextUtils.isEmpty(playbackKey)) return;
            AiTaskState current = STATES.get(playbackKey);
            long startedAt = current == null ? System.currentTimeMillis() : current.startedAt;
            STATES.put(playbackKey, new AiTaskState(false, startedAt, message));
        }

        static AiTaskState get(String playbackKey) {
            return TextUtils.isEmpty(playbackKey) ? null : STATES.get(playbackKey);
        }

        static long startedAt(String playbackKey, long fallback) {
            AiTaskState state = get(playbackKey);
            return state == null ? fallback : state.startedAt;
        }

        static void clearForTest() {
            STATES.clear();
        }

        boolean isTranslating() {
            return translating;
        }

        long getStartedAt() {
            return startedAt;
        }

        String getMessage() {
            return message;
        }
    }

    @Override
    public void onItemClick(Track item) {
        if (type == C.TRACK_TYPE_TEXT) stopRealtimeAi();
        if (secondarySubtitle) {
            player.setSecondarySubtitleTrack(item);
            dismiss();
            return;
        }
        player.setTrack(Arrays.asList(item.key(player.getKey()).save()));
        dismiss();
    }

    @Override
    protected boolean transparent() {
        return !Util.isLeanback();
    }

    @Override
    protected boolean stableOverlay() {
        return !Util.isLeanback();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        stopRealtimeAi();
        player.setSub(Sub.from(FileChooser.getPathFromUri(result.getData().getData())));
        dismiss();
    });

    public interface Listener {

        void onSubtitleClick();
 
        default void onShooterClick() {}
    }
}
