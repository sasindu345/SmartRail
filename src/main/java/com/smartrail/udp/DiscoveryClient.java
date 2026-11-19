package com.smartrail.udp;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import java.util.Optional;

/**
 * OWNER: Member 3 - CONCEPT: UDP broadcast discovery, client side.
 * <ul>
 *   <li>TODO(M3) DatagramSocket with setBroadcast(true); send DISCOVER to
 *       {@link ConfigKeys#UDP_DISCOVERY_BROADCAST_ADDRESS}:{@link ConfigKeys#UDP_DISCOVERY_PORT}</li>
 *   <li>TODO(M3) setSoTimeout({@link ConfigKeys#UDP_DISCOVERY_TIMEOUT_MS}); on timeout return empty
 *       (the UI then offers manual host/IP entry)</li>
 *   <li>TODO(M3) validate the reply type/service/version before trusting it</li>
 * </ul>
 */
public final class DiscoveryClient {

    private final NetworkConfig config;

    public DiscoveryClient(NetworkConfig config) {
        this.config = config;
    }

    public Optional<DiscoveredServer> discover() {
        throw new UnsupportedOperationException("TODO(M3): broadcast DISCOVER on port " + config.get(ConfigKeys.UDP_DISCOVERY_PORT));
    }
}
