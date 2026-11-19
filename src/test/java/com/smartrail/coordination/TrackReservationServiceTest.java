package com.smartrail.coordination;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * OWNER: Member 1 - tests for atomic reservations.
 * Remove @Disabled as you implement. Use testsupport.FakeRailwayService / FakeTrainConnection / TestConfig
 * (TestConfig.withOverrides(key, "0") gives an OS-chosen free port).
 */
class TrackReservationServiceTest {

    @Test
    @Disabled("TODO(M1)")
    void freeSectionIsGranted() {
    }

    @Test
    @Disabled("TODO(M1)")
    void reservedSectionIsDeniedWithHolder() {
    }

    @Test
    @Disabled("TODO(M1)")
    void concurrentRequestsOnlyOneWins() {
    }

    @Test
    @Disabled("TODO(M1)")
    void releaseFreesSection() {
    }
}
