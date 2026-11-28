package com.smartrail.tls;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.model.Transport;
import com.smartrail.common.service.ClientSocketFactory;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;

/**
 * OWNER: Member 4 - CONCEPT: TLS client socket with certificate + host name verification.
 *
 * <p>Used by Member 2's TrainClient when {@code client.transport=TLS}.
 * Sets endpoint identification to "HTTPS" so the JDK validates the server cert's SAN against
 * the connected hostname — no custom HostnameVerifier needed.
 */
public final class TlsSocketFactory implements ClientSocketFactory {

    private final NetworkConfig config;

    public TlsSocketFactory(NetworkConfig config) {
        this.config = config;
    }

    @Override
    public Socket connect(InetSocketAddress address, int timeoutMs) throws IOException {
        SslContextFactory factory = new SslContextFactory(config);
        SSLSocket socket;
        try {
            socket = (SSLSocket) factory.clientContext().getSocketFactory().createSocket();
        } catch (GeneralSecurityException e) {
            throw new IOException("TLS context error: " + e.getMessage(), e);
        }
        // enforce hostname validation using the standard HTTPS algorithm
        SSLParameters params = socket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(params);
        socket.connect(address, timeoutMs);
        socket.startHandshake();   // SSLHandshakeException propagates to caller — never swallowed
        return socket;
    }

    @Override
    public Transport transport() {
        return Transport.TLS;
    }
}
