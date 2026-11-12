package com.github.catvod.crawler.diagnostics;

import java.net.URI;
import java.util.Set;
import java.util.function.LongSupplier;

/** Same-origin local debug controls with a bounded action rate; no pairing state. */
public final class DiagnosticAccess {
    private final LongSupplier clock;
    private long actionWindow;
    private int actions;
    public DiagnosticAccess() { this(() -> System.nanoTime() / 1_000_000); }
    public DiagnosticAccess(LongSupplier clock) { this.clock = clock; }

    public synchronized boolean allowAction() {
        long now = clock.getAsLong();
        if (actions == 0 || now < actionWindow || now - actionWindow >= 1000) {
            actionWindow = now;
            actions = 0;
        }
        if (actions >= 5) return false;
        actions++;
        return true;
    }

    public static boolean sameOrigin(String origin, String host, Set<String> allowedHosts) {
        if (host == null || !allowedHosts.contains(host.toLowerCase(java.util.Locale.ROOT))) return false;
        if (origin == null || origin.isEmpty()) return false; // Browser POST must identify its source.
        try {
            URI uri = URI.create(origin);
            return "http".equals(uri.getScheme()) && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && host.equalsIgnoreCase(uri.getRawAuthority());
        } catch (IllegalArgumentException ignored) { return false; }
    }

    /**
     * 日志页控件（清空/开关采集/诊断动作）的来源判定，比 {@link #sameOrigin} 宽松一档：
     * 部分浏览器与 WebView 对同源 POST 不发 {@code Origin}（或发字面量 {@code null}），
     * 于是用 {@code Referer} 或 {@code Sec-Fetch-Site: same-origin} 作为同源证据。
     *
     * <p>安全边界没有放宽：跨站页面的 POST 一定带自己的 Origin，与 Host 不同即拒绝；
     * 三者全都没有时只信任回环地址（{@code 127.x / localhost / [::1]}），那只能来自本机进程。
     */
    public static boolean controlRequestAllowed(String origin, String referer, String secFetchSite, String host, Set<String> allowedHosts) {
        if (host == null) return false;
        String authority = host.toLowerCase(java.util.Locale.ROOT);
        if (!allowedHosts.contains(authority) && !isLocalAuthority(authority)) return false;
        String source = origin == null ? "" : origin.trim();
        if (!source.isEmpty() && !"null".equalsIgnoreCase(source)) return matchesAuthority(source, authority, true);
        String current = referer == null ? "" : referer.trim();
        String site = secFetchSite == null ? "" : secFetchSite.trim();
        if (source.isEmpty() && current.isEmpty() && site.isEmpty()) return isLoopbackAuthority(authority);
        if ("same-origin".equalsIgnoreCase(site)) return true;
        return matchesAuthority(current, authority, false);
    }

    private static boolean matchesAuthority(String url, String host, boolean bareOrigin) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url);
            if (!"http".equals(uri.getScheme()) || uri.getRawUserInfo() != null) return false;
            if (!host.equalsIgnoreCase(uri.getRawAuthority())) return false;
            String path = uri.getRawPath();
            return bareOrigin ? path == null || path.isEmpty() : path != null && path.startsWith("/debug/");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isLocalAuthority(String host) {
        String name = hostName(host);
        return "localhost".equals(name) || isLoopbackAuthority(host) || isPrivateIpv4(name);
    }

    private static boolean isLoopbackAuthority(String host) {
        String name = hostName(host);
        return "localhost".equals(name) || "::1".equals(name) || "127.0.0.1".equals(name) || name.startsWith("127.");
    }

    private static String hostName(String host) {
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(1, end) : host;
        }
        int colon = host.lastIndexOf(':');
        return colon > 0 ? host.substring(0, colon) : host;
    }

    private static boolean isPrivateIpv4(String name) {
        String[] parts = name.split("\\.");
        if (parts.length != 4) return false;
        int first, second;
        try {
            first = Integer.parseInt(parts[0]);
            second = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return false;
        }
        return first == 10 || first == 192 && second == 168 || first == 172 && second >= 16 && second <= 31 || first == 169 && second == 254;
    }
}
