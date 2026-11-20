package com.smartrail.ui.shell;

import com.smartrail.common.log.EventLog;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.NavSidebar;
import com.smartrail.ui.components.NetworkStatusBar;
import com.smartrail.ui.components.SimulationControlBar;
import com.smartrail.ui.components.StatusPill;
import com.smartrail.ui.components.TableStyles;
import com.smartrail.ui.components.TopBar;
import com.smartrail.ui.panels.RmiHandlerPanel;
import com.smartrail.ui.panels.TlsResourcePanel;
import com.smartrail.ui.panels.UdpMulticastPanel;
import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
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

    // Per-train console areas for Trains screen
    private final Map<String, JTextArea> trainConsoles = new ConcurrentHashMap<>();
    private final JTabbedPane trainConsoleTabs = new JTabbedPane();

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
            public void onStopAll() {
                handleStopAll();
            }
        });

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
            appendTrainOutput("ALL", "[" + trainId + "] " + line);
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
        JPanel panel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        panel.setOpaque(false);

        Card card = new Card("Railway Network Overview");
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel info = new JLabel("Central Railway Traffic Network Map and Train Overview.");
        info.setFont(Theme.FONT_BODY);
        info.setForeground(Theme.TEXT_MUTED);

        JLabel status = new JLabel("● Ready for Member 1 UI-1 components (NetworkMapPanel, TrackSectionsPanel).");
        status.setFont(Theme.FONT_BODY);
        status.setForeground(Theme.PRIMARY);

        inner.add(info);
        inner.add(Box.createVerticalStrut(Theme.GAP));
        inner.add(status);
        inner.add(Box.createVerticalStrut(Theme.PAD));

        // Quick active services table
        String[][] data = {
                {"NIO TCP Server", "Port " + controller.config().getInt("nio.tcp.port")},
                {"UDP Discovery", "Port " + controller.config().getInt("udp.discovery.port")},
                {"Multicast Alerts", controller.config().get("multicast.group") + ":" + controller.config().getInt("multicast.port")},
                {"TLS Server", "Port " + controller.config().getInt("tls.port")}
        };
        JTable table = new JTable(data, new String[]{"Service Name", "Configured Endpoint"});
        TableStyles.apply(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(500, 160));
        inner.add(scroll);

        card.setBody(inner);
        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTrainsScreen() {
        JPanel panel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        panel.setOpaque(false);

        Card headerCard = new Card("Train Processes & Consoles");
        JPanel topInfo = new JPanel();
        topInfo.setOpaque(false);
        topInfo.setLayout(new BoxLayout(topInfo, BoxLayout.Y_AXIS));

        JLabel desc = new JLabel("Real separate child JVM processes spawned via TrainProcessSupervisor.");
        desc.setFont(Theme.FONT_BODY);
        desc.setForeground(Theme.TEXT_MUTED);

        topInfo.add(desc);
        topInfo.add(Box.createVerticalStrut(Theme.GAP));
        headerCard.setBody(topInfo);

        // Per-train console tabs (TR01, TR02, TR03, ALL)
        createTrainConsoleTab("TR01");
        createTrainConsoleTab("TR02");
        createTrainConsoleTab("TR03");
        createTrainConsoleTab("ALL");

        Card consoleCard = new Card("Child Process Console Output (Piped stdout / stderr)");
        consoleCard.setBody(trainConsoleTabs);

        panel.add(headerCard, BorderLayout.NORTH);
        panel.add(consoleCard, BorderLayout.CENTER);
        return panel;
    }

    private void createTrainConsoleTab(String trainId) {
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
        trainConsoleTabs.addTab("Train " + trainId, scroll);
    }

    private void appendTrainOutput(String trainId, String line) {
        JTextArea area = trainConsoles.get(trainId);
        if (area != null) {
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        }
    }

    private JPanel createTrackSectionsScreen() {
        JPanel panel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        panel.setOpaque(false);

        Card card = new Card("Track Sections & Mutual Exclusion");
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel info = new JLabel("Exclusive track sections coordination (SEC_A, SEC_B, SEC_C, SEC_D).");
        info.setFont(Theme.FONT_BODY);
        info.setForeground(Theme.TEXT_MUTED);

        JLabel status = new JLabel("● Ready for Member 1 UI-1 conflict demo (TrackSectionsPanel).");
        status.setFont(Theme.FONT_BODY);
        status.setForeground(Theme.PRIMARY);

        inner.add(info);
        inner.add(Box.createVerticalStrut(Theme.GAP));
        inner.add(status);

        card.setBody(inner);
        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAlertsScreen() {
        JPanel panel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        panel.setOpaque(false);

        Card card = new Card("Multicast Alerts Feed");
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel info = new JLabel("Live feed of EMERGENCY_ALERT messages received over IP multicast.");
        info.setFont(Theme.FONT_BODY);
        info.setForeground(Theme.TEXT_MUTED);

        JLabel status = new JLabel("● Ready for Member 3 UI-3 components (AlertsPanel).");
        status.setFont(Theme.FONT_BODY);
        status.setForeground(Theme.PRIMARY);

        inner.add(info);
        inner.add(Box.createVerticalStrut(Theme.GAP));
        inner.add(status);

        card.setBody(inner);
        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createNetworkScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        UdpMulticastPanel udpPanel = new UdpMulticastPanel(controller.config(), discovered -> {
            UiThreads.onEdt(() -> {
                // switch to trains view when server is discovered
            });
        });
        panel.add(udpPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSecurityScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        TlsResourcePanel tlsPanel = new TlsResourcePanel(controller.config());
        panel.add(tlsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAdminScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        RmiHandlerPanel rmiPanel = new RmiHandlerPanel(controller.config());
        panel.add(rmiPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createEventLogScreen() {
        JPanel panel = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        panel.setOpaque(false);

        Card card = new Card("Central Server Event Log");
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(Theme.FONT_MONO);
        logArea.setBackground(Theme.SURFACE);
        logArea.setForeground(Theme.TEXT);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // Periodic poll of EventLog to keep display fresh without blocking
        Timer logTimer = new Timer(1000, e -> {
            EventLog log = controller.eventLog();
            if (log != null) {
                StringBuilder sb = new StringBuilder();
                for (EventLog.Entry entry : log.snapshot()) {
                    sb.append(entry.toString()).append("\n");
                }
                logArea.setText(sb.toString());
            }
        });
        logTimer.start();

        card.setBody(scroll);
        panel.add(card, BorderLayout.CENTER);
        return panel;
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
        controlBar.setActionsInFlight(true);
        UiThreads.runInBackground(() -> {
            try {
                supervisor.startTrain(trainId, tls);
            } catch (IOException e) {
                appendTrainOutput(trainId, "[SUPERVISOR ERROR] Failed to spawn train: " + e.getMessage());
            } finally {
                UiThreads.onEdt(() -> {
                    controlBar.setActionsInFlight(false);
                    navSidebar.select(NavSidebar.TRAINS);
                });
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
