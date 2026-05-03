package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.Scan;

public class ScansTableModel extends BaseTableModel<Scan> {

    private int currentOffset;

    public ScansTableModel() {
        super(ScansColumnDefs.create());
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
