package com.smartrail.app;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.model.Transport;
import com.smartrail.common.service.ClientSocketFactory;
import com.smartrail.heartbeat.HeartbeatSender;
import com.smartrail.multicast.MulticastAlertSubscriber;
import com.smartrail.tcpclient.PlainSocketFactory;
import com.smartrail.tcpclient.TrainClient;
import com.smartrail.tcpclient.TrainClientListener;
import com.smartrail.tcpclient.TrainSimulator;
import com.smartrail.tls.TlsSocketFactory;

/**
 * SHARED launcher: one independent train client process.
 *
 * <p>Headless. The Control Center starts one process per train and shows its stdout.
 * There is no train window.
 *
 * <p>Run: {@code scripts/run.sh train TR02} (train ID from args, default {@code client.default.train.id});
 * use TLS with {@code -Dclient.transport=TLS}.
 */
public final class TrainClientApp {

    private TrainClientApp() {
    }

    public static void main(String[] args) {
        NetworkConfig config = NetworkConfig.load();
        RailwayData data = RailwayData.load(config);
        String trainId = args.length > 0 ? args[0] : config.get(ConfigKeys.CLIENT_DEFAULT_TRAIN_ID);
        TrainDefinition train = data.train(trainId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown train " + trainId + " - see config/railway-data.properties"));
        String chosenStart = System.getProperty("smartrail.journey.start");
        if (chosenStart != null && !chosenStart.isBlank()) {
            String start = chosenStart.trim();
            if (data.station(start).isEmpty()) {
                throw new IllegalArgumentException("Unknown start station " + start);
            }
            train = new TrainDefinition(train.trainId(), train.name(), start, train.routeId());
        }

        Transport transport = Transport.valueOf(config.get(ConfigKeys.CLIENT_TRANSPORT).toUpperCase());
        ClientSocketFactory sockets = transport == Transport.TLS
                ? new TlsSocketFactory(config)          // Member 4
                : new PlainSocketFactory();             // Member 2
        TrainClient client = new TrainClient(config, train, sockets);                       // Member 2

        HeartbeatSender heartbeat = new HeartbeatSender(config, trainId, client::send);     // Member 3
        TrainSimulator simulator = new TrainSimulator(config, client);                      // Member 2
        client.addListener(simulator);
        client.addListener(new TrainClientListener() {
            @Override
            public void onConnected(String serverAddress) {
                System.out.println("[TRAIN] connected " + serverAddress);
                heartbeat.start();
            }

            @Override
            public void onDisconnected(String reason) {
                System.out.println("[TRAIN] " + reason);
                heartbeat.stop();
            }

            @Override
            public void onMessage(com.smartrail.common.protocol.Message message) {
                System.out.println("[TRAIN] <- " + message);
            }

            @Override
            public void onError(String error) {
                System.out.println("[TRAIN] " + error);
            }
        });

        // Alerts subscriber: prints to stdout so child JVM console streams alert fan-out
        MulticastAlertSubscriber alerts = new MulticastAlertSubscriber(config, alert -> {
            System.out.println("[ALERT] Received " + alert);
        });

        alerts.start();
        simulator.start();
        String host = config.get(ConfigKeys.CLIENT_SERVER_HOST);
        int port = transport == Transport.TLS
                ? config.getInt(ConfigKeys.TLS_PORT)
                : config.getInt(ConfigKeys.NIO_TCP_PORT);
        System.out.println("[TRAIN] " + trainId + " (" + train.name() + ") connecting to " + host + ":" + port
                + " (" + transport + ")");
        java.util.concurrent.CountDownLatch shutdown = new java.util.concurrent.CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            simulator.stop();
            client.disconnect();
            heartbeat.stop();
            alerts.stop();
            shutdown.countDown();
        }, "train-shutdown"));
        int delayMs = config.getInt(ConfigKeys.CLIENT_RECONNECT_DELAY_MS);
        try {
            while (shutdown.getCount() > 0) {
                if (!client.isConnected()) {
                    client.connect(host, port);
                }
                if (shutdown.await(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
