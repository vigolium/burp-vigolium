package com.vigolium.extension.ui.table;

import static org.junit.jupiter.api.Assertions.*;

import com.vigolium.extension.filter.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilterRulesTableModelTest {

    private FilterRulesTableModel model;

    @BeforeEach
    void setUp() {
        model = new FilterRulesTableModel();
    }

    @Test
    void hasCorrectColumns() {
        assertEquals(5, model.getColumnCount());
        assertEquals("Enabled", model.getColumnName(0));
        assertEquals("Operator", model.getColumnName(1));
        assertEquals("Match Type", model.getColumnName(2));
        assertEquals("Relationship", model.getColumnName(3));
        assertEquals("Condition", model.getColumnName(4));
    }

    @Test
    void enabledColumnIsBoolean() {
        assertEquals(Boolean.class, model.getColumnClass(0));
    }

    @Test
    void enabledColumnIsEditable() {
        model.addRow(new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "test"));
        assertTrue(model.isCellEditable(0, 0));
        assertFalse(model.isCellEditable(0, 1));
    }

    @Test
    void setValueAtTogglesEnabled() {
        FilterRule rule = new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "test");
        model.addRow(rule);

        assertTrue((Boolean) model.getValueAt(0, 0));

        model.setValueAt(false, 0, 0);
        assertFalse((Boolean) model.getValueAt(0, 0));
        assertFalse(rule.isEnabled());
    }

    @Test
    void accessorsExtractCorrectValues() {
        FilterRule rule = new FilterRule(true, Operator.OR, MatchType.HOST, Relationship.MATCHES, ".*\\.target\\.com");
        model.addRow(rule);

        assertEquals(true, model.getValueAt(0, 0));
        assertEquals("Or", model.getValueAt(0, 1));
        assertEquals("Host", model.getValueAt(0, 2));
        assertEquals("Matches", model.getValueAt(0, 3));
        assertEquals(".*\\.target\\.com", model.getValueAt(0, 4));
    }

    @Test
    void nullOperatorShowsEmpty() {
        FilterRule rule = new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "test");
        model.addRow(rule);
        assertEquals("", model.getValueAt(0, 1));
    }

    @Test
    void nullConditionShowsEmpty() {
        FilterRule rule = new FilterRule(true, null, MatchType.REQUEST, Relationship.HAS_PARAMETERS, null);
        model.addRow(rule);
        assertEquals("", model.getValueAt(0, 4));
    }
}
