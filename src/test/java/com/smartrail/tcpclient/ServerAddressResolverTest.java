package com.smartrail.tcpclient;

import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OWNER: Member 2 - InetAddress resolution, IPv4/IPv6 description, local interfaces. */
class ServerAddressResolverTest {

    private final ServerAddressResolver resolver = new ServerAddressResolver();

    @Test
    void resolvesLocalhost() throws Exception {
        InetAddress address = resolver.resolve("localhost");
        assertTrue(address.isLoopbackAddress());
        assertFalse(resolver.resolveAll("localhost").isEmpty());
    }

    @Test
    void resolvesIpv4AndIpv6Literals() throws Exception {
        InetAddress ipv4 = resolver.resolve("127.0.0.1");
        assertInstanceOf(Inet4Address.class, ipv4);
        assertTrue(ipv4.isLoopbackAddress());

        InetAddress ipv6 = resolver.resolve("::1");
        assertInstanceOf(Inet6Address.class, ipv6);
        assertTrue(ipv6.isLoopbackAddress());
    }

    @Test
    void describesIpv4AndIpv6() throws Exception {
        String ipv4 = resolver.describe(InetAddress.getByName("127.0.0.1"));
        assertTrue(ipv4.contains("IPv4"), ipv4);
        assertTrue(ipv4.contains("loopback"), ipv4);

        String ipv6 = resolver.describe(InetAddress.getByName("::1"));
        assertTrue(ipv6.contains("IPv6"), ipv6);
        assertTrue(ipv6.contains("loopback"), ipv6);

        String privateIpv4 = resolver.describe(InetAddress.getByName("192.168.1.20"));
        assertTrue(privateIpv4.contains("IPv4"), privateIpv4);
        assertTrue(privateIpv4.contains("site-local/private"), privateIpv4);

        String linkLocalIpv6 = resolver.describe(InetAddress.getByName("fe80::1"));
        assertTrue(linkLocalIpv6.contains("IPv6"), linkLocalIpv6);
        assertTrue(linkLocalIpv6.contains("link-local"), linkLocalIpv6);

        String multicast = resolver.describe(InetAddress.getByName("230.0.0.1"));
        assertTrue(multicast.contains("multicast"), multicast);

        String publicIpv4 = resolver.describe(InetAddress.getByName("8.8.8.8"));
        assertTrue(publicIpv4.contains("public"), publicIpv4);
    }

    @Test
    void unknownHostGivesClearError() {
        UnknownHostException error = assertThrows(UnknownHostException.class,
                () -> resolver.resolve("no-such-host.invalid"));
        assertTrue(error.getMessage().startsWith("Unknown host:"), error.getMessage());

        assertThrows(UnknownHostException.class, () -> resolver.resolve(null));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("   "));
    }

    @Test
    void listsLocalInterfaces() throws Exception {
        assertNotNull(resolver.localHost());
        var lines = resolver.localInterfaceAddresses();
        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().anyMatch(line -> line.contains("loopback")), lines.toString());
    }
}
