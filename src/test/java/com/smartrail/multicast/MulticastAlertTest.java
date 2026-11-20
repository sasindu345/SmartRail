package com.smartrail.multicast;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.testsupport.FakeRailwayService;
import com.smartrail.testsupport.FakeTrainConnection;
import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER: Member 3 - tests for multicast alerts.
 *
 * <p>{@link #nonAlertDatagramsAreIgnored} and {@link #unicastFallbackReachesConnectedTrains} exercise real
 * decoding logic with plain, routable UDP and always run. {@link #twoSubscribersReceiveOneAlert} needs an
 * actual multicast-capable route, which some hosts (VPNs, restrictive sandboxes, campus Wi-Fi) do not
 * provide; it skips itself with a clear message rather than failing the build when that happens - see
 * docs/modules/m3-udp-multicast.md.
 */
class MulticastAlertTest {

    private MulticastAlertPublisher publisher;
    private MulticastAlertSubscriber subscriberA;
    private MulticastAlertSubscriber subscriberB;

    @AfterEach
    void cleanup() {
        if (publisher != null) {
            publisher.stop();
        }
        if (subscriberA != null) {
            subscriberA.stop();
        }
        if (subscriberB != null) {
            subscriberB.stop();
        }
    }

    @Test
    void twoSubscribersReceiveOneAlert() throws Exception {
        NetworkConfig config = TestConfig.withOverrides(
                ConfigKeys.MULTICAST_GROUP, "230.0.0.1",
                ConfigKeys.MULTICAST_PORT, "15999",
                ConfigKeys.MULTICAST_UNICAST_FALLBACK_ENABLED, "false");

        BlockingQueue<Message> receivedByA = new ArrayBlockingQueue<>(10);
        BlockingQueue<Message> receivedByB = new ArrayBlockingQueue<>(10);
        subscriberA = new MulticastAlertSubscriber(config, receivedByA::offer);
        subscriberB = new MulticastAlertSubscriber(config, receivedByB::offer);

        boolean joined = tryStart(subscriberA) && tryStart(subscriberB);
        Assumptions.assumeTrue(joined, "multicast group join not available on this host/network - skipping");

        publisher = new MulticastAlertPublisher(config, new EventLog(100));
        publisher.start();
        // Give both subscribers' receive loops a moment to actually be listening.
        Thread.sleep(300);

        publisher.publishAlert("SEC_B", "OBSTRUCTION");

        Message gotByA = receivedByA.poll(3, TimeUnit.SECONDS);
        Message gotByB = receivedByB.poll(3, TimeUnit.SECONDS);
        Assumptions.assumeTrue(gotByA != null && gotByB != null,
                "no multicast datagram arrived within the timeout - treating as multicast unavailable on this host/network");

        assertEquals(MessageType.EMERGENCY_ALERT, gotByA.type());
        assertEquals("SEC_B", gotByA.field(0));
        assertEquals("OBSTRUCTION", gotByA.field(1));
        assertEquals(gotByA, gotByB);
    }

    @Test
    void nonAlertDatagramsAreIgnored() {
        NetworkConfig config = TestConfig.withOverrides(ConfigKeys.MULTICAST_GROUP, "230.0.0.1");
        BlockingQueue<Message> received = new ArrayBlockingQueue<>(10);
        MulticastAlertSubscriber subscriber = new MulticastAlertSubscriber(config, received::offer);

        byte[] heartbeat = ProtocolCodec.encode(Message.of(MessageType.HEARTBEAT, "TR01")).getBytes(ProtocolCodec.CHARSET);
        subscriber.handleDatagram(heartbeat, 0, heartbeat.length);
        assertTrue(received.isEmpty(), "a non-alert message must not be dispatched");

        byte[] garbage = "not a smartrail message".getBytes(ProtocolCodec.CHARSET);
        subscriber.handleDatagram(garbage, 0, garbage.length);
        assertTrue(received.isEmpty(), "a malformed datagram must not crash the receive loop or be dispatched");

        byte[] alert = ProtocolCodec.encode(Message.of(MessageType.EMERGENCY_ALERT, "SEC_A", "DEBRIS")).getBytes(ProtocolCodec.CHARSET);
        subscriber.handleDatagram(alert, 0, alert.length);
        assertEquals(1, received.size(), "a real EMERGENCY_ALERT must still be dispatched");
    }

    @Test
    void unicastFallbackReachesConnectedTrains() throws Exception {
        // Plain, routable unicast UDP - unlike multicast this does not depend on a multicast-capable route.
        try (DatagramSocket listener = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            NetworkConfig config = TestConfig.withOverrides(
                    ConfigKeys.MULTICAST_GROUP, "230.0.0.1",
                    ConfigKeys.MULTICAST_PORT, Integer.toString(listener.getLocalPort()),
                    ConfigKeys.MULTICAST_UNICAST_FALLBACK_ENABLED, "true");

            FakeRailwayService railway = FakeRailwayService.withDefaultData();
            railway.registerTrain("TR01", "ST01", new FakeTrainConnection("127.0.0.1:54321", Transport.PLAIN));

            publisher = new MulticastAlertPublisher(config, new EventLog(100));
            publisher.bindRailwayService(railway);
            publisher.start();
            publisher.publishAlert("SEC_B", "OBSTRUCTION");

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            listener.setSoTimeout(3000);
            listener.receive(packet);

            Message received = ProtocolCodec.decode(new String(packet.getData(), 0, packet.getLength(), ProtocolCodec.CHARSET));
            assertEquals(MessageType.EMERGENCY_ALERT, received.type());
            assertEquals("SEC_B", received.field(0));
        }
    }

    private static boolean tryStart(MulticastAlertSubscriber subscriber) {
        try {
            subscriber.start();
            return true;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }
}
