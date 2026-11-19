package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.RouteInfo;

import java.util.Optional;

/**
 * OWNER: Member 1 - CONCEPT: server assigns predefined routes (ASSIGN_ROUTE message).
 * <ul>
 *   <li>TODO(M1) return the route for a train from RailwayData</li>
 *   <li>TODO(M1) build the ASSIGN_ROUTE|trainId|routeId|SEC_A,SEC_B message after REGISTER_OK</li>
 * </ul>
 */
public final class RouteService {

    private final RailwayData data;

    public RouteService(RailwayData data) {
        this.data = data;
    }

    public Optional<RouteInfo> routeFor(String trainId) {
        throw new UnsupportedOperationException("TODO(M1): route lookup for " + trainId + " in " + data.routes().size() + " routes");
    }
}
