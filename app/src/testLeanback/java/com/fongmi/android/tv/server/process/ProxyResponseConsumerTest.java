package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fongmi.quickjs.crawler.Spider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ProxyResponseConsumerTest {

    @Test
    public void legacyFourSlotResponseKeepsItsStatusMimeAndHeaders() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", "/legacy");
        Object[] legacy = new Object[]{200, "text/plain", new java.io.ByteArrayInputStream(new byte[]{'o', 'k'}), headers};
        String wire = send(consume(legacy, Map.of()));
        assertTrue(wire, wire.startsWith("HTTP/1.1 200 "));
        assertEquals("/legacy", header(wire, "Location"));
        assertTrue(wire, wire.endsWith("2\r\nok\r\n0\r\n\r\n"));
    }

    @Test
    public void nonIntegerStatusStillFallsBackToInvalidResponse() throws Exception {
        Object[] invalid = new Object[]{"abc", "text/plain", new java.io.ByteArrayInputStream(new byte[]{'x'})};
        String wire = send(consume(invalid, Map.of()));
        assertTrue(wire, wire.startsWith("HTTP/1.1 500 "));
        assertTrue(wire, wire.endsWith("Invalid proxy response"));
    }

    @Test
    public void catvodPartialResponseKeepsContentRangeOnTheWire() throws Exception {
        Object[] result = fixture("{\"code\":206,\"content\":\"test\",\"headers\":{\"Content-Type\":\"video/mp2t\",\"Content-Range\":\"bytes 0-3/4\"}}").proxy(catvodParams());
        String wire = send(consume(result, Map.of("range", "bytes=0-3")));
        assertTrue(wire, wire.startsWith("HTTP/1.1 206 "));
        assertEquals(wire, "bytes 0-3/4", header(wire, "Content-Range"));
    }

    @Test
    public void catvodHeadersUseOneContentTypeAndNanoOwnedFraming() throws Exception {
        Object[] result = fixture("{\"code\":302,\"content\":\"test\",\"headers\":{\"Content-Type\":\"video/mp2t\",\"Content-Length\":\"4\",\"Transfer-Encoding\":\"identity\",\"Location\":\"/next\"}}").proxy(catvodParams());
        String wire = send(consume(result, Map.of()));
        assertEquals(wire, 1, headerCount(wire, "Content-Type"));
        assertEquals(wire, 0, headerCount(wire, "Content-Length"));
        assertEquals(wire, 1, headerCount(wire, "Transfer-Encoding"));
        assertEquals("chunked", header(wire, "Transfer-Encoding"));
        assertEquals("/next", header(wire, "Location"));
    }

    @Test
    public void catvodBase64BodyAndExtendedStatusRemainExactOnTheWire() throws Exception {
        Object[] result = fixture("{\"code\":599,\"buffer\":2,\"content\":\"AH+A/w==\"}").proxy(catvodParams());
        String wire = send(consume(result, Map.of()));
        assertTrue(wire, wire.startsWith("HTTP/1.1 599 "));
        String body = new String(new byte[]{0, 127, (byte) 128, (byte) 255}, StandardCharsets.ISO_8859_1);
        assertTrue(wire, wire.endsWith("4\r\n" + body + "\r\n0\r\n\r\n"));
    }

    @Test
    public void threeAndFiveSlotResultsKeepTheServerContract() throws Exception {
        for (int size : new int[]{3, 5}) {
            Object[] result = new Object[size];
            result[0] = 600;
            result[1] = "text/plain";
            result[2] = new java.io.ByteArrayInputStream(new byte[]{'x'});
            if (size == 5) result[4] = 1;
            assertTrue(send(consume(result, Map.of())).startsWith("HTTP/1.1 500 "));
        }
    }

    private static int headerCount(String wire, String name) {
        int count = 0;
        for (String line : wire.substring(0, wire.indexOf("\r\n\r\n")).split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && name.equalsIgnoreCase(line.substring(0, colon))) count++;
        }
        return count;
    }

    private static Spider fixture(String json) {
        return new Spider("https://example.invalid/spider.js", null) {
            @Override
            protected String callProxy2(Map<String, String> params) {
                return json;
            }
        };
    }

    private static Map<String, String> catvodParams() {
        Map<String, String> params = new HashMap<>();
        params.put("from", "catvod");
        params.put("url", "https://example.invalid/video");
        params.put("header", "{}");
        return params;
    }

    private static NanoHTTPD.Response consume(Object[] result, Map<String, String> params) throws Exception {
        Method method = Proxy.class.getDeclaredMethod("createResponse", Map.class, String.class, Object[].class);
        method.setAccessible(true);
        return (NanoHTTPD.Response) method.invoke(new Proxy(), params, "/proxy", result);
    }

    private static String send(NanoHTTPD.Response response) throws Exception {
        response.setRequestMethod(NanoHTTPD.Method.GET);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Method method = NanoHTTPD.Response.class.getDeclaredMethod("send", java.io.OutputStream.class);
        method.setAccessible(true);
        method.invoke(response, output);
        return output.toString(StandardCharsets.ISO_8859_1);
    }

    private static String header(String wire, String name) {
        String headers = wire.substring(0, wire.indexOf("\r\n\r\n"));
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && name.equalsIgnoreCase(line.substring(0, colon))) return line.substring(colon + 1).trim();
        }
        return null;
    }
}
