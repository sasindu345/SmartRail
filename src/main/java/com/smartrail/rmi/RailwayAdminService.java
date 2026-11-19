package com.smartrail.rmi;

import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.service.RailwayService;

import java.rmi.RemoteException;
import java.util.List;

/**
 * OWNER: Member 5 - CONCEPT: RMI remote object implementation.
 * <ul>
 *   <li>TODO(M5) delegate every call to the shared RailwayService (never duplicate state)</li>
 *   <li>TODO(M5) unknown train/section -> RemoteException with a clear message (not NPE)</li>
 *   <li>TODO(M5) log each remote call (method, caller host via RemoteServer.getClientHost())</li>
 * </ul>
 * Export it in RmiAdminServer with UnicastRemoteObject.exportObject(this, rmi.service.port).
 */
public final class RailwayAdminService implements RailwayAdminRemote {

    private final RailwayService railway;
    private final EventLog log;

    public RailwayAdminService(RailwayService railway, EventLog log) {
        this.railway = railway;
        this.log = log;
    }

    @Override
    public List<TrainInfo> getActiveTrains() throws RemoteException {
        throw todo("getActiveTrains");
    }

    @Override
    public TrainInfo getTrainInfo(String trainId) throws RemoteException {
        throw todo("getTrainInfo");
    }

    @Override
    public TrackInfo getTrackStatus(String sectionId) throws RemoteException {
        throw todo("getTrackStatus");
    }

    @Override
    public List<TrackInfo> getAllTracks() throws RemoteException {
        throw todo("getAllTracks");
    }

    @Override
    public boolean requestSimulatedHold(String trainId) throws RemoteException {
        throw todo("requestSimulatedHold");
    }

    @Override
    public boolean requestResume(String trainId) throws RemoteException {
        throw todo("requestResume");
    }

    @Override
    public boolean requestEmergencyStop(String trainId) throws RemoteException {
        throw todo("requestEmergencyStop");
    }

    @Override
    public void reportObstruction(String sectionId, String reason) throws RemoteException {
        throw todo("reportObstruction");
    }

    @Override
    public List<EventLog.Entry> getEventLog() throws RemoteException {
        throw todo("getEventLog");
    }

    private static RemoteException todo(String method) {
        return new RemoteException("TODO(M5): RailwayAdminService." + method);
    }
}
