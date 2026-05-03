package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.HttpRecord;

public class HttpRecordsTableModel extends BaseTableModel<HttpRecord> {

    private int currentOffset;

    public HttpRecordsTableModel() {
        super(HttpRecordsColumnDefs.create());
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
