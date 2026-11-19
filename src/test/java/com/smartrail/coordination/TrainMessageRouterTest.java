package com.smartrail.coordination;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * OWNER: Member 1 - tests for message routing.
 * Remove @Disabled as you implement. Use testsupport.FakeRailwayService / FakeTrainConnection / TestConfig
 * (TestConfig.withOverrides(key, "0") gives an OS-chosen free port).
 */
class TrainMessageRouterTest {

    @Test
    @Disabled("TODO(M1)")
    void registerRepliesRegisterOkAndAssignRoute() {
    }

    @Test
    @Disabled("TODO(M1)")
    void unregisteredTrainGetsError() {
    }

    @Test
    @Disabled("TODO(M1)")
    void identityMismatchIsRejected() {
    }

    @Test
    @Disabled("TODO(M1)")
    void heartbeatIsForwardedToTracker() {
    }
}
