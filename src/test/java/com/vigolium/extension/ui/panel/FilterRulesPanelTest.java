package com.vigolium.extension.ui.panel;

import static org.junit.jupiter.api.Assertions.*;

import com.vigolium.extension.filter.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilterRulesPanelTest {

    private FilterRulesPanel panel;

    @BeforeEach
    void setUp() {
        panel = new FilterRulesPanel();
    }

    @Test
    void setRules_populatesTable() {
        List<FilterRule> rules = List.of(
                new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "a"),
                new FilterRule(true, Operator.AND, MatchType.HOST, Relationship.MATCHES, "b"),
                new FilterRule(false, Operator.OR, MatchType.FILE_EXTENSION, Relationship.DOES_NOT_MATCH, "c"));
        panel.setRules(rules);
        assertEquals(3, panel.getTableModel().getRowCount());
    }

    @Test
    void getSelectedRuleIndex_noSelection_returnsMinusOne() {
        panel.setRules(List.of(new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "a")));
        assertEquals(-1, panel.getSelectedRuleIndex());
    }

    @Test
    void setSelectedRuleIndex_selectsRow() {
        List<FilterRule> rules = List.of(
                new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "a"),
                new FilterRule(true, Operator.AND, MatchType.HOST, Relationship.MATCHES, "b"));
        panel.setRules(rules);
        panel.setSelectedRuleIndex(1);
        assertEquals(1, panel.getSelectedRuleIndex());
    }

    @Test
    void setSelectedRuleIndex_outOfBounds_ignored() {
        List<FilterRule> rules = List.of(new FilterRule(true, null, MatchType.URL, Relationship.MATCHES, "a"));
        panel.setRules(rules);
        panel.setSelectedRuleIndex(5);
        assertEquals(-1, panel.getSelectedRuleIndex());

        panel.setSelectedRuleIndex(-1);
        assertEquals(-1, panel.getSelectedRuleIndex());
    }
}
