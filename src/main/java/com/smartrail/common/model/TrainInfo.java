package com.smartrail.common.model;

import java.io.Serializable;

/**
 * Snapshot of one train as seen by the central server. SHARED CONTRACT (frozen).
 *
 * @param location       current station or section ID
 * @param transport      channel in use, or {@code null} if never connected
 * @param remoteAddress  "ip:port" of the train client, or {@code null}
 * @param lastSeenMillis epoch millis of the last message/heartbeat (0 = never)
 */
public record TrainInfo(String trainId, String name, TrainStatus status, String location, String routeId,
                        Transport transport, String remoteAddress, long lastSeenMillis) implements Serializable {
}
