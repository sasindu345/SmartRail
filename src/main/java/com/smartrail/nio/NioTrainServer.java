package com.smartrail.nio;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.common.protocol.ProtocolException;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

/**
 * OWNER: Member 1 - CONCEPT: Java NIO non-blocking TCP server.
 *
 * <p>One selector thread accepts and serves every plain train client. Reads go through
 * {@link LineFrameDecoder}; complete lines are decoded with {@link ProtocolCodec} and handed to
 * the shared {@code MessageHandler}. Writes are flushed only when a key is writable.
 */
public final class NioTrainServer implements NetworkService {

    private final ServerContext ctx;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Thread selectorThread;
    private volatile boolean running;
    private volatile int boundPort = -1;

    public NioTrainServer(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String name() {
        return "NIO train-control server";
    }

    /** Port actually bound. {@code -1} before {@link #start()}. Port {@code 0} in config means the OS chooses it. */
    public int boundPort() {
        return boundPort;
    }

    @Override
    public String endpoint() {
        int port = boundPort >= 0 ? boundPort : ctx.config().getInt(ConfigKeys.NIO_TCP_PORT);
        return "TCP " + ctx.config().get(ConfigKeys.SERVER_BIND_ADDRESS) + ":" + port;
    }

    @Override
    public void start() throws Exception {
        if (running) {
            return;
        }
        String bindAddress = ctx.config().get(ConfigKeys.SERVER_BIND_ADDRESS);
        int port = ctx.config().getInt(ConfigKeys.NIO_TCP_PORT);
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(bindAddress, port));
        boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        running = true;
        selectorThread = new Thread(this::selectLoop, "nio-selector");
        selectorThread.start();
        ctx.eventLog().log("NIO", "Selector listening on " + endpoint());
    }

    @Override
    public void stop() {
        running = false;
        Selector current = selector;
        if (current != null) {
            current.wakeup();
        }
        Thread thread = selectorThread;
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly();
    }

    private void selectLoop() {
        long timeout = ctx.config().getLong(ConfigKeys.NIO_SELECTOR_TIMEOUT_MS);
        while (running && selector.isOpen()) {
            try {
                selector.select(timeout);
                armWrites();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    try {
                        if (key.isAcceptable()) {
                            accept(key);
                        } else {
                            if (key.isReadable()) {
                                read(key);
                            }
                            if (key.isValid() && key.isWritable()) {
                                write(key);
                            }
                        }
                    } catch (IOException e) {
                        disconnect(key, e.getMessage());
                    }
                }
            } catch (ClosedSelectorException e) {
                break;
            } catch (IOException e) {
                if (running) {
                    ctx.eventLog().log("NIO", "Selector error: " + e.getMessage());
                }
            }
        }
    }

    /** Applies OP_WRITE on the selector thread for connections that queued a send. */
    private void armWrites() {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.attachment() instanceof NioTrainConnection connection && connection.hasPending()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
        int maxBytes = ctx.config().getInt(ConfigKeys.PROTOCOL_MAX_MESSAGE_BYTES);
        NioTrainConnection connection = new NioTrainConnection(client, clientKey, maxBytes);
        clientKey.attach(connection);
        ctx.eventLog().log("NIO", "Accepted " + connection.remoteAddress());
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        NioTrainConnection connection = (NioTrainConnection) key.attachment();
        ByteBuffer buffer = ByteBuffer.allocate(ctx.config().getInt(ConfigKeys.PROTOCOL_MAX_MESSAGE_BYTES));
        int read = channel.read(buffer);
        if (read == -1) {
            disconnect(key, "client closed");
            return;
        }
        buffer.flip();
        List<String> lines;
        try {
            lines = connection.decoder().decode(buffer);
        } catch (ProtocolException tooLarge) {
            ctx.messageHandler().onProtocolError(connection, tooLarge);
            disconnect(key, tooLarge.getMessage());
            return;
        }
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Message message = ProtocolCodec.decode(line);
                ctx.messageHandler().onMessage(connection, message);
            } catch (ProtocolException e) {
                ctx.messageHandler().onProtocolError(connection, e);
            }
        }
    }

    private void write(SelectionKey key) throws IOException {
        NioTrainConnection connection = (NioTrainConnection) key.attachment();
        if (connection.flush()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    private void disconnect(SelectionKey key, String reason) {
        if (key.attachment() instanceof NioTrainConnection connection) {
            ctx.eventLog().log("NIO", "Disconnected " + connection.remoteAddress() + " (" + reason + ")");
            try {
                ctx.messageHandler().onDisconnect(connection);
            } catch (RuntimeException e) {
                ctx.eventLog().log("NIO", "Disconnect handler failed: " + e.getMessage());
            }
            connection.close();
        }
        key.cancel();
    }

    private void closeQuietly() {
        try {
            if (selector != null && selector.isOpen()) {
                for (SelectionKey key : selector.keys()) {
                    if (key.attachment() instanceof NioTrainConnection connection && connection.isOpen()) {
                        try {
                            ctx.messageHandler().onDisconnect(connection);
                        } catch (RuntimeException ignored) {
                            // Shutdown still closes the channel.
                        }
                        connection.close();
                    }
                }
                selector.close();
            }
        } catch (IOException ignored) {
            // Shutdown closes whatever is still open.
        }
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}
