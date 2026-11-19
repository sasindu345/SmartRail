package com.smartrail.common.model;

import java.io.Serializable;

/** One timetable stop: train {@code trainId} is at {@code stationId} at {@code time} (HH:mm). */
public record ScheduleInfo(String trainId, String stationId, String time) implements Serializable {
}
