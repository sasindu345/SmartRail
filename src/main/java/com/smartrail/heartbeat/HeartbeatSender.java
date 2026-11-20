package com.smartrail.heartbeat;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OWNER: Member 3 - CONCEPT: heartbeat, client side.
 *
 * <p>Sends {@code HEARTBEAT|trainId} every {@code heartbeat.interval.ms} through {@code sink} (the train
 * client's send method, wired by {@code TrainClientApp}). Knows nothing about sockets - a send failure is
 * swallowed so the timer keeps running; the TCP/TLS layer handles connection problems on its own.
 */
public final class HeartbeatSender {

    private final NetworkConfig config;
    private final String trainId;
    private final Consumer<Message> sink;

    private volatile ScheduledExecutorService scheduler;

    public HeartbeatSender(NetworkConfig config, String trainId, Consumer<Message> sink) {
        this.config = config;
        this.trainId = trainId;
        this.sink = sink;
    }

    public synchronized void start() {
        stop();
        long intervalMs = config.getLong(ConfigKeys.HEARTBEAT_INTERVAL_MS);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "heartbeat-sender-" + trainId);
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::sendHeartbeat, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        scheduler = executor;
    }

    private void sendHeartbeat() {
        try {
            sink.accept(Message.of(MessageType.HEARTBEAT, trainId));
        } catch (Exception e) {
            // Swallow so the schedule keeps firing even if the connection is briefly down.
        }
    }

    public synchronized void stop() {
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) {
            current.shutdownNow();
        }
    }
}
