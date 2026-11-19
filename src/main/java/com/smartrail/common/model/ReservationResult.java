package com.smartrail.common.model;

import java.io.Serializable;

/**
 * Outcome of a section reservation request. SHARED CONTRACT (frozen).
 *
 * @param holderTrainId the train holding the section when denied (else {@code null})
 * @param reason        machine-readable reason when denied, e.g. {@code RESERVED_BY_TR02}
 */
public record ReservationResult(boolean granted, String trainId, String sectionId,
                                String holderTrainId, String reason) implements Serializable {

    public static ReservationResult granted(String trainId, String sectionId) {
        return new ReservationResult(true, trainId, sectionId, null, null);
    }

    public static ReservationResult denied(String trainId, String sectionId, String holderTrainId, String reason) {
        return new ReservationResult(false, trainId, sectionId, holderTrainId, reason);
    }
}
