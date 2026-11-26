package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.RouteInfo;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;

import java.util.Optional;

/**
 * OWNER: Member 1 - CONCEPT: the server assigns predefined routes ({@code ASSIGN_ROUTE}).
 */
public final class RouteService {

    private final RailwayData data;

    public RouteService(RailwayData data) {
        this.data = data;
    }

    public Optional<RouteInfo> routeFor(String trainId) {
        return data.train(trainId).flatMap(train -> data.route(train.routeId()));
    }

    /** {@code ASSIGN_ROUTE|trainId|routeId|SEC_A,SEC_B} for the train's predefined route. */
    public Optional<Message> assignment(String trainId) {
        return routeFor(trainId).map(route -> Message.of(MessageType.ASSIGN_ROUTE, trainId, route.routeId(),
                String.join(",", route.sectionIds())));
    }

    public boolean isOnRoute(String trainId, String sectionId) {
        return routeFor(trainId).map(route -> route.sectionIds().contains(sectionId)).orElse(false);
    }
}
