package com.smartrail.multicast;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.protocol.ProtocolCodec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.function.Consumer;

/**
 * OWNER: Member 3 - CONCEPT: IP multicast group membership, receiver side.
 *
 * <p>Joins the alert multicast group and calls {@code onAlert} for every {@code EMERGENCY_ALERT} datagram
 * received; any other datagram (including a stray packet from something else on the LAN) is ignored.
 * Unicast fallback datagrams from {@link MulticastAlertPublisher} arrive on the same port, so no extra code
 * is needed to receive them - they go through the identical decode-and-dispatch path.
 */
public final class MulticastAlertSubscriber {

    private final NetworkConfig config;
    private final Consumer<Message> onAlert;

    private volatile MulticastSocket socket;
    private volatile boolean running;
    private InetSocketAddress groupAddress;
    private NetworkInterface joinedInterface;
    private Thread loopThread;

    public MulticastAlertSubscriber(NetworkConfig config, Consumer<Message> onAlert) {
        this.config = config;
        this.onAlert = onAlert;
    }

    public void start() {
        try {
            int port = config.getInt(ConfigKeys.MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(config.get(ConfigKeys.MULTICAST_GROUP));
            groupAddress = new InetSocketAddress(group, port);
            joinedInterface = resolveInterface();

            MulticastSocket joined = new MulticastSocket(port);
            joined.joinGroup(groupAddress, joinedInterface);
            socket = joined;
            running = true;
            loopThread = new Thread(this::receiveLoop, "multicast-alert-subscriber");
            loopThread.setDaemon(true);
            loopThread.start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not join multicast group "
                    + config.get(ConfigKeys.MULTICAST_GROUP) + ":" + config.get(ConfigKeys.MULTICAST_PORT), e);
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[config.getInt(ConfigKeys.PROTOCOL_MAX_MESSAGE_BYTES)];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                // Expected when stop() closes the socket while receive() is blocked.
                break;
            }
            handleDatagram(packet.getData(), packet.getOffset(), packet.getLength());
        }
    }

    /**
     * Decodes one received datagram and dispatches it if (and only if) it is an {@code EMERGENCY_ALERT}.
     * Package-visible so tests can exercise the decode/filter logic directly, without needing a real
     * multicast-capable network (which some sandboxes and VPN setups do not provide).
     */
    void handleDatagram(byte[] data, int offset, int length) {
        try {
            String line = new String(data, offset, length, ProtocolCodec.CHARSET);
            Message message = ProtocolCodec.decode(line);
            if (message.type() == MessageType.EMERGENCY_ALERT) {
                onAlert.accept(message);
            }
        } catch (Exception malformed) {
            // Not a valid SmartRail message - ignore and keep listening.
        }
    }

    /**
     * Only resolves an explicit interface when the operator named one ({@code multicast.interface}) - for
     * a demo where several NICs are active and one specific one must be used. Otherwise returns
     * {@code null}, which tells {@code joinGroup}/{@code leaveGroup} to use the platform's own default
     * multicast interface. That is deliberately preferred over picking one ourselves: this machine, for
     * example, keeps several "utunN" point-to-point tunnel interfaces up (created by the OS, e.g. iCloud
     * Private Relay/Continuity - nothing the user connected) that report multicast support but cannot
     * actually carry multicast traffic, so a naive "first multicast-capable interface" search can pick a
     * dead one even though the real network adapter works fine.
     */
    private NetworkInterface resolveInterface() throws SocketException {
        var configured = config.find(ConfigKeys.MULTICAST_INTERFACE);
        if (configured.isEmpty()) {
            return null;
        }
        NetworkInterface ni = NetworkInterface.getByName(configured.get());
        if (ni == null) {
            throw new SocketException("Unknown multicast.interface '" + configured.get() + "'");
        }
        return ni;
    }

    public void stop() {
        running = false;
        MulticastSocket current = socket;
        if (current != null) {
            try {
                current.leaveGroup(groupAddress, joinedInterface);
            } catch (IOException ignored) {
                // Closing the socket right after is enough to stop receiving either way.
            }
            current.close();
        }
    }
}
