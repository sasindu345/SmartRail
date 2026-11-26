package com.smartrail.tcpclient;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void describesIpv4AndIpv6() throws Exception {
        String ipv4 = resolver.describe(InetAddress.getByName("127.0.0.1"));
        assertTrue(ipv4.contains("IPv4"), ipv4);
        assertTrue(ipv4.contains("loopback"), ipv4);

        String ipv6 = resolver.describe(InetAddress.getByName("::1"));
        assertTrue(ipv6.contains("IPv6"), ipv6);
        assertTrue(ipv6.contains("loopback"), ipv6);
    }

    @Test
    void unknownHostGivesClearError() {
        UnknownHostException error = assertThrows(UnknownHostException.class,
                () -> resolver.resolve("no-such-host.invalid"));
        assertTrue(error.getMessage().startsWith("Unknown host:"), error.getMessage());
    }

    @Test
    void listsLocalInterfaces() {
        var lines = resolver.localInterfaceAddresses();
        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().anyMatch(line -> line.contains("loopback")), lines.toString());
    }
}
