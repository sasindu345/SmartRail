package com.smartrail.common.model;

import java.io.Serializable;

/** Static train definition from railway-data.properties. SHARED CONTRACT (frozen). */
public record TrainDefinition(String trainId, String name, String startStationId, String routeId)
        implements Serializable {
}
