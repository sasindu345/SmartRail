package com.smartrail.protocolhandler;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.resource.ResourceResolver;
import com.smartrail.common.resource.ResourceTypes;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OWNER: Member 5 - CONCEPT: server side of the custom {@code smartrail://} protocol.
 * <ul>
 *   <li>ServerSocket on {@link ConfigKeys#SMARTRAIL_RESOURCE_PORT}; accept loop thread</li>
 *   <li>Per request: read {@code GET <path>}, resolve with ResourceResolver(ctx.railwayService()),
 *       write {@code <status> <mimeType>\n<body>}, close</li>
 *   <li>Bad request line -> {@code 400 text/plain}; logs each request</li>
 * </ul>
 */
public final class SmartRailResourceServer implements NetworkService {

    private final ServerContext ctx;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService clientExecutor;
    private volatile boolean running;

    public SmartRailResourceServer(ServerContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public String name() {
        return "smartrail:// resource server";
    }

    @Override
    public String endpoint() {
        int port = serverSocket != null ? serverSocket.getLocalPort() : ctx.config().getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT);
        return "TCP *:" + port + " (smartrail://)";
    }

    public int port() {
        return serverSocket != null ? serverSocket.getLocalPort() : ctx.config().getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT);
    }

    @Override
    public synchronized void start() throws Exception {
        int port = ctx.config().getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT);
        serverSocket = new ServerSocket(port);
        running = true;

        clientExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "smartrail-resource-worker");
            t.setDaemon(true);
            return t;
        });

        acceptThread = new Thread(this::acceptLoop, "smartrail-resource-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
            clientExecutor = null;
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                clientExecutor.submit(() -> handleRequest(client));
            } catch (SocketException e) {
                // ServerSocket closed during stop()
                break;
            } catch (IOException e) {
                if (running) {
                    ctx.eventLog().log("RESOURCE", "Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void handleRequest(Socket client) {
        try (client) {
            client.setSoTimeout(ctx.config().getInt(ConfigKeys.SMARTRAIL_RESOURCE_TIMEOUT_MS));
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = client.getOutputStream();

            String line = in.readLine();
            if (line == null) {
                return;
            }
            line = line.trim();
            ctx.eventLog().log("RESOURCE", "Request: " + line + " from " + client.getRemoteSocketAddress());

            if (!line.startsWith("GET ") || line.length() <= 4) {
                String err = "400 " + ResourceTypes.MIME_ERROR + "\nMalformed request line\n";
                out.write(err.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return;
            }

            String path = line.substring(4).trim();
            ResourceResolver resolver = new ResourceResolver(ctx.railwayService());
            ResourceResolver.Response response = resolver.resolve(path);

            String header = response.status() + " " + response.mimeType() + "\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            if (response.body() != null && !response.body().isEmpty()) {
                out.write(response.body().getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        } catch (Exception e) {
            ctx.eventLog().log("RESOURCE", "Error handling request: " + e.getMessage());
        }
    }
}
