package com.smartrail.udp;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.protocol.ProtocolCodec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Optional;

/**
 * OWNER: Member 3 - CONCEPT: UDP broadcast discovery, client side.
 *
 * <p>Sends one {@code DISCOVER} datagram to the configured broadcast address and waits (up to
 * {@code udp.discovery.timeout.ms} in total) for a {@code DISCOVER_REPLY} that matches this project's
 * service name and protocol version. Any other datagram received in that window - a stray packet from
 * something else on the LAN, or a reply for a different protocol version - is ignored and the client keeps
 * waiting until the deadline.
 */
public final class DiscoveryClient {

    private final NetworkConfig config;

    public DiscoveryClient(NetworkConfig config) {
        this.config = config;
    }

    public Optional<DiscoveredServer> discover() {
        String expectedService = config.get(ConfigKeys.PROTOCOL_SERVICE_NAME);
        String expectedVersion = config.get(ConfigKeys.PROTOCOL_VERSION);
        int port = config.getInt(ConfigKeys.UDP_DISCOVERY_PORT);
        int timeoutMs = config.getInt(ConfigKeys.UDP_DISCOVERY_TIMEOUT_MS);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);

            InetAddress broadcastAddress = InetAddress.getByName(config.get(ConfigKeys.UDP_DISCOVERY_BROADCAST_ADDRESS));
            byte[] requestBytes = ProtocolCodec.encode(Message.of(MessageType.DISCOVER, expectedService, expectedVersion))
                    .getBytes(ProtocolCodec.CHARSET);
            socket.send(new DatagramPacket(requestBytes, requestBytes.length, broadcastAddress, port));

            return waitForMatchingReply(socket, expectedService, expectedVersion, timeoutMs);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<DiscoveredServer> waitForMatchingReply(DatagramSocket socket, String expectedService,
                                                            String expectedVersion, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        byte[] buffer = new byte[config.getInt(ConfigKeys.PROTOCOL_MAX_MESSAGE_BYTES)];
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return Optional.empty();
            }
            try {
                socket.setSoTimeout((int) remaining);
                DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                socket.receive(reply);
                Optional<DiscoveredServer> parsed = parseReply(reply, expectedService, expectedVersion);
                if (parsed.isPresent()) {
                    return parsed;
                }
                // Not a matching reply - keep listening until the deadline.
            } catch (SocketTimeoutException timeout) {
                return Optional.empty();
            } catch (IOException e) {
                return Optional.empty();
            }
        }
    }

    private static Optional<DiscoveredServer> parseReply(DatagramPacket packet, String expectedService,
                                                          String expectedVersion) {
        try {
            String line = new String(packet.getData(), packet.getOffset(), packet.getLength(), ProtocolCodec.CHARSET);
            Message reply = ProtocolCodec.decode(line);
            if (reply.type() != MessageType.DISCOVER_REPLY) {
                return Optional.empty();
            }
            if (!reply.field(0).equals(expectedService) || !reply.field(1).equals(expectedVersion)) {
                return Optional.empty();
            }
            String host = reply.field(2);
            int plainPort = Integer.parseInt(reply.field(3));
            int tlsPort = Integer.parseInt(reply.field(4));
            return Optional.of(new DiscoveredServer(host, plainPort, tlsPort, reply.field(1)));
        } catch (Exception malformed) {
            return Optional.empty();
        }
    }
}
