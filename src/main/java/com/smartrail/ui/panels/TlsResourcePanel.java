package com.smartrail.ui.panels;

import com.smartrail.common.config.NetworkConfig;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * OWNER: Member 4.
 * <ul>
 *   <li>TODO(M4) resource selector (schedules, trains, tracks/{id}) + Fetch via ResourceClient (URLConnection)</li>
 *   <li>TODO(M4) show HTTP status, Content-Type, body; typed view when fetchContent() works</li>
 *   <li>TODO(M4) TLS check: connect with TlsSocketFactory and show protocol, cipher suite, peer certificate subject</li>
 * </ul>
 * Rules: network calls via UiThreads.runInBackground, UI updates via UiThreads.onEdt.
 */
public final class TlsResourcePanel extends JPanel {

    private final NetworkConfig config;

    public TlsResourcePanel(NetworkConfig config) {
        super(new BorderLayout());
        this.config = config;
        add(placeholder("Member 4 - TLS status & URLConnection resources"), BorderLayout.CENTER);
    }

    private static JLabel placeholder(String text) {
        return new JLabel("<html><center>" + text + "<br>(not implemented yet)</center></html>", SwingConstants.CENTER);
    }
}
