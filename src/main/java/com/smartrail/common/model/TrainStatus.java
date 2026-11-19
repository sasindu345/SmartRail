package com.smartrail.common.model;

/** Simulated train status. SHARED CONTRACT (frozen). */
public enum TrainStatus {
    REGISTERED,
    MOVING,
    WAITING,
    HELD,
    EMERGENCY_STOPPED,
    ARRIVED,
    /** Set by the heartbeat monitor (Member 3) when no heartbeat arrives within the timeout. */
    INACTIVE
}
