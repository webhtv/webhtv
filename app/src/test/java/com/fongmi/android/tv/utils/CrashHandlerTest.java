package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Drives the production fatal handler: one report, one handoff, then termination. */
public class CrashHandlerTest {

    private List<String> events;
    private boolean handoffEnded;
    private boolean terminatedEarly;
    private CrashReport.FatalHandler self;

    private CrashReport.FatalHandler handler(Runnable report, Runnable handoff) {
        return new CrashReport.FatalHandler(
                (thread, error) -> report.run(),
                (thread, error) -> handoff.run(),
                () -> {
                    if (!handoffEnded) terminatedEarly = true;
                    events.add("terminate");
                });
    }

    @Test
    public void fatalIsReportedHandedOffAndTerminatedInOrder() {
        events = Collections.synchronizedList(new ArrayList<>());
        List<Thread> seen = new ArrayList<>();
        self = handler(() -> {
            seen.add(Thread.currentThread());
            events.add("report");
        }, () -> {
            handoffEnded = true;
            events.add("page");
        });
        self.uncaughtException(Thread.currentThread(), new IllegalStateException("Application.onCreate failure"));
        assertEquals(List.of("report", "page", "terminate"), events);
        assertEquals(List.of(Thread.currentThread()), seen);
    }

    @Test
    public void reportFailureStillHandsOffAndTerminates() {
        events = Collections.synchronizedList(new ArrayList<>());
        self = handler(() -> {
            events.add("report");
            throw new OutOfMemoryError("report failed");
        }, () -> {
            handoffEnded = true;
            events.add("page");
        });
        self.uncaughtException(Thread.currentThread(), new Error("fatal"));
        assertEquals(List.of("report", "page", "terminate"), events);
        assertFalse(terminatedEarly);
    }

    @Test
    public void handoffFailureStillTerminates() {
        events = Collections.synchronizedList(new ArrayList<>());
        self = handler(() -> events.add("report"), () -> {
            events.add("page");
            throw new Error("startActivity failed");
        });
        self.uncaughtException(Thread.currentThread(), new Error("fatal"));
        assertEquals(List.of("report", "page", "terminate"), events);
    }

    @Test
    public void backgroundThreadFatalTakesTheSamePath() throws Exception {
        events = Collections.synchronizedList(new ArrayList<>());
        self = handler(() -> events.add("report"), () -> {
            handoffEnded = true;
            events.add("page");
        });
        Thread worker = new Thread(() -> self.uncaughtException(Thread.currentThread(), new IllegalStateException("background")), "background-worker");
        worker.start();
        worker.join(2000);
        assertEquals(List.of("report", "page", "terminate"), events);
    }

    @Test
    public void reentrantFatalCannotTerminateBeforeTheFirstHandoffEnds() {
        events = Collections.synchronizedList(new ArrayList<>());
        CrashReport.FatalHandler[] reference = new CrashReport.FatalHandler[1];
        reference[0] = handler(() -> {
            events.add("report");
            reference[0].uncaughtException(Thread.currentThread(), new Error("reentrant report"));
        }, () -> {
            events.add("page");
            reference[0].uncaughtException(Thread.currentThread(), new Error("reentrant handoff"));
            handoffEnded = true;
        });
        self = reference[0];
        self.uncaughtException(Thread.currentThread(), new Error("fatal"));
        self.uncaughtException(Thread.currentThread(), new Error("continuous"));
        assertEquals(List.of("report", "page", "terminate"), events);
        assertFalse("no recursive call may kill before the first handoff ends", terminatedEarly);
    }

    @Test
    public void concurrentFatalCannotTerminateBeforeTheFirstHandoff() throws Exception {
        events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch reporting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        self = handler(() -> {
            events.add("report");
            reporting.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, () -> {
            handoffEnded = true;
            events.add("page");
        });
        Thread first = new Thread(() -> self.uncaughtException(Thread.currentThread(), new Error("first")));
        Thread second = new Thread(() -> self.uncaughtException(Thread.currentThread(), new Error("second")));
        first.start();
        assertTrue(reporting.await(2, TimeUnit.SECONDS));
        second.start();
        release.countDown();
        first.join(2000);
        second.join(2000);
        assertEquals(List.of("report", "page", "terminate"), events);
        assertFalse(terminatedEarly);
    }
}
