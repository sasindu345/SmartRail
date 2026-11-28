package com.smartrail.tls;

import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.common.service.TrainConnection;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.OutputStream;

/**
 * OWNER: Member 4 - CONCEPT: TLS-protected connection to one train.
 *
 * <p>
 * Thread-safe send: synchronized on the output stream so concurrent server
 * commands
 * (SECTION_GRANTED, HOLD, etc.) cannot interleave bytes on the wire.
 */
public final class TlsTrainConnection implements TrainConnection {

    private final SSLSocket socket;

    public TlsTrainConnection(SSLSocket socket) {
        this.socket = socket;
    }

    @Override
    public synchronized void send(Message message) {
        try {
            OutputStream out = socket.getOutputStream();
            out.write(ProtocolCodec.toWireBytes(message));
            out.flush();
        } catch (IOException e) {
            // close so isOpen() returns false; caller must not retry on a dead socket
            close();
        }
    }

    @Override
    public String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public Transport transport() {
        return Transport.TLS;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
