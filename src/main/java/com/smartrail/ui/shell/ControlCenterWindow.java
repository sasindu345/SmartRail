package com.smartrail.ui.shell;

import com.smartrail.common.model.RouteInfo;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.NavSidebar;
import com.smartrail.ui.components.NetworkStatusBar;
import com.smartrail.ui.components.SimulationControlBar;
import com.smartrail.ui.components.StatusPill;
import com.smartrail.ui.components.TopBar;
import com.smartrail.ui.panels.AlertsPanel;
import com.smartrail.ui.panels.EventLogPanel;
import com.smartrail.ui.panels.NioServerPanel;
import com.smartrail.ui.panels.RmiHandlerPanel;
import com.smartrail.ui.panels.TlsResourcePanel;
import com.smartrail.ui.panels.TrackSectionsPanel;
import com.smartrail.ui.panels.UdpMulticastPanel;
import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Per-train console areas and status cards for the Trains screen
    private final Map<String, JTextArea> trainConsoles = new ConcurrentHashMap<>();
    private final Map<String, TrainCard> trainCards = new ConcurrentHashMap<>();
    private NioServerPanel overview;
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

        // Register train output listener to stream into train consoles
        supervisor.addOutputListener((trainId, line) -> UiThreads.onEdt(() -> {
            appendTrainOutput(trainId, line);
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

        Timer trainCardsTimer = new Timer(400, e -> refreshTrainCards());
        trainCardsTimer.setInitialDelay(0);
        trainCardsTimer.start();
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
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.FONT_BODY);
        for (TrainDefinition train : controller.railwayData().trains()) {
            tabs.addTab(train.trainId(), createTrainTab(train));
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTrainTab(TrainDefinition train) {
        JPanel tab = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        tab.setOpaque(false);
        tab.add(createTrainCard(train), BorderLayout.NORTH);

        JLabel hidden = new JLabel("Heartbeats are hidden. This is what the train process received.");
        hidden.setFont(Theme.FONT_SMALL);
        hidden.setForeground(Theme.TEXT_MUTED);
        JPanel consoleBody = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        consoleBody.setOpaque(false);
        consoleBody.add(hidden, BorderLayout.NORTH);
        consoleBody.add(trainConsole(train.trainId()), BorderLayout.CENTER);

        Card messages = new Card("Messages");
        messages.setBody(consoleBody);
        tab.add(messages, BorderLayout.CENTER);
        return tab;
    }

    private JPanel createTrainCard(TrainDefinition train) {
        Card card = new Card(train.trainId() + "  " + train.name());
        StatusPill process = new StatusPill("Process stopped", Theme.INACTIVE);
        JLabel place = cardLine("Place  " + train.startStationId());
        JLabel address = cardLine("Address  not connected");
        JLabel route = cardLine("Route  " + routeChain(train.routeId()));
        JComboBox<String> from = stationCombo(train.startStationId());
        JComboBox<String> to = stationCombo(routeEnd(train.routeId()));
        JLabel last = cardLine("Last message  —");
        Runnable showRoute = () -> {
            String start = String.valueOf(from.getSelectedItem());
            String end = String.valueOf(to.getSelectedItem());
            List<String> sections = controller.sectionsBetween(start, end);
            route.setText(sections.isEmpty()
                    ? "Route  no track from " + start + " to " + end
                    : "Route  " + chain(sections));
        };
        from.addActionListener(e -> showRoute.run());
        to.addActionListener(e -> showRoute.run());
        showRoute.run();
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(aligned(process));
        body.add(Box.createVerticalStrut(Theme.GAP / 2));
        body.add(aligned(cardLine("From")));
        body.add(aligned(from));
        body.add(Box.createVerticalStrut(Theme.GAP / 2));
        body.add(aligned(cardLine("To")));
        body.add(aligned(to));
        body.add(Box.createVerticalStrut(Theme.GAP / 2));
        body.add(aligned(route));
        body.add(Box.createVerticalStrut(Theme.GAP / 2));
        body.add(aligned(place));
        body.add(Box.createVerticalStrut(Theme.GAP / 2));
        body.add(aligned(address));
        body.add(Box.createVerticalStrut(Theme.GAP / 2));
        body.add(aligned(last));
        card.setBody(body);
        trainCards.put(train.trainId(), new TrainCard(train, process, place, address, last, from, to));
        return card;
    }

    private JComboBox<String> stationCombo(String selected) {
        JComboBox<String> combo = new JComboBox<>();
        for (StationInfo station : controller.railwayData().stations()) {
            combo.addItem(station.stationId());
        }
        combo.setSelectedItem(selected);
        combo.setFont(Theme.FONT_BODY);
        combo.setMaximumSize(new Dimension(160, 28));
        return combo;
    }

    private static JComponent aligned(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static JLabel cardLine(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_BODY);
        label.setForeground(Theme.TEXT);
        return label;
    }

    private JScrollPane trainConsole(String trainId) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(Theme.FONT_MONO);
        area.setBackground(Theme.SURFACE);
        area.setForeground(Theme.TEXT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        trainConsoles.put(trainId, area);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        return scroll;
    }

    private void appendTrainOutput(String trainId, String line) {
        if (line.contains("HEARTBEAT")) {
            return;
        }
        JTextArea area = trainConsoles.get(trainId);
        if (area != null) {
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        }
        TrainCard card = trainCards.get(trainId);
        String event = protocolEvent(line);
        if (card != null && event != null) {
            card.last.setText("Last message  " + event);
        }
    }

    /** The reply worth showing on the card. Heartbeats never reach this method. */
    private static String protocolEvent(String line) {
        String[] types = {
                "EMERGENCY_ALERT", "EMERGENCY_STOP", "SECTION_DENIED", "SECTION_GRANTED",
                "REGISTER_OK", "ASSIGN_ROUTE", "HOLD", "RESUME"
        };
        for (String type : types) {
            if (line.contains(type)) {
                return type;
            }
        }
        int connected = line.indexOf("connected ");
        if (connected >= 0) {
            return line.substring(connected).trim();
        }
        return null;
    }

    private void refreshTrainCards() {
        Map<String, TrainInfo> live = Map.of();
        if (controller.isServerRunning() && controller.serverContext() != null) {
            live = new java.util.HashMap<>();
            for (TrainInfo train : controller.serverContext().railwayService().getTrains()) {
                live.put(train.trainId(), train);
            }
        }
        for (TrainCard card : trainCards.values()) {
            boolean running = supervisor.isTrainRunning(card.train.trainId());
            String processText = running ? "Process running" : "Process stopped";
            if (!processText.equals(card.process.getText())) {
                card.process.setStatus(processText, running ? Theme.OK : Theme.INACTIVE, Theme.SURFACE, Theme.TEXT);
            }
            TrainInfo info = live.get(card.train.trainId());
            String place = info == null || info.location() == null ? card.train.startStationId() : placeName(info.location());
            String status = info == null || info.status() == null ? "" : "  " + info.status().name();
            setCardLine(card.place, "Place  " + place + status);
            String address = info == null || info.remoteAddress() == null ? "not connected" : info.remoteAddress();
            setCardLine(card.address, "Address  " + address);
        }
    }

    private static void setCardLine(JLabel label, String text) {
        if (!text.equals(label.getText())) {
            label.setText(text);
        }
    }

    private String placeName(String location) {
        return controller.railwayData().section(location)
                .map(section -> section.fromStationId() + "→" + section.toStationId())
                .orElse(location);
    }

    private String routeEnd(String routeId) {
        for (RouteInfo route : controller.railwayData().routes()) {
            if (!route.routeId().equals(routeId) || route.sectionIds().isEmpty()) {
                continue;
            }
            String last = route.sectionIds().get(route.sectionIds().size() - 1);
            return controller.railwayData().section(last).map(section -> section.toStationId()).orElse(last);
        }
        return controller.railwayData().stations().isEmpty()
                ? ""
                : controller.railwayData().stations().get(0).stationId();
    }

    private String chain(List<String> sectionIds) {
        if (sectionIds.isEmpty()) {
            return "—";
        }
        var first = controller.railwayData().section(sectionIds.get(0));
        if (first.isEmpty()) {
            return "—";
        }
        StringBuilder text = new StringBuilder(first.get().fromStationId());
        for (String sectionId : sectionIds) {
            controller.railwayData().section(sectionId)
                    .ifPresent(section -> text.append(" → ").append(section.toStationId()));
        }
        return text.toString();
    }

    private String routeChain(String routeId) {
        for (RouteInfo route : controller.railwayData().routes()) {
            if (!route.routeId().equals(routeId)) {
                continue;
            }
            if (route.sectionIds().isEmpty()) {
                return "—";
            }
            var first = controller.railwayData().section(route.sectionIds().get(0));
            if (first.isEmpty()) {
                return "—";
            }
            StringBuilder chain = new StringBuilder(first.get().fromStationId());
            for (String sectionId : route.sectionIds()) {
                controller.railwayData().section(sectionId)
                        .ifPresent(section -> chain.append(" → ").append(section.toStationId()));
            }
            return chain.toString();
        }
        return "—";
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
        panel.add(new UdpMulticastPanel(controller, discovered -> { }), BorderLayout.CENTER);
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
        TrainCard card = trainCards.get(trainId);
        String start = card == null ? null : String.valueOf(card.from.getSelectedItem());
        String end = card == null ? null : String.valueOf(card.to.getSelectedItem());
        controlBar.setActionsInFlight(true);
        UiThreads.runInBackground(() -> {
            try {
                if (start != null && end != null && !controller.chooseJourney(trainId, start, end)) {
                    UiThreads.onEdt(() -> appendTrainOutput(trainId, "[SUPERVISOR] No track from " + start + " to " + end));
                    return;
                }
                supervisor.startTrain(trainId, tls, start);
            } catch (IOException e) {
                appendTrainOutput(trainId, "[SUPERVISOR ERROR] Failed to spawn train: " + e.getMessage());
            } finally {
                UiThreads.onEdt(() -> {
                    controlBar.setActionsInFlight(false);
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

    private static final class TrainCard {
        private final TrainDefinition train;
        private final StatusPill process;
        private final JLabel place;
        private final JLabel address;
        private final JLabel last;
        private final JComboBox<String> from;
        private final JComboBox<String> to;

        private TrainCard(TrainDefinition train, StatusPill process, JLabel place, JLabel address, JLabel last,
                          JComboBox<String> from, JComboBox<String> to) {
            this.train = train;
            this.process = process;
            this.place = place;
            this.address = address;
            this.last = last;
            this.from = from;
            this.to = to;
        }
    }
}
