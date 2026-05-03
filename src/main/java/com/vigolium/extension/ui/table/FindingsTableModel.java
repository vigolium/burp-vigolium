package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.Finding;

public class FindingsTableModel extends BaseTableModel<Finding> {

    private int currentOffset;

    public FindingsTableModel() {
        super(FindingsColumnDefs.create());
    }

    public void setOffset(int offset) {
        this.currentOffset = offset;
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (col == 0) {
            return currentOffset + row + 1;
        }
        return super.getValueAt(row, col);
    }
}
