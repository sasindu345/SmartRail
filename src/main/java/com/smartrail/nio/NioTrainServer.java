package com.smartrail.nio;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

/**
 * OWNER: Member 1 - CONCEPT: Java NIO non-blocking TCP server.
 *
 * <p>Plain train-control channel. One selector thread serves every train client.
 * APIs: {@code ServerSocketChannel}, {@code SocketChannel}, {@code Selector}, {@code SelectionKey},
 * {@code ByteBuffer}, {@code InetSocketAddress}.
 *
 * <p>Plan: docs/plan/member-1-nio-server.md
 * <ul>
 *   <li>TODO(M1) open a ServerSocketChannel, configureBlocking(false), bind to
 *       {@link ConfigKeys#SERVER_BIND_ADDRESS}:{@link ConfigKeys#NIO_TCP_PORT}</li>
 *   <li>TODO(M1) register OP_ACCEPT; run the select loop on its own thread
 *       (timeout {@link ConfigKeys#NIO_SELECTOR_TIMEOUT_MS})</li>
 *   <li>TODO(M1) on accept: configure the SocketChannel non-blocking, register OP_READ,
 *       attach a {@link NioTrainConnection}</li>
 *   <li>TODO(M1) on read: feed bytes to the connection's {@link LineFrameDecoder}, decode each line with
 *       ProtocolCodec, pass messages to {@code ctx.messageHandler()}; -1 read = disconnect</li>
 *   <li>TODO(M1) on write: flush the connection's pending queue, drop OP_WRITE when empty</li>
 *   <li>TODO(M1) stop(): close selector + all channels, call messageHandler.onDisconnect for each</li>
 * </ul>
 */
public final class NioTrainServer implements NetworkService {

    private final ServerContext ctx;

    public NioTrainServer(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "NIO train-control server";
    }

    @Override
    public String endpoint() {
        return "TCP " + ctx.config().get(ConfigKeys.SERVER_BIND_ADDRESS) + ":" + ctx.config().getInt(ConfigKeys.NIO_TCP_PORT);
    }

    @Override
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M1): Selector-based NIO server");
    }

    @Override
    public void stop() {
        // TODO(M1): close selector, server channel and all client channels
    }
}
