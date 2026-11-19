package com.smartrail.ui.shell;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.ui.panels.RmiHandlerPanel;
import com.smartrail.ui.panels.TlsResourcePanel;
import com.smartrail.ui.panels.UdpMulticastPanel;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

/**
 * OWNER: Member 2 (application shell). Railway controller / administrator dashboard (separate process).
 * Hosts Member 3, 4 and 5 panels. Each member owns their panel's content; Member 2 owns this frame.
 */
public final class ControllerDashboard extends JFrame {

    public ControllerDashboard(NetworkConfig config) {
        super("SmartRail Controller Dashboard (simulation)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Discovery & alerts (UDP/Multicast)", new UdpMulticastPanel(config, server -> { }));
        tabs.addTab("Resources (TLS / URLConnection)", new TlsResourcePanel(config));
        tabs.addTab("Admin (RMI / smartrail://)", new RmiHandlerPanel(config));
        add(tabs);
        setSize(1000, 650);
        setLocationByPlatform(true);
    }
}
