package com.smartrail.rmi;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;

/**
 * OWNER: Member 5 - CONCEPT: RMI registry + exporting a remote object.
 *
 * <p>APIs: {@code LocateRegistry.createRegistry}, {@code UnicastRemoteObject.exportObject},
 * {@code Registry.rebind/unbind}.
 * <ul>
 *   <li>TODO(M5) set {@code java.rmi.server.hostname} from {@link ConfigKeys#SERVER_ADVERTISED_HOST} when set (LAN demo)</li>
 *   <li>TODO(M5) createRegistry({@link ConfigKeys#RMI_REGISTRY_PORT}); export RailwayAdminService on
 *       {@link ConfigKeys#RMI_SERVICE_PORT}; rebind as {@link ConfigKeys#RMI_SERVICE_NAME}</li>
 *   <li>TODO(M5) stop(): unbind + unexportObject(force=true)</li>
 * </ul>
 */
public final class RmiAdminServer implements NetworkService {

    private final ServerContext ctx;

    public RmiAdminServer(ServerContext ctx) {
        this.ctx = ctx;
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
    public void start() throws Exception {
        throw new UnsupportedOperationException("TODO(M5): RMI registry + export");
    }

    @Override
    public void stop() {
        // TODO(M5): unbind and unexport
    }
}
