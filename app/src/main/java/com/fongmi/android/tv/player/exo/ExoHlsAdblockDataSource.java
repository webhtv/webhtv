package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.utils.HlsAdblockNotice;
import com.fongmi.android.tv.utils.HlsAdblockPipeline;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ExoHlsAdblockDataSource implements DataSource {

    private static final String TAG = "exo-hls-adblock";
    private final DataSource upstream;
    private byte[] manifest;
    private int position;

    ExoHlsAdblockDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        long upstreamLength = upstream.open(dataSpec);
        if (!isManifestRequest(dataSpec.uri.toString(), upstream.getResponseHeaders())) return upstreamLength;
        try {
            byte[] original = readAll();
            if (!looksLikePlaylist(original)) {
                // 实测（2026-10，本地包直播）：分片地址会顶着 .m3u8 后缀返回 TS 字节
                // （形如 /xxx.m3u8?ts=<base64 的 .ts>）。按后缀当播放列表改写会把整段分片
                // 吃掉，EXO 只剩一条轨、画面全黑。这里按原样透传。
                manifest = original;
                position = 0;
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "not-a-playlist bytes=%d url=%s", original.length, dataSpec.uri);
                return manifest.length;
            }
            String text = new String(original, StandardCharsets.UTF_8);
            List<HlsManifestCleaner.Rule> rules = List.of();
            boolean legacyFallback = false;
            if (Setting.isAdblock()) {
                rules = HlsRuleConfig.getRules();
                legacyFallback = HlsRuleConfig.isLegacyFallbackEnabled();
            }
            HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                    dataSpec.uri.toString(), text, rules, legacyFallback);
            manifest = outcome.manifest().getBytes(StandardCharsets.UTF_8);
            position = 0;
            recordAndNotify(dataSpec.uri, outcome);
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(TAG, "manifest bytes=%d rewritten=%d removed=%d structured=%s legacy=%s url=%s",
                        original.length, manifest.length, outcome.removedSegments(), outcome.structured(),
                        outcome.legacy(), dataSpec.uri);
            }
            return manifest.length;
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (manifest == null) return upstream.read(buffer, offset, length);
        if (position >= manifest.length) return C.RESULT_END_OF_INPUT;
        int count = Math.min(length, manifest.length - position);
        System.arraycopy(manifest, position, buffer, offset, count);
        position += count;
        return count;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        manifest = null;
        position = 0;
        upstream.close();
    }

    static boolean isManifestUrl(String url) {
        if (url == null) return false;
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        int end = url.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return url.substring(0, end).toLowerCase(Locale.ROOT).endsWith(".m3u8");
    }

    static boolean isManifestRequest(String url, Map<String, List<String>> responseHeaders) {
        Boolean byContentType = hlsContentType(responseHeaders);
        // 显式 CONTENT-TYPE 是权威证据：分片会以 .m3u8 后缀 + TS/二进制类型返回。
        if (byContentType != null) return byContentType;
        return isManifestUrl(url);
    }

    /** 只有真正以 {@code #EXTM3U} 开头的正文才按播放列表处理，其余一律当分片透传。 */
    static boolean looksLikePlaylist(byte[] body) {
        if (body == null) return false;
        int index = 0;
        if (body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB && (body[2] & 0xFF) == 0xBF) index = 3;
        while (index < body.length && (body[index] == ' ' || body[index] == '\r' || body[index] == '\n' || body[index] == '\t')) index++;
        return index + 7 <= body.length
                && body[index] == '#' && body[index + 1] == 'E' && body[index + 2] == 'X'
                && body[index + 3] == 'T' && body[index + 4] == 'M' && body[index + 5] == '3'
                && body[index + 6] == 'U';
    }

    @Nullable
    private static Boolean hlsContentType(Map<String, List<String>> responseHeaders) {
        if (responseHeaders == null) return null;
        Boolean result = null;
        for (Map.Entry<String, List<String>> header : responseHeaders.entrySet()) {
            if (header.getKey() == null || !"content-type".equalsIgnoreCase(header.getKey())) continue;
            for (String value : header.getValue()) {
                if (value == null) continue;
                if (isHlsContentType(value)) return Boolean.TRUE;
                if (isSegmentContentType(value)) result = Boolean.FALSE;
            }
        }
        return result;
    }

    private static boolean isSegmentContentType(String value) {
        String type = value.toLowerCase(Locale.ROOT);
        if (isHlsContentType(type)) return false;
        // application/octet-stream 不能当「分片」证据：不少静态服务器没配 m3u8 的 MIME，
        // 真播放列表也返回 octet-stream。把它留给 URL 后缀 + looksLikePlaylist 正文明判，
        // 既不影响分片透传，也不会让这类服务器的广告过滤整体失效。
        return type.contains("mp2t") || type.startsWith("video/") || type.startsWith("audio/");
    }

    private static boolean isHlsContentType(String value) {
        if (value == null) return false;
        String type = value.toLowerCase(Locale.ROOT);
        return type.contains("application/vnd.apple.mpegurl")
                || type.contains("application/x-mpegurl")
                || type.contains("audio/mpegurl")
                || type.contains("audio/x-mpegurl");
    }

    static Result cleanForTest(String url, String manifest) {
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .id("test-ad-path")
                .segmentUrlPatterns(List.of("(^|/)ad/"))
                .build();
        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                url, manifest, List.of(rule), true);
        return new Result(outcome.manifest(), outcome.structured() || outcome.legacy(), notice(outcome));
    }

    private byte[] readAll() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = upstream.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void recordAndNotify(Uri uri, HlsAdblockPipeline.Outcome outcome) {
        if (!outcome.structured() && !outcome.legacy()) return;
        long fallbackCount = outcome.legacy() ? Math.max(1, outcome.removedSegments()) : 0;
        AdBlockStatsStore.recordBlocks(uri.getHost(), "EXO", outcome.ruleCounts(), fallbackCount,
                uri.getHost(), outcome.removedDurationSec(), outcome.removedSegmentDetails());
        if (!HlsAdblockNotice.shouldNotify(uri.toString(), System.currentTimeMillis())) return;
        String message = notice(outcome);
        App.post(() -> Notify.show(message));
    }

    private static String notice(HlsAdblockPipeline.Outcome outcome) {
        int removed = outcome.removedSegments() > 0 ? outcome.removedSegments() : (outcome.legacy() ? 1 : 0);
        return HlsAdblockNotice.message(removed, outcome.removedDurationSec());
    }

    record Result(String manifest, boolean changed, String notice) {}

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;

        Factory(DataSource.Factory upstreamFactory) {
            this.upstreamFactory = upstreamFactory;
        }

        @Override
        public DataSource createDataSource() {
            return new ExoHlsAdblockDataSource(upstreamFactory.createDataSource());
        }
    }
}
