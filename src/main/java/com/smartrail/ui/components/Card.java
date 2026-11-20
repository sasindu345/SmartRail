package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Base card container: white rounded panel, hairline border, optional title.
 * Owner: Member 3 (design-system.md §4).
 */
public class Card extends JPanel {

    private final JPanel contentPane;
    private final JLabel titleLabel;

    public Card() {
        this(null);
    }

    public Card(String title) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.PAD, Theme.PAD, Theme.PAD, Theme.PAD));

        titleLabel = new JLabel();
        titleLabel.setFont(Theme.FONT_CARD_TITLE);
        titleLabel.setForeground(Theme.TEXT);

        contentPane = new JPanel();
        contentPane.setOpaque(false);
        contentPane.setLayout(new BorderLayout());

        if (title != null && !title.isBlank()) {
            setTitle(title);
            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.add(titleLabel);
            header.add(Box.createVerticalStrut(Theme.GAP));
            super.add(header, BorderLayout.NORTH);
        }

        super.add(contentPane, BorderLayout.CENTER);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setBody(JComponent comp) {
        contentPane.removeAll();
        if (comp != null) {
            contentPane.add(comp, BorderLayout.CENTER);
        }
        contentPane.revalidate();
        contentPane.repaint();
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    @Override
    public Component add(Component comp) {
        return contentPane.add(comp);
    }

    @Override
    public void add(Component comp, Object constraints) {
        contentPane.add(comp, constraints);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Background
        g2.setColor(Theme.SURFACE);
        g2.fillRoundRect(0, 0, width - 1, height - 1, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);

        // Hairline border
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, width - 1, height - 1, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);

        g2.dispose();
        super.paintComponent(g);
    }
}
