package com.vigolium.extension.ui.dialog;

import com.vigolium.extension.filter.*;
import java.awt.*;
import javax.swing.*;

public class FilterRuleEditDialog extends JDialog {

    private final JCheckBox enabledCheck;
    private final JComboBox<Operator> operatorCombo;
    private final JComboBox<MatchType> matchTypeCombo;
    private final JComboBox<Relationship> relationshipCombo;
    private final JTextField conditionField;
    private boolean confirmed;

    public FilterRuleEditDialog(Frame owner, FilterRule existing, boolean isFirst) {
        super(owner, existing == null ? "Add Filter Rule" : "Edit Filter Rule", true);

        enabledCheck = new JCheckBox("Enabled", existing != null ? existing.isEnabled() : true);
        enabledCheck.setName("filterRuleEnabledCheck");

        operatorCombo = new JComboBox<>(Operator.values());
        operatorCombo.setName("filterRuleOperatorCombo");
        if (isFirst) {
            operatorCombo.setEnabled(false);
        }
        if (existing != null && existing.getOperator() != null) {
            operatorCombo.setSelectedItem(existing.getOperator());
        }

        matchTypeCombo = new JComboBox<>(MatchType.values());
        matchTypeCombo.setName("filterRuleMatchTypeCombo");

        relationshipCombo = new JComboBox<>();
        relationshipCombo.setName("filterRuleRelationshipCombo");

        conditionField = new JTextField(30);
        conditionField.setName("filterRuleConditionField");

        // Attach listeners BEFORE setting existing values so they trigger properly
        matchTypeCombo.addActionListener(e -> updateRelationships());

        relationshipCombo.addActionListener(e -> {
            Relationship rel = (Relationship) relationshipCombo.getSelectedItem();
            if (rel != null) {
                conditionField.setEnabled(rel.needsCondition());
                if (!rel.needsCondition()) conditionField.setText("");
            }
        });

        // Initialize relationships for default match type
        updateRelationships();

        // Now set existing values — listeners will fire and update state correctly
        if (existing != null) {
            matchTypeCombo.setSelectedItem(existing.getMatchType());
            relationshipCombo.setSelectedItem(existing.getRelationship());
            conditionField.setText(existing.getCondition());
        }

        // Layout
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 10, 5);

        int row = 0;
        gbc.gridx = 0;
        gbc.gridy = row;
        form.add(new JLabel("Enabled:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(enabledCheck, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        form.add(new JLabel("Operator:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(operatorCombo, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        form.add(new JLabel("Match Type:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(matchTypeCombo, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        form.add(new JLabel("Relationship:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(relationshipCombo, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        form.add(new JLabel("Condition:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(conditionField, gbc);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        okBtn.setName("filterRuleOkButton");
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setName("filterRuleCancelButton");
        buttons.add(cancelBtn);
        buttons.add(okBtn);

        okBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        getRootPane().setDefaultButton(okBtn);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "escape");
        getRootPane().getActionMap().put("escape", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);

        setName("filterRuleEditDialog");
    }

    private void updateRelationships() {
        MatchType selected = (MatchType) matchTypeCombo.getSelectedItem();
        if (selected == null) return;
        relationshipCombo.removeAllItems();
        for (Relationship r : getRelationshipsFor(selected)) {
            relationshipCombo.addItem(r);
        }
        Relationship rel = (Relationship) relationshipCombo.getSelectedItem();
        boolean needsCondition = rel != null && rel.needsCondition();
        conditionField.setEnabled(needsCondition);
        if (!needsCondition) conditionField.setText("");
    }

    private Relationship[] getRelationshipsFor(MatchType matchType) {
        return switch (matchType) {
            case FILE_EXTENSION, HTTP_METHOD, CONTENT_TYPE, STATUS_CODE, HOST -> new Relationship[] {
                Relationship.MATCHES, Relationship.DOES_NOT_MATCH, Relationship.EQUALS, Relationship.NOT_EQUALS
            };
            case URL -> new Relationship[] {
                Relationship.MATCHES,
                Relationship.DOES_NOT_MATCH,
                Relationship.EQUALS,
                Relationship.NOT_EQUALS,
                Relationship.IS_IN_TARGET_SCOPE,
                Relationship.IS_NOT_IN_TARGET_SCOPE
            };
            case REQUEST -> new Relationship[] {
                Relationship.HAS_PARAMETERS,
                Relationship.HAS_BODY,
                Relationship.DOES_NOT_HAVE_PARAMETERS,
                Relationship.DOES_NOT_HAVE_BODY
            };
        };
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public FilterRule getRule() {
        return new FilterRule(
                enabledCheck.isSelected(),
                operatorCombo.isEnabled() ? (Operator) operatorCombo.getSelectedItem() : null,
                (MatchType) matchTypeCombo.getSelectedItem(),
                (Relationship) relationshipCombo.getSelectedItem(),
                conditionField.getText());
    }
}
