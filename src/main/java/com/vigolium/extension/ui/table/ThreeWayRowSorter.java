package com.vigolium.extension.ui.table;

import java.util.List;
import javax.swing.SortOrder;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

public class ThreeWayRowSorter<M extends TableModel> extends TableRowSorter<M> {

    public ThreeWayRowSorter(M model) {
        super(model);
    }

    @Override
    public void toggleSortOrder(int column) {
        List<? extends SortKey> keys = getSortKeys();
        if (!keys.isEmpty()
                && keys.get(0).getColumn() == column
                && keys.get(0).getSortOrder() == SortOrder.DESCENDING) {
            setSortKeys(null);
        } else {
            super.toggleSortOrder(column);
        }
    }
}
