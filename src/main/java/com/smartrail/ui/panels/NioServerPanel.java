package com.smartrail.ui.panels;

import com.smartrail.common.service.ServerContext;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * OWNER: Member 1.
 * <ul>
 *   <li>TODO(M1) table of trains (id, status, location, transport, remote IP, last seen) from railwayService.getTrains()</li>
 *   <li>TODO(M1) table of sections (state, holder) from getTracks(); refresh with a javax.swing.Timer</li>
 *   <li>TODO(M1) live event log (ctx.eventLog().addListener)</li>
 * </ul>
 * Rules: network calls via UiThreads.runInBackground, UI updates via UiThreads.onEdt.
 */
public final class NioServerPanel extends JPanel {

    private final ServerContext ctx;

    public NioServerPanel(ServerContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        add(placeholder("Member 1 - Java NIO server: connected trains, track sections, event log"), BorderLayout.CENTER);
    }

    private static JLabel placeholder(String text) {
        return new JLabel("<html><center>" + text + "<br>(not implemented yet)</center></html>", SwingConstants.CENTER);
    }
}
