package com.smartrail.ui.panels;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.contenthandler.SmartRailContentHandlerFactory;
import com.smartrail.rmi.RailwayAdminRemote;
import com.smartrail.rmi.RmiAdminClient;
import com.smartrail.ui.shell.UiThreads;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OWNER: Member 5 - CONCEPT: Controller Dashboard admin panel & custom URL inspector.
 * <ul>
 *   <li>RMI: Connect, Active trains, Track status, Hold/Resume/Emergency stop, Report obstruction, Event log</li>
 *   <li>smartrail:// inspector: open URL, display Content-Type and inspected typed object fields</li>
 * </ul>
 * Rules: network calls via {@link UiThreads#runInBackground}, UI updates via {@link UiThreads#onEdt}.
 */
public final class RmiHandlerPanel extends JPanel {

    private final NetworkConfig config;
    private RailwayAdminRemote admin;

    // Top Connection Bar
    private final JTextField hostField;
    private final JTextField portField;
    private final JButton connectBtn;
    private final JLabel statusLabel;

    // Active Trains Tab
    private final DefaultTableModel trainsModel;
    private final JTable trainsTable;
    private final JTextField selectedTrainField;
    private final JButton holdBtn;
    private final JButton resumeBtn;
    private final JButton emergencyBtn;
    private final JButton refreshTrainsBtn;

    // Tracks Tab
    private final DefaultTableModel tracksModel;
    private final JTable tracksTable;
    private final JTextField sectionField;
    private final JTextField obstructionReasonField;
    private final JButton reportObstructionBtn;
    private final JButton refreshTracksBtn;

    // Event Log Tab
    private final JTextArea eventLogArea;
    private final JButton refreshLogBtn;

    // smartrail:// Inspector
    private final JTextField urlField;
    private final JComboBox<String> samplePathsCombo;
    private final JButton fetchResourceBtn;
    private final JLabel resourceMetaLabel;
    private final JTextArea resourceOutputArea;

    public RmiHandlerPanel(NetworkConfig config) {
        super(new BorderLayout(5, 5));
        this.config = config;

        // Ensure ContentHandlerFactory is installed
        try {
            URLConnection.setContentHandlerFactory(new SmartRailContentHandlerFactory());
        } catch (Error ignored) {
            // Already installed in this JVM
        }

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Initialize fields
        hostField = new JTextField(config.get(ConfigKeys.CLIENT_SERVER_HOST), 12);
        portField = new JTextField(String.valueOf(config.getInt(ConfigKeys.RMI_REGISTRY_PORT)), 6);
        connectBtn = new JButton("Connect");
        statusLabel = new JLabel("Disconnected");
        selectedTrainField = new JTextField(8);
        holdBtn = new JButton("Simulate Hold");
        resumeBtn = new JButton("Resume");
        emergencyBtn = new JButton("Emergency Stop");
        refreshTrainsBtn = new JButton("Refresh");
        sectionField = new JTextField(8);
        obstructionReasonField = new JTextField("OBSTRUCTION", 12);
        reportObstructionBtn = new JButton("Report Obstruction");
        refreshTracksBtn = new JButton("Refresh");
        eventLogArea = new JTextArea();
        refreshLogBtn = new JButton("Refresh Log");
        urlField = new JTextField("smartrail://localhost:" + config.getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT) + "/trains", 28);
        samplePathsCombo = new JComboBox<>(new String[]{
                "/trains",
                "/trains/TR01",
                "/tracks",
                "/tracks/SEC_A",
                "/schedules"
        });
        fetchResourceBtn = new JButton("Fetch & Parse");
        resourceMetaLabel = new JLabel("Content-Type: - | Status: Ready");
        resourceOutputArea = new JTextArea(8, 60);

        // -------------------------------------------------------------
        // 1. TOP PANEL: RMI Connection Controls
        // -------------------------------------------------------------
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("RMI Administration Connection"));

        topPanel.add(new JLabel("Server Host:"));
        topPanel.add(hostField);

        topPanel.add(new JLabel("RMI Registry Port:"));
        topPanel.add(portField);

        topPanel.add(connectBtn);

        statusLabel.setForeground(Color.RED);
        topPanel.add(statusLabel);

        add(topPanel, BorderLayout.NORTH);

        // -------------------------------------------------------------
        // 2. CENTER PANEL: Tabs for Trains, Tracks, and Event Log
        // -------------------------------------------------------------
        JTabbedPane adminTabs = new JTabbedPane();

        // --- Trains Tab ---
        JPanel trainsPanel = new JPanel(new BorderLayout(5, 5));
        trainsModel = new DefaultTableModel(
                new String[]{"Train ID", "Name", "Status", "Location", "Route", "Transport", "Remote Address", "Last Seen"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        trainsTable = new JTable(trainsModel);
        trainsTable.getSelectionModel().addListSelectionListener(e -> {
            int row = trainsTable.getSelectedRow();
            if (row >= 0) {
                selectedTrainField.setText((String) trainsModel.getValueAt(row, 0));
            }
        });
        trainsPanel.add(new JScrollPane(trainsTable), BorderLayout.CENTER);

        JPanel trainActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        trainActionsPanel.add(new JLabel("Target Train:"));
        trainActionsPanel.add(selectedTrainField);

        holdBtn.setEnabled(false);
        trainActionsPanel.add(holdBtn);

        resumeBtn.setEnabled(false);
        trainActionsPanel.add(resumeBtn);

        emergencyBtn.setEnabled(false);
        trainActionsPanel.add(emergencyBtn);

        refreshTrainsBtn.setEnabled(false);
        trainActionsPanel.add(refreshTrainsBtn);

        trainsPanel.add(trainActionsPanel, BorderLayout.SOUTH);
        adminTabs.addTab("Active Trains & Commands", trainsPanel);

        // --- Tracks Tab ---
        JPanel tracksPanel = new JPanel(new BorderLayout(5, 5));
        tracksModel = new DefaultTableModel(
                new String[]{"Section ID", "From Station", "To Station", "State", "Holder Train"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tracksTable = new JTable(tracksModel);
        tracksTable.getSelectionModel().addListSelectionListener(e -> {
            int row = tracksTable.getSelectedRow();
            if (row >= 0) {
                sectionField.setText((String) tracksModel.getValueAt(row, 0));
            }
        });
        tracksPanel.add(new JScrollPane(tracksTable), BorderLayout.CENTER);

        JPanel trackActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        trackActionsPanel.add(new JLabel("Section ID:"));
        trackActionsPanel.add(sectionField);

        trackActionsPanel.add(new JLabel("Reason:"));
        trackActionsPanel.add(obstructionReasonField);

        reportObstructionBtn.setEnabled(false);
        trackActionsPanel.add(reportObstructionBtn);

        refreshTracksBtn.setEnabled(false);
        trackActionsPanel.add(refreshTracksBtn);

        tracksPanel.add(trackActionsPanel, BorderLayout.SOUTH);
        adminTabs.addTab("Track Sections", tracksPanel);

        // --- Event Log Tab ---
        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        eventLogArea.setEditable(false);
        eventLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logPanel.add(new JScrollPane(eventLogArea), BorderLayout.CENTER);

        JPanel logActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        refreshLogBtn.setEnabled(false);
        logActionsPanel.add(refreshLogBtn);
        logPanel.add(logActionsPanel, BorderLayout.SOUTH);

        adminTabs.addTab("Server Event Log", logPanel);

        // -------------------------------------------------------------
        // 3. BOTTOM PANEL: smartrail:// Protocol & Content Inspector
        // -------------------------------------------------------------
        JPanel inspectorPanel = new JPanel(new BorderLayout(5, 5));
        inspectorPanel.setBorder(BorderFactory.createTitledBorder("smartrail:// Protocol & Content Handler Inspector"));

        JPanel inspectorControls = new JPanel();
        inspectorControls.setLayout(new BoxLayout(inspectorControls, BoxLayout.Y_AXIS));

        JPanel urlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        urlRow.add(new JLabel("Resource URL:"));
        urlRow.add(urlField);

        samplePathsCombo.addActionListener(e -> {
            String selected = (String) samplePathsCombo.getSelectedItem();
            if (selected != null) {
                urlField.setText("smartrail://localhost:" + config.getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT) + selected);
            }
        });
        urlRow.add(samplePathsCombo);

        urlRow.add(fetchResourceBtn);

        inspectorControls.add(urlRow);

        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        metaRow.add(resourceMetaLabel);
        inspectorControls.add(metaRow);

        inspectorPanel.add(inspectorControls, BorderLayout.NORTH);

        resourceOutputArea.setEditable(false);
        resourceOutputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        inspectorPanel.add(new JScrollPane(resourceOutputArea), BorderLayout.CENTER);

        // Split pane separating Admin tabs and smartrail:// inspector
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, adminTabs, inspectorPanel);
        splitPane.setResizeWeight(0.65);
        add(splitPane, BorderLayout.CENTER);

        // -------------------------------------------------------------
        // 4. ACTION LISTENERS & EVENT WIRING
        // -------------------------------------------------------------
        connectBtn.addActionListener(e -> toggleConnection());

        holdBtn.addActionListener(e -> executeTrainCommand("hold", selectedTrainField.getText().trim()));
        resumeBtn.addActionListener(e -> executeTrainCommand("resume", selectedTrainField.getText().trim()));
        emergencyBtn.addActionListener(e -> executeTrainCommand("emergency", selectedTrainField.getText().trim()));

        refreshTrainsBtn.addActionListener(e -> refreshTrains());
        refreshTracksBtn.addActionListener(e -> refreshTracks());
        refreshLogBtn.addActionListener(e -> refreshEventLog());

        reportObstructionBtn.addActionListener(e -> {
            String sectionId = sectionField.getText().trim();
            String reason = obstructionReasonField.getText().trim();
            if (sectionId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a Section ID", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            UiThreads.runInBackground(() -> {
                try {
                    admin.reportObstruction(sectionId, reason.isEmpty() ? "OBSTRUCTION" : reason);
                    refreshTracks();
                    refreshEventLog();
                } catch (Exception ex) {
                    showError("Failed to report obstruction: " + ex.getMessage());
                }
            });
        });

        fetchResourceBtn.addActionListener(e -> fetchSmartRailResource());
    }

    private void toggleConnection() {
        if (admin != null) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid RMI port: " + portText, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectBtn.setEnabled(false);
        statusLabel.setText("Connecting...");
        statusLabel.setForeground(Color.ORANGE);

        UiThreads.runInBackground(() -> {
            try {
                RmiAdminClient client = new RmiAdminClient(config);
                RailwayAdminRemote stub = client.connect(host, port);
                admin = stub;
                UiThreads.onEdt(() -> {
                    connectBtn.setText("Disconnect");
                    connectBtn.setEnabled(true);
                    statusLabel.setText("Connected to " + host + ":" + port);
                    statusLabel.setForeground(new Color(0, 150, 0));
                    setControlsEnabled(true);
                    refreshAll();
                });
            } catch (Exception e) {
                UiThreads.onEdt(() -> {
                    connectBtn.setText("Connect");
                    connectBtn.setEnabled(true);
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    setControlsEnabled(false);
                });
            }
        });
    }

    private void disconnect() {
        admin = null;
        connectBtn.setText("Connect");
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.RED);
        setControlsEnabled(false);
        trainsModel.setRowCount(0);
        tracksModel.setRowCount(0);
        eventLogArea.setText("");
    }

    private void setControlsEnabled(boolean enabled) {
        holdBtn.setEnabled(enabled);
        resumeBtn.setEnabled(enabled);
        emergencyBtn.setEnabled(enabled);
        refreshTrainsBtn.setEnabled(enabled);
        reportObstructionBtn.setEnabled(enabled);
        refreshTracksBtn.setEnabled(enabled);
        refreshLogBtn.setEnabled(enabled);
    }

    private void refreshAll() {
        refreshTrains();
        refreshTracks();
        refreshEventLog();
    }

    private void refreshTrains() {
        if (admin == null) return;
        UiThreads.runInBackground(() -> {
            try {
                List<TrainInfo> trains = admin.getActiveTrains();
                UiThreads.onEdt(() -> {
                    trainsModel.setRowCount(0);
                    for (TrainInfo t : trains) {
                        trainsModel.addRow(new Object[]{
                                t.trainId(),
                                t.name(),
                                t.status() != null ? t.status().name() : "-",
                                t.location() != null ? t.location() : "-",
                                t.routeId() != null ? t.routeId() : "-",
                                t.transport() != null ? t.transport().name() : "-",
                                t.remoteAddress() != null ? t.remoteAddress() : "-",
                                t.lastSeenMillis() > 0 ? t.lastSeenMillis() : "-"
                        });
                    }
                });
            } catch (Exception e) {
                showError("Error refreshing trains: " + e.getMessage());
            }
        });
    }

    private void refreshTracks() {
        if (admin == null) return;
        UiThreads.runInBackground(() -> {
            try {
                List<TrackInfo> tracks = admin.getAllTracks();
                UiThreads.onEdt(() -> {
                    tracksModel.setRowCount(0);
                    for (TrackInfo track : tracks) {
                        tracksModel.addRow(new Object[]{
                                track.sectionId(),
                                track.fromStationId(),
                                track.toStationId(),
                                track.state() != null ? track.state().name() : "-",
                                track.holderTrainId() != null ? track.holderTrainId() : "-"
                        });
                    }
                });
            } catch (Exception e) {
                showError("Error refreshing tracks: " + e.getMessage());
            }
        });
    }

    private void refreshEventLog() {
        if (admin == null) return;
        UiThreads.runInBackground(() -> {
            try {
                List<EventLog.Entry> entries = admin.getEventLog();
                StringBuilder sb = new StringBuilder();
                for (EventLog.Entry entry : entries) {
                    sb.append(entry.format()).append("\n");
                }
                UiThreads.onEdt(() -> {
                    eventLogArea.setText(sb.toString());
                    eventLogArea.setCaretPosition(eventLogArea.getDocument().getLength());
                });
            } catch (Exception e) {
                showError("Error refreshing event log: " + e.getMessage());
            }
        });
    }

    private void executeTrainCommand(String action, String trainId) {
        if (trainId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select or enter a Train ID", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (admin == null) return;

        UiThreads.runInBackground(() -> {
            try {
                boolean success;
                switch (action) {
                    case "hold" -> success = admin.requestSimulatedHold(trainId);
                    case "resume" -> success = admin.requestResume(trainId);
                    case "emergency" -> success = admin.requestEmergencyStop(trainId);
                    default -> success = false;
                }
                refreshTrains();
                refreshEventLog();
                if (!success) {
                    UiThreads.onEdt(() -> JOptionPane.showMessageDialog(this,
                            "Command " + action + " for " + trainId + " returned false (train not connected or held).",
                            "Command Notice", JOptionPane.INFORMATION_MESSAGE));
                }
            } catch (Exception e) {
                showError("RMI command failed: " + e.getMessage());
            }
        });
    }

    private void fetchSmartRailResource() {
        String urlString = urlField.getText().trim();
        if (urlString.isEmpty()) return;

        fetchResourceBtn.setEnabled(false);
        resourceMetaLabel.setText("Connecting...");
        resourceOutputArea.setText("Sending request to " + urlString + " ...\n");

        UiThreads.runInBackground(() -> {
            try {
                URL url = new URI(urlString).toURL();
                URLConnection conn = url.openConnection();
                conn.connect();

                String contentType = conn.getContentType();
                Object typedContent = null;
                try {
                    typedContent = conn.getContent();
                } catch (Exception e) {
                    // Fall back if content handler not registered or failed
                }

                String rawBody = "";
                try (InputStream in = conn.getInputStream()) {
                    rawBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }

                Object finalTypedContent = typedContent;
                String finalRawBody = rawBody;
                UiThreads.onEdt(() -> {
                    fetchResourceBtn.setEnabled(true);
                    resourceMetaLabel.setText("Content-Type: " + contentType + " | Status: 200 OK");
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== Resource Loaded Successfully ===\n");
                    sb.append("URL: ").append(url).append("\n");
                    sb.append("Content-Type: ").append(contentType).append("\n\n");

                    if (finalTypedContent != null) {
                        sb.append("=== Typed Object (via ContentHandler: ").append(finalTypedContent.getClass().getSimpleName()).append(") ===\n");
                        if (finalTypedContent instanceof List<?> list) {
                            sb.append("Elements count: ").append(list.size()).append("\n");
                            for (Object item : list) {
                                if (item instanceof TrainInfo t) {
                                    sb.append("  * TrainInfo: id=").append(t.trainId())
                                            .append(", name=").append(t.name())
                                            .append(", status=").append(t.status())
                                            .append(", location=").append(t.location()).append("\n");
                                } else if (item instanceof TrackInfo tr) {
                                    sb.append("  * TrackInfo: sectionId=").append(tr.sectionId())
                                            .append(", from=").append(tr.fromStationId())
                                            .append(", to=").append(tr.toStationId())
                                            .append(", state=").append(tr.state())
                                            .append(", holder=").append(tr.holderTrainId()).append("\n");
                                } else if (item instanceof ScheduleInfo sc) {
                                    sb.append("  * ScheduleInfo: trainId=").append(sc.trainId())
                                            .append(", station=").append(sc.stationId())
                                            .append(", time=").append(sc.time()).append("\n");
                                } else {
                                    sb.append("  * ").append(item).append("\n");
                                }
                            }
                        } else {
                            sb.append("Object: ").append(finalTypedContent).append("\n");
                        }
                        sb.append("\n");
                    }

                    sb.append("=== Raw Payload Stream ===\n").append(finalRawBody);
                    resourceOutputArea.setText(sb.toString());
                });
            } catch (Exception e) {
                UiThreads.onEdt(() -> {
                    fetchResourceBtn.setEnabled(true);
                    resourceMetaLabel.setText("Status: Error (" + e.getClass().getSimpleName() + ")");
                    resourceOutputArea.setText("Failed to load resource:\n" + e.getMessage() + "\n\n"
                            + (e.getCause() != null ? "Cause: " + e.getCause().getMessage() : ""));
                });
            }
        });
    }

    private void showError(String message) {
        UiThreads.onEdt(() -> JOptionPane.showMessageDialog(this, message, "RMI Error", JOptionPane.ERROR_MESSAGE));
    }
}
