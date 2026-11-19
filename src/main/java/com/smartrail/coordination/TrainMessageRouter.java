package com.smartrail.coordination;

import com.smartrail.common.log.EventLog;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.ProtocolException;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.common.service.MessageHandler;
import com.smartrail.common.service.RailwayService;
import com.smartrail.common.service.TrainConnection;

/**
 * OWNER: Member 1 - CONCEPT: application-layer protocol handling (request/response over TCP).
 *
 * <p>The ONE message handler used by both the NIO server (M1) and the TLS server (M4).
 * <ul>
 *   <li>TODO(M1) REGISTER -> railway.registerTrain -> REGISTER_OK + ASSIGN_ROUTE (or ERROR)</li>
 *   <li>TODO(M1) POSITION -> railway.updatePosition -> ACK|trainId|POSITION</li>
 *   <li>TODO(M1) REQUEST_SECTION -> railway.requestSection -> SECTION_GRANTED / SECTION_DENIED</li>
 *   <li>TODO(M1) RELEASE_SECTION -> railway.releaseSection -> ACK</li>
 *   <li>TODO(M1) HEARTBEAT -> heartbeat.recordHeartbeat (Member 3)</li>
 *   <li>TODO(M1) validate: train registered on THIS connection, trainId matches (IDENTITY_MISMATCH),
 *       legal type for train->server direction; reply ERROR|code|detail</li>
 *   <li>TODO(M1) onDisconnect -> railway.onTrainDisconnected</li>
 * </ul>
 */
public final class TrainMessageRouter implements MessageHandler {

    private final RailwayService railway;
    private final HeartbeatTracker heartbeat;
    private final EventLog log;

    public TrainMessageRouter(RailwayService railway, HeartbeatTracker heartbeat, EventLog log) {
        this.railway = railway;
        this.heartbeat = heartbeat;
        this.log = log;
    }

    @Override
    public void onMessage(TrainConnection connection, Message message) {
        throw new UnsupportedOperationException("TODO(M1): route " + message.type());
    }

    @Override
    public void onProtocolError(TrainConnection connection, ProtocolException error) {
        throw new UnsupportedOperationException("TODO(M1): reply " + error.toErrorMessage());
    }

    @Override
    public void onDisconnect(TrainConnection connection) {
        throw new UnsupportedOperationException("TODO(M1): cleanup train on " + connection.remoteAddress());
    }
}
