package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.AgentSession;

public class AgentSessionsTableModel extends BaseTableModel<AgentSession> {

    private int currentOffset;

    public AgentSessionsTableModel() {
        super(AgentSessionsColumnDefs.create());
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
