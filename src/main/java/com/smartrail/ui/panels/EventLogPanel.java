package com.smartrail.ui.panels;

import com.smartrail.common.log.EventLog;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * OWNER: Member 1 - the live {@link EventLog}, filtered by the module that wrote the line.
 */
public final class EventLogPanel extends JPanel {

    private static final String[] SOURCES = {"All", "NIO", "UDP", "MULTICAST", "TLS", "RMI", "SERVER", "HEARTBEAT"};

    private final SimulationController controller;
    private final JComboBox<String> source = new JComboBox<>(SOURCES);
    private final JTextArea logArea = new JTextArea();

    public EventLogPanel(SimulationController controller) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.controller = controller;
        setOpaque(false);
        source.setFont(Theme.FONT_BODY);
        source.addActionListener(e -> render());
        logArea.setEditable(false);
        logArea.setFont(Theme.FONT_MONO);
        logArea.setBackground(Theme.SURFACE);
        logArea.setForeground(Theme.TEXT);

        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        filter.setOpaque(false);
        JLabel label = new JLabel("Source");
        label.setFont(Theme.FONT_BODY_BOLD);
        label.setForeground(Theme.TEXT_MUTED);
        filter.add(label);
        filter.add(source);

        Card card = new Card("Event log");
        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(filter, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.getViewport().setBackground(Theme.SURFACE);
        body.add(scroll, BorderLayout.CENTER);
        card.setBody(body);
        add(card, BorderLayout.CENTER);

        controller.eventLog().addListener(entry -> UiThreads.onEdt(this::render));
        render();
    }

    private void render() {
        String selected = String.valueOf(source.getSelectedItem());
        StringBuilder text = new StringBuilder();
        for (EventLog.Entry entry : controller.eventLog().snapshot()) {
            if (!"All".equals(selected) && !selected.equals(entry.source())) {
                continue;
            }
            text.append(entry.format()).append('\n');
        }
        logArea.setText(text.toString());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
