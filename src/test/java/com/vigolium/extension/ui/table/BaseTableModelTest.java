package com.vigolium.extension.ui.table;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseTableModelTest {

    private BaseTableModel<String> model;

    @BeforeEach
    void setUp() {
        List<ColumnDef<String>> cols = List.of(ColumnDef.<String>builder()
                .name("Value")
                .type(String.class)
                .accessor(s -> s)
                .build());
        model = new BaseTableModel<>(cols) {};
    }

    @Test
    void addRowIncreasesCount() {
        model.addRow("a");
        assertEquals(1, model.getRowCount());
        assertEquals("a", model.getRow(0));
    }

    @Test
    void addRowsAddsAll() {
        model.addRows(List.of("a", "b", "c"));
        assertEquals(3, model.getRowCount());
        assertEquals("b", model.getRow(1));
    }

    @Test
    void removeRowDecrementsCount() {
        model.addRows(List.of("a", "b"));
        model.removeRow(0);
        assertEquals(1, model.getRowCount());
        assertEquals("b", model.getRow(0));
    }

    @Test
    void clearRemovesAll() {
        model.addRows(List.of("a", "b", "c"));
        model.clear();
        assertEquals(0, model.getRowCount());
    }

    @Test
    void clearOnEmptyIsNoop() {
        model.clear();
        assertEquals(0, model.getRowCount());
    }

    @Test
    void setRowsReplacesAll() {
        model.addRow("a");
        model.setRows(List.of("x", "y"));
        assertEquals(2, model.getRowCount());
        assertEquals("x", model.getRow(0));
    }

    @Test
    void getRowsReturnsImmutableCopy() {
        model.addRow("a");
        List<String> rows = model.getRows();
        assertThrows(UnsupportedOperationException.class, () -> rows.add("b"));
    }

    @Test
    void getValueAtUsesAccessor() {
        model.addRow("hello");
        assertEquals("hello", model.getValueAt(0, 0));
    }

    @Test
    void columnMetadata() {
        assertEquals(1, model.getColumnCount());
        assertEquals("Value", model.getColumnName(0));
        assertEquals(String.class, model.getColumnClass(0));
    }
}
