package com.smartrail.tcpclient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * OWNER: Member 2 - CONCEPT: IP addressing, InetAddress, host name resolution, IPv4/IPv6.
 *
 * <p>APIs: {@code InetAddress.getByName / getAllByName / getLocalHost}, {@code Inet4Address},
 * {@code Inet6Address}, {@code NetworkInterface.getNetworkInterfaces()}.
 * <ul>
 *   <li>TODO(M2) resolve a host name or literal IP (never assume IPv4 only)</li>
 *   <li>TODO(M2) describe an address: IPv4/IPv6, loopback, site-local (private), link-local, multicast</li>
 *   <li>TODO(M2) list this machine's interface addresses (for the UI and docs evidence)</li>
 *   <li>TODO(M2) clear error message for unknown host (shown in the UI, no stack trace)</li>
 * </ul>
 */
public final class ServerAddressResolver {

    public InetAddress resolve(String host) throws UnknownHostException {
        throw new UnsupportedOperationException("TODO(M2): resolve " + host);
    }

    public List<InetAddress> resolveAll(String host) throws UnknownHostException {
        throw new UnsupportedOperationException("TODO(M2): resolve all addresses of " + host);
    }

    /** e.g. "192.168.1.20 (IPv4, site-local/private)". */
    public String describe(InetAddress address) {
        throw new UnsupportedOperationException("TODO(M2): describe " + address);
    }

    /** e.g. ["en0 192.168.1.20 (IPv4, site-local)", "lo0 ::1 (IPv6, loopback)"]. */
    public List<String> localInterfaceAddresses() {
        throw new UnsupportedOperationException("TODO(M2): enumerate NetworkInterface addresses");
    }
}
