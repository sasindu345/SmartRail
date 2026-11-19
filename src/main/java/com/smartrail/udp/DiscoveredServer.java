package com.smartrail.udp;

/**
 * OWNER: Member 3. Parsed {@code DISCOVER_REPLY}: where the central server's TCP services are.
 */
public record DiscoveredServer(String host, int plainPort, int tlsPort, String protocolVersion) {
}
