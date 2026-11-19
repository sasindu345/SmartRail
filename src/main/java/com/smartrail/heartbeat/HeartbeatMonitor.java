package com.smartrail.heartbeat;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.service.HeartbeatTracker;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.RailwayService;

/**
 * OWNER: Member 3 - CONCEPT: availability monitoring (heartbeat + timeout), server side.
 * <ul>
 *   <li>TODO(M3) recordHeartbeat(): store last-seen time per train (ConcurrentHashMap)</li>
 *   <li>TODO(M3) start(): ScheduledExecutorService every {@link ConfigKeys#HEARTBEAT_INTERVAL_MS};
 *       trains silent for more than {@link ConfigKeys#HEARTBEAT_TIMEOUT_MS} -> railway.markInactive (once)</li>
 *   <li>TODO(M3) a train that sends a heartbeat again becomes alive again</li>
 * </ul>
 */
public final class HeartbeatMonitor implements HeartbeatTracker, NetworkService {

    private final NetworkConfig config;
    private final RailwayService railway;
    private final EventLog log;

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
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M3): schedule heartbeat timeout checks");
    }

    @Override
    public void recordHeartbeat(String trainId) {
        throw new UnsupportedOperationException("TODO(M3): record heartbeat of " + trainId);
    }

    @Override
    public boolean isAlive(String trainId) {
        throw new UnsupportedOperationException("TODO(M3): liveness of " + trainId);
    }

    @Override
    public void stop() {
        // TODO(M3): shut down scheduler
    }
}
