package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SpiderInitTest {

    private final ConcurrentHashMap<String, Spider> cache = new ConcurrentHashMap<>();
    private final List<Throwable> failures = new ArrayList<>();

    @Test
    public void successfulInstanceIsInitializedOnceAndCached() {
        FakeSpider spider = new FakeSpider(null, null);
        AtomicInteger creations = new AtomicInteger();
        Callable<Spider> factory = () -> {
            creations.incrementAndGet();
            return spider;
        };

        assertSame(spider, load("ready", factory));
        assertSame(spider, load("ready", factory));
        assertEquals(1, creations.get());
        assertEquals(List.of("init"), spider.events);
        assertEquals(1, spider.resources);
        assertEquals("ready", spider.homeContent(false));
        assertTrue(failures.isEmpty());
    }

    @Test
    public void failedInitializationIsDestroyedBeforeNextAttemptSucceeds() {
        RuntimeException primary = new IllegalStateException("init failed");
        FakeSpider failed = new FakeSpider(primary, null);
        FakeSpider ready = new FakeSpider(null, null);

        assertTrue(load("site", () -> failed) instanceof SpiderNull);
        assertEquals(List.of("init", "destroy"), failed.events);
        assertEquals(0, failed.resources);
        assertSame(primary, failures.get(0));
        assertSame(ready, load("site", () -> ready));
        assertSame(ready, load("site", () -> failed));
        assertEquals(List.of("init"), ready.events);
    }

    @Test
    public void factoryFailureDoesNotInitializeOrDestroyAndCanRetry() {
        RuntimeException primary = new IllegalStateException("factory failed");
        FakeSpider ready = new FakeSpider(null, null);

        assertTrue(load("site", () -> { throw primary; }) instanceof SpiderNull);
        assertSame(primary, failures.get(0));
        assertSame(ready, load("site", () -> ready));
        assertEquals(List.of("init"), ready.events);
    }

    @Test
    public void nullFactoryResultFallsBackWithoutPoisoningTheKey() {
        assertTrue(load("site", () -> null) instanceof SpiderNull);
        assertTrue(failures.get(0) instanceof NullPointerException);
        FakeSpider ready = new FakeSpider(null, null);
        assertSame(ready, load("site", () -> ready));
    }

    @Test
    public void cleanupFailureIsSuppressedOnTheOriginalInitializationFailure() {
        RuntimeException primary = new IllegalStateException("init failed");
        RuntimeException cleanup = new IllegalArgumentException("destroy failed");
        FakeSpider spider = new FakeSpider(primary, cleanup);

        assertTrue(load("site", () -> spider) instanceof SpiderNull);
        assertSame(primary, failures.get(0));
        assertEquals(1, primary.getSuppressed().length);
        assertSame(cleanup, primary.getSuppressed()[0]);
        assertEquals(List.of("init", "destroy"), spider.events);
    }

    @Test
    public void cleanupErrorDoesNotReplaceTheInitializationFailure() {
        RuntimeException primary = new IllegalStateException("init failed");
        Error cleanup = new AssertionError("destroy error");
        FakeSpider spider = new FakeSpider(primary, cleanup);

        assertTrue(load("site", () -> spider) instanceof SpiderNull);
        assertSame(primary, failures.get(0));
        assertSame(cleanup, primary.getSuppressed()[0]);
    }

    @Test
    public void initializationErrorPreservesTheExistingFallbackPolicy() {
        Error primary = new AssertionError("init error");
        FakeSpider spider = new FakeSpider(primary, null);

        assertTrue(load("site", () -> spider) instanceof SpiderNull);
        assertSame(primary, failures.get(0));
        assertEquals(List.of("init", "destroy"), spider.events);
        assertEquals(0, spider.resources);
    }

    @Test
    public void identicalInitializationAndCleanupFailureIsNotSelfSuppressed() {
        RuntimeException primary = new IllegalStateException("same failure");
        FakeSpider spider = new FakeSpider(primary, primary);

        assertTrue(load("site", () -> spider) instanceof SpiderNull);
        assertSame(primary, failures.get(0));
        assertEquals(0, primary.getSuppressed().length);
        assertEquals(List.of("init", "destroy"), spider.events);
    }

    @Test
    public void disabledSuppressionStillPreservesTheOriginalFailure() {
        RuntimeException primary = new RuntimeException("no suppression", null, false, true) {};
        FakeSpider spider = new FakeSpider(primary, new AssertionError("cleanup error"));

        assertTrue(load("site", () -> spider) instanceof SpiderNull);
        assertSame(primary, failures.get(0));
        assertEquals(0, primary.getSuppressed().length);
    }

    @Test
    public void failureForOneKeyDoesNotDestroyAnotherKeysSuccessfulInstance() {
        FakeSpider ready = new FakeSpider(null, null);
        FakeSpider failed = new FakeSpider(new IllegalStateException("failed"), null);

        assertSame(ready, load("ready", () -> ready));
        assertTrue(load("failed", () -> failed) instanceof SpiderNull);
        assertSame(ready, load("ready", () -> failed));
        assertEquals(List.of("init"), ready.events);
        assertEquals(1, ready.resources);
        assertEquals(List.of("init", "destroy"), failed.events);
    }

    @Test
    public void sameKeyConcurrentCallsShareOnlyTheSuccessfulInitialization() throws Exception {
        concurrentInitialization(false);
    }

    @Test
    public void sameKeyWaitingCallRetriesAfterFailedInitializationIsDestroyed() throws Exception {
        concurrentInitialization(true);
    }

    @Test
    public void checkedInitializationExceptionIsStillCleanedAndReported() {
        Exception primary = new Exception("checked init failure");
        FakeSpider spider = new FakeSpider(primary, null);
        assertTrue(load("site", () -> spider) instanceof SpiderNull);
        assertSame(primary, failures.get(0));
        assertEquals(List.of("init", "destroy"), spider.events);
        assertEquals(0, spider.resources);
    }

    @Test
    public void diagnosticFailureCannotReplaceThePrimaryOrCacheTheFallback() {
        RuntimeException primary = new IllegalStateException("primary init failure");
        RuntimeException diagnostic = new IllegalArgumentException("diagnostic failed");
        FakeSpider spider = new FakeSpider(primary, null);
        java.io.PrintStream original = System.err;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (java.io.PrintStream stderr = new java.io.PrintStream(output, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setErr(stderr);
            assertTrue(SpiderInit.load(cache, "site", () -> spider,
                    value -> value.init(null, "{}"), failure -> { throw diagnostic; }) instanceof SpiderNull);
            assertTrue(output.toString(java.nio.charset.StandardCharsets.UTF_8).contains("primary init failure"));
        } finally {
            System.setErr(original);
        }
        assertSame(diagnostic, primary.getSuppressed()[0]);
        FakeSpider ready = new FakeSpider(null, null);
        assertSame(ready, load("site", () -> ready));
    }

    @Test
    public void failedFallbackPrinterCannotEscapeOrPreventSameKeyRecovery() {
        RuntimeException diagnostic = new IllegalArgumentException("diagnostic failed");
        RuntimeException primary = new IllegalStateException("primary init failure") {
            @Override
            public void printStackTrace() {
                throw new AssertionError("fallback printer failed");
            }
        };
        FakeSpider failed = new FakeSpider(primary, null);
        Spider result = null;
        Throwable escaped = null;
        try {
            result = SpiderInit.load(cache, "site", () -> failed,
                    value -> value.init(null, "{}"), failure -> { throw diagnostic; });
        } catch (Throwable error) {
            escaped = error;
        }

        assertEquals(List.of("init", "destroy"), failed.events);
        assertEquals(0, failed.resources);
        org.junit.Assert.assertNull("Diagnostic output must not escape the fallback", escaped);
        assertTrue(result instanceof SpiderNull);
        assertSame(diagnostic, primary.getSuppressed()[0]);
        FakeSpider ready = new FakeSpider(null, null);
        assertSame(ready, load("site", () -> ready));
        assertSame(ready, load("site", () -> failed));
        assertEquals(List.of("init"), ready.events);
    }

    private void concurrentInitialization(boolean failFirst) throws Exception {
        CountDownLatch initialized = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch requested = new CountDownLatch(1);
        FakeSpider first = new FakeSpider(failFirst ? new IllegalStateException("first failed") : null, null);
        FakeSpider next = new FakeSpider(null, null);
        AtomicInteger creations = new AtomicInteger();
        Callable<Spider> factory = () -> creations.incrementAndGet() == 1 ? first : next;
        SpiderInit.Initializer initializer = spider -> {
            if (spider == first) {
                initialized.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            if (spider == next) assertEquals(List.of("init", "destroy"), first.events);
            spider.init(null, "{}");
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Spider> a = executor.submit(() -> SpiderInit.load(cache, "site", factory, initializer, failures::add));
            assertTrue(initialized.await(5, TimeUnit.SECONDS));
            Future<Spider> b = executor.submit(() -> {
                requested.countDown();
                return SpiderInit.load(cache, "site", factory, initializer, failures::add);
            });
            assertTrue(requested.await(5, TimeUnit.SECONDS));
            release.countDown();
            Spider resultA = a.get(5, TimeUnit.SECONDS);
            Spider resultB = b.get(5, TimeUnit.SECONDS);
            if (failFirst) {
                assertTrue(resultA instanceof SpiderNull);
                assertSame(next, resultB);
                assertEquals(2, creations.get());
                assertEquals(0, first.resources);
            } else {
                assertSame(first, resultA);
                assertSame(first, resultB);
                assertEquals(1, creations.get());
            }
            assertSame(resultB, load("site", () -> { throw new AssertionError("cached factory invoked"); }));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Spider load(String key, Callable<Spider> factory) {
        return SpiderInit.load(cache, key, factory, spider -> spider.init(null, "{}"), failures::add);
    }

    private static final class FakeSpider extends Spider {
        final List<String> events = new ArrayList<>();
        final Throwable initFailure;
        final Throwable cleanupFailure;
        int resources;

        FakeSpider(Throwable initFailure, Throwable cleanupFailure) {
            this.initFailure = initFailure;
            this.cleanupFailure = cleanupFailure;
        }

        @Override
        public void init(Context context, String ext) throws Exception {
            events.add("init");
            resources++;
            throwFailure(initFailure);
        }

        @Override
        public void destroy() {
            events.add("destroy");
            resources--;
            if (cleanupFailure instanceof Error fatal) throw fatal;
            if (cleanupFailure instanceof RuntimeException error) throw error;
        }

        @Override
        public String homeContent(boolean filter) {
            return resources == 1 ? "ready" : "released";
        }

        private static void throwFailure(Throwable failure) throws Exception {
            if (failure instanceof Error fatal) throw fatal;
            if (failure instanceof Exception error) throw error;
        }
    }
}
