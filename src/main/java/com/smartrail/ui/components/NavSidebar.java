package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left navigation sidebar: drives the CardLayout screens.
 * Owner: Member 3 (control-center-spec.md §3, design-system.md §4).
 */
public class NavSidebar extends JPanel {

    public static final String OVERVIEW = "Overview";
    public static final String TRAINS = "Trains";
    public static final String TRACK_SECTIONS = "Track Sections";
    public static final String ALERTS = "Alerts";
    public static final String NETWORK = "Network";
    public static final String SECURITY = "Security";
    public static final String ADMIN = "Admin";
    public static final String EVENT_LOG = "Event Log";

    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final Consumer<String> onSelect;
    private String selectedKey = OVERVIEW;

    public NavSidebar(Consumer<String> onSelect) {
        super(new BorderLayout());
        this.onSelect = onSelect != null ? onSelect : s -> {};

        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        setPreferredSize(new Dimension(180, 0));

        JPanel listPanel = new JPanel();
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(Theme.PAD, Theme.GAP, Theme.PAD, Theme.GAP));

        String[] items = {
                OVERVIEW,
                TRAINS,
                TRACK_SECTIONS,
                ALERTS,
                NETWORK,
                SECURITY,
                ADMIN,
                EVENT_LOG
        };

        for (String item : items) {
            JButton btn = createNavButton(item);
            navButtons.put(item, btn);
            listPanel.add(btn);
            listPanel.add(Box.createVerticalStrut(4));
        }

        add(listPanel, BorderLayout.NORTH);
        select(OVERVIEW);
    }

    private JButton createNavButton(String name) {
        JButton btn = new JButton(name);
        btn.setFont(Theme.FONT_BODY_BOLD);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btn.setPreferredSize(new Dimension(160, 36));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(true);
        btn.setHorizontalAlignment(JButton.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(0, Theme.PAD, 0, Theme.PAD));

        btn.addActionListener(e -> select(name));
        return btn;
    }

    public void select(String name) {
        if (!navButtons.containsKey(name)) {
            return;
        }
        selectedKey = name;
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            boolean active = entry.getKey().equals(name);
            JButton b = entry.getValue();
            if (active) {
                b.setBackground(Theme.PRIMARY);
                b.setForeground(Theme.SURFACE);
                b.setOpaque(true);
            } else {
                b.setBackground(Theme.BG);
                b.setForeground(Theme.TEXT);
                b.setOpaque(false);
            }
            b.repaint();
        }
        onSelect.accept(name);
    }

    public String getSelectedKey() {
        return selectedKey;
    }
}
