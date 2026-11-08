package com.fongmi.android.tv.player.exo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

import com.github.catvod.crawler.SpiderDebug;

import java.io.IOException;

/**
 * 直播加载容错：把「播放列表取到空体/无法解析」当作可重试的临时失败。
 * <p>
 * 背景（2026-10 实测）：本地包（如 pg）的合并直播源经 jar 代理返回 {@code HTTP 200} 但 body 为空，
 * 或返回 {@code smart race exhausted} 这类非 m3u8 文本。media3 把它归为 manifest 解析失败，
 * 而 {@link DefaultLoadErrorHandlingPolicy} 只对 5xx/IO 重试、不重试解析类错误，于是 EXO 立刻
 * 进入失败态：画面全黑、0 KB/s。同期 IJK 仍在放缓冲段，所以用户看到「IJK 能播、EXO 不能」。
 * <p>
 * 这里给这类错误一个有限的退避重试窗口：端点抖动时 EXO 表现为「加载中」并自动恢复，而不是直接报错
 * 掉核。超过窗口仍失败才交给默认策略，让上层换线/换核。
 */
public final class LiveManifestRetryPolicy implements LoadErrorHandlingPolicy {

    private static final String TAG = "exo-live-retry";
    private static final int MAX_MANIFEST_RETRIES = 8;
    private static final long BASE_DELAY_MS = 400L;
    private static final long MAX_DELAY_MS = 3_000L;

    private final DefaultLoadErrorHandlingPolicy delegate = new DefaultLoadErrorHandlingPolicy();

    @NonNull
    @Override
    public FallbackSelection getFallbackSelectionFor(
            @NonNull FallbackOptions fallbackOptions, @NonNull LoadErrorInfo loadErrorInfo) {
        return delegate.getFallbackSelectionFor(fallbackOptions, loadErrorInfo);
    }

    @Override
    public long getRetryDelayMsFor(@NonNull LoadErrorInfo loadErrorInfo) {
        if (!isTransientManifestFailure(loadErrorInfo.exception)) return delegate.getRetryDelayMsFor(loadErrorInfo);
        if (loadErrorInfo.errorCount > MAX_MANIFEST_RETRIES) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "give-up attempts=%d error=%s", loadErrorInfo.errorCount, loadErrorInfo.exception.getClass().getSimpleName());
            return delegate.getRetryDelayMsFor(loadErrorInfo);
        }
        long delay = Math.min(MAX_DELAY_MS, BASE_DELAY_MS << Math.min(loadErrorInfo.errorCount - 1, 3));
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "retry attempts=%d delayMs=%d error=%s", loadErrorInfo.errorCount, delay, loadErrorInfo.exception.getClass().getSimpleName());
        return delay;
    }

    @Override
    public int getMinimumLoadableRetryCount(int dataType) {
        int base = delegate.getMinimumLoadableRetryCount(dataType);
        // 只抬高 manifest 的重试次数：这是本地包直播真正会返回空体的环节。
        // 媒体分片沿用上层策略，避免点播死链/404 因为多出的重试而延后十几秒才失败。
        return dataType == C.DATA_TYPE_MANIFEST ? Math.max(base, MAX_MANIFEST_RETRIES) : base;
    }

    /**
     * 空体/无法识别/解析失败都算临时失败：端点抖动比内容真的坏掉常见得多，多试几次的代价远小于
     * 直接把视频轨丢掉。
     */
    static boolean isTransientManifestFailure(@Nullable Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth++ < 12; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof UnrecognizedInputFormatException) return true;
            String name = t.getClass().getName();
            if (name.equals("androidx.media3.extractor.ParserException")) return true;
            if (t instanceof IOException && isTransientMessage(t.getMessage())) return true;
        }
        return false;
    }

    private static boolean isTransientMessage(@Nullable String message) {
        if (message == null || message.isEmpty()) return true;
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("empty") || lower.contains("unrecognized") || lower.contains("failed to parse")
                || lower.contains("unexpected end") || lower.contains("unexpected eof");
    }
}
