package com.smartrail.rmi;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.service.ServerContext;
import com.smartrail.testsupport.FakeRailwayService;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER: Member 5 - tests for RMI admin.
 * Uses real RMI registry and stubs over loopback with FakeRailwayService.
 */
class RailwayAdminServiceTest {

    private int registryPort;
    private NetworkConfig config;
    private FakeRailwayService fake;
    private EventLog log;
    private RmiAdminServer server;
    private RailwayAdminRemote remote;

    @BeforeEach
    void setUp() throws Exception {
        registryPort = findFreePort();
        config = TestConfig.withOverrides(
                ConfigKeys.RMI_REGISTRY_PORT, String.valueOf(registryPort),
                ConfigKeys.RMI_SERVICE_PORT, "0");
        RailwayData data = RailwayData.load(Path.of("config/railway-data.properties"));
        fake = new FakeRailwayService(data);
        log = new EventLog(100);
        ServerContext ctx = new ServerContext(config, data, log, fake, null, null, null);

        server = new RmiAdminServer(ctx);
        server.start();

        RmiAdminClient client = new RmiAdminClient(config);
        remote = client.connect();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void remoteGetActiveTrains() throws Exception {
        List<TrainInfo> trains = remote.getActiveTrains();
        assertNotNull(trains);
        assertEquals(fake.getTrains().size(), trains.size());
        TrainInfo t1 = remote.getTrainInfo("TR01");
        assertNotNull(t1);
        assertEquals("TR01", t1.trainId());
    }

    @Test
    void remoteHoldReachesRailwayService() throws Exception {
        remote.requestSimulatedHold("TR01");
        assertTrue(fake.calls().contains("holdTrain:TR01"),
                "FakeRailwayService should have recorded holdTrain:TR01");

        remote.requestResume("TR01");
        assertTrue(fake.calls().contains("resumeTrain:TR01"),
                "FakeRailwayService should have recorded resumeTrain:TR01");

        remote.requestEmergencyStop("TR01");
        assertTrue(fake.calls().contains("emergencyStop:TR01"),
                "FakeRailwayService should have recorded emergencyStop:TR01");
    }

    @Test
    void unknownTrainGivesRemoteException() {
        RemoteException ex = assertThrows(RemoteException.class, () -> remote.getTrainInfo("UNKNOWN_TRAIN"));
        assertTrue(ex.getMessage().contains("Unknown train: UNKNOWN_TRAIN"));

        assertThrows(RemoteException.class, () -> remote.requestSimulatedHold("UNKNOWN_TRAIN"));
    }

    @Test
    void remoteTrackStatusAndObstruction() throws Exception {
        List<TrackInfo> tracks = remote.getAllTracks();
        assertNotNull(tracks);
        assertFalse(tracks.isEmpty());

        TrackInfo sectionA = remote.getTrackStatus("SEC_A");
        assertNotNull(sectionA);
        assertEquals("SEC_A", sectionA.sectionId());

        remote.reportObstruction("SEC_A", "DEBRIS");
        assertTrue(fake.calls().contains("reportObstruction:SEC_A:DEBRIS"));

        assertThrows(RemoteException.class, () -> remote.getTrackStatus("SEC_UNKNOWN"));
        assertThrows(RemoteException.class, () -> remote.reportObstruction("SEC_UNKNOWN", "FAIL"));
    }

    @Test
    void remoteEventLogReturnsEntries() throws Exception {
        log.log("TEST", "Sample event log entry");
        List<EventLog.Entry> entries = remote.getEventLog();
        assertNotNull(entries);
        assertFalse(entries.isEmpty());
        boolean hasTestEntry = entries.stream().anyMatch(e -> e.message().contains("Sample event log entry"));
        assertTrue(hasTestEntry, "Event log should contain the logged message");
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
