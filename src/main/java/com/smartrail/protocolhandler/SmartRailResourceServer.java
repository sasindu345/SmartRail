package com.smartrail.protocolhandler;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

/**
 * OWNER: Member 5 - CONCEPT: server side of the custom {@code smartrail://} protocol.
 * <ul>
 *   <li>TODO(M5) ServerSocket on {@link ConfigKeys#SMARTRAIL_RESOURCE_PORT}; accept loop thread</li>
 *   <li>TODO(M5) per request: read {@code GET <path>}, resolve with new ResourceResolver(ctx.railwayService()),
 *       write {@code <status> <mimeType>\n<body>}, close</li>
 *   <li>TODO(M5) bad request line -> {@code 400 text/plain}; log each request</li>
 * </ul>
 */
public final class SmartRailResourceServer implements NetworkService {

    private final ServerContext ctx;

    public SmartRailResourceServer(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "smartrail:// resource server";
    }

    @Override
    public String endpoint() {
        return "TCP *:" + ctx.config().getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT) + " (smartrail://)";
    }

    @Override
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M5): smartrail resource server");
    }

    @Override
    public void stop() {
        // TODO(M5): close server socket
    }
}
