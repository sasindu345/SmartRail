package com.smartrail.ui.panels;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.tcpclient.TrainClient;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * OWNER: Member 2.
 * <ul>
 *   <li>TODO(M2) host/IP + port fields (defaults from config), Resolve button showing InetAddress details</li>
 *   <li>TODO(M2) Connect / Disconnect / Start simulation buttons; connection + route + section status</li>
 *   <li>TODO(M2) message log of sent/received protocol lines; HOLD/RESUME/EMERGENCY_STOP clearly visible</li>
 * </ul>
 * Rules: network calls via UiThreads.runInBackground, UI updates via UiThreads.onEdt.
 */
public final class TcpClientPanel extends JPanel {

    private final NetworkConfig config;
    private final TrainClient client;

    public TcpClientPanel(NetworkConfig config, TrainClient client) {
        super(new BorderLayout());
        this.config = config;
        this.client = client;
        add(placeholder("Member 2 - TCP client & IP addressing for " + client.train().trainId()), BorderLayout.CENTER);
    }

    /** Called by Member 3's discovery panel when a server is found (fills host/port fields). */
    public void useDiscoveredServer(com.smartrail.udp.DiscoveredServer server) {
        // TODO(M2): fill host + the port that matches client.socketFactory().transport()
    }

    private static JLabel placeholder(String text) {
        return new JLabel("<html><center>" + text + "<br>(not implemented yet)</center></html>", SwingConstants.CENTER);
    }
}
