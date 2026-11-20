package com.smartrail.ui.components;

import com.smartrail.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Shared JTable styling rules: row height, header styling, alternating zebra rows, status-dot cell renderer.
 * Owner: Member 3 (control-center-spec.md §3, design-system.md §4).
 */
public final class TableStyles {

    public static final int ROW_HEIGHT = 28;

    private TableStyles() {
    }

    /**
     * Apply the shared table style to any JTable across all screens.
     */
    public static void apply(JTable table) {
        table.setRowHeight(ROW_HEIGHT);
        table.setFont(Theme.FONT_BODY);
        table.setForeground(Theme.TEXT);
        table.setBackground(Theme.SURFACE);
        table.setSelectionBackground(Theme.PRIMARY_SOFT);
        table.setSelectionForeground(Theme.TEXT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(Theme.BORDER);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setFont(Theme.FONT_BODY_BOLD);
            header.setForeground(Theme.TEXT);
            header.setBackground(Theme.BG);
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
            header.setReorderingAllowed(false);
            header.setPreferredSize(new Dimension(0, 32));
        }

        // Zebra striping for non-custom renderers
        TableCellRenderer defaultRenderer = table.getDefaultRenderer(Object.class);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = defaultRenderer != null
                        ? defaultRenderer.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col)
                        : super.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col);

                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT);
                    c.setForeground(Theme.TEXT);
                }
                if (c instanceof JComponent jc) {
                    jc.setBorder(BorderFactory.createEmptyBorder(0, Theme.GAP, 0, Theme.GAP));
                }
                return c;
            }
        });
    }

    /**
     * Renders a cell containing status text with an appropriate colored status dot.
     */
    public static TableCellRenderer statusDotRenderer() {
        return new TableCellRenderer() {
            private final StatusPill pill = new StatusPill("", Theme.INACTIVE);

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                String text = value != null ? value.toString() : "";
                Color dotColor = colorForStatus(text);
                Color bg = isSelected ? Theme.PRIMARY_SOFT : (row % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT);
                pill.setStatus(text, dotColor, bg, Theme.TEXT);
                return pill;
            }
        };
    }

    private static Color colorForStatus(String text) {
        if (text == null) return Theme.INACTIVE;
        String upper = text.toUpperCase();
        if (upper.contains("STARTED") || upper.contains("OK") || upper.contains("CONNECTED") ||
                upper.contains("RUNNING") || upper.contains("OCCUPIED") || upper.contains("ONLINE")) {
            return Theme.OK;
        }
        if (upper.contains("WARN") || upper.contains("HELD") || upper.contains("HOLD") ||
                upper.contains("RESERVED") || upper.contains("WAITING")) {
            return Theme.WARN;
        }
        if (upper.contains("FAIL") || upper.contains("ERROR") || upper.contains("DENIED") ||
                upper.contains("EMERGENCY") || upper.contains("STOPPED")) {
            return Theme.DANGER;
        }
        return Theme.INACTIVE;
    }
}
