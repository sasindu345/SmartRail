package com.smartrail.tcpclient;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

/**
 * OWNER: Member 2 - CONCEPT: client-side application logic driving TCP request/response.
 *
 * <p>Simulates a train following its ASSIGN_ROUTE: for each section REQUEST_SECTION, wait for
 * GRANTED/DENIED, send POSITION|..|MOVING, then RELEASE_SECTION the previous one; obey HOLD / RESUME /
 * EMERGENCY_STOP. Step interval {@link ConfigKeys#CLIENT_SIMULATOR_STEP_MS}.
 * <ul>
 *   <li>TODO(M2) state machine (WAITING, MOVING, HELD, EMERGENCY_STOPPED, ARRIVED)</li>
 *   <li>TODO(M2) retry a DENIED section after one step</li>
 *   <li>TODO(M2) run on a ScheduledExecutorService, never on the EDT</li>
 * </ul>
 */
public final class TrainSimulator implements TrainClientListener {

    private final NetworkConfig config;
    private final TrainClient client;

    public TrainSimulator(NetworkConfig config, TrainClient client) {
        this.config = config;
        this.client = client;
    }

    public void start() {
        throw new UnsupportedOperationException("TODO(M2): simulate " + client.train().trainId());
    }

    public void stop() {
        // TODO(M2)
    }
}
