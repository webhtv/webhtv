package com.fongmi.android.tv.server.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.function.Function;

import okhttp3.HttpUrl;

/**
 * Decides whether an upstream target may be reached from a local proxy that must never
 * become a stepping stone into the machine or the LAN.
 */
public final class UpstreamUrlGuard {

    public static final UpstreamUrlGuard PUBLIC_ONLY = new UpstreamUrlGuard(UpstreamUrlGuard::lookupAll);

    private final Function<String, InetAddress[]> resolver;

    UpstreamUrlGuard(Function<String, InetAddress[]> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void check(String url) throws IOException {
        HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
        if (parsed == null) throw new IOException("invalid upstream url");
        check(parsed);
    }

    public void check(HttpUrl url) throws IOException {
        if (url == null) throw new IllegalArgumentException("upstream url is null");
        String scheme = url.scheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) throw new IOException("blocked upstream scheme");
        resolve(url.host());
    }

    /**
     * Returns the addresses it just validated so a client can connect to exactly these instead of
     * resolving the same name again, where a second, unverified answer could point at the LAN.
     */
    public InetAddress[] resolve(String host) throws IOException {
        InetAddress[] addresses = lookup(host);
        for (InetAddress address : addresses) {
            if (address == null || !isPublic(address)) throw new IOException("blocked upstream address");
        }
        return addresses;
    }

    private InetAddress[] lookup(String host) throws IOException {
        if (host == null || host.isBlank()) throw new IOException("invalid upstream host");
        InetAddress literal = parseLiteral(host);
        if (literal != null) return new InetAddress[]{literal};
        InetAddress[] addresses;
        try {
            addresses = resolver.apply(host);
        } catch (RuntimeException e) {
            throw unresolvable(e);
        }
        if (addresses == null || addresses.length == 0) throw unresolvable(null);
        return addresses.clone();
    }

    private static IOException unresolvable(RuntimeException cause) {
        IOException exception = new IOException("unresolvable upstream host");
        if (cause != null) exception.initCause(cause);
        return exception;
    }

    private static InetAddress[] lookupAll(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new UnknownHost(e);
        }
    }

    private static InetAddress parseLiteral(String host) {
        // A numeric literal must be judged here; handing it to the resolver would let a name
        // shadow an address the caller believes it already pinned.
        if (host.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                return null;
            }
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] address = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) return null;
            int value = 0;
            for (int digit = 0; digit < part.length(); digit++) {
                char character = part.charAt(digit);
                if (character < '0' || character > '9') return null;
                value = value * 10 + (character - '0');
            }
            if (value > 255) return null;
            address[index] = (byte) value;
        }
        return bytes(address);
    }

    private static InetAddress bytes(byte[] address) {
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isPublic(InetAddress address) {
        byte[] raw = address.getAddress();
        return raw.length == 4 ? isPublicIpv4(raw, 0) : isPublicIpv6(raw);
    }

    private static boolean isPublicIpv6(byte[] raw) {
        if (isIpv4Mapped(raw) || isNat64(raw)) return isPublicIpv4(raw, 12);
        if (isSixToFour(raw)) return isPublicIpv4(raw, 2);
        // Special-purpose and documentation allocations inside global unicast must not become
        // alternative routes around the local-address checks.
        if (raw[0] == 0x20 && raw[1] == 0x01) {
            if ((raw[2] & 0xFE) == 0) return false;
            if (raw[2] == 0x0d && raw[3] == (byte) 0xb8) return false;
        }
        if (raw[0] == 0x3f && raw[1] == (byte) 0xff && (raw[2] & 0xF0) == 0) return false;
        // Allowing only 2000::/3 rejects loopback, unspecified, link-local, site-local, unique
        // local and multicast in one rule instead of enumerating each of them.
        return (raw[0] & 0xE0) == 0x20;
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int index = 0; index < 10; index++) {
            if (raw[index] != 0) return false;
        }
        return raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
    }

    private static boolean isNat64(byte[] raw) {
        if (raw[0] != 0x00 || raw[1] != 0x64 || raw[2] != (byte) 0xff || raw[3] != (byte) 0x9b) return false;
        for (int index = 4; index < 12; index++) {
            if (raw[index] != 0) return false;
        }
        return true;
    }

    private static boolean isSixToFour(byte[] raw) {
        return raw[0] == 0x20 && raw[1] == 0x02;
    }

    private static boolean isPublicIpv4(byte[] raw, int offset) {
        int first = raw[offset] & 0xFF;
        int second = raw[offset + 1] & 0xFF;
        int third = raw[offset + 2] & 0xFF;
        if (first == 0 || first == 10 || first == 127) return false;
        if (first == 172 && second >= 16 && second <= 31) return false;
        if (first == 192 && second == 168) return false;
        if (first == 169 && second == 254) return false;
        if (first == 100 && second >= 64 && second <= 127) return false;
        if (first == 192 && second == 0 && (third == 0 || third == 2)) return false;
        if (first == 198 && (second == 18 || second == 19)) return false;
        if (first == 198 && second == 51 && third == 100) return false;
        if (first == 203 && second == 0 && third == 113) return false;
        return (first & 0xF0) != 0xE0 && (first & 0xF0) != 0xF0;
    }

    private static final class UnknownHost extends RuntimeException {

        private UnknownHost(UnknownHostException cause) {
            super(cause);
        }
    }
}
