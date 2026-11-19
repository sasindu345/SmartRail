package com.smartrail.urlconnection;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

/**
 * OWNER: Member 4 - CONCEPT: HTTP resource endpoints consumed through URL/URLConnection.
 *
 * <p>APIs: {@code com.sun.net.httpserver.HttpServer} (built into the JDK), {@code HttpExchange}.
 * <ul>
 *   <li>TODO(M4) HttpServer on {@link ConfigKeys#HTTP_RESOURCE_PORT}, context {@link ConfigKeys#HTTP_RESOURCE_BASE_PATH}</li>
 *   <li>TODO(M4) GET {base}/trains, /trains/{id}, /tracks, /tracks/{id}, /schedules, /stations ->
 *       new ResourceResolver(ctx.railwayService()).resolve(path); set Content-Type to response.mimeType()
 *       and the status code</li>
 *   <li>TODO(M4) 405 for non-GET; small executor; log each request (method, path, client IP)</li>
 * </ul>
 */
public final class ResourceHttpServer implements NetworkService {

    private final ServerContext ctx;

    public ResourceHttpServer(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "HTTP resource service";
    }

    @Override
    public String endpoint() {
        return "HTTP *:" + ctx.config().getInt(ConfigKeys.HTTP_RESOURCE_PORT) + ctx.config().get(ConfigKeys.HTTP_RESOURCE_BASE_PATH);
    }

    @Override
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M4): HttpServer resource endpoints");
    }

    @Override
    public void stop() {
        // TODO(M4): server.stop(0)
    }
}
