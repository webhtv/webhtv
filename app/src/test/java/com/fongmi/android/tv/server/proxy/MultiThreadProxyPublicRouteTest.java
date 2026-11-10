package com.fongmi.android.tv.server.proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import java.net.InetAddress;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MultiThreadProxyPublicRouteTest {

    private static final String PAYLOAD = "ABCDEFGHIJKLMNOPQRSTUVWX";
    private final List<String> ranges = java.util.Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger activeShards = new AtomicInteger();
    private final AtomicInteger peakShards = new AtomicInteger();
    private final CountDownLatch concurrentShards = new CountDownLatch(2);
    private MockWebServer upstream;
    private MultiThreadProxyServer server;
    private OkHttpClient http;
    private OkHttpClient injected;
    private UpstreamUrlGuard guard;
    private final ProxyCapabilityToken capabilityToken = ProxyCapabilityToken.generate();
    private volatile String redirect;
    private volatile byte[] payload = PAYLOAD.getBytes(StandardCharsets.UTF_8);
    private volatile boolean trickle;
    private final AtomicInteger upstreamCancelled = new AtomicInteger();
    private volatile boolean ignoreRanges;
    private volatile boolean measureParallel;
    private volatile boolean blockPublicShards;
    private final CountDownLatch publicShardsStarted = new CountDownLatch(3);
    private final CountDownLatch slowBodyReadStarted = new CountDownLatch(1);
    private final CountDownLatch releasePublicShards = new CountDownLatch(1);

    @Before
    public void setUp() throws Exception {
        upstream = new MockWebServer();
        upstream.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (redirect != null) return new MockResponse.Builder().code(302)
                        .setHeader("Location", redirect).build();
                String range = request.getHeaders().get("Range");
                ranges.add("HEAD".equals(request.getMethod()) || range == null ? request.getMethod() : range);
                byte[] asset = payload;
                if ("HEAD".equals(request.getMethod())) return new MockResponse.Builder().code(200)
                        .setHeader("Content-Length", asset.length).build();
                if (range == null || ignoreRanges) return new MockResponse.Builder().code(200)
                        .setHeader("Content-Type", "video/mp4")
                        .body(new okio.Buffer().write(asset)).build();
                String[] bounds = range.substring(6).split("-", -1);
                int start = Integer.parseInt(bounds[0]);
                int end = bounds[1].isEmpty() || Integer.parseInt(bounds[1]) >= asset.length
                        ? asset.length - 1 : Integer.parseInt(bounds[1]);
                if (blockPublicShards && request.getUrl().encodedPath().equals("/slow") && start > 0) {
                    publicShardsStarted.countDown();
                    releasePublicShards.await(5, TimeUnit.SECONDS);
                }
                if (start > 0 && measureParallel) {
                    int active = activeShards.incrementAndGet();
                    peakShards.accumulateAndGet(active, Math::max);
                    concurrentShards.countDown();
                    try {
                        concurrentShards.await(2, TimeUnit.SECONDS);
                    } finally {
                        activeShards.decrementAndGet();
                    }
                }
                MockResponse.Builder shard = new MockResponse.Builder().code(206)
                        .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + asset.length)
                        .setHeader("Content-Type", "video/mp4")
                        .setHeader("ETag", "\"asset-v1\"")
                        .body(new okio.Buffer().write(asset, start, end - start + 1));
                if (trickle && start > 0) {
                    shard.throttleBody(1, 500, TimeUnit.MILLISECONDS);
                }
                return shard.build();
            }
        });
        upstream.start();
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        guard = new UpstreamUrlGuard(host -> new InetAddress[]{publicAddress});
        http = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
                .connectTimeout(2, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build();
        injected = http.newBuilder().eventListener(new okhttp3.EventListener() {
            @Override
            public void canceled(okhttp3.Call call) {
                upstreamCancelled.incrementAndGet();
            }

            @Override
            public void responseBodyStart(okhttp3.Call call) {
                String range = call.request().header("Range");
                if (trickle && range != null && range.startsWith("bytes=100-")) slowBodyReadStarted.countDown();
            }
        }).build();
        startServer(config(3, 3), guard);
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) server.close();
        if (upstream != null) upstream.close();
        if (http != null) {
            http.connectionPool().evictAll();
            http.dispatcher().executorService().shutdownNow();
        }
    }

    @Test
    public void evilOriginIsForbiddenWithoutUpstreamContact() throws Exception {
        assertRejected(403, "", Map.of("Origin", "http://evil.example"), "GET");
    }

    @Test
    public void crossSiteFetchIsForbiddenWithoutUpstreamContact() throws Exception {
        assertRejected(403, "", Map.of("Sec-Fetch-Site", "cross-site"), "GET");
    }

    @Test
    public void sourceFreeRequestRelaysPartialContentAndHeaders() throws Exception {
        HttpResult result = request("", Map.of("Range", "bytes=0-23"), "GET");
        assertEquals(206, result.code());
        assertEquals("bytes 0-23/24", result.contentRange());
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
    }

    @Test
    public void foreignHostAndWrongLoopbackPortAreForbidden() throws Exception {
        assertRejected(403, "", Map.of("Host", "evil.example:" + server.actualPort()), "GET");
        assertRejected(403, "", Map.of("Host", "127.0.0.1:1"), "GET");
        assertRejected(403, "", Map.of("Host", "127.0.0.1"), "GET");
    }

    @Test
    public void localOriginMustMatchListeningAuthority() throws Exception {
        assertRejected(403, "", Map.of("Origin", "http://127.0.0.1:1"), "GET");
        HttpResult result = request("", Map.of("Origin", "http://127.0.0.1:" + server.actualPort()), "GET");
        assertEquals(200, result.code());
    }

    @Test
    public void invalidOverridesAndDuplicateParametersAreBadRequestsEvenWithoutRangeOrForHead() throws Exception {
        for (String parameters : List.of("&thread=0", "&thread=abc", "&shards=4&chunkSize=1",
                "&url=" + encodedTarget(), "&thread=1&thread=2", "&shards=1&shards=2",
                "&chunkSize=1&chunkSize=2", "&thread=5", "&chunkSize=1")) {
            assertRejected(400, parameters, Map.of(), "GET");
            assertRejected(400, parameters, Map.of(), "HEAD");
        }
    }

    @Test
    public void fourShardsUseRealParallelismWithoutMutatingGlobalConfig() throws Exception {
        measureParallel = true;
        HttpResult result = request("&thread=4&shards=4", Map.of("Range", "bytes=0-23"), "GET");
        assertEquals(206, result.code());
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
        assertTrue(ranges.containsAll(List.of("bytes=0-5", "bytes=6-11", "bytes=12-17", "bytes=18-23")));
        assertTrue("expected overlapping shard requests", peakShards.get() >= 2);
        assertEquals(2, server.config().rangeConcurrency());
    }

    @Test
    public void internalLeaseReservesLastSessionForSingleAndRangeRequests() throws Exception {
        startServer(config(3, 2), guard);
        try (ProxySessionRegistry.Lease lease = server.sessions().tryAcquire(() -> {}).orElseThrow()) {
            HttpResult single = request("", Map.of(), "GET");
            HttpResult range = request("", Map.of("Range", "bytes=0-23"), "GET");
            assertEquals(503, single.code());
            assertEquals("1", single.retryAfter());
            assertEquals(503, range.code());
            assertEquals("1", range.retryAfter());
            assertEquals(0, upstream.getRequestCount());
        }
    }

    @Test
    public void privateLiteralIsBadRequestWithoutUpstreamContact() throws Exception {
        HttpResult result = requestUrl(publicUrl("http://10.1.2.3/v.mp4", ""), Map.of(), "GET");
        assertEquals(400, result.code());
        assertEquals(0, upstream.getRequestCount());
    }

    @Test
    public void noRangeAndHeadUseSingleConnectionAndReleaseAdmission() throws Exception {
        startServer(config(2, 3), guard);
        for (int i = 0; i < 2; i++) {
            HttpResult get = request("&thread=4&shards=4", Map.of(), "GET");
            assertEquals(200, get.code());
            assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), get.body());
            HttpResult head = request("&thread=4&shards=4", Map.of("Range", "bytes=0-23"), "HEAD");
            assertEquals(200, head.code());
            assertEquals(0, head.body().length);
        }
        assertEquals(List.of("GET", "HEAD", "GET", "HEAD"), ranges);
    }

    @Test
    public void legacyProxyStillRequiresToken() throws Exception {
        HttpResult result = requestUrl("http://127.0.0.1:" + server.actualPort()
                + "/v1/proxy?url=" + encodedTarget(), Map.of(), "GET");
        assertEquals(401, result.code());
        assertEquals(0, upstream.getRequestCount());
    }

    @Test
    public void methodCheckPrecedesOriginAndTargetValidation() throws Exception {
        assertRejected(405, "&thread=0", Map.of("Origin", "http://evil.example"), "POST");
    }

    @Test
    public void privateRedirectAndCredentialsAndFragmentAreBlocked() throws Exception {
        redirect = "http://10.1.2.3/private";
        assertEquals(502, request("", Map.of(), "GET").code());
        assertEquals(1, upstream.getRequestCount());
        redirect = "http://user:secret@asset.example:" + upstream.getPort() + "/private";
        assertEquals(502, request("", Map.of(), "GET").code());
        assertEquals(2, upstream.getRequestCount());
        redirect = "http://asset.example:" + upstream.getPort() + "/private#fragment";
        assertEquals(502, request("", Map.of(), "GET").code());
        assertEquals(3, upstream.getRequestCount());
    }

    @Test
    public void credentialsAndFragmentsAreRejectedBeforeUpstreamContact() throws Exception {
        for (String target : List.of("http://user:secret@asset.example/video", "http://asset.example/video#private")) {
            assertEquals(400, requestUrl(publicUrl(target, ""), Map.of(), "GET").code());
        }
        assertEquals(0, upstream.getRequestCount());
    }

    @Test
    public void slowPublicDnsCannotConsumeReservedHttpWorker() throws Exception {
        CountDownLatch resolving = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        InetAddress address = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        UpstreamUrlGuard slowGuard = new UpstreamUrlGuard(host -> {
            resolving.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test DNS timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new InetAddress[]{address};
        });
        startServer(config(2, 3), slowGuard);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<HttpResult> pending = executor.submit(() -> request("", Map.of(), "GET"));
        try {
            assertTrue(resolving.await(2, TimeUnit.SECONDS));
            HttpResult rejected = request("", Map.of(), "GET");
            assertEquals(503, rejected.code());
            assertEquals("1", rejected.retryAfter());
            assertEquals(200, requestUrl("http://127.0.0.1:" + server.actualPort() + "/v1/health", Map.of(), "GET").code());
            assertEquals(200, requestUrl("http://127.0.0.1:" + server.actualPort() + "/v1/proxy?url=" + encodedTarget(),
                    Map.of(MultiThreadProxyServer.TOKEN_HEADER, capabilityToken.value()), "GET").code());
        } finally {
            release.countDown();
            pending.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
        assertEquals(200, request("", Map.of(), "GET").code());
    }

    @Test
    public void blockedPublicShardsLeaveSharedRangeWorkerForInternalTraffic() throws Exception {
        blockPublicShards = true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<HttpResult> pending = executor.submit(() -> requestUrl(
                publicUrl("http://asset.example:" + upstream.getPort() + "/slow", "&thread=4&shards=4"),
                Map.of("Range", "bytes=0-23"), "GET"));
        try {
            assertTrue(publicShardsStarted.await(2, TimeUnit.SECONDS));
            Future<HttpResult> internal = executor.submit(() -> requestUrl("http://127.0.0.1:" + server.actualPort()
                    + "/v1/proxy?url=" + encodedTarget(), Map.of("Range", "bytes=0-23",
                    MultiThreadProxyServer.TOKEN_HEADER, capabilityToken.value()), "GET"));
            HttpResult result = internal.get(2, TimeUnit.SECONDS);
            assertEquals(206, result.code());
            assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
        } finally {
            releasePublicShards.countDown();
            assertEquals(206, pending.get(5, TimeUnit.SECONDS).code());
            executor.shutdownNow();
        }
    }

    @Test
    public void fallbackReleasesPublicAdmissionAndUsesSafeSingleConnection() throws Exception {
        startServer(config(2, 3), guard);
        ignoreRanges = true;
        for (int i = 0; i < 2; i++) {
            HttpResult result = request("", Map.of("Range", "bytes=0-23"), "GET");
            assertEquals(200, result.code());
            assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
        }
        assertEquals(4, upstream.getRequestCount());
    }

    @Test
    public void secureClientRejectsHttpsDowngradeBeforeSecondCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OkHttpClient redirectClient = http.newBuilder().addInterceptor(chain -> {
            calls.incrementAndGet();
            return new Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(302).message("Found").header("Location", "http://asset.example/plain")
                    .body(okhttp3.ResponseBody.create(new byte[0], null)).build();
        }).build();
        try (UpstreamRangeClient client = new UpstreamRangeClient(redirectClient, config(3, 3), guard)) {
            IOException failure = org.junit.Assert.assertThrows(IOException.class,
                    () -> client.singleRequest("https://asset.example/video", Map.of(), null, false).execute());
            assertEquals("blocked upstream target", failure.getMessage());
            assertEquals(1, calls.get());
        }
    }

    @Test
    public void closingPublicClientCancelsBlockedUpstreamShardAndReleasesAdmission() throws Exception {
        payload = new byte[4 * 1024 * 1024];
        trickle = true;
        startServer(config(2, 3, 8L * 1024 * 1024, 1024 * 1024), guard);
        NanoHTTPD.Response response = server.serve(rangeSession());
        try {
            assertEquals(206, response.getStatus().getRequestStatus());
            assertTrue(slowBodyReadStarted.await(3, TimeUnit.SECONDS));
            assertTrue(response.getData().read() >= 0);
        } finally {
            response.getData().close();
        }
        awaitAtLeast(upstreamCancelled, 1, 2_000);
        assertEquals(200, request("", Map.of(), "HEAD").code());
        assertEquals(200, requestUrl("http://127.0.0.1:" + server.actualPort()
                + "/v1/proxy?url=" + encodedTarget(),
                Map.of(MultiThreadProxyServer.TOKEN_HEADER, capabilityToken.value()), "HEAD").code());
    }

    private NanoHTTPD.IHTTPSession rangeSession() {
        Map<String, String> parameters = Map.of("url", target(), "thread", "4", "shards", "4");
        Map<String, String> headers = Map.of("host", "127.0.0.1:" + server.actualPort(),
                "range", "bytes=0-" + (payload.length - 1));
        return new NanoHTTPD.IHTTPSession() {
            @Override public void execute() { throw new UnsupportedOperationException(); }
            @Override public NanoHTTPD.CookieHandler getCookies() { throw new UnsupportedOperationException(); }
            @Override public Map<String, String> getHeaders() { return headers; }
            @Override public InputStream getInputStream() { throw new UnsupportedOperationException(); }
            @Override public NanoHTTPD.Method getMethod() { return NanoHTTPD.Method.GET; }
            @Override public Map<String, String> getParms() { return parameters; }
            @Override public Map<String, List<String>> getParameters() {
                return Map.of("url", List.of(target()), "thread", List.of("4"), "shards", List.of("4"));
            }
            @Override public String getQueryParameterString() { return "url=" + encodedTarget(); }
            @Override public String getUri() { return MultiThreadProxyServer.PUBLIC_PATH; }
            @Override public void parseBody(Map<String, String> files) { throw new UnsupportedOperationException(); }
            @Override public String getRemoteIpAddress() { return "127.0.0.1"; }
            @Override public String getRemoteHostName() { return "localhost"; }
        };
    }
    @Test
    public void publicRouteAppliesDomainRulesEvenWhenAutomaticProxyIsDisabled() throws Exception {
        payload = new byte[64 * 1024];
        ProxyDomainRuleSet rules = ProxyDomainRuleSet.parse("asset.example=3,4");
        startServer(config(3, 3, 1024 * 1024, 16 * 1024), guard, rules);

        HttpResult plain = request("", Map.of("Range", "bytes=0-65535"), "GET");
        assertEquals(206, plain.code());
        assertArrayEquals(payload, plain.body());
        assertEquals(6, ranges.size());
        assertTrue(ranges.containsAll(List.of("bytes=0-99", "bytes=100-16483")));

        ranges.clear();
        assertEquals(206, request("&thread=2", Map.of("Range", "bytes=0-65535"), "GET").code());
        assertEquals(6, ranges.size());

        ranges.clear();
        assertEquals(206, request("&chunkSize=4", Map.of("Range", "bytes=0-65535"), "GET").code());
        assertTrue(ranges.contains("bytes=100-4195"));
        assertEquals(18, ranges.size());

        assertFalse(server.config().enabled());
        assertEquals(2, server.config().rangeConcurrency());
        assertEquals(ProxyRuntimeConfig.AUTO_SHARD_COUNT, server.config().shardCount());
    }

    @Test
    public void productionPublicClientPinsNoProxyAndResolvesThroughGuard() throws Exception {
        OkHttpClient client = MultiThreadProxyServer.buildPublicClient(config(3, 3), guard);
        assertEquals(Proxy.NO_PROXY, client.proxy());
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        assertArrayEquals(new InetAddress[]{publicAddress}, client.dns().lookup("asset.example").toArray(new InetAddress[0]));

        InetAddress lanAddress = InetAddress.getByAddress(new byte[]{10, 1, 2, 3});
        OkHttpClient privateClient = MultiThreadProxyServer.buildPublicClient(config(3, 3),
                new UpstreamUrlGuard(host -> new InetAddress[]{lanAddress}));
        assertThrows(java.net.UnknownHostException.class, () -> privateClient.dns().lookup("asset.example"));
        assertThrows(java.net.UnknownHostException.class, () -> privateClient.dns().lookup("10.1.2.3"));

        InetAddress siteLocal = InetAddress.getByAddress(new byte[]{(byte) 192, (byte) 168, 1, 1});
        OkHttpClient mixedClient = MultiThreadProxyServer.buildPublicClient(config(3, 3),
                new UpstreamUrlGuard(host -> new InetAddress[]{publicAddress, siteLocal}));
        assertThrows(java.net.UnknownHostException.class, () -> mixedClient.dns().lookup("asset.example"));
    }

    private static void awaitAtLeast(AtomicInteger counter, int expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (counter.get() < expected && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue("expected at least " + expected + " but was " + counter.get(), counter.get() >= expected);
    }

    private void startServer(ProxyRuntimeConfig config, UpstreamUrlGuard urlGuard) throws Exception {
        startServer(config, urlGuard, ProxyDomainRuleSet.EMPTY);
    }

    private void startServer(
            ProxyRuntimeConfig config,
            UpstreamUrlGuard urlGuard,
            ProxyDomainRuleSet rules) throws Exception {
        if (server != null) server.close();
        OkHttpClient localClient = injected.newBuilder()
                .dns(host -> List.of(InetAddress.getByName("127.0.0.1"))).build();
        server = new MultiThreadProxyServer(config, capabilityToken, 7,
                new UpstreamRangeClient(localClient, config), rules, urlGuard, localClient);
        server.startServer();
    }

    private void assertRejected(int code, String parameters, Map<String, String> headers, String method) throws Exception {
        assertEquals(code, request(parameters, headers, method).code());
        assertEquals(0, upstream.getRequestCount());
    }

    private HttpResult request(String parameters, Map<String, String> headers, String method) throws IOException {
        return requestUrl(publicUrl(target(), parameters), headers, method);
    }

    private String target() {
        return "http://asset.example:" + upstream.getPort() + "/video.mp4";
    }

    private String encodedTarget() {
        return URLEncoder.encode(target(), StandardCharsets.UTF_8);
    }

    private String publicUrl(String target, String parameters) {
        return "http://127.0.0.1:" + server.actualPort() + "/proxy?url="
                + URLEncoder.encode(target, StandardCharsets.UTF_8) + parameters;
    }

    private HttpResult requestUrl(String url, Map<String, String> headers, String method) throws IOException {
        Request.Builder request = new Request.Builder().url(url).method(method,
                "POST".equals(method) ? okhttp3.RequestBody.create(new byte[0], null) : null);
        headers.forEach(request::header);
        try (Response response = http.newCall(request.build()).execute()) {
            return new HttpResult(response.code(), response.header("Content-Range"), response.header("Retry-After"),
                    response.body() == null ? new byte[0] : response.body().bytes());
        }
    }

    private static ProxyRuntimeConfig config(int serverWorkers, int maxSessions) {
        return config(serverWorkers, maxSessions, 96);
    }

    private static ProxyRuntimeConfig config(int serverWorkers, int maxSessions, long bufferBudgetBytes) {
        return config(serverWorkers, maxSessions, bufferBudgetBytes, 6);
    }

    private static ProxyRuntimeConfig config(
            int serverWorkers,
            int maxSessions,
            long bufferBudgetBytes,
            long chunkSizeBytes) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(defaults.schemaVersion(), false, ProxyRuntimeConfig.PortMode.AUTO, 0,
                serverWorkers, defaults.connectionQueueCapacity(), 4, 2,
                ProxyRuntimeConfig.ShardMode.SIZE, ProxyRuntimeConfig.AUTO_SHARD_COUNT, chunkSizeBytes,
                maxSessions, 4, bufferBudgetBytes, 0, defaults.connectTimeoutMillis(),
                defaults.readTimeoutMillis(), 0);
    }

    private record HttpResult(int code, String contentRange, String retryAfter, byte[] body) {
    }
}
