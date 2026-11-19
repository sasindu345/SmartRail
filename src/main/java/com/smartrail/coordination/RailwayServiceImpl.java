package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.ReservationResult;
import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.service.AlertService;
import com.smartrail.common.service.RailwayService;
import com.smartrail.common.service.TrainConnection;

import java.util.List;
import java.util.Optional;

/**
 * OWNER: Member 1 - CONCEPT: authoritative central-server state (client-server model).
 *
 * <p>Implements the shared {@link RailwayService} using {@link TrainRegistry},
 * {@link TrackReservationService} and {@link RouteService}. Every method must be thread-safe:
 * it is called from the NIO selector thread, TLS client threads, RMI threads and the heartbeat timer.
 * <ul>
 *   <li>TODO(M1) queries return immutable snapshots</li>
 *   <li>TODO(M1) holdTrain/resumeTrain/emergencyStop: update status AND push HOLD/RESUME/EMERGENCY_STOP
 *       through the train's TrainConnection</li>
 *   <li>TODO(M1) reportObstruction: log + alerts.publishAlert (Member 3)</li>
 *   <li>TODO(M1) markInactive / onTrainDisconnected: status INACTIVE, release reservations</li>
 * </ul>
 */
public final class RailwayServiceImpl implements RailwayService {

    private final RailwayData data;
    private final AlertService alerts;
    private final EventLog log;

    public RailwayServiceImpl(RailwayData data, AlertService alerts, EventLog log) {
        this.data = data;
        this.alerts = alerts;
        this.log = log;
    }

    @Override
    public List<TrainInfo> getTrains() {
        throw todo("getTrains");
    }

    @Override
    public Optional<TrainInfo> getTrain(String trainId) {
        throw todo("getTrain");
    }

    @Override
    public List<TrackInfo> getTracks() {
        throw todo("getTracks");
    }

    @Override
    public Optional<TrackInfo> getTrack(String sectionId) {
        throw todo("getTrack");
    }

    @Override
    public List<StationInfo> getStations() {
        throw todo("getStations");
    }

    @Override
    public List<ScheduleInfo> getSchedules() {
        throw todo("getSchedules");
    }

    @Override
    public boolean registerTrain(String trainId, String stationId, TrainConnection connection) {
        throw todo("registerTrain");
    }

    @Override
    public void updatePosition(String trainId, String location, TrainStatus status) {
        throw todo("updatePosition");
    }

    @Override
    public ReservationResult requestSection(String trainId, String sectionId) {
        throw todo("requestSection");
    }

    @Override
    public boolean releaseSection(String trainId, String sectionId) {
        throw todo("releaseSection");
    }

    @Override
    public void onTrainDisconnected(String trainId) {
        throw todo("onTrainDisconnected");
    }

    @Override
    public void markInactive(String trainId) {
        throw todo("markInactive");
    }

    @Override
    public boolean holdTrain(String trainId) {
        throw todo("holdTrain");
    }

    @Override
    public boolean resumeTrain(String trainId) {
        throw todo("resumeTrain");
    }

    @Override
    public boolean emergencyStop(String trainId) {
        throw todo("emergencyStop");
    }

    @Override
    public void reportObstruction(String sectionId, String reason) {
        throw todo("reportObstruction");
    }

    private static UnsupportedOperationException todo(String method) {
        return new UnsupportedOperationException("TODO(M1): RailwayServiceImpl." + method);
    }
}
