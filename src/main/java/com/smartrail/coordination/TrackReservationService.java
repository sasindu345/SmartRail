package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.ReservationResult;
import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.TrackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OWNER: Member 1 - CONCEPT: concurrent shared-state coordination on the server.
 *
 * <p>{@link #reserve} is an atomic check-and-reserve: two threads asking for one FREE section
 * cannot both be granted. A denial names the holder as {@code RESERVED_BY_<trainId>}.
 */
public final class TrackReservationService {

    private final Map<String, TrackInfo> sections = new LinkedHashMap<>();

    public TrackReservationService(RailwayData data) {
        data.sections().forEach(section -> sections.put(section.sectionId(), section));
    }

    public synchronized ReservationResult reserve(String trainId, String sectionId) {
        TrackInfo section = sections.get(sectionId);
        if (section == null) {
            return ReservationResult.denied(trainId, sectionId, null, "UNKNOWN_SECTION");
        }
        if (section.state() != SectionState.FREE && !trainId.equals(section.holderTrainId())) {
            return ReservationResult.denied(trainId, sectionId, section.holderTrainId(),
                    "RESERVED_BY_" + section.holderTrainId());
        }
        sections.put(sectionId, copy(section, SectionState.RESERVED, trainId));
        return ReservationResult.granted(trainId, sectionId);
    }

    public synchronized boolean release(String trainId, String sectionId) {
        TrackInfo section = sections.get(sectionId);
        if (section == null || !trainId.equals(section.holderTrainId())) {
            return false;
        }
        sections.put(sectionId, copy(section, SectionState.FREE, null));
        return true;
    }

    /** Returns every section to FREE. Used when the simulation is reset to the timetable start. */
    public synchronized void releaseEvery() {
        for (TrackInfo section : new ArrayList<>(sections.values())) {
            sections.put(section.sectionId(), copy(section, SectionState.FREE, null));
        }
    }

    /** Drops every section held by {@code trainId}. Called when that train disconnects or goes inactive. */
    public synchronized void releaseAll(String trainId) {
        for (TrackInfo section : new ArrayList<>(sections.values())) {
            if (trainId.equals(section.holderTrainId())) {
                sections.put(section.sectionId(), copy(section, SectionState.FREE, null));
            }
        }
    }

    /** A position report on a section this train holds moves it from RESERVED to OCCUPIED. */
    public synchronized void markOccupied(String trainId, String sectionId) {
        TrackInfo section = sections.get(sectionId);
        if (section != null && trainId.equals(section.holderTrainId())) {
            sections.put(sectionId, copy(section, SectionState.OCCUPIED, trainId));
        }
    }

    public synchronized List<TrackInfo> snapshots() {
        return List.copyOf(sections.values());
    }

    public synchronized Optional<TrackInfo> snapshot(String sectionId) {
        return Optional.ofNullable(sections.get(sectionId));
    }

    private static TrackInfo copy(TrackInfo section, SectionState state, String holder) {
        return new TrackInfo(section.sectionId(), section.fromStationId(), section.toStationId(), state, holder);
    }
}
