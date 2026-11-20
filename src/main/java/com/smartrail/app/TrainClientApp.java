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
import com.smartrail.tls.TlsSocketFactory;
import com.smartrail.ui.shell.TrainClientWindow;

import javax.swing.SwingUtilities;

/**
 * SHARED launcher: one independent train client process.
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

        Transport transport = Transport.valueOf(config.get(ConfigKeys.CLIENT_TRANSPORT).toUpperCase());
        ClientSocketFactory sockets = transport == Transport.TLS
                ? new TlsSocketFactory(config)          // Member 4
                : new PlainSocketFactory();             // Member 2
        TrainClient client = new TrainClient(config, train, sockets);                       // Member 2

        HeartbeatSender heartbeat = new HeartbeatSender(config, trainId, client::send);     // Member 3
        client.addListener(new TrainClientListener() {
            @Override
            public void onConnected(String serverAddress) {
                heartbeat.start();
            }

            @Override
            public void onDisconnected(String reason) {
                heartbeat.stop();
            }
        });

        // Alerts subscriber: prints to stdout so child JVM console streams alert fan-out
        MulticastAlertSubscriber alerts = new MulticastAlertSubscriber(config, alert -> {
            System.out.println("[ALERT] Received " + alert);
        });

        if (config.getBoolean(ConfigKeys.UI_TRAIN_WINDOW_ENABLED)) {
            SwingUtilities.invokeLater(() -> new TrainClientWindow(config, client, alerts).setVisible(true));
        } else {
            alerts.start();
            System.out.println("[TRAIN] " + trainId + " (" + train.name() + ") running in background [headless]");
            java.util.concurrent.CountDownLatch shutdown = new java.util.concurrent.CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                alerts.stop();
                shutdown.countDown();
            }, "train-shutdown"));
            try {
                shutdown.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
