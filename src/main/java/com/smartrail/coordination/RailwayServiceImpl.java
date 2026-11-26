package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.ReservationResult;
import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.service.AlertService;
import com.smartrail.common.service.RailwayService;
import com.smartrail.common.service.TrainConnection;

import java.util.List;
import java.util.Optional;

/**
 * OWNER: Member 1 - CONCEPT: authoritative central-server state (client-server model).
 *
 * <p>Every method is synchronized: the NIO selector thread, TLS client threads, RMI threads and the
 * heartbeat timer all call this object. Section ownership changes only inside
 * {@link TrackReservationService#reserve}.
 */
public final class RailwayServiceImpl implements RailwayService {

    private final RailwayData data;
    private final AlertService alerts;
    private final EventLog log;
    private final TrainRegistry registry = new TrainRegistry();
    private final TrackReservationService tracks;
    private final RouteService routes;

    public RailwayServiceImpl(RailwayData data, AlertService alerts, EventLog log) {
        this.data = data;
        this.alerts = alerts;
        this.log = log;
        this.tracks = new TrackReservationService(data);
        this.routes = new RouteService(data);
        for (TrainDefinition train : data.trains()) {
            registry.ensureKnown(train.trainId(), train.name(), train.routeId(), train.startStationId());
        }
    }

    /**
     * Clears every reservation and parks each train at the start station from railway data.
     * Open sockets are closed so a running simulator cannot immediately take a section again.
     */
    public synchronized void resetToStart() {
        tracks.releaseEvery();
        for (TrainDefinition train : data.trains()) {
            registry.restoreStart(train.trainId(), train.startStationId());
        }
        log.log("NIO", "RESET trains parked at start stations, all sections FREE");
    }

    /** Used by {@link TrainMessageRouter} after a successful REGISTER. */
    public Optional<Message> routeAssignment(String trainId) {
        return routes.assignment(trainId);
    }

    @Override
    public synchronized List<TrainInfo> getTrains() {
        return registry.snapshots();
    }

    @Override
    public synchronized Optional<TrainInfo> getTrain(String trainId) {
        return registry.snapshot(trainId);
    }

    @Override
    public synchronized List<TrackInfo> getTracks() {
        return tracks.snapshots();
    }

    @Override
    public synchronized Optional<TrackInfo> getTrack(String sectionId) {
        return tracks.snapshot(sectionId);
    }

    @Override
    public List<StationInfo> getStations() {
        return data.stations();
    }

    @Override
    public List<ScheduleInfo> getSchedules() {
        return data.schedules();
    }

    @Override
    public synchronized boolean registerTrain(String trainId, String stationId, TrainConnection connection) {
        Optional<TrainDefinition> defined = data.train(trainId);
        if (defined.isEmpty()) {
            log.log("NIO", "REGISTER rejected " + trainId + " (unknown train)");
            return false;
        }
        TrainDefinition train = defined.get();
        boolean accepted = registry.register(train.trainId(), train.name(), train.routeId(), stationId, connection);
        if (accepted) {
            log.log("NIO", "REGISTER " + trainId + " at " + stationId + " from " + connection.remoteAddress());
        } else {
            log.log("NIO", "REGISTER rejected " + trainId + " (already connected)");
        }
        return accepted;
    }

    @Override
    public synchronized void updatePosition(String trainId, String location, TrainStatus status) {
        registry.update(trainId, location, status);
        tracks.markOccupied(trainId, location);
        log.log("NIO", "POSITION " + trainId + " " + location + " " + status);
    }

    @Override
    public synchronized ReservationResult requestSection(String trainId, String sectionId) {
        if (data.section(sectionId).isEmpty()) {
            return ReservationResult.denied(trainId, sectionId, null, "UNKNOWN_SECTION");
        }
        if (!routes.isOnRoute(trainId, sectionId)) {
            log.log("NIO", "SECTION_DENIED " + trainId + " " + sectionId + " NOT_ON_ROUTE");
            return ReservationResult.denied(trainId, sectionId, null, "NOT_ON_ROUTE");
        }
        ReservationResult result = tracks.reserve(trainId, sectionId);
        if (result.granted()) {
            log.log("NIO", "SECTION_GRANTED " + trainId + " " + sectionId);
        } else {
            log.log("NIO", "SECTION_DENIED " + trainId + " " + sectionId + " " + result.reason());
        }
        return result;
    }

    @Override
    public synchronized boolean releaseSection(String trainId, String sectionId) {
        boolean released = tracks.release(trainId, sectionId);
        log.log("NIO", (released ? "RELEASED " : "RELEASE rejected ") + trainId + " " + sectionId);
        return released;
    }

    @Override
    public synchronized void onTrainDisconnected(String trainId) {
        tracks.releaseAll(trainId);
        registry.markInactive(trainId);
        log.log("NIO", "INACTIVE " + trainId + " (disconnected)");
    }

    @Override
    public synchronized void markInactive(String trainId) {
        tracks.releaseAll(trainId);
        registry.markInactive(trainId);
        log.log("NIO", "INACTIVE " + trainId + " (heartbeat timeout)");
    }

    @Override
    public synchronized boolean holdTrain(String trainId) {
        return command(trainId, MessageType.HOLD, TrainStatus.HELD);
    }

    @Override
    public synchronized boolean resumeTrain(String trainId) {
        return command(trainId, MessageType.RESUME, TrainStatus.MOVING);
    }

    @Override
    public synchronized boolean emergencyStop(String trainId) {
        return command(trainId, MessageType.EMERGENCY_STOP, TrainStatus.EMERGENCY_STOPPED);
    }

    @Override
    public synchronized void reportObstruction(String sectionId, String reason) {
        log.log("NIO", "OBSTRUCTION " + sectionId + " " + reason);
        alerts.publishAlert(sectionId, reason);
    }

    private boolean command(String trainId, MessageType type, TrainStatus status) {
        Optional<TrainConnection> connection = registry.connection(trainId);
        if (connection.isEmpty()) {
            log.log("NIO", type + " " + trainId + " failed (not connected)");
            return false;
        }
        registry.update(trainId, null, status);
        connection.get().send(Message.of(type, trainId));
        log.log("NIO", type + " " + trainId);
        return true;
    }
}
