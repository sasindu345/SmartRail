package com.smartrail.ui.theme;

import com.formdev.flatlaf.FlatLightLaf;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared styling, colour tokens, font stack and layout metrics for the SmartRail Control Center.
 * Owner: Member 3.
 *
 * <p>All colours and fonts must come from this class. No panel may invent its own literal colours
 * or custom font definitions.
 */
public final class Theme {

    // ---- Colour tokens (design-system.md §2) ----
    public static final Color PRIMARY = new Color(0x14532D);        // #14532D brand bar, selected nav item, train chips
    public static final Color PRIMARY_SOFT = new Color(0xD1FAE5);   // #D1FAE5 occupied section fill, selected card bg
    public static final Color OK = new Color(0x16A34A);              // #16A34A running / connected / service up
    public static final Color WARN = new Color(0xF59E0B);            // #F59E0B held / reserved / waiting
    public static final Color DANGER = new Color(0xDC2626);          // #DC2626 emergency stop, failed service, denied
    public static final Color INACTIVE = new Color(0x9CA3AF);        // #9CA3AF inactive train, free section, stopped
    public static final Color BG = new Color(0xF5F6F7);              // #F5F6F7 window background
    public static final Color SURFACE = new Color(0xFFFFFF);         // #FFFFFF cards and tables
    public static final Color BORDER = new Color(0xE5E7EB);          // #E5E7EB hairline card and table borders
    public static final Color TEXT = new Color(0x111827);            // #111827 primary text
    public static final Color TEXT_MUTED = new Color(0x6B7280);      // #6B7280 labels, captions, secondary text

    // Subtle hover / table zebra
    public static final Color ROW_ALT = new Color(0xF9FAFB);
    public static final Color HOVER = new Color(0xF3F4F6);

    // ---- Layout metrics (design-system.md §4) ----
    public static final int GAP = 8;
    public static final int PAD = 16;
    public static final int CORNER_RADIUS = 10;

    // ---- Typography (design-system.md §3) ----
    // Stack: Helvetica Neue -> Segoe UI -> Dialog (JDK logical font)
    private static final String BASE_FONT_FAMILY = resolveBaseFontFamily();

    public static final Font FONT_TITLE = new Font(BASE_FONT_FAMILY, Font.BOLD, 20);
    public static final Font FONT_SUBTITLE = new Font(BASE_FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_CARD_TITLE = new Font(BASE_FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_BODY = new Font(BASE_FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font(BASE_FONT_FAMILY, Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font(BASE_FONT_FAMILY, Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font(BASE_FONT_FAMILY, Font.BOLD, 11);
    public static final Font FONT_MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Theme() {
    }

    /**
     * Install FlatLaf look-and-feel.
     * Must degrade gracefully if it fails so styling never breaks the demo.
     */
    public static void install() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            FlatLightLaf.setup();
        } catch (Throwable t) {
            System.err.println("[THEME] FlatLaf installation failed, falling back to system L&F: " + t.getMessage());
        }
    }

    private static String resolveBaseFontFamily() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return Font.DIALOG;
            }
            Set<String> available = new HashSet<>(
                    Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            );
            if (available.contains("Helvetica Neue")) {
                return "Helvetica Neue";
            }
            if (available.contains("Segoe UI")) {
                return "Segoe UI";
            }
        } catch (Throwable ignored) {
        }
        return Font.DIALOG;
    }
}
