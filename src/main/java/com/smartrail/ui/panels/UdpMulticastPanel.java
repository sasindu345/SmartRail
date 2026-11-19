package com.smartrail.ui.panels;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.udp.DiscoveredServer;

import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * OWNER: Member 3.
 * <ul>
 *   <li>TODO(M3) Discover button -> DiscoveryClient.discover(); show result or "timeout - enter IP manually"</li>
 *   <li>TODO(M3) Subscribe/Unsubscribe to multicast alerts; list received EMERGENCY_ALERTs with time</li>
 * </ul>
 * Rules: network calls via UiThreads.runInBackground, UI updates via UiThreads.onEdt.
 */
public final class UdpMulticastPanel extends JPanel {

    private final NetworkConfig config;
    private final Consumer<DiscoveredServer> onDiscovered;

    public UdpMulticastPanel(NetworkConfig config, Consumer<DiscoveredServer> onDiscovered) {
        super(new BorderLayout());
        this.config = config;
        this.onDiscovered = onDiscovered;
        add(placeholder("Member 3 - UDP discovery, multicast alerts, heartbeat"), BorderLayout.CENTER);
    }

    private static JLabel placeholder(String text) {
        return new JLabel("<html><center>" + text + "<br>(not implemented yet)</center></html>", SwingConstants.CENTER);
    }
}
