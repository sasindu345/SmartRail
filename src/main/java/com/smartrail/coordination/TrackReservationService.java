package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.ReservationResult;

/**
 * OWNER: Member 1 - CONCEPT: concurrent shared-state coordination on the server.
 *
 * <p>Owns the FREE/RESERVED/OCCUPIED state of each exclusive section.
 * <ul>
 *   <li>TODO(M1) initialise sections from RailwayData (all FREE)</li>
 *   <li>TODO(M1) reserve(): ATOMIC check-and-reserve (synchronized / lock / ConcurrentHashMap.compute);
 *       denied reason format {@code RESERVED_BY_<trainId>}</li>
 *   <li>TODO(M1) markOccupied / release; release all sections of a disconnected train</li>
 * </ul>
 */
public final class TrackReservationService {

    private final RailwayData data;

    public TrackReservationService(RailwayData data) {
        this.data = data;
    }

    public ReservationResult reserve(String trainId, String sectionId) {
        throw new UnsupportedOperationException("TODO(M1): atomic check-and-reserve over " + data.sections().size() + " sections");
    }

    public boolean release(String trainId, String sectionId) {
        throw new UnsupportedOperationException("TODO(M1): release section");
    }
}
