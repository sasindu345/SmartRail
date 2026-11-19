package com.smartrail.tls;

import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.service.TrainConnection;

import javax.net.ssl.SSLSocket;

/**
 * OWNER: Member 4 - CONCEPT: TLS-protected connection to one train.
 * <ul>
 *   <li>TODO(M4) send(): synchronized write of ProtocolCodec.toWireBytes(message) + flush</li>
 *   <li>TODO(M4) remoteAddress(): socket.getRemoteSocketAddress()</li>
 * </ul>
 */
public final class TlsTrainConnection implements TrainConnection {

    private final SSLSocket socket;

    public TlsTrainConnection(SSLSocket socket) {
        this.socket = socket;
    }

    @Override
    public void send(Message message) {
        throw new UnsupportedOperationException("TODO(M4): write over TLS " + message);
    }

    @Override
    public String remoteAddress() {
        throw new UnsupportedOperationException("TODO(M4): remote address");
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
        // TODO(M4): close socket
    }
}
