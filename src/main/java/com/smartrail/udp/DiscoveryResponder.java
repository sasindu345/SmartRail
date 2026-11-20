package com.smartrail.udp;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;

/**
 * OWNER: Member 3 - CONCEPT: UDP datagrams (connectionless request/reply), server side.
 *
 * <p>Listens for {@code DISCOVER|service|version} broadcasts and replies directly to the sender with
 * {@code DISCOVER_REPLY|service|version|host|nioPort|tlsPort}, so a train client never has to be told the
 * server's IP address manually.
 */
public final class DiscoveryResponder implements NetworkService {

    private final ServerContext ctx;
    private volatile DatagramSocket socket;
    private volatile boolean running;
    private Thread loopThread;

    public DiscoveryResponder(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "UDP discovery responder";
    }

    @Override
    public String endpoint() {
        return "UDP *:" + ctx.config().getInt(ConfigKeys.UDP_DISCOVERY_PORT);
    }

    @Override
    public void start() throws IOException {
        int port = ctx.config().getInt(ConfigKeys.UDP_DISCOVERY_PORT);
        InetAddress bindAddress = InetAddress.getByName(ctx.config().get(ConfigKeys.SERVER_BIND_ADDRESS));
        socket = new DatagramSocket(port, bindAddress);
        running = true;
        loopThread = new Thread(this::receiveLoop, "udp-discovery-responder");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    /** Actual local port in use (useful in tests when the configured port is {@code 0}). */
    public int boundPort() {
        DatagramSocket current = socket;
        return current == null ? -1 : current.getLocalPort();
    }

    private void receiveLoop() {
        int maxBytes = ctx.config().getInt(ConfigKeys.PROTOCOL_MAX_MESSAGE_BYTES);
        byte[] buffer = new byte[maxBytes];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                // Expected when stop() closes the socket while receive() is blocked.
                break;
            }
            try {
                handleRequest(packet);
            } catch (Exception malformed) {
                ctx.eventLog().log("UDP", "Ignored malformed discovery datagram from "
                        + packet.getSocketAddress() + ": " + malformed.getMessage());
            }
        }
    }

    private void handleRequest(DatagramPacket packet) throws Exception {
        String line = new String(packet.getData(), packet.getOffset(), packet.getLength(), ProtocolCodec.CHARSET);
        Message request = ProtocolCodec.decode(line);
        if (request.type() != MessageType.DISCOVER) {
            return;
        }
        String expectedService = ctx.config().get(ConfigKeys.PROTOCOL_SERVICE_NAME);
        String expectedVersion = ctx.config().get(ConfigKeys.PROTOCOL_VERSION);
        if (!request.field(0).equals(expectedService) || !request.field(1).equals(expectedVersion)) {
            return;
        }

        Message reply = Message.of(MessageType.DISCOVER_REPLY, expectedService, expectedVersion,
                resolveAdvertisedHost(), ctx.config().get(ConfigKeys.NIO_TCP_PORT), ctx.config().get(ConfigKeys.TLS_PORT));
        byte[] replyBytes = ProtocolCodec.encode(reply).getBytes(ProtocolCodec.CHARSET);
        socket.send(new DatagramPacket(replyBytes, replyBytes.length, packet.getSocketAddress()));
        ctx.eventLog().log("UDP", "Discovery reply sent to " + packet.getSocketAddress());
    }

    private String resolveAdvertisedHost() {
        Optional<String> configured = ctx.config().find(ConfigKeys.SERVER_ADVERTISED_HOST);
        if (configured.isPresent()) {
            return configured.get();
        }
        return autoDetectSiteLocalAddress().orElse(InetAddress.getLoopbackAddress().getHostAddress());
    }

    /** Picks the first non-loopback IPv4 address on an "up" interface - best effort for the demo LAN. */
    private static Optional<String> autoDetectSiteLocalAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        if (addr.isSiteLocalAddress()) {
                            return Optional.of(addr.getHostAddress());
                        }
                        if (fallback == null) {
                            fallback = addr.getHostAddress();
                        }
                    }
                }
            }
            return Optional.ofNullable(fallback);
        } catch (SocketException e) {
            return Optional.empty();
        }
    }

    @Override
    public void stop() {
        running = false;
        DatagramSocket current = socket;
        if (current != null) {
            current.close();
        }
    }
}
