package com.smartrail.ui.panels;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.udp.DiscoveredServer;
import com.smartrail.udp.DiscoveryClient;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.NetworkStatusBar;
import com.smartrail.ui.components.TableStyles;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * OWNER: Member 3 — the Network screen.
 *
 * <p><b>Discover</b> broadcasts a UDP {@code DISCOVER} and shows the reply, or the timeout when
 * nothing answers. The services table is the same state as the status bar. The interface list
 * classifies each address on this machine.
 *
 * <p>Rules: network calls via {@link UiThreads#runInBackground}, UI updates via {@link UiThreads#onEdt}.
 */
public final class UdpMulticastPanel extends JPanel {

    private static final String[] SERVICE_KEYS = {
            NetworkStatusBar.SERVICE_TCP_NIO,
            NetworkStatusBar.SERVICE_UDP,
            NetworkStatusBar.SERVICE_TLS,
            NetworkStatusBar.SERVICE_MULTICAST,
            NetworkStatusBar.SERVICE_RMI,
            NetworkStatusBar.SERVICE_HTTP
    };

    private final NetworkConfig config;
    private final SimulationController controller;
    private final Consumer<DiscoveredServer> onDiscovered;

    private final JButton discoverButton = new JButton("Discover server");
    private final JLabel discoveryResultLabel = new JLabel("Not discovered yet.");
    private final DefaultTableModel servicesModel = new DefaultTableModel(
            new String[]{"Service", "Status", "Endpoint"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea interfaces = new JTextArea();

    public UdpMulticastPanel(SimulationController controller, Consumer<DiscoveredServer> onDiscovered) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.controller = controller;
        this.config = controller.config();
        this.onDiscovered = onDiscovered;
        setOpaque(false);

        discoverButton.setFont(Theme.FONT_BODY);
        discoveryResultLabel.setFont(Theme.FONT_BODY);
        discoveryResultLabel.setForeground(Theme.TEXT);
        discoverButton.addActionListener(e -> discover());

        JPanel discoveryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        discoveryRow.setOpaque(false);
        discoveryRow.add(discoverButton);
        discoveryRow.add(discoveryResultLabel);
        Card discoverCard = new Card("Discover server");
        discoverCard.setBody(discoveryRow);

        JTable servicesTable = new JTable(servicesModel);
        TableStyles.apply(servicesTable);
        Card servicesCard = new Card("Services and ports");
        servicesCard.setBody(new JScrollPane(servicesTable));

        interfaces.setEditable(false);
        interfaces.setFont(Theme.FONT_MONO);
        interfaces.setForeground(Theme.TEXT);
        interfaces.setBackground(Theme.SURFACE);
        interfaces.setLineWrap(true);
        interfaces.setWrapStyleWord(true);
        interfaces.setText("Reading interfaces…");
        Card interfaceCard = new Card("This machine");
        interfaceCard.setBody(new JScrollPane(interfaces));

        JPanel tables = new JPanel(new GridLayout(2, 1, Theme.GAP, Theme.GAP));
        tables.setOpaque(false);
        tables.add(servicesCard);
        tables.add(interfaceCard);

        add(discoverCard, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);

        controller.addStatusListener(states -> UiThreads.onEdt(() -> fillServices(states)));
        UiThreads.runInBackground(() -> {
            String text = interfaceReport();
            UiThreads.onEdt(() -> interfaces.setText(text));
        });
    }

    private void discover() {
        discoverButton.setEnabled(false);
        discoveryResultLabel.setText("Discovering...");
        UiThreads.runInBackground(() -> {
            Optional<DiscoveredServer> result = new DiscoveryClient(config).discover();
            UiThreads.onEdt(() -> {
                discoverButton.setEnabled(true);
                if (result.isPresent()) {
                    DiscoveredServer server = result.get();
                    discoveryResultLabel.setText("Found " + server.host() + " (plain:" + server.plainPort()
                            + " tls:" + server.tlsPort() + ")");
                    onDiscovered.accept(server);
                } else {
                    discoveryResultLabel.setText(
                            "No response - timed out. Enter the server IP manually in the Train tab.");
                }
            });
        });
    }

    private void fillServices(java.util.Map<String, SimulationController.ServiceState> states) {
        if (servicesModel.getRowCount() != SERVICE_KEYS.length) {
            servicesModel.setRowCount(0);
            for (String key : SERVICE_KEYS) {
                servicesModel.addRow(serviceRow(key, states.get(key)));
            }
            return;
        }
        for (int i = 0; i < SERVICE_KEYS.length; i++) {
            Object[] next = serviceRow(SERVICE_KEYS[i], states.get(SERVICE_KEYS[i]));
            for (int column = 0; column < next.length; column++) {
                if (!next[column].equals(servicesModel.getValueAt(i, column))) {
                    servicesModel.setValueAt(next[column], i, column);
                }
            }
        }
    }

    private static Object[] serviceRow(String key, SimulationController.ServiceState state) {
        if (state == null || state.status() == NetworkStatusBar.ServiceStatus.STOPPED) {
            return new Object[]{key, "Not started", "—"};
        }
        if (state.status() == NetworkStatusBar.ServiceStatus.FAILED) {
            String reason = state.endpointOrError() == null ? "failed" : state.endpointOrError();
            return new Object[]{key, "Failed", reason};
        }
        String endpoint = state.endpointOrError() == null ? "—" : state.endpointOrError();
        return new Object[]{key, "Started", endpoint};
    }

    private static String interfaceReport() {
        Enumeration<NetworkInterface> found;
        try {
            found = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return "Could not list network interfaces: " + e.getMessage();
        }
        if (found == null) {
            return "No network interfaces.";
        }
        StringBuilder report = new StringBuilder();
        for (NetworkInterface iface : Collections.list(found)) {
            try {
                if (!iface.isUp()) {
                    continue;
                }
            } catch (SocketException e) {
                continue;
            }
            for (InetAddress address : Collections.list(iface.getInetAddresses())) {
                if (!report.isEmpty()) {
                    report.append('\n');
                }
                report.append(iface.getName()).append(' ').append(describe(address));
            }
        }
        return report.isEmpty() ? "No interfaces are up." : report.toString();
    }

    private static String describe(InetAddress address) {
        String family = address instanceof Inet6Address ? "IPv6" : address instanceof Inet4Address ? "IPv4" : "IP";
        return address.getHostAddress() + " (" + family + ", " + kind(address) + ")";
    }

    private static String kind(InetAddress address) {
        if (address.isMulticastAddress()) {
            return "multicast";
        }
        if (address.isLoopbackAddress()) {
            return "loopback";
        }
        if (address.isLinkLocalAddress()) {
            return "link-local";
        }
        if (address.isSiteLocalAddress()) {
            return "site-local";
        }
        return "public";
    }
}
