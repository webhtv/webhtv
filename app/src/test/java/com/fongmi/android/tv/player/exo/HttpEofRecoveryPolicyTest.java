package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;

import org.junit.Test;

import java.io.IOException;
import java.net.ProtocolException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpEofRecoveryPolicyTest {

    @Test
    public void recognizesOnlyUnexpectedFixedLengthEof() {
        assertTrue(HttpEofRecoveryDataSource.isRecoverableEof(new ProtocolException("unexpected end of stream")));
        assertTrue(HttpEofRecoveryDataSource.isRecoverableEof(new IOException(new ProtocolException("unexpected end of stream"))));
        assertFalse(HttpEofRecoveryDataSource.isRecoverableEof(new ProtocolException("invalid response")));
        assertFalse(HttpEofRecoveryDataSource.isRecoverableEof(new IOException("connection reset")));
    }

    @Test
    public void computesExactUnreadRangeLength() {
        assertEquals(6, HttpEofRecoveryDataSource.remainingLength(10, 4));
        assertEquals(0, HttpEofRecoveryDataSource.remainingLength(10, 12));
        assertEquals(C.LENGTH_UNSET, HttpEofRecoveryDataSource.remainingLength(C.LENGTH_UNSET, 4));
    }

    @Test
    public void capsOnlyConsecutiveFailures() {
        assertTrue(HttpEofRecoveryDataSource.canRecoverConsecutively(0));
        assertTrue(HttpEofRecoveryDataSource.canRecoverConsecutively(2));
        assertFalse(HttpEofRecoveryDataSource.canRecoverConsecutively(3));
        assertFalse(HttpEofRecoveryDataSource.canRecoverConsecutively(-1));
    }

    @Test
    public void spacesOutConsecutiveReconnects() {
        // The first attempt is the one currently failing; it must not be delayed, or a single
        // transient EOF would cost latency for no benefit.
        assertEquals(0, HttpEofRecoveryDataSource.backoffMs(0));
        assertEquals(0, HttpEofRecoveryDataSource.backoffMs(-1));
        // Attempts 1..3 are the retries, strictly increasing so each gives the peer more room.
        long first = HttpEofRecoveryDataSource.backoffMs(1);
        long second = HttpEofRecoveryDataSource.backoffMs(2);
        long third = HttpEofRecoveryDataSource.backoffMs(3);
        assertTrue("first retry must be delayed", first > 0);
        assertTrue("backoff must increase", second > first);
        assertTrue("backoff must increase", third > second);
        // Beyond the retry budget the delay saturates instead of growing without bound.
        assertEquals(third, HttpEofRecoveryDataSource.backoffMs(4));
        assertEquals(third, HttpEofRecoveryDataSource.backoffMs(99));
    }

    @Test
    public void totalBackoffStaysWithinOneRecoveryBurst() {
        // Guards the cost of the change: a full 3-retry burst must stay well under a second so a
        // working stream is not made to feel slow, while still being far above the 23-123 ms
        // spacing that produced the observed retry storm.
        long total = HttpEofRecoveryDataSource.backoffMs(1)
                + HttpEofRecoveryDataSource.backoffMs(2)
                + HttpEofRecoveryDataSource.backoffMs(3);
        assertTrue("burst must be perceptible to the peer: " + total, total >= 500);
        assertTrue("burst must stay sub-second: " + total, total < 1_000);
    }
}
