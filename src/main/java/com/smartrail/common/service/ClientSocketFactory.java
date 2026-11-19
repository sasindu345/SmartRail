package com.smartrail.common.service;

import com.smartrail.common.model.Transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Opens the train client's connection. Member 2 provides {@code PlainSocketFactory}; Member 4 provides
 * {@code TlsSocketFactory}. The train client only depends on this interface, so switching to TLS is a
 * config change ({@code client.transport}). SHARED CONTRACT (frozen).
 */
public interface ClientSocketFactory {

    /** Connects (and for TLS completes the handshake) within {@code timeoutMs}. */
    Socket connect(InetSocketAddress address, int timeoutMs) throws IOException;

    Transport transport();
}
