package com.smartrail.multicast;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.service.AlertService;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.RailwayService;

/**
 * OWNER: Member 3 - CONCEPT: IP multicast (one-to-many UDP), sender side.
 *
 * <p>APIs: {@code MulticastSocket} or {@code DatagramChannel} with {@code StandardProtocolFamily},
 * {@code InetSocketAddress}, {@code NetworkInterface}, {@code StandardSocketOptions.IP_MULTICAST_TTL}.
 * <ul>
 *   <li>TODO(M3) start(): open the sending socket; TTL {@link ConfigKeys#MULTICAST_TTL};
 *       interface {@link ConfigKeys#MULTICAST_INTERFACE} if set</li>
 *   <li>TODO(M3) publishAlert(): send {@code EMERGENCY_ALERT|sectionId|reason} to
 *       {@link ConfigKeys#MULTICAST_GROUP}:{@link ConfigKeys#MULTICAST_PORT}; short alerts only</li>
 *   <li>TODO(M3) unicast fallback ({@link ConfigKeys#MULTICAST_UNICAST_FALLBACK_ENABLED}): also send the same
 *       datagram to each connected train's IP (from railway.getTrains()) on the multicast port</li>
 * </ul>
 */
public final class MulticastAlertPublisher implements AlertService, NetworkService {

    private final NetworkConfig config;
    private final EventLog log;
    private volatile RailwayService railway;

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
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M3): open multicast sending socket");
    }

    @Override
    public void publishAlert(String sectionId, String reason) {
        throw new UnsupportedOperationException("TODO(M3): multicast EMERGENCY_ALERT for " + sectionId);
    }

    @Override
    public void stop() {
        // TODO(M3): close socket
    }
}
