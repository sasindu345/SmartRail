package com.smartrail.ui.panels;

import com.smartrail.common.model.RouteInfo;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.StatusPill;
import com.smartrail.ui.components.TableStyles;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.TrainProcessSupervisor;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OWNER: Member 2 — Trains screen &amp; per-train process consoles (UI-2).
 *
 * <p>Displays the live train table (process state, status, location, route, transport, remote IP:port,
 * last seen), per-train start/stop controls, journey selection, the {@link TcpClientPanel} for
 * {@code InetAddress} resolution and error checks, and side-by-side consoles streaming each child
 * JVM's stdout through {@link TrainProcessSupervisor}.
 */
public final class TrainsPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SimulationController controller;
    private final TrainProcessSupervisor supervisor;
    private final TcpClientPanel connectionPanel;

    private final DefaultTableModel trainsModel = new DefaultTableModel(
            new String[]{"Train", "Process", "Status", "Location", "Route", "Transport", "Remote IP:Port", "Last Seen"},
            0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable trainsTable = new JTable(trainsModel);

    private final JComboBox<String> fromCombo = new JComboBox<>();
    private final JComboBox<String> toCombo = new JComboBox<>();
    private final JLabel routePreview = new JLabel("Route: —");
    private final JButton startPlainBtn = new JButton("Start");
    private final JButton startTlsBtn = new JButton("Start (TLS)");
    private final JButton stopTrainBtn = new JButton("Stop");

    private final List<TrainDefinition> definitions;
    private final Map<String, String[]> selectedJourneys = new ConcurrentHashMap<>();
    private final Map<String, TrainConsoleCard> consoleCards = new LinkedHashMap<>();
    private final Map<String, String> resolvedAddresses = new ConcurrentHashMap<>();

    private String selectedTrainId;
    private Map<String, TrainInfo> latestLive = Map.of();

    public TrainsPanel(SimulationController controller, TrainProcessSupervisor supervisor) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.controller = controller;
        this.supervisor = supervisor;
        this.definitions = controller.railwayData().trains();
        this.connectionPanel = new TcpClientPanel(controller.config());
        setOpaque(false);

        initStationsAndJourneys();
        initTable();
        initControls();

        JPanel topSplit = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        topSplit.setOpaque(false);
        topSplit.add(buildTableCard(), BorderLayout.CENTER);
        connectionPanel.setPreferredSize(new Dimension(410, 230));
        topSplit.add(connectionPanel, BorderLayout.EAST);

        Card consolesCard = buildConsolesCard();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, consolesCard);
        splitPane.setResizeWeight(0.36);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOpaque(false);
        add(splitPane, BorderLayout.CENTER);

        supervisor.addOutputListener((trainId, line) -> UiThreads.onEdt(() -> appendTrainOutput(trainId, line)));
        supervisor.addLifecycleListener(new TrainProcessSupervisor.TrainLifecycleListener() {
            @Override
            public void onStarted(String trainId) {
                resolvedAddresses.remove(trainId);
                UiThreads.onEdt(() -> {
                    TrainConsoleCard card = consoleCards.get(trainId);
                    if (card != null) {
                        card.processPill.setStatus("Running", Theme.OK, Theme.SURFACE, Theme.TEXT);
                        card.resolvedLabel.setText("Resolved: resolving...");
                    }
                    refresh();
                });
            }

            @Override
            public void onStopped(String trainId, int exitCode) {
                UiThreads.onEdt(() -> refresh());
            }
        });

        refresh();
        if (!definitions.isEmpty()) {
            selectTrain(definitions.get(0).trainId());
        }

        Timer timer = new Timer(400, e -> refresh());
        timer.setInitialDelay(400);
        timer.start();
    }

    private void initStationsAndJourneys() {
        List<StationInfo> stations = controller.railwayData().stations();
        for (StationInfo station : stations) {
            fromCombo.addItem(station.stationId());
            toCombo.addItem(station.stationId());
        }
        for (TrainDefinition def : definitions) {
            selectedJourneys.put(def.trainId(), new String[]{def.startStationId(), routeEnd(def.routeId())});
        }
    }

    private void initTable() {
        TableStyles.apply(trainsTable);
        trainsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trainsTable.getColumnModel().getColumn(1).setCellRenderer(TableStyles.statusDotRenderer());
        trainsTable.getColumnModel().getColumn(2).setCellRenderer(TableStyles.statusDotRenderer());
        trainsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = trainsTable.getSelectedRow();
                if (row >= 0 && row < definitions.size()) {
                    selectTrain(definitions.get(row).trainId());
                }
            }
        });
    }

    private void initControls() {
        fromCombo.setFont(Theme.FONT_BODY);
        toCombo.setFont(Theme.FONT_BODY);
        routePreview.setFont(Theme.FONT_SMALL);
        routePreview.setForeground(Theme.TEXT_MUTED);
        startPlainBtn.setFont(Theme.FONT_BODY_BOLD);
        startTlsBtn.setFont(Theme.FONT_BODY);
        stopTrainBtn.setFont(Theme.FONT_BODY);
        stopTrainBtn.setForeground(Theme.DANGER);

        fromCombo.addActionListener(e -> updateSelectedJourney());
        toCombo.addActionListener(e -> updateSelectedJourney());

        startPlainBtn.addActionListener(e -> {
            if (selectedTrainId != null) {
                launchTrain(selectedTrainId, false);
            }
        });
        startTlsBtn.addActionListener(e -> {
            if (selectedTrainId != null) {
                launchTrain(selectedTrainId, true);
            }
        });
        stopTrainBtn.addActionListener(e -> {
            if (selectedTrainId != null) {
                stopTrain(selectedTrainId);
            }
        });
    }

    private Card buildTableCard() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        toolbar.setOpaque(false);
        toolbar.add(startPlainBtn);
        toolbar.add(startTlsBtn);
        toolbar.add(stopTrainBtn);
        toolbar.add(label("From"));
        toolbar.add(fromCombo);
        toolbar.add(label("To"));
        toolbar.add(toCombo);
        toolbar.add(routePreview);

        JScrollPane tableScroll = new JScrollPane(trainsTable);
        tableScroll.setPreferredSize(new Dimension(520, 145));

        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(tableScroll, BorderLayout.CENTER);
        body.add(toolbar, BorderLayout.SOUTH);

        Card card = new Card("Trains");
        card.setBody(body);
        return card;
    }

    private Card buildConsolesCard() {
        int cols = definitions.size() <= 1 ? 1 : 2;
        JPanel grid = new JPanel(new GridLayout(0, cols, Theme.GAP, Theme.GAP));
        grid.setOpaque(false);
        for (TrainDefinition def : definitions) {
            TrainConsoleCard consoleCard = createConsoleCard(def);
            consoleCards.put(def.trainId(), consoleCard);
            grid.add(consoleCard.card);
        }

        JLabel caption = new JLabel("Heartbeats are hidden. Each console streams one independent train JVM process.");
        caption.setFont(Theme.FONT_SMALL);
        caption.setForeground(Theme.TEXT_MUTED);

        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(caption, BorderLayout.NORTH);
        body.add(grid, BorderLayout.CENTER);

        Card wrapper = new Card("Per-Train Consoles");
        wrapper.setBody(body);
        return wrapper;
    }

    private TrainConsoleCard createConsoleCard(TrainDefinition def) {
        Card card = new Card(def.trainId() + " — " + def.name());
        StatusPill processPill = new StatusPill("Stopped", Theme.INACTIVE);
        JLabel resolvedLabel = new JLabel("Resolved: —");
        resolvedLabel.setFont(Theme.FONT_SMALL);
        resolvedLabel.setForeground(Theme.TEXT_MUTED);

        JButton startBtn = new JButton("Start");
        JButton tlsBtn = new JButton("TLS");
        JButton stopBtn = new JButton("Stop");
        JButton clearBtn = new JButton("Clear");
        startBtn.setFont(Theme.FONT_SMALL_BOLD);
        tlsBtn.setFont(Theme.FONT_SMALL);
        stopBtn.setFont(Theme.FONT_SMALL);
        stopBtn.setForeground(Theme.DANGER);
        clearBtn.setFont(Theme.FONT_SMALL);

        JTextArea consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(Theme.FONT_MONO);
        consoleArea.setBackground(Theme.SURFACE);
        consoleArea.setForeground(Theme.TEXT);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);

        startBtn.addActionListener(e -> launchTrain(def.trainId(), false));
        tlsBtn.addActionListener(e -> launchTrain(def.trainId(), true));
        stopBtn.addActionListener(e -> stopTrain(def.trainId()));
        clearBtn.addActionListener(e -> consoleArea.setText(""));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP / 2, 0));
        actions.setOpaque(false);
        actions.add(startBtn);
        actions.add(tlsBtn);
        actions.add(stopBtn);
        actions.add(clearBtn);

        JPanel header = new JPanel(new BorderLayout(Theme.GAP, 0));
        header.setOpaque(false);
        header.add(processPill, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP / 2));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(resolvedLabel, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(consoleArea);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP / 2));
        body.setOpaque(false);
        body.add(top, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        card.setBody(body);

        return new TrainConsoleCard(def, card, processPill, resolvedLabel, consoleArea);
    }

    /** Returns the operator-selected start station for {@code trainId}. */
    public String chosenStartStation(String trainId) {
        String[] pair = selectedJourneys.get(trainId);
        return pair == null ? null : pair[0];
    }

    /** Returns the operator-selected destination station for {@code trainId}. */
    public String chosenEndStation(String trainId) {
        String[] pair = selectedJourneys.get(trainId);
        return pair == null ? null : pair[1];
    }

    /** Appends a supervisor status or error line directly into that train's console. */
    public void appendSupervisorMessage(String trainId, String line) {
        UiThreads.onEdt(() -> appendTrainOutput(trainId, line));
    }

    /** Highlights the row and connection details for {@code trainId}. */
    public void selectTrain(String trainId) {
        selectedTrainId = trainId;
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).trainId().equals(trainId)) {
                if (i < trainsTable.getRowCount() && trainsTable.getSelectedRow() != i) {
                    trainsTable.setRowSelectionInterval(i, i);
                }
                break;
            }
        }
        String[] journey = selectedJourneys.get(trainId);
        if (journey != null) {
            fromCombo.setSelectedItem(journey[0]);
            toCombo.setSelectedItem(journey[1]);
        }
        updateRoutePreview();
        updateConnectionCard();
    }

    /** Forwards a discovered server to the embedded {@link TcpClientPanel}. */
    public void useDiscoveredServer(com.smartrail.udp.DiscoveredServer server) {
        connectionPanel.useDiscoveredServer(server);
    }

    private void launchTrain(String trainId, boolean tls) {
        selectTrain(trainId);
        String start = chosenStartStation(trainId);
        String end = chosenEndStation(trainId);
        UiThreads.runInBackground(() -> {
            try {
                if (start != null && end != null && !controller.chooseJourney(trainId, start, end)) {
                    appendSupervisorMessage(trainId, "[SUPERVISOR] No track from " + start + " to " + end);
                    return;
                }
                supervisor.startTrain(trainId, tls, start);
            } catch (IOException e) {
                appendSupervisorMessage(trainId, "[SUPERVISOR ERROR] Failed to spawn train: " + e.getMessage());
            }
        });
    }

    private void stopTrain(String trainId) {
        UiThreads.runInBackground(() -> supervisor.stopTrain(trainId));
    }

    private void appendTrainOutput(String trainId, String line) {
        if (line == null || line.contains("HEARTBEAT")) {
            return;
        }
        TrainConsoleCard card = consoleCards.get(trainId);
        if (card != null) {
            card.console.append(line + "\n");
            card.console.setCaretPosition(card.console.getDocument().getLength());
        }
        String resolved = extractResolved(line);
        if (resolved != null && (line.contains("resolved ") || !resolvedAddresses.containsKey(trainId))) {
            resolvedAddresses.put(trainId, resolved);
            if (card != null) {
                card.resolvedLabel.setText("Resolved: " + resolved);
            }
            if (trainId.equals(selectedTrainId)) {
                updateConnectionCard();
            }
        }
        if (card != null && isErrorLine(line)) {
            card.processPill.setStatus("Error", Theme.DANGER, Theme.SURFACE, Theme.TEXT);
        }
    }

    private void refresh() {
        Map<String, TrainInfo> live = new HashMap<>();
        if (controller.isServerRunning() && controller.serverContext() != null) {
            for (TrainInfo info : controller.serverContext().railwayService().getTrains()) {
                live.put(info.trainId(), info);
            }
        }
        latestLive = live;

        if (trainsModel.getRowCount() != definitions.size()) {
            trainsModel.setRowCount(0);
            for (TrainDefinition def : definitions) {
                trainsModel.addRow(buildRow(def, live.get(def.trainId())));
            }
        } else {
            for (int i = 0; i < definitions.size(); i++) {
                Object[] next = buildRow(definitions.get(i), live.get(definitions.get(i).trainId()));
                for (int col = 0; col < next.length; col++) {
                    if (!next[col].equals(trainsModel.getValueAt(i, col))) {
                        trainsModel.setValueAt(next[col], i, col);
                    }
                }
            }
        }

        for (TrainConsoleCard card : consoleCards.values()) {
            boolean running = supervisor.isTrainRunning(card.definition.trainId());
            TrainInfo info = live.get(card.definition.trainId());
            String pillText = processStatusLabel(running, info);
            if (!pillText.equals(card.processPill.getText()) && !"Error".equals(card.processPill.getText())) {
                card.processPill.setStatus(pillText, running ? Theme.OK : Theme.INACTIVE, Theme.SURFACE, Theme.TEXT);
            } else if (running && "Error".equals(card.processPill.getText()) && info != null
                    && info.remoteAddress() != null) {
                card.processPill.setStatus(pillText, Theme.OK, Theme.SURFACE, Theme.TEXT);
            } else if (!running && !"Stopped".equals(card.processPill.getText())) {
                card.processPill.setStatus("Stopped", Theme.INACTIVE, Theme.SURFACE, Theme.TEXT);
            }
        }

        updateConnectionCard();
    }

    private Object[] buildRow(TrainDefinition def, TrainInfo info) {
        boolean running = supervisor.isTrainRunning(def.trainId());
        String processText = running ? "Running" : "Stopped";
        String statusText = info != null && info.status() != null ? info.status().name() : "INACTIVE";
        String location = info != null && info.location() != null
                ? placeName(info.location())
                : def.startStationId();
        String route = info != null && info.routeId() != null ? info.routeId() : def.routeId();
        String transport = info != null && info.transport() != null ? info.transport().name() : "—";
        String remote = info != null && info.remoteAddress() != null ? info.remoteAddress() : "—";
        String lastSeen = info != null && info.lastSeenMillis() > 0
                ? TIME_FORMAT.format(Instant.ofEpochMilli(info.lastSeenMillis()))
                : "—";
        return new Object[]{def.trainId(), processText, statusText, location, route, transport, remote, lastSeen};
    }

    private void updateSelectedJourney() {
        if (selectedTrainId == null || fromCombo.getSelectedItem() == null || toCombo.getSelectedItem() == null) {
            return;
        }
        String start = String.valueOf(fromCombo.getSelectedItem());
        String end = String.valueOf(toCombo.getSelectedItem());
        selectedJourneys.put(selectedTrainId, new String[]{start, end});
        updateRoutePreview();
    }

    private void updateRoutePreview() {
        if (fromCombo.getSelectedItem() == null || toCombo.getSelectedItem() == null) {
            routePreview.setText("Route: —");
            return;
        }
        String start = String.valueOf(fromCombo.getSelectedItem());
        String end = String.valueOf(toCombo.getSelectedItem());
        List<String> sections = controller.sectionsBetween(start, end);
        routePreview.setText(sections.isEmpty()
                ? "Route: no track from " + start + " to " + end
                : "Route: " + chain(sections));
    }

    private void updateConnectionCard() {
        if (selectedTrainId == null) {
            return;
        }
        TrainDefinition def = findDefinition(selectedTrainId);
        boolean running = supervisor.isTrainRunning(selectedTrainId);
        connectionPanel.showTrain(def, latestLive.get(selectedTrainId), running, resolvedAddresses.get(selectedTrainId));
    }

    private TrainDefinition findDefinition(String trainId) {
        for (TrainDefinition def : definitions) {
            if (def.trainId().equals(trainId)) {
                return def;
            }
        }
        return null;
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

    private static String processStatusLabel(boolean running, TrainInfo info) {
        if (!running) {
            return "Stopped";
        }
        if (info != null && info.transport() != null) {
            return "Running (" + info.transport().name() + ")";
        }
        return "Running";
    }

    private static String extractResolved(String line) {
        int resolvedIdx = line.indexOf("resolved ");
        if (resolvedIdx >= 0) {
            return line.substring(resolvedIdx + "resolved ".length()).trim();
        }
        int connectedIdx = line.indexOf("connected ");
        if (connectedIdx >= 0) {
            return line.substring(connectedIdx + "connected ".length()).trim();
        }
        return null;
    }

    private static boolean isErrorLine(String line) {
        return line.contains("SUPERVISOR ERROR")
                || line.contains("Could not connect")
                || line.contains("Unknown host")
                || line.contains("Timed out");
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_SMALL_BOLD);
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    private static final class TrainConsoleCard {
        private final TrainDefinition definition;
        private final Card card;
        private final StatusPill processPill;
        private final JLabel resolvedLabel;
        private final JTextArea console;

        private TrainConsoleCard(TrainDefinition definition, Card card, StatusPill processPill,
                                 JLabel resolvedLabel, JTextArea console) {
            this.definition = definition;
            this.card = card;
            this.processPill = processPill;
            this.resolvedLabel = resolvedLabel;
            this.console = console;
        }
    }
}
