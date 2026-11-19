package com.smartrail.common.model;

import java.io.Serializable;

/** A station. Serializable so it can cross RMI. SHARED CONTRACT (frozen). */
public record StationInfo(String stationId, String name) implements Serializable {
}
