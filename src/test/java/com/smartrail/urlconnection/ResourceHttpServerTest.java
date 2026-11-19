package com.smartrail.urlconnection;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * OWNER: Member 4 - tests for HTTP resources + URLConnection.
 * Remove @Disabled as you implement. Use testsupport.FakeRailwayService / FakeTrainConnection / TestConfig
 * (TestConfig.withOverrides(key, "0") gives an OS-chosen free port).
 */
class ResourceHttpServerTest {

    @Test
    @Disabled("TODO(M4)")
    void fetchesSchedulesAsText() {
    }

    @Test
    @Disabled("TODO(M4)")
    void unknownResourceGives404() {
    }

    @Test
    @Disabled("TODO(M4)")
    void contentTypeHeaderIsSmartRailMime() {
    }

    @Test
    @Disabled("TODO(M4)")
    void timeoutsAreApplied() {
    }
}
