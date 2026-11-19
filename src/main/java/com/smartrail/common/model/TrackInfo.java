package com.smartrail.common.model;

import java.io.Serializable;

/**
 * Snapshot of one track section.
 *
 * @param holderTrainId train that reserved/occupies the section, or {@code null} when FREE
 */
public record TrackInfo(String sectionId, String fromStationId, String toStationId,
                        SectionState state, String holderTrainId) implements Serializable {
}
