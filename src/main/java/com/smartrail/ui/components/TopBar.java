package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Top bar chrome: product mark, simulation title, live status pill, and live date-time clock.
 * Owner: Member 3 (control-center-spec.md §3, design-system.md §4).
 */
public class TopBar extends JPanel {

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss");

    private final StatusPill onlinePill;
    private final JLabel clockLabel;
    private final Timer clockTimer;

    public TopBar() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(Theme.GAP, Theme.PAD, Theme.GAP, Theme.PAD)
        ));
        setPreferredSize(new Dimension(0, 48));

        // Left brand & title
        JPanel brandPanel = new JPanel();
        brandPanel.setOpaque(false);
        brandPanel.setLayout(new BoxLayout(brandPanel, BoxLayout.X_AXIS));

        JLabel brandLabel = new JLabel("SmartRail");
        brandLabel.setFont(Theme.FONT_TITLE);
        brandLabel.setForeground(Theme.PRIMARY);

        JLabel separator = new JLabel("  │  ");
        separator.setFont(Theme.FONT_TITLE);
        separator.setForeground(Theme.BORDER);

        JLabel subtitleLabel = new JLabel("Railway Control / Railway Traffic Coordination Simulator");
        subtitleLabel.setFont(Theme.FONT_SUBTITLE);
        subtitleLabel.setForeground(Theme.TEXT_MUTED);

        brandPanel.add(brandLabel);
        brandPanel.add(separator);
        brandPanel.add(subtitleLabel);

        // Right status pill & clock
        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));

        onlinePill = new StatusPill("Simulation Offline", Theme.INACTIVE);

        clockLabel = new JLabel(LocalDateTime.now().format(CLOCK_FORMAT));
        clockLabel.setFont(Theme.FONT_BODY_BOLD);
        clockLabel.setForeground(Theme.TEXT_MUTED);

        rightPanel.add(onlinePill);
        rightPanel.add(Box.createHorizontalStrut(Theme.PAD));
        rightPanel.add(clockLabel);

        add(brandPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);

        // Update clock every second
        clockTimer = new Timer(1000, e -> clockLabel.setText(LocalDateTime.now().format(CLOCK_FORMAT)));
        clockTimer.setRepeats(true);
        clockTimer.start();
    }

    public void setSimulationOnline(boolean online) {
        if (online) {
            onlinePill.setStatus("Simulation Online", Theme.OK, Theme.PRIMARY_SOFT, Theme.PRIMARY);
        } else {
            onlinePill.setStatus("Simulation Offline", Theme.INACTIVE, Theme.SURFACE, Theme.TEXT_MUTED);
        }
    }

    public void dispose() {
        if (clockTimer != null && clockTimer.isRunning()) {
            clockTimer.stop();
        }
    }
}
