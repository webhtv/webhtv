package com.fongmi.android.tv.playback.origin;

import com.github.catvod.net.OkHttp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Call;
import okhttp3.Request;

public final class OriginMediaRelay {
    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    private static final Pattern HLS_URI = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<String> RESPONSE_HEADERS = Set.of("content-type", "content-range", "accept-ranges", "content-length", "cache-control", "etag", "last-modified");

    public static final class Endpoint {
        public final int port;
        public final String token;
        Endpoint(int port, String token) { this.port = port; this.token = token; }
    }
    private static final class Target {
        final String url, templateName, templateBase;
        final Map<String, String> headers;
        final boolean prefix;

        Target(String url, Map<String, String> headers, boolean prefix, String templateName, String templateBase) {
            this.url = url;
            this.headers = new LinkedHashMap<>(headers == null ? Map.of() : headers);
            this.prefix = prefix;
            this.templateName = templateName;
            this.templateBase = templateBase;

        }
        String requestUrl(String rest) throws IOException {
            if (rest.isEmpty()) return url;
            if (templateName != null) {
                String expanded = substitute(templateName, rest);
                if (expanded == null) throw new IOException("unknown template target");
                return templateBase == null ? expanded : resolve(templateBase, expanded);
            }
            if (!prefix || !url.endsWith("/") || rest.contains("..") || rest.contains(":") || rest.startsWith("/")) throw new IOException("unknown target path");
            return resolve(url, rest);
        }
    }
    private static final class Session {
        final String id, token;
        final Map<String, Target> targets = new ConcurrentHashMap<>();
        final AtomicInteger ids = new AtomicInteger();
        final Set<Call> calls = ConcurrentHashMap.newKeySet();
        final Set<InputStream> streams = ConcurrentHashMap.newKeySet();
        Server server;
        volatile ProxyHandler proxyHandler;
        volatile boolean closed;
        Session(String id, String token) { this.id = id; this.token = token; }
    }
    private static final class Server extends NanoHTTPD {
        final Session session;
        Server(Session session) { super("127.0.0.1", 0); this.session = session; }
        @Override public Response serve(IHTTPSession request) { return respond(session, request); }
    }
    private OriginMediaRelay() { }

    public static synchronized Endpoint begin(String leaseId) throws IOException {
        Session existing = SESSIONS.get(leaseId);
        if (existing != null) return new Endpoint(existing.server.getListeningPort(), existing.token);
        byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
        StringBuilder token = new StringBuilder(); for (byte value : random) token.append(String.format(Locale.ROOT, "%02x", value & 255));
        Session session = new Session(leaseId, token.toString());
        session.server = new Server(session);
        session.server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        SESSIONS.put(leaseId, session);
        return new Endpoint(session.server.getListeningPort(), session.token);
    }
    public static void close(String leaseId) {
        Session session = leaseId == null ? null : SESSIONS.remove(leaseId);
        if (session == null) return;
        session.closed = true;
        for (Call call : session.calls) call.cancel();
        for (InputStream stream : session.streams) try { stream.close(); } catch (IOException ignored) { }
        session.server.stop();
        session.targets.clear();
    }
    public static boolean isBound(String leaseId) { return port(leaseId) > 0; }
    public static int port(String leaseId) { Session session = leaseId == null ? null : SESSIONS.get(leaseId); return session == null || session.closed ? 0 : session.server.getListeningPort(); }
    public static String register(String leaseId, String url, Map<String, String> headers) { return register(leaseId, url, headers, false, null); }
    public static String registerPrefix(String leaseId, String url, Map<String, String> headers) { return register(leaseId, url, headers, true, null); }
    private static String register(String leaseId, String url, Map<String, String> headers, boolean prefix, String template) {
        return register(leaseId, url, headers, prefix, template, null);
    }
    private static String register(String leaseId, String url, Map<String, String> headers, boolean prefix, String template, String base) {
        Session session = require(leaseId);
        String id = "t" + session.ids.incrementAndGet();
        session.targets.put(id, new Target(url, headers, prefix, template, base));
        return id;
    }
    public static String localPath(String leaseId, String targetId) { return "/origin/" + require(leaseId).token + "/" + targetId; }
    public static String localUrl(String leaseId, String targetId) { return "http://127.0.0.1:" + port(leaseId) + localPath(leaseId, targetId); }
    public static Map<String, String> headers(String leaseId, String targetId) { Target target = require(leaseId).targets.get(targetId); return target == null ? Map.of() : new LinkedHashMap<>(target.headers); }
    public static String sourceUrl(String leaseId, String targetId) { Target target = require(leaseId).targets.get(targetId); return target == null ? "" : target.url; }
    private static Session require(String leaseId) { Session session = SESSIONS.get(leaseId); if (session == null || session.closed) throw new IllegalStateException(OriginSessionContract.PORT_OWNERSHIP_AMBIGUOUS); return session; }

    public interface ProxyHandler { Object[] proxy(Map<String, String> params) throws Exception; }
    public static void bindProxy(String leaseId, ProxyHandler handler) { require(leaseId).proxyHandler = handler; }
    public static String rewrite(String text, String baseUrl, String leaseId) {
        Session session = require(leaseId);
        Map<String, String> inherited = Map.of();
        for (Target target : session.targets.values()) if (target.url.equals(baseUrl)) { inherited = target.headers; break; }
        return rewrite(text, baseUrl, leaseId, inherited);
    }
    public static String rewrite(String text, String baseUrl, String leaseId, Map<String, String> headers) {
        if (text == null || text.isEmpty()) return "";
        if (text.contains("<MPD")) return dash(text, baseUrl, leaseId, headers);
        StringBuilder output = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i], trimmed = line.trim();
            if (trimmed.isEmpty()) output.append(line);
            else if (trimmed.startsWith("#")) {
                Matcher matcher = HLS_URI.matcher(line);
                StringBuffer patched = new StringBuffer();
                while (matcher.find()) matcher.appendReplacement(patched, Matcher.quoteReplacement("URI=\"" + mapped(baseUrl, matcher.group(1), leaseId, headers, false) + "\""));
                matcher.appendTail(patched); output.append(patched);
            } else output.append(mapped(baseUrl, trimmed, leaseId, headers, false));
            if (i + 1 < lines.length) output.append('\n');
        }
        return output.toString();
    }
    private static String mapped(String base, String value, String lease, Map<String, String> headers, boolean prefix) {
        if (value.startsWith("data:")) return value;
        String absolute = resolve(base, value);
        if (!(absolute.startsWith("http:") || absolute.startsWith("https:") || absolute.startsWith("file:"))) throw new IllegalArgumentException("ISOLATION_ERROR: manifest resource scheme");
        return localUrl(lease, register(lease, absolute, headers, prefix, null)) + (prefix && absolute.endsWith("/") ? "/" : "");
    }
    private static String dash(String text, String base, String lease, Map<String, String> headers) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            org.w3c.dom.Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            rewriteDashElement(document.getDocumentElement(), base, lease, headers);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            javax.xml.transform.Transformer transformer = TransformerFactory.newInstance().newTransformer();
            if (!text.trim().startsWith("<?xml")) transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) { throw new IllegalArgumentException("ISOLATION_ERROR: unsupported DASH resource structure", e); }
    }
    private static void rewriteDashElement(Element element, String inherited, String lease, Map<String, String> headers) {
        String base = inherited;
        Element baseNode = null;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element node) || !"BaseURL".equals(node.getTagName())) continue;
            if (baseNode != null) throw new IllegalArgumentException("multiple DASH BaseURL alternatives");
            baseNode = node; base = resolve(inherited, node.getTextContent().trim());
        }
        if (baseNode != null) baseNode.setTextContent(mapped(inherited, base, lease, headers, base.endsWith("/")));
        for (String attribute : new String[]{"sourceURL", "media", "initialization", "href"}) {
            if (!element.hasAttribute(attribute)) continue;
            String value = element.getAttribute(attribute);
            String absolute = resolve(base, value);
            if (value.contains("$")) {
                String id = register(lease, absolute, headers, false, value, base);
                element.setAttribute(attribute, localUrl(lease, id) + "/" + value);
            } else element.setAttribute(attribute, mapped(base, value, lease, headers, false));
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) if (child instanceof Element node && node != baseNode) rewriteDashElement(node, base, lease, headers);
    }
    private static final Pattern TEMPLATE = Pattern.compile("\\$[A-Za-z]+(?:%0[0-9]+[dxX])?\\$|\\$\\$");

    /** 展开完整 DASH 模板：目录/文件名/query 占位符逐段校验，返回真实原 URL。 */
    static String substitute(String template, String rest) {
        int templateQuery = template.indexOf('?'), restQuery = rest.indexOf('?');
        String templatePath = templateQuery < 0 ? template : template.substring(0, templateQuery);
        String restPath = restQuery < 0 ? rest : rest.substring(0, restQuery);
        String[] templateSegments = templatePath.split("/", -1), restSegments = restPath.split("/", -1);
        if (templateSegments.length != restSegments.length) return null;
        StringBuilder expanded = new StringBuilder();
        for (int i = 0; i < templateSegments.length; i++) {
            if (i > 0) expanded.append('/');
            if (!segment(templateSegments[i], restSegments[i])) return null;
            expanded.append(restSegments[i]);
        }
        String templateRest = templateQuery < 0 ? "" : template.substring(templateQuery + 1);
        String restRest = restQuery < 0 ? "" : rest.substring(restQuery + 1);
        if (!templateRest.isEmpty() || !restRest.isEmpty()) {
            String[] templatePairs = templateRest.isEmpty() ? new String[0] : templateRest.split("&", -1);
            String[] restPairs = restRest.isEmpty() ? new String[0] : restRest.split("&", -1);
            if (templatePairs.length != restPairs.length) return null;
            expanded.append('?');
            for (int i = 0; i < templatePairs.length; i++) {
                int split = templatePairs[i].indexOf('=');
                String name = split < 0 ? templatePairs[i] : templatePairs[i].substring(0, split);
                String value = split < 0 ? "" : templatePairs[i].substring(split + 1);
                int restSplit = restPairs[i].indexOf('=');
                String restName = restSplit < 0 ? restPairs[i] : restPairs[i].substring(0, restSplit);
                String restValue = restSplit < 0 ? "" : restPairs[i].substring(restSplit + 1);
                if (!segment(name, restName) || !segment(value, restValue)) return null;
                if (i > 0) expanded.append('&');
                expanded.append(restName).append('=').append(restValue);
            }
        }
        return expanded.toString();
    }
    private static boolean segment(String template, String value) {
        Matcher matcher = TEMPLATE.matcher(template);
        StringBuilder regex = new StringBuilder(); int offset = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(template.substring(offset, matcher.start())));
            regex.append("$$".equals(matcher.group()) ? "\\$" : "[A-Za-z0-9_.%:@!-]+");
            offset = matcher.end();
        }
        regex.append(Pattern.quote(template.substring(offset)));
        return Pattern.compile(regex.toString()).matcher(value).matches();
    }
    static String resolve(String baseUrl, String url) { return URI.create(baseUrl).resolve(url).toString(); }

    private static NanoHTTPD.Response respond(Session session, NanoHTTPD.IHTTPSession request) {
        String prefix = "/origin/" + session.token + "/";
        if (session.closed || !request.getUri().startsWith(prefix)) return error(403, "unknown lease");
        String tail = request.getUri().substring(prefix.length());
        int slash = tail.indexOf('/');
        String id = slash < 0 ? tail : tail.substring(0, slash);
        String rest = slash < 0 ? "" : tail.substring(slash + 1);
        Target target = session.targets.get(id);
        if (target == null) return error(404, "unregistered target");
        String query = request.getQueryParameterString();
        if (query != null && !query.isEmpty() && (target.templateName != null || !rest.isEmpty())) rest += "?" + query;
        try {
            String url = target.requestUrl(rest);
            if (url.startsWith("file:")) return localFile(session, target, url, request);
            URI uri = URI.create(url);
            if (uri.getPort() == session.server.getListeningPort() && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost())) && uri.getPath().startsWith("/proxy")) return spiderProxy(session, target, uri, request);
            Request.Builder builder = new Request.Builder().url(url);
            for (Map.Entry<String, String> header : target.headers.entrySet()) builder.header(header.getKey(), header.getValue());
            String range = request.getHeaders().get("range");
            if (range != null) builder.header("Range", range);
            if (request.getMethod() == NanoHTTPD.Method.POST) {
                Map<String, String> body = new LinkedHashMap<>(); request.parseBody(body);
                builder.post(okhttp3.RequestBody.create(body.getOrDefault("postData", "").getBytes(StandardCharsets.ISO_8859_1), okhttp3.MediaType.parse(request.getHeaders().getOrDefault("content-type", "application/octet-stream"))));
            } else if (request.getMethod() == NanoHTTPD.Method.HEAD) builder.head(); else builder.get();
            Call call = OkHttp.player().newCall(builder.build()); session.calls.add(call);
            okhttp3.Response source;
            try { source = call.execute(); } finally { session.calls.remove(call); }
            String type = source.header("Content-Type", "application/octet-stream");
            String effective = source.request().url().toString();
            boolean manifest = type.toLowerCase(Locale.ROOT).contains("mpegurl") || type.toLowerCase(Locale.ROOT).contains("dash+xml") || effective.split("\\?", 2)[0].endsWith(".m3u8") || effective.split("\\?", 2)[0].endsWith(".mpd");
            if (source.body() == null) { source.close(); return error(source.code(), ""); }
            if (manifest && source.isSuccessful()) {
                String rewritten;
                try { rewritten = rewrite(readText(source.body().byteStream()), effective, session.id, target.headers); }
                finally { source.close(); }
                return NanoHTTPD.newFixedLengthResponse(status(source.code()), type, rewritten);
            }
            InputStream stream = new FilterInputStream(source.body().byteStream()) {
                @Override public void close() throws IOException { super.close(); source.close(); session.streams.remove(this); }
            };
            session.streams.add(stream);
            long length = source.body().contentLength();
            NanoHTTPD.Response reply = length < 0 ? NanoHTTPD.newChunkedResponse(status(source.code()), type, stream) : NanoHTTPD.newFixedLengthResponse(status(source.code()), type, stream, length);
            for (String name : source.headers().names()) if (RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) && !"content-length".equalsIgnoreCase(name)) reply.addHeader(name, source.header(name));
            String location = source.header("Location");
            if (location != null) reply.addHeader("Location", mapped(effective, location, session.id, target.headers, false));
            return reply;
        } catch (Exception e) { return error(500, String.valueOf(e.getMessage())); }
    }
    private static NanoHTTPD.Response localFile(Session session, Target target, String url, NanoHTTPD.IHTTPSession request) throws Exception {
        File file = new File(URI.create(url));
        if (!file.isFile()) return error(404, "origin file missing");
        String path = file.getName().toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) {
            String mime = path.endsWith(".mpd") ? "application/dash+xml" : "application/vnd.apple.mpegurl";
            String text = readText(localStream(session, file, 0));
            return NanoHTTPD.newFixedLengthResponse(status(200), mime, rewrite(text, url, session.id, target.headers));
        }
        long length = file.length(), start = 0, end = length - 1;
        String range = request.getHeaders().get("range");
        if (range != null) {
            Matcher match = Pattern.compile("bytes=([0-9]*)-([0-9]*)").matcher(range);
            if (!match.matches() || match.group(1).isEmpty() && match.group(2).isEmpty()) return error(416, "bad range");
            try {
                if (match.group(1).isEmpty()) {
                    long suffix = Long.parseLong(match.group(2));
                    if (suffix == 0) return error(416, "bad range");
                    start = Math.max(0, length - suffix);
                } else {
                    start = Long.parseLong(match.group(1));
                    if (!match.group(2).isEmpty()) end = Math.min(end, Long.parseLong(match.group(2)));
                }
            } catch (NumberFormatException e) { return error(416, "bad range"); }
            if (start > end) return error(416, "bad range");
        }
        InputStream input = localStream(session, file, start);
        NanoHTTPD.Response reply = NanoHTTPD.newFixedLengthResponse(status(range == null ? 200 : 206), "application/octet-stream", input, end - start + 1);
        reply.addHeader("Accept-Ranges", "bytes");
        if (range != null) reply.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
        return reply;
    }
    private static InputStream localStream(Session session, File file, long position) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try { input.getChannel().position(position); }
        catch (IOException e) { input.close(); throw e; }
        InputStream stream = new FilterInputStream(input) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { session.streams.remove(this); }
            }
        };
        session.streams.add(stream);
        if (session.closed) { stream.close(); throw new IOException("origin session closed"); }
        return stream;
    }
    private static NanoHTTPD.Response spiderProxy(Session session, Target target, URI uri, NanoHTTPD.IHTTPSession request) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        if (uri.getRawQuery() != null) for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2); params.put(java.net.URLDecoder.decode(parts[0], "UTF-8"), parts.length < 2 ? "" : java.net.URLDecoder.decode(parts[1], "UTF-8"));
        }
        params.putAll(request.getHeaders()); params.putAll(target.headers);
        if (session.proxyHandler == null) return error(500, "origin proxy handler absent");
        Object[] response = session.proxyHandler.proxy(params);
        if (response == null || response.length < 3 || !(response[0] instanceof Integer code) || !(response[2] instanceof InputStream input)) return error(500, "invalid original spider proxy response");
        String mime = String.valueOf(response[1]);
        String lower = mime.toLowerCase(Locale.ROOT);
        // 只有成功的清单响应才需要改地址；3xx 清单体是空的，必须走下面的头处理保留 Location。
        if (code >= 200 && code < 300 && (lower.contains("mpegurl") || lower.contains("dash+xml"))) return NanoHTTPD.newFixedLengthResponse(status(code), mime, rewrite(readText(input), target.url, session.id, target.headers));
        if (lower.contains("vnd.ms-sstr+xml")) { input.close(); return error(501, "origin SmoothStreaming manifest is not isolated yet"); }
        NanoHTTPD.Response reply = NanoHTTPD.newChunkedResponse(status(code), mime, input);
        if (response.length > 3 && response[3] instanceof Map<?, ?> headers) for (Map.Entry<?, ?> entry : headers.entrySet()) {
            String name = String.valueOf(entry.getKey());
            // 重定向目标也留在租约里：原 302 若直接把原站 Location 交回主端，续播断链；
            // 交回原站 URL 又会绕过 worker 账号隔离，所以只给登记后的回环地址。
            if ("location".equalsIgnoreCase(name)) {
                String location = String.valueOf(entry.getValue());
                if (!location.isEmpty()) try {
                    reply.addHeader(name, mapped(target.url, location, session.id, target.headers, false));
                } catch (RuntimeException ignored) { }
                continue;
            }
            if (RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) reply.addHeader(name, String.valueOf(entry.getValue()));
        }
        return reply;
    }
    private static String readText(InputStream input) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) { if (output.size() + count > MAX_MANIFEST_BYTES) throw new IOException("origin manifest exceeds limit"); output.write(buffer, 0, count); }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static NanoHTTPD.Response.IStatus status(int code) {
        NanoHTTPD.Response.Status value = NanoHTTPD.Response.Status.lookup(code);
        return value != null ? value : new NanoHTTPD.Response.IStatus() {
            @Override public int getRequestStatus() { return code; }
            @Override public String getDescription() { return code + " Origin"; }
        };
    }
    private static NanoHTTPD.Response error(int code, String message) { return NanoHTTPD.newFixedLengthResponse(status(code), "text/plain", message); }
}
