package com.smartrail.tcpclient;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * OWNER: Member 2 - tests for the TCP train client.
 * Remove @Disabled as you implement. Use testsupport.FakeRailwayService / FakeTrainConnection / TestConfig
 * (TestConfig.withOverrides(key, "0") gives an OS-chosen free port).
 */
class TrainClientTest {

    @Test
    @Disabled("TODO(M2)")
    void connectsAndSendsRegister() {
    }

    @Test
    @Disabled("TODO(M2)")
    void receivesAndDecodesServerLines() {
    }

    @Test
    @Disabled("TODO(M2)")
    void wrongPortReportsError() {
    }

    @Test
    @Disabled("TODO(M2)")
    void reconnectsAfterServerRestart() {
    }
}
