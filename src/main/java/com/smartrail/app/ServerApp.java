package com.smartrail.app;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;
import com.smartrail.coordination.RailwayServiceImpl;
import com.smartrail.coordination.TrainMessageRouter;
import com.smartrail.heartbeat.HeartbeatMonitor;
import com.smartrail.multicast.MulticastAlertPublisher;
import com.smartrail.nio.NioTrainServer;
import com.smartrail.protocolhandler.SmartRailResourceServer;
import com.smartrail.rmi.RmiAdminServer;
import com.smartrail.tls.TlsTrainServer;
import com.smartrail.udp.DiscoveryResponder;
import com.smartrail.urlconnection.ResourceHttpServer;
import com.smartrail.ui.shell.ServerWindow;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * SHARED launcher: the central railway control server. Wires every member's services through the
 * shared interfaces and starts each one independently - a service that is not implemented yet (or fails)
 * is logged as NOT AVAILABLE and the rest keep running. Members should not need to edit this file.
 *
 * <p>Run: {@code scripts/run.sh server}
 */
public final class ServerApp {

    private ServerApp() {
    }

    public static void main(String[] args) throws InterruptedException {
        NetworkConfig config = NetworkConfig.load();
        RailwayData data = RailwayData.load(config);
        EventLog log = new EventLog(config.getInt(ConfigKeys.LOG_CAPACITY));
        log.log("SERVER", "SmartRail educational simulator - not for real railway control");

        MulticastAlertPublisher alerts = new MulticastAlertPublisher(config, log);           // Member 3
        RailwayServiceImpl railway = new RailwayServiceImpl(data, alerts, log);               // Member 1
        alerts.bindRailwayService(railway);
        HeartbeatMonitor heartbeat = new HeartbeatMonitor(config, railway, log);              // Member 3
        TrainMessageRouter router = new TrainMessageRouter(railway, heartbeat, log);          // Member 1
        ServerContext ctx = new ServerContext(config, data, log, railway, router, alerts, heartbeat);

        List<NetworkService> services = new ArrayList<>();
        addIfEnabled(services, config, ConfigKeys.NIO_TCP_ENABLED, () -> new NioTrainServer(ctx));             // M1
        addIfEnabled(services, config, ConfigKeys.TLS_ENABLED, () -> new TlsTrainServer(ctx));                 // M4
        addIfEnabled(services, config, ConfigKeys.UDP_DISCOVERY_ENABLED, () -> new DiscoveryResponder(ctx));   // M3
        addIfEnabled(services, config, ConfigKeys.MULTICAST_ENABLED, () -> alerts);                           // M3
        addIfEnabled(services, config, ConfigKeys.HEARTBEAT_ENABLED, () -> heartbeat);                        // M3
        addIfEnabled(services, config, ConfigKeys.HTTP_RESOURCE_ENABLED, () -> new ResourceHttpServer(ctx));   // M4
        addIfEnabled(services, config, ConfigKeys.SMARTRAIL_RESOURCE_ENABLED, () -> new SmartRailResourceServer(ctx)); // M5
        addIfEnabled(services, config, ConfigKeys.RMI_ENABLED, () -> new RmiAdminServer(ctx));                 // M5

        for (NetworkService service : services) {
            try {
                service.start();
                log.log("SERVER", "STARTED " + service.name() + " -> " + service.endpoint());
            } catch (Exception e) {
                log.log("SERVER", "NOT AVAILABLE " + service.name() + " (" + e.getMessage() + ")");
            }
        }

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            services.forEach(NetworkService::stop);
            shutdown.countDown();
        }, "shutdown"));

        if (!GraphicsEnvironment.isHeadless() && config.getBoolean(ConfigKeys.UI_SERVER_WINDOW_ENABLED)) {
            SwingUtilities.invokeLater(() -> new ServerWindow(ctx, services).setVisible(true));
        }
        shutdown.await();
    }

    private static void addIfEnabled(List<NetworkService> services, NetworkConfig config, String enabledKey,
                                     Supplier<NetworkService> factory) {
        if (config.getBoolean(enabledKey)) {
            services.add(factory.get());
        }
    }
}
