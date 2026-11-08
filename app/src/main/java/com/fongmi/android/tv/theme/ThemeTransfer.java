package com.fongmi.android.tv.theme;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Bounded, no-cookie transport used by the theme draft importer. */
public final class ThemeTransfer {

    public static final int MAX_BYTES = ThemeProfileValidator.MAX_JSON_BYTES;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .proxy(Proxy.NO_PROXY)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build();

    private ThemeTransfer() {
    }

    public static boolean isHttps(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getPort() != 0
                    && uri.getPort() <= 65535
                    && !isBlockedHost(uri.getHost());
        } catch (URISyntaxException | RuntimeException error) {
            return false;
        }
    }

    public static String host(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (URISyntaxException | RuntimeException error) {
            return "";
        }
    }

    public static String read(InputStream input) throws IOException {
        if (input == null) throw new IOException("theme source is unavailable");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_BYTES, 16 * 1024));
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_BYTES - total) throw new IOException("theme JSON is too large");
            output.write(buffer, 0, count);
            total += count;
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String fetch(String value) throws IOException {
        if (!isHttps(value)) throw new IOException("theme URL must be HTTPS and public");
        String requestedHost = host(value);
        List<InetAddress> addresses = lookupPublic(requestedHost);
        OkHttpClient client = CLIENT.newBuilder().dns(host -> {
            if (requestedHost.equalsIgnoreCase(host)) return addresses;
            return Dns.SYSTEM.lookup(host);
        }).build();
        Request request = new Request.Builder().url(value).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() >= 300 && response.code() < 400) {
                throw new IOException("theme URL redirects are not allowed");
            }
            if (!response.isSuccessful()) throw new IOException("theme URL returned HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("theme URL returned an empty response");
            if (body.contentLength() > MAX_BYTES) throw new IOException("theme JSON is too large");
            return read(body.byteStream());
        }
    }

    private static List<InetAddress> lookupPublic(String hostname) throws IOException {
        if (hostname == null || hostname.isBlank()) throw new IOException("theme URL host is missing");
        List<InetAddress> addresses;
        try {
            addresses = Dns.SYSTEM.lookup(hostname);
        } catch (RuntimeException error) {
            throw new IOException("theme URL host cannot be resolved", error);
        }
        if (addresses.isEmpty()) throw new IOException("theme URL host cannot be resolved");
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) throw new IOException("private or special theme host is not allowed");
        }
        return addresses;
    }

    private static boolean isBlockedHost(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "localhost".equals(normalized)
                || normalized.endsWith(".localhost") || normalized.endsWith(".local")) return true;
        if (!isIpLiteral(normalized)) return false;
        try {
            return isBlockedAddress(InetAddress.getByName(normalized));
        } catch (java.net.UnknownHostException | RuntimeException error) {
            return true;
        }
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) return true;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            int value = 0;
            for (int index = 0; index < part.length(); index++) {
                char current = part.charAt(index);
                if (current < '0' || current > '9') return false;
                value = value * 10 + current - '0';
            }
            if (value > 255) return false;
        }
        return true;
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) return true;
        if (bytes.length != 4) return false;
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0 || (first == 100 && second >= 64 && second <= 127)
                || (first == 192 && second == 0) || (first == 192 && second == 2)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51) || (first == 203 && second == 0)
                || first >= 224;
    }
}
