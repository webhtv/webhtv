package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.cache.CacheCenter;
import com.fongmi.android.tv.cache.CacheModuleId;
import com.fongmi.android.tv.cache.CachePolicyStore;
import com.fongmi.android.tv.cache.PlaybackCacheBudget;
import com.fongmi.android.tv.player.exo.ass.AssFontSet;
import com.fongmi.android.tv.player.exo.ass.ExoAssSession;
import com.fongmi.android.tv.player.cache.DiskCacheCapacityPolicy;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MediaSourceFactory implements MediaSource.Factory {

    private static final String CONCAT_SOURCE_SEPARATOR = "***";
    private static final String CONCAT_SOURCE_SEPARATOR_REGEX = "\\*\\*\\*";
    private static final String CONCAT_DURATION_SEPARATOR = "|||";
    private static final String CONCAT_DURATION_SEPARATOR_REGEX = "\\|\\|\\|";
    private static final PriorityTaskManager PLAYBACK_PRIORITY_MANAGER = new PriorityTaskManager();
    private static final CacheCapacityState CACHE_CAPACITY_STATE = new CacheCapacityState();

    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private OkHttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    @Nullable private final ExoDolbyVisionPlaybackState dolbyVisionPlaybackState;
    @Nullable private final ExoAssSession assSession;

    public MediaSourceFactory() {
        this(null);
    }

    MediaSourceFactory(
            @Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState) {
        this(dolbyVisionPlaybackState, null);
    }

    MediaSourceFactory(@Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState,
            @Nullable ExoAssSession assSession) {
        this.dolbyVisionPlaybackState = dolbyVisionPlaybackState;
        this.assSession = assSession;
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory()).setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
    }

    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers) {
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(OkHttp.player());
        applyHeaders(factory, headers);
        DataSource.Factory upstream = new DefaultDataSource.Factory(App.get(), factory);
        DataSource.Factory recovered = new HttpEofRecoveryDataSource.Factory(upstream);
        return new PriorityTaskDataSource.Factory(recovered, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK_PRELOAD, true);
    }

    /** The two phases that decide which ceiling applies to the playback cache. */
    private enum CapacityPhase {
        /** A player session is open: the preload setting decides, so playback keeps its headroom. */
        PLAYBACK,
        /** Nothing plays: the total limit decides, so the cache converges once playback ends. */
        IDLE
    }

    /**
     * The cache every playback consumer uses, created for the playback phase.
     *
     * <p>Playback is the only reason a cache exists, so the no-argument form stays playback-phase;
     * the idle phase is requested explicitly wherever nothing plays.</p>
     */
    static synchronized Cache getCache() {
        return getCache(CapacityPhase.PLAYBACK);
    }

    private static synchronized Cache getCache(CapacityPhase phase) {
        if (cache != null) return cache;
        File dir = Path.exoCache();
        DiskCacheCapacityPolicy.Decision decision = resolveCapacity(dir, FileUtil.getDirectorySize(dir), phase);
        long capacityBytes = initialCapacityBytes(decision);
        Cache created = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(capacityBytes), getDatabaseProvider());
        cache = created;
        CACHE_CAPACITY_STATE.recordCreated(capacityBytes);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "created capacityBytes=%d phase=%s policy=%s existingBytes=%d availableBytes=%d reserveBytes=%d", capacityBytes, phase, decision.state(), decision.existingCacheBytes(), decision.availableStorageBytes(), decision.reserveBytes());
        return created;
    }

    public static synchronized void acquireCacheSession() {
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity(CapacityPhase.PLAYBACK);
        if (isReliable(decision) && CACHE_CAPACITY_STATE.canReleasePending()) rebuildCacheLocked("next-player-session", CapacityPhase.PLAYBACK);
        CACHE_CAPACITY_STATE.acquireSession();
    }

    /**
     * Converges the cache once the last player session ends.
     *
     * <p>This is where the total limit takes effect for the playback cache. Rebuilding with the
     * smaller idle capacity makes SimpleCache evict down to it while it loads its index, so the
     * shrink happens now rather than at the next write.</p>
     */
    public static synchronized void releaseCacheSession() {
        CACHE_CAPACITY_STATE.releaseSession();
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity(CapacityPhase.IDLE);
        if (isReliable(decision) && CACHE_CAPACITY_STATE.canReleasePending()) rebuildCacheLocked("last-player-release", CapacityPhase.IDLE);
    }

    public static synchronized boolean clearCacheIfIdle() {
        if (CACHE_CAPACITY_STATE.activeSessions() > 0) return false;
        if (!releaseCacheLocked("manual-cache-clear")) {
            if (cache != null) return false;
        }
        Path.clear(Path.exoCache());
        try {
            getCache(CapacityPhase.IDLE);
            return true;
        } catch (RuntimeException error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "manual-clear-rebuild-failed error=%s", error.getClass().getSimpleName());
            return false;
        }
    }

    private static StandaloneDatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    private static DiskCacheCapacityPolicy.Decision resolveCapacity(File dir, long existingCacheBytes, CapacityPhase phase) {
        FileUtil.StorageSpace storage = FileUtil.getStorageSpace(dir);
        return DiskCacheCapacityPolicy.resolve(storage.available(), desiredCapacityBytes(phase), existingCacheBytes, storage.availableBytes(), storage.totalBytes());
    }

    /**
     * The ceiling for the requested phase.
     *
     * <p>Playback answers to the preload setting so buffering, seeking and re-buffering keep the
     * headroom the user configured. The total limit answers for the idle phase instead, where it is
     * what makes the largest cache on the device converge as soon as playback ends.</p>
     */
    private static long desiredCapacityBytes(CapacityPhase phase) {
        return PlaybackCacheBudget.capacityBytes(
                phase == CapacityPhase.PLAYBACK,
                CachePolicyStore.getTotalLimitBytes(),
                playbackCeilingBytes(),
                CacheCenter.get().cachedOthersBytes(CacheModuleId.EXO));
    }

    /**
     * The preload ceiling, lowered by the playback limit the cache panel stores.
     *
     * <p>The panel row would otherwise persist a preference that no code reads: the capacity the
     * player computes is the only thing that bounds this cache. An unset limit reports zero and
     * leaves the preload setting deciding alone.</p>
     */
    private static long playbackCeilingBytes() {
        return PlaybackCacheBudget.ceilingBytes(
                PreloadSetting.getPreloadSizeBytes(PlayerSetting.EXO),
                CachePolicyStore.getLimit(CacheModuleId.EXO));
    }

    private static long initialCapacityBytes(DiskCacheCapacityPolicy.Decision decision) {
        if (!isReliable(decision)) return decision.existingCacheBytes();
        return decision.effectiveCapacityBytes();
    }

    static synchronized long getCacheCapacityBytes() {
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity(CapacityPhase.PLAYBACK);
        return cache == null ? initialCapacityBytes(decision) : CACHE_CAPACITY_STATE.actualCapacityBytes();
    }

    static synchronized long getPendingCacheCapacityBytes() {
        refreshPendingCacheCapacity(CapacityPhase.IDLE);
        return CACHE_CAPACITY_STATE.pendingCapacityBytes();
    }

    /**
     * The capacity the cache converges to once playback ends, for display.
     *
     * <p>Always answers with the idle figure: that is the number the panel promises, and it must
     * not depend on whether a player currently holds a larger playback-phase capacity. Never scans
     * the disk, so the settings panel can call it on the UI thread.</p>
     */
    public static synchronized long targetCapacityBytes() {
        return desiredCapacityBytes(CapacityPhase.IDLE);
    }

    /**
     * Applies a freshly configured limit without waiting for the next player release.
     *
     * <p>Refuses while any player session holds the cache: shrinking a SimpleCache a running
     * player is reading would cut the buffering headroom it was promised. The release path
     * converges later instead.</p>
     */
    public static synchronized boolean enforceCapacityNow() {
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity(CapacityPhase.IDLE);
        if (!isReliable(decision) || !CACHE_CAPACITY_STATE.canReleasePending()) return false;
        rebuildCacheLocked("limit-changed", CapacityPhase.IDLE);
        return true;
    }

    static synchronized ExoCacheWritePolicy.Decision getCacheWriteDecision() {
        DiskCacheCapacityPolicy.Decision capacity = refreshPendingCacheCapacity(CapacityPhase.PLAYBACK);
        long actualCapacityBytes = cache == null ? 0 : CACHE_CAPACITY_STATE.actualCapacityBytes();
        return ExoCacheWritePolicy.resolve(capacity, actualCapacityBytes);
    }

    private static DiskCacheCapacityPolicy.Decision refreshPendingCacheCapacity(CapacityPhase phase) {
        File dir = Path.exoCache();
        long existingCacheBytes = cache == null ? FileUtil.getDirectorySize(dir) : cache.getCacheSpace();
        DiskCacheCapacityPolicy.Decision decision = resolveCapacity(dir, existingCacheBytes, phase);
        if (isReliable(decision)) CACHE_CAPACITY_STATE.report(decision.effectiveCapacityBytes());
        return decision;
    }

    private static boolean isReliable(DiskCacheCapacityPolicy.Decision decision) {
        return decision.state() != DiskCacheCapacityPolicy.State.UNAVAILABLE;
    }

    private static void rebuildCacheLocked(String reason, CapacityPhase phase) {
        if (!releaseCacheLocked(reason)) return;
        try {
            getCache(phase);
        } catch (RuntimeException e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "rebuild-failed reason=%s phase=%s error=%s", reason, phase, e.getClass().getSimpleName());
        }
    }

    private static boolean releaseCacheLocked(String reason) {
        if (cache == null) return false;
        Cache releasing = cache;
        long actual = CACHE_CAPACITY_STATE.actualCapacityBytes();
        long pending = CACHE_CAPACITY_STATE.pendingCapacityBytes();
        try {
            releasing.release();
            cache = null;
            CACHE_CAPACITY_STATE.recordReleased();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "released reason=%s actualCapacityBytes=%d pendingCapacityBytes=%d activeSessions=%d", reason, actual, pending, CACHE_CAPACITY_STATE.activeSessions());
            return true;
        } catch (RuntimeException e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "release-failed reason=%s error=%s activeSessions=%d", reason, e.getClass().getSimpleName(), CACHE_CAPACITY_STATE.activeSessions());
            return false;
        }
    }

    static boolean isConcatenatingUrl(String url) {
        return url != null && url.contains(CONCAT_SOURCE_SEPARATOR) && url.contains(CONCAT_DURATION_SEPARATOR);
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        applyHeaders(getHttpDataSourceFactory(), ExoUtil.extractHeaders(mediaItem));
        String url = mediaItem.requestMetadata.mediaUri != null ? mediaItem.requestMetadata.mediaUri.toString() : "";
        AssFontSet fonts = assSession == null ? null : assSession.beginMediaFonts();
        if (isConcatenatingUrl(url)) return createConcatenatingMediaSource(mediaItem, url);
        if (fonts != null || mediaItem.localConfiguration != null && mediaItem.localConfiguration.tag instanceof com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context) {
            // Bind the collection to this source's extractors. Old loaders cannot populate a new video.
            // Container ASS needs its attachments even when no external subtitle was configured.
            return new DefaultMediaSourceFactory(getDataSourceFactory(), Media3DiagnosticBridge.extractors(buildExtractorsFactory(fonts), mediaItem))
                    .setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled())
                    .createMediaSource(mediaItem);
        }
        else return defaultMediaSourceFactory.createMediaSource(mediaItem);
    }

    private MediaSource createConcatenatingMediaSource(MediaItem mediaItem, String url) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(),
                Media3DiagnosticBridge.extractors(getExtractorsFactory(), mediaItem))
                .setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
        for (String split : url.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX);
            if (info.length >= 2) builder.add(sourceFactory.createMediaSource(mediaItem.buildUpon().setUri(UrlUtil.uri(info[0])).build()), Long.parseLong(info[1]));
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = buildExtractorsFactory(null);
        return extractorsFactory;
    }

    private ExtractorsFactory buildExtractorsFactory(@Nullable AssFontSet fonts) {
            ExtractorsFactory defaults = new DefaultExtractorsFactory()
                    .setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(
                            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
            ExtractorsFactory withApe = new ExtractorsFactory() {
                @Override
                public androidx.media3.extractor.Extractor[] createExtractors() {
                    return prependApe(defaults.createExtractors());
                }

                @Override
                public androidx.media3.extractor.Extractor[] createExtractors(
                        Uri uri, Map<String, List<String>> responseHeaders) {
                    return prependApe(defaults.createExtractors(uri, responseHeaders));
                }
            };
            return new DolbyVisionP81ExtractorsFactory(withApe, dolbyVisionPlaybackState, fonts);
    }

    private static androidx.media3.extractor.Extractor[] prependApe(
            androidx.media3.extractor.Extractor[] defaults) {
        androidx.media3.extractor.Extractor[] extractors =
                new androidx.media3.extractor.Extractor[defaults.length + 1];
        extractors[0] = new ApeExtractor();
        System.arraycopy(defaults, 0, extractors, 1, defaults.length);
        return extractors;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) {
            DataSource.Factory cacheDataSource = getCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()));
            DataSource.Factory trackedDataSource = new PlaybackBytePositionDataSource.Factory(cacheDataSource);
            dataSourceFactory = new PriorityTaskDataSource.Factory(trackedDataSource, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK, false);
        }
        return dataSourceFactory;
    }

    private CacheDataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        return new CacheDataSource.Factory()
                .setCache(getCache())
                .setUpstreamDataSourceFactory(new HttpEofRecoveryDataSource.Factory(upstreamFactory))
                .setCacheWriteDataSinkFactory(null)
                .setEventListener(PlaybackCacheMetrics.listener())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private OkHttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player());
        return httpDataSourceFactory;
    }

    private static void applyHeaders(OkHttpDataSource.Factory factory, Map<String, String> headers) {
        Map<String, String> sanitized = sanitizeHeaders(headers);
        String userAgent = removeUserAgentHeader(sanitized);
        factory.setUserAgent(userAgent).setDefaultRequestProperties(sanitized);
    }

    static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) return sanitized;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey().trim();
            if (key.isEmpty()) continue;
            sanitized.put(key, entry.getValue().trim());
        }
        return sanitized;
    }

    static String removeUserAgentHeader(Map<String, String> headers) {
        String userAgent = null;
        Iterator<Map.Entry<String, String>> iterator = headers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (!"User-Agent".equalsIgnoreCase(entry.getKey())) continue;
            String value = entry.getValue().trim();
            if (!value.isEmpty()) userAgent = value;
            iterator.remove();
        }
        return userAgent;
    }
}
