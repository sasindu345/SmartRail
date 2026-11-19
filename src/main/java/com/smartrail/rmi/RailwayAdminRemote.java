package com.smartrail.rmi;

import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * OWNER: Member 5 - CONCEPT: Java RMI remote interface (low-volume administration only;
 * train telemetry stays on TCP/TLS).
 *
 * <p>Every return/parameter type is Serializable (records in common.model). Changing this interface
 * breaks the controller dashboard - coordinate with the team first.
 */
public interface RailwayAdminRemote extends Remote {

    List<TrainInfo> getActiveTrains() throws RemoteException;

    TrainInfo getTrainInfo(String trainId) throws RemoteException;

    TrackInfo getTrackStatus(String sectionId) throws RemoteException;

    List<TrackInfo> getAllTracks() throws RemoteException;

    boolean requestSimulatedHold(String trainId) throws RemoteException;

    boolean requestResume(String trainId) throws RemoteException;

    boolean requestEmergencyStop(String trainId) throws RemoteException;

    void reportObstruction(String sectionId, String reason) throws RemoteException;

    List<EventLog.Entry> getEventLog() throws RemoteException;
}
