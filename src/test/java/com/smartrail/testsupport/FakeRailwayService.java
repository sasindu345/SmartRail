package com.smartrail.testsupport;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.ReservationResult;
import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.service.RailwayService;
import com.smartrail.common.service.TrainConnection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simple, synchronized in-memory RailwayService for TESTS ONLY. Lets Members 3, 4 and 5 test their modules
 * before Member 1's RailwayServiceImpl is finished. Not the real implementation - do not copy it into main.
 */
public final class FakeRailwayService implements RailwayService {

    private final RailwayData data;
    private final Map<String, TrainInfo> trains = new LinkedHashMap<>();
    private final Map<String, TrackInfo> tracks = new LinkedHashMap<>();
    private final Map<String, TrainConnection> connections = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();

    public FakeRailwayService(RailwayData data) {
        this.data = data;
        for (TrainDefinition t : data.trains()) {
            trains.put(t.trainId(), new TrainInfo(t.trainId(), t.name(), TrainStatus.INACTIVE, t.startStationId(),
                    t.routeId(), null, null, 0L));
        }
        data.sections().forEach(s -> tracks.put(s.sectionId(), s));
    }

    /** Loads config/railway-data.properties from the project root (Maven runs tests there). */
    public static FakeRailwayService withDefaultData() {
        return new FakeRailwayService(RailwayData.load(Path.of("config/railway-data.properties")));
    }

    /** Names of command methods called, e.g. "holdTrain:TR01" - handy for RMI/heartbeat assertions. */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public synchronized List<TrainInfo> getTrains() {
        return List.copyOf(trains.values());
    }

    @Override
    public synchronized Optional<TrainInfo> getTrain(String trainId) {
        return Optional.ofNullable(trains.get(trainId));
    }

    @Override
    public synchronized List<TrackInfo> getTracks() {
        return List.copyOf(tracks.values());
    }

    @Override
    public synchronized Optional<TrackInfo> getTrack(String sectionId) {
        return Optional.ofNullable(tracks.get(sectionId));
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
        calls.add("registerTrain:" + trainId);
        TrainInfo t = trains.get(trainId);
        if (t == null || connections.containsKey(trainId)) {
            return false;
        }
        connections.put(trainId, connection);
        trains.put(trainId, new TrainInfo(trainId, t.name(), TrainStatus.REGISTERED, stationId, t.routeId(),
                connection.transport(), connection.remoteAddress(), System.currentTimeMillis()));
        return true;
    }

    @Override
    public synchronized void updatePosition(String trainId, String location, TrainStatus status) {
        calls.add("updatePosition:" + trainId);
        setStatus(trainId, status, location);
    }

    @Override
    public synchronized ReservationResult requestSection(String trainId, String sectionId) {
        calls.add("requestSection:" + trainId + ":" + sectionId);
        TrackInfo s = tracks.get(sectionId);
        if (s == null) {
            return ReservationResult.denied(trainId, sectionId, null, "UNKNOWN_SECTION");
        }
        if (s.state() != SectionState.FREE && !trainId.equals(s.holderTrainId())) {
            return ReservationResult.denied(trainId, sectionId, s.holderTrainId(), "RESERVED_BY_" + s.holderTrainId());
        }
        tracks.put(sectionId, new TrackInfo(sectionId, s.fromStationId(), s.toStationId(), SectionState.RESERVED, trainId));
        return ReservationResult.granted(trainId, sectionId);
    }

    @Override
    public synchronized boolean releaseSection(String trainId, String sectionId) {
        calls.add("releaseSection:" + trainId + ":" + sectionId);
        TrackInfo s = tracks.get(sectionId);
        if (s == null || !trainId.equals(s.holderTrainId())) {
            return false;
        }
        tracks.put(sectionId, new TrackInfo(sectionId, s.fromStationId(), s.toStationId(), SectionState.FREE, null));
        return true;
    }

    @Override
    public synchronized void onTrainDisconnected(String trainId) {
        calls.add("onTrainDisconnected:" + trainId);
        connections.remove(trainId);
        setStatus(trainId, TrainStatus.INACTIVE, null);
    }

    @Override
    public synchronized void markInactive(String trainId) {
        calls.add("markInactive:" + trainId);
        setStatus(trainId, TrainStatus.INACTIVE, null);
    }

    @Override
    public synchronized boolean holdTrain(String trainId) {
        return command(trainId, "holdTrain", MessageType.HOLD, TrainStatus.HELD);
    }

    @Override
    public synchronized boolean resumeTrain(String trainId) {
        return command(trainId, "resumeTrain", MessageType.RESUME, TrainStatus.MOVING);
    }

    @Override
    public synchronized boolean emergencyStop(String trainId) {
        return command(trainId, "emergencyStop", MessageType.EMERGENCY_STOP, TrainStatus.EMERGENCY_STOPPED);
    }

    @Override
    public synchronized void reportObstruction(String sectionId, String reason) {
        calls.add("reportObstruction:" + sectionId + ":" + reason);
    }

    private boolean command(String trainId, String name, MessageType type, TrainStatus status) {
        calls.add(name + ":" + trainId);
        TrainConnection c = connections.get(trainId);
        if (c == null) {
            return false;
        }
        c.send(Message.of(type, trainId));
        setStatus(trainId, status, null);
        return true;
    }

    private void setStatus(String trainId, TrainStatus status, String location) {
        TrainInfo t = trains.get(trainId);
        if (t != null) {
            trains.put(trainId, new TrainInfo(trainId, t.name(), status, location == null ? t.location() : location,
                    t.routeId(), t.transport(), t.remoteAddress(), System.currentTimeMillis()));
        }
    }
}
