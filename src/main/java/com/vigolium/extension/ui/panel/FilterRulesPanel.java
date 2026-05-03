package com.vigolium.extension.ui.panel;

import com.vigolium.extension.filter.FilterRule;
import com.vigolium.extension.ui.table.FilterRulesTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.*;

public class FilterRulesPanel extends JPanel {

    private final FilterRulesTableModel tableModel;
    private final JTable table;
    private final JButton addButton;
    private final JButton editButton;
    private final JButton removeButton;
    private final JButton resetDefaultButton;
    private final JButton upButton;
    private final JButton downButton;

    public FilterRulesPanel() {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("filterRulesPanel");

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Proxy Filter Rules");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel("Define rules to filter which proxy requests are forwarded to Vigolium.");
        descLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        // Buttons panel (left side)
        JPanel buttonsPanel = new JPanel(new GridLayout(0, 1, 0, 5));
        addButton = new JButton("Add");
        addButton.setName("filterRulesAddButton");
        editButton = new JButton("Edit");
        editButton.setName("filterRulesEditButton");
        removeButton = new JButton("Remove");
        removeButton.setName("filterRulesRemoveButton");
        resetDefaultButton = new JButton("Reset Default");
        resetDefaultButton.setName("filterRulesResetDefaultButton");
        upButton = new JButton("Up");
        upButton.setName("filterRulesUpButton");
        downButton = new JButton("Down");
        downButton.setName("filterRulesDownButton");

        buttonsPanel.add(addButton);
        buttonsPanel.add(editButton);
        buttonsPanel.add(removeButton);
        buttonsPanel.add(resetDefaultButton);
        buttonsPanel.add(upButton);
        buttonsPanel.add(downButton);

        JPanel buttonsWrapper = new JPanel(new BorderLayout());
        buttonsWrapper.add(buttonsPanel, BorderLayout.NORTH);

        // Table
        tableModel = new FilterRulesTableModel();
        table = new JTable(tableModel);
        table.setName("filterRulesTable");
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.putClientProperty("html.disable", Boolean.TRUE);
        table.setPreferredScrollableViewportSize(
                new Dimension(table.getPreferredScrollableViewportSize().width, table.getRowHeight() * 10));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(25);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setWheelScrollingEnabled(false);
        scrollPane.addMouseWheelListener(e -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            if (bar.isVisible() && bar.getValue() > 0 && bar.getValue() + bar.getVisibleAmount() < bar.getMaximum()) {
                bar.setValue(bar.getValue() + e.getWheelRotation() * bar.getUnitIncrement() * e.getScrollAmount());
            } else {
                Container parent = scrollPane.getParent();
                if (parent != null) {
                    parent.dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, e, parent));
                }
            }
        });

        // Content: buttons left, table right
        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.add(scrollPane, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 0));
        contentPanel.add(buttonsWrapper, BorderLayout.WEST);
        contentPanel.add(tableWrapper, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
    }

    public List<FilterRule> getRules() {
        return tableModel.getRows();
    }

    public void setRules(List<FilterRule> rules) {
        tableModel.setRows(rules);
    }

    public int getSelectedRuleIndex() {
        return table.getSelectedRow();
    }

    public FilterRulesTableModel getTableModel() {
        return tableModel;
    }

    public void addAddListener(ActionListener listener) {
        addButton.addActionListener(listener);
    }

    public void addEditListener(ActionListener listener) {
        editButton.addActionListener(listener);
    }

    public void addRemoveListener(ActionListener listener) {
        removeButton.addActionListener(listener);
    }

    public void addResetDefaultListener(ActionListener listener) {
        resetDefaultButton.addActionListener(listener);
    }

    public void addUpListener(ActionListener listener) {
        upButton.addActionListener(listener);
    }

    public void addDownListener(ActionListener listener) {
        downButton.addActionListener(listener);
    }

    public void setSelectedRuleIndex(int index) {
        if (index >= 0 && index < tableModel.getRowCount()) {
            table.setRowSelectionInterval(index, index);
        }
    }
}
