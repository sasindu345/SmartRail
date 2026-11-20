package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Small rounded pill badge with a coloured status dot and label.
 * Owner: Member 3 (design-system.md §4).
 *
 * <p>Examples: {@code "Simulation Online"}, {@code "Connected (TLS)"}, {@code "INACTIVE"}, {@code "HOLD"}.
 */
public class StatusPill extends JComponent {

    private String text;
    private Color dotColor;
    private Color bgColor;
    private Color textColor;

    private static final int DOT_SIZE = 8;
    private static final int HORIZONTAL_PAD = 10;
    private static final int VERTICAL_PAD = 4;
    private static final int DOT_TEXT_GAP = 6;

    public StatusPill(String text, Color dotColor) {
        this(text, dotColor, Theme.SURFACE, Theme.TEXT);
    }

    public StatusPill(String text, Color dotColor, Color bgColor, Color textColor) {
        this.text = text != null ? text : "";
        this.dotColor = dotColor != null ? dotColor : Theme.INACTIVE;
        this.bgColor = bgColor != null ? bgColor : Theme.SURFACE;
        this.textColor = textColor != null ? textColor : Theme.TEXT;
        setFont(Theme.FONT_SMALL_BOLD);
        updatePreferredSize();
    }

    public void setStatus(String text, Color dotColor) {
        this.text = text != null ? text : "";
        this.dotColor = dotColor != null ? dotColor : Theme.INACTIVE;
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public void setStatus(String text, Color dotColor, Color bgColor, Color textColor) {
        this.text = text != null ? text : "";
        this.dotColor = dotColor != null ? dotColor : Theme.INACTIVE;
        this.bgColor = bgColor != null ? bgColor : Theme.SURFACE;
        this.textColor = textColor != null ? textColor : Theme.TEXT;
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public String getText() {
        return text;
    }

    public Color getDotColor() {
        return dotColor;
    }

    private void updatePreferredSize() {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont() : Theme.FONT_SMALL_BOLD);
        int textWidth = fm.stringWidth(text);
        int width = HORIZONTAL_PAD * 2 + DOT_SIZE + DOT_TEXT_GAP + textWidth;
        int height = Math.max(fm.getHeight() + VERTICAL_PAD * 2, 22);
        setPreferredSize(new Dimension(width, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = h;

        // Pill background
        g2.setColor(bgColor);
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

        // Subtle hairline border
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        // Status dot
        int dotY = (h - DOT_SIZE) / 2;
        int dotX = HORIZONTAL_PAD;
        g2.setColor(dotColor);
        g2.fillOval(dotX, dotY, DOT_SIZE, DOT_SIZE);

        // Label text
        g2.setColor(textColor);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int textX = dotX + DOT_SIZE + DOT_TEXT_GAP;
        int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, textX, textY);

        g2.dispose();
    }
}
