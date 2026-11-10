package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ProxyClientOriginPolicyTest {

    @Test
    public void originMustBeValidLocalHttpOrHttps() {
        assertOrigin(false, "http://evil.example");
        assertOrigin(false, null);
        assertOrigin(false, "null");
        assertOrigin(false, "NULL");
        assertOrigin(false, "file://");
        assertOrigin(false, "file:///tmp/player.html");
        assertOrigin(false, "http://[bad");
        assertOrigin(false, "not a url");
        assertOrigin(false, "");
        assertOrigin(false, "http://localhost.evil.example");
        assertOrigin(false, "https://127.0.0.1.evil.example");
        assertOrigin(false, "http://localhost@evil.example");
        assertOrigin(false, "http://[::1]");
        assertOrigin(false, "ftp://localhost");
        assertOrigin(false, "http://localhost:bad");
        assertOrigin(false, "http://localhost:-1");
        assertOrigin(false, "http:///localhost");
        assertOrigin(true, "http://127.0.0.1:17575");
        assertOrigin(true, "https://localhost");
        assertOrigin(true, "http://127.0.0.1");
        assertOrigin(true, "http://localhost/");
        assertOrigin(true, "https://127.0.0.1:17575/");
        assertOrigin(true, "HTTP://LOCALHOST:17575/");
    }

    @Test
    public void ordinaryUrlsAreNotAcceptedAsOrigins() {
        assertOrigin(false, "http://evil@localhost/x?q=1");
        assertOrigin(false, "http://user:password@127.0.0.1");
        assertOrigin(false, "http://localhost/player");
        assertOrigin(false, "http://localhost//");
        assertOrigin(false, "http://localhost?token=1");
        assertOrigin(false, "http://localhost#player");
        assertOrigin(false, "http://localhost?");
        assertOrigin(false, "http://localhost#");
        assertOrigin(false, "http://localhost:65536");
        assertOrigin(false, "http://localhost:999999999999");
        assertOrigin(true, "http://localhost:65535/");
    }

    @Test
    public void explicitPortCannotBeEmpty() {
        assertOrigin(false, "http://localhost:");
        assertOrigin(false, "http://127.0.0.1:/");
        assertOrigin(true, "http://localhost");
        assertOrigin(true, "http://localhost:0");
    }

    @Test
    public void surroundingHeaderWhitespaceIsTrimmed() {
        assertOrigin(true, "  HTTPS://LOCALHOST:17575/\t");
        assertOrigin(false, " null ");
        assertOrigin(false, " http://evil.example ");
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", "\t none ")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", " cross-site ")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", "\t cors ")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", " no-cors ")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", " navigate ")));
    }

    @Test
    public void requestsWithoutOriginMetadataAreAllowed() {
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of()));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("User-Agent", "Python-urllib/3.12")));
    }

    @Test
    public void fetchSiteMustBeNoneOrSameOrigin() {
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", "cross-site")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", "same-site")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", "unknown")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", "")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", "none")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Site", "same-origin")));
        Map<String, String> headers = new HashMap<>();
        headers.put("Sec-Fetch-Site", null);
        assertEquals(false, ProxyClientOriginPolicy.allows(headers));
    }

    @Test
    public void corsAndNoCorsModesAreRejected() {
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", "cors")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", "no-cors")));
    }

    @Test
    public void navigateAndOtherModesDoNotRejectOnTheirOwn() {
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", "navigate")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", "websocket")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("Sec-Fetch-Mode", "")));
        Map<String, String> headers = new HashMap<>();
        headers.put("Sec-Fetch-Mode", null);
        assertEquals(true, ProxyClientOriginPolicy.allows(headers));
    }

    @Test
    public void headerNamesAreCaseInsensitive() {
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("origin", "http://evil.example")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("oRiGiN", "HTTPS://LOCALHOST/")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("SEC-FETCH-SITE", "cross-site")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of("sec-fetch-site", "none")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of("sEc-FeTcH-MoDe", "no-cors")));
    }

    @Test
    public void everyOriginConstraintMustPass() {
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of(
                "Origin", "http://evil.example", "Sec-Fetch-Site", "none", "Sec-Fetch-Mode", "navigate")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of(
                "Origin", "https://localhost", "Sec-Fetch-Site", "cross-site", "Sec-Fetch-Mode", "navigate")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of(
                "Origin", "http://127.0.0.1:17575", "Sec-Fetch-Site", "same-origin", "Sec-Fetch-Mode", "cors")));
        assertEquals(true, ProxyClientOriginPolicy.allows(Map.of(
                "Origin", "https://localhost", "Sec-Fetch-Site", "same-origin", "Sec-Fetch-Mode", "navigate")));
    }

    @Test
    public void differentlyCasedDuplicateNamesCannotOverrideARejection() {
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of(
                "Origin", "http://localhost", "origin", "http://evil.example")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of(
                "Sec-Fetch-Site", "none", "sec-fetch-site", "cross-site")));
        assertEquals(false, ProxyClientOriginPolicy.allows(Map.of(
                "Sec-Fetch-Mode", "navigate", "sec-fetch-mode", "cors")));
    }

    private static void assertOrigin(boolean expected, String origin) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", origin);
        assertEquals("Origin: " + origin, expected, ProxyClientOriginPolicy.allows(headers));
    }
}
