package com.smartrail.urlconnection;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.ProtocolException;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.common.service.MessageHandler;
import com.smartrail.common.service.ServerContext;
import com.smartrail.common.service.TrainConnection;
import com.smartrail.testsupport.FakeRailwayService;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OWNER: Member 4 - ResourceHttpServer + ResourceClient over a real HttpServer on port 0.
 * FakeRailwayService is used so the test never needs the NIO server running.
 */
class ResourceHttpServerTest {

    private ResourceHttpServer server;
    private ResourceClient client;

    @BeforeEach
    void start() throws Exception {
        var config = TestConfig.withOverrides(ConfigKeys.HTTP_RESOURCE_PORT, "0");
        var data = RailwayData.load(Path.of("config/railway-data.properties"));
        var log = new EventLog(50);
        var railway = new FakeRailwayService(data);
        MessageHandler noOp = new MessageHandler() {
            public void onMessage(TrainConnection c, Message m) {}
            public void onProtocolError(TrainConnection c, ProtocolException e) {}
            public void onDisconnect(TrainConnection c) {}
        };
        HeartbeatTracker noOpHb = new HeartbeatTracker() {
            public void recordHeartbeat(String id) {}
            public boolean isAlive(String id) { return true; }
        };
        var ctx = new ServerContext(config, data, log, railway, noOp, (s, r) -> {}, noOpHb);
        server = new ResourceHttpServer(ctx);
        server.start();

        client = new ResourceClient(TestConfig.withOverrides(
                ConfigKeys.HTTP_RESOURCE_PORT, String.valueOf(server.boundPort()),
                ConfigKeys.CLIENT_SERVER_HOST, "localhost"));
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop();
    }

    @Test
    void fetchesSchedulesAsText() throws Exception {
        String body = client.fetchText("schedules");
        assertNotNull(body);
        assertFalse(body.isBlank(), "schedules body should not be empty");
    }

    @Test
    void unknownResourceGives404() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                URI.create("http://localhost:" + server.boundPort() + "/api/bogus").toURL().openConnection();
        assertEquals(404, conn.getResponseCode());
    }

    @Test
    void contentTypeHeaderIsSmartRailMime() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                URI.create("http://localhost:" + server.boundPort() + "/api/trains").toURL().openConnection();
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("smartrail-train"),
                "unexpected content-type: " + conn.getContentType());
    }

    @Test
    void nonGetMethodGives405() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                URI.create("http://localhost:" + server.boundPort() + "/api/trains").toURL().openConnection();
        conn.setRequestMethod("POST");
        assertEquals(405, conn.getResponseCode());
    }
}
