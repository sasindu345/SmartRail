package com.smartrail.tcpclient;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * OWNER: Member 2 - CONCEPT: IP addressing, InetAddress, host name resolution, IPv4/IPv6.
 *
 * <p>Resolves the server host from configuration and describes the address the train actually
 * connects to (loopback, private, link-local, multicast). The train client uses this before
 * {@code Socket.connect}, so the dashboard can show which IP and family the socket uses.
 */
public final class ServerAddressResolver {

    public InetAddress resolve(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new UnknownHostException("Unknown host: " + host);
        }
        try {
            return InetAddress.getByName(host.trim());
        } catch (UnknownHostException e) {
            throw new UnknownHostException("Unknown host: " + host);
        }
    }

    public List<InetAddress> resolveAll(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new UnknownHostException("Unknown host: " + host);
        }
        try {
            return List.of(InetAddress.getAllByName(host.trim()));
        } catch (UnknownHostException e) {
            throw new UnknownHostException("Unknown host: " + host);
        }
    }

    /** Returns this host's primary address via {@link InetAddress#getLocalHost()}. */
    public InetAddress localHost() throws UnknownHostException {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new UnknownHostException("Local host could not be resolved: " + e.getMessage());
        }
    }

    /** e.g. "192.168.1.20 (IPv4, site-local/private)". */
    public String describe(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("Address must not be null");
        }
        String family = address instanceof Inet6Address ? "IPv6" : address instanceof Inet4Address ? "IPv4" : "IP";
        return address.getHostAddress() + " (" + family + ", " + kind(address) + ")";
    }

    /** e.g. ["en0 192.168.1.20 (IPv4, site-local/private)", "lo0 ::1 (IPv6, loopback)"]. */
    public List<String> localInterfaceAddresses() {
        List<String> lines = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return List.of("Could not list network interfaces: " + e.getMessage());
        }
        if (interfaces == null) {
            return List.of();
        }
        for (NetworkInterface iface : Collections.list(interfaces)) {
            try {
                if (!iface.isUp()) {
                    continue;
                }
            } catch (SocketException e) {
                continue;
            }
            for (InetAddress address : Collections.list(iface.getInetAddresses())) {
                lines.add(iface.getName() + " " + describe(address));
            }
        }
        return List.copyOf(lines);
    }

    private static String kind(InetAddress address) {
        if (address.isMulticastAddress()) {
            return "multicast";
        }
        if (address.isLoopbackAddress()) {
            return "loopback";
        }
        if (address.isLinkLocalAddress()) {
            return "link-local";
        }
        if (address.isSiteLocalAddress()) {
            return "site-local/private";
        }
        return "public";
    }
}
