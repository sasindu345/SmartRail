package com.smartrail.protocolhandler;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.resource.ResourceTypes;
import com.smartrail.common.service.ServerContext;
import com.smartrail.testsupport.FakeRailwayService;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER: Member 5 - tests for the smartrail:// protocol handler.
 * Verifies URL creation via ServiceLoader registration, server resource resolution over TCP,
 * and error handling for missing resources.
 */
class SmartRailUrlTest {

    private int serverPort;
    private NetworkConfig config;
    private FakeRailwayService fake;
    private EventLog log;
    private SmartRailResourceServer server;

    @BeforeEach
    void setUp() throws Exception {
        serverPort = findFreePort();
        config = TestConfig.withOverrides(
                ConfigKeys.SMARTRAIL_RESOURCE_PORT, String.valueOf(serverPort),
                ConfigKeys.SMARTRAIL_RESOURCE_TIMEOUT_MS, "2000");
        RailwayData data = RailwayData.load(Path.of("config/railway-data.properties"));
        fake = new FakeRailwayService(data);
        log = new EventLog(100);
        ServerContext ctx = new ServerContext(config, data, log, fake, null, null, null);

        server = new SmartRailResourceServer(ctx);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void urlWithSchemeIsAccepted() throws Exception {
        URL url = new URI("smartrail://localhost:5100/trains").toURL();
        assertNotNull(url);
        assertEquals("smartrail", url.getProtocol());
        assertEquals("localhost", url.getHost());
        assertEquals(5100, url.getPort());
        assertEquals("/trains", url.getPath());
    }

    @Test
    void opensTrainResourceFromServer() throws Exception {
        URL url = new URI("smartrail://localhost:" + serverPort + "/trains/TR01").toURL();
        URLConnection conn = url.openConnection();
        assertNotNull(conn);
        assertEquals(ResourceTypes.MIME_TRAIN, conn.getContentType());

        String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("trainId=TR01"), "Body should contain trainId=TR01");
    }

    @Test
    void missingResourceThrows() throws Exception {
        URL url = new URI("smartrail://localhost:" + serverPort + "/trains/UNKNOWN_TRAIN").toURL();
        URLConnection conn = url.openConnection();
        assertThrows(IOException.class, conn::getInputStream);

        URL badUrl = new URI("smartrail://localhost:" + serverPort + "/invalid_collection").toURL();
        URLConnection badConn = badUrl.openConnection();
        assertThrows(IOException.class, badConn::getInputStream);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
