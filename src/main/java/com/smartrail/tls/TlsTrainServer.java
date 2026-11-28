package com.smartrail.tls;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.common.protocol.ProtocolException;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

/**
 * OWNER: Member 4 - CONCEPT: TLS secure server socket (thread-per-client, blocking I/O).
 *
 * <p>NIO belongs to Member 1; this server intentionally uses blocking {@code SSLServerSocket}
 * to demonstrate the TLS handshake, cipher negotiation, and certificate validation directly.
 * One accept thread + one virtual thread per connected train client.
 */
public final class TlsTrainServer implements NetworkService {

    private final ServerContext ctx;
    private SSLServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;
    private volatile int boundPort = -1;

    public TlsTrainServer(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "TLS train-control server";
    }

    @Override
    public String endpoint() {
        int port = boundPort >= 0 ? boundPort : ctx.config().getInt(ConfigKeys.TLS_PORT);
        return "TLS " + ctx.config().get(ConfigKeys.SERVER_BIND_ADDRESS) + ":" + port;
    }

    /** Actual port chosen by the OS when configured as 0. {@code -1} before {@link #start()}. */
    public int boundPort() {
        return boundPort;
    }

    @Override
    public void start() throws Exception {
        if (running) return;

        SslContextFactory factory = new SslContextFactory(ctx.config());
        SSLServerSocketFactory ssf = factory.serverContext().getServerSocketFactory();

        int configPort = ctx.config().getInt(ConfigKeys.TLS_PORT);
        serverSocket = (SSLServerSocket) ssf.createServerSocket();
        serverSocket.bind(new InetSocketAddress(ctx.config().get(ConfigKeys.SERVER_BIND_ADDRESS), configPort));
        boundPort = serverSocket.getLocalPort();

        // restrict to configured protocols e.g. TLSv1.3,TLSv1.2
        String[] protocols = ctx.config().getList(ConfigKeys.TLS_PROTOCOLS).toArray(String[]::new);
        serverSocket.setEnabledProtocols(protocols);

        running = true;
        ctx.eventLog().log("TLS", "Selector listening on " + endpoint());

        acceptThread = Thread.ofVirtual().name("tls-accept").start(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                SSLSocket client = (SSLSocket) serverSocket.accept();
                Thread.ofVirtual().name("tls-client-" + client.getRemoteSocketAddress())
                        .start(() -> handleClient(client));
            } catch (IOException e) {
                if (running) ctx.eventLog().log("TLS", "Accept error: " + e.getMessage());
            }
        }
    }

    private void handleClient(SSLSocket client) {
        String addr = client.getRemoteSocketAddress().toString();
        try {
            client.startHandshake();
            SSLSession session = client.getSession();
            ctx.eventLog().log("TLS", "Handshake OK " + addr
                    + " proto=" + session.getProtocol()
                    + " cipher=" + session.getCipherSuite());

            TlsTrainConnection conn = new TlsTrainConnection(client);
            int maxBytes = ctx.config().getInt(ConfigKeys.PROTOCOL_MAX_MESSAGE_BYTES);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), ProtocolCodec.CHARSET))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > maxBytes) {
                        ctx.eventLog().log("TLS", "Oversized message from " + addr + " — dropped");
                        continue;
                    }
                    try {
                        ctx.messageHandler().onMessage(conn, ProtocolCodec.decode(line));
                    } catch (ProtocolException e) {
                        ctx.messageHandler().onProtocolError(conn, e);
                    }
                }
            }
            ctx.messageHandler().onDisconnect(conn);
            ctx.eventLog().log("TLS", "Disconnected " + addr + " (client closed)");
        } catch (SSLHandshakeException e) {
            // not fatal — a rogue client or misconfigured truststore; log and move on
            ctx.eventLog().log("TLS", "Handshake FAILED " + addr + ": " + e.getMessage());
        } catch (IOException e) {
            if (running) ctx.eventLog().log("TLS", "Client error " + addr + ": " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }
}
