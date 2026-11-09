package com.fongmi.android.tv.playback.origin;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class OriginRelayTargetTest {
    private String lease;
    private final Map<String, String> bob = Map.of("Authorization", "Basic Ym9iOnNlY3JldA==",
            "Cookie", "sid=bob", "Referer", "https://same.example/history/");

    @Before
    public void openOwnedRelay() throws Exception {
        lease = UUID.randomUUID().toString();
        OriginMediaRelay.begin(lease);
    }

    @After
    public void closeOwnedRelay() {
        OriginMediaRelay.close(lease);
    }

    @Test
    public void sameHostTargetsKeepTheirOwnAccountsAndCopyHeadersAtRegistration() throws Exception {
        String other = UUID.randomUUID().toString();
        OriginMediaRelay.begin(other);
        try {
            Map<String, String> original = new LinkedHashMap<>(bob);
            String b = OriginMediaRelay.register(lease, "https://same.example/media", original);
            String a = OriginMediaRelay.register(other, "https://same.example/media", Map.of("Cookie", "sid=alice"));
            original.put("Cookie", "sid=alice");
            assertEquals(bob, OriginMediaRelay.headers(lease, b));
            assertEquals(Map.of("Cookie", "sid=alice"), OriginMediaRelay.headers(other, a));
            Map<String, String> exported = OriginMediaRelay.headers(lease, b);
            exported.clear();
            assertEquals("callers cannot mutate the registered origin account", bob, OriginMediaRelay.headers(lease, b));
        } finally {
            OriginMediaRelay.close(other);
        }
    }

    @Test
    public void hlsKeyMapVariantAndSegmentInheritTheRegisteredOriginsHeaders() {
        String base = "https://same.example/b/variant.m3u8?sig=keep%2Fexact";
        OriginMediaRelay.register(lease, base, bob);
        String hls = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"audio.m3u8\"\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"../keys/key.bin?sig=a%2Fb\"\n"
                + "#EXT-X-MAP:URI=\"init.mp4?token=unchanged\"\n"
                + "#EXTINF:2,\nseg.m4s?x=1&signed=%2B\n";
        String rewritten = OriginMediaRelay.rewrite(hls, base, lease, bob);
        assertTrue("KEY syntax is retained, not replaced by a bare URL", rewritten.contains("#EXT-X-KEY:METHOD=AES-128,URI=\""));
        assertTrue("MAP syntax is retained", rewritten.contains("#EXT-X-MAP:URI=\""));
        List<String> targets = urls(rewritten);
        assertEquals("variant, key, map and segment are all registered", 4, targets.size());
        String[] expected = {"https://same.example/b/audio.m3u8", "https://same.example/keys/key.bin?sig=a%2Fb",
                "https://same.example/b/init.mp4?token=unchanged", "https://same.example/b/seg.m4s?x=1&signed=%2B"};
        for (int i = 0; i < expected.length; i++) {
            String id = targetId(targets.get(i));
            assertEquals("relative resolution and signed query are retained", expected[i], OriginMediaRelay.sourceUrl(lease, id));
            assertEquals("nested target must use bob, never the current host account: " + expected[i], bob,
                    OriginMediaRelay.headers(lease, id));
        }
    }

    @Test
    public void dashXmlIsNotMistakenForOneUrlAndKeepsOriginalTargetHeaders() throws Exception {
        String base = "https://same.example/b/manifest.mpd";
        OriginMediaRelay.register(lease, base, bob);
        String xml = "<MPD><Period><AdaptationSet><Representation>"
                + "<BaseURL>video/</BaseURL><SegmentList><Initialization sourceURL=\"init.mp4\"/>"
                + "<SegmentURL media=\"one.m4s?sig=%2B\"/></SegmentList>"
                + "</Representation></AdaptationSet></Period></MPD>";
        String rewritten = OriginMediaRelay.rewrite(xml, base, lease, bob);
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(rewritten.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("the MPD root and XML structure must survive", "MPD", document.getDocumentElement().getTagName());
        assertTrue("BaseURL remains an XML element", rewritten.contains("<BaseURL>") && rewritten.contains("</BaseURL>"));
        assertTrue("initialization retains its XML attribute", rewritten.contains("sourceURL=\""));
        assertTrue("segment retains its XML attribute", rewritten.contains("media=\""));
        assertEquals("base, init and segment must all be mapped", 3, urls(rewritten).size());
        for (String url : urls(rewritten)) {
            assertEquals("DASH child target must inherit the origin account", bob,
                    OriginMediaRelay.headers(lease, targetId(url)));
        }
        assertFalse("an entire XML document is not a source URL",
                OriginMediaRelay.sourceUrl(lease, "t2").contains("<MPD>"));
    }

    @Test
    public void dashTemplateExpandsRelativePathAndSignedQueryInOriginalCoordinates() throws Exception {
        String xml = "<MPD><SegmentTemplate media=\"$RepresentationID$/chunk-$Number$.m4s?time=$Time$&amp;sig=%2B\"/></MPD>";
        String rewritten = OriginMediaRelay.rewrite(xml, "https://same.example/b/manifest.mpd", lease, bob);
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(rewritten.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String local = ((org.w3c.dom.Element) document.getElementsByTagName("SegmentTemplate").item(0)).getAttribute("media");
        String expanded = local.replace("$RepresentationID$", "video").replace("$Number$", "3").replace("$Time$", "900");
        String id = targetId(expanded);
        String rest = expanded.substring(expanded.indexOf("/" + id + "/") + id.length() + 2);
        java.lang.reflect.Field sessions = OriginMediaRelay.class.getDeclaredField("SESSIONS");
        sessions.setAccessible(true);
        Object session = ((Map<?, ?>) sessions.get(null)).get(lease);
        java.lang.reflect.Field targets = session.getClass().getDeclaredField("targets");
        targets.setAccessible(true);
        Object target = ((Map<?, ?>) targets.get(session)).get(id);
        java.lang.reflect.Method request = target.getClass().getDeclaredMethod("requestUrl", String.class);
        request.setAccessible(true);
        try {
            assertEquals("the real registered target must resolve path and query placeholders",
                    "https://same.example/b/video/chunk-3.m4s?time=900&sig=%2B", request.invoke(target, rest));
        } catch (java.lang.reflect.InvocationTargetException failure) {
            fail("the registered target rejected a valid DASH request: " + failure.getCause());
        }
        assertEquals("template targets must retain the original account", bob, OriginMediaRelay.headers(lease, id));
    }

    @Test
    public void realListenerRejectsForeignTokenAndUnregisteredTargetBeforeAnyUpstreamRequest() throws Exception {
        String other = UUID.randomUUID().toString();
        OriginMediaRelay.Endpoint b = OriginMediaRelay.begin(lease);
        OriginMediaRelay.begin(other);
        try {
            String target = OriginMediaRelay.register(lease, "https://same.example/no-network-required", bob);
            String foreign = "http://127.0.0.1:" + b.port + OriginMediaRelay.localPath(other, target);
            assertEquals(403, status(foreign));
            assertEquals(404, status("http://127.0.0.1:" + b.port + OriginMediaRelay.localPath(lease, "not-registered")));
            assertEquals(403, status("http://127.0.0.1:" + b.port + "/?url=https://same.example/anything"));
        } finally {
            OriginMediaRelay.close(other);
        }
    }

    @Test
    public void closeIsIdempotentAndInvalidatesAllTargetsWithoutClosingAnotherLease() throws Exception {
        String other = UUID.randomUUID().toString();
        OriginMediaRelay.begin(other);
        String id = OriginMediaRelay.register(lease, "https://same.example/media", bob);
        String oldUrl = OriginMediaRelay.localUrl(lease, id);
        try {
            OriginMediaRelay.close(lease);
            OriginMediaRelay.close(lease);
            assertFalse(OriginMediaRelay.isBound(lease));
            assertEquals(0, OriginMediaRelay.port(lease));
            assertTrue("unrelated lease remains live", OriginMediaRelay.isBound(other));
            try {
                OriginMediaRelay.headers(lease, id);
                fail("closed targets must not expose origin credentials");
            } catch (IllegalStateException expected) {
                assertEquals(OriginSessionContract.PORT_OWNERSHIP_AMBIGUOUS, expected.getMessage());
            }
            try {
                status(oldUrl);
                fail("the closed relay must not remain reachable");
            } catch (IOException expected) {
                // A refused/closed socket is the external evidence, not a flag-only check.
            }
        } finally {
            OriginMediaRelay.close(other);
        }
    }

    private static List<String> urls(String text) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = Pattern.compile("http://127\\.0\\.0\\.1:[0-9]+/origin/[^\\s\"<>]+").matcher(text);
        while (matcher.find()) urls.add(matcher.group());
        return urls;
    }

    private static String targetId(String url) {
        Matcher target = Pattern.compile("/origin/[^/]+/(t\\d+)(?:/|\\?|$)").matcher(url);
        assertTrue("relay URL must name a registered target: " + url, target.find());
        return target.group(1);
    }

    private static int status(String url) throws IOException {
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setConnectTimeout(1500);
        request.setReadTimeout(1500);
        request.setInstanceFollowRedirects(false);
        try { return request.getResponseCode(); }
        finally { request.disconnect(); }
    }
}
