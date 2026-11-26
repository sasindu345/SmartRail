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
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.StatusPill;
import com.smartrail.ui.components.TableStyles;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
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
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OWNER: Member 5 - CONCEPT: Administration screen (RMI + smartrail:// inspector).
 * <ul>
 *   <li>RMI: Connect, Active trains, Track status, Hold/Resume/Emergency stop, Report obstruction, Event log</li>
 *   <li>smartrail:// inspector: open URL, display Content-Type and inspected typed object fields</li>
 * </ul>
 * <p>Restyled to match the Control Center design system using {@link Card}, {@link StatusPill},
 * and {@link TableStyles}. All network calls use {@link UiThreads#runInBackground}, and UI updates
 * use {@link UiThreads#onEdt}.
 */
public final class RmiHandlerPanel extends JPanel {

    private final NetworkConfig config;
    private RailwayAdminRemote admin;

    // Top Connection Bar & Status
    private final JTextField hostField;
    private final JTextField portField;
    private final JButton connectBtn;
    private final StatusPill rmiStatusPill;
    private final JLabel inlineNoticeLabel;

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
    private final StatusPill inspectorStatusPill;
    private final StatusPill contentTypePill;
    private final JTextArea resourceOutputArea;

    public RmiHandlerPanel(NetworkConfig config) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.config = config;

        // Ensure ContentHandlerFactory is installed
        try {
            URLConnection.setContentHandlerFactory(new SmartRailContentHandlerFactory());
        } catch (Error ignored) {
            // Already installed in this JVM
        }

        setOpaque(false);

        // -------------------------------------------------------------
        // Initialize fields
        // -------------------------------------------------------------
        hostField = new JTextField(config.get(ConfigKeys.CLIENT_SERVER_HOST), 12);
        hostField.setFont(Theme.FONT_BODY);

        portField = new JTextField(String.valueOf(config.getInt(ConfigKeys.RMI_REGISTRY_PORT)), 6);
        portField.setFont(Theme.FONT_BODY);

        connectBtn = new JButton("Connect");
        connectBtn.setFont(Theme.FONT_BODY_BOLD);

        rmiStatusPill = new StatusPill("Disconnected", Theme.INACTIVE);

        inlineNoticeLabel = new JLabel(" ");
        inlineNoticeLabel.setFont(Theme.FONT_SMALL_BOLD);
        inlineNoticeLabel.setForeground(Theme.TEXT_MUTED);

        selectedTrainField = new JTextField(8);
        selectedTrainField.setFont(Theme.FONT_BODY);

        holdBtn = new JButton("Hold");
        holdBtn.setFont(Theme.FONT_BODY);

        resumeBtn = new JButton("Resume");
        resumeBtn.setFont(Theme.FONT_BODY);

        emergencyBtn = new JButton("Emergency Stop");
        emergencyBtn.setFont(Theme.FONT_BODY);
        emergencyBtn.setForeground(Theme.DANGER);

        refreshTrainsBtn = new JButton("Refresh");
        refreshTrainsBtn.setFont(Theme.FONT_BODY);

        sectionField = new JTextField(8);
        sectionField.setFont(Theme.FONT_BODY);

        obstructionReasonField = new JTextField("OBSTRUCTION", 12);
        obstructionReasonField.setFont(Theme.FONT_BODY);

        reportObstructionBtn = new JButton("Report Obstruction");
        reportObstructionBtn.setFont(Theme.FONT_BODY);

        refreshTracksBtn = new JButton("Refresh");
        refreshTracksBtn.setFont(Theme.FONT_BODY);

        eventLogArea = new JTextArea();
        eventLogArea.setEditable(false);
        eventLogArea.setFont(Theme.FONT_MONO);
        eventLogArea.setBackground(Theme.SURFACE);
        eventLogArea.setForeground(Theme.TEXT);

        refreshLogBtn = new JButton("Refresh Log");
        refreshLogBtn.setFont(Theme.FONT_BODY);

        urlField = new JTextField("smartrail://localhost:" + config.getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT) + "/trains", 28);
        urlField.setFont(Theme.FONT_BODY);

        samplePathsCombo = new JComboBox<>(new String[]{
                "/trains",
                "/trains/TR01",
                "/tracks",
                "/tracks/SEC_A",
                "/schedules"
        });
        samplePathsCombo.setFont(Theme.FONT_BODY);

        fetchResourceBtn = new JButton("Fetch & Parse");
        fetchResourceBtn.setFont(Theme.FONT_BODY_BOLD);

        inspectorStatusPill = new StatusPill("Status: Ready", Theme.INACTIVE);
        contentTypePill = new StatusPill("MIME: none", Theme.INACTIVE);

        resourceOutputArea = new JTextArea(8, 60);
        resourceOutputArea.setEditable(false);
        resourceOutputArea.setFont(Theme.FONT_MONO);
        resourceOutputArea.setBackground(Theme.SURFACE);
        resourceOutputArea.setForeground(Theme.TEXT);

        // -------------------------------------------------------------
        // 1. TOP CARD: RMI Administration Connection
        // -------------------------------------------------------------
        Card connectionCard = new Card("RMI Remote Administration");
        JPanel connectionBody = new JPanel();
        connectionBody.setOpaque(false);
        connectionBody.setLayout(new BoxLayout(connectionBody, BoxLayout.Y_AXIS));

        JPanel connControlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        connControlsRow.setOpaque(false);

        JLabel hostLbl = new JLabel("Server Host:");
        hostLbl.setFont(Theme.FONT_BODY_BOLD);
        hostLbl.setForeground(Theme.TEXT_MUTED);
        connControlsRow.add(hostLbl);
        connControlsRow.add(hostField);

        JLabel portLbl = new JLabel("Registry Port:");
        portLbl.setFont(Theme.FONT_BODY_BOLD);
        portLbl.setForeground(Theme.TEXT_MUTED);
        connControlsRow.add(portLbl);
        connControlsRow.add(portField);

        connControlsRow.add(connectBtn);
        connControlsRow.add(Box.createHorizontalStrut(Theme.GAP));
        connControlsRow.add(rmiStatusPill);

        JPanel noticeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 4));
        noticeRow.setOpaque(false);
        noticeRow.add(inlineNoticeLabel);

        connectionBody.add(connControlsRow);
        connectionBody.add(noticeRow);
        connectionCard.setBody(connectionBody);

        add(connectionCard, BorderLayout.NORTH);

        // -------------------------------------------------------------
        // 2. CENTER TABS: Trains, Tracks, and Event Log (in Card)
        // -------------------------------------------------------------
        Card adminTabsCard = new Card("Railway Management (RMI)");
        JTabbedPane adminTabs = new JTabbedPane();
        adminTabs.setFont(Theme.FONT_BODY_BOLD);

        // --- Trains Tab ---
        JPanel trainsPanel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        trainsPanel.setOpaque(false);
        trainsModel = new DefaultTableModel(
                new String[]{"Train ID", "Name", "Status", "Location", "Route", "Transport", "Remote Address", "Last Seen"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        trainsTable = new JTable(trainsModel);
        TableStyles.apply(trainsTable);
        trainsTable.getColumnModel().getColumn(2).setCellRenderer(TableStyles.statusDotRenderer());
        trainsTable.getSelectionModel().addListSelectionListener(e -> {
            int row = trainsTable.getSelectedRow();
            if (row >= 0) {
                selectedTrainField.setText((String) trainsModel.getValueAt(row, 0));
            }
        });

        JScrollPane trainsScroll = new JScrollPane(trainsTable);
        trainsScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        trainsScroll.getViewport().setBackground(Theme.SURFACE);
        trainsPanel.add(trainsScroll, BorderLayout.CENTER);

        JPanel trainActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, Theme.GAP));
        trainActionsPanel.setOpaque(false);
        JLabel targetLbl = new JLabel("Target Train:");
        targetLbl.setFont(Theme.FONT_BODY_BOLD);
        trainActionsPanel.add(targetLbl);
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
        JPanel tracksPanel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        tracksPanel.setOpaque(false);
        tracksModel = new DefaultTableModel(
                new String[]{"Section ID", "From Station", "To Station", "State", "Holder Train"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tracksTable = new JTable(tracksModel);
        TableStyles.apply(tracksTable);
        tracksTable.getColumnModel().getColumn(3).setCellRenderer(TableStyles.statusDotRenderer());
        tracksTable.getSelectionModel().addListSelectionListener(e -> {
            int row = tracksTable.getSelectedRow();
            if (row >= 0) {
                sectionField.setText((String) tracksModel.getValueAt(row, 0));
            }
        });

        JScrollPane tracksScroll = new JScrollPane(tracksTable);
        tracksScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        tracksScroll.getViewport().setBackground(Theme.SURFACE);
        tracksPanel.add(tracksScroll, BorderLayout.CENTER);

        JPanel trackActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, Theme.GAP));
        trackActionsPanel.setOpaque(false);
        JLabel secLbl = new JLabel("Section ID:");
        secLbl.setFont(Theme.FONT_BODY_BOLD);
        trackActionsPanel.add(secLbl);
        trackActionsPanel.add(sectionField);

        JLabel reasonLbl = new JLabel("Reason:");
        reasonLbl.setFont(Theme.FONT_BODY_BOLD);
        trackActionsPanel.add(reasonLbl);
        trackActionsPanel.add(obstructionReasonField);

        reportObstructionBtn.setEnabled(false);
        trackActionsPanel.add(reportObstructionBtn);

        refreshTracksBtn.setEnabled(false);
        trackActionsPanel.add(refreshTracksBtn);

        tracksPanel.add(trackActionsPanel, BorderLayout.SOUTH);
        adminTabs.addTab("Track Sections", tracksPanel);

        // --- Event Log Tab ---
        JPanel logPanel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        logPanel.setOpaque(false);
        JScrollPane logScroll = new JScrollPane(eventLogArea);
        logScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        logScroll.getViewport().setBackground(Theme.SURFACE);
        logPanel.add(logScroll, BorderLayout.CENTER);

        JPanel logActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, Theme.GAP));
        logActionsPanel.setOpaque(false);
        refreshLogBtn.setEnabled(false);
        logActionsPanel.add(refreshLogBtn);
        logPanel.add(logActionsPanel, BorderLayout.SOUTH);

        adminTabs.addTab("Server Event Log", logPanel);

        adminTabsCard.setBody(adminTabs);

        // -------------------------------------------------------------
        // 3. BOTTOM CARD: smartrail:// Protocol & Content Inspector
        // -------------------------------------------------------------
        Card inspectorCard = new Card("smartrail:// Protocol & Content Handler Inspector");
        JPanel inspectorBody = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        inspectorBody.setOpaque(false);

        JPanel inspectorControls = new JPanel();
        inspectorControls.setOpaque(false);
        inspectorControls.setLayout(new BoxLayout(inspectorControls, BoxLayout.Y_AXIS));

        JPanel urlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        urlRow.setOpaque(false);
        JLabel urlLbl = new JLabel("Resource URL:");
        urlLbl.setFont(Theme.FONT_BODY_BOLD);
        urlLbl.setForeground(Theme.TEXT_MUTED);
        urlRow.add(urlLbl);
        urlRow.add(urlField);

        samplePathsCombo.addActionListener(e -> {
            String selected = (String) samplePathsCombo.getSelectedItem();
            if (selected != null) {
                urlField.setText("smartrail://localhost:" + config.getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT) + selected);
            }
        });
        urlRow.add(samplePathsCombo);
        urlRow.add(fetchResourceBtn);

        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 4));
        metaRow.setOpaque(false);
        metaRow.add(inspectorStatusPill);
        metaRow.add(contentTypePill);

        inspectorControls.add(urlRow);
        inspectorControls.add(Box.createVerticalStrut(Theme.GAP));
        inspectorControls.add(metaRow);

        inspectorBody.add(inspectorControls, BorderLayout.NORTH);

        JScrollPane resourceScroll = new JScrollPane(resourceOutputArea);
        resourceScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        resourceScroll.getViewport().setBackground(Theme.SURFACE);
        inspectorBody.add(resourceScroll, BorderLayout.CENTER);

        inspectorCard.setBody(inspectorBody);

        // Split pane separating Admin tabs and smartrail:// inspector
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, adminTabsCard, inspectorCard);
        splitPane.setResizeWeight(0.58);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setDividerSize(6);

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
                showNotice("Please enter a Section ID to report obstruction", Theme.WARN);
                return;
            }
            UiThreads.runInBackground(() -> {
                try {
                    admin.reportObstruction(sectionId, reason.isEmpty() ? "OBSTRUCTION" : reason);
                    showNotice("Obstruction reported on " + sectionId, Theme.OK);
                    refreshTracks();
                    refreshEventLog();
                } catch (Exception ex) {
                    showNotice("Failed to report obstruction: " + ex.getMessage(), Theme.DANGER);
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
            showNotice("Invalid RMI registry port: " + portText, Theme.DANGER);
            return;
        }

        connectBtn.setEnabled(false);
        rmiStatusPill.setStatus("Connecting...", Theme.WARN);
        showNotice("Connecting to RMI Registry at " + host + ":" + port + "...", Theme.TEXT_MUTED);

        UiThreads.runInBackground(() -> {
            try {
                RmiAdminClient client = new RmiAdminClient(config);
                RailwayAdminRemote stub = client.connect(host, port);
                admin = stub;
                UiThreads.onEdt(() -> {
                    connectBtn.setText("Disconnect");
                    connectBtn.setEnabled(true);
                    rmiStatusPill.setStatus("Connected (" + host + ":" + port + ")", Theme.OK);
                    showNotice("RMI stub connected successfully.", Theme.OK);
                    setControlsEnabled(true);
                    refreshAll();
                });
            } catch (Exception e) {
                UiThreads.onEdt(() -> {
                    connectBtn.setText("Connect");
                    connectBtn.setEnabled(true);
                    rmiStatusPill.setStatus("Failed", Theme.DANGER);
                    showNotice("Connection failed: " + e.getMessage(), Theme.DANGER);
                    setControlsEnabled(false);
                });
            }
        });
    }

    private void disconnect() {
        admin = null;
        connectBtn.setText("Connect");
        rmiStatusPill.setStatus("Disconnected", Theme.INACTIVE);
        showNotice("RMI connection closed.", Theme.TEXT_MUTED);
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
                showNotice("Error refreshing trains: " + e.getMessage(), Theme.DANGER);
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
                showNotice("Error refreshing tracks: " + e.getMessage(), Theme.DANGER);
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
                showNotice("Error refreshing event log: " + e.getMessage(), Theme.DANGER);
            }
        });
    }

    private void executeTrainCommand(String action, String trainId) {
        if (trainId.isEmpty()) {
            showNotice("Please select or enter a Target Train ID", Theme.WARN);
            return;
        }
        if (admin == null) return;

        showNotice("Executing " + action.toUpperCase() + " on " + trainId + "...", Theme.TEXT_MUTED);

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
                if (success) {
                    showNotice("Command " + action.toUpperCase() + " executed for " + trainId, Theme.OK);
                } else {
                    showNotice("Command " + action.toUpperCase() + " for " + trainId + " returned false (train not connected or held).", Theme.WARN);
                }
            } catch (Exception e) {
                showNotice("RMI command failed: " + e.getMessage(), Theme.DANGER);
            }
        });
    }

    private void fetchSmartRailResource() {
        String urlString = urlField.getText().trim();
        if (urlString.isEmpty()) return;

        fetchResourceBtn.setEnabled(false);
        inspectorStatusPill.setStatus("Connecting...", Theme.WARN);
        contentTypePill.setStatus("MIME: resolving", Theme.INACTIVE);
        resourceOutputArea.setText("Sending GET request to " + urlString + " ...\n");

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
                    inspectorStatusPill.setStatus("200 OK", Theme.OK);
                    contentTypePill.setStatus(contentType != null ? contentType : "unknown", Theme.PRIMARY, Theme.PRIMARY_SOFT, Theme.PRIMARY);

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
                    inspectorStatusPill.setStatus("Error", Theme.DANGER);
                    contentTypePill.setStatus("MIME: none", Theme.INACTIVE);
                    resourceOutputArea.setText("Failed to load resource:\n" + e.getMessage() + "\n\n"
                            + (e.getCause() != null ? "Cause: " + e.getCause().getMessage() : ""));
                });
            }
        });
    }

    private void showNotice(String message, Color color) {
        UiThreads.onEdt(() -> {
            inlineNoticeLabel.setForeground(color);
            inlineNoticeLabel.setText(message);
        });
    }
}
