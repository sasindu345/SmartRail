package com.smartrail.heartbeat;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.RailwayService;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OWNER: Member 3 - CONCEPT: availability monitoring (heartbeat + timeout), server side.
 *
 * <p>Tracks the last time each train sent a {@code HEARTBEAT} (recorded by Member 1's message router) and,
 * on a fixed schedule, marks any train silent for longer than {@code heartbeat.timeout.ms} as
 * {@code INACTIVE} through {@link RailwayService#markInactive}. Marking happens exactly once per silence -
 * a renewed heartbeat clears the flag so the train can be marked inactive again later if it goes quiet once more.
 */
public final class HeartbeatMonitor implements HeartbeatTracker, NetworkService {

    private final NetworkConfig config;
    private final RailwayService railway;
    private final EventLog log;

    private final Map<String, Long> lastSeenMillis = new ConcurrentHashMap<>();
    private final Set<String> markedInactive = ConcurrentHashMap.newKeySet();
    private volatile ScheduledExecutorService scheduler;

    public HeartbeatMonitor(NetworkConfig config, RailwayService railway, EventLog log) {
        this.config = config;
        this.railway = railway;
        this.log = log;
    }

    @Override
    public String name() {
        return "Heartbeat monitor";
    }

    @Override
    public String endpoint() {
        return "timeout " + config.getLong(ConfigKeys.HEARTBEAT_TIMEOUT_MS) + " ms";
    }

    @Override
    public void start() {
        long intervalMs = config.getLong(ConfigKeys.HEARTBEAT_INTERVAL_MS);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "heartbeat-monitor");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::checkTimeouts, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        scheduler = executor;
    }

    private void checkTimeouts() {
        long timeoutMs = config.getLong(ConfigKeys.HEARTBEAT_TIMEOUT_MS);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastSeenMillis.entrySet()) {
            String trainId = entry.getKey();
            if (now - entry.getValue() > timeoutMs && markedInactive.add(trainId)) {
                log.log("HEARTBEAT", trainId + " silent for over " + timeoutMs + " ms -> INACTIVE");
                railway.markInactive(trainId);
            }
        }
    }

    @Override
    public void recordHeartbeat(String trainId) {
        lastSeenMillis.put(trainId, System.currentTimeMillis());
        if (markedInactive.remove(trainId)) {
            log.log("HEARTBEAT", trainId + " is alive again");
        }
    }

    @Override
    public boolean isAlive(String trainId) {
        Long lastSeen = lastSeenMillis.get(trainId);
        if (lastSeen == null) {
            return false;
        }
        return System.currentTimeMillis() - lastSeen <= config.getLong(ConfigKeys.HEARTBEAT_TIMEOUT_MS);
    }

    @Override
    public void stop() {
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) {
            current.shutdownNow();
        }
    }
}
