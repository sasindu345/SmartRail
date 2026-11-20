package com.smartrail.ui.panels;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.protocol.Message;
import com.smartrail.multicast.MulticastAlertSubscriber;
import com.smartrail.udp.DiscoveredServer;
import com.smartrail.udp.DiscoveryClient;
import com.smartrail.ui.shell.UiThreads;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * OWNER: Member 3.
 *
 * <p><b>Discover</b> broadcasts a UDP {@code DISCOVER} and either fills in the result (handed to the TCP
 * panel through {@code onDiscovered}) or reports a timeout, in which case the Train tab still accepts a
 * manually typed host/IP. <b>Subscribe</b> joins the multicast alert group and lists every
 * {@code EMERGENCY_ALERT} received, with the time it arrived.
 *
 * <p>Rules: network calls via {@link UiThreads#runInBackground}, UI updates via {@link UiThreads#onEdt}.
 */
public final class UdpMulticastPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkConfig config;
    private final Consumer<DiscoveredServer> onDiscovered;

    private final JButton discoverButton = new JButton("Discover server");
    private final JLabel discoveryResultLabel = new JLabel("Not discovered yet.");
    private final JButton subscribeButton = new JButton("Subscribe to alerts");
    private final DefaultListModel<String> alertListModel = new DefaultListModel<>();

    private volatile MulticastAlertSubscriber subscriber;

    public UdpMulticastPanel(NetworkConfig config, Consumer<DiscoveredServer> onDiscovered) {
        super(new BorderLayout(8, 8));
        this.config = config;
        this.onDiscovered = onDiscovered;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel discoveryRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        discoveryRow.add(discoverButton);
        discoveryRow.add(discoveryResultLabel);
        discoverButton.addActionListener(e -> discover());

        JList<String> alertList = new JList<>(alertListModel);
        JPanel alertsPanel = new JPanel(new BorderLayout(4, 4));
        alertsPanel.add(new JLabel("Emergency alerts received (multicast, or unicast fallback):"), BorderLayout.NORTH);
        alertsPanel.add(new JScrollPane(alertList), BorderLayout.CENTER);
        subscribeButton.addActionListener(e -> toggleSubscription());
        JPanel subscribeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        subscribeRow.add(subscribeButton);
        alertsPanel.add(subscribeRow, BorderLayout.SOUTH);

        add(discoveryRow, BorderLayout.NORTH);
        add(alertsPanel, BorderLayout.CENTER);
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
                    discoveryResultLabel.setText("No response - timed out. Enter the server IP manually in the Train tab.");
                }
            });
        });
    }

    private void toggleSubscription() {
        if (subscriber == null) {
            MulticastAlertSubscriber newSubscriber = new MulticastAlertSubscriber(config, this::onAlertReceived);
            subscriber = newSubscriber;
            subscribeButton.setText("Unsubscribe");
            UiThreads.runInBackground(() -> {
                try {
                    newSubscriber.start();
                } catch (RuntimeException e) {
                    subscriber = null;
                    UiThreads.onEdt(() -> {
                        subscribeButton.setText("Subscribe to alerts");
                        alertListModel.addElement("Could not join multicast group: " + e.getMessage());
                    });
                }
            });
        } else {
            stopSubscription();
        }
    }

    private void onAlertReceived(Message alert) {
        String line = LocalTime.now().format(TIME_FORMAT) + "  EMERGENCY_ALERT  section=" + alert.field(0)
                + " reason=" + alert.field(1);
        UiThreads.onEdt(() -> alertListModel.addElement(line));
    }

    /**
     * Leaves the multicast group if subscribed. Safe to call more than once. Exposed so the owning window
     * can stop the subscription when the user closes it.
     */
    public void stopSubscription() {
        MulticastAlertSubscriber current = subscriber;
        subscriber = null;
        subscribeButton.setText("Subscribe to alerts");
        if (current != null) {
            UiThreads.runInBackground(current::stop);
        }
    }
}
