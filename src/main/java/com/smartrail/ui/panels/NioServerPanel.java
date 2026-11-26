package com.smartrail.ui.panels;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.RouteInfo;
import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.service.RailwayService;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.StatusPill;
import com.smartrail.ui.components.TableStyles;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OWNER: Member 1 - the Overview screen.
 *
 * <p>The map, train table, selected-train commands and attention list all read
 * {@link RailwayService} snapshots. Hold, resume and emergency stop are the same calls the RMI admin uses.
 */
public final class NioServerPanel extends JPanel {

    private final SimulationController controller;
    private final NetworkMapPanel map;
    private final StatusPill serverPill = new StatusPill("NIO server not started", Theme.INACTIVE);
    private final DefaultTableModel trainsModel = new DefaultTableModel(
            new String[]{"Train", "Current", "Next section", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable trainsTable = new JTable(trainsModel);
    private final JLabel selectedId = new JLabel("No train selected");
    private final StatusPill connectionPill = new StatusPill("Not connected", Theme.INACTIVE);
    private final JLabel selectedDetail = new JLabel(" ");
    private final JLabel commandResult = new JLabel(" ");
    private final JTextArea attention = new JTextArea(6, 24);
    private final JButton hold = new JButton("Hold");
    private final JButton resume = new JButton("Resume");
    private final JButton emergency = new JButton("Emergency Stop");
    private List<TrainInfo> shown = List.of();
    private String pendingSelection;
    private String commandFor;

    public NioServerPanel(SimulationController controller) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.controller = controller;
        setOpaque(false);
        long glideMs = controller.config().getLong(ConfigKeys.CLIENT_SIMULATOR_STEP_MS) * 3L;
        map = new NetworkMapPanel(controller.railwayData(), glideMs);
        map.setPreferredSize(new Dimension(480, 320));

        TableStyles.apply(trainsTable);
        trainsTable.getColumnModel().getColumn(3).setCellRenderer(TableStyles.statusDotRenderer());
        trainsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });

        selectedId.setFont(Theme.FONT_CARD_TITLE);
        selectedId.setForeground(Theme.TEXT);
        selectedId.setHorizontalAlignment(SwingConstants.LEFT);
        selectedDetail.setFont(Theme.FONT_BODY);
        selectedDetail.setForeground(Theme.TEXT_MUTED);
        selectedDetail.setHorizontalAlignment(SwingConstants.LEFT);
        commandResult.setFont(Theme.FONT_BODY);
        commandResult.setForeground(Theme.TEXT);
        commandResult.setHorizontalAlignment(SwingConstants.LEFT);
        attention.setEditable(false);
        attention.setFont(Theme.FONT_SMALL);
        attention.setForeground(Theme.TEXT);
        attention.setBackground(Theme.SURFACE);
        hold.setFont(Theme.FONT_BODY);
        resume.setFont(Theme.FONT_BODY);
        emergency.setFont(Theme.FONT_BODY);
        emergency.setForeground(Theme.DANGER);
        hold.addActionListener(e -> command("hold"));
        resume.addActionListener(e -> command("resume"));
        emergency.addActionListener(e -> command("stop"));

        Card mapCard = new Card("Network map");
        mapCard.setBody(map);
        Card tableCard = new Card("Train overview");
        JScrollPane tableScroll = new JScrollPane(trainsTable);
        tableScroll.setPreferredSize(new Dimension(420, 140));
        tableCard.setBody(tableScroll);

        JPanel left = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        left.setOpaque(false);
        left.add(mapCard, BorderLayout.CENTER);
        left.add(tableCard, BorderLayout.SOUTH);

        Card selected = new Card("Selected train");
        JPanel selectedBody = new JPanel();
        selectedBody.setOpaque(false);
        selectedBody.setLayout(new BoxLayout(selectedBody, BoxLayout.Y_AXIS));
        selectedBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        alignLeft(selectedId);
        alignLeft(connectionPill);
        alignLeft(selectedDetail);
        alignLeft(commandResult);
        selectedBody.add(selectedId);
        selectedBody.add(Box.createVerticalStrut(Theme.GAP));
        selectedBody.add(connectionPill);
        selectedBody.add(Box.createVerticalStrut(Theme.GAP));
        selectedBody.add(selectedDetail);
        selectedBody.add(Box.createVerticalStrut(Theme.GAP));
        selectedBody.add(commandResult);
        selectedBody.add(Box.createVerticalStrut(Theme.GAP));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(hold);
        buttons.add(resume);
        buttons.add(emergency);
        selectedBody.add(buttons);
        selected.setBody(selectedBody);

        Card attentionCard = new Card("Attention");
        attentionCard.setBody(new JScrollPane(attention));

        JPanel right = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(340, 0));
        right.add(selected, BorderLayout.NORTH);
        right.add(attentionCard, BorderLayout.CENTER);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusRow.add(serverPill, BorderLayout.WEST);

        add(statusRow, BorderLayout.NORTH);
        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        Timer timer = new Timer(400, e -> refresh());
        timer.setInitialDelay(0);
        timer.start();
    }

    private void refresh() {
        boolean running = controller.isServerRunning() && controller.serverContext() != null;
        setPill(serverPill, running ? "NIO server started" : "NIO server not started",
                running ? Theme.OK : Theme.INACTIVE);
        if (!running) {
            map.show(List.of(), List.of());
            if (trainsModel.getRowCount() > 0) {
                trainsModel.setRowCount(0);
            }
            setAttention("Start the server to see reservations.");
            map.setAlerts(Map.of());
            return;
        }
        RailwayService railway = controller.serverContext().railwayService();
        List<TrainInfo> trains = railway.getTrains();
        shown = trains;
        map.show(railway.getTracks(), trains);
        String keep = selectedTrainId() != null ? selectedTrainId() : pendingSelection;
        replaceTrainRows(trains);
        if (keep != null && !keep.equals(selectedTrainId())) {
            restoreSelection(keep);
        }
        Situation situation = situation(railway);
        setAttention(situation.text());
        map.setAlerts(situation.labels());
        showSelected();
    }

    private void replaceTrainRows(List<TrainInfo> trains) {
        if (trainsModel.getRowCount() != trains.size()) {
            trainsModel.setRowCount(0);
            for (TrainInfo train : trains) {
                trainsModel.addRow(row(train));
            }
            return;
        }
        for (int i = 0; i < trains.size(); i++) {
            Object[] next = row(trains.get(i));
            for (int column = 0; column < next.length; column++) {
                if (!next[column].equals(trainsModel.getValueAt(i, column))) {
                    trainsModel.setValueAt(next[column], i, column);
                }
            }
        }
    }

    private Object[] row(TrainInfo train) {
        return new Object[]{
                train.trainId(),
                placeName(train.location()),
                nextSection(train),
                train.status() == null ? "" : train.status().name()
        };
    }

    private static void alignLeft(javax.swing.JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static void setPill(StatusPill pill, String text, java.awt.Color color) {
        if (text.equals(pill.getText()) && color.equals(pill.getDotColor())) {
            return;
        }
        pill.setStatus(text, color, Theme.SURFACE, Theme.TEXT);
    }

    private void setAttention(String text) {
        if (!text.equals(attention.getText())) {
            attention.setText(text);
        }
    }

    private void showSelected() {
        int row = trainsTable.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            selectedId.setText("No train selected");
            connectionPill.setStatus("Not connected", Theme.INACTIVE, Theme.SURFACE, Theme.TEXT);
            selectedDetail.setText(" ");
            if (commandFor != null) {
                commandFor = null;
                commandResult.setText(" ");
            }
            return;
        }
        TrainInfo train = shown.get(row);
        if (!train.trainId().equals(commandFor)) {
            commandFor = train.trainId();
            commandResult.setText(" ");
            commandResult.setForeground(Theme.TEXT);
        }
        String title = train.trainId() + "  " + train.name();
        if (!title.equals(selectedId.getText())) {
            selectedId.setText(title);
        }
        boolean connected = train.remoteAddress() != null && train.status() != TrainStatus.INACTIVE;
        String badge = !connected ? "Not connected"
                : train.transport() == null ? "Connected"
                : "Connected (" + train.transport().name() + ")";
        setPill(connectionPill, badge, connected ? Theme.OK : Theme.INACTIVE);
        String seen = train.lastSeenMillis() == 0L ? "never" : String.valueOf(train.lastSeenMillis());
        String detail = "<html>Current " + placeName(train.location())
                + " &nbsp; Next " + nextSection(train)
                + " &nbsp; Status " + train.status()
                + "<br>Last update " + seen + "</html>";
        if (!detail.equals(selectedDetail.getText())) {
            selectedDetail.setText(detail);
        }
    }

    /** A block is shown as its direction, for example ST01→ST02. A station stays ST01. */
    private String placeName(String location) {
        if (location == null || location.isBlank()) {
            return "";
        }
        for (var section : controller.railwayData().sections()) {
            if (section.sectionId().equals(location)) {
                return section.fromStationId() + "→" + section.toStationId();
            }
        }
        return location;
    }

    private String nextSection(TrainInfo train) {
        if (train.status() == TrainStatus.ARRIVED) {
            return "—";
        }
        var chosen = controller.chosenSections(train.trainId());
        if (chosen.isPresent()) {
            return nextAlong(train, chosen.get());
        }
        if (train.routeId() == null) {
            return "—";
        }
        for (RouteInfo route : controller.railwayData().routes()) {
            if (!route.routeId().equals(train.routeId())) {
                continue;
            }
            List<String> sections = route.sectionIds();
            int at = sections.indexOf(train.location());
            if (at >= 0) {
                return at + 1 < sections.size() ? placeName(sections.get(at + 1)) : "—";
            }
            for (String sectionId : sections) {
                var section = controller.railwayData().section(sectionId);
                if (section.isPresent() && section.get().fromStationId().equals(train.location())) {
                    return placeName(sectionId);
                }
            }
            return sections.isEmpty() ? "—" : placeName(sections.get(0));
        }
        return "—";
    }

    private String nextAlong(TrainInfo train, List<String> sections) {
        int at = sections.indexOf(train.location());
        if (at >= 0) {
            return at + 1 < sections.size() ? placeName(sections.get(at + 1)) : "—";
        }
        for (String sectionId : sections) {
            var section = controller.railwayData().section(sectionId);
            if (section.isPresent() && section.get().fromStationId().equals(train.location())) {
                return placeName(sectionId);
            }
        }
        return sections.isEmpty() ? "—" : placeName(sections.get(0));
    }

    /** Called when a train process is started, so Hold has an obvious target. */
    public void selectTrain(String trainId) {
        pendingSelection = trainId;
        UiThreads.onEdt(() -> restoreSelection(trainId));
    }

    private void command(String action) {
        String trainId = selectedTrainId();
        if (trainId == null || !controller.isServerRunning() || controller.serverContext() == null) {
            commandResult.setForeground(Theme.DANGER);
            commandResult.setText("Select a connected train first.");
            return;
        }
        RailwayService railway = controller.serverContext().railwayService();
        String wire = switch (action) {
            case "hold" -> "HOLD";
            case "resume" -> "RESUME";
            case "stop" -> "EMERGENCY_STOP";
            default -> action;
        };
        UiThreads.runInBackground(() -> {
            boolean sent = switch (action) {
                case "hold" -> railway.holdTrain(trainId);
                case "resume" -> railway.resumeTrain(trainId);
                case "stop" -> railway.emergencyStop(trainId);
                default -> false;
            };
            String text = sent
                    ? "Sent " + wire + " to " + trainId
                    : wire + " was not sent. " + trainId + " is not connected.";
            UiThreads.onEdt(() -> {
                if (!trainId.equals(selectedTrainId())) {
                    return;
                }
                commandFor = trainId;
                commandResult.setForeground(sent ? Theme.TEXT : Theme.DANGER);
                commandResult.setText(text);
            });
        });
    }

    private String selectedTrainId() {
        int row = trainsTable.getSelectedRow();
        if (row < 0 || row >= trainsModel.getRowCount()) {
            return null;
        }
        Object value = trainsModel.getValueAt(row, 0);
        return value == null ? null : value.toString();
    }

    private void restoreSelection(String trainId) {
        if (trainId == null) {
            return;
        }
        for (int row = 0; row < trainsModel.getRowCount(); row++) {
            if (trainId.equals(trainsModel.getValueAt(row, 0))) {
                trainsTable.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    /**
     * Current problems only. A denial stays while that block is still held. An inactive line
     * stays while the train is inactive away from its start. A reset frees the blocks and
     * parks every train, so both disappear. The latest obstruction after that reset remains.
     */
    private Situation situation(RailwayService railway) {
        List<EventLog.Entry> entries = controller.eventLog().snapshot();
        long since = 0L;
        for (EventLog.Entry entry : entries) {
            if (entry.message().startsWith("Simulation reset")) {
                since = entry.timeMillis();
            }
        }
        List<String> lines = new ArrayList<>();
        Map<String, String> labels = new LinkedHashMap<>();
        List<String> seenSections = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            EventLog.Entry entry = entries.get(i);
            if (entry.timeMillis() < since || !entry.message().startsWith("SECTION_DENIED ")) {
                continue;
            }
            String[] parts = entry.message().split(" ");
            if (parts.length < 3) {
                continue;
            }
            String trainId = parts[1];
            String sectionId = parts[2];
            if (seenSections.contains(sectionId)) {
                continue;
            }
            seenSections.add(sectionId);
            TrackInfo track = railway.getTrack(sectionId).orElse(null);
            if (track == null || track.state() == SectionState.FREE) {
                continue;
            }
            String holder = track.holderTrainId() == null ? "another train" : track.holderTrainId();
            lines.add(placeName(sectionId) + " refused " + trainId + ". Held by " + holder + ".");
            labels.put(sectionId, "Conflict " + trainId);
        }
        for (TrainInfo train : railway.getTrains()) {
            if (train.status() == TrainStatus.INACTIVE && train.lastSeenMillis() > 0L && !parkedAtStart(train)) {
                lines.add(train.trainId() + " is inactive.");
            }
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            EventLog.Entry entry = entries.get(i);
            if (entry.timeMillis() < since || !entry.message().startsWith("OBSTRUCTION ")) {
                continue;
            }
            String[] parts = entry.message().split(" ", 3);
            if (parts.length >= 2) {
                String sectionId = parts[1];
                String reason = parts.length == 3 ? parts[2] : "";
                String onIt = trainOn(railway, sectionId);
                lines.add("Obstruction on " + placeName(sectionId) + ". " + reason
                        + (onIt.isEmpty() ? "" : " " + onIt + " is on it."));
                String existing = labels.get(sectionId);
                String chip = onIt.isEmpty() ? "Obstruction" : "Obstruction " + onIt;
                labels.put(sectionId, existing == null ? chip : existing + " · Obstruction");
            }
            break;
        }
        String text = lines.isEmpty() ? "No denials, inactive trains, or obstructions." : String.join("\n", lines);
        return new Situation(text, labels);
    }

    private static String trainOn(RailwayService railway, String sectionId) {
        StringBuilder names = new StringBuilder();
        for (TrainInfo train : railway.getTrains()) {
            if (sectionId.equals(train.location())) {
                if (!names.isEmpty()) {
                    names.append(' ');
                }
                names.append(train.trainId());
            }
        }
        return names.toString().trim();
    }

    private record Situation(String text, Map<String, String> labels) {
    }

    private boolean parkedAtStart(TrainInfo train) {
        return controller.railwayData().train(train.trainId())
                .map(defined -> defined.startStationId().equals(train.location()))
                .orElse(false);
    }
}
