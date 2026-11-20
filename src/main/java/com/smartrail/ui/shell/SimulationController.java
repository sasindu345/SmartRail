package com.smartrail.ui.shell;

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
import com.smartrail.ui.components.NetworkStatusBar;
import com.smartrail.urlconnection.ResourceHttpServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Manages the lifecycle of in-process server services for the Control Center.
 * Owner: Member 3 (control-center-spec.md §2-§3).
 *
 * <p>Reuses the exact service wiring from {@code ServerApp.java}, starts real bound ports,
 * and records per-service state (STARTED, FAILED, STOPPED) to feed {@link NetworkStatusBar}.
 */
public final class SimulationController {

    public record ServiceState(
            String serviceKey,
            NetworkStatusBar.ServiceStatus status,
            String endpointOrError
    ) {}

    public interface StatusListener {
        void onServiceStatusChanged(Map<String, ServiceState> states);
    }

    private final NetworkConfig config;
    private final RailwayData data;
    private final EventLog log;

    private MulticastAlertPublisher alerts;
    private RailwayServiceImpl railway;
    private HeartbeatMonitor heartbeat;
    private TrainMessageRouter router;
    private ServerContext ctx;

    private final List<NetworkService> services = new ArrayList<>();
    private final Map<String, ServiceState> serviceStates = new LinkedHashMap<>();
    private final List<StatusListener> statusListeners = new CopyOnWriteArrayList<>();

    private boolean running = false;

    public SimulationController(NetworkConfig config) {
        this.config = config != null ? config : NetworkConfig.load();
        this.data = RailwayData.load(this.config);
        this.log = new EventLog(this.config.getInt(ConfigKeys.LOG_CAPACITY));
        this.log.log("SERVER", "SmartRail educational simulator - Control Center initialized");

        initDefaultStates();

        // Ensure in-process services are stopped on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer, "simulation-controller-cleaner"));
    }

    private void initDefaultStates() {
        String[] keys = {
                NetworkStatusBar.SERVICE_TCP_NIO,
                NetworkStatusBar.SERVICE_UDP,
                NetworkStatusBar.SERVICE_TLS,
                NetworkStatusBar.SERVICE_MULTICAST,
                NetworkStatusBar.SERVICE_RMI,
                NetworkStatusBar.SERVICE_HTTP
        };
        for (String key : keys) {
            serviceStates.put(key, new ServiceState(key, NetworkStatusBar.ServiceStatus.STOPPED, null));
        }
    }

    public void addStatusListener(StatusListener listener) {
        if (listener != null) {
            statusListeners.add(listener);
            listener.onServiceStatusChanged(Collections.unmodifiableMap(new LinkedHashMap<>(serviceStates)));
        }
    }

    /**
     * Start the in-process server services. Real ports are bound.
     */
    public synchronized void startServer() {
        if (running) {
            return;
        }

        log.log("SERVER", "Starting in-process central server services...");

        // Recreate services for clean restart
        alerts = new MulticastAlertPublisher(config, log);
        railway = new RailwayServiceImpl(data, alerts, log);
        alerts.bindRailwayService(railway);
        heartbeat = new HeartbeatMonitor(config, railway, log);
        router = new TrainMessageRouter(railway, heartbeat, log);
        ctx = new ServerContext(config, data, log, railway, router, alerts, heartbeat);

        services.clear();
        Map<NetworkService, String> serviceKeyMap = new LinkedHashMap<>();

        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.NIO_TCP_ENABLED,
                NetworkStatusBar.SERVICE_TCP_NIO, () -> new NioTrainServer(ctx));
        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.TLS_ENABLED,
                NetworkStatusBar.SERVICE_TLS, () -> new TlsTrainServer(ctx));
        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.UDP_DISCOVERY_ENABLED,
                NetworkStatusBar.SERVICE_UDP, () -> new DiscoveryResponder(ctx));
        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.MULTICAST_ENABLED,
                NetworkStatusBar.SERVICE_MULTICAST, () -> alerts);
        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.HEARTBEAT_ENABLED,
                null, () -> heartbeat);
        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.HTTP_RESOURCE_ENABLED,
                NetworkStatusBar.SERVICE_HTTP, () -> new ResourceHttpServer(ctx));
        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.SMARTRAIL_RESOURCE_ENABLED,
                null, () -> new SmartRailResourceServer(ctx));
        addIfEnabled(services, serviceKeyMap, config, ConfigKeys.RMI_ENABLED,
                NetworkStatusBar.SERVICE_RMI, () -> new RmiAdminServer(ctx));

        for (NetworkService service : services) {
            String key = serviceKeyMap.get(service);
            try {
                service.start();
                log.log("SERVER", "STARTED " + service.name() + " -> " + service.endpoint());
                if (key != null) {
                    serviceStates.put(key, new ServiceState(key, NetworkStatusBar.ServiceStatus.STARTED, service.endpoint()));
                }
            } catch (Exception e) {
                log.log("SERVER", "NOT AVAILABLE " + service.name() + " (" + e.getMessage() + ")");
                if (key != null) {
                    serviceStates.put(key, new ServiceState(key, NetworkStatusBar.ServiceStatus.FAILED, e.getMessage()));
                }
            }
        }

        running = true;
        notifyListeners();
    }

    /**
     * Stop all running server services.
     */
    public synchronized void stopServer() {
        if (!running && services.isEmpty()) {
            return;
        }

        log.log("SERVER", "Stopping in-process server services...");
        for (NetworkService s : services) {
            try {
                s.stop();
                log.log("SERVER", "STOPPED " + s.name());
            } catch (Exception ignored) {
            }
        }
        services.clear();
        running = false;

        initDefaultStates();
        notifyListeners();
    }

    public synchronized boolean isServerRunning() {
        return running;
    }

    public NetworkConfig config() {
        return config;
    }

    public RailwayData railwayData() {
        return data;
    }

    public EventLog eventLog() {
        return log;
    }

    public ServerContext serverContext() {
        return ctx;
    }

    public List<NetworkService> services() {
        return Collections.unmodifiableList(services);
    }

    public Map<String, ServiceState> getServiceStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(serviceStates));
    }

    private void notifyListeners() {
        Map<String, ServiceState> snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(serviceStates));
        for (StatusListener l : statusListeners) {
            try {
                l.onServiceStatusChanged(snapshot);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void addIfEnabled(List<NetworkService> services, Map<NetworkService, String> keyMap,
                                     NetworkConfig config, String enabledKey, String uiKey,
                                     Supplier<NetworkService> factory) {
        if (config.getBoolean(enabledKey)) {
            NetworkService service = factory.get();
            services.add(service);
            if (uiKey != null) {
                keyMap.put(service, uiKey);
            }
        }
    }
}
