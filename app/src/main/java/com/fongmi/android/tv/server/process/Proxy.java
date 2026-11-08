package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Proxy implements Process {

    private static final String INVALID_RESPONSE = "Invalid proxy response";
    private static final AtomicLong STREAM_ID = new AtomicLong();
    private static final long PROGRESS_INTERVAL_NS = TimeUnit.SECONDS.toNanos(5);

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/proxy");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            params.putAll(session.getHeaders());
            params.putAll(files);
            SpiderDebug.log("proxy", "request uri=%s method=%s do=%s params=%s", url, session.getMethod(), params.get("do"), params);
            return createResponse(params, url, BaseLoader.get().proxy(params));
        } catch (Throwable e) {
            e.printStackTrace();
            SpiderDebug.log("proxy", e);
            return Nano.error(Objects.toString(e.getMessage(), e.toString()));
        }
    }

    private Response createResponse(Map<String, String> params, String url, Object[] rs) {
        if (rs == null || rs.length == 0) {
            SpiderDebug.log("proxy", "response invalid do=%s uri=%s reason=null_or_empty", params.get("do"), url);
            return Nano.error(INVALID_RESPONSE);
        }
        if (rs[0] instanceof Response response) {
            SpiderDebug.log("proxy", "response object do=%s type=%s", params.get("do"), rs[0].getClass().getName());
            return response;
        }
        if (rs.length < 3 || !(rs[0] instanceof Integer code)) {
            SpiderDebug.log("proxy", "response invalid do=%s status=%s mime=%s body=%s headers=%s", params.get("do"), rs.length > 0 ? rs[0] : null, rs.length > 1 ? rs[1] : null, rs.length > 2 ? rs[2] : null, rs.length > 3 ? rs[3] : null);
            return Nano.error(INVALID_RESPONSE);
        }
        InputStream stream = rs[2] instanceof InputStream candidate ? candidate : null;
        if (stream == null && !canResponseHaveEmptyBody(code)) {
            SpiderDebug.log("proxy", "response invalid do=%s status=%s mime=%s body=%s headers=%s", params.get("do"), code, rs[1], rs[2], rs.length > 3 ? rs[3] : null);
            return Nano.error(INVALID_RESPONSE);
        }
        Map<String, String> headers = headers(rs.length > 3 ? rs[3] : null);
        if (stream == null && (headers == null || header(headers, "Location") == null)) {
            SpiderDebug.log("proxy", "response invalid do=%s status=%s mime=%s body=%s headers=%s", params.get("do"), code, rs[1], rs[2], headers);
            return Nano.error(INVALID_RESPONSE);
        }
        SpiderDebug.log("proxy", "response do=%s status=%s mime=%s body=%s headers=%s", params.get("do"), code, rs[1], rs[2] == null ? "empty" : rs[2].getClass().getName(), rs.length > 3 ? rs[3] : null);

        if (stream != null) stream = wrapStream(params, headers, stream);
        Response response = NanoHTTPD.newChunkedResponse(toStatus(code), Objects.toString(rs[1], null), stream == null ? new ByteArrayInputStream(new byte[0]) : stream);
        addHeaders(response, headers);
        long rangeStart = ProxyRangeResponsePolicy.resolveStart(code, first(params.get("range"), params.get("Range")));
        if (rangeStart >= 0) response.addHeader(ProxyRangeResponsePolicy.HEADER_RANGE_START, String.valueOf(rangeStart));
        return response;
    }

    private static boolean canResponseHaveEmptyBody(int code) {
        return code >= 300 && code < 400;
    }

    private static void addHeaders(Response response, Map<String, String> headers) {
        if (headers == null) return;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (ProxyRangeResponsePolicy.HEADER_RANGE_START.equalsIgnoreCase(entry.getKey())) continue;
            response.addHeader(entry.getKey(), entry.getValue());
        }
    }

    private static IStatus toStatus(int code) {
        Status status = Status.lookup(code);
        return status != null ? status : code >= 100 && code <= 599 ? new ProxyStatus(code) : Status.INTERNAL_ERROR;
    }

    private record ProxyStatus(int code) implements IStatus {

        @Override
        public String getDescription() {
            return code + " Proxy Status";
        }

        @Override
        public int getRequestStatus() {
            return code;
        }
    }

    private InputStream wrapStream(Map<String, String> params, Map<String, String> headers, InputStream stream) {
        if (stream == null || !SpiderDebug.isEnabled()) return stream;
        String id = String.valueOf(STREAM_ID.incrementAndGet());
        String range = first(params.get("range"), params.get("Range"));
        String contentLength = header(headers, "Content-Length");
        String contentRange = header(headers, "Content-Range");
        String label = label(params);
        SpiderDebug.log("proxy-stream", "open id=%s do=%s site=%s range=%s length=%s contentRange=%s", id, params.get("do"), params.get("siteKey"), empty(range), empty(contentLength), empty(contentRange));
        return new DebugInputStream(stream, id, label);
    }

    private static Map<String, String> headers(Object headers) {
        if (!(headers instanceof Map<?, ?> map)) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return result;
    }

    private static String label(Map<String, String> params) {
        return shorten(first(params.get("url"), params.get("playUrl"), params.get("thread"), params.get("siteKey")), 120);
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || name == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        return null;
    }

    private static String first(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isEmpty()) return value;
        return null;
    }

    private static String empty(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private static String shorten(String value, int max) {
        if (value == null) return "-";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static class DebugInputStream extends FilterInputStream {

        private final String id;
        private final String label;
        private final long startNs;
        private long lastLogNs;
        private long bytes;
        private boolean firstByte;
        private boolean closed;

        private DebugInputStream(InputStream in, String id, String label) {
            super(in);
            this.id = id;
            this.label = label;
            this.startNs = System.nanoTime();
            this.lastLogNs = startNs;
        }

        @Override
        public int read() throws IOException {
            try {
                int read = super.read();
                if (read != -1) onRead(1);
                return read;
            } catch (IOException e) {
                onError(e);
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                int read = super.read(b, off, len);
                if (read > 0) onRead(read);
                return read;
            } catch (IOException e) {
                onError(e);
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                log("close");
            }
            super.close();
        }

        private void onRead(int count) {
            bytes += count;
            long now = System.nanoTime();
            if (!firstByte) {
                firstByte = true;
                SpiderDebug.log("proxy-stream", "firstByte id=%s latency=%dms label=%s", id, elapsedMs(now), label);
            }
            if (now - lastLogNs >= PROGRESS_INTERVAL_NS) {
                lastLogNs = now;
                log("progress");
            }
        }

        private void onError(IOException e) {
            SpiderDebug.log("proxy-stream", "error id=%s bytes=%d elapsed=%dms speed=%.2fMiB/s error=%s", id, bytes, elapsedMs(System.nanoTime()), speedMiB(System.nanoTime()), e.getMessage());
        }

        private void log(String event) {
            long now = System.nanoTime();
            SpiderDebug.log("proxy-stream", "%s id=%s bytes=%d elapsed=%dms speed=%.2fMiB/s label=%s", event, id, bytes, elapsedMs(now), speedMiB(now), label);
        }

        private long elapsedMs(long now) {
            return TimeUnit.NANOSECONDS.toMillis(now - startNs);
        }

        private double speedMiB(long now) {
            long elapsedNs = Math.max(1, now - startNs);
            return bytes / 1024.0 / 1024.0 / (elapsedNs / 1_000_000_000.0);
        }
    }
}
