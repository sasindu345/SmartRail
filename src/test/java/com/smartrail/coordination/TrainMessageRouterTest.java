package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.ErrorCode;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.service.AlertService;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.testsupport.FakeTrainConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OWNER: Member 1 - router replies. Connections are fakes; the railway state is the real implementation. */
class TrainMessageRouterTest {

    private RailwayServiceImpl railway;
    private RecordingHeartbeat heartbeat;
    private TrainMessageRouter router;

    @BeforeEach
    void setUp() {
        AlertService alerts = (sectionId, reason) -> { };
        railway = new RailwayServiceImpl(RailwayData.load(Path.of("config/railway-data.properties")), alerts, new EventLog(100));
        heartbeat = new RecordingHeartbeat();
        router = new TrainMessageRouter(railway, heartbeat, new EventLog(100));
    }

    @Test
    void registerRepliesRegisterOkAndAssignRoute() {
        FakeTrainConnection connection = connection();
        router.onMessage(connection, Message.of(MessageType.REGISTER, "TR01", "ST01"));
        assertEquals(MessageType.REGISTER_OK, connection.sent().get(0).type());
        assertEquals("TR01", connection.sent().get(0).field(0));
        Message route = connection.sent().get(1);
        assertEquals(MessageType.ASSIGN_ROUTE, route.type());
        assertEquals("R01", route.field(1));
        assertEquals("SEC_A,SEC_B", route.field(2));
    }

    @Test
    void unregisteredTrainGetsError() {
        FakeTrainConnection connection = connection();
        router.onMessage(connection, Message.of(MessageType.POSITION, "TR01", "ST01", "MOVING"));
        assertEquals(ErrorCode.NOT_REGISTERED.name(), connection.sent().get(0).field(0));
    }

    @Test
    void identityMismatchIsRejected() {
        FakeTrainConnection connection = connection();
        router.onMessage(connection, Message.of(MessageType.REGISTER, "TR01", "ST01"));
        connection.sent().clear();
        router.onMessage(connection, Message.of(MessageType.HEARTBEAT, "TR02"));
        assertEquals(ErrorCode.IDENTITY_MISMATCH.name(), connection.sent().get(0).field(0));
        assertTrue(heartbeat.ids.isEmpty());
    }

    @Test
    void heartbeatIsForwardedToTracker() {
        FakeTrainConnection connection = connection();
        router.onMessage(connection, Message.of(MessageType.REGISTER, "TR01", "ST01"));
        router.onMessage(connection, Message.of(MessageType.HEARTBEAT, "TR01"));
        assertEquals(List.of("TR01"), heartbeat.ids);
    }

    private static FakeTrainConnection connection() {
        return new FakeTrainConnection("127.0.0.1:40000", Transport.PLAIN);
    }

    private static final class RecordingHeartbeat implements HeartbeatTracker {
        private final List<String> ids = new CopyOnWriteArrayList<>();

        @Override
        public void recordHeartbeat(String trainId) {
            ids.add(trainId);
        }

        @Override
        public boolean isAlive(String trainId) {
            return ids.contains(trainId);
        }
    }
}
