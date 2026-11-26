package com.smartrail.coordination;

import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.service.TrainConnection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OWNER: Member 1 - CONCEPT: server-side client registry (client-server model).
 *
 * <p>Thread-safe map of trainId to the train's current snapshot and the connection that may be
 * pushed HOLD / RESUME / EMERGENCY_STOP. Only Member 1's classes call this.
 */
public final class TrainRegistry {

    private final Map<String, TrainRecord> trains = new LinkedHashMap<>();

    public synchronized boolean register(String trainId, String name, String routeId, String stationId,
                                         TrainConnection connection) {
        TrainRecord existing = trains.get(trainId);
        if (existing != null && existing.connection != null && existing.connection.isOpen()
                && existing.connection != connection) {
            return false;
        }
        TrainRecord record = existing == null
                ? new TrainRecord(trainId, name, routeId)
                : existing;
        record.location = stationId;
        record.status = TrainStatus.REGISTERED;
        record.connection = connection;
        record.transport = connection.transport();
        record.remoteAddress = connection.remoteAddress();
        record.lastSeenMillis = System.currentTimeMillis();
        trains.put(trainId, record);
        return true;
    }

    public synchronized void ensureKnown(String trainId, String name, String routeId, String startStationId) {
        trains.computeIfAbsent(trainId, id -> {
            TrainRecord record = new TrainRecord(id, name, routeId);
            record.location = startStationId;
            record.status = TrainStatus.INACTIVE;
            return record;
        });
    }

    public synchronized Optional<TrainConnection> connection(String trainId) {
        TrainRecord record = trains.get(trainId);
        if (record == null || record.connection == null || !record.connection.isOpen()) {
            return Optional.empty();
        }
        return Optional.of(record.connection);
    }

    public synchronized void update(String trainId, String location, TrainStatus status) {
        TrainRecord record = trains.get(trainId);
        if (record == null) {
            return;
        }
        if (location != null) {
            record.location = location;
        }
        if (status != null) {
            record.status = status;
        }
        record.lastSeenMillis = System.currentTimeMillis();
    }

    /** Puts a train back at its configured start with no open connection. */
    public synchronized void restoreStart(String trainId, String startStationId) {
        TrainRecord record = trains.get(trainId);
        if (record == null) {
            return;
        }
        TrainConnection connection = record.connection;
        record.connection = null;
        record.remoteAddress = null;
        record.transport = null;
        record.location = startStationId;
        record.status = TrainStatus.INACTIVE;
        record.lastSeenMillis = System.currentTimeMillis();
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    public synchronized void markInactive(String trainId) {
        TrainRecord record = trains.get(trainId);
        if (record == null) {
            return;
        }
        record.status = TrainStatus.INACTIVE;
        record.connection = null;
        record.lastSeenMillis = System.currentTimeMillis();
    }

    public synchronized Optional<TrainInfo> snapshot(String trainId) {
        TrainRecord record = trains.get(trainId);
        return record == null ? Optional.empty() : Optional.of(record.snapshot());
    }

    public synchronized List<TrainInfo> snapshots() {
        List<TrainInfo> copy = new ArrayList<>();
        for (TrainRecord record : trains.values()) {
            copy.add(record.snapshot());
        }
        return List.copyOf(copy);
    }

    private static final class TrainRecord {
        private final String trainId;
        private final String name;
        private final String routeId;
        private String location;
        private TrainStatus status = TrainStatus.INACTIVE;
        private TrainConnection connection;
        private com.smartrail.common.model.Transport transport;
        private String remoteAddress;
        private long lastSeenMillis;

        private TrainRecord(String trainId, String name, String routeId) {
            this.trainId = trainId;
            this.name = name;
            this.routeId = routeId;
        }

        private TrainInfo snapshot() {
            return new TrainInfo(trainId, name, status, location, routeId, transport, remoteAddress, lastSeenMillis);
        }
    }
}
