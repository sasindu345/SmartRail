package com.smartrail.tls;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.model.Transport;
import com.smartrail.common.service.ClientSocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * OWNER: Member 4 - CONCEPT: TLS client socket with certificate + host name verification.
 *
 * <p>Used by Member 2's TrainClient when {@code client.transport=TLS}.
 * <ul>
 *   <li>TODO(M4) SSLSocket from SslContextFactory.clientContext(); connect with timeout</li>
 *   <li>TODO(M4) SSLParameters.setEndpointIdentificationAlgorithm("HTTPS") so the certificate must match the host</li>
 *   <li>TODO(M4) startHandshake(); on SSLHandshakeException close and rethrow (no bypass)</li>
 * </ul>
 */
public final class TlsSocketFactory implements ClientSocketFactory {

    private final NetworkConfig config;

    public TlsSocketFactory(NetworkConfig config) {
        this.config = config;
    }

    @Override
    public Socket connect(InetSocketAddress address, int timeoutMs) throws IOException {
        throw new UnsupportedOperationException("TODO(M4): TLS connect + handshake to " + address);
    }

    @Override
    public Transport transport() {
        return Transport.TLS;
    }
}
