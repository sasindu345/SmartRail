package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bottom status bar showing the 6 per-service indicators:
 * ● TCP/NIO  ● UDP  ● TLS  ● Multicast  ● RMI  ● HTTP.
 * Owner: Member 3 (control-center-spec.md §3, design-system.md §4).
 *
 * <p>Green = service started, red = failed, grey = stopped / not started.
 */
public class NetworkStatusBar extends JPanel {

    public enum ServiceStatus {
        STOPPED,
        STARTED,
        FAILED
    }

    public static final String SERVICE_TCP_NIO = "TCP/NIO";
    public static final String SERVICE_UDP = "UDP";
    public static final String SERVICE_TLS = "TLS";
    public static final String SERVICE_MULTICAST = "Multicast";
    public static final String SERVICE_RMI = "RMI";
    public static final String SERVICE_HTTP = "HTTP";

    private final Map<String, ServiceIndicator> indicators = new LinkedHashMap<>();

    public NetworkStatusBar() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(4, Theme.PAD, 4, Theme.PAD));
        setPreferredSize(new Dimension(0, 28));

        JPanel dotsPanel = new JPanel();
        dotsPanel.setOpaque(false);
        dotsPanel.setLayout(new BoxLayout(dotsPanel, BoxLayout.X_AXIS));

        String[] services = {
                SERVICE_TCP_NIO,
                SERVICE_UDP,
                SERVICE_TLS,
                SERVICE_MULTICAST,
                SERVICE_RMI,
                SERVICE_HTTP
        };

        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            ServiceIndicator ind = new ServiceIndicator(name);
            indicators.put(name, ind);
            dotsPanel.add(ind);
            if (i < services.length - 1) {
                dotsPanel.add(Box.createHorizontalStrut(Theme.PAD));
            }
        }

        add(dotsPanel, BorderLayout.WEST);
    }

    public void setServiceStatus(String serviceKey, ServiceStatus status, String detail) {
        ServiceIndicator ind = indicators.get(serviceKey);
        if (ind != null) {
            ind.setStatus(status, detail);
        }
    }

    public void resetAll() {
        for (ServiceIndicator ind : indicators.values()) {
            ind.setStatus(ServiceStatus.STOPPED, null);
        }
    }

    private static class ServiceIndicator extends JPanel {
        private final String name;
        private final DotComponent dot;
        private final JLabel label;

        ServiceIndicator(String name) {
            this.name = name;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(false);

            dot = new DotComponent(Theme.INACTIVE);
            label = new JLabel(name);
            label.setFont(Theme.FONT_SMALL_BOLD);
            label.setForeground(Theme.TEXT_MUTED);

            add(dot);
            add(Box.createHorizontalStrut(5));
            add(label);
            setToolTipText(name + ": STOPPED");
        }

        void setStatus(ServiceStatus status, String detail) {
            Color color;
            String desc;
            switch (status) {
                case STARTED -> {
                    color = Theme.OK;
                    desc = "STARTED";
                    label.setForeground(Theme.TEXT);
                }
                case FAILED -> {
                    color = Theme.DANGER;
                    desc = "FAILED";
                    label.setForeground(Theme.DANGER);
                }
                default -> {
                    color = Theme.INACTIVE;
                    desc = "STOPPED";
                    label.setForeground(Theme.TEXT_MUTED);
                }
            }
            dot.setColor(color);
            String tip = name + ": " + desc + (detail != null && !detail.isBlank() ? " (" + detail + ")" : "");
            setToolTipText(tip);
            repaint();
        }
    }

    private static class DotComponent extends JComponent {
        private Color color;
        private static final int DIAMETER = 8;

        DotComponent(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(DIAMETER, DIAMETER));
            setMaximumSize(new Dimension(DIAMETER, DIAMETER));
            setMinimumSize(new Dimension(DIAMETER, DIAMETER));
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, DIAMETER, DIAMETER);
            g2.dispose();
        }
    }
}
