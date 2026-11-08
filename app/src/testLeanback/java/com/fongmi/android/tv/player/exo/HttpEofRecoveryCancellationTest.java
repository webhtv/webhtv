package com.fongmi.android.tv.player.exo;

import android.app.Application;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Cancellation behavior of the EOF recovery backoff, which needs a real {@link Uri}.
 *
 * <p>The backoff sleep sits between {@code read()}'s own {@code dataSpec == null} guard and the
 * dereference of {@code dataSpec}. Media3 cancels a load by calling {@link DataSource#close()}
 * from another thread, so a close landing <em>inside</em> that sleep would let the source reopen a
 * closed connection at a stale offset — or dereference a null spec. The shared JVM test set cannot
 * build a {@link DataSpec} because {@code android.net.Uri} is not mocked there, hence Robolectric.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HttpEofRecoveryCancellationTest {

    /**
     * Only the foreground source may publish the yield signal. If preload published it too, preload
     * would yield to itself and never restart; if the foreground did not publish, the yield would
     * never fire. Both halves are load-bearing, so both are pinned.
     */
    @Test
    public void onlyForegroundSourcePublishesTheYieldSignal() throws Exception {
        ExoForegroundEofSignal.reset();
        driveOneRecovery(true);
        long afterForeground = ExoForegroundEofSignal.ageMs(0);
        assertTrue("foreground recovery must publish the signal",
                afterForeground != Long.MAX_VALUE);

        ExoForegroundEofSignal.reset();
        driveOneRecovery(false);
        assertEquals("preload-side recovery must not publish the signal",
                Long.MAX_VALUE, ExoForegroundEofSignal.ageMs(0));
    }

    private static void driveOneRecovery(boolean foreground) throws Exception {
        DataSource upstream = new DataSource() {
            @Override public void addTransferListener(TransferListener listener) {}

            @Override public long open(DataSpec dataSpec) { return C.LENGTH_UNSET; }

            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                throw new ProtocolException("unexpected end of stream");
            }

            @Override public Uri getUri() { return Uri.EMPTY; }

            @Override public void close() {}
        };
        HttpEofRecoveryDataSource source = new HttpEofRecoveryDataSource(upstream, foreground);
        source.open(new DataSpec(Uri.parse("http://example.invalid/a.mp4"), 0, C.LENGTH_UNSET));
        try {
            source.read(new byte[16], 0, 16);
        } catch (IOException expected) {
            // The recovery runs (and publishes) before the retry budget is exhausted.
        }
    }

    /** First-attempt backoff is 100 ms; this delay must land well inside it. */
    private static final long CLOSE_INSIDE_BACKOFF_DELAY_MS = 30L;

    @Test
    public void closeDuringBackoffCancelsCleanlyInsteadOfReopening() throws Exception {
        CountDownLatch readThrew = new CountDownLatch(1);
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();

        DataSource upstream = new DataSource() {
            @Override public void addTransferListener(TransferListener listener) {}

            @Override public long open(DataSpec dataSpec) {
                opens.incrementAndGet();
                return C.LENGTH_UNSET;
            }

            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                readThrew.countDown();
                throw new ProtocolException("unexpected end of stream");
            }

            @Override public Uri getUri() { return Uri.EMPTY; }

            @Override public void close() {}
        };

        HttpEofRecoveryDataSource source = new HttpEofRecoveryDataSource(upstream);
        source.open(new DataSpec(Uri.parse("http://example.invalid/a.mp4"), 0, C.LENGTH_UNSET));

        Thread reader = new Thread(() -> {
            try {
                source.read(new byte[16], 0, 16);
            } catch (Throwable error) {
                readerFailure.set(error);
            }
        });
        reader.start();

        assertTrue("upstream read should have been reached",
                readThrew.await(2, TimeUnit.SECONDS));
        // The catch block's own null check runs within microseconds of the throw, so sleeping here
        // guarantees close() lands after that check and inside the backoff sleep.
        Thread.sleep(CLOSE_INSIDE_BACKOFF_DELAY_MS);
        source.close();
        reader.join(5_000);

        assertFalse("reader thread must terminate", reader.isAlive());
        Throwable failure = readerFailure.get();
        assertNotNull("read() must surface a failure, not return normally", failure);
        // The discriminator: a clean cancellation is an IOException. Without the post-backoff
        // re-check the stale spec is dereferenced and this becomes a NullPointerException.
        assertTrue("close during backoff must surface IOException, got " + failure,
                failure instanceof IOException);
        assertFalse("close during backoff must not NPE",
                failure instanceof NullPointerException);
        assertEquals("close during backoff must not reopen", 1, opens.get());
    }
}
