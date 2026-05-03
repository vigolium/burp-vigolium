package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.Severity;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

public class SeverityRenderer extends DefaultTableCellRenderer {

    public SeverityRenderer() {
        putClientProperty("html.disable", Boolean.TRUE);
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value instanceof Severity severity) {
            setText(severity.label());
            if (!isSelected) {
                setForeground(severity.color());
            }
            setFont(getFont().deriveFont(Font.BOLD));
        }

        return this;
    }

    @Override
    public JToolTip createToolTip() {
        JToolTip tip = super.createToolTip();
        tip.putClientProperty("html.disable", Boolean.TRUE);
        return tip;
    }
}
