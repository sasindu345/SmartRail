package com.smartrail.nio;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * OWNER: Member 1 - tests for NIO framing.
 * Remove @Disabled as you implement. Use testsupport.FakeRailwayService / FakeTrainConnection / TestConfig
 * (TestConfig.withOverrides(key, "0") gives an OS-chosen free port).
 */
class LineFrameDecoderTest {

    @Test
    @Disabled("TODO(M1)")
    void partialMessageIsBufferedUntilNewline() {
    }

    @Test
    @Disabled("TODO(M1)")
    void twoMessagesInOneReadAreBothReturned() {
    }

    @Test
    @Disabled("TODO(M1)")
    void oversizedLineIsRejected() {
    }
}
