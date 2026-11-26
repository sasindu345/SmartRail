package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Control bar for simulation and process lifecycle.
 * Train buttons come from the railway data, plus Reset and Stop All.
 * Owner: Member 3 (control-center-spec.md §3, design-system.md §4).
 */
public class SimulationControlBar extends JPanel {

    private final JButton serverToggleBtn;
    private final List<JButton> trainButtons = new ArrayList<>();
    private final JButton resetBtn;
    private final JButton stopAllBtn;

    private boolean serverRunning = false;

    public interface ControlListener {
        void onToggleServer();
        void onLaunchTrain(String trainId, boolean tls);
        void onReset();
        void onStopAll();
    }

    public SimulationControlBar(ControlListener listener, List<String> trainIds) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(Theme.GAP, Theme.PAD, Theme.GAP, Theme.PAD)
        ));
        setPreferredSize(new Dimension(0, 46));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        serverToggleBtn = new JButton("▶ Start Server");
        serverToggleBtn.setFont(Theme.FONT_BODY_BOLD);
        serverToggleBtn.setFocusPainted(false);
        serverToggleBtn.setBackground(Theme.PRIMARY);
        serverToggleBtn.setForeground(Theme.SURFACE);
        serverToggleBtn.addActionListener(e -> {
            if (listener != null) {
                listener.onToggleServer();
            }
        });

        buttonPanel.add(serverToggleBtn);
        buttonPanel.add(Box.createHorizontalStrut(Theme.PAD));
        for (String trainId : trainIds) {
            JButton button = new JButton("+" + trainId);
            button.setFont(Theme.FONT_BODY_BOLD);
            button.setFocusPainted(false);
            button.addActionListener(e -> {
                if (listener != null) {
                    listener.onLaunchTrain(trainId, false);
                }
            });
            trainButtons.add(button);
            buttonPanel.add(button);
            buttonPanel.add(Box.createHorizontalStrut(Theme.GAP));
        }

        resetBtn = new JButton("Reset");
        resetBtn.setFont(Theme.FONT_BODY_BOLD);
        resetBtn.setFocusPainted(false);
        resetBtn.addActionListener(e -> {
            if (listener != null) {
                listener.onReset();
            }
        });

        stopAllBtn = new JButton("■ Stop All");
        stopAllBtn.setFont(Theme.FONT_BODY_BOLD);
        stopAllBtn.setFocusPainted(false);
        stopAllBtn.setForeground(Theme.DANGER);
        stopAllBtn.addActionListener(e -> {
            if (listener != null) {
                listener.onStopAll();
            }
        });

        buttonPanel.add(resetBtn);
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(stopAllBtn);

        add(buttonPanel, BorderLayout.CENTER);
    }

    public void setServerRunning(boolean running) {
        this.serverRunning = running;
        if (running) {
            serverToggleBtn.setText("■ Stop Server");
            serverToggleBtn.setBackground(Theme.WARN);
            serverToggleBtn.setForeground(Theme.TEXT);
        } else {
            serverToggleBtn.setText("▶ Start Server");
            serverToggleBtn.setBackground(Theme.PRIMARY);
            serverToggleBtn.setForeground(Theme.SURFACE);
        }
    }

    public boolean isServerRunning() {
        return serverRunning;
    }

    /**
     * Disable all buttons while an action is in flight (control-center-spec.md §3).
     */
    public void setActionsInFlight(boolean inFlight) {
        serverToggleBtn.setEnabled(!inFlight);
        for (JButton button : trainButtons) {
            button.setEnabled(!inFlight);
        }
        resetBtn.setEnabled(!inFlight);
        stopAllBtn.setEnabled(!inFlight);
    }
}
