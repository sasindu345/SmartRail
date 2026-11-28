package com.smartrail.urlconnection;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.resource.ResourceResolver;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * OWNER: Member 4 - CONCEPT: HTTP resource endpoints consumed through URL/URLConnection.
 *
 * <p>APIs: {@code com.sun.net.httpserver.HttpServer} (JDK built-in), {@code HttpExchange}.
 * GET {base}/trains|tracks|schedules|stations[/{id}] → ResourceResolver → response.
 * Non-GET methods get 405. Each request is logged to EventLog.
 */
public final class ResourceHttpServer implements NetworkService {

    private final ServerContext ctx;
    private HttpServer server;
    private volatile int boundPort = -1;

    public ResourceHttpServer(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "HTTP resource service";
    }

    @Override
    public String endpoint() {
        int port = boundPort >= 0 ? boundPort : ctx.config().getInt(ConfigKeys.HTTP_RESOURCE_PORT);
        return "HTTP *:" + port + ctx.config().get(ConfigKeys.HTTP_RESOURCE_BASE_PATH);
    }

    /** Actual port chosen by the OS when configured as 0. {@code -1} before {@link #start()}. */
    public int boundPort() {
        return boundPort;
    }

    @Override
    public void start() throws Exception {
        int configPort = ctx.config().getInt(ConfigKeys.HTTP_RESOURCE_PORT);
        server = HttpServer.create(new InetSocketAddress(configPort), 0);
        boundPort = server.getAddress().getPort();

        ResourceResolver resolver = new ResourceResolver(ctx.railwayService());
        String basePath = ctx.config().get(ConfigKeys.HTTP_RESOURCE_BASE_PATH);  // "/api"

        server.createContext(basePath, exchange -> handle(exchange, resolver, basePath));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        ctx.eventLog().log("HTTP", "Resource server started on " + endpoint());
    }

    private void handle(HttpExchange ex, ResourceResolver resolver, String basePath) throws IOException {
        String method = ex.getRequestMethod();
        // strip the base path prefix to get e.g. "/trains/TR01" → "trains/TR01"
        String rawPath = ex.getRequestURI().getPath().substring(basePath.length());
        if (rawPath.startsWith("/")) rawPath = rawPath.substring(1);
        String clientIp = ex.getRemoteAddress().toString();
        ctx.eventLog().log("HTTP", method + " " + rawPath + " from " + clientIp);

        if (!"GET".equalsIgnoreCase(method)) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }

        ResourceResolver.Response resp = resolver.resolve(rawPath);
        byte[] body = resp.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", resp.mimeType());
        ex.sendResponseHeaders(resp.status(), body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    @Override
    public void stop() {
        if (server != null) server.stop(0);
    }
}
