package com.smartrail.rmi;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;

/**
 * OWNER: Member 5 - CONCEPT: RMI registry + exporting a remote object.
 *
 * <p>APIs: {@code LocateRegistry.createRegistry}, {@code UnicastRemoteObject.exportObject},
 * {@code Registry.rebind/unbind}.
 * <ul>
 *   <li>Sets {@code java.rmi.server.hostname} from {@link ConfigKeys#SERVER_ADVERTISED_HOST} when set (LAN demo)</li>
 *   <li>createRegistry({@link ConfigKeys#RMI_REGISTRY_PORT}); export RailwayAdminService on
 *       {@link ConfigKeys#RMI_SERVICE_PORT}; rebind as {@link ConfigKeys#RMI_SERVICE_NAME}</li>
 *   <li>stop(): unbind + unexportObject(force=true)</li>
 * </ul>
 */
public final class RmiAdminServer implements NetworkService {

    private final ServerContext ctx;
    private Registry registry;
    private RailwayAdminService service;
    private RailwayAdminRemote stub;
    private boolean registryCreatedByUs = false;

    public RmiAdminServer(ServerContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public String name() {
        return "RMI admin service";
    }

    @Override
    public String endpoint() {
        return "RMI registry *:" + ctx.config().getInt(ConfigKeys.RMI_REGISTRY_PORT) + "/" + ctx.config().get(ConfigKeys.RMI_SERVICE_NAME)
                + " (object port " + ctx.config().getInt(ConfigKeys.RMI_SERVICE_PORT) + ")";
    }

    @Override
    public synchronized void start() throws Exception {
        ctx.config().find(ConfigKeys.SERVER_ADVERTISED_HOST).ifPresent(host -> {
            if (!host.isBlank()) {
                System.setProperty("java.rmi.server.hostname", host);
            }
        });

        int registryPort = ctx.config().getInt(ConfigKeys.RMI_REGISTRY_PORT);
        try {
            registry = LocateRegistry.createRegistry(registryPort);
            registryCreatedByUs = true;
        } catch (ExportException e) {
            registry = LocateRegistry.getRegistry(registryPort);
            registryCreatedByUs = false;
        }

        service = new RailwayAdminService(ctx.railwayService(), ctx.eventLog());
        int servicePort = ctx.config().getInt(ConfigKeys.RMI_SERVICE_PORT);
        stub = (RailwayAdminRemote) UnicastRemoteObject.exportObject(service, servicePort);

        String serviceName = ctx.config().get(ConfigKeys.RMI_SERVICE_NAME);
        registry.rebind(serviceName, stub);
    }

    @Override
    public synchronized void stop() {
        String serviceName = ctx.config().get(ConfigKeys.RMI_SERVICE_NAME);
        if (registry != null) {
            try {
                registry.unbind(serviceName);
            } catch (Exception ignored) {
            }
        }
        if (service != null) {
            try {
                UnicastRemoteObject.unexportObject(service, true);
            } catch (Exception ignored) {
            }
            service = null;
            stub = null;
        }
        if (registry != null && registryCreatedByUs) {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (Exception ignored) {
            }
            registry = null;
            registryCreatedByUs = false;
        }
    }
}
