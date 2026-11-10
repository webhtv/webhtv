package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import com.github.catvod.Init;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexClassLoader;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, shadows = {
        LoaderFailureLoggingTest.PythonFactory.class,
        LoaderFailureLoggingTest.JavaScriptFactory.class})
public class LoaderFailureLoggingTest {

    @Before
    public void resetFactories() {
        Init.set(RuntimeEnvironment.getApplication());
        DebugLogStore.setEnabled(false);
        PythonFactory.spiders.clear();
        JavaScriptFactory.spiders.clear();
    }

    @After
    public void clearFactories() {
        resetFactories();
    }

    @Test
    public void failedPythonInitializationReleasesItsInstance() {
        State state = new State(true);
        PythonFactory.spiders.add(new FakePythonSpider(state));

        Spider result = new PyLoader().getSpider("python", "fixture.py", "[]");

        assertTrue(result instanceof SpiderNull);
        assertEquals(List.of("init", "destroy"), state.events);
        assertEquals(0, state.resources);
    }

    @Test
    public void failedJavaScriptInitializationReleasesItsInstance() {
        State state = new State(true);
        JavaScriptFactory.spiders.add(new FakeJavaScriptSpider(state));

        Spider result = new JsLoader().getSpider("javascript", "fixture.js", "{}", "");

        assertTrue(result instanceof SpiderNull);
        assertEquals(List.of("init", "destroy"), state.events);
        assertEquals(0, state.resources);
    }

    @Test
    public void pythonRetriesTheSameKeyAfterFailure() {
        State failed = new State(true);
        State ready = new State(false);
        FakePythonSpider success = new FakePythonSpider(ready);
        PythonFactory.spiders.add(new FakePythonSpider(failed));
        PythonFactory.spiders.add(success);
        PyLoader loader = new PyLoader();

        assertTrue(loader.getSpider("python", "fixture.py", "[]") instanceof SpiderNull);
        assertSame(success, loader.getSpider("python", "fixture.py", "[]"));
        assertSame(success, loader.getSpider("python", "fixture.py", "[]"));
        assertEquals(List.of("init"), ready.events);
        assertEquals("python", ready.siteKey);
        assertEquals("{}", ready.ext);
    }

    @Test
    public void javaScriptRetriesTheSameKeyAfterFailure() {
        State failed = new State(true);
        State ready = new State(false);
        FakeJavaScriptSpider success = new FakeJavaScriptSpider(ready);
        JavaScriptFactory.spiders.add(new FakeJavaScriptSpider(failed));
        JavaScriptFactory.spiders.add(success);
        JsLoader loader = new JsLoader();

        assertTrue(loader.getSpider("javascript", "fixture.js", " [] ", "") instanceof SpiderNull);
        assertSame(success, loader.getSpider("javascript", "fixture.js", " [] ", ""));
        assertSame(success, loader.getSpider("javascript", "fixture.js", " [] ", ""));
        assertEquals(List.of("init"), ready.events);
        assertEquals("javascript", ready.siteKey);
        assertEquals(" [] ", ready.ext);
    }

    @Test
    public void namespacedPythonFailureCleanupRecoveryAndCacheStayIsolated() throws Exception {
        PyLoader loader = new PyLoader();
        State failed = new State(true), ready = new State(false), other = new State(false);
        FakePythonSpider success = new FakePythonSpider(ready), sibling = new FakePythonSpider(other);
        PythonFactory.spiders.add(new FakePythonSpider(failed));
        PythonFactory.spiders.add(success); PythonFactory.spiders.add(sibling);
        assertTrue(loader.getSpider("A", "same", "fixture.py", " [] ") instanceof SpiderNull);
        assertEquals(List.of("init", "destroy"), failed.events);
        assertEquals(0, failed.resources);
        assertSame(success, loader.getSpider("A", "same", "fixture.py", " [] "));
        assertSame(sibling, loader.getSpider("B", "same", "fixture.py", "{}"));
        assertSame(success, loader.getSpider("A", "same", "fixture.py", " [] "));
        assertEquals(List.of("init"), ready.events);
        assertEquals("same", ready.siteKey); assertEquals("{}", ready.ext);
        loader.setRecent("A", "same"); assertEquals("same", loader.proxy(java.util.Map.of())[0]);
        assertEquals(java.util.Set.of("same"), loader.activeKeys());
        loader.clear(); assertEquals(List.of("init", "destroy"), ready.events);
        assertEquals(List.of("init", "destroy"), other.events);
    }

    @Test
    public void namespacedJavaScriptFailureCleanupRecoveryAndCacheStayIsolated() throws Exception {
        JsLoader loader = new JsLoader();
        State failed = new State(true), ready = new State(false), other = new State(false);
        FakeJavaScriptSpider success = new FakeJavaScriptSpider(ready), sibling = new FakeJavaScriptSpider(other);
        JavaScriptFactory.spiders.add(new FakeJavaScriptSpider(failed));
        JavaScriptFactory.spiders.add(success); JavaScriptFactory.spiders.add(sibling);
        assertTrue(loader.getSpider("A", "same", "fixture.js", " [] ", "") instanceof SpiderNull);
        assertEquals(List.of("init", "destroy"), failed.events); assertEquals(0, failed.resources);
        assertSame(success, loader.getSpider("A", "same", "fixture.js", " [] ", ""));
        assertSame(sibling, loader.getSpider("B", "same", "fixture.js", "{}", ""));
        assertSame(success, loader.getSpider("A", "same", "fixture.js", " [] ", ""));
        assertEquals(List.of("init"), ready.events);
        assertEquals("same", ready.siteKey); assertEquals(" [] ", ready.ext);
        loader.setRecent("B", "same"); assertEquals("same", loader.proxy(java.util.Map.of())[0]);
        assertEquals(java.util.Set.of("same"), loader.activeKeys());
        loader.clear(); assertEquals(List.of("init", "destroy"), ready.events);
        assertEquals(List.of("init", "destroy"), other.events);
    }

    @Test
    public void pythonPreservesExtensionsOtherThanTheEmptyListCompatibilityCase() {
        PyLoader loader = new PyLoader();
        String[] extensions = {null, "", "   ", " {\"token\":1} ", " [] "};
        String[] expected = {null, "", "   ", " {\"token\":1} ", "{}"};
        for (int i = 0; i < extensions.length; i++) {
            State state = new State(false);
            FakePythonSpider spider = new FakePythonSpider(state);
            PythonFactory.spiders.add(spider);
            assertSame(spider, loader.getSpider("key" + i, "fixture.py", extensions[i]));
            assertEquals(expected[i], state.ext);
            assertEquals("key" + i, state.siteKey);
        }
    }

    @Test
    public void failedPythonKeyIsNotActiveAndCannotProxyThroughTheFailedInstance() throws Exception {
        State failed = new State(true);
        PythonFactory.spiders.add(new FakePythonSpider(failed));
        PyLoader loader = new PyLoader();
        assertTrue(loader.getSpider("failed", "fixture.py", "{}") instanceof SpiderNull);
        loader.setRecent("failed");
        assertFalse(loader.activeKeys().contains("failed"));
        assertNull(loader.proxy(java.util.Map.of()));
        assertEquals(List.of("init", "destroy"), failed.events);
    }

    @Test
    public void failedJavaScriptKeyDoesNotRetireAnotherSuccessfulKey() throws Exception {
        State ready = new State(false);
        State failed = new State(true);
        FakeJavaScriptSpider success = new FakeJavaScriptSpider(ready);
        JavaScriptFactory.spiders.add(success);
        JavaScriptFactory.spiders.add(new FakeJavaScriptSpider(failed));
        JsLoader loader = new JsLoader();
        assertSame(success, loader.getSpider("ready", "fixture.js", "{}", ""));
        assertTrue(loader.getSpider("failed", "fixture.js", "{}", "") instanceof SpiderNull);
        assertEquals(java.util.Set.of("ready"), loader.activeKeys());
        assertSame(success, loader.getSpider("ready", "fixture.js", "{}", ""));
        loader.setRecent("ready");
        assertEquals("ready", loader.proxy(java.util.Map.of())[0]);
        assertEquals(List.of("init"), ready.events);
        assertEquals(1, ready.resources);
    }

    @Test
    public void pythonFailureKeepsOriginalStreamsAndReportsSuppressedCleanup() {
        failureLogging(false);
    }

    @Test
    public void javaScriptFailureKeepsOriginalStreamsAndBothExistingDiagnostics() {
        failureLogging(true);
    }

    private void failureLogging(boolean javascript) {
        State failed = new State(true);
        failed.emitOutput = true;
        failed.cleanupFailure = new IllegalArgumentException("fixture cleanup failure");
        if (javascript) JavaScriptFactory.spiders.add(new FakeJavaScriptSpider(failed));
        else PythonFactory.spiders.add(new FakePythonSpider(failed));
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            System.setErr(err);
            if (javascript) DebugLogStore.setEnabled(true);
            Spider result = javascript
                    ? new JsLoader().getSpider("javascript", "fixture.js", "{}", "")
                    : new PyLoader().getSpider("python", "fixture.py", "{}");
            assertTrue(result instanceof SpiderNull);
            assertSame(out, System.out);
            assertSame(err, System.err);
            assertEquals("fixture stdout\n", stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
            String errors = stderr.toString(StandardCharsets.UTF_8);
            assertTrue(errors.contains("fixture stderr"));
            assertTrue(errors.contains("fixture init failure"));
            assertTrue(errors.contains("Suppressed: java.lang.IllegalArgumentException: fixture cleanup failure"));
            assertEquals(1, errors.split("fixture stderr", -1).length - 1);
            if (javascript) {
                String diagnostics = String.join("\n", DebugLogStore.snapshot());
                assertTrue(diagnostics.contains("JS spider init failed: key=javascript api=fixture.js"));
                assertTrue(diagnostics.contains("fixture init failure"));
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            DebugLogStore.setEnabled(false);
        }
        assertEquals(List.of("init", "destroy"), failed.events);
    }

    private static final class State {
        final List<String> events = new ArrayList<>();
        final boolean fails;
        int resources;
        boolean emitOutput;
        RuntimeException cleanupFailure;
        String siteKey;
        String ext;

        State(boolean fails) {
            this.fails = fails;
        }

        void init(Spider spider, String ext) {
            if (emitOutput) {
                System.out.println("fixture stdout");
                System.err.println("fixture stderr");
            }
            events.add("init");
            resources++;
            siteKey = spider.siteKey;
            this.ext = ext;
            if (fails) throw new IllegalStateException("fixture init failure");
        }

        void destroy() {
            events.add("destroy");
            resources--;
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }

    private static final class FakePythonSpider extends com.fongmi.chaquo.Spider {
        private final State state;

        FakePythonSpider(State state) {
            super(null, null, "fixture.py");
            this.state = state;
        }

        @Override
        public void init(Context context, String ext) {
            state.init(this, ext);
        }

        @Override
        public Object[] proxy(java.util.Map<String, String> params) {
            return new Object[]{state.siteKey};
        }

        @Override
        public void destroy() {
            state.destroy();
        }
    }

    private static final class FakeJavaScriptSpider extends com.fongmi.quickjs.crawler.Spider {
        private final State state;

        FakeJavaScriptSpider(State state) {
            super("fixture.js", null);
            this.state = state;
        }

        @Override
        public void init(Context context, String ext) {
            state.init(this, ext);
        }

        @Override
        public Object[] proxy(java.util.Map<String, String> params) {
            return new Object[]{state.siteKey};
        }

        @Override
        public void destroy() {
            state.destroy();
        }
    }

    @Implements(com.fongmi.chaquo.Loader.class)
    public static class PythonFactory {
        static final ArrayDeque<com.fongmi.chaquo.Spider> spiders = new ArrayDeque<>();

        @Implementation
        protected void __constructor__() {
        }

        @Implementation
        protected com.fongmi.chaquo.Spider spider(String api) {
            return spiders.remove();
        }
    }

    @Implements(com.fongmi.quickjs.crawler.Loader.class)
    public static class JavaScriptFactory {
        static final ArrayDeque<com.fongmi.quickjs.crawler.Spider> spiders = new ArrayDeque<>();

        @Implementation
        protected void __constructor__() {
        }

        @Implementation
        protected com.fongmi.quickjs.crawler.Spider spider(String api, DexClassLoader dex) {
            return spiders.remove();
        }
    }
}
