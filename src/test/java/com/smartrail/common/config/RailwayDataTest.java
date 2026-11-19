package com.smartrail.common.config;

import com.smartrail.common.model.RouteInfo;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailwayDataTest {

    private final RailwayData data = RailwayData.load(TestConfig.defaults());

    @Test
    void loadsModel() {
        assertFalse(data.trains().isEmpty());
        assertFalse(data.stations().isEmpty());
        assertFalse(data.sections().isEmpty());
    }

    @Test
    void referencesAreConsistent() {
        data.trains().forEach(t -> {
            assertTrue(data.station(t.startStationId()).isPresent(), "unknown start station for " + t.trainId());
            RouteInfo route = data.route(t.routeId()).orElseThrow();
            route.sectionIds().forEach(s -> assertTrue(data.section(s).isPresent(), "unknown section " + s));
        });
        data.schedules().forEach(s -> assertTrue(data.station(s.stationId()).isPresent(), "unknown station " + s));
    }
}
