package com.smartrail.nio;

import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.common.service.TrainConnection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * OWNER: Member 1 - CONCEPT: Java NIO channel + buffers per client.
 *
 * <p>Wraps one non-blocking {@link SocketChannel}. {@link #send(Message)} only queues UTF-8 bytes
 * and wakes the selector; the selector thread registers {@code OP_WRITE} and {@link #flush()}
 * writes until the socket buffer is full, leaving a partial buffer queued for the next write.
 */
public final class NioTrainConnection implements TrainConnection {

    private final SocketChannel channel;
    private final SelectionKey key;
    private final LineFrameDecoder decoder;
    private final Queue<ByteBuffer> outgoing = new ConcurrentLinkedQueue<>();
    private volatile boolean open = true;

    public NioTrainConnection(SocketChannel channel, SelectionKey key, int maxMessageBytes) {
        this.channel = channel;
        this.key = key;
        this.decoder = new LineFrameDecoder(maxMessageBytes);
    }

    public LineFrameDecoder decoder() {
        return decoder;
    }

    /** {@code true} when {@link #send} has bytes the selector thread still has to write. */
    public boolean hasPending() {
        return !outgoing.isEmpty();
    }

    /**
     * Writes queued buffers. A short write leaves the remainder at the head of the queue.
     *
     * @return {@code true} when the queue is empty
     */
    public boolean flush() throws IOException {
        ByteBuffer next;
        while ((next = outgoing.peek()) != null) {
            channel.write(next);
            if (next.hasRemaining()) {
                return false;
            }
            outgoing.poll();
        }
        return true;
    }

    @Override
    public void send(Message message) {
        if (!open) {
            return;
        }
        outgoing.add(ByteBuffer.wrap(ProtocolCodec.toWireBytes(message)));
        SelectorWake.wakeup(key);
    }

    @Override
    public String remoteAddress() {
        try {
            if (channel.getRemoteAddress() instanceof InetSocketAddress remote && remote.getAddress() != null) {
                String host = remote.getAddress().getHostAddress();
                if (host.indexOf(':') >= 0) {
                    host = "[" + host + "]";
                }
                return host + ":" + remote.getPort();
            }
            return String.valueOf(channel.getRemoteAddress());
        } catch (IOException e) {
            return "unknown";
        }
    }

    @Override
    public Transport transport() {
        return Transport.PLAIN;
    }

    @Override
    public boolean isOpen() {
        return open && channel.isOpen();
    }

    @Override
    public void close() {
        open = false;
        try {
            key.cancel();
        } catch (RuntimeException ignored) {
            // Key may already be cancelled during shutdown.
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Closing a failed channel is best-effort.
        }
    }

    /** Wakes the selector without touching interest sets off the selector thread. */
    static final class SelectorWake {
        private SelectorWake() {
        }

        static void wakeup(SelectionKey key) {
            if (key != null && key.selector().isOpen()) {
                key.selector().wakeup();
            }
        }
    }
}
