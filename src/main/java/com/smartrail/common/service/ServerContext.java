package com.smartrail.common.service;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;

/**
 * Everything a server-side {@link NetworkService} may need, built once by {@code ServerApp}.
 * Constructors of server services take only this object. SHARED CONTRACT (frozen).
 */
public record ServerContext(
        NetworkConfig config,
        RailwayData railwayData,
        EventLog eventLog,
        RailwayService railwayService,
        MessageHandler messageHandler,
        AlertService alertService,
        HeartbeatTracker heartbeatTracker) {
}
