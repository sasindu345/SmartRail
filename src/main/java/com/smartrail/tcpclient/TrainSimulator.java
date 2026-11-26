package com.smartrail.tcpclient;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OWNER: Member 2 - CONCEPT: client-side application logic driving TCP request/response.
 *
 * <p>Follows the server's {@code ASSIGN_ROUTE}. Each step asks for the next section, and only a
 * {@code SECTION_GRANTED} moves the train: it sends {@code POSITION|section|MOVING} and releases
 * the section it left. {@code SECTION_DENIED} keeps the train where it is and retries on the next
 * step. {@code HOLD}, {@code RESUME} and {@code EMERGENCY_STOP} change the reported status.
 * The step timer is {@code client.simulator.step.ms}. All of this runs off the Swing thread.
 */
public final class TrainSimulator implements TrainClientListener {

    private enum Phase {
        WAITING, REQUESTED, GRANTED, DENIED, ON_SECTION, HELD, EMERGENCY, ARRIVED
    }

    private final NetworkConfig config;
    private final TrainClient client;
    private final ScheduledExecutorService scheduler;

    private List<String> sections = List.of();
    private int nextIndex;
    private String heldSection;
    private String pendingSection;
    private Phase phase = Phase.WAITING;
    private Phase resumePhase = Phase.WAITING;
    private boolean started;

    public TrainSimulator(NetworkConfig config, TrainClient client) {
        this.config = config;
        this.client = client;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "train-simulator-" + client.train().trainId());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        long stepMs = config.getLong(ConfigKeys.CLIENT_SIMULATOR_STEP_MS);
        scheduler.scheduleWithFixedDelay(this::tick, stepMs, stepMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    @Override
    public void onMessage(Message message) {
        try {
            scheduler.execute(() -> handle(message));
        } catch (RejectedExecutionException ignored) {
            // stop() has already shut the simulator down.
        }
    }

    private void handle(Message message) {
        if (!message.field(0).equals(client.train().trainId())) {
            return;
        }
        switch (message.type()) {
            case ASSIGN_ROUTE -> {
                if (sections.isEmpty()) {
                    sections = List.of(message.field(2).split(","));
                    sendPosition(client.train().startStationId(), TrainStatus.WAITING);
                }
            }
            case SECTION_GRANTED -> {
                if (message.field(1).equals(pendingSection) && phase == Phase.REQUESTED) {
                    phase = Phase.GRANTED;
                }
            }
            case SECTION_DENIED -> {
                if (message.field(1).equals(pendingSection) && phase == Phase.REQUESTED) {
                    phase = Phase.DENIED;
                }
            }
            case HOLD -> {
                if (phase != Phase.HELD && phase != Phase.ARRIVED) {
                    resumePhase = phase;
                    phase = Phase.HELD;
                }
            }
            case RESUME -> {
                if (phase == Phase.HELD || phase == Phase.EMERGENCY) {
                    phase = resumePhase;
                }
            }
            case EMERGENCY_STOP -> {
                if (phase != Phase.ARRIVED) {
                    resumePhase = phase == Phase.HELD ? resumePhase : phase;
                    phase = Phase.EMERGENCY;
                }
            }
            default -> {
                // ACK and REGISTER_OK need no simulator action.
            }
        }
    }

    private void tick() {
        if (!client.isConnected() || phase == Phase.ARRIVED || phase == Phase.REQUESTED) {
            return;
        }
        switch (phase) {
            case EMERGENCY -> sendPosition(location(), TrainStatus.EMERGENCY_STOPPED);
            case HELD -> sendPosition(location(), TrainStatus.HELD);
            case DENIED -> {
                sendPosition(location(), TrainStatus.WAITING);
                phase = Phase.WAITING;
            }
            case GRANTED -> enterGrantedSection();
            case ON_SECTION -> phase = Phase.WAITING;
            case WAITING -> requestNext();
            default -> {
            }
        }
    }

    private void requestNext() {
        if (sections.isEmpty()) {
            return;
        }
        if (nextIndex >= sections.size()) {
            sendPosition(arrivalStation(), TrainStatus.ARRIVED);
            if (heldSection != null) {
                send(Message.of(MessageType.RELEASE_SECTION, client.train().trainId(), heldSection));
                heldSection = null;
            }
            phase = Phase.ARRIVED;
            return;
        }
        pendingSection = sections.get(nextIndex);
        phase = Phase.REQUESTED;
        if (!send(Message.of(MessageType.REQUEST_SECTION, client.train().trainId(), pendingSection))) {
            phase = Phase.WAITING;
        }
    }

    private void enterGrantedSection() {
        String previous = heldSection;
        String entered = pendingSection;
        heldSection = entered;
        nextIndex++;
        phase = Phase.ON_SECTION;
        sendPosition(entered, TrainStatus.MOVING);
        if (previous != null) {
            send(Message.of(MessageType.RELEASE_SECTION, client.train().trainId(), previous));
        }
    }

    private String location() {
        return heldSection != null ? heldSection : client.train().startStationId();
    }

    /** The station at the end of the last block, so an arrival is reported as a station. */
    private String arrivalStation() {
        if (heldSection == null) {
            return client.train().startStationId();
        }
        return RailwayData.load(config).section(heldSection)
                .map(section -> section.toStationId())
                .orElse(client.train().startStationId());
    }

    private void sendPosition(String where, TrainStatus status) {
        send(Message.of(MessageType.POSITION, client.train().trainId(), where, status.name()));
    }

    private boolean send(Message message) {
        try {
            client.send(message);
            return true;
        } catch (RuntimeException ignored) {
            // The next step retries. A dropped socket is TrainClient's reconnect.
            return false;
        }
    }
}
