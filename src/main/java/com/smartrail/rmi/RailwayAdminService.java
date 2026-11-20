package com.smartrail.rmi;

import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.service.RailwayService;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.util.List;
import java.util.Objects;

/**
 * OWNER: Member 5 - CONCEPT: RMI remote object implementation.
 * <ul>
 *   <li>Delegates calls to the shared RailwayService (never duplicates state)</li>
 *   <li>Unknown train/section -> RemoteException with a clear message (not NPE)</li>
 *   <li>Logs each remote call (method, caller host via RemoteServer.getClientHost())</li>
 * </ul>
 * Exported in RmiAdminServer with UnicastRemoteObject.exportObject(this, rmi.service.port).
 */
public final class RailwayAdminService implements RailwayAdminRemote {

    private final RailwayService railway;
    private final EventLog log;

    public RailwayAdminService(RailwayService railway, EventLog log) {
        this.railway = Objects.requireNonNull(railway, "railway");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public List<TrainInfo> getActiveTrains() throws RemoteException {
        String host = clientHost();
        log.log("RMI", "getActiveTrains() from " + host);
        return railway.getTrains();
    }

    @Override
    public TrainInfo getTrainInfo(String trainId) throws RemoteException {
        Objects.requireNonNull(trainId, "trainId");
        String host = clientHost();
        log.log("RMI", "getTrainInfo(" + trainId + ") from " + host);
        return railway.getTrain(trainId)
                .orElseThrow(() -> new RemoteException("Unknown train: " + trainId));
    }

    @Override
    public TrackInfo getTrackStatus(String sectionId) throws RemoteException {
        Objects.requireNonNull(sectionId, "sectionId");
        String host = clientHost();
        log.log("RMI", "getTrackStatus(" + sectionId + ") from " + host);
        return railway.getTrack(sectionId)
                .orElseThrow(() -> new RemoteException("Unknown section: " + sectionId));
    }

    @Override
    public List<TrackInfo> getAllTracks() throws RemoteException {
        String host = clientHost();
        log.log("RMI", "getAllTracks() from " + host);
        return railway.getTracks();
    }

    @Override
    public boolean requestSimulatedHold(String trainId) throws RemoteException {
        Objects.requireNonNull(trainId, "trainId");
        String host = clientHost();
        log.log("RMI", "requestSimulatedHold(" + trainId + ") from " + host);
        if (railway.getTrain(trainId).isEmpty()) {
            throw new RemoteException("Unknown train: " + trainId);
        }
        return railway.holdTrain(trainId);
    }

    @Override
    public boolean requestResume(String trainId) throws RemoteException {
        Objects.requireNonNull(trainId, "trainId");
        String host = clientHost();
        log.log("RMI", "requestResume(" + trainId + ") from " + host);
        if (railway.getTrain(trainId).isEmpty()) {
            throw new RemoteException("Unknown train: " + trainId);
        }
        return railway.resumeTrain(trainId);
    }

    @Override
    public boolean requestEmergencyStop(String trainId) throws RemoteException {
        Objects.requireNonNull(trainId, "trainId");
        String host = clientHost();
        log.log("RMI", "requestEmergencyStop(" + trainId + ") from " + host);
        if (railway.getTrain(trainId).isEmpty()) {
            throw new RemoteException("Unknown train: " + trainId);
        }
        return railway.emergencyStop(trainId);
    }

    @Override
    public void reportObstruction(String sectionId, String reason) throws RemoteException {
        Objects.requireNonNull(sectionId, "sectionId");
        String host = clientHost();
        log.log("RMI", "reportObstruction(" + sectionId + ", " + reason + ") from " + host);
        if (railway.getTrack(sectionId).isEmpty()) {
            throw new RemoteException("Unknown section: " + sectionId);
        }
        railway.reportObstruction(sectionId, reason);
    }

    @Override
    public List<EventLog.Entry> getEventLog() throws RemoteException {
        String host = clientHost();
        log.log("RMI", "getEventLog() from " + host);
        return log.snapshot();
    }

    private String clientHost() {
        try {
            return RemoteServer.getClientHost();
        } catch (ServerNotActiveException e) {
            return "local";
        }
    }
}
