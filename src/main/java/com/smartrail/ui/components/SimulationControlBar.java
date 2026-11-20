package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Control bar for simulation and process lifecycle:
 * [Start/Stop Server] [+TR01] [+TR02] [+TR03 (TLS)] [Stop All].
 * Owner: Member 3 (control-center-spec.md §3, design-system.md §4).
 */
public class SimulationControlBar extends JPanel {

    private final JButton serverToggleBtn;
    private final JButton tr01Btn;
    private final JButton tr02Btn;
    private final JButton tr03Btn;
    private final JButton stopAllBtn;

    private boolean serverRunning = false;

    public interface ControlListener {
        void onToggleServer();
        void onLaunchTrain(String trainId, boolean tls);
        void onStopAll();
    }

    public SimulationControlBar(ControlListener listener) {
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

        tr01Btn = new JButton("+TR01");
        tr01Btn.setFont(Theme.FONT_BODY_BOLD);
        tr01Btn.setFocusPainted(false);
        tr01Btn.addActionListener(e -> {
            if (listener != null) {
                listener.onLaunchTrain("TR01", false);
            }
        });

        tr02Btn = new JButton("+TR02");
        tr02Btn.setFont(Theme.FONT_BODY_BOLD);
        tr02Btn.setFocusPainted(false);
        tr02Btn.addActionListener(e -> {
            if (listener != null) {
                listener.onLaunchTrain("TR02", false);
            }
        });

        tr03Btn = new JButton("+TR03 (TLS)");
        tr03Btn.setFont(Theme.FONT_BODY_BOLD);
        tr03Btn.setFocusPainted(false);
        tr03Btn.addActionListener(e -> {
            if (listener != null) {
                listener.onLaunchTrain("TR03", true);
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

        buttonPanel.add(serverToggleBtn);
        buttonPanel.add(Box.createHorizontalStrut(Theme.PAD));
        buttonPanel.add(tr01Btn);
        buttonPanel.add(Box.createHorizontalStrut(Theme.GAP));
        buttonPanel.add(tr02Btn);
        buttonPanel.add(Box.createHorizontalStrut(Theme.GAP));
        buttonPanel.add(tr03Btn);
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
        tr01Btn.setEnabled(!inFlight);
        tr02Btn.setEnabled(!inFlight);
        tr03Btn.setEnabled(!inFlight);
        stopAllBtn.setEnabled(!inFlight);
    }
}
