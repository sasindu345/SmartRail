package com.smartrail.common.service;

/**
 * A server-side network service started by {@code ServerApp} (NIO server, TLS server, UDP discovery,
 * multicast publisher, heartbeat monitor, HTTP resources, smartrail:// resources, RMI).
 * Each member implements this for their own service, so ServerApp never needs to change.
 * SHARED CONTRACT (frozen).
 */
public interface NetworkService {

    /** Human-readable name shown in logs and the Control Center. */
    String name();

    /** Where the service listens, e.g. {@code "TCP 0.0.0.0:5000"}; read from config, never hard-coded. */
    String endpoint();

    /** Opens sockets and starts background threads. Must return quickly (do not block the caller). */
    void start() throws Exception;

    /** Closes sockets and stops threads. Must be safe to call more than once. */
    void stop();
}
