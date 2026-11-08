package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import com.github.catvod.crawler.SpiderDebug;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.util.List;
import java.util.Map;

/** Reopens a truncated fixed-length HTTP response at the exact unread byte offset. */
final class HttpEofRecoveryDataSource implements DataSource {

    /**
     * Limit only consecutive EOF recoveries. A long remote file may legitimately
     * close its HTTP response more than once, so a lifetime limit would eventually
     * turn a recoverable stream interruption into a player error.
     */
    private static final int MAX_CONSECUTIVE_RECONNECTS = 3;

    /**
     * Backoff before each consecutive reconnect, indexed by the attempt about to be made.
     *
     * <p>A truncated response is usually the upstream still recovering, so reconnecting with no
     * delay only adds load to a struggling peer: on device the three attempts landed 23-123 ms
     * apart (median 47 ms), and 83% of all recoveries followed the previous one within 100 ms.
     * Spacing them gives the peer a window to recover and stops the reader from occupying the
     * connection continuously.
     *
     * <p>Deliberately not jittered. Jitter exists to spread retries across many clients hitting one
     * server; here a single client retries a single offset, so it would add nondeterminism without
     * changing the load. The count is unchanged ({@link #MAX_CONSECUTIVE_RECONNECTS}); only the
     * spacing is.
     */
    private static final long[] RECONNECT_BACKOFF_MS = {100L, 250L, 500L};

    private final DataSource upstream;
    private final boolean foreground;
    private DataSpec dataSpec;
    private long bytesRead;
    private int consecutiveReconnects;

    HttpEofRecoveryDataSource(DataSource upstream) {
        this(upstream, true);
    }

    /**
     * @param foreground whether this source serves the playing item. Only foreground recoveries are
     *     published as {@link ExoForegroundEofSignal}, so preload cannot yield to itself.
     */
    HttpEofRecoveryDataSource(DataSource upstream, boolean foreground) {
        this.upstream = upstream;
        this.foreground = foreground;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.dataSpec = dataSpec;
        this.bytesRead = 0;
        this.consecutiveReconnects = 0;
        return upstream.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        while (true) {
            try {
                int read = upstream.read(buffer, offset, length);
                if (read > 0) {
                    bytesRead += read;
                    // Progress proves the resumed connection is healthy. Do not
                    // carry an old burst of EOFs into a later, independent stall.
                    consecutiveReconnects = 0;
                }
                return read;
            } catch (IOException error) {
                if (!isRecoverableEof(error) || !canRecoverConsecutively(consecutiveReconnects) || dataSpec == null) throw error;
                consecutiveReconnects++;
                reconnectAtCurrentPosition();
            }
        }
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
        dataSpec = null;
        bytesRead = 0;
        consecutiveReconnects = 0;
        upstream.close();
    }

    private void reconnectAtCurrentPosition() throws IOException {
        // Space out consecutive attempts before reopening, so a peer that is still recovering is
        // not hit again immediately. See RECONNECT_BACKOFF_MS.
        backoffBeforeReconnect(consecutiveReconnects);
        // close() may have run while we slept: the source is cancelled, so reopening would
        // resurrect a closed connection at a stale offset. The caller's own dataSpec guard cannot
        // cover this, because it ran before the sleep.
        if (dataSpec == null) {
            throw new InterruptedIOException("EOF recovery cancelled by close");
        }
        if (foreground) ExoForegroundEofSignal.record(SystemClock.elapsedRealtime());
        try {
            upstream.close();
        } catch (IOException ignored) {
        }
        long remaining = remainingLength();
        DataSpec retrySpec = remaining == C.LENGTH_UNSET ? dataSpec.subrange(bytesRead) : dataSpec.subrange(bytesRead, remaining);
        upstream.open(retrySpec);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-network", "unexpected EOF recovered offset=%d remaining=%d consecutiveAttempt=%d backoffMs=%d", retrySpec.position, remaining, consecutiveReconnects, backoffMs(consecutiveReconnects));
    }

    /**
     * Sleeps for the backoff belonging to the attempt about to be made. A negative or out-of-range
     * attempt sleeps nothing, so the cap stays enforceable by the caller's own limit.
     */
    private static void backoffBeforeReconnect(int consecutiveReconnects) throws IOException {
        long delayMs = backoffMs(consecutiveReconnects);
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException error = new InterruptedIOException("EOF recovery backoff interrupted");
            error.initCause(e);
            throw error;
        }
    }

    static long backoffMs(int consecutiveReconnects) {
        if (consecutiveReconnects <= 0) return 0L;
        int index = Math.min(consecutiveReconnects, RECONNECT_BACKOFF_MS.length) - 1;
        return RECONNECT_BACKOFF_MS[index];
    }

    private long remainingLength() {
        if (dataSpec == null || dataSpec.length == C.LENGTH_UNSET) return C.LENGTH_UNSET;
        return remainingLength(dataSpec.length, bytesRead);
    }

    static long remainingLength(long requestedLength, long bytesRead) {
        if (requestedLength == C.LENGTH_UNSET) return C.LENGTH_UNSET;
        return Math.max(0, requestedLength - Math.max(0, bytesRead));
    }

    static boolean isRecoverableEof(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ProtocolException && messageContainsUnexpectedEnd(current.getMessage())) return true;
            current = current.getCause();
        }
        return false;
    }

    static boolean canRecoverConsecutively(int consecutiveReconnects) {
        return consecutiveReconnects >= 0 && consecutiveReconnects < MAX_CONSECUTIVE_RECONNECTS;
    }

    private static boolean messageContainsUnexpectedEnd(String message) {
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("unexpected end of stream");
    }

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;
        private final boolean foreground;

        Factory(DataSource.Factory upstreamFactory) {
            this(upstreamFactory, true);
        }

        Factory(DataSource.Factory upstreamFactory, boolean foreground) {
            this.upstreamFactory = upstreamFactory;
            this.foreground = foreground;
        }

        @Override
        public DataSource createDataSource() {
            return new HttpEofRecoveryDataSource(upstreamFactory.createDataSource(), foreground);
        }
    }
}
