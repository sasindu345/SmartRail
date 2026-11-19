package com.smartrail.ui.shell;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.multicast.MulticastAlertSubscriber;
import com.smartrail.tcpclient.TrainClient;
import com.smartrail.ui.panels.TcpClientPanel;
import com.smartrail.ui.panels.UdpMulticastPanel;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

/**
 * OWNER: Member 2 (application shell). One window per train client process.
 * Tab 1 = Member 2's TCP client panel, Tab 2 = Member 3's discovery/alerts panel.
 * <ul>
 *   <li>TODO(M2) on close: disconnect client and stop the alert subscriber</li>
 * </ul>
 */
public final class TrainClientWindow extends JFrame {

    public TrainClientWindow(NetworkConfig config, TrainClient client, MulticastAlertSubscriber alerts) {
        super("SmartRail Train " + client.train().trainId() + " - " + client.train().name() + " (simulation)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        TcpClientPanel tcpPanel = new TcpClientPanel(config, client);
        UdpMulticastPanel udpPanel = new UdpMulticastPanel(config, tcpPanel::useDiscoveredServer);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Train (TCP / IP)", tcpPanel);
        tabs.addTab("Discovery & alerts (UDP)", udpPanel);
        add(tabs);
        setSize(700, 500);
        setLocationByPlatform(true);
    }
}
