package com.smartrail.tcpclient;

import com.smartrail.common.model.Transport;
import com.smartrail.common.service.ClientSocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * OWNER: Member 2 - CONCEPT: TCP client socket.
 * <ul>
 *   <li>TODO(M2) new Socket(); socket.connect(address, timeoutMs); setTcpNoDelay; setKeepAlive</li>
 * </ul>
 * Member 4's TlsSocketFactory implements the same interface for the secure channel.
 */
public final class PlainSocketFactory implements ClientSocketFactory {

    @Override
    public Socket connect(InetSocketAddress address, int timeoutMs) throws IOException {
        throw new UnsupportedOperationException("TODO(M2): plain TCP connect to " + address);
    }

    @Override
    public Transport transport() {
        return Transport.PLAIN;
    }
}
