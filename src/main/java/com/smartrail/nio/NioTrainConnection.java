package com.smartrail.nio;

import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.service.TrainConnection;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * OWNER: Member 1 - CONCEPT: Java NIO channel + buffers per client.
 *
 * <p>Wraps one non-blocking {@link SocketChannel}: owns its {@link LineFrameDecoder} and an outgoing
 * queue of {@code ByteBuffer}s.
 * <ul>
 *   <li>TODO(M1) send(): thread-safe enqueue of ProtocolCodec.toWireBytes(message), set OP_WRITE,
 *       call selector.wakeup() (send may be called from RMI/heartbeat threads)</li>
 *   <li>TODO(M1) flush(): write queued buffers until empty or the socket buffer is full
 *       (partial writes!)</li>
 *   <li>TODO(M1) remoteAddress(): from channel.getRemoteAddress()</li>
 * </ul>
 */
public final class NioTrainConnection implements TrainConnection {

    private final SocketChannel channel;
    private final SelectionKey key;
    private final LineFrameDecoder decoder;

    public NioTrainConnection(SocketChannel channel, SelectionKey key, int maxMessageBytes) {
        this.channel = channel;
        this.key = key;
        this.decoder = new LineFrameDecoder(maxMessageBytes);
    }

    public LineFrameDecoder decoder() {
        return decoder;
    }

    @Override
    public void send(Message message) {
        throw new UnsupportedOperationException("TODO(M1): queue bytes + OP_WRITE + wakeup");
    }

    @Override
    public String remoteAddress() {
        throw new UnsupportedOperationException("TODO(M1): channel.getRemoteAddress()");
    }

    @Override
    public Transport transport() {
        return Transport.PLAIN;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() {
        // TODO(M1): cancel key and close channel
    }
}
