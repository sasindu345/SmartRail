package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.RouteInfo;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Sections in corridor order from {@code startId} to {@code endId}, one direction only.
     * Empty when the stations are the same or the corridor has no block between them.
     */
    public static List<String> between(RailwayData data, String startId, String endId) {
        List<StationInfo> stations = data.stations();
        int from = indexOf(stations, startId);
        int to = indexOf(stations, endId);
        if (from < 0 || to < 0 || from == to) {
            return List.of();
        }
        int step = to > from ? 1 : -1;
        List<String> sections = new ArrayList<>();
        for (int i = from; i != to; i += step) {
            String begin = stations.get(i).stationId();
            String finish = stations.get(i + step).stationId();
            String sectionId = null;
            for (TrackInfo section : data.sections()) {
                if (section.fromStationId().equals(begin) && section.toStationId().equals(finish)) {
                    sectionId = section.sectionId();
                    break;
                }
            }
            if (sectionId == null) {
                return List.of();
            }
            sections.add(sectionId);
        }
        return List.copyOf(sections);
    }

    private static int indexOf(List<StationInfo> stations, String stationId) {
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).stationId().equals(stationId)) {
                return i;
            }
        }
        return -1;
    }
}
