package com.smartrail.common.service;

/**
 * Liveness bookkeeping. Implemented by Member 3 ({@code HeartbeatMonitor}); Member 1's router calls
 * {@link #recordHeartbeat} for every HEARTBEAT message. SHARED CONTRACT (frozen).
 */
public interface HeartbeatTracker {

    void recordHeartbeat(String trainId);

    /** {@code true} if a heartbeat arrived within {@code heartbeat.timeout.ms}. */
    boolean isAlive(String trainId);
}
