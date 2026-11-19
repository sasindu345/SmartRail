package com.smartrail.multicast;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.protocol.Message;

import java.util.function.Consumer;

/**
 * OWNER: Member 3 - CONCEPT: IP multicast group membership, receiver side.
 * <ul>
 *   <li>TODO(M3) bind to {@link ConfigKeys#MULTICAST_PORT}, joinGroup(group, networkInterface)
 *       (use the non-deprecated SocketAddress + NetworkInterface overload)</li>
 *   <li>TODO(M3) receive loop on a daemon thread; decode with ProtocolCodec; accept only EMERGENCY_ALERT;
 *       call onAlert</li>
 *   <li>TODO(M3) stop(): leaveGroup + close</li>
 * </ul>
 * Unicast fallback datagrams arrive on the same port, so no extra code is needed to receive them.
 */
public final class MulticastAlertSubscriber {

    private final NetworkConfig config;
    private final Consumer<Message> onAlert;

    public MulticastAlertSubscriber(NetworkConfig config, Consumer<Message> onAlert) {
        this.config = config;
        this.onAlert = onAlert;
    }

    public void start() {
        throw new UnsupportedOperationException("TODO(M3): join " + config.get(ConfigKeys.MULTICAST_GROUP));
    }

    public void stop() {
        // TODO(M3): leave group and close
    }
}
