package com.smartrail.nio;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.common.service.ServerContext;
import com.smartrail.coordination.RailwayServiceImpl;
import com.smartrail.coordination.TrainMessageRouter;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OWNER: Member 1 - real non-blocking server on an OS-chosen port, several clients, one selector thread. */
class NioTrainServerTest {

    private NioTrainServer server;
    private RailwayServiceImpl railway;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void multipleClientsConnectAndRegister() throws Exception {
        start();
        List<Socket> clients = new ArrayList<>();
        try {
            for (String trainId : List.of("TR01", "TR02", "TR03")) {
                Socket socket = connect();
                clients.add(socket);
                BufferedReader reader = reader(socket);
                send(socket, "REGISTER|" + trainId + "|ST01");
                String ok = readLine(reader);
                assertTrue(ok.startsWith("REGISTER_OK|" + trainId), ok);
                assertTrue(readLine(reader).startsWith("ASSIGN_ROUTE|" + trainId), trainId);
            }
            assertEquals(1, selectorThreads());
            assertEquals(TrainStatus.REGISTERED, railway.getTrain("TR01").orElseThrow().status());
            assertEquals(TrainStatus.REGISTERED, railway.getTrain("TR03").orElseThrow().status());
        } finally {
            for (Socket socket : clients) {
                socket.close();
            }
        }
    }

    @Test
    void positionIsAcknowledged() throws Exception {
        start();
        try (Socket socket = connect()) {
            BufferedReader reader = reader(socket);
            send(socket, "REGISTER|TR01|ST01");
            readLine(reader);
            readLine(reader);
            send(socket, "POSITION|TR01|SEC_A|MOVING");
            assertEquals("ACK|TR01|POSITION", readLine(reader));
        }
    }

    @Test
    void disconnectIsDetected() throws Exception {
        start();
        Socket socket = connect();
        BufferedReader reader = reader(socket);
        send(socket, "REGISTER|TR02|ST03");
        readLine(reader);
        readLine(reader);
        socket.close();
        long deadline = System.currentTimeMillis() + 2000;
        TrainStatus status = TrainStatus.REGISTERED;
        while (System.currentTimeMillis() < deadline) {
            status = railway.getTrain("TR02").orElseThrow().status();
            if (status == TrainStatus.INACTIVE) {
                break;
            }
            Thread.sleep(20);
        }
        assertEquals(TrainStatus.INACTIVE, status);
    }

    private void start() throws Exception {
        var config = TestConfig.withOverrides(ConfigKeys.NIO_TCP_PORT, "0");
        var data = RailwayData.load(Path.of("config/railway-data.properties"));
        var log = new EventLog(100);
        railway = new RailwayServiceImpl(data, (sectionId, reason) -> { }, log);
        HeartbeatTracker heartbeat = new HeartbeatTracker() {
            @Override
            public void recordHeartbeat(String trainId) {
            }

            @Override
            public boolean isAlive(String trainId) {
                return true;
            }
        };
        var router = new TrainMessageRouter(railway, heartbeat, log);
        var ctx = new ServerContext(config, data, log, railway, router, (sectionId, reason) -> { }, heartbeat);
        server = new NioTrainServer(ctx);
        server.start();
    }

    private Socket connect() throws Exception {
        Socket socket = new Socket();
        socket.connect(new java.net.InetSocketAddress("127.0.0.1", server.boundPort()), 2000);
        socket.setSoTimeout(2000);
        return socket;
    }

    private static void send(Socket socket, String line) throws Exception {
        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }

    private static BufferedReader reader(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static String readLine(BufferedReader reader) throws Exception {
        String line = reader.readLine();
        assertTrue(line != null && !line.isBlank(), "expected a protocol line");
        return line;
    }

    private static int selectorThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("nio-selector".equals(thread.getName()) && thread.isAlive()) {
                count++;
            }
        }
        return count;
    }
}
