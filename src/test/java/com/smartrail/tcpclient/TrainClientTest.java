package com.smartrail.tcpclient;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.common.service.ServerContext;
import com.smartrail.coordination.RailwayServiceImpl;
import com.smartrail.coordination.TrainMessageRouter;
import com.smartrail.nio.NioTrainServer;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OWNER: Member 2 - TCP connect, line decode, reconnect, and the route simulator. */
class TrainClientTest {

    private final List<TrainClient> clients = new CopyOnWriteArrayList<>();
    private final List<TrainSimulator> simulators = new CopyOnWriteArrayList<>();
    private NioTrainServer server;

    @AfterEach
    void cleanup() {
        for (TrainSimulator simulator : simulators) {
            simulator.stop();
        }
        for (TrainClient client : clients) {
            client.disconnect();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void connectsAndSendsRegister() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            TrainClient client = client(fastConfig(), train("TR01", "ST01"));
            Thread connecting = new Thread(() -> client.connect("127.0.0.1", server.getLocalPort()));
            connecting.start();
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(2000);
                assertEquals("REGISTER|TR01|ST01", read(socket));
            }
            connecting.join(2000);
        }
    }

    @Test
    void receivesAndDecodesServerLines() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            TrainClient client = client(fastConfig(), train("TR01", "ST01"));
            List<Message> received = new CopyOnWriteArrayList<>();
            client.addListener(new TrainClientListener() {
                @Override
                public void onMessage(Message message) {
                    received.add(message);
                }
            });
            Thread connecting = new Thread(() -> client.connect("127.0.0.1", server.getLocalPort()));
            connecting.start();
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(2000);
                assertEquals("REGISTER|TR01|ST01", read(socket));
                OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                writer.write("REGISTER_OK|TR01\n");
                writer.write("ASSIGN_ROUTE|TR01|R01|SEC_A,SEC_B\n");
                writer.flush();
                assertTrue(await(() -> received.size() >= 2), received.toString());
            }
            assertEquals("REGISTER_OK", received.get(0).type().name());
            assertEquals("ASSIGN_ROUTE", received.get(1).type().name());
            assertEquals("SEC_A,SEC_B", received.get(1).field(2));
            connecting.join(2000);
        }
    }

    @Test
    void wrongPortReportsError() throws Exception {
        int closedPort;
        try (ServerSocket server = new ServerSocket(0)) {
            closedPort = server.getLocalPort();
        }
        TrainClient client = client(fastConfig(), train("TR01", "ST01"));
        AtomicReference<String> error = new AtomicReference<>();
        client.addListener(new TrainClientListener() {
            @Override
            public void onError(String message) {
                error.set(message);
            }
        });
        client.connect("127.0.0.1", closedPort);
        assertFalse(client.isConnected());
        assertTrue(error.get() != null && error.get().contains("Could not connect"), error.get());
        assertFalse(error.get().contains("Exception"), error.get());
    }

    @Test
    void reconnectsAfterServerRestart() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            TrainClient client = client(fastConfig(), train("TR01", "ST01"));
            CountDownLatch registered = new CountDownLatch(2);
            Thread acceptor = new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (Socket socket = server.accept()) {
                            socket.setSoTimeout(2000);
                            if ("REGISTER|TR01|ST01".equals(read(socket))) {
                                registered.countDown();
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // The client disconnect closes the listening socket.
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            client.connect("127.0.0.1", server.getLocalPort());
            assertTrue(registered.await(5, TimeUnit.SECONDS), "client did not register again after the drop");
        }
    }

    @Test
    void simulatorMovesTrainAndWaitsWhenSectionIsHeld() throws Exception {
        NetworkConfig config = TestConfig.withOverrides(
                ConfigKeys.NIO_TCP_PORT, "0",
                ConfigKeys.CLIENT_SIMULATOR_STEP_MS, "400",
                ConfigKeys.CLIENT_CONNECT_TIMEOUT_MS, "1000",
                ConfigKeys.CLIENT_RECONNECT_ATTEMPTS, "2",
                ConfigKeys.CLIENT_RECONNECT_DELAY_MS, "50");
        RailwayData data = RailwayData.load(Path.of("config/railway-data.properties"));
        EventLog log = new EventLog(100);
        RailwayServiceImpl railway = new RailwayServiceImpl(data, (sectionId, reason) -> { }, log);
        HeartbeatTracker heartbeat = new HeartbeatTracker() {
            @Override
            public void recordHeartbeat(String trainId) {
            }

            @Override
            public boolean isAlive(String trainId) {
                return true;
            }
        };
        TrainMessageRouter router = new TrainMessageRouter(railway, heartbeat, log);
        ServerContext context = new ServerContext(config, data, log, railway, router, (sectionId, reason) -> { }, heartbeat);
        server = new NioTrainServer(context);
        server.start();

        TrainClient leader = client(config, data.train("TR01").orElseThrow());
        TrainSimulator leaderSim = new TrainSimulator(config, leader);
        leader.addListener(leaderSim);
        simulators.add(leaderSim);
        leaderSim.start();
        leader.connect("127.0.0.1", server.boundPort());

        assertTrue(await(() -> "SEC_A".equals(location(railway, "TR01"))), "TR01 never reached SEC_A");
        assertEquals(TrainStatus.MOVING, railway.getTrain("TR01").orElseThrow().status());

        TrainClient follower = client(config, data.train("TR03").orElseThrow());
        TrainSimulator followerSim = new TrainSimulator(config, follower);
        follower.addListener(followerSim);
        simulators.add(followerSim);
        followerSim.start();
        follower.connect("127.0.0.1", server.boundPort());

        assertTrue(await(() -> log.snapshot().stream().anyMatch(entry ->
                entry.message().contains("SECTION_DENIED") && entry.message().contains("TR03"))),
                "TR03 was not denied while TR01 held SEC_A");
        assertFalse("SEC_A".equals(location(railway, "TR03")), "TR03 entered a section TR01 still holds");
    }

    private TrainClient client(NetworkConfig config, TrainDefinition train) {
        TrainClient client = new TrainClient(config, train, new PlainSocketFactory());
        clients.add(client);
        return client;
    }

    private static TrainDefinition train(String trainId, String stationId) {
        return new TrainDefinition(trainId, trainId, stationId, "R01");
    }

    private static NetworkConfig fastConfig() {
        return TestConfig.withOverrides(
                ConfigKeys.CLIENT_CONNECT_TIMEOUT_MS, "500",
                ConfigKeys.CLIENT_RECONNECT_ATTEMPTS, "3",
                ConfigKeys.CLIENT_RECONNECT_DELAY_MS, "100");
    }

    private static String read(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
    }

    private static String location(RailwayServiceImpl railway, String trainId) {
        return railway.getTrain(trainId).map(train -> train.location()).orElse("");
    }

    private static boolean await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(30);
        }
        return condition.getAsBoolean();
    }
}
