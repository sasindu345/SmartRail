package com.smartrail.ui.panels;

import com.smartrail.common.config.NetworkConfig;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * OWNER: Member 5.
 * <ul>
 *   <li>TODO(M5) RMI: Connect, Active trains, Track status, Hold/Resume/Emergency stop, Report obstruction, Event log</li>
 *   <li>TODO(M5) smartrail:// box: open URL, show content type and the typed object's fields</li>
 * </ul>
 * Rules: network calls via UiThreads.runInBackground, UI updates via UiThreads.onEdt.
 */
public final class RmiHandlerPanel extends JPanel {

    private final NetworkConfig config;

    public RmiHandlerPanel(NetworkConfig config) {
        super(new BorderLayout());
        this.config = config;
        add(placeholder("Member 5 - RMI administration, smartrail:// protocol & content handlers"), BorderLayout.CENTER);
    }

    private static JLabel placeholder(String text) {
        return new JLabel("<html><center>" + text + "<br>(not implemented yet)</center></html>", SwingConstants.CENTER);
    }
}
