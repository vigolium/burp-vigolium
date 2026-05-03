package com.vigolium.extension.ui.table;

import java.awt.Component;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;

public class SortableHeaderRenderer implements TableCellRenderer {

    private final TableCellRenderer delegate;
    private final IntSupplier sortedColumnSupplier;
    private final Supplier<String> sortOrderSupplier;

    public SortableHeaderRenderer(
            TableCellRenderer delegate, IntSupplier sortedColumnSupplier, Supplier<String> sortOrderSupplier) {
        this.delegate = delegate;
        this.sortedColumnSupplier = sortedColumnSupplier;
        this.sortOrderSupplier = sortOrderSupplier;
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component comp = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (comp instanceof JLabel label) {
            int sortedCol = sortedColumnSupplier.getAsInt();
            if (sortedCol >= 0 && sortedCol == column) {
                String order = sortOrderSupplier.get();
                Icon icon = "asc".equals(order)
                        ? UIManager.getIcon("Table.ascendingSortIcon")
                        : UIManager.getIcon("Table.descendingSortIcon");
                label.setIcon(icon);
                label.setHorizontalTextPosition(JLabel.LEADING);
            } else {
                label.setIcon(null);
            }
        }
        return comp;
    }
}
