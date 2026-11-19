package com.smartrail.tls;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * OWNER: Member 4 - tests for TLS.
 * Remove @Disabled as you implement. Use testsupport.FakeRailwayService / FakeTrainConnection / TestConfig
 * (TestConfig.withOverrides(key, "0") gives an OS-chosen free port).
 */
class TlsChannelTest {

    @Test
    @Disabled("TODO(M4)")
    void trustedCertificateHandshakeSucceeds() {
    }

    @Test
    @Disabled("TODO(M4)")
    void untrustedCertificateIsRejected() {
    }

    @Test
    @Disabled("TODO(M4)")
    void hostnameMismatchIsRejected() {
    }

    @Test
    @Disabled("TODO(M4)")
    void registerWorksOverTls() {
    }
}
