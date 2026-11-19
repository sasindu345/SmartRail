package com.smartrail.tls;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

/**
 * OWNER: Member 4 - CONCEPT: TLS secure server socket.
 *
 * <p>Secure train-control channel (same protocol as Member 1's NIO channel). APIs:
 * {@code SSLServerSocketFactory}, {@code SSLServerSocket}, {@code SSLSocket}, {@code SSLSession}.
 * <ul>
 *   <li>TODO(M4) create SSLServerSocket on {@link ConfigKeys#TLS_PORT} from SslContextFactory.serverContext();
 *       restrict protocols to {@link ConfigKeys#TLS_PROTOCOLS}</li>
 *   <li>TODO(M4) accept loop thread; one handler thread (or small pool) per client</li>
 *   <li>TODO(M4) per client: startHandshake, log protocol + cipher suite, read lines (enforce
 *       {@code protocol.max.message.bytes}), decode with ProtocolCodec, pass to ctx.messageHandler()
 *       with a {@link TlsTrainConnection}</li>
 *   <li>TODO(M4) handshake failures (untrusted client/cert) are logged, not fatal</li>
 * </ul>
 */
public final class TlsTrainServer implements NetworkService {

    private final ServerContext ctx;

    public TlsTrainServer(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "TLS train-control server";
    }

    @Override
    public String endpoint() {
        return "TLS " + ctx.config().get(ConfigKeys.SERVER_BIND_ADDRESS) + ":" + ctx.config().getInt(ConfigKeys.TLS_PORT);
    }

    @Override
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M4): SSLServerSocket train server");
    }

    @Override
    public void stop() {
        // TODO(M4): close server socket and client sockets
    }
}
