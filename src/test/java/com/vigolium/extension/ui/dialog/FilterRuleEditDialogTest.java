package com.vigolium.extension.ui.dialog;

import static org.junit.jupiter.api.Assertions.*;

import com.vigolium.extension.filter.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class FilterRuleEditDialogTest {

    @Test
    void addMode_defaultValues() {
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, null, true);

        assertTrue(dialog.getRule().isEnabled());
        assertNull(dialog.getRule().getOperator()); // isFirst disables operator
        assertEquals(MatchType.FILE_EXTENSION, dialog.getRule().getMatchType());
        assertEquals(Relationship.MATCHES, dialog.getRule().getRelationship());
        assertEquals("", dialog.getRule().getCondition());
    }

    @Test
    void editMode_populatesExistingValues() {
        FilterRule existing =
                new FilterRule(false, Operator.AND, MatchType.HOST, Relationship.DOES_NOT_MATCH, "example.com");
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, existing, false);

        FilterRule result = dialog.getRule();
        assertFalse(result.isEnabled());
        assertEquals(Operator.AND, result.getOperator());
        assertEquals(MatchType.HOST, result.getMatchType());
        assertEquals(Relationship.DOES_NOT_MATCH, result.getRelationship());
        assertEquals("example.com", result.getCondition());
    }

    @Test
    void editMode_scopeRelationship_disablesCondition() {
        FilterRule existing = new FilterRule(true, Operator.OR, MatchType.URL, Relationship.IS_IN_TARGET_SCOPE, "");
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, existing, false);

        JTextField conditionField = findComponent(dialog, "filterRuleConditionField", JTextField.class);
        assertNotNull(conditionField);
        assertFalse(conditionField.isEnabled(), "Condition field should be disabled for IS_IN_TARGET_SCOPE");
    }

    @Test
    void isFirst_disablesOperator() {
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, null, true);

        JComboBox<?> operatorCombo = findComponent(dialog, "filterRuleOperatorCombo", JComboBox.class);
        assertNotNull(operatorCombo);
        assertFalse(operatorCombo.isEnabled());
    }

    @Test
    void matchTypeChange_updatesRelationships() {
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, null, false);

        JComboBox<MatchType> matchTypeCombo = findComponent(dialog, "filterRuleMatchTypeCombo", JComboBox.class);
        JComboBox<Relationship> relationshipCombo =
                findComponent(dialog, "filterRuleRelationshipCombo", JComboBox.class);

        // Change to URL — should have 6 relationships including equals and scope
        matchTypeCombo.setSelectedItem(MatchType.URL);
        assertEquals(6, relationshipCombo.getItemCount());

        // Change to REQUEST — should have 4 different relationships
        matchTypeCombo.setSelectedItem(MatchType.REQUEST);
        assertEquals(4, relationshipCombo.getItemCount());
        assertEquals(Relationship.HAS_PARAMETERS, relationshipCombo.getItemAt(0));

        // Change to HOST — should have 4 relationships (matches, not matches, equals, not equals)
        matchTypeCombo.setSelectedItem(MatchType.HOST);
        assertEquals(4, relationshipCombo.getItemCount());
    }

    @Test
    void okButton_setsConfirmed() {
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, null, false);
        assertFalse(dialog.isConfirmed());

        JButton okBtn = findComponent(dialog, "filterRuleOkButton", JButton.class);
        assertNotNull(okBtn);
        okBtn.doClick();
        assertTrue(dialog.isConfirmed());
    }

    @Test
    void cancelButton_notConfirmed() {
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, null, false);
        assertFalse(dialog.isConfirmed());
    }

    @Test
    void getRule_returnsCorrectValues() {
        FilterRuleEditDialog dialog = new FilterRuleEditDialog(null, null, false);

        JCheckBox enabledCheck = findComponent(dialog, "filterRuleEnabledCheck", JCheckBox.class);
        JComboBox<Operator> operatorCombo = findComponent(dialog, "filterRuleOperatorCombo", JComboBox.class);
        JComboBox<MatchType> matchTypeCombo = findComponent(dialog, "filterRuleMatchTypeCombo", JComboBox.class);
        JComboBox<Relationship> relationshipCombo =
                findComponent(dialog, "filterRuleRelationshipCombo", JComboBox.class);
        JTextField conditionField = findComponent(dialog, "filterRuleConditionField", JTextField.class);

        enabledCheck.setSelected(false);
        operatorCombo.setSelectedItem(Operator.OR);
        matchTypeCombo.setSelectedItem(MatchType.HOST);
        relationshipCombo.setSelectedItem(Relationship.MATCHES);
        conditionField.setText("test.com");

        FilterRule rule = dialog.getRule();
        assertFalse(rule.isEnabled());
        assertEquals(Operator.OR, rule.getOperator());
        assertEquals(MatchType.HOST, rule.getMatchType());
        assertEquals(Relationship.MATCHES, rule.getRelationship());
        assertEquals("test.com", rule.getCondition());
    }

    @SuppressWarnings("unchecked")
    private <T extends java.awt.Component> T findComponent(java.awt.Container container, String name, Class<T> type) {
        for (java.awt.Component comp : container.getComponents()) {
            if (name.equals(comp.getName()) && type.isInstance(comp)) {
                return (T) comp;
            }
            if (comp instanceof java.awt.Container child) {
                T found = findComponent(child, name, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
