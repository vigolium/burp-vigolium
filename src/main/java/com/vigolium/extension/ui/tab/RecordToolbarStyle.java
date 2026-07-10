package com.vigolium.extension.ui.tab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

final class RecordToolbarStyle {

    static final int CONTROL_GAP = 8;

    private RecordToolbarStyle() {}

    static Border toolbarBorder() {
        Color separator = UIManager.getColor("Component.borderColor");
        if (separator == null) separator = UIManager.getColor("Separator.foreground");
        if (separator == null) separator = Color.GRAY;
        return new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, separator), BorderFactory.createEmptyBorder(8, 12, 8, 12));
    }

    static JSeparator separator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 22));
        separator.setMaximumSize(new Dimension(1, 22));
        return separator;
    }

    static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        makeBold(label);
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) label.setForeground(muted);
        return label;
    }

    static void makeBold(JLabel label) {
        Font base = UIManager.getFont("defaultFont");
        if (base != null) label.setFont(base.deriveFont(Font.BOLD));
    }

    static void styleTable(JTable table) {
        table.setRowHeight(Math.max(table.getRowHeight(), 24));
        if (table.getTableHeader() != null) {
            Dimension preferred = table.getTableHeader().getPreferredSize();
            table.getTableHeader().setPreferredSize(new Dimension(preferred.width, Math.max(preferred.height, 26)));
        }
    }
}
