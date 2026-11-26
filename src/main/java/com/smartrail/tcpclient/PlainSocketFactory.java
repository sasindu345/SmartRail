package com.smartrail.tcpclient;

import com.smartrail.common.model.Transport;
import com.smartrail.common.service.ClientSocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * OWNER: Member 2 - CONCEPT: TCP client socket.
 *
 * <p>Opens one plain {@link Socket} to the NIO server. The connect timeout comes from the caller
 * ({@code client.connect.timeout.ms}). TCP_NODELAY and keep-alive keep short protocol lines moving
 * and let a dead server be noticed. Member 4's {@code TlsSocketFactory} implements the same
 * interface for the secure channel.
 */
public final class PlainSocketFactory implements ClientSocketFactory {

    @Override
    public Socket connect(InetSocketAddress address, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        socket.connect(address, timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        return socket;
    }

    @Override
    public Transport transport() {
        return Transport.PLAIN;
    }
}
