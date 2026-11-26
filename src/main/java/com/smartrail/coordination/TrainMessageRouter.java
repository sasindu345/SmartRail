package com.smartrail.coordination;

import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.ReservationResult;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.protocol.ErrorCode;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.protocol.ProtocolException;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.common.service.MessageHandler;
import com.smartrail.common.service.RailwayService;
import com.smartrail.common.service.TrainConnection;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OWNER: Member 1 - CONCEPT: application-layer protocol handling (request/response over TCP).
 *
 * <p>The one {@link MessageHandler} used by both the NIO server and the TLS server. The first
 * message on a connection must be {@code REGISTER}. Later messages must use the train id registered
 * on that same connection.
 */
public final class TrainMessageRouter implements MessageHandler {

    private static final Set<MessageType> FROM_TRAIN = EnumSet.of(
            MessageType.REGISTER,
            MessageType.POSITION,
            MessageType.REQUEST_SECTION,
            MessageType.RELEASE_SECTION,
            MessageType.HEARTBEAT);

    private final RailwayService railway;
    private final HeartbeatTracker heartbeat;
    private final EventLog log;
    private final Map<TrainConnection, String> registered = new ConcurrentHashMap<>();

    public TrainMessageRouter(RailwayService railway, HeartbeatTracker heartbeat, EventLog log) {
        this.railway = railway;
        this.heartbeat = heartbeat;
        this.log = log;
    }

    @Override
    public void onMessage(TrainConnection connection, Message message) {
        if (!FROM_TRAIN.contains(message.type())) {
            reply(connection, Message.error(ErrorCode.ILLEGAL_STATE, "trains cannot send " + message.type()));
            return;
        }
        String registeredId = registered.get(connection);
        if (message.type() != MessageType.REGISTER && registeredId == null) {
            reply(connection, Message.error(ErrorCode.NOT_REGISTERED, "send REGISTER first"));
            return;
        }
        if (registeredId != null && !registeredId.equals(message.field(0))) {
            reply(connection, Message.error(ErrorCode.IDENTITY_MISMATCH,
                    "connection is " + registeredId + " but message says " + message.field(0)));
            return;
        }
        switch (message.type()) {
            case REGISTER -> onRegister(connection, message);
            case POSITION -> onPosition(connection, message);
            case REQUEST_SECTION -> onRequestSection(connection, message);
            case RELEASE_SECTION -> onRelease(connection, message);
            case HEARTBEAT -> heartbeat.recordHeartbeat(message.field(0));
            default -> reply(connection, Message.error(ErrorCode.ILLEGAL_STATE, message.type().name()));
        }
    }

    @Override
    public void onProtocolError(TrainConnection connection, ProtocolException error) {
        log.log("NIO", "Protocol error from " + connection.remoteAddress() + ": " + error.getMessage());
        reply(connection, error.toErrorMessage());
        if (error.code() == ErrorCode.MESSAGE_TOO_LARGE) {
            connection.close();
        }
    }

    @Override
    public void onDisconnect(TrainConnection connection) {
        String trainId = registered.remove(connection);
        if (trainId != null) {
            railway.onTrainDisconnected(trainId);
        }
    }

    private void onRegister(TrainConnection connection, Message message) {
        if (registered.containsKey(connection)) {
            reply(connection, Message.error(ErrorCode.ILLEGAL_STATE, "already registered"));
            return;
        }
        String trainId = message.field(0);
        String stationId = message.field(1);
        if (!railway.registerTrain(trainId, stationId, connection)) {
            ErrorCode code = railway.getTrain(trainId).isPresent() ? ErrorCode.ILLEGAL_STATE : ErrorCode.UNKNOWN_TRAIN;
            String detail = code == ErrorCode.UNKNOWN_TRAIN ? trainId : trainId + " already connected";
            reply(connection, Message.error(code, detail));
            return;
        }
        registered.put(connection, trainId);
        reply(connection, Message.of(MessageType.REGISTER_OK, trainId));
        if (railway instanceof RailwayServiceImpl impl) {
            impl.routeAssignment(trainId).ifPresent(connection::send);
        }
    }

    private void onPosition(TrainConnection connection, Message message) {
        String trainId = message.field(0);
        TrainStatus status;
        try {
            status = TrainStatus.valueOf(message.field(2));
        } catch (IllegalArgumentException e) {
            reply(connection, Message.error(ErrorCode.INVALID_MESSAGE, "unknown status " + message.field(2)));
            return;
        }
        railway.updatePosition(trainId, message.field(1), status);
        reply(connection, Message.of(MessageType.ACK, trainId, MessageType.POSITION.name()));
    }

    private void onRequestSection(TrainConnection connection, Message message) {
        ReservationResult result = railway.requestSection(message.field(0), message.field(1));
        if (result.granted()) {
            reply(connection, Message.of(MessageType.SECTION_GRANTED, result.trainId(), result.sectionId()));
        } else {
            reply(connection, Message.of(MessageType.SECTION_DENIED, result.trainId(), result.sectionId(), result.reason()));
        }
    }

    private void onRelease(TrainConnection connection, Message message) {
        String trainId = message.field(0);
        String sectionId = message.field(1);
        if (railway.releaseSection(trainId, sectionId)) {
            reply(connection, Message.of(MessageType.ACK, trainId, MessageType.RELEASE_SECTION.name()));
        } else {
            reply(connection, Message.error(ErrorCode.ILLEGAL_STATE, trainId + " does not hold " + sectionId));
        }
    }

    private void reply(TrainConnection connection, Message message) {
        connection.send(message);
    }
}
