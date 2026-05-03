package com.vigolium.extension.ui.table;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.SortOrder;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class ThreeWayRowSorterTest {

    @Test
    void toggleCyclesAscDescUnsorted() {
        DefaultTableModel tableModel = new DefaultTableModel(new Object[][] {{"a"}, {"b"}}, new Object[] {"Col"});
        ThreeWayRowSorter<DefaultTableModel> sorter = new ThreeWayRowSorter<>(tableModel);

        // Initially unsorted
        assertTrue(sorter.getSortKeys().isEmpty());

        // First toggle: ascending
        sorter.toggleSortOrder(0);
        assertEquals(SortOrder.ASCENDING, sorter.getSortKeys().get(0).getSortOrder());

        // Second toggle: descending
        sorter.toggleSortOrder(0);
        assertEquals(SortOrder.DESCENDING, sorter.getSortKeys().get(0).getSortOrder());

        // Third toggle: unsorted
        sorter.toggleSortOrder(0);
        assertTrue(sorter.getSortKeys().isEmpty());
    }
}
