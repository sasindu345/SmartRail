package com.smartrail.common.config;

/**
 * Names of every key in {@code config/smartrail.properties}.
 *
 * <p>SHARED CONTRACT (frozen). Code must read settings through these constants and
 * {@link NetworkConfig}; never hard-code a port, host, timeout, path or password.
 * Adding a key = edit this class + smartrail.properties + docs/configuration.md in one PR.
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    // ---- Shared ----
    public static final String PROTOCOL_SERVICE_NAME = "protocol.service.name";
    public static final String PROTOCOL_VERSION = "protocol.version";
    public static final String PROTOCOL_MAX_MESSAGE_BYTES = "protocol.max.message.bytes";
    public static final String RAILWAY_DATA_PATH = "railway.data.path";
    public static final String LOG_CAPACITY = "log.capacity";

    // ---- Central server ----
    public static final String SERVER_BIND_ADDRESS = "server.bind.address";
    public static final String SERVER_ADVERTISED_HOST = "server.advertised.host";
    public static final String UI_SERVER_WINDOW_ENABLED = "ui.server.window.enabled";
    public static final String UI_TRAIN_WINDOW_ENABLED = "ui.train.window.enabled";

    // ---- Client side ----
    public static final String CLIENT_SERVER_HOST = "client.server.host";
    public static final String CLIENT_TRANSPORT = "client.transport";
    public static final String CLIENT_DEFAULT_TRAIN_ID = "client.default.train.id";
    public static final String CLIENT_CONNECT_TIMEOUT_MS = "client.connect.timeout.ms";
    public static final String CLIENT_RECONNECT_ATTEMPTS = "client.reconnect.attempts";
    public static final String CLIENT_RECONNECT_DELAY_MS = "client.reconnect.delay.ms";
    public static final String CLIENT_SIMULATOR_STEP_MS = "client.simulator.step.ms";

    // ---- Member 1: Java NIO ----
    public static final String NIO_TCP_ENABLED = "nio.tcp.enabled";
    public static final String NIO_TCP_PORT = "nio.tcp.port";
    public static final String NIO_SELECTOR_TIMEOUT_MS = "nio.selector.timeout.ms";

    // ---- Member 3: UDP / multicast / heartbeat ----
    public static final String UDP_DISCOVERY_ENABLED = "udp.discovery.enabled";
    public static final String UDP_DISCOVERY_PORT = "udp.discovery.port";
    public static final String UDP_DISCOVERY_BROADCAST_ADDRESS = "udp.discovery.broadcast.address";
    public static final String UDP_DISCOVERY_TIMEOUT_MS = "udp.discovery.timeout.ms";
    public static final String MULTICAST_ENABLED = "multicast.enabled";
    public static final String MULTICAST_GROUP = "multicast.group";
    public static final String MULTICAST_PORT = "multicast.port";
    public static final String MULTICAST_TTL = "multicast.ttl";
    public static final String MULTICAST_INTERFACE = "multicast.interface";
    public static final String MULTICAST_UNICAST_FALLBACK_ENABLED = "multicast.unicast.fallback.enabled";
    public static final String HEARTBEAT_ENABLED = "heartbeat.enabled";
    public static final String HEARTBEAT_INTERVAL_MS = "heartbeat.interval.ms";
    public static final String HEARTBEAT_TIMEOUT_MS = "heartbeat.timeout.ms";

    // ---- Member 4: TLS + URLConnection ----
    public static final String TLS_ENABLED = "tls.enabled";
    public static final String TLS_PORT = "tls.port";
    public static final String TLS_PROTOCOLS = "tls.protocols";
    public static final String TLS_KEYSTORE_TYPE = "tls.keystore.type";
    public static final String TLS_KEYSTORE_PATH = "tls.keystore.path";
    public static final String TLS_KEYSTORE_PASSWORD_ENV = "tls.keystore.password.env";
    public static final String TLS_TRUSTSTORE_PATH = "tls.truststore.path";
    public static final String TLS_TRUSTSTORE_PASSWORD_ENV = "tls.truststore.password.env";
    public static final String HTTP_RESOURCE_ENABLED = "http.resource.enabled";
    public static final String HTTP_RESOURCE_PORT = "http.resource.port";
    public static final String HTTP_RESOURCE_BASE_PATH = "http.resource.base.path";
    public static final String HTTP_CLIENT_CONNECT_TIMEOUT_MS = "http.client.connect.timeout.ms";
    public static final String HTTP_CLIENT_READ_TIMEOUT_MS = "http.client.read.timeout.ms";

    // ---- Member 5: RMI + smartrail:// ----
    public static final String RMI_ENABLED = "rmi.enabled";
    public static final String RMI_REGISTRY_PORT = "rmi.registry.port";
    public static final String RMI_SERVICE_PORT = "rmi.service.port";
    public static final String RMI_SERVICE_NAME = "rmi.service.name";
    public static final String SMARTRAIL_RESOURCE_ENABLED = "smartrail.resource.enabled";
    public static final String SMARTRAIL_RESOURCE_PORT = "smartrail.resource.port";
    public static final String SMARTRAIL_RESOURCE_TIMEOUT_MS = "smartrail.resource.timeout.ms";
}
