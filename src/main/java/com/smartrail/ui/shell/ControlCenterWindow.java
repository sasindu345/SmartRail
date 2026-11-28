package com.smartrail.ui.shell;

import com.smartrail.ui.components.NavSidebar;
import com.smartrail.ui.components.NetworkStatusBar;
import com.smartrail.ui.components.SimulationControlBar;
import com.smartrail.ui.components.TopBar;
import com.smartrail.ui.panels.AlertsPanel;
import com.smartrail.ui.panels.EventLogPanel;
import com.smartrail.ui.panels.NioServerPanel;
import com.smartrail.ui.panels.RmiHandlerPanel;
import com.smartrail.ui.panels.TlsResourcePanel;
import com.smartrail.ui.panels.TrackSectionsPanel;
import com.smartrail.ui.panels.TrainsPanel;
import com.smartrail.ui.panels.UdpMulticastPanel;
import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

/**
 * SmartRail Control Center: the single demo window.
 * Owner: Member 3 (control-center-spec.md §2-§4).
 *
 * <p>Hosts in-process central server services (real bound ports) and coordinates child
 * train client JVM processes.
 */
public final class ControlCenterWindow extends JFrame {

    private final SimulationController controller;
    private final TrainProcessSupervisor supervisor;

    private final TopBar topBar;
    private final NavSidebar navSidebar;
    private final SimulationControlBar controlBar;
    private final NetworkStatusBar statusBar;

    private final CardLayout cardLayout;
    private final JPanel contentCards;

    private NioServerPanel overview;
    private TrainsPanel trainsPanel;
    private AlertsPanel alertsPanel;

    public ControlCenterWindow(SimulationController controller, TrainProcessSupervisor supervisor) {
        super("SmartRail Control Center - Railway Traffic Coordination Simulator");
        this.controller = controller;
        this.supervisor = supervisor;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));
        setSize(1150, 780);
        setLocationRelativeTo(null);

        // Window closing hook
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                topBar.dispose();
                if (alertsPanel != null) {
                    alertsPanel.stopSubscription();
                }
                UiThreads.runInBackground(() -> {
                    supervisor.stopAllTrains();
                    controller.stopServer();
                });
            }
        });

        // 1. Top bar
        topBar = new TopBar();

        // 2. Navigation sidebar (left)
        cardLayout = new CardLayout();
        contentCards = new JPanel(cardLayout);
        contentCards.setOpaque(true);
        contentCards.setBackground(Theme.BG);
        contentCards.setBorder(BorderFactory.createEmptyBorder(Theme.PAD, Theme.PAD, Theme.PAD, Theme.PAD));

        navSidebar = new NavSidebar(screenName -> cardLayout.show(contentCards, screenName));

        // 3. Setup screens in CardLayout
        setupScreens();

        // 4. Simulation Control Bar
        List<String> trainIds = controller.railwayData().trains().stream()
                .map(train -> train.trainId())
                .toList();
        controlBar = new SimulationControlBar(new SimulationControlBar.ControlListener() {
            @Override
            public void onToggleServer() {
                handleToggleServer();
            }

            @Override
            public void onLaunchTrain(String trainId, boolean tls) {
                handleLaunchTrain(trainId, tls);
            }

            @Override
            public void onReset() {
                handleReset();
            }

            @Override
            public void onStopAll() {
                handleStopAll();
            }
        }, trainIds);

        // 5. Network Status Bar
        statusBar = new NetworkStatusBar();

        // Register listener for controller service status changes
        controller.addStatusListener(states -> UiThreads.onEdt(() -> {
            for (SimulationController.ServiceState state : states.values()) {
                statusBar.setServiceStatus(state.serviceKey(), state.status(), state.endpointOrError());
            }
            boolean isRunning = controller.isServerRunning();
            topBar.setSimulationOnline(isRunning);
            controlBar.setServerRunning(isRunning);
        }));

        // Layout assembly
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(navSidebar, BorderLayout.WEST);
        centerPanel.add(contentCards, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(controlBar, BorderLayout.NORTH);
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(topBar, BorderLayout.NORTH);
        getContentPane().add(centerPanel, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupScreens() {
        // Screen 1: Overview (M1 / UI-1)
        contentCards.add(createOverviewScreen(), NavSidebar.OVERVIEW);

        // Screen 2: Trains (M2 / UI-2) — hosts per-train streaming console tabs
        contentCards.add(createTrainsScreen(), NavSidebar.TRAINS);

        // Screen 3: Track Sections (M1 / UI-1)
        contentCards.add(createTrackSectionsScreen(), NavSidebar.TRACK_SECTIONS);

        // Screen 4: Alerts (M3 / UI-3)
        contentCards.add(createAlertsScreen(), NavSidebar.ALERTS);

        // Screen 5: Network (M3 + M2)
        contentCards.add(createNetworkScreen(), NavSidebar.NETWORK);

        // Screen 6: Security (M4 / UI-4)
        contentCards.add(createSecurityScreen(), NavSidebar.SECURITY);

        // Screen 7: Admin (M5 / UI-5)
        contentCards.add(createAdminScreen(), NavSidebar.ADMIN);

        // Screen 8: Event Log (M1 / UI-1)
        contentCards.add(createEventLogScreen(), NavSidebar.EVENT_LOG);
    }

    private JPanel createOverviewScreen() {
        overview = new NioServerPanel(controller);
        return overview;
    }

    private JPanel createTrainsScreen() {
        // M2 / UI-2 — TrainsPanel hosts the train table, TcpClientPanel, and per-train consoles
        trainsPanel = new TrainsPanel(controller, supervisor);
        return trainsPanel;
    }

    private JPanel createTrackSectionsScreen() {
        return new TrackSectionsPanel(controller);
    }

    private JPanel createAlertsScreen() {
        alertsPanel = new AlertsPanel(controller);
        return alertsPanel;
    }

    private JPanel createNetworkScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(new UdpMulticastPanel(controller, discovered -> {
            if (trainsPanel != null) {
                trainsPanel.useDiscoveredServer(discovered);
            }
        }), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSecurityScreen() {
        // M4 / UI-4 — TlsResourcePanel is the Security screen (control-center-spec.md §4.6)
        return new TlsResourcePanel(controller);
    }

    private JPanel createAdminScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        RmiHandlerPanel rmiPanel = new RmiHandlerPanel(controller.config());
        panel.add(rmiPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createEventLogScreen() {
        return new EventLogPanel(controller);
    }

    // ---- Handlers for control bar actions ----

    private void handleToggleServer() {
        controlBar.setActionsInFlight(true);
        UiThreads.runInBackground(() -> {
            try {
                if (controller.isServerRunning()) {
                    controller.stopServer();
                } else {
                    controller.startServer();
                }
            } finally {
                UiThreads.onEdt(() -> controlBar.setActionsInFlight(false));
            }
        });
    }

    private void handleLaunchTrain(String trainId, boolean tls) {
        String start = trainsPanel == null ? null : trainsPanel.chosenStartStation(trainId);
        String end = trainsPanel == null ? null : trainsPanel.chosenEndStation(trainId);
        controlBar.setActionsInFlight(true);
        UiThreads.runInBackground(() -> {
            try {
                if (start != null && end != null && !controller.chooseJourney(trainId, start, end)) {
                    if (trainsPanel != null) {
                        trainsPanel.appendSupervisorMessage(trainId, "[SUPERVISOR] No track from " + start + " to " + end);
                    }
                    return;
                }
                supervisor.startTrain(trainId, tls, start);
            } catch (IOException e) {
                if (trainsPanel != null) {
                    trainsPanel.appendSupervisorMessage(trainId, "[SUPERVISOR ERROR] Failed to spawn train: " + e.getMessage());
                }
            } finally {
                UiThreads.onEdt(() -> {
                    controlBar.setActionsInFlight(false);
                    if (trainsPanel != null) {
                        trainsPanel.selectTrain(trainId);
                    }
                    if (overview != null) {
                        overview.selectTrain(trainId);
                    }
                });
            }
        });
    }

    private void handleReset() {
        controlBar.setActionsInFlight(true);
        UiThreads.runInBackground(() -> {
            try {
                supervisor.stopAllTrains();
                controller.resetSimulation();
            } finally {
                UiThreads.onEdt(() -> controlBar.setActionsInFlight(false));
            }
        });
    }

    private void handleStopAll() {
        controlBar.setActionsInFlight(true);
        UiThreads.runInBackground(() -> {
            try {
                supervisor.stopAllTrains();
                controller.stopServer();
            } finally {
                UiThreads.onEdt(() -> controlBar.setActionsInFlight(false));
            }
        });
    }

    public SimulationController getController() {
        return controller;
    }

    public TrainProcessSupervisor getSupervisor() {
        return supervisor;
    }
}
