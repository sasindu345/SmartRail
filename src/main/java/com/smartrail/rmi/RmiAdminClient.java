package com.smartrail.rmi;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Objects;

/**
 * OWNER: Member 5 - CONCEPT: RMI client lookup.
 * <ul>
 *   <li>LocateRegistry.getRegistry(client.server.host, rmi.registry.port).lookup(rmi.service.name)</li>
 *   <li>Callers handle RemoteException (server down) with a clear UI message</li>
 * </ul>
 */
public final class RmiAdminClient {

    private final NetworkConfig config;

    public RmiAdminClient(NetworkConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public RailwayAdminRemote connect() throws RemoteException, NotBoundException {
        return connect(config.get(ConfigKeys.CLIENT_SERVER_HOST), config.getInt(ConfigKeys.RMI_REGISTRY_PORT));
    }

    public RailwayAdminRemote connect(String host, int port) throws RemoteException, NotBoundException {
        Objects.requireNonNull(host, "host");
        String serviceName = config.get(ConfigKeys.RMI_SERVICE_NAME);
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (RailwayAdminRemote) registry.lookup(serviceName);
    }
}
