package com.smartrail.common.service;

import com.smartrail.common.model.ReservationResult;
import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;

import java.util.List;
import java.util.Optional;

/**
 * The authoritative railway state on the central server. Implemented by Member 1
 * ({@code RailwayServiceImpl}). Every other server-side module (heartbeat, HTTP resources,
 * smartrail:// resources, RMI, UI) uses ONLY this interface - never Member 1's internals.
 * All methods must be thread-safe. SHARED CONTRACT (frozen).
 */
public interface RailwayService {

    // ---------- Queries (return immutable snapshots) ----------

    List<TrainInfo> getTrains();

    Optional<TrainInfo> getTrain(String trainId);

    List<TrackInfo> getTracks();

    Optional<TrackInfo> getTrack(String sectionId);

    List<StationInfo> getStations();

    List<ScheduleInfo> getSchedules();

    // ---------- Train lifecycle (called by the message router) ----------

    /** Registers a known train on a connection. Returns {@code false} if unknown or already connected. */
    boolean registerTrain(String trainId, String stationId, TrainConnection connection);

    void updatePosition(String trainId, String location, TrainStatus status);

    /** Atomic check-and-reserve: two concurrent requests for one FREE section can never both succeed. */
    ReservationResult requestSection(String trainId, String sectionId);

    /** Releases a section held by {@code trainId}. Returns {@code false} if it did not hold it. */
    boolean releaseSection(String trainId, String sectionId);

    void onTrainDisconnected(String trainId);

    /** Called by the heartbeat monitor (Member 3) after the timeout. */
    void markInactive(String trainId);

    // ---------- Simulated control commands (RMI admin / dashboard) ----------

    /** Sets HELD and pushes {@code HOLD|trainId} to the train. */
    boolean holdTrain(String trainId);

    /** Sets MOVING/WAITING and pushes {@code RESUME|trainId}. */
    boolean resumeTrain(String trainId);

    /** Sets EMERGENCY_STOPPED and pushes {@code EMERGENCY_STOP|trainId}. */
    boolean emergencyStop(String trainId);

    /** Simulated obstruction: logs it and publishes an alert through {@link AlertService}. */
    void reportObstruction(String sectionId, String reason);
}
