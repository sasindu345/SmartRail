package com.smartrail.multicast;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.common.service.AlertService;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.RailwayService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Optional;

/**
 * OWNER: Member 3 - CONCEPT: IP multicast (one-to-many UDP), sender side.
 *
 * <p>Sends one {@code EMERGENCY_ALERT} datagram to the multicast group so every subscribed train receives it
 * without the server looping over each TCP/TLS connection. When {@code multicast.unicast.fallback.enabled} is
 * set (some Wi-Fi networks block multicast), the same datagram is also sent individually to every connected
 * train's IP address, on the same port the subscriber listens on.
 */
public final class MulticastAlertPublisher implements AlertService, NetworkService {

    private final NetworkConfig config;
    private final EventLog log;
    private volatile RailwayService railway;
    private volatile MulticastSocket socket;
    private volatile InetAddress group;
    private volatile int port;

    public MulticastAlertPublisher(NetworkConfig config, EventLog log) {
        this.config = config;
        this.log = log;
    }

    /** Called once by ServerApp after the railway service exists (needed for the unicast fallback). */
    public void bindRailwayService(RailwayService railway) {
        this.railway = railway;
    }

    @Override
    public String name() {
        return "Multicast alert publisher";
    }

    @Override
    public String endpoint() {
        return "UDP multicast " + config.get(ConfigKeys.MULTICAST_GROUP) + ":" + config.getInt(ConfigKeys.MULTICAST_PORT);
    }

    @Override
    public void start() throws IOException {
        group = InetAddress.getByName(config.get(ConfigKeys.MULTICAST_GROUP));
        port = config.getInt(ConfigKeys.MULTICAST_PORT);

        MulticastSocket sendSocket = new MulticastSocket();
        sendSocket.setTimeToLive(config.getInt(ConfigKeys.MULTICAST_TTL));
        resolveInterface().ifPresent(ni -> {
            try {
                sendSocket.setNetworkInterface(ni);
            } catch (IOException e) {
                log.log("MULTICAST", "Could not bind publisher to interface " + ni.getName() + ": " + e.getMessage());
            }
        });
        socket = sendSocket;
    }

    @Override
    public void publishAlert(String sectionId, String reason) {
        MulticastSocket current = socket;
        if (current == null) {
            log.log("MULTICAST", "Cannot publish alert - publisher not started");
            return;
        }
        Message alert = Message.of(MessageType.EMERGENCY_ALERT, sectionId, reason);
        byte[] bytes = ProtocolCodec.encode(alert).getBytes(ProtocolCodec.CHARSET);

        try {
            current.send(new DatagramPacket(bytes, bytes.length, group, port));
            log.log("MULTICAST", "Published " + alert + " to " + group.getHostAddress() + ":" + port);
        } catch (IOException e) {
            log.log("MULTICAST", "Failed to publish multicast alert: " + e.getMessage());
        }

        if (config.getBoolean(ConfigKeys.MULTICAST_UNICAST_FALLBACK_ENABLED)) {
            sendUnicastFallback(current, bytes);
        }
    }

    private void sendUnicastFallback(MulticastSocket current, byte[] bytes) {
        RailwayService r = railway;
        if (r == null) {
            return;
        }
        for (TrainInfo train : r.getTrains()) {
            String remoteAddress = train.remoteAddress();
            if (remoteAddress == null) {
                continue;
            }
            try {
                InetAddress trainAddress = InetAddress.getByName(extractHost(remoteAddress));
                current.send(new DatagramPacket(bytes, bytes.length, trainAddress, port));
                log.log("MULTICAST", "Unicast fallback alert sent to " + train.trainId() + " (" + trainAddress.getHostAddress() + ")");
            } catch (Exception e) {
                log.log("MULTICAST", "Unicast fallback failed for " + train.trainId() + " (" + remoteAddress + "): " + e.getMessage());
            }
        }
    }

    /** Extracts the host part of a {@code host:port} or {@code [ipv6]:port} remote address string. */
    private static String extractHost(String hostPort) {
        if (hostPort.startsWith("[")) {
            int end = hostPort.indexOf(']');
            return end > 0 ? hostPort.substring(1, end) : hostPort;
        }
        int lastColon = hostPort.lastIndexOf(':');
        return lastColon > 0 ? hostPort.substring(0, lastColon) : hostPort;
    }

    private Optional<NetworkInterface> resolveInterface() {
        Optional<String> configured = config.find(ConfigKeys.MULTICAST_INTERFACE);
        if (configured.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(NetworkInterface.getByName(configured.get()));
        } catch (IOException e) {
            log.log("MULTICAST", "Unknown multicast interface '" + configured.get() + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void stop() {
        MulticastSocket current = socket;
        socket = null;
        if (current != null) {
            current.close();
        }
    }
}
