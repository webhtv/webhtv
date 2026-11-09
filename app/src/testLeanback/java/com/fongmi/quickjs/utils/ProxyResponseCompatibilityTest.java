package com.fongmi.quickjs.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ProxyResponseCompatibilityTest {

    @Test
    public void topLevelJsonNullIsARejectedResponseInsteadOfACrash() {
        assertThrows(IllegalArgumentException.class, () -> ProxyResponse.fromJson("null"));
    }

    @Test
    public void dangerousHeaderCharactersAreRejectedBeforeTheWire() {
        assertThrows(IllegalArgumentException.class, () -> ProxyResponse.fromJson("{\"headers\":{\"Location\":\"/a\\r\\nSet-Cookie: x\"}}"));
        assertThrows(IllegalArgumentException.class, () -> ProxyResponse.fromJson("{\"headers\":{\"X-Test\":\"a\\u0000b\"}}"));
        assertThrows(IllegalArgumentException.class, () -> ProxyResponse.fromJson("{\"headers\":{\"Content-Type\":\"text/plain\\nX: y\"}}"));
    }

    @Test
    public void trailingControlCharactersAreRejectedBeforeLegacyTrimming() {
        assertThrows(IllegalArgumentException.class, () -> ProxyResponse.fromJson("{\"headers\":{\"Location\":\"/next\\r\"}}"));
        assertThrows(IllegalArgumentException.class, () -> ProxyResponse.fromJson("{\"headers\":{\"Content-Type\":\"text/plain\\u0000\"}}"));
    }

    @Test
    public void contentTypeUsesHttpCaseInsensitiveNameBeforeFiltering() {
        Object[] result = ProxyResponse.fromJson("{\"headers\":{\"cOnTeNt-TyPe\":\"video/mp2t; charset=utf-8\"}}");
        assertEquals("video/mp2t; charset=utf-8", result[1]);
        assertTrue(((Map<?, ?>) result[3]).isEmpty());
    }

    @Test
    public void legacyBodyConverterPreservesBytesBase64AndDataUri() throws Exception {
        com.fongmi.quickjs.crawler.Spider spider = new com.fongmi.quickjs.crawler.Spider("fixture", null);
        java.lang.reflect.Method converter = com.fongmi.quickjs.crawler.Spider.class.getDeclaredMethod("getStream", Object.class, boolean.class);
        converter.setAccessible(true);
        byte[] expected = new byte[]{0, 127, (byte) 128, (byte) 255};
        for (Object body : new Object[]{expected, "AH+A/w==", "data:application/octet-stream;base64,AH+A/w=="}) {
            try (InputStream stream = (InputStream) converter.invoke(spider, body, !(body instanceof byte[]))) {
                assertArrayEquals(expected, stream.readAllBytes());
            }
        }
    }

    @Test
    public void base64BodyPreservesNonUtf8Octets() throws Exception {
        assertArrayEquals(new byte[]{(byte) 255, (byte) 254, 0, (byte) 128}, bytes(ProxyResponse.fromJson("{\"buffer\":2,\"content\":\"//4AgA==\"}")));
        assertArrayEquals(new byte[0], bytes(ProxyResponse.fromJson("{\"buffer\":2,\"content\":\"\"}")));
    }

    @Test
    public void malformedHeaderValuesKeepExistingEmptyStringFallback() {
        Object[] result = ProxyResponse.fromJson("{\"headers\":{\"Number\":3,\"Boolean\":true,\"Array\":[\"x\"],\"Object\":{},\"Null\":null}}");
        Map<?, ?> headers = (Map<?, ?>) result[3];
        assertEquals("3", headers.get("Number"));
        assertEquals("true", headers.get("Boolean"));
        assertEquals("", headers.get("Array"));
        assertEquals("", headers.get("Object"));
        assertEquals("", headers.get("Null"));
    }

    @Test
    public void jsonNullContentBecomesAnEmptyBody() throws Exception {
        Object[] result = ProxyResponse.fromJson("{\"code\":200,\"content\":null}");
        assertEquals(0, read(result));
    }

    @Test
    public void emptyResponseKeepsLegacyStatusAndMimeDefaults() throws Exception {
        Object[] result = ProxyResponse.fromJson("{}");
        assertEquals(200, result[0]);
        assertEquals("application/octet-stream", result[1]);
        assertEquals(0, read(result));
        assertTrue(((Map<?, ?>) result[3]).isEmpty());
    }

    @Test
    public void unknownStatusAndCoercibleValuesStayTolerant() throws Exception {
        Object[] result = ProxyResponse.fromJson("{\"code\":\"599\",\"headers\":\"{\\\"ETag\\\":\\\"x\\\"}\"}");
        assertEquals(599, result[0]);
        assertEquals("x", ((Map<?, ?>) result[3]).get("ETag"));
    }

    @Test
    public void nonObjectHeadersFallBackInsteadOfFailing() throws Exception {
        Object[] result = ProxyResponse.fromJson("{\"headers\":5}");
        assertTrue(((Map<?, ?>) result[3]).isEmpty());
    }

    @Test
    public void nonUtf8TextKeepsItsBytesAndLiteralNullStaysText() throws Exception {
        Object[] text = ProxyResponse.fromJson("{\"content\":\"\\u00e9\"}");
        assertArrayEquals(new byte[]{(byte) 0xc3, (byte) 0xa9}, bytes(text));
        Object[] literal = ProxyResponse.fromJson("{\"content\":\"null\"}");
        assertArrayEquals(new byte[]{'n', 'u', 'l', 'l'}, bytes(literal));
    }

    @Test
    public void contentEncodingIsDroppedBecauseTheBodyIsAlreadyDecoded() {
        Object[] result = ProxyResponse.fromJson("{\"code\":206,\"headers\":{\"Content-Encoding\":\"gzip\",\"content-encoding\":\"br\",\"Content-Type\":\"text/plain\",\"ETag\":\"x\"},\"content\":\"plain\"}");
        assertEquals(206, result[0]);
        assertEquals("text/plain", result[1]);
        Map<?, ?> headers = (Map<?, ?>) result[3];
        assertFalse(headers.containsKey("Content-Encoding"));
        assertFalse(headers.containsKey("content-encoding"));
        assertFalse(headers.containsKey("Content-Type"));
        assertEquals("x", headers.get("ETag"));
    }

    @Test
    public void nonObjectTopLevelJsonIsARejectedResponse() {
        for (String json : new String[]{"[]", "123", "\"x\""}) {
            assertThrows(IllegalArgumentException.class, () -> ProxyResponse.fromJson(json));
        }
    }

    private static int read(Object[] result) throws IOException {
        try (InputStream stream = (InputStream) result[2]) {
            int count = 0;
            while (stream.read() != -1) count++;
            return count;
        }
    }

    private static byte[] bytes(Object[] result) throws IOException {
        try (InputStream stream = (InputStream) result[2]) {
            return stream.readAllBytes();
        }
    }
}
