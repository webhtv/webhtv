package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

public class ProxyRedirectResponseTest {

    private static final String INVALID_RESPONSE = "Invalid proxy response";

    @Test
    public void redirectMayHaveAnEmptyBody() throws Exception {
        assertTrue(canResponseHaveEmptyBody(301));
        assertTrue(canResponseHaveEmptyBody(302));
        assertTrue(canResponseHaveEmptyBody(307));
    }

    @Test
    public void nonRedirectStillRequiresAStreamBody() throws Exception {
        assertFalse(canResponseHaveEmptyBody(200));
        assertFalse(canResponseHaveEmptyBody(204));
        assertFalse(canResponseHaveEmptyBody(400));
    }

    @Test
    public void shortResponsesReturnServerErrorWithoutThrowing() throws Exception {
        assertEquals(INVALID_RESPONSE, responseBody(createResponse(new Object[] {302})));
        assertEquals(INVALID_RESPONSE, responseBody(createResponse(new Object[] {302, "text/html"})));
    }

    @Test
    public void redirectWithEmptyBodyRequiresLocationHeader() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", "https://example.com/video");

        Response response = createResponse(new Object[] {302, "text/html", null, headers});

        assertEquals(Response.Status.FOUND, response.getStatus());
        assertEquals("https://example.com/video", response.getHeader("Location"));
        assertEquals(0, responseBody(response).length());
    }

    @Test
    public void redirectWithoutLocationHeaderIsRejected() throws Exception {
        Response response = createResponse(new Object[] {302, "text/html", null});

        assertEquals(INVALID_RESPONSE, responseBody(response));
    }

    @Test
    public void streamResponseKeepsBodyAndHeader() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "video/mp4");
        InputStream stream = new ByteArrayInputStream("video".getBytes(StandardCharsets.UTF_8));

        Response response = createResponse(new Object[] {200, "video/mp4", stream, headers});

        assertEquals(Response.Status.OK, response.getStatus());
        assertEquals("video/mp4", response.getHeader("Content-Type"));
        assertEquals("video", responseBody(response));
    }

    @Test
    public void objectResponseIsReturnedDirectly() throws Exception {
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "ok");

        assertSame(response, createResponse(new Object[] {response}));
    }

    @Test
    public void missingResponseReturnsServerError() throws Exception {
        assertEquals(INVALID_RESPONSE, responseBody(createResponse(null)));
        assertEquals(INVALID_RESPONSE, responseBody(createResponse(new Object[0])));
    }

    private static Response createResponse(Object[] rs) throws Exception {
        Proxy proxy = new Proxy();
        Method method = Proxy.class.getDeclaredMethod("createResponse", Map.class, String.class, Object[].class);
        method.setAccessible(true);
        return (Response) method.invoke(proxy, new HashMap<>(), "/proxy", rs);
    }

    private static String responseBody(Response response) throws Exception {
        InputStream stream = response.getData();
        assertNotNull(stream);
        byte[] buffer = new byte[8192];
        int length = stream.read(buffer);
        return length < 0 ? "" : new String(buffer, 0, length, StandardCharsets.UTF_8);
    }

    private static boolean canResponseHaveEmptyBody(int code) throws Exception {
        Method method = Proxy.class.getDeclaredMethod("canResponseHaveEmptyBody", int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, code);
    }
}
