package com.smartrail.heartbeat;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.protocol.Message;

import java.util.function.Consumer;

/**
 * OWNER: Member 3 - CONCEPT: heartbeat, client side.
 *
 * <p>Sends {@code HEARTBEAT|trainId} every {@link ConfigKeys#HEARTBEAT_INTERVAL_MS} through {@code sink}
 * (the train client's send method, wired by TrainClientApp). Knows nothing about sockets.
 * <ul>
 *   <li>TODO(M3) start(): ScheduledExecutorService (daemon thread); stop(): cancel</li>
 *   <li>TODO(M3) swallow/log send failures so the timer keeps running</li>
 * </ul>
 */
public final class HeartbeatSender {

    private final NetworkConfig config;
    private final String trainId;
    private final Consumer<Message> sink;

    public HeartbeatSender(NetworkConfig config, String trainId, Consumer<Message> sink) {
        this.config = config;
        this.trainId = trainId;
        this.sink = sink;
    }

    public void start() {
        throw new UnsupportedOperationException("TODO(M3): periodic HEARTBEAT for " + trainId);
    }

    public void stop() {
        // TODO(M3)
    }
}
