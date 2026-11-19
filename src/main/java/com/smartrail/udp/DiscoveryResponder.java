package com.smartrail.udp;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

/**
 * OWNER: Member 3 - CONCEPT: UDP datagrams (connectionless request/reply), server side.
 *
 * <p>APIs: {@code DatagramSocket}, {@code DatagramPacket}, {@code InetSocketAddress}.
 * <ul>
 *   <li>TODO(M3) bind a DatagramSocket on {@link ConfigKeys#UDP_DISCOVERY_PORT}; loop on its own thread</li>
 *   <li>TODO(M3) on {@code DISCOVER|service|version} with matching {@link ConfigKeys#PROTOCOL_SERVICE_NAME}
 *       and version: reply {@code DISCOVER_REPLY|service|version|host|nioPort|tlsPort} to the sender's address</li>
 *   <li>TODO(M3) host = {@link ConfigKeys#SERVER_ADVERTISED_HOST} or an auto-detected site-local address</li>
 *   <li>TODO(M3) ignore/log malformed datagrams (never crash the loop)</li>
 * </ul>
 */
public final class DiscoveryResponder implements NetworkService {

    private final ServerContext ctx;

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
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M3): UDP discovery responder");
    }

    @Override
    public void stop() {
        // TODO(M3): close DatagramSocket
    }
}
