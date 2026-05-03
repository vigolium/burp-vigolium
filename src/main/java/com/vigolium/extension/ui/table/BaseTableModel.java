package com.vigolium.extension.ui.table;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public abstract class BaseTableModel<E> extends AbstractTableModel {

    protected final List<ColumnDef<E>> columns;
    protected final ArrayList<E> rows = new ArrayList<>();

    protected BaseTableModel(List<ColumnDef<E>> columns) {
        this.columns = columns;
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public String getColumnName(int col) {
        return columns.get(col).name();
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return columns.get(col).type();
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return columns.get(col).editable();
    }

    @Override
    public Object getValueAt(int row, int col) {
        return columns.get(col).accessor().extract(rows.get(row));
    }

    public E getRow(int row) {
        return rows.get(row);
    }

    public List<E> getRows() {
        return List.copyOf(rows);
    }

    public void addRow(E row) {
        int index = rows.size();
        rows.add(row);
        fireTableRowsInserted(index, index);
    }

    public void addRows(List<E> newRows) {
        if (newRows.isEmpty()) return;
        int first = rows.size();
        rows.addAll(newRows);
        fireTableRowsInserted(first, rows.size() - 1);
    }

    public void removeRow(int index) {
        rows.remove(index);
        fireTableRowsDeleted(index, index);
    }

    public void setRows(List<E> newRows) {
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    public void clear() {
        int last = rows.size() - 1;
        if (last < 0) return;
        rows.clear();
        fireTableRowsDeleted(0, last);
    }

    @Override
    public void fireTableRowsInserted(int firstRow, int lastRow) {
        if (SwingUtilities.isEventDispatchThread()) {
            super.fireTableRowsInserted(firstRow, lastRow);
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> super.fireTableRowsInserted(firstRow, lastRow));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void fireTableRowsDeleted(int firstRow, int lastRow) {
        if (SwingUtilities.isEventDispatchThread()) {
            super.fireTableRowsDeleted(firstRow, lastRow);
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> super.fireTableRowsDeleted(firstRow, lastRow));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void fireTableDataChanged() {
        if (SwingUtilities.isEventDispatchThread()) {
            super.fireTableDataChanged();
        } else {
            try {
                SwingUtilities.invokeAndWait(super::fireTableDataChanged);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
