package com.smartrail.common.model;

import java.io.Serializable;
import java.util.List;

/** Predefined route = ordered section IDs. SHARED CONTRACT (frozen). */
public record RouteInfo(String routeId, List<String> sectionIds) implements Serializable {
    public RouteInfo {
        sectionIds = List.copyOf(sectionIds);
    }
}
