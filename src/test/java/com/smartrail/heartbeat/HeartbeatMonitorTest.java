package com.smartrail.heartbeat;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.testsupport.FakeRailwayService;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER: Member 3 - tests for heartbeat timeout (server side: {@link HeartbeatMonitor}) and the periodic
 * sender (client side: {@link HeartbeatSender}). Uses short overridden intervals so the tests run in well
 * under a second instead of waiting out the real {@code heartbeat.*} defaults.
 */
class HeartbeatMonitorTest {

    private HeartbeatMonitor monitor;
    private HeartbeatSender sender;

    @AfterEach
    void cleanup() {
        if (monitor != null) {
            monitor.stop();
        }
        if (sender != null) {
            sender.stop();
        }
    }

    @Test
    void silentTrainIsMarkedInactive() throws Exception {
        NetworkConfig config = TestConfig.withOverrides(
                ConfigKeys.HEARTBEAT_INTERVAL_MS, "50",
                ConfigKeys.HEARTBEAT_TIMEOUT_MS, "150");
        FakeRailwayService railway = FakeRailwayService.withDefaultData();
        monitor = new HeartbeatMonitor(config, railway, new EventLog(100));
        monitor.start();

        monitor.recordHeartbeat("TR01");
        assertTrue(monitor.isAlive("TR01"));

        Thread.sleep(400); // well past the 150ms timeout, with the 50ms check interval

        assertFalse(monitor.isAlive("TR01"));
        assertTrue(railway.calls().contains("markInactive:TR01"), "expected markInactive to be called once the train went silent");
    }

    @Test
    void heartbeatKeepsTrainAlive() throws Exception {
        NetworkConfig config = TestConfig.withOverrides(
                ConfigKeys.HEARTBEAT_INTERVAL_MS, "50",
                ConfigKeys.HEARTBEAT_TIMEOUT_MS, "200");
        FakeRailwayService railway = FakeRailwayService.withDefaultData();
        monitor = new HeartbeatMonitor(config, railway, new EventLog(100));
        monitor.start();

        // Keep refreshing TR02's heartbeat faster than the timeout.
        for (int i = 0; i < 5; i++) {
            monitor.recordHeartbeat("TR02");
            Thread.sleep(80);
        }

        assertTrue(monitor.isAlive("TR02"));
        assertFalse(railway.calls().contains("markInactive:TR02"), "a train still sending heartbeats must never be marked inactive");
    }

    @Test
    void senderEmitsHeartbeatPeriodically() throws Exception {
        NetworkConfig config = TestConfig.withOverrides(ConfigKeys.HEARTBEAT_INTERVAL_MS, "50");
        List<Message> sent = new CopyOnWriteArrayList<>();
        sender = new HeartbeatSender(config, "TR03", sent::add);

        sender.start();
        Thread.sleep(260);
        sender.stop();

        assertTrue(sent.size() >= 2, "expected several heartbeats within 260ms at a 50ms interval, got " + sent.size());
        for (Message m : sent) {
            assertTrue(m.type() == MessageType.HEARTBEAT, "expected only HEARTBEAT messages");
            assertTrue(m.field(0).equals("TR03"));
        }

        int countAfterStop = sent.size();
        Thread.sleep(150);
        assertTrue(sent.size() == countAfterStop, "no more heartbeats should be sent after stop()");
    }
}
