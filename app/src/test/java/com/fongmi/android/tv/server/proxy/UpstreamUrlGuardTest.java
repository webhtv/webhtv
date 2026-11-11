package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.function.Function;

import okhttp3.HttpUrl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class UpstreamUrlGuardTest {

    private static final String BLOCKED = "blocked upstream address";

    @Test
    public void rejectsReservedIpv4Destinations() throws Exception {
        assertAddressBlocked(ipv4(0, 0, 0, 0));
        assertAddressBlocked(ipv4(127, 0, 0, 1));
        assertAddressBlocked(ipv4(10, 1, 2, 3));
        assertAddressBlocked(ipv4(172, 16, 0, 9));
        assertAddressBlocked(ipv4(172, 31, 255, 254));
        assertAddressBlocked(ipv4(192, 168, 1, 5));
        assertAddressBlocked(ipv4(169, 254, 1, 1));
        assertAddressBlocked(ipv4(100, 64, 0, 1));
        assertAddressBlocked(ipv4(100, 127, 255, 255));
        assertAddressBlocked(ipv4(192, 0, 0, 8));
        assertAddressBlocked(ipv4(192, 0, 2, 55));
        assertAddressBlocked(ipv4(198, 18, 0, 1));
        assertAddressBlocked(ipv4(198, 19, 255, 254));
        assertAddressBlocked(ipv4(198, 51, 100, 7));
        assertAddressBlocked(ipv4(203, 0, 113, 9));
        assertAddressBlocked(ipv4(224, 0, 0, 1));
        assertAddressBlocked(ipv4(240, 0, 0, 1));
        assertAddressBlocked(ipv4(255, 255, 255, 255));
    }

    @Test
    public void acceptsPublicIpv4Destinations() throws Exception {
        assertAddressAllowed(ipv4(93, 184, 216, 34));
        assertAddressAllowed(ipv4(8, 8, 8, 8));
        assertAddressAllowed(ipv4(172, 32, 0, 1));
        assertAddressAllowed(ipv4(100, 128, 0, 1));
    }

    @Test
    public void rejectsDomainResolvingToAnyBlockedAddress() throws Exception {
        assertAddressBlocked(ipv4(93, 184, 216, 34), ipv4(10, 1, 2, 3));
        assertAddressBlocked(ipv4(10, 1, 2, 3), ipv4(93, 184, 216, 34));
        assertAddressBlocked(ipv4(93, 184, 216, 34), literal("::1"));
        assertAddressAllowed(ipv4(93, 184, 216, 34), ipv4(8, 8, 8, 8));
    }

    @Test
    public void rejectsNonGlobalIpv6Destinations() throws Exception {
        assertAddressBlocked(literal("::"));
        assertAddressBlocked(literal("::1"));
        assertAddressBlocked(literal("fe80::1"));
        assertAddressBlocked(literal("fec0::1"));
        assertAddressBlocked(literal("fc00::1"));
        assertAddressBlocked(literal("fdff::9"));
        assertAddressBlocked(literal("ff02::1"));
        assertAddressBlocked(literal("ff00::"));
        assertAddressBlocked(literal("4000::1"));
        assertAddressBlocked(literal("1fff:ffff::1"));
    }

    @Test
    public void acceptsGlobalUnicastIpv6Destinations() throws Exception {
        assertAddressAllowed(literal("2606:2800:220:1:248:1893:25c8:1946"));
        assertAddressAllowed(literal("2001:4860:4860::8888"));
    }

    @Test
    public void appliesIpv4RulesToIpv4MappedIpv6() throws Exception {
        assertAddressBlocked(ipv6(mapped(127, 0, 0, 1)));
        assertAddressBlocked(ipv6(mapped(10, 1, 2, 3)));
        assertAddressBlocked(ipv6(mapped(169, 254, 1, 1)));
        assertAddressBlocked(ipv6(mapped(0, 0, 0, 0)));
        assertAddressAllowed(ipv6(mapped(93, 184, 216, 34)));
    }

    @Test
    public void appliesIpv4RulesToNat64AndSixToFour() throws Exception {
        assertAddressBlocked(ipv6(nat64(127, 0, 0, 1)));
        assertAddressBlocked(ipv6(nat64(192, 168, 1, 5)));
        assertAddressAllowed(ipv6(nat64(93, 184, 216, 34)));
        assertAddressBlocked(ipv6(sixToFour(127, 0, 0, 1)));
        assertAddressBlocked(ipv6(sixToFour(10, 1, 2, 3)));
        assertAddressAllowed(ipv6(sixToFour(93, 184, 216, 34)));
    }

    @Test
    public void rejectsNat64AddressesOutsideTheExactTranslationPrefix() {
        assertAddressBlocked(literal("64:ff9b:0:1234::5db8:d822"));
        for (int index = 4; index < 12; index++) {
            byte[] address = nat64(93, 184, 216, 34);
            address[index] = 1;
            assertAddressBlocked(ipv6(address));
        }
    }

    @Test
    public void rejectsIpv6DocumentationAndIetfSpecialPurposeRanges() throws IOException {
        assertAddressBlocked(literal("2001:db8::1"));
        assertAddressBlocked(literal("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff"));
        assertAddressBlocked(literal("3fff::1"));
        assertAddressBlocked(literal("3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff"));
        assertAddressBlocked(literal("2001::1"));
        assertAddressBlocked(literal("2001:2::1"));
        assertAddressBlocked(literal("2001:10::1"));
        assertAddressBlocked(literal("2001:20::1"));
        assertAddressBlocked(literal("2001:1ff:ffff:ffff:ffff:ffff:ffff:ffff"));
        assertAddressAllowed(literal("2001:200::1"));
        assertAddressAllowed(literal("3fff:1000::1"));
        assertAddressAllowed(literal("2001:4860::8888"));
    }

    @Test
    public void judgesIpLiteralHostsWithoutResolverLookup() throws Exception {
        UpstreamUrlGuard unusedResolver = guard(host -> {
            throw new AssertionError("literal host must not reach the resolver: " + host);
        });

        IOException ipv4Blocked = assertThrows(IOException.class, () -> unusedResolver.check("http://127.0.0.1/live.m3u8"));
        assertEquals(BLOCKED, ipv4Blocked.getMessage());
        IOException ipv6Blocked = assertThrows(IOException.class, () -> unusedResolver.check("http://[::1]/live.m3u8"));
        assertEquals(BLOCKED, ipv6Blocked.getMessage());
        assertEquals(BLOCKED, assertThrows(IOException.class,
                () -> unusedResolver.check(HttpUrl.parse("http://[fe80::1]/live.m3u8"))).getMessage());

        unusedResolver.check("https://93.184.216.34/live.m3u8");
        unusedResolver.check(HttpUrl.parse("https://[2606:2800:220:1:248:1893:25c8:1946]/live.m3u8"));
    }

    @Test
    public void rejectsNonHttpSchemes() {
        UpstreamUrlGuard guard = guard(host -> {
            throw new AssertionError("scheme must be rejected before resolving: " + host);
        });

        IOException blocked = assertThrows(IOException.class, () -> guard.check("ftp://93.184.216.34/live.m3u8"));
        assertEquals("invalid upstream url", blocked.getMessage());
        assertEquals("invalid upstream url",
                assertThrows(IOException.class, () -> guard.check("file:///etc/hosts")).getMessage());
        assertEquals("invalid upstream url",
                assertThrows(IOException.class, () -> guard.check("rtmp://93.184.216.34/live")).getMessage());
    }

    @Test
    public void rejectsNullAndUnparsableUrls() throws Exception {
        UpstreamUrlGuard guard = guard(host -> new InetAddress[]{ipv4(93, 184, 216, 34)});

        assertThrows(IllegalArgumentException.class, () -> guard.check((HttpUrl) null));
        assertEquals("invalid upstream url", assertThrows(IOException.class, () -> guard.check((String) null)).getMessage());
        assertEquals("invalid upstream url", assertThrows(IOException.class, () -> guard.check("not a url")).getMessage());
        assertEquals("invalid upstream url", assertThrows(IOException.class, () -> guard.check("http://")).getMessage());
    }

    @Test
    public void translatesResolverFailuresIntoIoExceptions() throws Exception {
        UpstreamUrlGuard unknownHost = guard(host -> {
            throw new UncheckedIOException(new UnknownHostException(host));
        });
        assertEquals("unresolvable upstream host", assertThrows(IOException.class,
                () -> unknownHost.check("https://missing.example/live.m3u8")).getMessage());

        UpstreamUrlGuard exploding = guard(host -> {
            throw new IllegalStateException("dns exploded");
        });
        assertEquals("unresolvable upstream host", assertThrows(IOException.class,
                () -> exploding.check("https://missing.example/live.m3u8")).getMessage());

        UpstreamUrlGuard empty = guard(host -> new InetAddress[0]);
        assertEquals("unresolvable upstream host", assertThrows(IOException.class,
                () -> empty.check("https://missing.example/live.m3u8")).getMessage());
    }

    @Test
    public void resolveReturnsTheExactAddressesItValidated() throws Exception {
        InetAddress[] resolved = {ipv4(93, 184, 216, 34), ipv4(8, 8, 8, 8)};
        UpstreamUrlGuard guard = guard(host -> resolved);

        assertArrayEquals(resolved, guard.resolve("cdn.example"));

        UpstreamUrlGuard privateOnly = guard(host -> new InetAddress[]{ipv4(192, 168, 1, 5)});
        assertEquals(BLOCKED, assertThrows(IOException.class, () -> privateOnly.resolve("cdn.example")).getMessage());
    }

    private static void assertAddressBlocked(InetAddress... addresses) {
        UpstreamUrlGuard guard = guard(host -> addresses);
        IOException exception = assertThrows(IOException.class, () -> guard.check(HttpUrl.parse("https://cdn.example/live.m3u8")));
        assertEquals(BLOCKED, exception.getMessage());
    }

    private static void assertAddressAllowed(InetAddress... addresses) throws IOException {
        guard(host -> addresses).check(HttpUrl.parse("https://cdn.example/live.m3u8"));
    }

    private static UpstreamUrlGuard guard(Function<String, InetAddress[]> resolver) {
        return new UpstreamUrlGuard(resolver);
    }

    private static InetAddress ipv4(int first, int second, int third, int fourth) {
        return bytes((byte) first, (byte) second, (byte) third, (byte) fourth);
    }

    private static InetAddress literal(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    private static Inet6Address ipv6(byte[] address) {
        try {
            return Inet6Address.getByAddress(null, address, -1);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] mapped(int first, int second, int third, int fourth) {
        return new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff,
                (byte) first, (byte) second, (byte) third, (byte) fourth};
    }

    private static byte[] nat64(int first, int second, int third, int fourth) {
        return new byte[]{0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0,
                (byte) first, (byte) second, (byte) third, (byte) fourth};
    }

    private static byte[] sixToFour(int first, int second, int third, int fourth) {
        return new byte[]{0x20, 0x02, (byte) first, (byte) second, (byte) third, (byte) fourth,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    private static InetAddress bytes(byte... address) {
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }
}
