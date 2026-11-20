package com.smartrail.udp;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.service.ServerContext;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER: Member 3 - tests for UDP discovery.
 *
 * <p>Uses a responder bound to an OS-chosen free port ({@code udp.discovery.port=0}) and points the client
 * at {@code 127.0.0.1} on that exact port, so the test never depends on real LAN broadcast delivery.
 */
class DiscoveryTest {

    private DiscoveryResponder responder;

    @AfterEach
    void stopResponder() {
        if (responder != null) {
            responder.stop();
        }
    }

    @Test
    void clientDiscoversResponder() throws Exception {
        NetworkConfig serverConfig = TestConfig.withOverrides(ConfigKeys.UDP_DISCOVERY_PORT, "0");
        responder = new DiscoveryResponder(serverContextFor(serverConfig));
        responder.start();

        NetworkConfig clientConfig = TestConfig.withOverrides(
                ConfigKeys.UDP_DISCOVERY_PORT, Integer.toString(responder.boundPort()),
                ConfigKeys.UDP_DISCOVERY_BROADCAST_ADDRESS, "127.0.0.1",
                ConfigKeys.UDP_DISCOVERY_TIMEOUT_MS, "2000");

        Optional<DiscoveredServer> result = new DiscoveryClient(clientConfig).discover();

        assertTrue(result.isPresent(), "expected a discovery reply");
        assertEquals(clientConfig.getInt(ConfigKeys.NIO_TCP_PORT), result.get().plainPort());
        assertEquals(clientConfig.getInt(ConfigKeys.TLS_PORT), result.get().tlsPort());
        assertEquals(clientConfig.get(ConfigKeys.PROTOCOL_VERSION), result.get().protocolVersion());
    }

    @Test
    void timeoutReturnsEmpty() {
        // Nothing is listening on this port, so the client must time out instead of hanging.
        NetworkConfig clientConfig = TestConfig.withOverrides(
                ConfigKeys.UDP_DISCOVERY_PORT, "0",
                ConfigKeys.UDP_DISCOVERY_BROADCAST_ADDRESS, "127.0.0.1",
                ConfigKeys.UDP_DISCOVERY_TIMEOUT_MS, "300");

        Optional<DiscoveredServer> result = new DiscoveryClient(clientConfig).discover();

        assertTrue(result.isEmpty(), "expected no reply when nothing is listening");
    }

    @Test
    void wrongServiceNameIsIgnored() throws Exception {
        NetworkConfig serverConfig = TestConfig.withOverrides(ConfigKeys.UDP_DISCOVERY_PORT, "0");
        responder = new DiscoveryResponder(serverContextFor(serverConfig));
        responder.start();

        // Same responder, but the client expects a different service name - the responder must stay silent.
        NetworkConfig clientConfig = TestConfig.withOverrides(
                ConfigKeys.UDP_DISCOVERY_PORT, Integer.toString(responder.boundPort()),
                ConfigKeys.UDP_DISCOVERY_BROADCAST_ADDRESS, "127.0.0.1",
                ConfigKeys.UDP_DISCOVERY_TIMEOUT_MS, "500",
                ConfigKeys.PROTOCOL_SERVICE_NAME, "SOME_OTHER_SERVICE");

        Optional<DiscoveredServer> result = new DiscoveryClient(clientConfig).discover();

        assertTrue(result.isEmpty(), "a reply for the wrong service name must not be accepted");
    }

    /** Only {@code config} and {@code eventLog} are used by DiscoveryResponder; the rest can stay null here. */
    private static ServerContext serverContextFor(NetworkConfig config) {
        RailwayData data = RailwayData.load(Path.of("config/railway-data.properties"));
        return new ServerContext(config, data, new EventLog(100), null, null, null, null);
    }
}
