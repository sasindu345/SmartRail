package com.smartrail.rmi;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

/**
 * OWNER: Member 5 - CONCEPT: RMI client lookup.
 * <ul>
 *   <li>TODO(M5) LocateRegistry.getRegistry({@link ConfigKeys#CLIENT_SERVER_HOST}, {@link ConfigKeys#RMI_REGISTRY_PORT})
 *       .lookup({@link ConfigKeys#RMI_SERVICE_NAME})</li>
 *   <li>TODO(M5) callers handle RemoteException (server down) with a clear UI message</li>
 * </ul>
 */
public final class RmiAdminClient {

    private final NetworkConfig config;

    public RmiAdminClient(NetworkConfig config) {
        this.config = config;
    }

    public RailwayAdminRemote connect() throws RemoteException, NotBoundException {
        throw new UnsupportedOperationException("TODO(M5): RMI lookup of " + config.get(ConfigKeys.RMI_SERVICE_NAME));
    }
}
