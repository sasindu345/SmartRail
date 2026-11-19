package com.smartrail.coordination;

/**
 * OWNER: Member 1 - CONCEPT: server-side client registry (client-server model).
 *
 * <p>Thread-safe map of trainId -> current state + {@code TrainConnection}.
 * <ul>
 *   <li>TODO(M1) register / unregister a train with its connection (reject duplicates)</li>
 *   <li>TODO(M1) look up the connection for a trainId (used to push HOLD/RESUME/EMERGENCY_STOP)</li>
 *   <li>TODO(M1) keep status, location, transport, remoteAddress, lastSeenMillis; produce TrainInfo snapshots</li>
 * </ul>
 * Design the methods you need - only Member 1's classes call this one.
 */
public final class TrainRegistry {
}
