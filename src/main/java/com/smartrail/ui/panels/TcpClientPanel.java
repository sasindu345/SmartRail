package com.smartrail.ui.panels;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.Transport;
import com.smartrail.common.service.ClientSocketFactory;
import com.smartrail.tcpclient.PlainSocketFactory;
import com.smartrail.tcpclient.ServerAddressResolver;
import com.smartrail.tcpclient.TrainClient;
import com.smartrail.tcpclient.TrainClientListener;
import com.smartrail.tls.TlsSocketFactory;
import com.smartrail.udp.DiscoveredServer;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.StatusPill;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OWNER: Member 2 — Connection &amp; IP addressing details for the selected train (UI-2).
 *
 * <p>Shows host/IP and port fields defaulted from {@link NetworkConfig}, resolves host names and
 * IPv4/IPv6 literals through {@link ServerAddressResolver}, and probes TCP/TLS connectivity off
 * the Swing EDT so deliberate wrong-host or wrong-port errors appear clearly without freezing
 * the window.
 */
public final class TcpClientPanel extends JPanel {

    private final NetworkConfig config;
    private final TrainClient client;
    private final ServerAddressResolver resolver = new ServerAddressResolver();

    private final JLabel trainHeader = new JLabel("Selected train: —");
    private final StatusPill statusPill = new StatusPill("Not tested", Theme.INACTIVE);
    private final JTextField hostField = new JTextField(14);
    private final JTextField portField = new JTextField(6);
    private final JComboBox<Transport> transportCombo = new JComboBox<>(Transport.values());
    private final JButton resolveBtn = new JButton("Resolve");
    private final JButton testConnectBtn = new JButton("Test Connect");
    private final JLabel resolvedLabel = new JLabel("Resolved IP: not resolved yet");
    private final JLabel remoteEndpointLabel = new JLabel("Remote endpoint: not connected");
    private final JTextArea resultArea = new JTextArea(3, 28);

    public TcpClientPanel(NetworkConfig config) {
        this(config, null);
    }

    public TcpClientPanel(NetworkConfig config, TrainClient client) {
        super(new BorderLayout());
        this.config = config;
        this.client = client;
        setOpaque(false);

        Transport initialTransport = client != null
                ? client.socketFactory().transport()
                : parseTransport(config.get(ConfigKeys.CLIENT_TRANSPORT));
        hostField.setText(config.get(ConfigKeys.CLIENT_SERVER_HOST));
        transportCombo.setSelectedItem(initialTransport);
        portField.setText(String.valueOf(defaultPortFor(initialTransport)));

        if (client != null) {
            trainHeader.setText(client.train().trainId() + " — " + client.train().name());
            transportCombo.setEnabled(false);
            attachClientListener(client);
        }

        styleComponents();
        wireActions();

        Card card = new Card("Connection & IP Resolution");
        card.setBody(buildBody());
        add(card, BorderLayout.CENTER);
    }

    private void styleComponents() {
        trainHeader.setFont(Theme.FONT_BODY_BOLD);
        trainHeader.setForeground(Theme.TEXT);

        hostField.setFont(Theme.FONT_BODY);
        portField.setFont(Theme.FONT_BODY);
        transportCombo.setFont(Theme.FONT_BODY);
        resolveBtn.setFont(Theme.FONT_BODY);
        testConnectBtn.setFont(Theme.FONT_BODY_BOLD);

        resolvedLabel.setFont(Theme.FONT_SMALL);
        resolvedLabel.setForeground(Theme.TEXT_MUTED);
        remoteEndpointLabel.setFont(Theme.FONT_SMALL);
        remoteEndpointLabel.setForeground(Theme.TEXT_MUTED);

        resultArea.setEditable(false);
        resultArea.setFont(Theme.FONT_MONO);
        resultArea.setForeground(Theme.TEXT);
        resultArea.setBackground(Theme.SURFACE);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setText("Click Resolve to inspect IPv4/IPv6 addressing, or enter a wrong port and click Test Connect.");
    }

    private JPanel buildBody() {
        JPanel topRow = new JPanel(new BorderLayout(Theme.GAP, 0));
        topRow.setOpaque(false);
        topRow.add(trainHeader, BorderLayout.WEST);
        topRow.add(statusPill, BorderLayout.EAST);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fieldsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        fieldsRow.setOpaque(false);
        fieldsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldsRow.add(mutedLabel("Host"));
        fieldsRow.add(hostField);
        fieldsRow.add(mutedLabel("Port"));
        fieldsRow.add(portField);
        fieldsRow.add(mutedLabel("Transport"));
        fieldsRow.add(transportCombo);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.add(resolveBtn);
        actionRow.add(testConnectBtn);

        JPanel infoCol = new JPanel();
        infoCol.setOpaque(false);
        infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
        infoCol.add(topRow);
        infoCol.add(Box.createVerticalStrut(Theme.GAP));
        infoCol.add(fieldsRow);
        infoCol.add(Box.createVerticalStrut(Theme.GAP));
        infoCol.add(actionRow);
        infoCol.add(Box.createVerticalStrut(Theme.GAP));
        resolvedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        remoteEndpointLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCol.add(resolvedLabel);
        infoCol.add(Box.createVerticalStrut(Theme.GAP / 2));
        infoCol.add(remoteEndpointLabel);

        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        resultScroll.setPreferredSize(new Dimension(320, 68));

        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(infoCol, BorderLayout.NORTH);
        body.add(resultScroll, BorderLayout.CENTER);
        return body;
    }

    private void wireActions() {
        transportCombo.addActionListener(e -> {
            Transport chosen = (Transport) transportCombo.getSelectedItem();
            if (chosen == null) {
                return;
            }
            String currentPort = portField.getText().trim();
            String plainDefault = String.valueOf(config.getInt(ConfigKeys.NIO_TCP_PORT));
            String tlsDefault = String.valueOf(config.getInt(ConfigKeys.TLS_PORT));
            if (currentPort.isEmpty() || currentPort.equals(plainDefault) || currentPort.equals(tlsDefault)) {
                portField.setText(String.valueOf(defaultPortFor(chosen)));
            }
        });
        resolveBtn.addActionListener(e -> resolveHost());
        testConnectBtn.addActionListener(e -> testConnection());
    }

    /** Updates the connection card to reflect the currently selected train in {@link TrainsPanel}. */
    public void showTrain(TrainDefinition definition, TrainInfo liveInfo, boolean processRunning, String resolvedFromProcess) {
        if (definition == null) {
            trainHeader.setText("Selected train: —");
            remoteEndpointLabel.setText("Remote endpoint: not connected");
            return;
        }
        String suffix = liveInfo != null && liveInfo.transport() != null
                ? " (" + liveInfo.transport().name() + ")"
                : "";
        trainHeader.setText(definition.trainId() + " — " + definition.name() + suffix);

        if (resolvedFromProcess != null && !resolvedFromProcess.isBlank()) {
            resolvedLabel.setText("Resolved IP: " + resolvedFromProcess);
        } else if (!processRunning && liveInfo == null) {
            resolvedLabel.setText("Resolved IP: not resolved yet");
        }

        if (liveInfo != null && liveInfo.remoteAddress() != null) {
            remoteEndpointLabel.setText("Remote endpoint: " + liveInfo.remoteAddress()
                    + " (" + liveInfo.status().name() + ")");
        } else if (processRunning) {
            remoteEndpointLabel.setText("Remote endpoint: connecting...");
        } else {
            remoteEndpointLabel.setText("Remote endpoint: not connected");
        }
    }

    /** Called when UDP discovery finds a server (fills host + port matching the selected transport). */
    public void useDiscoveredServer(DiscoveredServer server) {
        if (server == null) {
            return;
        }
        UiThreads.onEdt(() -> {
            Transport transport = (Transport) transportCombo.getSelectedItem();
            int port = transport == Transport.TLS ? server.tlsPort() : server.plainPort();
            hostField.setText(server.host());
            portField.setText(String.valueOf(port));
            statusPill.setStatus("Discovered", Theme.OK, Theme.SURFACE, Theme.TEXT);
            resultArea.setForeground(Theme.TEXT);
            resultArea.setText("Discovered server " + server.host() + " (plain:" + server.plainPort()
                    + ", tls:" + server.tlsPort() + ")");
        });
    }

    private void resolveHost() {
        String host = hostField.getText().trim();
        setActionsEnabled(false);
        statusPill.setStatus("Resolving...", Theme.WARN, Theme.SURFACE, Theme.TEXT);
        UiThreads.runInBackground(() -> {
            try {
                List<InetAddress> addresses = resolver.resolveAll(host);
                String primary = resolver.describe(addresses.get(0));
                String all = addresses.stream()
                        .map(resolver::describe)
                        .collect(Collectors.joining("\n"));
                UiThreads.onEdt(() -> {
                    resolvedLabel.setText("Resolved IP: " + primary);
                    statusPill.setStatus("Resolved", Theme.OK, Theme.SURFACE, Theme.TEXT);
                    resultArea.setForeground(Theme.TEXT);
                    resultArea.setText(host + " ->\n" + all);
                    setActionsEnabled(true);
                });
            } catch (UnknownHostException e) {
                String message = e.getMessage() == null ? "Unknown host: " + host : e.getMessage();
                UiThreads.onEdt(() -> {
                    statusPill.setStatus("Resolve failed", Theme.DANGER, Theme.SURFACE, Theme.TEXT);
                    resolvedLabel.setText("Resolved IP: " + message);
                    resultArea.setForeground(Theme.DANGER);
                    resultArea.setText(message);
                    setActionsEnabled(true);
                });
            }
        });
    }

    private void testConnection() {
        String host = hostField.getText().trim();
        Integer port = parsePort(portField.getText().trim());
        if (port == null) {
            statusPill.setStatus("Invalid port", Theme.DANGER, Theme.SURFACE, Theme.TEXT);
            resultArea.setForeground(Theme.DANGER);
            resultArea.setText("Invalid port: enter a number between 1 and 65535.");
            return;
        }
        Transport transport = (Transport) transportCombo.getSelectedItem();
        if (transport == null) {
            transport = Transport.PLAIN;
        }
        setActionsEnabled(false);
        statusPill.setStatus("Connecting...", Theme.WARN, Theme.SURFACE, Theme.TEXT);

        if (client != null) {
            UiThreads.runInBackground(() -> {
                client.connect(host, port);
                UiThreads.onEdt(() -> setActionsEnabled(true));
            });
            return;
        }

        Transport chosenTransport = transport;
        int timeoutMs = config.getInt(ConfigKeys.CLIENT_CONNECT_TIMEOUT_MS);
        UiThreads.runInBackground(() -> {
            try {
                InetAddress address = resolver.resolve(host);
                String described = resolver.describe(address);
                ClientSocketFactory factory = chosenTransport == Transport.TLS
                        ? new TlsSocketFactory(config)
                        : new PlainSocketFactory();
                try (Socket socket = factory.connect(new InetSocketAddress(address, port), timeoutMs)) {
                    String endpoint = socket.getRemoteSocketAddress().toString();
                    UiThreads.onEdt(() -> {
                        resolvedLabel.setText("Resolved IP: " + described);
                        statusPill.setStatus("Reachable (" + chosenTransport + ")", Theme.OK, Theme.SURFACE, Theme.TEXT);
                        resultArea.setForeground(Theme.TEXT);
                        resultArea.setText("Connected cleanly to " + endpoint + "\nResolved: " + described);
                        setActionsEnabled(true);
                    });
                }
            } catch (IOException | RuntimeException e) {
                String message = clearError(host, port, e);
                UiThreads.onEdt(() -> {
                    statusPill.setStatus("Failed", Theme.DANGER, Theme.SURFACE, Theme.TEXT);
                    resultArea.setForeground(Theme.DANGER);
                    resultArea.setText(message);
                    setActionsEnabled(true);
                });
            }
        });
    }

    private void attachClientListener(TrainClient target) {
        target.addListener(new TrainClientListener() {
            @Override
            public void onConnected(String serverAddress) {
                UiThreads.onEdt(() -> {
                    statusPill.setStatus("Connected", Theme.OK, Theme.SURFACE, Theme.TEXT);
                    remoteEndpointLabel.setText("Remote endpoint: " + serverAddress);
                    resultArea.setForeground(Theme.TEXT);
                    resultArea.setText("Connected and sent REGISTER to " + serverAddress);
                });
            }

            @Override
            public void onDisconnected(String reason) {
                UiThreads.onEdt(() -> {
                    statusPill.setStatus("Disconnected", Theme.INACTIVE, Theme.SURFACE, Theme.TEXT);
                    resultArea.setForeground(Theme.TEXT_MUTED);
                    resultArea.setText("Disconnected: " + reason);
                });
            }

            @Override
            public void onError(String error) {
                UiThreads.onEdt(() -> {
                    statusPill.setStatus("Error", Theme.DANGER, Theme.SURFACE, Theme.TEXT);
                    resultArea.setForeground(Theme.DANGER);
                    resultArea.setText(error);
                });
            }
        });
    }

    private void setActionsEnabled(boolean enabled) {
        resolveBtn.setEnabled(enabled);
        testConnectBtn.setEnabled(enabled);
    }

    private int defaultPortFor(Transport transport) {
        return transport == Transport.TLS
                ? config.getInt(ConfigKeys.TLS_PORT)
                : config.getInt(ConfigKeys.NIO_TCP_PORT);
    }

    private static Transport parseTransport(String value) {
        try {
            return Transport.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            return Transport.PLAIN;
        }
    }

    private static Integer parsePort(String raw) {
        try {
            int parsed = Integer.parseInt(raw);
            return parsed >= 1 && parsed <= 65535 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clearError(String host, int port, Throwable error) {
        if (error instanceof UnknownHostException) {
            return error.getMessage() == null ? "Unknown host: " + host : error.getMessage();
        }
        if (error instanceof SocketTimeoutException) {
            return "Timed out connecting to " + host + ":" + port;
        }
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "connection failed";
        }
        return "Could not connect to " + host + ":" + port + " (" + detail + ")";
    }

    private static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_SMALL_BOLD);
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }
}
